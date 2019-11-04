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

package com.android.car.dialer.telecom;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.Context;

import com.android.car.dialer.CarDialerRobolectricTestRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

@RunWith(CarDialerRobolectricTestRunner.class)
public class UiBluetoothMonitorTest {

    private Context mContext;
    private UiBluetoothMonitor mUiBluetoothMonitor;

    @Before
    public void setup() {
        mContext = RuntimeEnvironment.application;
        mUiBluetoothMonitor = UiBluetoothMonitor.init(mContext);
    }

    @Test
    public void testInit_initTwice_ThrowException() {
        assertNotNull(mUiBluetoothMonitor);

        try {
            UiBluetoothMonitor.init(mContext);
            fail();
        } catch (IllegalStateException e) {
            // This is expected.
        }
    }

    @Test
    public void testGet() {
        assertThat(UiBluetoothMonitor.get()).isEqualTo(mUiBluetoothMonitor);
    }

    @After
    public void tearDown() {
        mUiBluetoothMonitor.tearDown();
    }
}
