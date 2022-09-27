/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* Changes from Qualcomm Innovation Center are provided under the following license:
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted (subject to the limitations in the
 * disclaimer below) provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *
 *   * Neither the name of Qualcomm Innovation Center, Inc. nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
 * GRANTED BY THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.phone;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.provider.Settings;
import android.R.drawable;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * Displays a notification that allows users to disable Backup Calling
 */
public class CiWlanNotificationReceiver extends BroadcastReceiver {

    private static final String TAG = "CiWlanNotificationReceiver";

    private final String C_IWLAN_NOTIFICATION_STATUS = "C_IWLAN_NOTIFICATION_STATUS";
    private final String C_IWLAN_NOTIFICATION_PHONE_ID = "C_IWLAN_NOTIFICATION_PHONE_ID";
    private final String C_IWLAN_NOTIFICATION_CHANNEL_ID = "C_IWLAN_NOTIFICATION_CHANNEL_ID";

    private final String ACTION_DISABLE_C_IWLAN_NOTIFICATION =
            "com.qti.phone.action.ACTION_DISABLE_C_IWLAN_NOTIFICATION";

    private static final String EXTRA_STATE = "state";
    private static final String ACTION_RADIO_POWER_STATE_CHANGED =
            "org.codeaurora.intent.action.RADIO_POWER_STATE";

    private NotificationManager mNotificationManager;

    @Override
    public void onReceive(Context context, Intent intent) {
        mNotificationManager = context.getSystemService(NotificationManager.class);
        switch (intent.getAction()) {
            case ACTION_DISABLE_C_IWLAN_NOTIFICATION:
                boolean show = intent.getBooleanExtra(C_IWLAN_NOTIFICATION_STATUS, false);
                int phoneId = intent.getIntExtra(C_IWLAN_NOTIFICATION_PHONE_ID,
                        SubscriptionManager.INVALID_PHONE_INDEX);
                Log.d(TAG, "ACTION_DISABLE_C_IWLAN_NOTIFICATION: " + show + " phoneId: " + phoneId);
                toggleNotification(show, phoneId, context.getApplicationContext());
                break;
            case ACTION_RADIO_POWER_STATE_CHANGED:
                Log.d(TAG, "ACTION_RADIO_POWER_STATE_CHANGED");
                int radioStateExtra = intent.getIntExtra(EXTRA_STATE,
                        TelephonyManager.RADIO_POWER_UNAVAILABLE);
                handleRadioPowerStateChanged(radioStateExtra);
                break;
            default:
                Log.e(TAG, "Unsupported action");
        }
    }

    private void handleRadioPowerStateChanged(int radioState) {
        // Hide the notification if radio becomes unavailable
        if (radioState == TelephonyManager.RADIO_POWER_UNAVAILABLE) {
            Log.d(TAG, "Cancelling all notifications");
            mNotificationManager.cancelAll();
        }
    }

    private void toggleNotification(boolean show, int phoneId, Context context) {
        if (phoneId == SubscriptionManager.INVALID_PHONE_INDEX) {
            Log.e(TAG, "Invalid phoneId");
            return;
        }
        if (show) {
            showNotification(context, phoneId);
        } else {
            dismissNotification(context, phoneId);
        }
    }

    private void showNotification(Context context, int phoneId) {
        Log.d(TAG, "showNotification phoneId: " + phoneId);

        Resources resources = context.getResources();

        createNotificationChannel(resources);

        // Build the positive button that launches the UI to disable C_IWLAN
        Intent ciwlanIntent = new Intent(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, ciwlanIntent,
                PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Action ciwlanSettingAction = new NotificationCompat.Action.Builder(0,
                resources.getString(R.string.c_iwlan_exit_notification_positive_button),
                pendingIntent).build();

        // Build the negative button that dismisses the notification
        Intent dismissIntent = new Intent(ACTION_DISABLE_C_IWLAN_NOTIFICATION);
        dismissIntent.putExtra(C_IWLAN_NOTIFICATION_STATUS, false);
        dismissIntent.putExtra(C_IWLAN_NOTIFICATION_PHONE_ID, phoneId);
        dismissIntent.setComponent(new ComponentName(context.getPackageName(),
                CiWlanNotificationReceiver.class.getName()));
        // For the 2nd parameter, the requestCode, we are passing in the phoneId to differentiate
        // between the pending intents for the different subs. If we pass the same number for this
        // parameter, the extras for the latest pending intent will override the previous one.
        pendingIntent = PendingIntent.getBroadcast(context, phoneId, dismissIntent,
                PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Action dismissAction = new NotificationCompat.Action.Builder(0,
                resources.getString(R.string.c_iwlan_exit_notification_negative_button),
                pendingIntent).build();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                C_IWLAN_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(resources.getString(R.string.c_iwlan_exit_notification_title))
                .setContentText(resources.getString(
                        R.string.c_iwlan_exit_notification_description))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setOngoing(true)
                .addAction(ciwlanSettingAction)
                .addAction(dismissAction);

        // The 1st argument to notify, the notification tag, will be used to differentiate between
        // the notifications for different subs in the multi-sim case
        mNotificationManager.notify(Integer.toString(phoneId),
                NotificationMgr.BACKUP_CALLING_NOTIFICATION, builder.build());
    }

    private void dismissNotification(Context context, int phoneId) {
        Log.d(TAG, "dismissNotification phoneId: " + phoneId);
        mNotificationManager.cancel(Integer.toString(phoneId),
                NotificationMgr.BACKUP_CALLING_NOTIFICATION);
    }

    private void createNotificationChannel(Resources r) {
        CharSequence name = r.getString(R.string.c_iwlan_channel_name);
        NotificationChannel channel = new NotificationChannel(C_IWLAN_NOTIFICATION_CHANNEL_ID, name,
                NotificationManager.IMPORTANCE_HIGH);
        mNotificationManager.createNotificationChannel(channel);
    }
}