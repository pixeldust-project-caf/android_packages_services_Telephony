/*
 * Copyright (c) 2021, The Linux Foundation. All rights reserved.
 *
 * Not a Contribution.
 *
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.phone;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.UserHandle;
import android.sysprop.TelephonyProperties;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.util.NotificationChannelController;

import java.text.SimpleDateFormat;

/**
 * Application service that inserts/removes SMS Callback Mode notification and
 * updates SMS Callback Mode countdown clock in the notification
 *
 * @see SmsCallbackModeExitDialog
 */
public class SmsCallbackModeService extends Service {

    private static final String LOG_TAG = "SmsCallbackModeService";

    private NotificationManager mNotificationManager = null;
    private long mTime = 0;
    private Phone mPhone = null;

    /**
     * Intent action broadcasted when Sms Callback Mode changed.
     */
    public static final String ACTION_SMS_CALLBACK_MODE_CHANGED =
            "org.codeaurora.intent.action.SMS_CALLBACK_MODE_CHANGED";
    /**
     * Extra included in {@link #ACTION_SMS_CALLBACK_MODE_CHANGED}.
     * Indicates whether the phone is in an sms callback mode.
     */
    public static final String EXTRA_PHONE_IN_SCM_STATE =
            "org.codeaurora.extra.PHONE_IN_SCM_STATE";

    /**
     * Intent broadcasted to indicate the sms callback mode blocks
     * datacall/sms.
     */
    public static final String ACTION_SHOW_NOTICE_SCM_BLOCK_OTHERS =
            "org.codeaurora.intent.action.SHOW_NOTICE_SCM_BLOCK_OTHERS";

    @Override
    public void onCreate() {
        mPhone = PhoneFactory.getDefaultPhone();

        // Register receiver for intents
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SMS_CALLBACK_MODE_CHANGED);
        registerReceiver(mScmReceiver, filter);

        mNotificationManager = getSystemService(NotificationManager.class);
        showNotification();
    }

    @Override
    public void onDestroy() {
        if (mPhone != null) {
            // Unregister receiver
            unregisterReceiver(mScmReceiver);
            // Cancel the notification and timer
            mNotificationManager.cancelAsUser(null, R.string.phone_in_scm_notification_title,
                    UserHandle.ALL);
        }
    }

    /**
     * Listens for SMS Callback Mode intents
     */
    private BroadcastReceiver mScmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Stop the service when phone exits SMS Callback Mode
            if (intent.getAction().equals(ACTION_SMS_CALLBACK_MODE_CHANGED)) {
                if (!intent.getBooleanExtra(EXTRA_PHONE_IN_SCM_STATE, false)) {
                    stopSelf();
                }
            }
        }
    };

    /**
     * Shows notification for Sms Callback Mode
     */
    private void showNotification() {
        boolean isInScm = mPhone.isInScbm();
        if (!isInScm) {
            Log.i(LOG_TAG, "Asked to show notification but not in SCM mode");
            return;
        }
        final Notification.Builder builder = new Notification.Builder(getApplicationContext());
        builder.setOngoing(true);
        builder.setPriority(Notification.PRIORITY_HIGH);
        builder.setSmallIcon(R.drawable.ic_emergency_callback_mode);
        builder.setTicker(getText(R.string.phone_entered_scm_text));
        builder.setContentTitle(getText(R.string.phone_in_scm_notification_title));
        builder.setColor(getResources().getColor(R.color.dialer_theme_color));

        // PendingIntent to launch Sms Callback Mode Exit activity if the user selects
        // this notification
        Intent intent = new Intent(this, SmsCallbackModeExitDialog.class);
        intent.setAction(SmsCallbackModeExitDialog.ACTION_SHOW_SCM_EXIT_DIALOG);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        builder.setContentIntent(contentIntent);

        // Format notification string
        String text = null;
        // Calculate the time in ms when the notification will be finished.
        mTime = System.currentTimeMillis();
        String completeTime = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT).format(
                mTime);
        text = getResources().getString(
               R.string.phone_in_scm_notification_complete_time,
               completeTime);
        builder.setContentText(text);
        builder.setChannelId(NotificationChannelController.CHANNEL_ID_ALERT);

        // Show notification
        mNotificationManager.notifyAsUser(null, R.string.phone_in_scm_notification_title,
                builder.build(), UserHandle.ALL);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // This is the object that receives interactions from clients.
    private final IBinder mBinder = new LocalBinder();

    /**
     * Class for clients to access
     */
    public class LocalBinder extends Binder {
        SmsCallbackModeService getService() {
            return SmsCallbackModeService.this;
        }
    }

}
