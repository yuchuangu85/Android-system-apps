/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.preprovisioning;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_USER;
import static android.app.admin.DevicePolicyManager.CODE_ADD_MANAGED_PROFILE_DISALLOWED;
import static android.app.admin.DevicePolicyManager.CODE_CANNOT_ADD_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.CODE_HAS_DEVICE_OWNER;
import static android.app.admin.DevicePolicyManager.CODE_MANAGED_USERS_NOT_SUPPORTED;
import static android.app.admin.DevicePolicyManager.CODE_NOT_SYSTEM_USER;
import static android.app.admin.DevicePolicyManager.CODE_NOT_SYSTEM_USER_SPLIT;
import static android.app.admin.DevicePolicyManager.CODE_OK;
import static android.app.admin.DevicePolicyManager.CODE_SPLIT_SYSTEM_USER_DEVICE_SYSTEM_USER;
import static android.app.admin.DevicePolicyManager.CODE_USER_SETUP_COMPLETED;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_IMEI;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SERIAL_NUMBER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS;
import static android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_PREPROVISIONING_ACTIVITY_TIME_MS;
import static com.android.internal.util.Preconditions.checkNotNull;
import static com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker.CANCELLED_BEFORE_PROVISIONING;
import static com.android.managedprovisioning.common.Globals.ACTION_RESUME_PROVISIONING;
import static com.android.managedprovisioning.model.ProvisioningParams.PROVISIONING_MODE_FULLY_MANAGED_DEVICE;
import static com.android.managedprovisioning.model.ProvisioningParams.PROVISIONING_MODE_MANAGED_PROFILE;

import android.accounts.Account;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.os.UserManager;
import android.service.persistentdata.PersistentDataBlockManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.analytics.MetricsWriterFactory;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.analytics.TimeLogger;
import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.MdmPackageInfo;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.model.ProvisioningParams.ProvisioningMode;
import com.android.managedprovisioning.parser.MessageParser;
import com.android.managedprovisioning.preprovisioning.terms.TermsActivity;
import com.android.managedprovisioning.preprovisioning.terms.TermsDocument;
import com.android.managedprovisioning.preprovisioning.terms.TermsProvider;

import java.util.List;
import java.util.stream.Collectors;

public class PreProvisioningController {
    private final Context mContext;
    private final Ui mUi;
    private final MessageParser mMessageParser;
    private final Utils mUtils;
    private final SettingsFacade mSettingsFacade;
    private final EncryptionController mEncryptionController;

    // used system services
    private final DevicePolicyManager mDevicePolicyManager;
    private final UserManager mUserManager;
    private final PackageManager mPackageManager;
    private final ActivityManager mActivityManager;
    private final KeyguardManager mKeyguardManager;
    private final PersistentDataBlockManager mPdbManager;
    private final TimeLogger mTimeLogger;
    private final ProvisioningAnalyticsTracker mProvisioningAnalyticsTracker;
    private final ManagedProvisioningSharedPreferences mSharedPreferences;

    private ProvisioningParams mParams;

    public PreProvisioningController(
            @NonNull Context context,
            @NonNull Ui ui) {
        this(context, ui,
                new TimeLogger(context, PROVISIONING_PREPROVISIONING_ACTIVITY_TIME_MS),
                new MessageParser(context), new Utils(), new SettingsFacade(),
                EncryptionController.getInstance(context),
                new ManagedProvisioningSharedPreferences(context));
    }
    @VisibleForTesting
    PreProvisioningController(
            @NonNull Context context,
            @NonNull Ui ui,
            @NonNull TimeLogger timeLogger,
            @NonNull MessageParser parser,
            @NonNull Utils utils,
            @NonNull SettingsFacade settingsFacade,
            @NonNull EncryptionController encryptionController,
            @NonNull ManagedProvisioningSharedPreferences sharedPreferences) {
        mContext = checkNotNull(context, "Context must not be null");
        mUi = checkNotNull(ui, "Ui must not be null");
        mTimeLogger = checkNotNull(timeLogger, "Time logger must not be null");
        mMessageParser = checkNotNull(parser, "MessageParser must not be null");
        mSettingsFacade = checkNotNull(settingsFacade);
        mUtils = checkNotNull(utils, "Utils must not be null");
        mEncryptionController = checkNotNull(encryptionController,
                "EncryptionController must not be null");
        mSharedPreferences = checkNotNull(sharedPreferences);

        mDevicePolicyManager = mContext.getSystemService(DevicePolicyManager.class);
        mUserManager = mContext.getSystemService(UserManager.class);
        mPackageManager = mContext.getPackageManager();
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        mPdbManager = (PersistentDataBlockManager) mContext.getSystemService(
                Context.PERSISTENT_DATA_BLOCK_SERVICE);
        mProvisioningAnalyticsTracker = new ProvisioningAnalyticsTracker(
                MetricsWriterFactory.getMetricsWriter(mContext, mSettingsFacade),
                mSharedPreferences);
    }

    interface Ui {
        /**
         * Show an error message and cancel provisioning.
         * @param titleId resource id used to form the user facing error title
         * @param messageId resource id used to form the user facing error message
         * @param errorMessage an error message that gets logged for debugging
         */
        void showErrorAndClose(Integer titleId, int messageId, String errorMessage);

        /**
         * Request the user to encrypt the device.
         * @param params the {@link ProvisioningParams} object related to the ongoing provisioning
         */
        void requestEncryption(ProvisioningParams params);

        /**
         * Request the user to choose a wifi network.
         */
        void requestWifiPick();

        /**
         * Start provisioning.
         * @param userId the id of the user we want to start provisioning on
         * @param params the {@link ProvisioningParams} object related to the ongoing provisioning
         */
        void startProvisioning(int userId, ProvisioningParams params);

        /**
         * Show a dialog to delete an existing managed profile.
         * @param mdmPackageName the {@link ComponentName} of the existing profile's profile owner
         * @param domainName domain name of the organization which owns the managed profile
         * @param userId the user id of the existing profile
         */
        void showDeleteManagedProfileDialog(ComponentName mdmPackageName, String domainName,
                int userId);

        /**
         * Show an error dialog indicating that the current launcher does not support managed
         * profiles and ask the user to choose a different one.
         */
        void showCurrentLauncherInvalid();

        void prepareAdminIntegratedFlow(ProvisioningParams params);

        void showFactoryResetDialog(Integer titleId, int messageId);

        void initiateUi(UiParams uiParams);
    }

    /**
     * Wrapper which holds information related to the consent screen.
     * <p>Does not implement {@link Object#equals(Object)}, {@link Object#hashCode()}
     * or {@link Object#toString()}.
     */
    public static class UiParams {
        /**
         * The desired provisioning mode - values are defined in {@link ProvisioningMode}.
         */
        public @ProvisioningMode int provisioningMode;
        /**
         * Admin-related package information, e.g. icon, app label.
         * <p>These are inferred from the installed admin application.
         */
        public MdmPackageInfo packageInfo;
        /**
         * Defined by the organization in the provisioning trigger (e.g. QR code).
         */
        public String deviceAdminIconFilePath;
        /**
         * Defined by the organization in the provisioning trigger (e.g. QR code).
         */
        public String deviceAdminLabel;
        /**
         * Admin application package name.
         */
        public String packageName;
        /**
         * Various organization-defined customizations, e.g. colors, organization name.
         */
        public CustomizationParams customization;
        /**
         * List of headings for the organization-provided terms and conditions.
         */
        public List<String> disclaimerHeadings;
        public boolean isDeviceManaged;
        /**
         * The original provisioning action, kept for backwards compatibility.
         */
        public String provisioningAction;
        /**
         * {@link Intent} to launch the view terms screen.
         */
        public Intent viewTermsIntent;
        public boolean isSilentProvisioning;
        public boolean isOrganizationOwnedProvisioning;
    }

    /**
     * Initiates Profile owner and device owner provisioning.
     * @param intent Intent that started provisioning.
     * @param params cached ProvisioningParams if it has been parsed from Intent
     * @param callingPackage Package that started provisioning.
     */
    public void initiateProvisioning(Intent intent, ProvisioningParams params,
            String callingPackage) {
        mSharedPreferences.writeProvisioningStartedTimestamp(SystemClock.elapsedRealtime());
        mProvisioningAnalyticsTracker.logProvisioningSessionStarted(mContext);

        if (!tryParseParameters(intent, params)) {
            return;
        }

        if (!checkFactoryResetProtection(mParams, callingPackage)) {
            return;
        }

        if (!verifyActionAndCaller(intent, callingPackage)) {
            return;
        }

        // PO preconditions
        if (isProfileOwnerProvisioning()) {
            // If there is already a managed profile, setup the profile deletion dialog.
            int existingManagedProfileUserId = mUtils.alreadyHasManagedProfile(mContext);
            if (existingManagedProfileUserId != -1) {
                ComponentName mdmPackageName = mDevicePolicyManager
                        .getProfileOwnerAsUser(existingManagedProfileUserId);
                String domainName = mDevicePolicyManager
                        .getProfileOwnerNameAsUser(existingManagedProfileUserId);
                mUi.showDeleteManagedProfileDialog(mdmPackageName, domainName,
                        existingManagedProfileUserId);
                return;
            }
        }

        if (isDeviceOwnerProvisioning()) {
            // TODO: make a general test based on deviceAdminDownloadInfo field
            // PO doesn't ever initialize that field, so OK as a general case
            if (!mUtils.isConnectedToNetwork(mContext) && mParams.wifiInfo == null
                    && mParams.deviceAdminDownloadInfo != null
                    && !mParams.useMobileData) {
                // Have the user pick a wifi network if necessary.
                // It is not possible to ask the user to pick a wifi network if
                // the screen is locked.
                // TODO: remove this check once we know the screen will not be locked.
                if (mKeyguardManager.inKeyguardRestrictedInputMode()) {
                    // TODO: decide on what to do in that case; fail? retry on screen unlock?
                    ProvisionLogger.logi("Cannot pick wifi because the screen is locked.");
                } else if (canRequestWifiPick()) {
                    // we resume this method after a successful WiFi pick
                    // TODO: refactor as evil - logic should be less spread out
                    mUi.requestWifiPick();
                    return;
                } else {
                    mUi.showErrorAndClose(R.string.cant_set_up_device,
                            R.string.contact_your_admin_for_help,
                            "Cannot pick WiFi because there is no handler to the intent");
                }
            }
        }

        mTimeLogger.start();
        mProvisioningAnalyticsTracker.logPreProvisioningStarted(mContext, intent);

        if (mParams.isOrganizationOwnedProvisioning) {
            mUi.prepareAdminIntegratedFlow(mParams);
        } else {
            // skipUserConsent can only be set from a device owner provisioning to a work profile.
            if (mParams.skipUserConsent || Utils.isSilentProvisioning(mContext, mParams)) {
                continueProvisioningAfterUserConsent();
            } else {
                showUserConsentScreen();
            }
        }
    }

    void showUserConsentScreen() {
        // Check whether provisioning is allowed for the current action
        if (!checkDevicePolicyPreconditions()) {
            if (mParams.isOrganizationOwnedProvisioning) {
                mUi.showFactoryResetDialog(R.string.cant_set_up_device,
                        R.string.contact_your_admin_for_help);
            } else {
                return;
            }
        }

        ProvisionLogger.logd("Sending user consent:" + mParams.provisioningAction);

        CustomizationParams customization =
                CustomizationParams.createInstance(mParams, mContext, mUtils);

        ProvisionLogger.logd("Provisioning action for user consent:" + mParams.provisioningAction);

        // show UI so we can get user's consent to continue
        final String packageName = mParams.inferDeviceAdminPackageName();
        final MdmPackageInfo packageInfo =
            MdmPackageInfo.createFromPackageName(mContext, packageName);
        final UiParams uiParams = new UiParams();
        uiParams.customization = customization;
        uiParams.deviceAdminIconFilePath = mParams.deviceAdminIconFilePath;
        uiParams.deviceAdminLabel = mParams.deviceAdminLabel;
        uiParams.disclaimerHeadings = getDisclaimerHeadings();
        uiParams.provisioningMode = mParams.provisioningMode;
        uiParams.provisioningAction = mParams.provisioningAction;
        uiParams.packageName = packageName;
        uiParams.isDeviceManaged = mDevicePolicyManager.isDeviceManaged();
        uiParams.packageInfo = packageInfo;
        uiParams.viewTermsIntent = createViewTermsIntent();
        uiParams.isSilentProvisioning = Utils.isSilentProvisioning(mContext, mParams);
        uiParams.isOrganizationOwnedProvisioning = mParams.isOrganizationOwnedProvisioning;

        mUi.initiateUi(uiParams);
    }

    boolean updateProvisioningParamsFromIntent(Intent resultIntent) {
        final int provisioningMode = resultIntent.getIntExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_MODE, 0);
        final ProvisioningParams.Builder builder = mParams.toBuilder();
        switch (provisioningMode) {
            case DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE:
                builder.setProvisioningMode(PROVISIONING_MODE_FULLY_MANAGED_DEVICE);
                builder.setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE);
                maybeUpdateAdminExtrasBundle(builder, resultIntent);
                maybeUpdateSkipEducationScreens(builder, resultIntent);
                mParams = builder.build();
                return true;
            case DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE:
                builder.setProvisioningMode(PROVISIONING_MODE_MANAGED_PROFILE);
                builder.setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE);
                maybeUpdateAccountToMigrate(builder, resultIntent);
                maybeUpdateAdminExtrasBundle(builder, resultIntent);
                maybeUpdateSkipEducationScreens(builder, resultIntent);
                mParams = builder.build();
                return true;
            default:
                ProvisionLogger.logw("Unknown returned provisioning mode:"
                        + provisioningMode);
                return false;
        }
    }

    private void maybeUpdateSkipEducationScreens(ProvisioningParams.Builder builder,
            Intent resultIntent) {
        if (resultIntent.hasExtra(EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS)) {
            builder.setSkipEducationScreens(resultIntent.getBooleanExtra(
                    EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS, /* defaultValue */ false));
        }
    }

    private void maybeUpdateAccountToMigrate(ProvisioningParams.Builder builder,
            Intent resultIntent) {
        if (resultIntent.hasExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE)) {
            final Account account = resultIntent.getParcelableExtra(
                    EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE);
            builder.setAccountToMigrate(account);
        }
    }

    private void maybeUpdateAdminExtrasBundle(ProvisioningParams.Builder builder,
            Intent resultIntent) {
        if (resultIntent.hasExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)) {
            final PersistableBundle bundle = resultIntent.getParcelableExtra(
                    EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
            builder.setAdminExtrasBundle(bundle);
        }
    }

    void setProvisioningMode(int provisioningMode) {
        mParams = mParams.toBuilder().setProvisioningMode(provisioningMode).build();
    }

    void putExtrasIntoGetModeIntent(Intent intentGetMode) {
        final TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
        intentGetMode.putExtra(EXTRA_PROVISIONING_IMEI, telephonyManager.getImei());
        intentGetMode.putExtra(EXTRA_PROVISIONING_SERIAL_NUMBER, Build.getSerial());
        intentGetMode.putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, mParams.adminExtrasBundle);
    }

    private @NonNull List<String> getDisclaimerHeadings() {
        // TODO: only fetch headings, no need to fetch content; now not fast, but at least correct
        return new TermsProvider(mContext, StoreUtils::readString, mUtils)
                .getTerms(mParams, TermsProvider.Flags.SKIP_GENERAL_DISCLAIMER)
                .stream()
                .map(TermsDocument::getHeading)
                .collect(Collectors.toList());
    }

    private Intent createViewTermsIntent() {
        return new Intent(mContext, TermsActivity.class).putExtra(
            ProvisioningParams.EXTRA_PROVISIONING_PARAMS, mParams);
    }

    /**
     * Start provisioning for real. In profile owner case, double check that the launcher
     * supports managed profiles if necessary. In device owner case, possibly create a new user
     * before starting provisioning.
     */
    public void continueProvisioningAfterUserConsent() {
        mProvisioningAnalyticsTracker.logProvisioningAction(mContext, mParams.provisioningAction);

        // check if encryption is required
        if (isEncryptionRequired()) {
            if (mDevicePolicyManager.getStorageEncryptionStatus()
                    == DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED) {
                mUi.showErrorAndClose(R.string.cant_set_up_device,
                        R.string.device_doesnt_allow_encryption_contact_admin,
                        "This device does not support encryption, and "
                                + DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION
                                + " was not passed.");
            } else {
                mUi.requestEncryption(mParams);
                // we come back to this method after returning from encryption dialog
                // TODO: refactor as evil - logic should be less spread out
            }
            return;
        }

        if (isProfileOwnerProvisioning()) { // PO case
            // Check whether the current launcher supports managed profiles.
            if (!mUtils.currentLauncherSupportsManagedProfiles(mContext)) {
                mUi.showCurrentLauncherInvalid();
                // we come back to this method after returning from launcher dialog
                // TODO: refactor as evil - logic should be less spread out
                return;
            } else {
                // Cancel the boot reminder as provisioning has now started.
                mEncryptionController.cancelEncryptionReminder();
                stopTimeLogger();
                mUi.startProvisioning(mUserManager.getUserHandle(), mParams);
            }
        } else { // DO case
            // Cancel the boot reminder as provisioning has now started.
            mEncryptionController.cancelEncryptionReminder();
            if (isMeatUserCreationRequired(mParams.provisioningAction)) {
                // Create the primary user, and continue the provisioning in this user.
                // successful end of this task triggers provisioning
                // TODO: refactor as evil - logic should be less spread out
                new CreatePrimaryUserTask().execute();
            } else {
                stopTimeLogger();
                mUi.startProvisioning(mUserManager.getUserHandle(), mParams);
            }
        }
    }

    /** @return False if condition preventing further provisioning */
    @VisibleForTesting
    boolean checkFactoryResetProtection(ProvisioningParams params, String callingPackage) {
        if (skipFactoryResetProtectionCheck(params, callingPackage)) {
            return true;
        }
        if (factoryResetProtected()) {
            mUi.showErrorAndClose(R.string.cant_set_up_device,
                    R.string.device_has_reset_protection_contact_admin,
                    "Factory reset protection blocks provisioning.");
            return false;
        }
        return true;
    }

    private boolean skipFactoryResetProtectionCheck(
            ProvisioningParams params, String callingPackage) {
        if (TextUtils.isEmpty(callingPackage)) {
            return false;
        }
        String persistentDataPackageName = mContext.getResources()
                .getString(com.android.internal.R.string.config_persistentDataPackageName);
        try {
            // Only skip the FRP check if the caller is the package responsible for maintaining FRP
            // - i.e. if this is a flow for restoring device owner after factory reset.
            PackageInfo callingPackageInfo = mPackageManager.getPackageInfo(callingPackage, 0);
            return callingPackageInfo != null
                    && callingPackageInfo.applicationInfo != null
                    && callingPackageInfo.applicationInfo.isSystemApp()
                    && !TextUtils.isEmpty(persistentDataPackageName)
                    && callingPackage.equals(persistentDataPackageName)
                    && params != null
                    && params.startedByTrustedSource;
        } catch (PackageManager.NameNotFoundException e) {
            ProvisionLogger.loge("Calling package not found.", e);
            return false;
        }
    }

    /** @return False if condition preventing further provisioning */
    @VisibleForTesting protected boolean checkDevicePolicyPreconditions() {
        // If isSilentProvisioningForTestingDeviceOwner returns true, the component must be
        // current device owner, and we can safely ignore isProvisioningAllowed as we don't call
        // setDeviceOwner.
        if (Utils.isSilentProvisioningForTestingDeviceOwner(mContext, mParams)) {
            return true;
        }

        int provisioningPreCondition = mDevicePolicyManager.checkProvisioningPreCondition(
                mParams.provisioningAction, mParams.inferDeviceAdminPackageName());
        // Check whether provisioning is allowed for the current action.
        if (provisioningPreCondition != CODE_OK) {
            mProvisioningAnalyticsTracker.logProvisioningNotAllowed(mContext,
                    provisioningPreCondition);
            showProvisioningErrorAndClose(mParams.provisioningAction, provisioningPreCondition);
            return false;
        }
        return true;
    }

    /** @return False if condition preventing further provisioning */
    private boolean tryParseParameters(Intent intent, ProvisioningParams params) {
        try {
            // Read the provisioning params from the provisioning intent
            mParams = params == null ? mMessageParser.parse(intent) : params;
        } catch (IllegalProvisioningArgumentException e) {
            mUi.showErrorAndClose(R.string.cant_set_up_device, R.string.contact_your_admin_for_help,
                    e.getMessage());
            return false;
        }
        return true;
    }

    /** @return False if condition preventing further provisioning */
    @VisibleForTesting protected boolean verifyActionAndCaller(Intent intent,
            String callingPackage) {
        if (verifyActionAndCallerInner(intent, callingPackage)) {
            return true;
        } else {
            mUi.showErrorAndClose(R.string.cant_set_up_device, R.string.contact_your_admin_for_help,
                    "invalid intent or calling package");
            return false;
        }
    }

    private boolean verifyActionAndCallerInner(Intent intent, String callingPackage) {
        // If this is a resume after encryption or trusted intent, we verify the activity alias.
        // Otherwise, verify that the calling app is trying to set itself as Device/ProfileOwner
        if (ACTION_RESUME_PROVISIONING.equals(intent.getAction())) {
            return verifyActivityAlias(intent, "PreProvisioningActivityAfterEncryption");
        } else if (ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            return verifyActivityAlias(intent, "PreProvisioningActivityViaNfc");
        } else if (ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE.equals(intent.getAction())) {
            return verifyActivityAlias(intent, "PreProvisioningActivityViaTrustedApp");
        } else {
            return verifyCaller(callingPackage);
        }
    }

    private boolean verifyActivityAlias(Intent intent, String activityAlias) {
        ComponentName componentName = intent.getComponent();
        if (componentName == null || componentName.getClassName() == null) {
            ProvisionLogger.loge("null class in component when verifying activity alias "
                    + activityAlias);
            return false;
        }

        if (!componentName.getClassName().endsWith(activityAlias)) {
            ProvisionLogger.loge("Looking for activity alias " + activityAlias + ", but got "
                    + componentName.getClassName());
            return false;
        }

        return true;
    }

    /**
     * Verify that the caller is trying to set itself as owner.
     * @return false if the caller is trying to set a different package as owner.
     */
    private boolean verifyCaller(@NonNull String callingPackage) {
        if (callingPackage == null) {
            ProvisionLogger.loge("Calling package is null. Was startActivityForResult used to "
                    + "start this activity?");
            return false;
        }

        if (!callingPackage.equals(mParams.inferDeviceAdminPackageName())) {
            ProvisionLogger.loge("Permission denied, "
                    + "calling package tried to set a different package as owner. ");
            return false;
        }

        return true;
    }

    /**
     * Returns whether the device needs encryption.
     */
    private boolean isEncryptionRequired() {
        return !mParams.skipEncryption && mUtils.isEncryptionRequired();
    }

    /**
     * Returns whether the device is frp protected during setup wizard.
     */
    private boolean factoryResetProtected() {
        // If we are started during setup wizard, check for factory reset protection.
        // If the device is already setup successfully, do not check factory reset protection.
        if (mSettingsFacade.isDeviceProvisioned(mContext)) {
            ProvisionLogger.logd("Device is provisioned, FRP not required.");
            return false;
        }

        if (mPdbManager == null) {
            ProvisionLogger.logd("Reset protection not supported.");
            return false;
        }
        int size = mPdbManager.getDataBlockSize();
        ProvisionLogger.logd("Data block size: " + size);
        return size > 0;
    }

    /**
     * Returns whether meat user creation is required or not.
     * @param action Intent action that started provisioning
     */
    public boolean isMeatUserCreationRequired(String action) {
        if (mUtils.isSplitSystemUser()
                && ACTION_PROVISION_MANAGED_DEVICE.equals(action)) {
            List<UserInfo> users = mUserManager.getUsers();
            if (users.size() > 1) {
                mUi.showErrorAndClose(R.string.cant_set_up_device,
                        R.string.contact_your_admin_for_help,
                        "Cannot start Device Owner Provisioning because there are already "
                                + users.size() + " users");
                return false;
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns whether activity to pick wifi can be requested or not.
     */
    private boolean canRequestWifiPick() {
        return mPackageManager.resolveActivity(mUtils.getWifiPickIntent(), 0) != null;
    }

    /**
     * Returns whether the provisioning process is a profile owner provisioning process.
     */
    public boolean isProfileOwnerProvisioning() {
        return mUtils.isProfileOwnerAction(mParams.provisioningAction);
    }

    /**
     * Returns whether the provisioning process is a device owner provisioning process.
     */
    public boolean isDeviceOwnerProvisioning() {
        return mUtils.isDeviceOwnerAction(mParams.provisioningAction);
    }


    @Nullable
    public ProvisioningParams getParams() {
        return mParams;
    }

    /**
     * Notifies the time logger to stop.
     */
    public void stopTimeLogger() {
        mTimeLogger.stop();
    }

    /**
     * Log if PreProvisioning was cancelled.
     */
    public void logPreProvisioningCancelled() {
        mProvisioningAnalyticsTracker.logProvisioningCancelled(mContext,
                CANCELLED_BEFORE_PROVISIONING);
    }

    /**
     * Removes a user profile. If we are in COMP case, and were blocked by having to delete a user,
     * resumes COMP provisioning.
     */
    public void removeUser(int userProfileId) {
        // There is a possibility that the DO has set the disallow remove managed profile user
        // restriction, but is initiating the provisioning. In this case, we still want to remove
        // the managed profile.
        // We know that we can remove the managed profile because we checked
        // DevicePolicyManager.checkProvisioningPreCondition
        mUserManager.removeUserEvenWhenDisallowed(userProfileId);
    }

    SettingsFacade getSettingsFacade() {
        return mSettingsFacade;
    }

    // TODO: review the use of async task for the case where the activity might have got killed
    private class CreatePrimaryUserTask extends AsyncTask<Void, Void, UserInfo> {
        @Override
        protected UserInfo doInBackground(Void... args) {
            // Create the user where we're going to install the device owner.
            UserInfo userInfo = mUserManager.createUser(
                    mContext.getString(R.string.default_first_meat_user_name),
                    UserInfo.FLAG_PRIMARY | UserInfo.FLAG_ADMIN);

            if (userInfo != null) {
                ProvisionLogger.logi("Created user " + userInfo.id + " to hold the device owner");
            }
            return userInfo;
        }

        @Override
        protected void onPostExecute(UserInfo userInfo) {
            if (userInfo == null) {
                mUi.showErrorAndClose(R.string.cant_set_up_device,
                        R.string.contact_your_admin_for_help,
                        "Could not create user to hold the device owner");
            } else {
                mActivityManager.switchUser(userInfo.id);
                stopTimeLogger();
                // TODO: refactor as evil - logic should be less spread out
                mUi.startProvisioning(userInfo.id, mParams);
            }
        }
    }

    private void showProvisioningErrorAndClose(String action, int provisioningPreCondition) {
        // Try to show an error message explaining why provisioning is not allowed.
        switch (action) {
            case ACTION_PROVISION_MANAGED_USER:
                mUi.showErrorAndClose(R.string.cant_set_up_device,
                        R.string.contact_your_admin_for_help,
                        "Exiting managed user provisioning, setup incomplete");
                return;
            case ACTION_PROVISION_MANAGED_PROFILE:
                showManagedProfileErrorAndClose(provisioningPreCondition);
                return;
            case ACTION_PROVISION_MANAGED_DEVICE:
            case ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE:
                showDeviceOwnerErrorAndClose(provisioningPreCondition);
                return;
        }
        // This should never be the case, as showProvisioningError is always called after
        // verifying the supported provisioning actions.
    }

    private void showManagedProfileErrorAndClose(int provisioningPreCondition) {
        UserInfo userInfo = mUserManager.getUserInfo(mUserManager.getUserHandle());
        ProvisionLogger.logw("DevicePolicyManager.checkProvisioningPreCondition returns code: "
                + provisioningPreCondition);
        switch (provisioningPreCondition) {
            case CODE_ADD_MANAGED_PROFILE_DISALLOWED:
            case CODE_MANAGED_USERS_NOT_SUPPORTED:
                mUi.showErrorAndClose(R.string.cant_add_work_profile,
                        R.string.work_profile_cant_be_added_contact_admin,
                        "Exiting managed profile provisioning, managed profiles feature is not available");
                break;
            case CODE_CANNOT_ADD_MANAGED_PROFILE:
                if (!userInfo.canHaveProfile()) {
                    mUi.showErrorAndClose(R.string.cant_add_work_profile,
                            R.string.work_profile_cant_be_added_contact_admin,
                            "Exiting managed profile provisioning, calling user cannot have managed profiles");
                } else if (isRemovingManagedProfileDisallowed()){
                    mUi.showErrorAndClose(R.string.cant_replace_or_remove_work_profile,
                            R.string.for_help_contact_admin,
                            "Exiting managed profile provisioning, removing managed profile is disallowed");
                } else {
                    mUi.showErrorAndClose(R.string.cant_add_work_profile,
                            R.string.work_profile_cant_be_added_contact_admin,
                            "Exiting managed profile provisioning, cannot add more managed profiles");
                }
                break;
            case CODE_SPLIT_SYSTEM_USER_DEVICE_SYSTEM_USER:
                mUi.showErrorAndClose(R.string.cant_add_work_profile,
                        R.string.contact_your_admin_for_help,
                        "Exiting managed profile provisioning, a device owner exists");
                break;
            default:
                mUi.showErrorAndClose(R.string.cant_add_work_profile,
                        R.string.contact_your_admin_for_help,
                        "Managed profile provisioning not allowed for an unknown " +
                        "reason, code: " + provisioningPreCondition);
        }
    }

    private boolean isRemovingManagedProfileDisallowed() {
        return mUtils.alreadyHasManagedProfile(mContext) != -1
                && mUserManager.hasUserRestriction(UserManager.DISALLOW_REMOVE_MANAGED_PROFILE);
    }

    private void showDeviceOwnerErrorAndClose(int provisioningPreCondition) {
        switch (provisioningPreCondition) {
            case CODE_HAS_DEVICE_OWNER:
            case CODE_USER_SETUP_COMPLETED:
                mUi.showErrorAndClose(R.string.device_already_set_up,
                        R.string.if_questions_contact_admin, "Device already provisioned.");
                return;
            case CODE_NOT_SYSTEM_USER:
                mUi.showErrorAndClose(R.string.cant_set_up_device,
                        R.string.contact_your_admin_for_help,
                        "Device owner can only be set up for USER_SYSTEM.");
                return;
            case CODE_NOT_SYSTEM_USER_SPLIT:
                mUi.showErrorAndClose(R.string.cant_set_up_device,
                        R.string.contact_your_admin_for_help,
                        "System User Device owner can only be set on a split-user system.");
                return;
        }
        mUi.showErrorAndClose(R.string.cant_set_up_device, R.string.contact_your_admin_for_help,
                "Device Owner provisioning not allowed for an unknown reason.");
    }
}
