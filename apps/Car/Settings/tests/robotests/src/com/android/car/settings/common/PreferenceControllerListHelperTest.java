/*
 * Copyright 2018 The Android Open Source Project
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

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertThrows;

import android.car.drivingstate.CarUxRestrictions;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit test for {@link PreferenceControllerListHelper}.
 */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class PreferenceControllerListHelperTest {

    private static final CarUxRestrictions UX_RESTRICTIONS =
            new CarUxRestrictions.Builder(/* reqOpt= */ true,
                    CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

    @Test
    public void getControllers_returnsList() {
        List<String> validKeys = Arrays.asList("key1", "key2");
        List<PreferenceController> controllers =
                PreferenceControllerListHelper.getPreferenceControllersFromXml(
                        RuntimeEnvironment.application,
                        R.xml.preference_controller_list_helper_success,
                        mock(FragmentController.class),
                        UX_RESTRICTIONS);

        assertThat(controllers).hasSize(validKeys.size());
        List<String> foundKeys = new ArrayList<>();
        for (PreferenceController controller : controllers) {
            assertThat(controller).isInstanceOf(DefaultRestrictionsPreferenceController.class);
            foundKeys.add(controller.getPreferenceKey());
        }
        assertThat(foundKeys).containsAllIn(validKeys);
    }

    @Test
    public void getControllers_invalidController_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> PreferenceControllerListHelper.getPreferenceControllersFromXml(
                        RuntimeEnvironment.application,
                        R.xml.preference_controller_list_helper_fail_invalid_controller,
                        mock(FragmentController.class), UX_RESTRICTIONS));
    }

    @Test
    public void getControllers_missingKey_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> PreferenceControllerListHelper.getPreferenceControllersFromXml(
                        RuntimeEnvironment.application,
                        R.xml.preference_controller_list_helper_fail_missing_key,
                        mock(FragmentController.class), UX_RESTRICTIONS));
    }
}
