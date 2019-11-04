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
 * limitations under the License
 */

package com.android.managedprovisioning.analytics;

import android.content.Context;

import com.android.managedprovisioning.common.SettingsFacade;

import com.google.common.truth.Truth;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Robolectric integration tests for {@link MetricsWriterFactory}.
 */
@RunWith(RobolectricTestRunner.class)
public class MetricsWriterFactoryRoboTest {

    private static final SettingsFacade SETTINGS_FACADE_DURING_SETUP_WIZARD =
            new SettingsFacade() {
                @Override
                public boolean isDuringSetupWizard(Context context) {
                    return true;
                }
            };

    private static final SettingsFacade SETTINGS_FACADE_POST_SETUP_WIZARD = new SettingsFacade() {
        @Override
        public boolean isDuringSetupWizard(Context context) {
            return false;
        }
    };

    @Test
    public void getMetricsWriter_duringSetupWizard_returnsDeferredMetricsWriter() {
        Truth.assertThat(MetricsWriterFactory.getMetricsWriter(
                RuntimeEnvironment.application, SETTINGS_FACADE_DURING_SETUP_WIZARD))
                        .isInstanceOf(DeferredMetricsWriter.class);
    }

    @Test
    public void getMetricsWriter_postSetupWizard_returnsInstantMetricsWriter() {
        Truth.assertThat(MetricsWriterFactory.getMetricsWriter(
                RuntimeEnvironment.application, SETTINGS_FACADE_POST_SETUP_WIZARD))
                        .isInstanceOf(InstantMetricsWriter.class);
    }
}
