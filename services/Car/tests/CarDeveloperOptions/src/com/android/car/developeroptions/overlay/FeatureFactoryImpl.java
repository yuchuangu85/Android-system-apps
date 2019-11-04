/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.developeroptions.overlay;

import android.app.AppGlobals;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.UserManager;

import androidx.annotation.Keep;

import com.android.car.developeroptions.accounts.AccountFeatureProvider;
import com.android.car.developeroptions.accounts.AccountFeatureProviderImpl;
import com.android.car.developeroptions.applications.ApplicationFeatureProvider;
import com.android.car.developeroptions.applications.ApplicationFeatureProviderImpl;
import com.android.car.developeroptions.aware.AwareFeatureProvider;
import com.android.car.developeroptions.aware.AwareFeatureProviderImpl;
import com.android.car.developeroptions.bluetooth.BluetoothFeatureProvider;
import com.android.car.developeroptions.bluetooth.BluetoothFeatureProviderImpl;
import com.android.car.developeroptions.connecteddevice.dock.DockUpdaterFeatureProviderImpl;
import com.android.car.developeroptions.core.instrumentation.SettingsMetricsFeatureProvider;
import com.android.car.developeroptions.dashboard.DashboardFeatureProvider;
import com.android.car.developeroptions.dashboard.DashboardFeatureProviderImpl;
import com.android.car.developeroptions.dashboard.suggestions.SuggestionFeatureProvider;
import com.android.car.developeroptions.dashboard.suggestions.SuggestionFeatureProviderImpl;
import com.android.car.developeroptions.enterprise.EnterprisePrivacyFeatureProvider;
import com.android.car.developeroptions.enterprise.EnterprisePrivacyFeatureProviderImpl;
import com.android.car.developeroptions.fuelgauge.PowerUsageFeatureProvider;
import com.android.car.developeroptions.fuelgauge.PowerUsageFeatureProviderImpl;
import com.android.car.developeroptions.gestures.AssistGestureFeatureProvider;
import com.android.car.developeroptions.gestures.AssistGestureFeatureProviderImpl;
import com.android.car.developeroptions.homepage.contextualcards.ContextualCardFeatureProvider;
import com.android.car.developeroptions.homepage.contextualcards.ContextualCardFeatureProviderImpl;
import com.android.car.developeroptions.localepicker.LocaleFeatureProvider;
import com.android.car.developeroptions.localepicker.LocaleFeatureProviderImpl;
import com.android.car.developeroptions.panel.PanelFeatureProvider;
import com.android.car.developeroptions.panel.PanelFeatureProviderImpl;
import com.android.car.developeroptions.search.SearchFeatureProvider;
import com.android.car.developeroptions.search.SearchFeatureProviderImpl;
import com.android.car.developeroptions.security.SecurityFeatureProvider;
import com.android.car.developeroptions.security.SecurityFeatureProviderImpl;
import com.android.car.developeroptions.slices.SlicesFeatureProvider;
import com.android.car.developeroptions.slices.SlicesFeatureProviderImpl;
import com.android.car.developeroptions.users.UserFeatureProvider;
import com.android.car.developeroptions.users.UserFeatureProviderImpl;
import com.android.settingslib.core.instrumentation.MetricsFeatureProvider;

/**
 * {@link FeatureFactory} implementation for AOSP Settings.
 */
@Keep
public class FeatureFactoryImpl extends FeatureFactory {

    private ApplicationFeatureProvider mApplicationFeatureProvider;
    private MetricsFeatureProvider mMetricsFeatureProvider;
    private DashboardFeatureProviderImpl mDashboardFeatureProvider;
    private DockUpdaterFeatureProvider mDockUpdaterFeatureProvider;
    private LocaleFeatureProvider mLocaleFeatureProvider;
    private EnterprisePrivacyFeatureProvider mEnterprisePrivacyFeatureProvider;
    private SearchFeatureProvider mSearchFeatureProvider;
    private SecurityFeatureProvider mSecurityFeatureProvider;
    private SuggestionFeatureProvider mSuggestionFeatureProvider;
    private PowerUsageFeatureProvider mPowerUsageFeatureProvider;
    private AssistGestureFeatureProvider mAssistGestureFeatureProvider;
    private UserFeatureProvider mUserFeatureProvider;
    private SlicesFeatureProvider mSlicesFeatureProvider;
    private AccountFeatureProvider mAccountFeatureProvider;
    private PanelFeatureProvider mPanelFeatureProvider;
    private ContextualCardFeatureProvider mContextualCardFeatureProvider;
    private BluetoothFeatureProvider mBluetoothFeatureProvider;
    private AwareFeatureProvider mAwareFeatureProvider;

    @Override
    public SupportFeatureProvider getSupportFeatureProvider(Context context) {
        return null;
    }

    @Override
    public MetricsFeatureProvider getMetricsFeatureProvider() {
        if (mMetricsFeatureProvider == null) {
            mMetricsFeatureProvider = new SettingsMetricsFeatureProvider();
        }
        return mMetricsFeatureProvider;
    }

    @Override
    public PowerUsageFeatureProvider getPowerUsageFeatureProvider(Context context) {
        if (mPowerUsageFeatureProvider == null) {
            mPowerUsageFeatureProvider = new PowerUsageFeatureProviderImpl(
                    context.getApplicationContext());
        }
        return mPowerUsageFeatureProvider;
    }

    @Override
    public DashboardFeatureProvider getDashboardFeatureProvider(Context context) {
        if (mDashboardFeatureProvider == null) {
            mDashboardFeatureProvider = new DashboardFeatureProviderImpl(
                    context.getApplicationContext());
        }
        return mDashboardFeatureProvider;
    }

    @Override
    public DockUpdaterFeatureProvider getDockUpdaterFeatureProvider() {
        if (mDockUpdaterFeatureProvider == null) {
            mDockUpdaterFeatureProvider = new DockUpdaterFeatureProviderImpl();
        }
        return mDockUpdaterFeatureProvider;
    }

    @Override
    public ApplicationFeatureProvider getApplicationFeatureProvider(Context context) {
        if (mApplicationFeatureProvider == null) {
            final Context appContext = context.getApplicationContext();
            mApplicationFeatureProvider = new ApplicationFeatureProviderImpl(appContext,
                    appContext.getPackageManager(),
                    AppGlobals.getPackageManager(),
                    (DevicePolicyManager) appContext
                            .getSystemService(Context.DEVICE_POLICY_SERVICE));
        }
        return mApplicationFeatureProvider;
    }

    @Override
    public LocaleFeatureProvider getLocaleFeatureProvider() {
        if (mLocaleFeatureProvider == null) {
            mLocaleFeatureProvider = new LocaleFeatureProviderImpl();
        }
        return mLocaleFeatureProvider;
    }

    @Override
    public EnterprisePrivacyFeatureProvider getEnterprisePrivacyFeatureProvider(Context context) {
        if (mEnterprisePrivacyFeatureProvider == null) {
            final Context appContext = context.getApplicationContext();
            mEnterprisePrivacyFeatureProvider = new EnterprisePrivacyFeatureProviderImpl(appContext,
                    (DevicePolicyManager) appContext.getSystemService(
                            Context.DEVICE_POLICY_SERVICE),
                    appContext.getPackageManager(),
                    UserManager.get(appContext),
                    (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE),
                    appContext.getResources());
        }
        return mEnterprisePrivacyFeatureProvider;
    }

    @Override
    public SearchFeatureProvider getSearchFeatureProvider() {
        if (mSearchFeatureProvider == null) {
            mSearchFeatureProvider = new SearchFeatureProviderImpl();
        }
        return mSearchFeatureProvider;
    }

    @Override
    public SurveyFeatureProvider getSurveyFeatureProvider(Context context) {
        return null;
    }

    @Override
    public SecurityFeatureProvider getSecurityFeatureProvider() {
        if (mSecurityFeatureProvider == null) {
            mSecurityFeatureProvider = new SecurityFeatureProviderImpl();
        }
        return mSecurityFeatureProvider;
    }

    @Override
    public SuggestionFeatureProvider getSuggestionFeatureProvider(Context context) {
        if (mSuggestionFeatureProvider == null) {
            mSuggestionFeatureProvider = new SuggestionFeatureProviderImpl(
                    context.getApplicationContext());
        }
        return mSuggestionFeatureProvider;
    }

    @Override
    public UserFeatureProvider getUserFeatureProvider(Context context) {
        if (mUserFeatureProvider == null) {
            mUserFeatureProvider = new UserFeatureProviderImpl(context.getApplicationContext());
        }
        return mUserFeatureProvider;
    }

    @Override
    public AssistGestureFeatureProvider getAssistGestureFeatureProvider() {
        if (mAssistGestureFeatureProvider == null) {
            mAssistGestureFeatureProvider = new AssistGestureFeatureProviderImpl();
        }
        return mAssistGestureFeatureProvider;
    }

    @Override
    public SlicesFeatureProvider getSlicesFeatureProvider() {
        if (mSlicesFeatureProvider == null) {
            mSlicesFeatureProvider = new SlicesFeatureProviderImpl();
        }
        return mSlicesFeatureProvider;
    }

    @Override
    public AccountFeatureProvider getAccountFeatureProvider() {
        if (mAccountFeatureProvider == null) {
            mAccountFeatureProvider = new AccountFeatureProviderImpl();
        }
        return mAccountFeatureProvider;
    }

    @Override
    public PanelFeatureProvider getPanelFeatureProvider() {
        if (mPanelFeatureProvider == null) {
            mPanelFeatureProvider = new PanelFeatureProviderImpl();
        }
        return mPanelFeatureProvider;
    }

    @Override
    public ContextualCardFeatureProvider getContextualCardFeatureProvider(Context context) {
        if (mContextualCardFeatureProvider == null) {
            mContextualCardFeatureProvider = new ContextualCardFeatureProviderImpl(
                    context.getApplicationContext());
        }
        return mContextualCardFeatureProvider;
    }

    @Override
    public BluetoothFeatureProvider getBluetoothFeatureProvider(Context context) {
        if (mBluetoothFeatureProvider == null) {
            mBluetoothFeatureProvider = new BluetoothFeatureProviderImpl(
                    context.getApplicationContext());
        }
        return mBluetoothFeatureProvider;
    }

    @Override
    public AwareFeatureProvider getAwareFeatureProvider() {
        if (mAwareFeatureProvider == null) {
            mAwareFeatureProvider = new AwareFeatureProviderImpl();
        }
        return mAwareFeatureProvider;
    }
}
