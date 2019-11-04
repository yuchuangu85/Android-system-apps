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

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.provider.Settings;
import android.service.autofill.AutofillService;
import android.view.autofill.AutofillManager;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.ButtonPreference;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowAutofillServiceInfo;
import com.android.car.settings.testutils.ShadowSecureSettings;
import com.android.settingslib.applications.DefaultAppInfo;

import com.google.android.collect.Lists;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.Collections;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowSecureSettings.class, ShadowAutofillServiceInfo.class})
public class DefaultAutofillPickerEntryPreferenceControllerTest {

    private static final String TEST_PACKAGE = "com.android.car.settings.testutils";
    private static final String TEST_CLASS = "BaseTestActivity";
    private static final String TEST_OTHER_CLASS = "BaseTestOtherActivity";
    private static final String TEST_COMPONENT =
            new ComponentName(TEST_PACKAGE, TEST_CLASS).flattenToString();
    private static final int TEST_USER_ID = 10;

    private Context mContext;
    private ButtonPreference mButtonPreference;
    private DefaultAutofillPickerEntryPreferenceController mController;
    private PreferenceControllerTestHelper<DefaultAutofillPickerEntryPreferenceController>
            mControllerHelper;
    @Mock
    private AutofillManager mAutofillManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Shadows.shadowOf(RuntimeEnvironment.application).setSystemService(
                Context.AUTOFILL_MANAGER_SERVICE, mAutofillManager);
        mContext = RuntimeEnvironment.application;
        mButtonPreference = new ButtonPreference(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                DefaultAutofillPickerEntryPreferenceController.class, mButtonPreference);
        mController = mControllerHelper.getController();

        Settings.Secure.putString(mContext.getContentResolver(), Settings.Secure.AUTOFILL_SERVICE,
                "");
    }

    @After
    public void tearDown() {
        ShadowAutofillServiceInfo.reset();
        ShadowSecureSettings.reset();
    }

    @Test
    public void getAvailabilityStatus_autofillManagerIsNull_unsupportedOnDevice() {
        Shadows.shadowOf(RuntimeEnvironment.application).setSystemService(
                Context.AUTOFILL_MANAGER_SERVICE, null);

        // Reinitialize so that it uses the system service set in this test.
        ButtonPreference preference = new ButtonPreference(mContext);
        PreferenceControllerTestHelper<DefaultAutofillPickerEntryPreferenceController> helper =
                new PreferenceControllerTestHelper<>(mContext,
                        DefaultAutofillPickerEntryPreferenceController.class, preference);
        DefaultAutofillPickerEntryPreferenceController controller = helper.getController();

        assertThat(controller.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_autofillNotSupported_unsupportedOnDevice() {
        when(mAutofillManager.isAutofillSupported()).thenReturn(false);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_autofillSupported_isAvailable() {
        when(mAutofillManager.isAutofillSupported()).thenReturn(true);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getCurrentDefaultAppInfo_noService_returnsNull() {
        Settings.Secure.putString(mContext.getContentResolver(), Settings.Secure.AUTOFILL_SERVICE,
                "");

        assertThat(mController.getCurrentDefaultAppInfo()).isNull();
    }

    @Test
    public void getCurrentDefaultAppInfo_hasService_returnsDefaultAppInfo() {
        Settings.Secure.putString(mContext.getContentResolver(), Settings.Secure.AUTOFILL_SERVICE,
                TEST_COMPONENT);

        DefaultAppInfo info = mController.getCurrentDefaultAppInfo();
        assertThat(info.getKey()).isEqualTo(TEST_COMPONENT);
    }

    @Test
    public void getSettingIntent_nullDefaultAppInfo_returnsNull() {
        assertThat(mController.getSettingIntent(null)).isNull();
    }

    @Test
    public void getSettingIntent_noServiceInterface_returnsNull() {
        Intent intent = new Intent(AutofillService.SERVICE_INTERFACE);
        ShadowPackageManager shadowPackageManager = Shadows.shadowOf(mContext.getPackageManager());
        shadowPackageManager.addResolveInfoForIntent(intent, Collections.emptyList());

        DefaultAppInfo info = new DefaultAppInfo(mContext, mContext.getPackageManager(),
                TEST_USER_ID, ComponentName.unflattenFromString(TEST_COMPONENT));

        assertThat(mController.getSettingIntent(info)).isNull();
    }

    @Test
    public void getSettingIntent_hasServiceInterface_returnsIntent() {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.packageName = TEST_PACKAGE;
        resolveInfo.serviceInfo.name = TEST_CLASS;

        ShadowAutofillServiceInfo.setSettingsActivity(TEST_OTHER_CLASS);

        Intent intent = new Intent(AutofillService.SERVICE_INTERFACE);
        ShadowPackageManager shadowPackageManager = Shadows.shadowOf(mContext.getPackageManager());
        shadowPackageManager.addResolveInfoForIntent(intent, Lists.newArrayList(resolveInfo));

        DefaultAppInfo info = new DefaultAppInfo(mContext, mContext.getPackageManager(),
                TEST_USER_ID, ComponentName.unflattenFromString(TEST_COMPONENT));

        Intent result = mController.getSettingIntent(info);
        assertThat(result.getAction()).isEqualTo(Intent.ACTION_MAIN);
        assertThat(result.getComponent()).isEqualTo(
                new ComponentName(TEST_PACKAGE, TEST_OTHER_CLASS));
    }
}
