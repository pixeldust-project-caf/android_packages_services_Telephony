/*
 * Copyright (c) 2021, The Linux Foundation. All rights reserved.

 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.

 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.services.telephony;

import android.os.Bundle;
import android.telecom.Connection;
import android.telephony.DisconnectCause;
import android.telecom.StatusHints;

import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.imsphone.ImsPhone.ImsDialArgs.DeferDial;
import com.android.internal.telephony.imsphone.ImsPhoneConnection;
import com.android.ims.internal.ConferenceParticipant;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/* Handles dialing an outgoing call when there is an ACTIVE call on the other sub */
public class HoldAndDialHandler extends HoldHandlerBase {
    private TelephonyConnection mConnToDial = null;
    private TelephonyConnectionService mConnectionService;
    private Phone mPhone;
    private int mVideoState;
    private Bundle mExtras;
    String[] mParticipants;
    boolean mIsConference = false;
    private com.android.internal.telephony.Connection mOriginalConnection = null;

    public HoldAndDialHandler(TelephonyConnection connToHold, TelephonyConnection connToDial,
                TelephonyConnectionService connectionService, Phone phone, int videoState,
                Bundle extras) {
        this(connToHold, connToDial, connectionService, phone, videoState);
        mExtras = extras;
    }

    public HoldAndDialHandler(TelephonyConnection connToHold, TelephonyConnection connToDial,
                TelephonyConnectionService connectionService, Phone phone, int videoState,
                String[] participants) {
        this(connToHold, connToDial, connectionService, phone, videoState);
        mParticipants = participants;
        mIsConference = true;
    }

    private HoldAndDialHandler(TelephonyConnection connToHold, TelephonyConnection connToDial,
                TelephonyConnectionService connectionService, Phone phone, int videoState) {
        mConnToDial = connToDial;
        mConnToHold = connToHold;
        mConnectionService = connectionService;
        mPhone = phone;
        mVideoState = videoState;
        mConnToHold.addTelephonyConnectionListener(this);
    }

    // Holds ACTIVE call and invokes dial to get a pending connection
    @Override
    public com.android.internal.telephony.Connection dial() throws CallStateException {
        // DeferDial is enabled which means that ImsPhoneCallTracker will only return a pending
        // connection without placing the call. Call will be placed after hold completes and
        // DeferDial is disabled
        try {
            mOriginalConnection = (mIsConference ? startConferenceInternal(DeferDial.ENABLE) :
                    dialInternal(DeferDial.ENABLE));
            holdInternal();
            return mOriginalConnection;
        } catch (CallStateException e) {
            onCompleted(false);
            throw e;
        }
    }

    private void holdInternal() {
        if (mConnToHold.getState() != Connection.STATE_HOLDING) {
            mConnToHold.onHold();
        }
    }

    private void onCompleted(boolean success) {
        cleanup();
        notifyOnCompleted(success);
    }

    private void cleanup() {
        mConnToHold.removeTelephonyConnectionListener(this);
        mConnToHold = null;
        mConnToDial = null;
    }

    private com.android.internal.telephony.Connection dialInternal(DeferDial deferDial)
            throws CallStateException {
        Log.d(this, "HoldAndDialHandler dialInternal deferDial = " + deferDial);
        String number = (mConnToDial.getAddress() != null)
                ? mConnToDial.getAddress().getSchemeSpecificPart()
                : "";
        com.android.internal.telephony.Connection originalConnection =
                mPhone.dial(number, new ImsPhone.ImsDialArgs.Builder()
                        .setVideoState(mVideoState)
                        .setIntentExtras(mExtras)
                        .setRttTextStream(mConnToDial.getRttTextStream())
                        .setDeferDial(deferDial)
                        .build());
        if (originalConnection == null) {
            // TODO:MMI use case
            Log.d(this, "HoldAndDialHandler originalconnection = null");
        }
        return originalConnection;
    }

    private com.android.internal.telephony.Connection startConferenceInternal(DeferDial deferDial)
            throws CallStateException {
        Log.d(this, "HoldAndDialHandler startConferenceInternal deferDial = " + deferDial);
        com.android.internal.telephony.Connection originalConnection = mPhone.startConference(
                mParticipants, new ImsPhone.ImsDialArgs.Builder()
                        .setVideoState(mVideoState)
                        .setRttTextStream(mConnToDial.getRttTextStream())
                        .setDeferDial(deferDial)
                        .build());
        return originalConnection;
    }

    private void handleDialException(CallStateException e, String msg) {
        Log.e(this, e, msg + " exception: " + e);
        mConnectionService.handleCallStateException(e, mConnToDial, mPhone);
        onCompleted(false);
    }

    @Override
    public void onStateChanged(android.telecom.Connection c, int state) {
        Log.d(this, "onStateChanged state = " + state);
        if (c != mConnToHold || !(state == Connection.STATE_HOLDING ||
                state == Connection.STATE_DISCONNECTED)) {
            return;
        }
        if (mConnToDial.getState() == Connection.STATE_DISCONNECTED) {
            // Pending MO call was hung up
            onCompleted(false);
            return;
        }
        try {
            com.android.internal.telephony.Connection originalConnection =
                    (mIsConference ? startConferenceInternal(DeferDial.DISABLE) :
                            dialInternal(DeferDial.DISABLE));
            if (mOriginalConnection != originalConnection) {
                // Safe check. This should not happen
                Log.e(this, null,
                        "original connection is different " + mOriginalConnection
                                + " " + originalConnection);
                mConnToDial.setTelephonyConnectionDisconnected(
                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.OUTGOING_FAILURE,
                                "original connection is different", mPhone.getPhoneId()));
                // This clears mOriginalConnection as that is the on associated with
                // TelephonyConnection. originalConnection will not be cleared
                mConnToDial.close();
                onCompleted(false);
                return;
            }
            onCompleted(true);
        } catch (CallStateException e) {
            handleDialException(e, "conference failed");
        }
    }

    @Override
    public void onConnectionEvent(Connection c, String event, Bundle extras) {
        if (c != mConnToHold) return;
        if (event == android.telecom.Connection.EVENT_CALL_HOLD_FAILED) {
            // Disconnect dialing call
            ImsPhoneConnection conn = (ImsPhoneConnection)mOriginalConnection;
            //set cause similar to same sub hold fail case
            conn.setDisconnectCause(DisconnectCause.ERROR_UNSPECIFIED);
            mConnToDial.onDisconnect();
            onCompleted(false);
        }
    }
}