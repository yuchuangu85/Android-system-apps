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

package com.android.car.settings.inputmethod;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.view.inputmethod.InputMethodInfo;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowDevicePolicyManager;
import com.android.car.settings.testutils.ShadowInputMethodManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowInputMethodManager.class, ShadowDevicePolicyManager.class})
public class KeyboardPreferenceControllerTest {
    private static final String EMPTY = "";
    private static final String DUMMY_LABEL = "dummy label";
    private static final String DUMMY_LABEL_1 = "dummy label 1";
    private static final String DUMMY_LABEL_2 = "dummy label 2";
    private static final String DUMMY_SETTINGS_ACTIVITY = "dummy settings activity";
    private static final String DUMMY_PACKAGE_NAME = "dummy package name";
    private static final String ALLOWED_PACKAGE_NAME = "allowed package name";
    private static final String DISALLOWED_PACKAGE_NAME = "disallowed package name";
    private List<String> mPermittedList;
    private PreferenceControllerTestHelper<KeyboardPreferenceController> mControllerHelper;
    private Preference mPreference;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;

        mPreference = new Preference(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                KeyboardPreferenceController.class, mPreference);

        mPermittedList = new ArrayList<>();
        mPermittedList.add(DUMMY_PACKAGE_NAME);
        mPermittedList.add(ALLOWED_PACKAGE_NAME);
        mControllerHelper.markState(Lifecycle.State.CREATED);
    }

    @After
    public void tearDown() {
        ShadowInputMethodManager.reset();
        ShadowDevicePolicyManager.reset();
    }

    @Test
    public void refreshUi_noInputMethodInfo() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(Collections.emptyList());

        mControllerHelper.getController().refreshUi();
        assertThat(mPreference.getSummary()).isEqualTo(EMPTY);
    }

    @Test
    public void refreshUi_permitAllInputMethods_hasOneInputMethodInfo() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);

        List<InputMethodInfo> infos = new ArrayList<>();
        PackageManager packageManager = mContext.getPackageManager();
        infos.add(createInputMethodInfo(packageManager, DUMMY_PACKAGE_NAME, DUMMY_LABEL));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();
        assertThat(mPreference.getSummary()).isNotNull();
        assertThat(mPreference.getSummary().toString().contains(DUMMY_LABEL)).isTrue();
    }

    @Test
    public void refreshUi_permitAllInputMethods_hasTwoInputMethodInfo() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);

        List<InputMethodInfo> infos = new ArrayList<>();
        PackageManager packageManager = mContext.getPackageManager();
        infos.add(createInputMethodInfo(packageManager, DUMMY_PACKAGE_NAME, DUMMY_LABEL));
        infos.add(createInputMethodInfo(packageManager, DUMMY_PACKAGE_NAME, DUMMY_LABEL_1));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();
        assertThat(mPreference.getSummary()).isNotNull();
        assertThat(mPreference.getSummary().toString().contains(DUMMY_LABEL)).isTrue();
        assertThat(mPreference.getSummary().toString().contains(DUMMY_LABEL_1)).isTrue();
    }

    @Test
    public void refreshUi_permitAllInputMethods_hasThreeInputMethodInfo() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);

        List<InputMethodInfo> infos = new ArrayList<>();
        PackageManager packageManager = mContext.getPackageManager();
        infos.add(createInputMethodInfo(packageManager, DUMMY_PACKAGE_NAME, DUMMY_LABEL));
        infos.add(createInputMethodInfo(packageManager, DUMMY_PACKAGE_NAME, DUMMY_LABEL_1));
        infos.add(createInputMethodInfo(packageManager, DUMMY_PACKAGE_NAME, DUMMY_LABEL_2));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();
        assertThat(mPreference.getSummary()).isNotNull();
        assertThat(mPreference.getSummary().toString().contains(DUMMY_LABEL)).isTrue();
        assertThat(mPreference.getSummary().toString().contains(DUMMY_LABEL_1)).isTrue();
        assertThat(mPreference.getSummary().toString().contains(DUMMY_LABEL_2)).isTrue();
    }

    @Test
    public void refreshUi_hasAllowedImeByOrganization() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(
                mPermittedList);

        List<InputMethodInfo> infos = new ArrayList<>();
        PackageManager packageManager = mContext.getPackageManager();
        infos.add(createInputMethodInfo(packageManager, ALLOWED_PACKAGE_NAME, DUMMY_LABEL));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();
        assertThat(mPreference.getSummary()).isEqualTo(DUMMY_LABEL);
    }

    @Test
    public void refreshUi_disallowedByOrganization() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(
                mPermittedList);

        List<InputMethodInfo> infos = new ArrayList<>();
        PackageManager packageManager = mContext.getPackageManager();
        infos.add(createInputMethodInfo(packageManager, DISALLOWED_PACKAGE_NAME, DUMMY_LABEL));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();
        assertThat(mPreference.getSummary()).isEqualTo(EMPTY);
    }

    private static InputMethodInfo createInputMethodInfo(
            PackageManager packageManager, String packageName, String label) {
        ResolveInfo resolveInfo = mock(ResolveInfo.class);
        ServiceInfo serviceInfo = new ServiceInfo();
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = packageName;
        applicationInfo.enabled = true;
        applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        serviceInfo.applicationInfo = applicationInfo;
        serviceInfo.enabled = true;
        serviceInfo.packageName = packageName;
        serviceInfo.name = label;
        serviceInfo.exported = true;
        serviceInfo.nonLocalizedLabel = label;
        resolveInfo.serviceInfo = serviceInfo;
        resolveInfo.nonLocalizedLabel = label;
        when(resolveInfo.loadLabel(packageManager)).thenReturn(label);
        return new InputMethodInfo(resolveInfo, /* isAuxIme */false,
                DUMMY_SETTINGS_ACTIVITY,  /* subtypes */null, /* isDefaultResId */
                1, /*forceDefault*/false);
    }

    private static ShadowInputMethodManager getShadowInputMethodManager(Context context) {
        return Shadow.extract(context.getSystemService(Context.INPUT_METHOD_SERVICE));
    }

    private static ShadowDevicePolicyManager getShadowDevicePolicyManager(Context context) {
        return Shadow.extract(context.getSystemService(Context.DEVICE_POLICY_SERVICE));
    }
}
