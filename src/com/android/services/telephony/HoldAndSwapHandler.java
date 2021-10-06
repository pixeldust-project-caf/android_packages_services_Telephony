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
import android.telecom.StatusHints;

import com.android.ims.internal.ConferenceParticipant;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/* Handles DSDA across sub call swap use case */
public class HoldAndSwapHandler extends HoldHandlerBase {
    private TelephonyConnection mConnToResume = null;

    public HoldAndSwapHandler(TelephonyConnection connToHold, TelephonyConnection connToResume) {
        mConnToResume = connToResume;
        mConnToHold = connToHold;
        mConnToHold.addTelephonyConnectionListener(this);
    }

    // Holds active call
    @Override
    public void accept() {
        Log.i(this, "hold " + mConnToHold.getTelecomCallId() + " resume " +
                mConnToResume.getTelecomCallId());
        mConnToHold.onHold();
    }

    private void cleanup() {
        if (mConnToHold != null) {
            mConnToHold.removeTelephonyConnectionListener(this);
            mConnToHold = null;
        }
        if (mConnToResume != null) {
            mConnToResume.removeTelephonyConnectionListener(this);
            mConnToResume = null;
        }
    }

    private void onFail() {
        cleanup();
        notifyOnCompleted(false);
    }

    private void onSuccess() {
        cleanup();
        notifyOnCompleted(true);
    }

    private void revertPreviousHold() {
        // mConnToResume got terminated in the middle of a swap after the other call
        // was held. So resume other call as swap failed
        if (mConnToResume != null) {
            mConnToResume.removeTelephonyConnectionListener(this);
        }
        if (mConnToHold == null) {
            onFail();
        } else if (mConnToHold.getState() == Connection.STATE_HOLDING) {
            mConnToResume = mConnToHold;
            mConnToHold = null;
            mConnToResume.addTelephonyConnectionListener(this);
            mConnToResume.onUnhold();
        }
    }

    @Override
    public void onStateChanged(android.telecom.Connection c, int state) {
        if (c == null) return;
        if (c.equals(mConnToHold)) {
            Log.d(this,"onStateChanged callToHold state = " + state);
            if (state == Connection.STATE_HOLDING || state == Connection.STATE_DISCONNECTED) {
                mConnToHold.removeTelephonyConnectionListener(this);
                if (mConnToResume.getState() == Connection.STATE_HOLDING) {
                    mConnToResume.addTelephonyConnectionListener(this);
                    mConnToResume.onUnhold();
                }// Here we could possibly check for mConnToResume == DISCONNECTED and unhold
                // mConnToHold but that is not in sync with ImsPhoneCallTracker handling
            }
        } else if (c.equals(mConnToResume)) {
            Log.d(this,"onStateChanged callToResume state = " + state);
            switch (state) {
                case Connection.STATE_DISCONNECTED:
                    revertPreviousHold();
                    break;
                case Connection.STATE_ACTIVE:
                    onSuccess();
                    break;
                default:
                    // Do nothing for other events
            }
        }
    }

    @Override
    public void onConnectionEvent(Connection c, String event, Bundle extras) {
        switch (event) {
            case android.telecom.Connection.EVENT_CALL_HOLD_FAILED:
                onFail();
                break;
            case android.telecom.Connection.EVENT_CALL_RESUME_FAILED://Add new event
                revertPreviousHold();
                break;
            default:
                // Do nothing for other events
        }
    }
}