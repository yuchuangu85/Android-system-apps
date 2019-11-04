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

package com.android.managedprovisioning.provisioning;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_TOTAL_TASK_TIME_MS;
import static com.android.internal.util.Preconditions.checkNotNull;
import static com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker.CANCELLED_DURING_PROVISIONING;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.analytics.MetricsWriterFactory;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.analytics.TimeLogger;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * Singleton instance that provides communications between the ongoing provisioning process and the
 * UI layer.
 */
public class ProvisioningManager implements ProvisioningControllerCallback,
        ProvisioningManagerInterface {

    private static ProvisioningManager sInstance;

    private final Context mContext;
    private final ProvisioningControllerFactory mFactory;
    private final ProvisioningAnalyticsTracker mProvisioningAnalyticsTracker;
    private final TimeLogger mTimeLogger;
    private final ProvisioningManagerHelper mHelper;

    @GuardedBy("this")
    private AbstractProvisioningController mController;

    public static ProvisioningManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ProvisioningManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private ProvisioningManager(Context context) {
        this(
                context,
                new Handler(Looper.getMainLooper()),
                new ProvisioningControllerFactory(),
                new ProvisioningAnalyticsTracker(
                        MetricsWriterFactory.getMetricsWriter(context, new SettingsFacade()),
                        new ManagedProvisioningSharedPreferences(context)),
                new TimeLogger(context, PROVISIONING_TOTAL_TASK_TIME_MS));
    }

    @VisibleForTesting
    ProvisioningManager(
            Context context,
            Handler uiHandler,
            ProvisioningControllerFactory factory,
            ProvisioningAnalyticsTracker analyticsTracker,
            TimeLogger timeLogger) {
        mContext = checkNotNull(context);
        mFactory = checkNotNull(factory);
        mProvisioningAnalyticsTracker = checkNotNull(analyticsTracker);
        mTimeLogger = checkNotNull(timeLogger);
        mHelper = new ProvisioningManagerHelper(context);
    }

    @Override
    public void maybeStartProvisioning(final ProvisioningParams params) {
        synchronized (this) {
            if (mController == null) {
                mTimeLogger.start();
                mController = getController(params);
                mHelper.startNewProvisioningLocked(mController);
                mProvisioningAnalyticsTracker.logProvisioningStarted(mContext, params);
            } else {
                ProvisionLogger.loge("Trying to start provisioning, but it's already running");
            }
        }
    }

    @Override
    public void registerListener(ProvisioningManagerCallback callback) {
        mHelper.registerListener(callback);
    }

    @Override
    public void unregisterListener(ProvisioningManagerCallback callback) {
        mHelper.unregisterListener(callback);
    }

    @Override
    public void cancelProvisioning() {
        synchronized (this) {
            final boolean provisioningCanceled = mHelper.cancelProvisioning(mController);
            if (provisioningCanceled) {
                mProvisioningAnalyticsTracker.logProvisioningCancelled(mContext,
                        CANCELLED_DURING_PROVISIONING);
            }
        }
    }

    @Override
    public void provisioningTasksCompleted() {
        synchronized (this) {
            mTimeLogger.stop();
            preFinalizationCompleted();
        }
    }

    @Override
    public void preFinalizationCompleted() {
        synchronized (this) {
            mHelper.notifyPreFinalizationCompleted();
            mProvisioningAnalyticsTracker.logProvisioningSessionCompleted(mContext);
            clearControllerLocked();
            ProvisionLogger.logi("ProvisioningManager pre-finalization completed");
        }
    }

    @Override
    public void cleanUpCompleted() {
        synchronized (this) {
            clearControllerLocked();
        }
    }

    @Override
    public void error(int titleId, int messageId, boolean factoryResetRequired) {
        mHelper.error(titleId, messageId, factoryResetRequired);
    }

    private AbstractProvisioningController getController(ProvisioningParams params) {
        return mFactory.createProvisioningController(mContext, params, this);
    }

    private void clearControllerLocked() {
        mController = null;
        mHelper.clearResourcesLocked();
    }
}
