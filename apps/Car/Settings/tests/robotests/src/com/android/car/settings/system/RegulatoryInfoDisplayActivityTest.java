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

package com.android.car.settings.system;

import static com.google.common.truth.Truth.assertThat;

import android.os.SystemProperties;

import com.android.car.settings.CarSettingsRobolectricTestRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;

/** Unit test for {@link RegulatoryInfoDisplayActivity}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class RegulatoryInfoDisplayActivityTest {

    private ActivityController<RegulatoryInfoDisplayActivity> mActivityController;
    private RegulatoryInfoDisplayActivity mActivity;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mActivityController = ActivityController.of(new RegulatoryInfoDisplayActivity());
        mActivity = mActivityController.get();
        mActivityController.create();
    }

    @After
    public void tearDown() {
        ShadowAlertDialog.reset();
    }

    @Test
    public void getRegulatoryInfoImageFileName_skuIsNotEmpty() {
        SystemProperties.set("ro.boot.hardware.sku", "test");

        assertThat(mActivity.getRegulatoryInfoImageFileName())
                .isEqualTo("/data/misc/elabel/regulatory_info_test.png");
    }

    @Test
    public void getRegulatoryInfoImageFileName_skuIsEmpty() {
        SystemProperties.set("ro.boot.hardware.sku", "");

        assertThat(mActivity.getRegulatoryInfoImageFileName())
                .isEqualTo("/data/misc/elabel/regulatory_info.png");
    }

    @Test
    public void getSku_shouldReturnSystemProperty() {
        String testSku = "test";
        SystemProperties.set("ro.boot.hardware.sku", testSku);

        assertThat(mActivity.getSku()).isEqualTo(testSku);
    }
}
