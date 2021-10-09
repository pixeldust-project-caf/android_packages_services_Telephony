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
import android.telecom.VideoProfile;

import com.android.ims.internal.ConferenceParticipant;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/* Handles answering an incoming call when there is an ACTIVE call on the other sub */
public class HoldAndAnswerHandler extends HoldHandlerBase {
    private TelephonyConnection mConnToAnswer = null;
    int mVideoState;

    public HoldAndAnswerHandler(TelephonyConnection connToHold, TelephonyConnection connToAnswer,
                                int videoState) {
        mConnToAnswer = connToAnswer;
        mConnToHold = connToHold;
        mVideoState = videoState;
        mConnToHold.addTelephonyConnectionListener(this);
    }

    // Holds ACTIVE call
    @Override
    public void accept() {
        Log.i(this, "hold " + mConnToHold.getTelecomCallId() + " to answer " +
                mConnToAnswer.getTelecomCallId());
        mConnToHold.onHold();
    }

    private void cleanup() {
        mConnToHold.removeTelephonyConnectionListener(this);
        mConnToHold = null;
        mConnToAnswer = null;
    }

    private void onHoldFail() {
        cleanup();
        notifyOnCompleted(false);
    }

    private void onHoldSuccess() {
        // Make sure waiting call still exists
        if (mConnToAnswer != null && mConnToAnswer.getState() == Connection.STATE_RINGING) {
            if (mVideoState == VideoProfile.STATE_AUDIO_ONLY) {
                mConnToAnswer.onAnswer();
            } else {
                mConnToAnswer.onAnswer(mVideoState);
            }
        }
        cleanup();
        notifyOnCompleted(true);
    }

    @Override
    public void onStateChanged(android.telecom.Connection c, int state) {
        Log.d(this, "onStateChanged state = " + state);
        // We are ready to answer if the connection pending HOLD is successfully held or
        // got disconnected
        if (c == mConnToHold &&
                (state == Connection.STATE_HOLDING || state == Connection.STATE_DISCONNECTED)) {
            onHoldSuccess();
        }
    }

    @Override
    public void onConnectionEvent(Connection c, String event, Bundle extras) {
        Log.d(this, "onConnectionEvent event = " + event);
        if (c == mConnToHold && event ==
                android.telecom.Connection.EVENT_CALL_HOLD_FAILED) {
            //If hold fails, incoming call will remain ringing
            onHoldFail();
        }
    }
}