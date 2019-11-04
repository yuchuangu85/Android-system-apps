/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.annotation.IntDef;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.XmlResourceParser;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.sip.SipManager;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UpdateLock;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.telephony.AnomalyReporter;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.util.LocalLog;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.SettingsObserver;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.internal.telephony.TelephonyComponentFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.dataconnection.DataConnectionReasons;
import com.android.internal.telephony.dataconnection.DataConnectionReasons.DataDisallowedReasonType;
import com.android.internal.util.IndentingPrintWriter;
import com.android.phone.settings.SettingsConstants;
import com.android.phone.vvm.CarrierVvmPackageInstalledReceiver;
import com.android.services.telephony.sip.SipAccountRegistry;
import com.android.services.telephony.sip.SipUtil;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * Global state for the telephony subsystem when running in the primary
 * phone process.
 */
public class PhoneGlobals extends ContextWrapper {
    public static final String LOG_TAG = "PhoneGlobals";

    /**
     * Phone app-wide debug level:
     *   0 - no debug logging
     *   1 - normal debug logging if ro.debuggable is set (which is true in
     *       "eng" and "userdebug" builds but not "user" builds)
     *   2 - ultra-verbose debug logging
     *
     * Most individual classes in the phone app have a local DBG constant,
     * typically set to
     *   (PhoneApp.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1)
     * or else
     *   (PhoneApp.DBG_LEVEL >= 2)
     * depending on the desired verbosity.
     *
     * ***** DO NOT SUBMIT WITH DBG_LEVEL > 0 *************
     */
    public static final int DBG_LEVEL = 0;

    private static final boolean DBG =
            (PhoneGlobals.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1);
    private static final boolean VDBG = (PhoneGlobals.DBG_LEVEL >= 2);

    // Message codes; see mHandler below.
    private static final int EVENT_SIM_NETWORK_LOCKED = 3;
    private static final int EVENT_SIM_STATE_CHANGED = 8;
    private static final int EVENT_DATA_ROAMING_DISCONNECTED = 10;
    private static final int EVENT_DATA_ROAMING_CONNECTED = 11;
    private static final int EVENT_DATA_ROAMING_OK = 12;
    private static final int EVENT_UNSOL_CDMA_INFO_RECORD = 13;
    private static final int EVENT_RESTART_SIP = 14;
    private static final int EVENT_DATA_ROAMING_SETTINGS_CHANGED = 15;
    private static final int EVENT_MOBILE_DATA_SETTINGS_CHANGED = 16;

    // The MMI codes are also used by the InCallScreen.
    public static final int MMI_INITIATE = 51;
    public static final int MMI_COMPLETE = 52;
    public static final int MMI_CANCEL = 53;
    // Don't use message codes larger than 99 here; those are reserved for
    // the individual Activities of the Phone UI.

    public static final int AIRPLANE_ON = 1;
    public static final int AIRPLANE_OFF = 0;

    /**
     * Allowable values for the wake lock code.
     *   SLEEP means the device can be put to sleep.
     *   PARTIAL means wake the processor, but we display can be kept off.
     *   FULL means wake both the processor and the display.
     */
    public enum WakeState {
        SLEEP,
        PARTIAL,
        FULL
    }

    private static PhoneGlobals sMe;

    CallManager mCM;
    CallNotifier notifier;
    CallerInfoCache callerInfoCache;
    NotificationMgr notificationMgr;
    public PhoneInterfaceManager phoneMgr;
    CarrierConfigLoader configLoader;

    private Phone phoneInEcm;

    static boolean sVoiceCapable = true;

    // TODO: Remove, no longer used.
    CdmaPhoneCallState cdmaPhoneCallState;

    // The currently-active PUK entry activity and progress dialog.
    // Normally, these are the Emergency Dialer and the subsequent
    // progress dialog.  null if there is are no such objects in
    // the foreground.
    private Activity mPUKEntryActivity;
    private ProgressDialog mPUKEntryProgressDialog;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"ROAMING_NOTIFICATION_"},
            value = {
                    ROAMING_NOTIFICATION_NO_NOTIFICATION,
                    ROAMING_NOTIFICATION_CONNECTED,
                    ROAMING_NOTIFICATION_DISCONNECTED})
    public @interface RoamingNotification {}

    private static final int ROAMING_NOTIFICATION_NO_NOTIFICATION = 0;
    private static final int ROAMING_NOTIFICATION_CONNECTED       = 1;
    private static final int ROAMING_NOTIFICATION_DISCONNECTED    = 2;

    @RoamingNotification
    private int mPrevRoamingNotification = ROAMING_NOTIFICATION_NO_NOTIFICATION;

    private WakeState mWakeState = WakeState.SLEEP;

    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;
    private PowerManager.WakeLock mPartialWakeLock;
    private KeyguardManager mKeyguardManager;

    private UpdateLock mUpdateLock;

    private int mDefaultDataSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private final LocalLog mDataRoamingNotifLog = new LocalLog(50);

    // Broadcast receiver for various intent broadcasts (see onCreate())
    private final BroadcastReceiver mReceiver = new PhoneAppBroadcastReceiver();
    // Broadcast receiver for SIP based intents (see onCreate())
    private final SipReceiver mSipReceiver = new SipReceiver();

    private final CarrierVvmPackageInstalledReceiver mCarrierVvmPackageInstalledReceiver =
            new CarrierVvmPackageInstalledReceiver();

    private final SettingsObserver mSettingsObserver;

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            PhoneConstants.State phoneState;
            if (VDBG) Log.v(LOG_TAG, "event=" + msg.what);
            switch (msg.what) {
                // TODO: This event should be handled by the lock screen, just
                // like the "SIM missing" and "Sim locked" cases (bug 1804111).
                case EVENT_SIM_NETWORK_LOCKED:
                    if (getCarrierConfig().getBoolean(
                            CarrierConfigManager.KEY_IGNORE_SIM_NETWORK_LOCKED_EVENTS_BOOL)) {
                        // Some products don't have the concept of a "SIM network lock"
                        Log.i(LOG_TAG, "Ignoring EVENT_SIM_NETWORK_LOCKED event; "
                              + "not showing 'SIM network unlock' PIN entry screen");
                    } else {
                        // Normal case: show the "SIM network unlock" PIN entry screen.
                        // The user won't be able to do anything else until
                        // they enter a valid SIM network PIN.
                        Log.i(LOG_TAG, "show sim depersonal panel");
                        Phone phone = (Phone) ((AsyncResult) msg.obj).userObj;
                        IccNetworkDepersonalizationPanel.showDialog(phone);
                    }
                    break;

                case EVENT_DATA_ROAMING_DISCONNECTED:
                    notificationMgr.showDataRoamingNotification(msg.arg1, false);
                    break;

                case EVENT_DATA_ROAMING_CONNECTED:
                    notificationMgr.showDataRoamingNotification(msg.arg1, true);
                    break;

                case EVENT_DATA_ROAMING_OK:
                    notificationMgr.hideDataRoamingNotification();
                    break;

                case MMI_COMPLETE:
                    onMMIComplete((AsyncResult) msg.obj);
                    break;

                case MMI_CANCEL:
                    PhoneUtils.cancelMmiCode(mCM.getFgPhone());
                    break;

                case EVENT_SIM_STATE_CHANGED:
                    // Marks the event where the SIM goes into ready state.
                    // Right now, this is only used for the PUK-unlocking
                    // process.
                    if (msg.obj.equals(IccCardConstants.INTENT_VALUE_ICC_READY)
                            || msg.obj.equals(IccCardConstants.INTENT_VALUE_ICC_LOADED)) {
                        // when the right event is triggered and there
                        // are UI objects in the foreground, we close
                        // them to display the lock panel.
                        if (mPUKEntryActivity != null) {
                            mPUKEntryActivity.finish();
                            mPUKEntryActivity = null;
                        }
                        if (mPUKEntryProgressDialog != null) {
                            mPUKEntryProgressDialog.dismiss();
                            mPUKEntryProgressDialog = null;
                        }
                    }
                    break;

                case EVENT_UNSOL_CDMA_INFO_RECORD:
                    //TODO: handle message here;
                    break;
                case EVENT_RESTART_SIP:
                    // This should only run if the Phone process crashed and was restarted. We do
                    // not want this running if the device is still in the FBE encrypted state.
                    // This is the same procedure that is triggered in the SipIncomingCallReceiver
                    // upon BOOT_COMPLETED.
                    UserManager userManager = UserManager.get(sMe);
                    if (userManager != null && userManager.isUserUnlocked()) {
                        SipUtil.startSipService();
                    }
                    break;
                case EVENT_DATA_ROAMING_SETTINGS_CHANGED:
                case EVENT_MOBILE_DATA_SETTINGS_CHANGED:
                    updateDataRoamingStatus();
                    break;
            }
        }
    };

    public PhoneGlobals(Context context) {
        super(context);
        sMe = this;
        mSettingsObserver = new SettingsObserver(context, mHandler);
    }

    public void onCreate() {
        if (VDBG) Log.v(LOG_TAG, "onCreate()...");

        ContentResolver resolver = getContentResolver();

        // Cache the "voice capable" flag.
        // This flag currently comes from a resource (which is
        // overrideable on a per-product basis):
        sVoiceCapable =
                getResources().getBoolean(com.android.internal.R.bool.config_voice_capable);
        // ...but this might eventually become a PackageManager "system
        // feature" instead, in which case we'd do something like:
        // sVoiceCapable =
        //   getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY_VOICE_CALLS);

        if (mCM == null) {
            // Initialize AnomalyReporter early so that it can be used
            AnomalyReporter.initialize(this);

            // Inject telephony component factory if configured using other jars.
            XmlResourceParser parser = getResources().getXml(R.xml.telephony_injection);
            TelephonyComponentFactory.getInstance().injectTheComponentFactory(parser);
            // Initialize the telephony framework
            PhoneFactory.makeDefaultPhones(this);

            // Start TelephonyDebugService After the default phone is created.
            Intent intent = new Intent(this, TelephonyDebugService.class);
            startService(intent);

            mCM = CallManager.getInstance();
            for (Phone phone : PhoneFactory.getPhones()) {
                mCM.registerPhone(phone);
            }

            // Create the NotificationMgr singleton, which is used to display
            // status bar icons and control other status bar behavior.
            notificationMgr = NotificationMgr.init(this);

            // If PhoneGlobals has crashed and is being restarted, then restart.
            mHandler.sendEmptyMessage(EVENT_RESTART_SIP);

            // Create an instance of CdmaPhoneCallState and initialize it to IDLE
            cdmaPhoneCallState = new CdmaPhoneCallState();
            cdmaPhoneCallState.CdmaPhoneCallStateInit();

            // before registering for phone state changes
            mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = mPowerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, LOG_TAG);
            // lock used to keep the processor awake, when we don't care for the display.
            mPartialWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK
                    | PowerManager.ON_AFTER_RELEASE, LOG_TAG);

            mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

            // Get UpdateLock to suppress system-update related events (e.g. dialog show-up)
            // during phone calls.
            mUpdateLock = new UpdateLock("phone");

            if (DBG) Log.d(LOG_TAG, "onCreate: mUpdateLock: " + mUpdateLock);

            // Create the CallerInfoCache singleton, which remembers custom ring tone and
            // send-to-voicemail settings.
            //
            // The asynchronous caching will start just after this call.
            callerInfoCache = CallerInfoCache.init(this);

            phoneMgr = PhoneInterfaceManager.init(this);

            configLoader = CarrierConfigLoader.init(this);

            // Create the CallNotifier singleton, which handles
            // asynchronous events from the telephony layer (like
            // launching the incoming-call UI when an incoming call comes
            // in.)
            notifier = CallNotifier.init(this);

            PhoneUtils.registerIccStatus(mHandler, EVENT_SIM_NETWORK_LOCKED);

            // register for MMI/USSD
            mCM.registerForMmiComplete(mHandler, MMI_COMPLETE, null);

            // Register for misc other intent broadcasts.
            IntentFilter intentFilter =
                    new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
            intentFilter.addAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
            registerReceiver(mReceiver, intentFilter);

            IntentFilter sipIntentFilter = new IntentFilter(Intent.ACTION_BOOT_COMPLETED);
            sipIntentFilter.addAction(SipManager.ACTION_SIP_SERVICE_UP);
            sipIntentFilter.addAction(SipManager.ACTION_SIP_CALL_OPTION_CHANGED);
            sipIntentFilter.addAction(SipManager.ACTION_SIP_REMOVE_PHONE);
            registerReceiver(mSipReceiver, sipIntentFilter);

            mCarrierVvmPackageInstalledReceiver.register(this);

            //set the default values for the preferences in the phone.
            PreferenceManager.setDefaultValues(this, R.xml.network_setting_fragment, false);

            PreferenceManager.setDefaultValues(this, R.xml.call_feature_setting, false);
        }

        // XXX pre-load the SimProvider so that it's ready
        resolver.getType(Uri.parse("content://icc/adn"));

        // TODO: Register for Cdma Information Records
        // phone.registerCdmaInformationRecord(mHandler, EVENT_UNSOL_CDMA_INFO_RECORD, null);

        // Read HAC settings and configure audio hardware
        if (getResources().getBoolean(R.bool.hac_enabled)) {
            int hac = android.provider.Settings.System.getInt(
                    getContentResolver(),
                    android.provider.Settings.System.HEARING_AID,
                    0);
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioManager.setParameter(SettingsConstants.HAC_KEY,
                    hac == SettingsConstants.HAC_ENABLED
                            ? SettingsConstants.HAC_VAL_ON : SettingsConstants.HAC_VAL_OFF);
        }
    }

    /**
     * Returns the singleton instance of the PhoneApp.
     */
    public static PhoneGlobals getInstance() {
        if (sMe == null) {
            throw new IllegalStateException("No PhoneGlobals here!");
        }
        return sMe;
    }

    /**
     * Returns the default phone.
     *
     * WARNING: This method should be used carefully, now that there may be multiple phones.
     */
    public static Phone getPhone() {
        return PhoneFactory.getDefaultPhone();
    }

    public static Phone getPhone(int subId) {
        return PhoneFactory.getPhone(SubscriptionManager.getPhoneId(subId));
    }

    /* package */ CallManager getCallManager() {
        return mCM;
    }

    public PersistableBundle getCarrierConfig() {
        return getCarrierConfigForSubId(SubscriptionManager.getDefaultSubscriptionId());
    }

    public PersistableBundle getCarrierConfigForSubId(int subId) {
        return configLoader.getConfigForSubId(subId, getOpPackageName());
    }

    private void registerSettingsObserver() {
        mSettingsObserver.unobserve();
        String dataRoamingSetting = Settings.Global.DATA_ROAMING;
        String mobileDataSetting = Settings.Global.MOBILE_DATA;
        if (TelephonyManager.getDefault().getSimCount() > 1) {
            int subId = mDefaultDataSubId;
            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                dataRoamingSetting += subId;
                mobileDataSetting += subId;
            }
        }

        // Listen for user data roaming setting changed event
        mSettingsObserver.observe(Settings.Global.getUriFor(dataRoamingSetting),
                EVENT_DATA_ROAMING_SETTINGS_CHANGED);

        // Listen for mobile data setting changed event
        mSettingsObserver.observe(Settings.Global.getUriFor(mobileDataSetting),
                EVENT_MOBILE_DATA_SETTINGS_CHANGED);
    }

    /**
     * Sets the activity responsible for un-PUK-blocking the device
     * so that we may close it when we receive a positive result.
     * mPUKEntryActivity is also used to indicate to the device that
     * we are trying to un-PUK-lock the phone. In other words, iff
     * it is NOT null, then we are trying to unlock and waiting for
     * the SIM to move to READY state.
     *
     * @param activity is the activity to close when PUK has
     * finished unlocking. Can be set to null to indicate the unlock
     * or SIM READYing process is over.
     */
    void setPukEntryActivity(Activity activity) {
        mPUKEntryActivity = activity;
    }

    Activity getPUKEntryActivity() {
        return mPUKEntryActivity;
    }

    /**
     * Sets the dialog responsible for notifying the user of un-PUK-
     * blocking - SIM READYing progress, so that we may dismiss it
     * when we receive a positive result.
     *
     * @param dialog indicates the progress dialog informing the user
     * of the state of the device.  Dismissed upon completion of
     * READYing process
     */
    void setPukEntryProgressDialog(ProgressDialog dialog) {
        mPUKEntryProgressDialog = dialog;
    }

    /**
     * If we are not currently keeping the screen on, then poke the power
     * manager to wake up the screen for the user activity timeout duration.
     */
    /* package */ void wakeUpScreen() {
        synchronized (this) {
            if (mWakeState == WakeState.SLEEP) {
                if (DBG) Log.d(LOG_TAG, "pulse screen lock");
                mPowerManager.wakeUp(SystemClock.uptimeMillis(), "android.phone:WAKE");
            }
        }
    }

    KeyguardManager getKeyguardManager() {
        return mKeyguardManager;
    }

    private void onMMIComplete(AsyncResult r) {
        if (VDBG) Log.d(LOG_TAG, "onMMIComplete()...");
        MmiCode mmiCode = (MmiCode) r.result;
        PhoneUtils.displayMMIComplete(mmiCode.getPhone(), getInstance(), mmiCode, null, null);
    }

    private void initForNewRadioTechnology() {
        if (DBG) Log.d(LOG_TAG, "initForNewRadioTechnology...");
        notifier.updateCallNotifierRegistrationsAfterRadioTechnologyChange();
    }

    private void handleAirplaneModeChange(Context context, int newMode) {
        int cellState = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.CELL_ON, PhoneConstants.CELL_ON_FLAG);
        boolean isAirplaneNewlyOn = (newMode == 1);
        switch (cellState) {
            case PhoneConstants.CELL_OFF_FLAG:
                // Airplane mode does not affect the cell radio if user
                // has turned it off.
                break;
            case PhoneConstants.CELL_ON_FLAG:
                maybeTurnCellOff(context, isAirplaneNewlyOn);
                break;
            case PhoneConstants.CELL_OFF_DUE_TO_AIRPLANE_MODE_FLAG:
                maybeTurnCellOn(context, isAirplaneNewlyOn);
                break;
        }
    }

    /*
     * Returns true if the radio must be turned off when entering airplane mode.
     */
    private boolean isCellOffInAirplaneMode(Context context) {
        String airplaneModeRadios = Settings.Global.getString(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_RADIOS);
        return airplaneModeRadios == null
                || airplaneModeRadios.contains(Settings.Global.RADIO_CELL);
    }

    private void setRadioPowerOff(Context context) {
        Log.i(LOG_TAG, "Turning radio off - airplane");
        Settings.Global.putInt(context.getContentResolver(), Settings.Global.CELL_ON,
                 PhoneConstants.CELL_OFF_DUE_TO_AIRPLANE_MODE_FLAG);
        SystemProperties.set("persist.radio.airplane_mode_on", "1");
        Settings.Global.putInt(getContentResolver(), Settings.Global.ENABLE_CELLULAR_ON_BOOT, 0);
        PhoneUtils.setRadioPower(false);
    }

    private void setRadioPowerOn(Context context) {
        Log.i(LOG_TAG, "Turning radio on - airplane");
        Settings.Global.putInt(context.getContentResolver(), Settings.Global.CELL_ON,
                PhoneConstants.CELL_ON_FLAG);
        Settings.Global.putInt(getContentResolver(), Settings.Global.ENABLE_CELLULAR_ON_BOOT,
                1);
        SystemProperties.set("persist.radio.airplane_mode_on", "0");
        PhoneUtils.setRadioPower(true);
    }

    private void maybeTurnCellOff(Context context, boolean isAirplaneNewlyOn) {
        if (isAirplaneNewlyOn) {
            // If we are trying to turn off the radio, make sure there are no active
            // emergency calls.  If there are, switch airplane mode back to off.
            TelecomManager tm = (TelecomManager) context.getSystemService(TELECOM_SERVICE);

            if (tm != null && tm.isInEmergencyCall()) {
                // Switch airplane mode back to off.
                ConnectivityManager.from(this).setAirplaneMode(false);
                Toast.makeText(this, R.string.radio_off_during_emergency_call, Toast.LENGTH_LONG)
                        .show();
                Log.i(LOG_TAG, "Ignoring airplane mode: emergency call. Turning airplane off");
            } else if (isCellOffInAirplaneMode(context)) {
                setRadioPowerOff(context);
            } else {
                Log.i(LOG_TAG, "Ignoring airplane mode: settings prevent cell radio power off");
            }
        }
    }

    private void maybeTurnCellOn(Context context, boolean isAirplaneNewlyOn) {
        if (!isAirplaneNewlyOn) {
            setRadioPowerOn(context);
        }
    }

    /**
     * Receiver for misc intent broadcasts the Phone app cares about.
     */
    private class PhoneAppBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                int airplaneMode = Settings.Global.getInt(getContentResolver(),
                        Settings.Global.AIRPLANE_MODE_ON, AIRPLANE_OFF);
                // Treat any non-OFF values as ON.
                if (airplaneMode != AIRPLANE_OFF) {
                    airplaneMode = AIRPLANE_ON;
                }
                handleAirplaneModeChange(context, airplaneMode);
            } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                // re-register as it may be a new IccCard
                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                        SubscriptionManager.INVALID_PHONE_INDEX);
                if (SubscriptionManager.isValidPhoneId(phoneId)) {
                    PhoneUtils.unregisterIccStatus(mHandler, phoneId);
                    PhoneUtils.registerIccStatus(mHandler, EVENT_SIM_NETWORK_LOCKED, phoneId);
                }
                if (mPUKEntryActivity != null) {
                    // if an attempt to un-PUK-lock the device was made, while we're
                    // receiving this state change notification, notify the handler.
                    // NOTE: This is ONLY triggered if an attempt to un-PUK-lock has
                    // been attempted.
                    mHandler.sendMessage(mHandler.obtainMessage(EVENT_SIM_STATE_CHANGED,
                            intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE)));
                }
            } else if (action.equals(TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED)) {
                String newPhone = intent.getStringExtra(PhoneConstants.PHONE_NAME_KEY);
                Log.d(LOG_TAG, "Radio technology switched. Now " + newPhone + " is active.");
                initForNewRadioTechnology();
            } else if (action.equals(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED)) {
                handleServiceStateChanged(intent);
            } else if (action.equals(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED)) {
                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY, 0);
                phoneInEcm = PhoneFactory.getPhone(phoneId);
                Log.d(LOG_TAG, "Emergency Callback Mode. phoneId:" + phoneId);
                if (phoneInEcm != null) {
                    if (TelephonyCapabilities.supportsEcm(phoneInEcm)) {
                        Log.d(LOG_TAG, "Emergency Callback Mode arrived in PhoneApp.");
                        // Start Emergency Callback Mode service
                        if (intent.getBooleanExtra("phoneinECMState", false)) {
                            context.startService(new Intent(context,
                                    EmergencyCallbackModeService.class));
                        } else {
                            phoneInEcm = null;
                        }
                    } else {
                        // It doesn't make sense to get ACTION_EMERGENCY_CALLBACK_MODE_CHANGED
                        // on a device that doesn't support ECM in the first place.
                        Log.e(LOG_TAG, "Got ACTION_EMERGENCY_CALLBACK_MODE_CHANGED, but "
                                + "ECM isn't supported for phone: " + phoneInEcm.getPhoneName());
                        phoneInEcm = null;
                    }
                } else {
                    Log.w(LOG_TAG, "phoneInEcm is null.");
                }
            } else if (action.equals(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED)) {
                // Roaming status could be overridden by carrier config, so we need to update it.
                if (VDBG) Log.v(LOG_TAG, "carrier config changed.");
                updateDataRoamingStatus();
                updateLimitedSimFunctionForDualSim();
            } else if (action.equals(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)) {
                // We also need to pay attention when default data subscription changes.
                if (VDBG) Log.v(LOG_TAG, "default data sub changed.");
                mDefaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
                registerSettingsObserver();
                Phone phone = getPhone(mDefaultDataSubId);
                if (phone != null) {
                    updateDataRoamingStatus();
                }
            }
        }
    }

    private class SipReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            SipAccountRegistry sipAccountRegistry = SipAccountRegistry.getInstance();
            if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                SipUtil.startSipService();
            } else if (action.equals(SipManager.ACTION_SIP_SERVICE_UP)
                    || action.equals(SipManager.ACTION_SIP_CALL_OPTION_CHANGED)) {
                sipAccountRegistry.setup(context);
            } else if (action.equals(SipManager.ACTION_SIP_REMOVE_PHONE)) {
                if (DBG) {
                    Log.d(LOG_TAG, "SIP_REMOVE_PHONE "
                            + intent.getStringExtra(SipManager.EXTRA_LOCAL_URI));
                }
                sipAccountRegistry.removeSipProfile(intent.getStringExtra(
                        SipManager.EXTRA_LOCAL_URI));
            } else {
                if (DBG) Log.d(LOG_TAG, "onReceive, action not processed: " + action);
            }
        }
    }

    private void handleServiceStateChanged(Intent intent) {
        /**
         * This used to handle updating EriTextWidgetProvider this routine
         * and and listening for ACTION_SERVICE_STATE_CHANGED intents could
         * be removed. But leaving just in case it might be needed in the near
         * future.
         */

        if (VDBG) Log.v(LOG_TAG, "handleServiceStateChanged");
        // If service just returned, start sending out the queued messages
        Bundle extras = intent.getExtras();
        if (extras != null) {
            ServiceState ss = ServiceState.newFromBundle(extras);
            if (ss != null) {
                int state = ss.getState();
                int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                notificationMgr.updateNetworkSelection(state, subId);

                if (VDBG) {
                    Log.v(LOG_TAG, "subId=" + subId + ",mDefaultDataSubId="
                            + mDefaultDataSubId + ",ss roaming=" + ss.getDataRoaming());
                }
                if (subId == mDefaultDataSubId) {
                    updateDataRoamingStatus();
                }
            }
        }
    }

    /**
     * @return whether or not we should show a notification when connecting to data roaming if the
     * user has data roaming enabled
     */
    private boolean shouldShowDataConnectedRoaming(int subId) {
        PersistableBundle config = getCarrierConfigForSubId(subId);
        return config.getBoolean(CarrierConfigManager
                .KEY_SHOW_DATA_CONNECTED_ROAMING_NOTIFICATION_BOOL);
    }

    /**
     * When roaming, if mobile data cannot be established due to data roaming not enabled, we need
     * to notify the user so they can enable it through settings. Vise versa if the condition
     * changes, we need to dismiss the notification.
     */
    private void updateDataRoamingStatus() {
        if (VDBG) Log.v(LOG_TAG, "updateDataRoamingStatus");
        Phone phone = getPhone(mDefaultDataSubId);
        if (phone == null) {
            Log.w(LOG_TAG, "Can't get phone with sub id = " + mDefaultDataSubId);
            return;
        }

        DataConnectionReasons reasons = new DataConnectionReasons();
        boolean dataAllowed = phone.isDataAllowed(ApnSetting.TYPE_DEFAULT, reasons);
        mDataRoamingNotifLog.log("dataAllowed=" + dataAllowed + ", reasons=" + reasons);
        if (VDBG) Log.v(LOG_TAG, "dataAllowed=" + dataAllowed + ", reasons=" + reasons);
        if (!dataAllowed && reasons.containsOnly(DataDisallowedReasonType.ROAMING_DISABLED)) {
            // No need to show it again if we never cancelled it explicitly.
            if (mPrevRoamingNotification == ROAMING_NOTIFICATION_DISCONNECTED) return;
            // If the only reason of no data is data roaming disabled, then we notify the user
            // so the user can turn on data roaming.
            mPrevRoamingNotification = ROAMING_NOTIFICATION_DISCONNECTED;
            Log.d(LOG_TAG, "Show roaming disconnected notification");
            mDataRoamingNotifLog.log("Show roaming off.");
            Message msg = mHandler.obtainMessage(EVENT_DATA_ROAMING_DISCONNECTED);
            msg.arg1 = mDefaultDataSubId;
            msg.sendToTarget();
        } else if (dataAllowed && dataIsNowRoaming(mDefaultDataSubId)
                && shouldShowDataConnectedRoaming(mDefaultDataSubId)) {
            // No need to show it again if we never cancelled it explicitly, or carrier config
            // indicates this is not needed.
            if (mPrevRoamingNotification == ROAMING_NOTIFICATION_CONNECTED) return;
            mPrevRoamingNotification = ROAMING_NOTIFICATION_CONNECTED;
            Log.d(LOG_TAG, "Show roaming connected notification");
            mDataRoamingNotifLog.log("Show roaming on.");
            Message msg = mHandler.obtainMessage(EVENT_DATA_ROAMING_CONNECTED);
            msg.arg1 = mDefaultDataSubId;
            msg.sendToTarget();
        } else if (mPrevRoamingNotification != ROAMING_NOTIFICATION_NO_NOTIFICATION) {
            // Otherwise we either 1) we are not roaming or 2) roaming is off but ROAMING_DISABLED
            // is not the only data disable reason. In this case we dismiss the notification we
            // showed earlier.
            mPrevRoamingNotification = ROAMING_NOTIFICATION_NO_NOTIFICATION;
            Log.d(LOG_TAG, "Dismiss roaming notification");
            mDataRoamingNotifLog.log("Hide. data allowed=" + dataAllowed + ", reasons=" + reasons);
            mHandler.sendEmptyMessage(EVENT_DATA_ROAMING_OK);
        }
    }

    /**
     *
     * @param subId to check roaming on
     * @return whether we have transitioned to dataRoaming
     */
    private boolean dataIsNowRoaming(int subId) {
        return getPhone(subId).getServiceState().getDataRoaming();
    }

    private void updateLimitedSimFunctionForDualSim() {
        if (DBG) Log.d(LOG_TAG, "updateLimitedSimFunctionForDualSim");
        // check conditions to display limited SIM function notification under dual SIM
        SubscriptionManager subMgr = (SubscriptionManager) getSystemService(
                Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        List<SubscriptionInfo> subList = subMgr.getActiveSubscriptionInfoList(false);
        if (subList != null && subList.size() > 1) {
            CarrierConfigManager configMgr = (CarrierConfigManager)
                    getSystemService(Context.CARRIER_CONFIG_SERVICE);
            for (SubscriptionInfo info : subList) {
                PersistableBundle b = configMgr.getConfigForSubId(info.getSubscriptionId());
                if (b != null) {
                    if (b.getBoolean(CarrierConfigManager
                            .KEY_LIMITED_SIM_FUNCTION_NOTIFICATION_FOR_DSDS_BOOL)) {
                        notificationMgr.showLimitedSimFunctionWarningNotification(
                                info.getSubscriptionId(),
                                info.getDisplayName().toString());
                    } else {
                        notificationMgr.dismissLimitedSimFunctionWarningNotification(
                                info.getSubscriptionId());
                    }
                }
            }
        } else {
            // cancel notifications for all subs
            notificationMgr.dismissLimitedSimFunctionWarningNotification(
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        }
        notificationMgr.dismissLimitedSimFunctionWarningNotificationForInactiveSubs();

    }

    public Phone getPhoneInEcm() {
        return phoneInEcm;
    }

    /**
     * Triggers a refresh of the message waiting (voicemail) indicator.
     *
     * @param subId the subscription id we should refresh the notification for.
     */
    public void refreshMwiIndicator(int subId) {
        notificationMgr.refreshMwi(subId);
    }

    /**
     * Called when the network selection on the subscription {@code subId} is changed by the user.
     *
     * @param subId the subscription id.
     */
    public void onNetworkSelectionChanged(int subId) {
        Phone phone = getPhone(subId);
        if (phone != null) {
            notificationMgr.updateNetworkSelection(phone.getServiceState().getState(), subId);
        } else {
            Log.w(LOG_TAG, "onNetworkSelectionChanged on null phone, subId: " + subId);
        }
    }

    /**
     * Dump the state of the object, add calls to other objects as desired.
     *
     * @param fd File descriptor
     * @param printWriter Print writer
     * @param args Arguments
     */
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println("------- PhoneGlobals -------");
        pw.increaseIndent();
        pw.println("mPrevRoamingNotification=" + mPrevRoamingNotification);
        pw.println("mDefaultDataSubId=" + mDefaultDataSubId);
        pw.println("mDataRoamingNotifLog:");
        pw.println("isSmsCapable=" + TelephonyManager.from(this).isSmsCapable());
        pw.increaseIndent();
        mDataRoamingNotifLog.dump(fd, pw, args);
        pw.decreaseIndent();
        pw.decreaseIndent();
        pw.println("------- End PhoneGlobals -------");
    }
}
