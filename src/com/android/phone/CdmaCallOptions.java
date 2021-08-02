/*
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MenuItem;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.SubscriptionController;

import java.util.List;

public class CdmaCallOptions extends TimeConsumingPreferenceActivity
               implements DialogInterface.OnClickListener,
               DialogInterface.OnCancelListener {
    private static final String LOG_TAG = "CdmaCallOptions";

    public static final int CALL_WAITING = 7;
    private static final String BUTTON_VP_KEY = "button_voice_privacy_key";
    private static final String CALL_FORWARDING_KEY = "call_forwarding_key";
    private static final String CALL_WAITING_KEY = "call_waiting_key";
    public static final String CALL_FORWARD_INTENT = "org.codeaurora.settings.CDMA_CALL_FORWARDING";
    public static final String CALL_WAITING_INTENT = "org.codeaurora.settings.CDMA_CALL_WAITING";

    private CallWaitingSwitchPreference mCWButton;
    private PreferenceScreen mPrefCW;
    private boolean mUtEnabled = false;
    private boolean mCommon = false;
    private Phone mPhone = null;
    private boolean mCdmaCfCwEnabled = false;
    private static final String BUTTON_CW_KEY = "button_cw_ut_key";

    private static boolean isActivityPresent(Context context, String intentName) {
        PackageManager pm = context.getPackageManager();
        // check whether the target handler exist in system
        Intent intent = new Intent(intentName);
        List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
        for (ResolveInfo resolveInfo : list){
            if ((resolveInfo.activityInfo.applicationInfo.flags &
                    ApplicationInfo.FLAG_SYSTEM) != 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCdmaCallForwardingActivityPresent(Context context) {
        return isActivityPresent(context, CALL_FORWARD_INTENT);
    }

    public static boolean isCdmaCallWaitingActivityPresent(Context context) {
        return isActivityPresent(context, CALL_WAITING_INTENT);
    }

    //prompt dialog to notify user turn off Enhance 4G LTE switch
    private boolean isPromptTurnOffEnhance4GLTE(Phone phone) {
        if (phone == null || phone.getImsPhone() == null) {
            return false;
        }

        com.android.ims.ImsManager imsMgr = com.android.ims.ImsManager.getInstance(this, phone.getPhoneId());
        try {
            if (imsMgr.getImsServiceState() != ImsFeature.STATE_READY) {
                Log.d(LOG_TAG, "ImsServiceStatus is not ready!");
                return false;
            }
        } catch (com.android.ims.ImsException ex) {
            Log.d(LOG_TAG, "Exception when trying to get ImsServiceStatus: " + ex);
            return false;
        }

        return imsMgr.isEnhanced4gLteModeSettingEnabledByUser()
            && imsMgr.isNonTtyOrTtyOnVolteEnabled()
            && !phone.isUtEnabled()
            && !phone.isVolteEnabled()
            && !phone.isVideoEnabled();
    }

    /*
     * Some operators ask to prompt user to switch DDS to sub which query CF/CW over UT
     */
    private  boolean maybePromptUserToSwitchDds() {
        // check the active data sub.
        int sub = mPhone.getSubId();
        final SubscriptionManager subMgr = SubscriptionManager.from(this);
        int slotId = subMgr.getSlotIndex(sub);
        int defaultDataSub = subMgr.getDefaultDataSubscriptionId();
        Log.d(LOG_TAG, "isUtEnabled = " + mPhone.isUtEnabled() + ", need to check DDS ");
        if (mPhone != null && sub != defaultDataSub && !mPhone.isUtEnabled()) {
            Log.d(LOG_TAG, "Show dds switch dialog if data sub is not on current sub");
            showSwitchDdsDialog(slotId);
            return true;
        }
        return false;
    }

    private void showSwitchDdsDialog(int slotId) {
        String title = (String)this.getResources().getText(R.string.no_mobile_data);
        int simId = slotId + 1;
        String message = (String)this.getResources()
            .getText(R.string.switch_dds_to_sub_alert_msg) + String.valueOf(simId);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent newIntent = new Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS);
                newIntent.putExtra(Settings.EXTRA_SUB_ID,mPhone.getSubId());
                newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(newIntent);
                finish();
            }
        });
        builder.setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                finish();
            }
        });
        builder.create().show();
    }

    private void showAlertDialog(String title, String message) {
        Dialog dialog = new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setIconAttribute(android.R.attr.alertDialogIcon)
            .setPositiveButton(android.R.string.ok, this)
            .setNegativeButton(android.R.string.cancel, this)
            .setOnCancelListener(this)
            .create();
        dialog.show();
    }

    @Override
    public void onClick(DialogInterface dialog, int id) {
        if (id == DialogInterface.BUTTON_POSITIVE) {
            Intent newIntent = new Intent("android.settings.SETTINGS");
            newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(newIntent);
        }
        finish();
        return;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        finish();
        return;
    }

    private class UtCallback extends ImsMmTelManager.CapabilityCallback {
        @Override
        public void onCapabilitiesStatusChanged(MmTelFeature.MmTelCapabilities capabilities) {
            boolean isUtAvailable = capabilities.isCapable(
                    MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_UT);
            updatePreferencesEnabled(isUtAvailable);
        }
    }

    private Preference mCallForwardingPref;
    private CdmaCallWaitingPreference mCallWaitingPref;
    private UtCallback mUtCallback;
    private ImsMmTelManager mMmTelManager;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.cdma_call_privacy);

        SubscriptionInfoHelper subInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        PersistableBundle carrierConfig;
        int subId;
        if (subInfoHelper.hasSubId()) {
            subId = subInfoHelper.getSubId();
        } else {
            subId = SubscriptionManager.getDefaultSubscriptionId();
        }
        carrierConfig = PhoneGlobals.getInstance().getCarrierConfigForSubId(subId);
        mCommon = carrierConfig.getBoolean("config_common_callsettings_support_bool");
        subInfoHelper.setActionBarTitle(
                getActionBar(), getResources(),
                mCommon ? R.string.labelCommonMore_with_label : R.string.labelCdmaMore_with_label);

        CdmaVoicePrivacySwitchPreference buttonVoicePrivacy =
            (CdmaVoicePrivacySwitchPreference) findPreference(BUTTON_VP_KEY);
        mPhone = subInfoHelper.getPhone();
        buttonVoicePrivacy.setPhone(mPhone);
        Log.d(LOG_TAG, "sub id = " + subInfoHelper.getSubId() + " phone id = " +
                mPhone.getPhoneId());

        mCdmaCfCwEnabled = carrierConfig
            .getBoolean(CarrierConfigManager.KEY_CDMA_CW_CF_ENABLED_BOOL);
        PreferenceScreen prefScreen = getPreferenceScreen();
        if (mPhone.getPhoneType() != PhoneConstants.PHONE_TYPE_CDMA ||
                carrierConfig.getBoolean(CarrierConfigManager.KEY_VOICE_PRIVACY_DISABLE_UI_BOOL)) {
            CdmaVoicePrivacySwitchPreference prefPri = (CdmaVoicePrivacySwitchPreference)
                    prefScreen.findPreference("button_voice_privacy_key");
            if (prefPri != null) {
                prefPri.setEnabled(false);
            }
        }

        if(carrierConfig.getBoolean("check_mobile_data_for_cf") && maybePromptUserToSwitchDds()) {
            return;
        }
        if(mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA
                && isPromptTurnOffEnhance4GLTE(mPhone)
                && carrierConfig.getBoolean(CarrierConfigManager.KEY_CDMA_CW_CF_ENABLED_BOOL)) {
            String title = (String)this.getResources()
                .getText(R.string.ut_not_support);
            String msg = (String)this.getResources()
                .getText(R.string.ct_ut_not_support_close_4glte);
            showAlertDialog(title, msg);
        }

        mCWButton = (CallWaitingSwitchPreference) prefScreen.findPreference(BUTTON_CW_KEY);
        if (mPhone.getPhoneType() != PhoneConstants.PHONE_TYPE_CDMA
                || !carrierConfig.getBoolean(CarrierConfigManager.KEY_CDMA_CW_CF_ENABLED_BOOL)
                || !isCdmaCallWaitingActivityPresent(this)) {
            Log.d(LOG_TAG, "Disabled CW CF");
            mPrefCW = (PreferenceScreen) prefScreen.findPreference("button_cw_key");
            if (mCWButton != null) {
                 prefScreen.removePreference(mCWButton);
            }

            if (mPrefCW != null) {
                mPrefCW.setEnabled(false);
            }
            PreferenceScreen prefCF = (PreferenceScreen)
                    prefScreen.findPreference("button_cf_expand_key");
            if (prefCF != null) {
                prefCF.setEnabled(false);
            }
        } else {
            Log.d(LOG_TAG, "Enabled CW CF");
            mPrefCW = (PreferenceScreen) prefScreen.findPreference("button_cw_key");

            com.android.ims.ImsManager imsMgr = com.android.ims.ImsManager.getInstance(this, mPhone.getPhoneId());
            Boolean isEnhanced4G = imsMgr.isEnhanced4gLteModeSettingEnabledByUser();
            if (mPhone.isUtEnabled() && isEnhanced4G) {
                mUtEnabled = mPhone.isUtEnabled();
                prefScreen.removePreference(mPrefCW);
                mCWButton.init(this, false, mPhone);
            } else {
                if (mCWButton != null) {
                    prefScreen.removePreference(mCWButton);
                }
                if (mPrefCW != null) {
                    mPrefCW.setOnPreferenceClickListener(
                            new Preference.OnPreferenceClickListener() {
                                @Override
                                public boolean onPreferenceClick(Preference preference) {
                                    Intent intent = new Intent(CALL_WAITING_INTENT);
                                    intent.putExtra(
                                        SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                                        mPhone.getSubId());
                                    startActivity(intent);
                                    return true;
                                }
                            });
                }
            }
            PreferenceScreen prefCF = (PreferenceScreen)
                    prefScreen.findPreference("button_cf_expand_key");
            if (prefCF != null) {
                prefCF.setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(Preference preference) {
                                Intent intent = mPhone.isUtEnabled() ?
                                    subInfoHelper.getIntent(GsmUmtsCallForwardOptions.class)
                                    : new Intent(CALL_FORWARD_INTENT);
                                if (mPhone.isUtEnabled()) {
                                    intent.putExtra(PhoneUtils.SERVICE_CLASS,
                                        CommandsInterface.SERVICE_CLASS_VOICE);
                                } else {
                                    intent.putExtra(
                                        SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                                        mPhone.getSubId());
                                }
                                startActivity(intent);
                                return true;
                            }
                        });
            }
        }

        mCallForwardingPref = getPreferenceScreen().findPreference(CALL_FORWARDING_KEY);
        if (carrierConfig != null && carrierConfig.getBoolean(
                CarrierConfigManager.KEY_CALL_FORWARDING_VISIBILITY_BOOL)) {
            mCallForwardingPref.setIntent(
                    subInfoHelper.getIntent(CdmaCallForwardOptions.class));
        } else {
            getPreferenceScreen().removePreference(mCallForwardingPref);
            mCallForwardingPref = null;
        }

        mCallWaitingPref = (CdmaCallWaitingPreference) getPreferenceScreen()
                .findPreference(CALL_WAITING_KEY);
        if (carrierConfig == null || !carrierConfig.getBoolean(
                CarrierConfigManager.KEY_ADDITIONAL_SETTINGS_CALL_WAITING_VISIBILITY_BOOL)) {
            getPreferenceScreen().removePreference(mCallWaitingPref);
            mCallWaitingPref = null;
        }
        // Do not go further if the preferences are removed.
        if (mCallForwardingPref == null && mCallWaitingPref == null) return;

        boolean isSsOverCdmaEnabled = carrierConfig != null && carrierConfig.getBoolean(
                CarrierConfigManager.KEY_SUPPORT_SS_OVER_CDMA_BOOL);
        boolean isSsOverUtEnabled = carrierConfig != null && carrierConfig.getBoolean(
                CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL);

        if (isSsOverCdmaEnabled && mCallWaitingPref != null) {
            // If SS over CDMA is enabled, then the preference will always be enabled,
            // independent of SS over UT status. Initialize it now.
            mCallWaitingPref.init(this, subInfoHelper.getPhone());
            return;
        }
        // Since SS over UT availability can change, first disable the preferences that rely on it
        // and only enable it if UT is available.
        updatePreferencesEnabled(false);
        if (isSsOverUtEnabled) {
            // Register a callback to listen to SS over UT state. This will enable the preferences
            // once the callback notifies settings that UT is enabled.
            registerMmTelCapsCallback(subId);
        } else {
            Log.w(LOG_TAG, "SS over UT and CDMA disabled, but preferences are visible.");
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        unregisterMmTelCapsCallback();
    }

    private void unregisterMmTelCapsCallback() {
        if (mMmTelManager == null || mUtCallback == null) return;
        mMmTelManager.unregisterMmTelCapabilityCallback(mUtCallback);
        mUtCallback = null;
        Log.d(LOG_TAG, "unregisterMmTelCapsCallback: UT availability callback unregistered");
    }

    private void registerMmTelCapsCallback(int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) return;
        ImsManager imsManager = getSystemService(ImsManager.class);
        try {
            if (imsManager != null) {
                mUtCallback = new UtCallback();
                mMmTelManager = imsManager.getImsMmTelManager(subId);
                // Callback will call back with the state as soon as it is available.
                mMmTelManager.registerMmTelCapabilityCallback(getMainExecutor(), mUtCallback);
                Log.d(LOG_TAG, "registerMmTelCapsCallback: UT availability callback "
                        + "registered");
            } else {
                Log.w(LOG_TAG, "registerMmTelCapsCallback: couldn't get ImsManager, assuming "
                        + "UT is not available: ");
                updatePreferencesEnabled(false);
            }
        } catch (IllegalArgumentException | ImsException e) {
            Log.w(LOG_TAG, "registerMmTelCapsCallback: couldn't register callback, assuming "
                    + "UT is not available: " + e);
            updatePreferencesEnabled(false);
        }
    }

    private void updatePreferencesEnabled(boolean isEnabled) {
        Log.d(LOG_TAG, "updatePreferencesEnabled: " + isEnabled);
        if (mCallForwardingPref != null) mCallForwardingPref.setEnabled(isEnabled);

        if (mCallWaitingPref == null || mCallWaitingPref.isEnabled() == isEnabled) return;
        mCallWaitingPref.setActionAvailable(isEnabled);
        if (isEnabled) {
            SubscriptionInfoHelper subInfoHelper = new SubscriptionInfoHelper(this, getIntent());
            // kick off the normal process to populate the Call Waiting status.
            mCallWaitingPref.init(this, subInfoHelper.getPhone());
        }
    }

    @Override
    public void onFinished(Preference preference, boolean reading) {
        if (mCdmaCfCwEnabled && mUtEnabled && mPhone != null && !mPhone.isUtEnabled()) {
            if (isPromptTurnOffEnhance4GLTE(mPhone)) {
                String title = (String)this.getResources()
                    .getText(R.string.ut_not_support);
                String msg = (String)this.getResources()
                    .getText(R.string.ct_ut_not_support_close_4glte);
                showAlertDialog(title, msg);
            }
            mUtEnabled = false;
            if (mCWButton != null) {
                PreferenceScreen prefScreen = getPreferenceScreen();
                prefScreen.removePreference(mCWButton);
                prefScreen.addPreference(mPrefCW);
                if (mPrefCW != null) {
                    mPrefCW.setOnPreferenceClickListener(
                            new Preference.OnPreferenceClickListener() {
                                @Override
                                public boolean onPreferenceClick(Preference preference) {
                                    Intent intent = new Intent(CALL_WAITING_INTENT);
                                    intent.putExtra(
                                        SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                                        mPhone.getSubId());
                                    startActivity(intent);
                                    return true;
                                }
                            });
                }
            }
        }
        super.onFinished(preference, reading);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference.getKey().equals(BUTTON_VP_KEY)) {
            return true;
        }
        return false;
    }
}
