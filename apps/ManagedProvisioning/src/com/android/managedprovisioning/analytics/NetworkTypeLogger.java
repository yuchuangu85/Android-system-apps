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

package com.android.managedprovisioning.analytics;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_NETWORK_TYPE;
import static com.android.internal.util.Preconditions.checkNotNull;

import android.app.admin.DevicePolicyEventLogger;
import android.content.Context;
import android.net.NetworkInfo;
import android.stats.devicepolicy.DevicePolicyEnums;
import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;

/**
 * Utility class to log the network type used while provisioning.
 */
public class NetworkTypeLogger {

    public static final String NETWORK_TYPE_NOT_CONNECTED = "network_type_not_connected";

    private final Context mContext;
    private final MetricsLoggerWrapper mMetricsLoggerWrapper;
    private final Utils mUtils;
    private final MetricsWriter mMetricsWriter;
    private final ManagedProvisioningSharedPreferences mSharedPreferences;

    public NetworkTypeLogger(Context context) {
        this(context, new Utils(), new MetricsLoggerWrapper(),
                MetricsWriterFactory.getMetricsWriter(context, new SettingsFacade()),
                new ManagedProvisioningSharedPreferences(context));
    }

    @VisibleForTesting
    NetworkTypeLogger(
            Context context,
            Utils utils,
            MetricsLoggerWrapper metricsLoggerWrapper,
            MetricsWriter metricsWriter,
            ManagedProvisioningSharedPreferences sharedPreferences) {
        mContext = checkNotNull(context);
        mUtils = checkNotNull(utils);
        mMetricsLoggerWrapper = checkNotNull(metricsLoggerWrapper);
        mMetricsWriter = checkNotNull(metricsWriter);
        mSharedPreferences = checkNotNull(sharedPreferences);
    }

    /**
     * Logs the network type to which the device is connected.
     */
    public void log() {
        final NetworkInfo networkInfo = mUtils.getActiveNetworkInfo(mContext);
        if (mUtils.isConnectedToNetwork(mContext)) {
            final int networkType = networkInfo.getType();
            mMetricsLoggerWrapper.logAction(mContext, PROVISIONING_NETWORK_TYPE, networkType);
            mMetricsWriter.write(DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.PROVISIONING_NETWORK_TYPE)
                    .setStrings(String.valueOf(networkType))
            .setTimePeriod(AnalyticsUtils.getProvisioningTime(mSharedPreferences)));
        } else {
            mMetricsLoggerWrapper.logAction(mContext, PROVISIONING_NETWORK_TYPE,
                    NETWORK_TYPE_NOT_CONNECTED);
            mMetricsWriter.write(DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.PROVISIONING_NETWORK_TYPE)
                    .setStrings(NETWORK_TYPE_NOT_CONNECTED)
                    .setTimePeriod(AnalyticsUtils.getProvisioningTime(mSharedPreferences)));
        }
    }
}
