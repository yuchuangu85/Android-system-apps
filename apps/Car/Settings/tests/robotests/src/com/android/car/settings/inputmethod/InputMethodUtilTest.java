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

import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder;

import com.android.car.settings.CarSettingsRobolectricTestRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class InputMethodUtilTest {
    private static final String DUMMY_PACKAGE_NAME = "dummy package name";
    private static final String DUMMY_LABEL = "dummy label";
    private static final String DUMMY_SETTINGS_ACTIVITY = "dummy settings activity";
    private static final String SUBTYPES_STRING =
            "English (United States), German (Belgium), and Occitan (France)";
    private static final String DUMMY_ENABLED_INPUT_METHODS =
            "com.google.android.googlequicksearchbox/com.google.android.voicesearch.ime"
                    + ".VoiceInputMethodService:com.google.android.apps.automotive.inputmethod/"
                    + ".InputMethodService";
    private static final String DUMMY_ENABLED_INPUT_METHOD_ID_DEFAULT =
            "com.google.android.apps.automotive.inputmethod/.InputMethodService";
    private static final String DUMMY_ENABLED_INPUT_METHOD_ID =
            "com.google.android.googlequicksearchbox/com.google.android.voicesearch.ime"
                    + ".VoiceInputMethodService";
    private static final String DUMMY_DISABLED_INPUT_METHOD_ID = "disabled input method id";
    private Context mContext;
    private List<InputMethodInfo> mDummyEnabledInputMethodsListAllDefaultable;
    private List<InputMethodInfo> mDummyEnabledInputMethodsListOneDefaultable;

    @Mock
    private PackageManager mPackageManager;
    @Mock
    private InputMethodManager mInputMethodManager;
    @Mock
    private Drawable mIcon;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = RuntimeEnvironment.application;

        Settings.Secure.putString(mContext.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS, DUMMY_ENABLED_INPUT_METHODS);
        Settings.Secure.putString(mContext.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD, DUMMY_ENABLED_INPUT_METHOD_ID_DEFAULT);

        mDummyEnabledInputMethodsListOneDefaultable = Arrays
                .stream(DUMMY_ENABLED_INPUT_METHODS.split(String.valueOf(InputMethodUtil
                        .INPUT_METHOD_DELIMITER))).collect(Collectors.toList()).stream().map(
                            result -> {
                                InputMethodInfo info = createMockInputMethodInfo(
                                        mPackageManager, DUMMY_PACKAGE_NAME);
                                when(info.getId()).thenReturn(result);
                                when(info.isDefault(mContext)).thenReturn(result.equals(
                                        DUMMY_ENABLED_INPUT_METHOD_ID_DEFAULT));
                                return info;
                            }).collect(Collectors.toList());
        mDummyEnabledInputMethodsListAllDefaultable = Arrays
                .stream(DUMMY_ENABLED_INPUT_METHODS.split(String.valueOf(InputMethodUtil
                        .INPUT_METHOD_DELIMITER))).collect(Collectors.toList()).stream().map(
                                result -> {
                                    InputMethodInfo info = createMockInputMethodInfo(
                                            mPackageManager, DUMMY_PACKAGE_NAME);
                                    when(info.getId()).thenReturn(result);
                                    when(info.isDefault(mContext)).thenReturn(true);
                                    return info;
                                }).collect(Collectors.toList());
    }

    @Test
    public void getPackageIcon_hasApplicationIcon() throws NameNotFoundException {
        InputMethodInfo info = createMockInputMethodInfoWithSubtypes(mPackageManager,
                mInputMethodManager, DUMMY_PACKAGE_NAME);
        when(mPackageManager.getApplicationIcon(eq(info.getPackageName()))).thenReturn(mIcon);
        assertThat(InputMethodUtil.getPackageIcon(mPackageManager, info)).isEqualTo(mIcon);
    }

    @Test
    public void getPackageIcon_noApplicationIcon() throws NameNotFoundException {
        InputMethodInfo info = createMockInputMethodInfoWithSubtypes(mPackageManager,
                mInputMethodManager, DUMMY_PACKAGE_NAME);
        when(mPackageManager.getApplicationIcon(DUMMY_PACKAGE_NAME)).thenThrow(
                new NameNotFoundException());
        assertThat(InputMethodUtil.getPackageIcon(mPackageManager, info)).isEqualTo(
                InputMethodUtil.NO_ICON);
    }

    @Test
    public void getPackageLabel() {
        InputMethodInfo info = createMockInputMethodInfoWithSubtypes(mPackageManager,
                mInputMethodManager, DUMMY_PACKAGE_NAME);
        assertThat(InputMethodUtil.getPackageLabel(mPackageManager, info)).isEqualTo(
                DUMMY_LABEL);
    }

    @Test
    public void getSummaryString() {
        InputMethodInfo info = createMockInputMethodInfoWithSubtypes(mPackageManager,
                mInputMethodManager, DUMMY_PACKAGE_NAME);
        assertThat(InputMethodUtil.getSummaryString(mContext, mInputMethodManager, info)).isEqualTo(
                SUBTYPES_STRING);
    }

    @Test
    public void isInputMethodEnabled_isDisabled_returnsFalse() {
        InputMethodInfo info = createMockInputMethodInfo(mPackageManager, DUMMY_PACKAGE_NAME);
        when(info.getId()).thenReturn(DUMMY_DISABLED_INPUT_METHOD_ID);

        assertThat(InputMethodUtil.isInputMethodEnabled(mContext.getContentResolver(), info))
                .isFalse();
    }

    @Test
    public void isInputMethodEnabled_isEnabled_returnsTrue() {
        InputMethodInfo info = createMockInputMethodInfo(mPackageManager, DUMMY_PACKAGE_NAME);
        when(info.getId()).thenReturn(DUMMY_ENABLED_INPUT_METHOD_ID_DEFAULT);

        assertThat(InputMethodUtil.isInputMethodEnabled(mContext.getContentResolver(), info))
                .isTrue();
    }

    @Test
    public void enableInputMethod_alreadyEnabled_remainsUnchanged() {
        InputMethodInfo info = createMockInputMethodInfo(mPackageManager, DUMMY_PACKAGE_NAME);
        when(info.getId()).thenReturn(DUMMY_ENABLED_INPUT_METHOD_ID_DEFAULT);

        InputMethodUtil.enableInputMethod(mContext.getContentResolver(), info);

        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS)).isEqualTo(DUMMY_ENABLED_INPUT_METHODS);
    }

    @Test
    public void enableInputMethod_noEnabledInputMethods_addsIME() {
        Settings.Secure.putString(mContext.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS, "");
        InputMethodInfo info = createMockInputMethodInfo(mPackageManager, DUMMY_PACKAGE_NAME);
        when(info.getId()).thenReturn(DUMMY_ENABLED_INPUT_METHOD_ID_DEFAULT);

        InputMethodUtil.enableInputMethod(mContext.getContentResolver(), info);

        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS)).isEqualTo(
                DUMMY_ENABLED_INPUT_METHOD_ID_DEFAULT);
    }

    @Test
    public void enableInputMethod_someEnabledInputMethods_addsIME() {
        InputMethodInfo info = createMockInputMethodInfo(mPackageManager, DUMMY_PACKAGE_NAME);
        when(info.getId()).thenReturn(DUMMY_DISABLED_INPUT_METHOD_ID);

        InputMethodUtil.enableInputMethod(mContext.getContentResolver(), info);

        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS)).isEqualTo(
                DUMMY_ENABLED_INPUT_METHODS + ":"
                        + DUMMY_DISABLED_INPUT_METHOD_ID);
    }

    @Test
    public void disableInputMethod_notEnabled_remainsUnchanged() {
        InputMethodInfo info = createMockInputMethodInfo(mPackageManager, DUMMY_PACKAGE_NAME);
        when(info.getId()).thenReturn(DUMMY_DISABLED_INPUT_METHOD_ID);
        when(mInputMethodManager.getEnabledInputMethodList())
                .thenReturn(mDummyEnabledInputMethodsListAllDefaultable);

        InputMethodUtil.disableInputMethod(mContext, mInputMethodManager, info);

        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS)).isEqualTo(DUMMY_ENABLED_INPUT_METHODS);
        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD)).isEqualTo(
                DUMMY_ENABLED_INPUT_METHOD_ID_DEFAULT);
    }

    @Test
    public void disableInputMethod_notDefault_removesIMEWhileDefaultRemainsSame() {
        InputMethodInfo info = createMockInputMethodInfo(mPackageManager, DUMMY_PACKAGE_NAME);
        when(info.getId()).thenReturn(DUMMY_ENABLED_INPUT_METHOD_ID);
        when(mInputMethodManager.getEnabledInputMethodList())
                .thenReturn(mDummyEnabledInputMethodsListAllDefaultable);

        InputMethodUtil.disableInputMethod(mContext, mInputMethodManager, info);

        assertThat(splitConcatenatedIdsIntoSet(Settings.Secure.getString(mContext
                .getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS))).isEqualTo(
                splitConcatenatedIdsIntoSet(DUMMY_ENABLED_INPUT_METHOD_ID_DEFAULT));
        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD)).isEqualTo(
                DUMMY_ENABLED_INPUT_METHOD_ID_DEFAULT);
    }

    @Test
    public void disableInputMethod_twoDefaultableIMEsEnabled_removesIMEAndChangesDefault() {
        InputMethodInfo info = createMockInputMethodInfo(mPackageManager, DUMMY_PACKAGE_NAME);
        when(info.getId()).thenReturn(DUMMY_ENABLED_INPUT_METHOD_ID_DEFAULT);
        when(mInputMethodManager.getEnabledInputMethodList())
                .thenReturn(mDummyEnabledInputMethodsListAllDefaultable);

        InputMethodUtil.disableInputMethod(mContext, mInputMethodManager, info);

        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS)).isEqualTo(
                DUMMY_ENABLED_INPUT_METHOD_ID);
        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD)).isEqualTo(
                DUMMY_ENABLED_INPUT_METHOD_ID);
    }

    @Test
    public void disableInputMethod_isDefaultWithNoOtherDefaultableEnabled_remainsUnchanged() {
        InputMethodInfo info = createMockInputMethodInfo(mPackageManager, DUMMY_PACKAGE_NAME);
        when(info.getId()).thenReturn(DUMMY_ENABLED_INPUT_METHOD_ID_DEFAULT);
        when(mInputMethodManager.getEnabledInputMethodList())
                .thenReturn(mDummyEnabledInputMethodsListOneDefaultable);

        InputMethodUtil.disableInputMethod(mContext, mInputMethodManager, info);

        assertThat(splitConcatenatedIdsIntoSet(Settings.Secure.getString(mContext
                .getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS))).isEqualTo(
                splitConcatenatedIdsIntoSet(DUMMY_ENABLED_INPUT_METHODS));
        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD)).isEqualTo(
                DUMMY_ENABLED_INPUT_METHOD_ID_DEFAULT);
    }

    private static InputMethodInfo createMockInputMethodInfoWithSubtypes(
            PackageManager packageManager, InputMethodManager inputMethodManager,
            String packageName) {
        InputMethodInfo mockInfo = createMockInputMethodInfo(packageManager, packageName);
        List<InputMethodSubtype> subtypes = createSubtypes();
        when(inputMethodManager.getEnabledInputMethodSubtypeList(
                eq(mockInfo), anyBoolean())).thenReturn(subtypes);
        return mockInfo;
    }

    private static InputMethodInfo createMockInputMethodInfo(
            PackageManager packageManager, String packageName) {
        InputMethodInfo mockInfo = mock(InputMethodInfo.class);
        when(mockInfo.getPackageName()).thenReturn(packageName);
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

    private Set<String> splitConcatenatedIdsIntoSet(String ids) {
        Set<String> result = new HashSet<>();

        if (ids == null || ids.isEmpty()) {
            return result;
        }

        InputMethodUtil.sInputMethodSplitter.setString(ids);
        while (InputMethodUtil.sInputMethodSplitter.hasNext()) {
            result.add(InputMethodUtil.sInputMethodSplitter.next());
        }

        return result;
    }
}
