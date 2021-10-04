/*
 * Copyright (c) 2020-2021, The Linux Foundation. All rights reserved.

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

import android.net.Uri;
import android.os.Bundle;
import android.telecom.Conference;
import android.telecom.Conferenceable;
import android.telecom.Connection;
import android.telecom.Connection.VideoProvider;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;

import com.android.ims.internal.ConferenceParticipant;
import com.android.internal.telephony.CallStateException;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/* Base class that handles across sub HOLD use cases for DSDA */
public class HoldHandlerBase extends TelephonyConnection.TelephonyConnectionListener {
    private List<Listener> mListeners = new CopyOnWriteArrayList<>();
    protected TelephonyConnection mConnToHold = null;

    public interface Listener {
        // status true indicates operation succeeded, false indicates failure
        void onCompleted(boolean status);
    }

    public void addListener(HoldHandlerBase.Listener listener) {
        mListeners.add(listener);
    }

    public void removeListener(HoldHandlerBase.Listener listener) {
        mListeners.remove(listener);
    }

    protected void notifyOnCompleted(boolean status) {
        for (HoldHandlerBase.Listener l : mListeners) {
            l.onCompleted(status);
        }
    }

    public void accept() {}
    public com.android.internal.telephony.Connection dial() throws CallStateException {
        return null;
    }
    public void onOriginalConnectionConfigured(TelephonyConnection c) {}
    public void onOriginalConnectionRetry(TelephonyConnection c, boolean isPermanentFailure) {}
    public void onConferenceParticipantsChanged(Connection c,
                                                List<ConferenceParticipant> participants) {}
    public void onConferenceStarted() {}
    public void onConferenceSupportedChanged(Connection c, boolean isConferenceSupported) {}

    public void onConnectionCapabilitiesChanged(Connection c, int connectionCapabilities) {}
    public void onConnectionEvent(Connection c, String event, Bundle extras) {}
    public void onConnectionPropertiesChanged(Connection c, int connectionProperties) {}
    public void onExtrasChanged(Connection c, Bundle extras) {}
    public void onExtrasRemoved(Connection c, List<String> keys) {}
    public void onStateChanged(android.telecom.Connection c, int state) {}
    public void onStatusHintsChanged(Connection c, StatusHints statusHints) {}
    public void onDestroyed(Connection c) {}
    public void onDisconnected(android.telecom.Connection c,
                               android.telecom.DisconnectCause disconnectCause) {}
    public void onVideoProviderChanged(android.telecom.Connection c,
                                       Connection.VideoProvider videoProvider) {}
    public void onVideoStateChanged(android.telecom.Connection c, int videoState) {}
    public void onRingbackRequested(Connection c, boolean ringback) {}
}
