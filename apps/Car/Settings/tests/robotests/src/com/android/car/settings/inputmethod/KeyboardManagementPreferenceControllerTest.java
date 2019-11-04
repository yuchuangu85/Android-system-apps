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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowDevicePolicyManager;
import com.android.car.settings.testutils.ShadowInputMethodManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowInputMethodManager.class, ShadowDevicePolicyManager.class})
public class KeyboardManagementPreferenceControllerTest {
    private static final String DUMMY_LABEL = "dummy label";
    private static final String DUMMY_SETTINGS_ACTIVITY = "dummy settings activity";
    private static final String DUMMY_PACKAGE_NAME = "dummy package name";
    private static final String DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE =
            "dummy id defaultable direct boot aware";
    private static final String DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE =
            "dummy id defaultable not direct boot aware";
    private static final String DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE =
            "dummy id not defaultable direct boot aware";
    private static final String DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE =
            "dummy id not defaultable not direct boot aware";
    private static final String DISALLOWED_PACKAGE_NAME = "disallowed package name";
    private static final String ALLOWED_PACKAGE_NAME = "allowed package name";
    private List<String> mPermittedList;
    private PreferenceControllerTestHelper<KeyboardManagementPreferenceController>
            mControllerHelper;
    private PreferenceGroup mPreferenceGroup;
    private Context mContext;
    private InputMethodManager mInputMethodManager;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;

        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                KeyboardManagementPreferenceController.class, mPreferenceGroup);
        mInputMethodManager = (InputMethodManager) mContext.getSystemService(Context
                .INPUT_METHOD_SERVICE);

        mPermittedList = new ArrayList<>();
        mPermittedList.add(DUMMY_PACKAGE_NAME);
        mPermittedList.add(ALLOWED_PACKAGE_NAME);

        getShadowInputMethodManager(mContext).setInputMethodList(new ArrayList<>());

        mControllerHelper.markState(Lifecycle.State.CREATED);
    }

    @Test
    public void refreshUi_permitAllInputMethods_preferenceCountIs4() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE,
                DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        getShadowInputMethodManager(mContext).setInputMethodList(infos);
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(4);
    }

    @Test
    public void refreshUi_multiplteAllowedImeByOrganization_allPreferencesVisible() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(
                mPermittedList);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE,
                DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        getShadowInputMethodManager(mContext).setInputMethodList(infos);
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();

        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            assertThat(mPreferenceGroup.getPreference(i).isVisible()).isTrue();
        }
    }

    @Test
    public void refreshUi_multipleEnabledInputMethods_allPreferencesEnabled() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(
                mPermittedList);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE,
                DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        getShadowInputMethodManager(mContext).setInputMethodList(infos);
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();

        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            assertThat(mPreferenceGroup.getPreference(i).isEnabled()).isTrue();
        }
    }

    @Test
    public void refreshUi_multipleEnabledInputMethods_allPreferencesChecked() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(
                mPermittedList);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE,
                DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        getShadowInputMethodManager(mContext).setInputMethodList(infos);
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();

        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            assertThat(((SwitchPreference) mPreferenceGroup.getPreference(i)).isChecked())
                    .isTrue();
        }
    }

    @Test
    public void refreshUi_disallowedByOrganization_noPreferencesShown() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(
                mPermittedList);
        List<InputMethodInfo> infos = createInputMethodInfoList(DISALLOWED_PACKAGE_NAME,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE,
                DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        getShadowInputMethodManager(mContext).setInputMethodList(infos);
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void refreshUi_verifyPreferenceIcon() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE,
                DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        getShadowInputMethodManager(mContext).setInputMethodList(infos);
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();

        Preference preference = mPreferenceGroup.getPreference(0);
        assertThat(preference.getIcon()).isEqualTo(
                InputMethodUtil.getPackageIcon(mContext.getPackageManager(), infos.get(0)));
    }

    @Test
    public void refreshUi_verifyPreferenceTitle() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE,
                DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        getShadowInputMethodManager(mContext).setInputMethodList(infos);
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();

        Preference preference = mPreferenceGroup.getPreference(0);
        assertThat(preference.getTitle()).isEqualTo(
                InputMethodUtil.getPackageLabel(mContext.getPackageManager(), infos.get(0)));
    }

    @Test
    public void refreshUi_verifyPreferenceSummary() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE,
                DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        getShadowInputMethodManager(mContext).setInputMethodList(infos);
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(infos);

        mControllerHelper.getController().refreshUi();

        InputMethodManager inputMethodManager =
                (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        Preference preference = mPreferenceGroup.getPreference(0);
        assertThat(preference.getSummary()).isEqualTo(
                InputMethodUtil.getSummaryString(mContext, inputMethodManager, infos.get(0)));
    }

    @Test
    public void refreshUi_oneInputMethod_noneEnabled_oneInputMethodPreferenceInView() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void refreshUi_oneInputMethod_noneEnabled_preferenceEnabled() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        assertThat(mPreferenceGroup.getPreference(0).isEnabled()).isTrue();
    }

    @Test
    public void refreshUi_oneInputMethod_noneEnabled_preferenceNotChecked() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        assertThat(((SwitchPreference) mPreferenceGroup.getPreference(0)).isChecked())
                .isFalse();
    }

    @Test
    public void performClick_toggleTrue_securityDialogShown() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        verify(mControllerHelper.getMockFragmentController()).showDialog(
                any(ConfirmationDialogFragment.class),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
    }

    @Test
    public void performClick_toggleTrue_showSecurityDialog_positive_noOtherPreferenceAdded() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(dialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreferenceGroup.getPreference(0).getKey()).isEqualTo(
                DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE);
    }

    @Test
    public void performClick_toggleTrue_showSecurityDialog_positive_preferenceChecked() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(dialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        assertThat(((SwitchPreference) mPreferenceGroup.getPreference(0)).isChecked())
                .isTrue();
    }

    @Test
    public void performClick_toggleTrue_showSecurityDialog_positive_preferenceEnabled() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(dialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        assertThat(mPreferenceGroup.getPreference(0).isEnabled()).isTrue();
    }

    @Test
    public void performClick_toggleTrue_showSecurityDialog_positive_inputMethodEnabled() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(dialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        assertThat(mInputMethodManager.getEnabledInputMethodList().get(0).getId())
                .isEqualTo(DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE);
    }

    @Test
    public void performClick_toggleTrue_showSecurityDialog_negative_noOtherPreferenceAdded() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(dialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_NEGATIVE);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreferenceGroup.getPreference(0).getKey()).isEqualTo(
                DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE);
    }

    @Test
    public void performClick_toggleTrue_showSecurityDialog_negative_preferenceEnabled() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(dialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_NEGATIVE);

        assertThat(mPreferenceGroup.getPreference(0).isEnabled()).isTrue();
    }

    @Test
    public void performClick_toggleTrue_showSecurityDialog_negative_inputMethodDisabled() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(dialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_NEGATIVE);

        assertThat(((SwitchPreference) mPreferenceGroup.getPreference(0)).isChecked())
                .isFalse();
    }

    @Test
    public void performClick_toggleTrue_directBootWarningShown() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> securityDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(
                securityDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = securityDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);


        verify(mControllerHelper.getMockFragmentController()).showDialog(
                any(ConfirmationDialogFragment.class),
                eq(KeyboardManagementPreferenceController.DIRECT_BOOT_WARN_DIALOG_TAG));
    }

    @Test
    public void performClick_toggleTrue_showDirectBootDialog_positive_noOtherPreferenceAdded() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> securityDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(
                securityDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = securityDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        ArgumentCaptor<ConfirmationDialogFragment> bootDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(bootDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.DIRECT_BOOT_WARN_DIALOG_TAG));
        dialogFragment = bootDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreferenceGroup.getPreference(0).getKey()).isEqualTo(
                DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
    }

    @Test
    public void performClick_toggleTrue_showDirectBootDialog_positive_preferenceChecked() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> securityDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(
                securityDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = securityDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        ArgumentCaptor<ConfirmationDialogFragment> bootDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(bootDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.DIRECT_BOOT_WARN_DIALOG_TAG));
        dialogFragment = bootDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        assertThat(((SwitchPreference) mPreferenceGroup.getPreference(0)).isChecked())
                .isTrue();
    }

    @Test
    public void performClick_toggleTrue_showDirectBootDialog_positive_preferenceEnabled() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> securityDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(
                securityDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = securityDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        ArgumentCaptor<ConfirmationDialogFragment> bootDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(bootDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.DIRECT_BOOT_WARN_DIALOG_TAG));
        dialogFragment = bootDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        assertThat(mPreferenceGroup.getPreference(0).isEnabled()).isTrue();
    }

    @Test
    public void performClick_toggleTrue_showDirectBootDialog_positive_inputMethodEnabled() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> securityDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(
                securityDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = securityDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        ArgumentCaptor<ConfirmationDialogFragment> bootDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(bootDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.DIRECT_BOOT_WARN_DIALOG_TAG));
        dialogFragment = bootDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        assertThat(mInputMethodManager.getEnabledInputMethodList().get(0).getId())
                .isEqualTo(DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
    }

    @Test
    public void performClick_toggleTrue_showDirectBootDialog_negative_noOtherPreferenceAdded() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> securityDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(
                securityDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = securityDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        ArgumentCaptor<ConfirmationDialogFragment> bootDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(bootDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.DIRECT_BOOT_WARN_DIALOG_TAG));
        dialogFragment = bootDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_NEGATIVE);


        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreferenceGroup.getPreference(0).getKey()).isEqualTo(
                DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
    }

    @Test
    public void performClick_toggleTrue_showDirectBootDialog_negative_preferenceNotChecked() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> securityDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(
                securityDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = securityDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        ArgumentCaptor<ConfirmationDialogFragment> bootDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(bootDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.DIRECT_BOOT_WARN_DIALOG_TAG));
        dialogFragment = bootDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_NEGATIVE);


        assertThat(((SwitchPreference) mPreferenceGroup.getPreference(0)).isChecked())
                .isFalse();
    }

    @Test
    public void performClick_toggleTrue_showDirectBootDialog_negative_preferenceEnabled() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> securityDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(
                securityDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = securityDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        ArgumentCaptor<ConfirmationDialogFragment> bootDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(bootDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.DIRECT_BOOT_WARN_DIALOG_TAG));
        dialogFragment = bootDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_NEGATIVE);

        assertThat(mPreferenceGroup.getPreference(0).isEnabled()).isTrue();
    }

    @Test
    public void performClick_toggleTrue_showDirectBootDialog_negative_inputMethodDisabled() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(new ArrayList<>());

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> securityDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(
                securityDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = securityDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        ArgumentCaptor<ConfirmationDialogFragment> bootDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(bootDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.DIRECT_BOOT_WARN_DIALOG_TAG));
        dialogFragment = bootDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_NEGATIVE);

        assertThat(mInputMethodManager.getEnabledInputMethodList().size())
                .isEqualTo(0);
    }

    @Test
    public void performClick_toggleFalse_noOtherPreferenceAdded() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE));

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreferenceGroup.getPreference(0).getKey()).isEqualTo(
                DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
    }

    @Test
    public void performClick_toggleFalse_preferenceNotChecked() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE));

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        assertThat(((SwitchPreference) mPreferenceGroup.getPreference(0)).isChecked())
                .isFalse();
    }

    @Test
    public void performClick_toggleFalse_preferenceEnabled() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE));

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        assertThat((mPreferenceGroup.getPreference(0)).isEnabled())
                .isTrue();
    }

    @Test
    public void performClick_toggleFalse_inputMethodDisabled() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE));
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE));

        mControllerHelper.getController().refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        assertThat(mInputMethodManager.getEnabledInputMethodList().size())
                .isEqualTo(0);
    }

    @Test
    public void performClick_toggleFalse_twoDefaultable_notClickDefaultablePreferenceDisabled() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE)
        );
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE)
        );

        mControllerHelper.getController().refreshUi();

        getPreferenceFromGroupByKey(mPreferenceGroup, DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE)
                .performClick();

        assertThat(getPreferenceFromGroupByKey(mPreferenceGroup,
                DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE).isEnabled()).isFalse();
    }

    @Test
    public void performClick_toggleFalse_twoDefaultable_clickedDefaultablePreferenceEnabled() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE)
        );
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE)
        );

        mControllerHelper.getController().refreshUi();

        getPreferenceFromGroupByKey(mPreferenceGroup,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE).performClick();

        assertThat(getPreferenceFromGroupByKey(mPreferenceGroup,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE).isEnabled()).isTrue();
    }

    @Test
    public void performClick_toggleFalse_twoDefaultable_nonDefaultablePreferenceEnabled() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE)
        );
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE)
        );

        mControllerHelper.getController().refreshUi();

        getPreferenceFromGroupByKey(mPreferenceGroup,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE).performClick();

        assertThat(getPreferenceFromGroupByKey(mPreferenceGroup,
                DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE).isEnabled()).isTrue();
    }

    @Test
    public void performClick_toggleFalse_twoDefaultable_clickedDefaultablePreferenceNotChecked() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE)
        );
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE)
        );

        mControllerHelper.getController().refreshUi();

        getPreferenceFromGroupByKey(mPreferenceGroup,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE).performClick();

        assertThat(((SwitchPreference) getPreferenceFromGroupByKey(mPreferenceGroup,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE)).isChecked()).isFalse();
    }

    @Test
    public void performClick_toggleFalse_twoDefaultable_notClickedDefaultablePreferenceChecked() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE)
        );
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE)
        );

        mControllerHelper.getController().refreshUi();

        getPreferenceFromGroupByKey(mPreferenceGroup,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE).performClick();

        assertThat(((SwitchPreference) getPreferenceFromGroupByKey(mPreferenceGroup,
                DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE)).isChecked()).isTrue();
    }

    @Test
    public void performClick_toggleFalse_twoDefaultable_nonDefaultablePreferenceChecked() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE)
        );
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE)
        );

        mControllerHelper.getController().refreshUi();

        getPreferenceFromGroupByKey(mPreferenceGroup,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE).performClick();

        assertThat(((SwitchPreference) getPreferenceFromGroupByKey(mPreferenceGroup,
                DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE)).isChecked()).isTrue();
    }

    @Test
    public void performClick_toggleTrue_twoDefaultable_allPreferencesEnabled() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE)
        );
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE));

        mControllerHelper.getController().refreshUi();

        getPreferenceFromGroupByKey(mPreferenceGroup,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(dialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            assertThat(mPreferenceGroup.getPreference(i).isEnabled()).isTrue();
        }
    }

    @Test
    public void performClick_toggleTrue_twoDefaultable_allPreferencesChecked() {
        getShadowDevicePolicyManager(mContext).setPermittedInputMethodsForCurrentUser(null);
        getShadowInputMethodManager(mContext).setInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE, DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE)
        );
        getShadowInputMethodManager(mContext).setEnabledInputMethodList(createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE));

        mControllerHelper.getController().refreshUi();

        getPreferenceFromGroupByKey(mPreferenceGroup,
                DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mControllerHelper.getMockFragmentController()).showDialog(dialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            assertThat(((SwitchPreference) mPreferenceGroup.getPreference(i)).isChecked())
                    .isTrue();
        }
    }

    private static InputMethodInfo createMockInputMethodInfo(
            Context context, PackageManager packageManager,
            ShadowInputMethodManager inputMethodManager, String packageName, String id,
            boolean isDefaultable, boolean directBootAware) {
        ServiceInfo mockServiceInfo = mock(ServiceInfo.class);
        mockServiceInfo.directBootAware = directBootAware;

        InputMethodInfo mockInfo = mock(InputMethodInfo.class);
        when(mockInfo.getPackageName()).thenReturn(packageName);
        when(mockInfo.loadLabel(packageManager)).thenReturn(DUMMY_LABEL);
        when(mockInfo.getServiceInfo()).thenReturn(mockServiceInfo);
        when(mockInfo.getSettingsActivity()).thenReturn(DUMMY_SETTINGS_ACTIVITY);
        when(mockInfo.getId()).thenReturn(id);
        when(mockInfo.isDefault(context)).thenReturn(isDefaultable);
        List<InputMethodSubtype> subtypes = createSubtypes();
        inputMethodManager.setEnabledInputMethodSubtypeList(subtypes);
        return mockInfo;
    }

    private static Preference getPreferenceFromGroupByKey(PreferenceGroup prefGroup, String key) {
        for (int i = 0; i < prefGroup.getPreferenceCount(); i++) {
            Preference pref = prefGroup.getPreference(i);
            if (pref.getKey().equals(key)) {
                return pref;
            }
        }
        return null;
    }

    private static List<InputMethodSubtype> createSubtypes() {
        List<InputMethodSubtype> subtypes = new ArrayList<>();
        subtypes.add(createSubtype(1, "en_US"));
        subtypes.add(createSubtype(2, "de_BE"));
        subtypes.add(createSubtype(3, "oc-FR"));
        return subtypes;
    }

    private static InputMethodSubtype createSubtype(int id, String locale) {
        return new InputMethodSubtype.InputMethodSubtypeBuilder().setSubtypeId(id)
                .setSubtypeLocale(locale).setIsAuxiliary(false).setIsAsciiCapable(true).build();
    }

    private static ShadowInputMethodManager getShadowInputMethodManager(Context context) {
        return Shadow.extract(context.getSystemService(Context.INPUT_METHOD_SERVICE));
    }

    private static ShadowDevicePolicyManager getShadowDevicePolicyManager(Context context) {
        return Shadow.extract(context.getSystemService(Context.DEVICE_POLICY_SERVICE));
    }

    private List<InputMethodInfo> createInputMethodInfoList(String packageName, String... ids) {
        List<InputMethodInfo> infos = new ArrayList<>();
        PackageManager packageManager = mContext.getPackageManager();
        List<String> idsList = Arrays.asList(ids);
        idsList.forEach(id -> {
            boolean defaultable;
            boolean directBootAware;
            switch (id) {
                case DUMMY_ID_DEFAULTABLE_DIRECT_BOOT_AWARE:
                    defaultable = true;
                    directBootAware = true;
                    break;
                case DUMMY_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE:
                    defaultable = true;
                    directBootAware = false;
                    break;
                case DUMMY_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE:
                    defaultable = false;
                    directBootAware = true;
                    break;
                default: //case DUMMY_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE:
                    defaultable = false;
                    directBootAware = false;
                    break;
            }
            infos.add(createMockInputMethodInfo(mContext, packageManager,
                    getShadowInputMethodManager(mContext), packageName, id, defaultable,
                    directBootAware));
        });
        return infos;
    }
}
