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

import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;


/**
 * Displays dialog that enables users to exit Sms Callback Mode
 *
 * @see SmsCallbackModeService
 */
public class SmsCallbackModeExitDialog extends Activity implements OnCancelListener {

    private static final String TAG = "SmsCallbackMode";

    /** Intent to trigger the Sms Callback Mode exit dialog */
    static final String ACTION_SHOW_SCM_EXIT_DIALOG =
            "org.codeaurora.intent.action.ACTION_SHOW_SCM_EXIT_DIALOG";

    public static final int EXIT_SCM_BLOCK_OTHERS = 1;
    public static final int EXIT_SCM_DIALOG = 2;
    public static final int EXIT_SCM_PROGRESS_DIALOG = 3;
    public static final int EXIT_SCM_IN_EMERGENCY_SMS_DIALOG = 4;

    AlertDialog mAlertDialog = null;
    ProgressDialog mProgressDialog = null;
    SmsCallbackModeService mService = null;
    Handler mHandler = null;
    int mDialogType = 0;
    private Phone mPhone = null;
    private boolean mIsResumed = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addPrivateFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);
        mPhone = PhoneFactory.getDefaultPhone();

        // Check if phone is in Emergency Callback Mode or
        // Exit SCBM feature supported or not. If not, exit.
        if (mPhone == null || !mPhone.isInScbm() ||
                !mPhone.isExitScbmFeatureSupported()) {
            SmsCallbackModeExitDialog.this.setResult(RESULT_OK);
            finish();
            return;
        }
        Log.i(TAG, "SCMExitDialog launched - isInScm: true" + " phone:" + mPhone);

        mHandler = new Handler();

        // Start thread that will wait for the connection completion so that it can get
        // timeout value from the service
        Thread waitForConnectionCompleteThread = new Thread(null, mTask,
                "ScmExitDialogWaitThread");
        waitForConnectionCompleteThread.start();

        // Register receiver for intent closing the dialog
        IntentFilter filter = new IntentFilter();
        filter.addAction(SmsCallbackModeService.ACTION_SMS_CALLBACK_MODE_CHANGED);
        registerReceiver(mScmExitReceiver, filter);
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsResumed = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsResumed = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(mScmExitReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver was never registered - silently ignore.
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mDialogType = savedInstanceState.getInt("DIALOG_TYPE");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("DIALOG_TYPE", mDialogType);
    }

    /**
     * Waits until bind to the service completes
     */
    private Runnable mTask = new Runnable() {
        public void run() {
            Looper.prepare();

            // Show dialog
            mHandler.post(new Runnable() {
                public void run() {
                    showSmsCallbackModeExitDialog();
                }
            });
        }
    };

    /**
     * Shows Sms Callback Mode dialog and starts countdown timer
     */
    private void showSmsCallbackModeExitDialog() {
        if (isDestroyed()) {
            Log.w(TAG, "Tried to show dialog, but activity was already finished");
            return;
        }
        if (getIntent().getAction().equals(
                SmsCallbackModeService.ACTION_SHOW_NOTICE_SCM_BLOCK_OTHERS)) {
            mDialogType = EXIT_SCM_BLOCK_OTHERS;
            showDialog(EXIT_SCM_BLOCK_OTHERS);
        } else if (getIntent().getAction().equals(ACTION_SHOW_SCM_EXIT_DIALOG)) {
            mDialogType = EXIT_SCM_DIALOG;
            showDialog(EXIT_SCM_DIALOG);
        }
    }

    /**
     * Creates dialog that enables users to exit Sms Callback Mode
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case EXIT_SCM_BLOCK_OTHERS:
            case EXIT_SCM_DIALOG:
                CharSequence text = getDialogText();
                mAlertDialog = new AlertDialog.Builder(SmsCallbackModeExitDialog.this,
                        android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setIcon(R.drawable.ic_emergency_callback_mode)
                        .setTitle(R.string.phone_in_scm_notification_title)
                        .setMessage(text)
                        .setPositiveButton(R.string.alert_dialog_yes,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,int whichButton) {
                                        // User clicked Yes. Exit Sms Callback Mode.
                                        try {
                                            mPhone.exitScbm();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                        // Show progress dialog
                                        showDialog(EXIT_SCM_PROGRESS_DIALOG);
                                    }
                                })
                        .setNegativeButton(R.string.alert_dialog_no,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        // User clicked No
                                        setResult(RESULT_CANCELED);
                                        finish();
                                    }
                                }).create();
                mAlertDialog.setOnCancelListener(this);
                return mAlertDialog;

            case EXIT_SCM_PROGRESS_DIALOG:
                mProgressDialog = new ProgressDialog(SmsCallbackModeExitDialog.this);
                mProgressDialog.setMessage(getText(R.string.progress_dialog_exiting_scm));
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                return mProgressDialog;

            default:
                return null;
        }
    }

    /**
     * Returns dialog box text with updated timeout value
     */
    private CharSequence getDialogText() {
        switch (mDialogType) {
            case EXIT_SCM_BLOCK_OTHERS:
                return getResources().getString(
                        R.string.alert_dialog_not_avaialble_in_scm);
            case EXIT_SCM_DIALOG:
                return getResources().getString(
                        R.string.alert_dialog_exit_scm);
        }
        return null;
    }

    /**
     * Closes activity when dialog is canceled
     */
    @Override
    public void onCancel(DialogInterface dialog) {
        SmsCallbackModeExitDialog.this.setResult(RESULT_CANCELED);
        finish();
    }

    /**
     * Listen for Sms Callback Mode state change intents
     */
    private BroadcastReceiver mScmExitReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Received exit Sms Callback Mode notification close all dialogs
            if (intent.getAction().equals(
                    SmsCallbackModeService.ACTION_SMS_CALLBACK_MODE_CHANGED)) {
                // Cancel if the sticky broadcast extra for whether or not we are in SCM is false.
                if (!intent.getBooleanExtra(SmsCallbackModeService.EXTRA_PHONE_IN_SCM_STATE, false)) {
                    if (mAlertDialog != null)
                        mAlertDialog.dismiss();
                    if (mProgressDialog != null)
                        mProgressDialog.dismiss();
                    SmsCallbackModeExitDialog.this.setResult(RESULT_OK);
                    finish();
                }
            }
        }
    };

}
