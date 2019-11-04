/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.finalization;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import static com.android.internal.util.Preconditions.checkNotNull;
import static com.android.managedprovisioning.finalization.SendDpcBroadcastService.EXTRA_PROVISIONING_PARAMS;

import android.annotation.IntDef;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.UserHandle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.analytics.DeferredMetricsReader;
import com.android.managedprovisioning.common.NotificationHelper;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.provisioning.Constants;

import java.io.File;

/**
 * Controller for the finalization of managed provisioning.
 *
 * <p>This controller is invoked when the active provisioning is completed via
 * {@link #provisioningInitiallyDone(ProvisioningParams)}. In the case of provisioning during SUW,
 * it is invoked again when provisioning is finalized via {@link #provisioningFinalized()}.</p>
 */
public class FinalizationController {
    private static final String PROVISIONING_PARAMS_FILE_NAME =
            "finalization_activity_provisioning_params.xml";
    static final int PROVISIONING_FINALIZED_RESULT_DEFAULT = 1;
    static final int PROVISIONING_FINALIZED_RESULT_ADMIN_WILL_LAUNCH = 2;
    static final int PROVISIONING_FINALIZED_RESULT_EARLY_EXIT = 3;
    @IntDef({
            PROVISIONING_FINALIZED_RESULT_DEFAULT,
            PROVISIONING_FINALIZED_RESULT_ADMIN_WILL_LAUNCH,
            PROVISIONING_FINALIZED_RESULT_EARLY_EXIT})
    @interface ProvisioningFinalizedResult {}

    private final Context mContext;
    private final Utils mUtils;
    private final SettingsFacade mSettingsFacade;
    private final UserProvisioningStateHelper mUserProvisioningStateHelper;
    private final ProvisioningIntentProvider mProvisioningIntentProvider;
    private final NotificationHelper mNotificationHelper;
    private final DeferredMetricsReader mDeferredMetricsReader;
    private @ProvisioningFinalizedResult int mProvisioningFinalizedResult;

    public FinalizationController(Context context,
          UserProvisioningStateHelper userProvisioningStateHelper) {
        this(
                context,
                new Utils(),
                new SettingsFacade(),
                userProvisioningStateHelper,
                new NotificationHelper(context),
                new DeferredMetricsReader(
                        Constants.getDeferredMetricsFile(context)));
    }

    public FinalizationController(Context context) {
        this(
                context,
                new Utils(),
                new SettingsFacade(),
                new UserProvisioningStateHelper(context),
                new NotificationHelper(context),
                new DeferredMetricsReader(
                        Constants.getDeferredMetricsFile(context)));
    }

    @VisibleForTesting
    FinalizationController(Context context,
            Utils utils,
            SettingsFacade settingsFacade,
            UserProvisioningStateHelper helper,
            NotificationHelper notificationHelper,
            DeferredMetricsReader deferredMetricsReader) {
        mContext = checkNotNull(context);
        mUtils = checkNotNull(utils);
        mSettingsFacade = checkNotNull(settingsFacade);
        mUserProvisioningStateHelper = checkNotNull(helper);
        mProvisioningIntentProvider = new ProvisioningIntentProvider();
        mNotificationHelper = checkNotNull(notificationHelper);
        mDeferredMetricsReader = checkNotNull(deferredMetricsReader);
    }

    /**
     * This method is invoked when the provisioning process is done.
     *
     * <p>If provisioning happens as part of SUW, we rely on {@link #provisioningFinalized()} to be
     * called at the end of SUW. Otherwise, this method will finalize provisioning. If called after
     * SUW, this method notifies the DPC about the completed provisioning; otherwise, it stores the
     * provisioning params for later digestion.</p>
     *
     * <p>Note that fully managed device provisioning is only possible during SUW.
     *
     * @param params the provisioning params
     */
    public void provisioningInitiallyDone(ProvisioningParams params) {
        if (!mUserProvisioningStateHelper.isStateUnmanagedOrFinalized()) {
            // In any other state than STATE_USER_UNMANAGED and STATE_USER_SETUP_FINALIZED, we've
            // already run this method, so don't do anything.
            // STATE_USER_SETUP_FINALIZED can occur here if a managed profile is provisioned on a
            // device owner device.
            ProvisionLogger.logw("provisioningInitiallyDone called, but state is not finalized or "
                    + "unmanaged");
            return;
        }

        mUserProvisioningStateHelper.markUserProvisioningStateInitiallyDone(params);
        if (ACTION_PROVISION_MANAGED_PROFILE.equals(params.provisioningAction)) {
            if (params.isOrganizationOwnedProvisioning) {
                setProfileOwnerCanAccessDeviceIds();
            }
            if (!mSettingsFacade.isDuringSetupWizard(mContext)) {
                // If a managed profile was provisioned after SUW, notify the DPC straight away.
                notifyDpcManagedProfile(params);
            }
        }
        if (mSettingsFacade.isDuringSetupWizard(mContext)) {
            // Store the information and wait for provisioningFinalized to be called
            storeProvisioningParams(params);
        }
    }

    private void setProfileOwnerCanAccessDeviceIds() {
        final DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);
        final int managedProfileUserId = mUtils.getManagedProfile(mContext).getIdentifier();
        final ComponentName admin = dpm.getProfileOwnerAsUser(managedProfileUserId);
        if (admin != null) {
            try {
                final Context profileContext = mContext.createPackageContextAsUser(
                        mContext.getPackageName(), 0 /* flags */,
                        UserHandle.of(managedProfileUserId));
                final DevicePolicyManager profileDpm =
                        profileContext.getSystemService(DevicePolicyManager.class);
                profileDpm.setProfileOwnerCanAccessDeviceIds(admin);
            } catch (NameNotFoundException e) {
                ProvisionLogger.logw("Error setting access to Device IDs: " + e.getMessage());
            }
        }
    }

    @VisibleForTesting
    PrimaryProfileFinalizationHelper getPrimaryProfileFinalizationHelper(
            ProvisioningParams params) {
        return new PrimaryProfileFinalizationHelper(params.accountToMigrate,
                params.keepAccountMigrated, mUtils.getManagedProfile(mContext),
                params.inferDeviceAdminPackageName(), mUtils,
                mUtils.isAdminIntegratedFlow(params));
    }

    /**
     * This method is invoked when provisioning is finalized.
     *
     * <p>This method has to be invoked after {@link #provisioningInitiallyDone(ProvisioningParams)}
     * was called. It is commonly invoked at the end of SUW if provisioning occurs during SUW. It
     * loads the provisioning params from the storage, notifies the DPC about the completed
     * provisioning and sets the right user provisioning states.
     *
     * <p>To retrieve the resulting state of this method, use
     * {@link #getProvisioningFinalizedResult()}
     */
    void provisioningFinalized() {
        mProvisioningFinalizedResult = PROVISIONING_FINALIZED_RESULT_EARLY_EXIT;
        mDeferredMetricsReader.scheduleDumpMetrics(mContext);

        if (mUserProvisioningStateHelper.isStateUnmanagedOrFinalized()) {
            ProvisionLogger.logw("provisioningInitiallyDone called, but state is finalized or "
                    + "unmanaged");
            return;
        }

        final ProvisioningParams params = loadProvisioningParamsAndClearFile();
        if (params == null) {
            ProvisionLogger.logw("FinalizationController invoked, but no stored params");
            return;
        }

        mProvisioningFinalizedResult = PROVISIONING_FINALIZED_RESULT_DEFAULT;
        if (mUtils.isAdminIntegratedFlow(params)) {
            // Don't send ACTION_PROFILE_PROVISIONING_COMPLETE broadcast to DPC or launch DPC by
            // ACTION_PROVISIONING_SUCCESSFUL intent if it's admin integrated flow.
            if (params.provisioningAction.equals(ACTION_PROVISION_MANAGED_PROFILE)) {
                getPrimaryProfileFinalizationHelper(params)
                        .finalizeProvisioningInPrimaryProfile(mContext, null);
            } else if (ACTION_PROVISION_MANAGED_DEVICE.equals(params.provisioningAction)) {
                mNotificationHelper.showPrivacyReminderNotification(
                        mContext, NotificationManager.IMPORTANCE_DEFAULT);
            }
            mProvisioningIntentProvider.launchFinalizationScreen(mContext, params);
        } else {
            if (params.provisioningAction.equals(ACTION_PROVISION_MANAGED_PROFILE)) {
                notifyDpcManagedProfile(params);
                final UserHandle managedProfileUserHandle = mUtils.getManagedProfile(mContext);
                final int userId = managedProfileUserHandle.getIdentifier();
                mProvisioningFinalizedResult =
                        mProvisioningIntentProvider.canLaunchDpc(params, userId, mUtils, mContext)
                        ? PROVISIONING_FINALIZED_RESULT_ADMIN_WILL_LAUNCH
                        : PROVISIONING_FINALIZED_RESULT_DEFAULT;
            } else {
                // For managed user and device owner, we send the provisioning complete intent and
                // maybe launch the DPC.
                final int userId = UserHandle.myUserId();
                final Intent provisioningCompleteIntent = mProvisioningIntentProvider
                        .createProvisioningCompleteIntent(params, userId, mUtils, mContext);
                if (provisioningCompleteIntent == null) {
                    return;
                }
                mContext.sendBroadcast(provisioningCompleteIntent);

                mProvisioningIntentProvider.maybeLaunchDpc(params, userId, mUtils, mContext);

                if (ACTION_PROVISION_MANAGED_DEVICE.equals(params.provisioningAction)) {
                    mNotificationHelper.showPrivacyReminderNotification(
                            mContext, NotificationManager.IMPORTANCE_DEFAULT);
                }
            }
        }

        mUserProvisioningStateHelper.markUserProvisioningStateFinalized(params);
    }

    /**
     * @throws IllegalStateException if {@link #provisioningFinalized()} was not called before.
     */
    @ProvisioningFinalizedResult int getProvisioningFinalizedResult() {
        if (mProvisioningFinalizedResult == 0) {
            throw new IllegalStateException("provisioningFinalized() has not been called.");
        }
        return mProvisioningFinalizedResult;
    }

    /**
     * Start a service which notifies the DPC on the managed profile that provisioning has
     * completed. When the DPC has received the intent, send notify the primary instance that the
     * profile is ready. The service is needed to prevent the managed provisioning process from
     * getting killed while the user is on the DPC screen.
     */
    private void notifyDpcManagedProfile(ProvisioningParams params) {
        mContext.startService(
                new Intent(mContext, SendDpcBroadcastService.class)
                        .putExtra(EXTRA_PROVISIONING_PARAMS, params));
    }

    private void storeProvisioningParams(ProvisioningParams params) {
        params.save(getProvisioningParamsFile());
    }

    private File getProvisioningParamsFile() {
        return new File(mContext.getFilesDir(), PROVISIONING_PARAMS_FILE_NAME);
    }

    @VisibleForTesting
    ProvisioningParams loadProvisioningParamsAndClearFile() {
        File file = getProvisioningParamsFile();
        ProvisioningParams result = ProvisioningParams.load(file);
        file.delete();
        return result;
    }
}
