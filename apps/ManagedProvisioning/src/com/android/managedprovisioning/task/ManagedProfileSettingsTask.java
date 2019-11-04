/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.managedprovisioning.task;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.analytics.MetricsWriterFactory;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * Set the default PO policies and restrictions and mark user setup as completed.
 */
public class ManagedProfileSettingsTask extends AbstractProvisioningTask {

    @VisibleForTesting
    static final boolean DEFAULT_CONTACT_REMOTE_SEARCH = true;
    static final boolean DEFAULT_CROSS_PROFILE_CALENDAR_ENABLED = true;

    private final SettingsFacade mSettingsFacade;
    private final CrossProfileIntentFiltersSetter mCrossProfileIntentFiltersSetter;

    public ManagedProfileSettingsTask(
            Context context,
            ProvisioningParams params,
            Callback callback) {
        this(new SettingsFacade(), new CrossProfileIntentFiltersSetter(context), context, params,
                callback, new ProvisioningAnalyticsTracker(
                        MetricsWriterFactory.getMetricsWriter(context, new SettingsFacade()),
                        new ManagedProvisioningSharedPreferences(context)));
    }

    @VisibleForTesting
    ManagedProfileSettingsTask(
            SettingsFacade settingsFacade,
            CrossProfileIntentFiltersSetter crossProfileIntentFiltersSetter,
            Context context,
            ProvisioningParams params,
            Callback callback,
            ProvisioningAnalyticsTracker provisioningAnalyticsTracker) {
        super(context, params, callback, provisioningAnalyticsTracker);
        mSettingsFacade = checkNotNull(settingsFacade);
        mCrossProfileIntentFiltersSetter = checkNotNull(crossProfileIntentFiltersSetter);
    }

    @Override
    public void run(int userId) {
        // Turn on managed profile contacts remote search.
        mSettingsFacade.setProfileContactRemoteSearch(mContext, DEFAULT_CONTACT_REMOTE_SEARCH,
                userId);
        // Turn on cross-profile calendar.
        mSettingsFacade.setCrossProfileCalendarEnabled(mContext,
                DEFAULT_CROSS_PROFILE_CALENDAR_ENABLED, userId);

        // Disable managed profile wallpaper access
        UserManager um = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        um.setUserRestriction(UserManager.DISALLOW_WALLPAPER, true, UserHandle.of(userId));

        // Set the main color of managed provisioning from the provisioning params
        if (mProvisioningParams.mainColor != null) {
            DevicePolicyManager dpm = (DevicePolicyManager) mContext.getSystemService(
                    Context.DEVICE_POLICY_SERVICE);
            dpm.setOrganizationColorForUser(mProvisioningParams.mainColor, userId);
        }

        mCrossProfileIntentFiltersSetter.setFilters(UserHandle.myUserId(), userId);

        // always mark managed profile setup as completed
        mSettingsFacade.setUserSetupCompleted(mContext, userId);

        success();
    }

    @Override
    public int getStatusMsgId() {
        return R.string.progress_finishing_touches;
    }
}
