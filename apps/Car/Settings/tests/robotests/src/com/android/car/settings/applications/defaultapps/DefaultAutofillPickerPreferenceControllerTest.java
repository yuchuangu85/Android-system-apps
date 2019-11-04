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

package com.android.car.settings.applications.defaultapps;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.provider.Settings;
import android.service.autofill.AutofillService;

import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowSecureSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPackageManager;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowSecureSettings.class})
public class DefaultAutofillPickerPreferenceControllerTest {

    private static final String TEST_PACKAGE_NAME = "com.test.package";
    private static final String TEST_SERVICE = "TestService";

    private Context mContext;
    private PreferenceGroup mPreferenceGroup;
    private DefaultAutofillPickerPreferenceController mController;
    private PreferenceControllerTestHelper<DefaultAutofillPickerPreferenceController>
            mControllerHelper;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                DefaultAutofillPickerPreferenceController.class, mPreferenceGroup);
        mController = mControllerHelper.getController();
    }

    @After
    public void tearDown() {
        ShadowSecureSettings.reset();
    }

    @Test
    public void getCandidates_hasServiceWithoutPermissions_returnsEmptyList() {
        ResolveInfo serviceResolveInfo = new ResolveInfo();
        serviceResolveInfo.serviceInfo = new ServiceInfo();
        serviceResolveInfo.serviceInfo.packageName = TEST_PACKAGE_NAME;
        serviceResolveInfo.serviceInfo.name = TEST_SERVICE;
        serviceResolveInfo.serviceInfo.permission = "";
        getShadowPackageManager().addResolveInfoForIntent(
                new Intent(AutofillService.SERVICE_INTERFACE), serviceResolveInfo);

        assertThat(mController.getCandidates()).hasSize(0);
    }

    @Test
    public void getCandidates_hasServiceWithBindAutofillServicePermission_returnsService() {
        ResolveInfo serviceResolveInfo = new ResolveInfo();
        serviceResolveInfo.serviceInfo = new ServiceInfo();
        serviceResolveInfo.serviceInfo.packageName = TEST_PACKAGE_NAME;
        serviceResolveInfo.serviceInfo.name = TEST_SERVICE;
        serviceResolveInfo.serviceInfo.permission = Manifest.permission.BIND_AUTOFILL_SERVICE;
        getShadowPackageManager().addResolveInfoForIntent(
                new Intent(AutofillService.SERVICE_INTERFACE), serviceResolveInfo);

        assertThat(mController.getCandidates()).hasSize(1);
    }

    @Test
    public void getCandidates_hasServiceWithBindAutofillPermission_returnsEmptyList() {
        ResolveInfo serviceResolveInfo = new ResolveInfo();
        serviceResolveInfo.serviceInfo = new ServiceInfo();
        serviceResolveInfo.serviceInfo.packageName = TEST_PACKAGE_NAME;
        serviceResolveInfo.serviceInfo.name = TEST_SERVICE;
        serviceResolveInfo.serviceInfo.permission = Manifest.permission.BIND_AUTOFILL;
        getShadowPackageManager().addResolveInfoForIntent(
                new Intent(AutofillService.SERVICE_INTERFACE), serviceResolveInfo);

        assertThat(mController.getCandidates()).hasSize(0);
    }

    @Test
    public void getCurrentDefaultKey_secureSettingEmpty_returnsNoneKey() {
        // Secure Setting not set should return null.
        assertThat(mController.getCurrentDefaultKey()).isEqualTo(
                DefaultAppsPickerBasePreferenceController.NONE_PREFERENCE_KEY);
    }

    @Test
    public void getCurrentDefaultKey_secureSettingReturnsInvalidString_returnsNoneKey() {
        Settings.Secure.putString(mContext.getContentResolver(), Settings.Secure.AUTOFILL_SERVICE,
                "invalid");
        assertThat(mController.getCurrentDefaultKey()).isEqualTo(
                DefaultAppsPickerBasePreferenceController.NONE_PREFERENCE_KEY);
    }

    @Test
    public void getCurrentDefaultKey_secureSettingReturnsValidString_returnsCorrectKey() {
        String key = new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE).flattenToString();
        Settings.Secure.putString(mContext.getContentResolver(), Settings.Secure.AUTOFILL_SERVICE,
                key);
        assertThat(mController.getCurrentDefaultKey()).isEqualTo(key);
    }

    @Test
    public void setCurrentDefault_setInvalidKey_getCurrentDefaultKeyReturnsNone() {
        mController.setCurrentDefault("invalid");
        assertThat(mController.getCurrentDefaultKey()).isEqualTo(
                DefaultAppsPickerBasePreferenceController.NONE_PREFERENCE_KEY);
    }

    @Test
    public void setCurrentDefault_setValidKey_getCurrentDefaultKeyReturnsKey() {
        String key = new ComponentName(TEST_PACKAGE_NAME, TEST_SERVICE).flattenToString();
        mController.setCurrentDefault(key);
        assertThat(mController.getCurrentDefaultKey()).isEqualTo(key);
    }

    private ShadowPackageManager getShadowPackageManager() {
        return Shadows.shadowOf(mContext.getPackageManager());
    }
}
