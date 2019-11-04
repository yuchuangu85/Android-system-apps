/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.settings.system;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_USER;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowCarrierConfigManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.List;

/** Unit test for {@link SystemUpdatePreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowCarrierConfigManager.class})
public class SystemUpdatePreferenceControllerTest {

    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;
    private Context mContext;
    private Preference mPreference;
    private PreferenceControllerTestHelper<SystemUpdatePreferenceController> mControllerHelper;
    private SystemUpdatePreferenceController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        when(mCarUserManagerHelper.isCurrentProcessAdminUser()).thenReturn(true);

        mContext = RuntimeEnvironment.application;
        mPreference = new Preference(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                SystemUpdatePreferenceController.class, mPreference);
        mController = mControllerHelper.getController();
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
    }

    @Test
    public void getAvailabilityStatus_adminUser_available() {
        when(mCarUserManagerHelper.isCurrentProcessAdminUser()).thenReturn(true);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_nonAdminUser_disabledForUser() {
        when(mCarUserManagerHelper.isCurrentProcessAdminUser()).thenReturn(false);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_USER);
    }

    @Test
    public void onCreate_setsActivityLabelAsTitle() {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;

        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.applicationInfo = applicationInfo;
        activityInfo.packageName = "some.test.package";
        activityInfo.name = "SomeActivity";

        String label = "Activity Label";
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.nonLocalizedLabel = label;
        resolveInfo.activityInfo = activityInfo;

        Intent intent = new Intent();
        ShadowPackageManager packageManager = Shadows.shadowOf(mContext.getPackageManager());
        packageManager.addResolveInfoForIntent(intent, resolveInfo);
        mPreference.setIntent(intent);

        mControllerHelper.markState(Lifecycle.State.CREATED);

        assertThat(mPreference.getTitle()).isEqualTo(label);
    }

    @Test
    public void refreshUi_activityNotFount_hidesPreference() {
        mPreference.setIntent(new Intent());
        mControllerHelper.markState(Lifecycle.State.STARTED);

        mController.refreshUi();

        assertThat(mPreference.isVisible()).isFalse();
    }

    @Test
    public void preferenceClicked_triggersClientInitiatedAction() {
        // Arrange
        String action = "action";
        String key = "key";
        String value = "value";

        PersistableBundle config = new PersistableBundle();
        config.putBoolean(CarrierConfigManager.KEY_CI_ACTION_ON_SYS_UPDATE_BOOL, true);
        config.putString(CarrierConfigManager.KEY_CI_ACTION_ON_SYS_UPDATE_INTENT_STRING, action);
        config.putString(CarrierConfigManager.KEY_CI_ACTION_ON_SYS_UPDATE_EXTRA_STRING, key);
        config.putString(CarrierConfigManager.KEY_CI_ACTION_ON_SYS_UPDATE_EXTRA_VAL_STRING, value);

        getShadowCarrierConfigManager().setConfigForSubId(
                SubscriptionManager.getDefaultSubscriptionId(), config);

        mControllerHelper.markState(Lifecycle.State.STARTED);

        // Act
        mPreference.performClick();

        // Assert
        List<Intent> broadcasts = ShadowApplication.getInstance().getBroadcastIntents();
        assertThat(broadcasts).hasSize(1);
        Intent broadcast = broadcasts.get(0);
        assertThat(broadcast.getAction()).isEqualTo(action);
        assertThat(broadcast.getStringExtra(key)).isEqualTo(value);
    }

    @Test
    public void preferenceClicked_handledReturnsFalse() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        assertThat(mPreference.getOnPreferenceClickListener().onPreferenceClick(
                mPreference)).isFalse();
    }

    private ShadowCarrierConfigManager getShadowCarrierConfigManager() {
        return Shadow.extract(mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE));
    }
}
