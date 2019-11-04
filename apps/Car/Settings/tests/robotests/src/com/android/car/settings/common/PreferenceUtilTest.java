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

package com.android.car.settings.common;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class PreferenceUtilTest {

    @Test
    public void testCheckPreferenceType_true() {
        Preference preference = new SwitchPreference(RuntimeEnvironment.application);
        assertThat(PreferenceUtil.checkPreferenceType(preference, SwitchPreference.class)).isTrue();
    }

    @Test
    public void testCheckPreferenceType_superclass_true() {
        Preference preference = new SwitchPreference(RuntimeEnvironment.application);
        assertThat(
                PreferenceUtil.checkPreferenceType(preference, TwoStatePreference.class)).isTrue();
    }

    @Test
    public void testCheckPreferenceType_false() {
        Preference preference = new ListPreference(RuntimeEnvironment.application);
        assertThat(
                PreferenceUtil.checkPreferenceType(preference, TwoStatePreference.class)).isFalse();
    }

    // Test should succeed without throwing an exception.
    @Test
    public void testRequirePreferenceType_true() {
        Preference preference = new SwitchPreference(RuntimeEnvironment.application);
        PreferenceUtil.requirePreferenceType(preference, SwitchPreference.class);
    }

    // Test should succeed without throwing an exception.
    @Test
    public void testRequirePreferenceType_superclass_true() {
        Preference preference = new SwitchPreference(RuntimeEnvironment.application);
        PreferenceUtil.requirePreferenceType(preference, TwoStatePreference.class);
    }

    @Test
    public void testRequirePreferenceType_false() {
        Preference preference = new ListPreference(RuntimeEnvironment.application);
        assertThrows(IllegalArgumentException.class,
                () -> PreferenceUtil.requirePreferenceType(preference, TwoStatePreference.class));
    }
}
