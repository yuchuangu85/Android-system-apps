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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowDevicePolicyManager;
import com.android.car.settings.testutils.ShadowInputMethodManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowApplication;

import java.util.ArrayList;
import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowInputMethodManager.class, ShadowDevicePolicyManager.class})
public class EnabledKeyboardPreferenceControllerTest {
    private static final String DUMMY_LABEL = "dummy label";
    private static final String DUMMY_ID = "dummy id";
    private static final String DUMMY_SETTINGS_ACTIVITY = "dummy settings activity";
    private static final String DUMMY_PACKAGE_NAME = "dummy package name";
    private static final String ALLOWED_PACKAGE_NAME = "allowed package name";
    private static final String DISALLOWED_PACKAGE_NAME = "disallowed package name";
    private List<String> mPermittedList;
    private PreferenceControllerTestHelper<EnabledKeyboardPreferenceController> mControllerHelper;
    private PreferenceGroup mPreferenceGroup;
    private Context mContext;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;

        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                EnabledKeyboardPreferenceController.class, mPreferenceGroup);

        mPermittedList = new ArrayList<>();
        mPermittedList.add(DUMMY_PACKAGE_NAME);
        mPermittedList.add(ALLOWED_PACKAGE_NAME);
        mControllerHelper.markState(Lifecycle.State.CREATED);
    }

    @After
    public void tearDown() {
        getShadowInputMethodManager(mContext).reset();
        getShadowDevicePolicyManager(mContext).reset();
    }

    @Test
    public void refreshUi_permitAllInputMethods() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(DUMMY_PACKAGE_NAME);
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void refreshUi_hasAllowedImeByOrganization() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(
                mPermittedList);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME);
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void refreshUi_disallowedByOrganization() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(
                mPermittedList);
        List<InputMethodInfo> infos = createInputMethodInfoList(DISALLOWED_PACKAGE_NAME);
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void refreshUi_verifyPreferenceIcon() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME);
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();

        Preference preference = mPreferenceGroup.getPreference(0);
        assertThat(preference.getIcon()).isEqualTo(
                InputMethodUtil.getPackageIcon(mContext.getPackageManager(), infos.get(0)));
    }

    @Test
    public void refreshUi_verifyPreferenceTitle() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME);
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();

        Preference preference = mPreferenceGroup.getPreference(0);
        assertThat(preference.getTitle()).isEqualTo(
                InputMethodUtil.getPackageLabel(mContext.getPackageManager(), infos.get(0)));
    }

    @Test
    public void refreshUi_verifyPreferenceSummary() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME);
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();

        InputMethodManager inputMethodManager =
                (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        Preference preference = mPreferenceGroup.getPreference(0);
        assertThat(preference.getSummary()).isEqualTo(
                InputMethodUtil.getSummaryString(mContext, inputMethodManager, infos.get(0)));
    }

    @Test
    public void performClick_launchSettingsActivity() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(DISALLOWED_PACKAGE_NAME);
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();

        Preference preference = mPreferenceGroup.getPreference(0);
        preference.performClick();

        Intent intent = ShadowApplication.getInstance().getNextStartedActivity();
        assertThat(intent).isNotNull();
        assertThat(intent.getAction()).isEqualTo(Intent.ACTION_MAIN);
        assertThat(intent.getComponent().getClassName()).isEqualTo(DUMMY_SETTINGS_ACTIVITY);
        assertThat(intent.getComponent().getPackageName()).isEqualTo(DISALLOWED_PACKAGE_NAME);
    }

    @Test
    public void performClick_noSettingsActivity_noCrash() {
        // Set to true if you'd like Robolectric to strictly simulate the real Android behavior when
        // calling {@link Context#startActivity(android.content.Intent)}. Real Android throws a
        // {@link android.content.ActivityNotFoundException} if given an {@link Intent} that is not
        // known to the {@link android.content.pm.PackageManager.
        // DUMMY_SETTINGS_ACTIVITY shouldn't exist, so it throws ActivityNotFoundException.
        ShadowApplication.getInstance().checkActivities(true);
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(DISALLOWED_PACKAGE_NAME);
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();

        Preference preference = mPreferenceGroup.getPreference(0);
        preference.performClick();

        Intent intent = ShadowApplication.getInstance().getNextStartedActivity();
        assertThat(intent).isNull();
    }

    private List<InputMethodInfo> createInputMethodInfoList(String packageName) {
        List<InputMethodInfo> infos = new ArrayList<>();
        PackageManager packageManager = mContext.getPackageManager();
        infos.add(createMockInputMethodInfoWithSubtypes(
                packageManager, getShadowInputMethodManager(mContext), packageName));
        return infos;
    }

    private static InputMethodInfo createMockInputMethodInfoWithSubtypes(
            PackageManager packageManager, ShadowInputMethodManager inputMethodManager,
            String packageName) {
        InputMethodInfo mockInfo = createMockInputMethodInfo(packageManager, packageName);
        List<InputMethodSubtype> subtypes = createSubtypes();
        inputMethodManager.setEnabledInputMethodSubtypeList(subtypes);

        return mockInfo;
    }

    private static InputMethodInfo createMockInputMethodInfo(
            PackageManager packageManager, String packageName) {
        InputMethodInfo mockInfo = mock(InputMethodInfo.class);
        when(mockInfo.getPackageName()).thenReturn(packageName);
        when(mockInfo.getId()).thenReturn(DUMMY_ID);
        when(mockInfo.loadLabel(packageManager)).thenReturn(DUMMY_LABEL);
        when(mockInfo.getServiceInfo()).thenReturn(new ServiceInfo());
        when(mockInfo.getSettingsActivity()).thenReturn(DUMMY_SETTINGS_ACTIVITY);
        return mockInfo;
    }

    private static List<InputMethodSubtype> createSubtypes() {
        List<InputMethodSubtype> subtypes = new ArrayList<>();
        subtypes.add(createSubtype(1, "en_US"));
        subtypes.add(createSubtype(2, "de_BE"));
        subtypes.add(createSubtype(3, "oc-FR"));
        return subtypes;
    }

    private static InputMethodSubtype createSubtype(int id, String locale) {
        return new InputMethodSubtypeBuilder().setSubtypeId(id).setSubtypeLocale(locale)
                .setIsAuxiliary(false).setIsAsciiCapable(true).build();
    }

    private static ShadowInputMethodManager getShadowInputMethodManager(Context context) {
        return Shadow.extract(context.getSystemService(Context.INPUT_METHOD_SERVICE));
    }

    private static ShadowDevicePolicyManager getShadowDevicePolicyManager(Context context) {
        return Shadow.extract(context.getSystemService(Context.DEVICE_POLICY_SERVICE));
    }
}
