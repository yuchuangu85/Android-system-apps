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

package android.car;

import static android.car.VehiclePropertyIds.HVAC_FAN_SPEED;
import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_CURRENT;

import static com.google.common.truth.Truth.assertThat;

import android.car.hardware.property.CarPropertyManager;
import android.car.testapi.CarPropertyController;
import android.car.testapi.FakeCar;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public class CarPropertyManagerTest {
    private static final int FAN_SPEED_VALUE = 42;
    private static final float TEMPERATURE_VALUE = 42.24f;

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    private CarPropertyController mController;
    private CarPropertyManager mManager;

    @Before
    public void setUp() {
        FakeCar fakeCar = FakeCar.createFakeCar(ApplicationProvider.getApplicationContext());
        mController = fakeCar.getCarPropertyController();
        Car car = fakeCar.getCar();
        mManager = (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
        assertThat(mManager).isNotNull();
    }

    @Test
    public void carPropertyManager_int() {
        mController.addProperty(HVAC_FAN_SPEED, FAN_SPEED_VALUE);
        assertThat(mManager.getIntProperty(HVAC_FAN_SPEED, 0)).isEqualTo(FAN_SPEED_VALUE);
    }

    @Test
    public void carPropertyManager_intThrows() {
        mController.addProperty(HVAC_FAN_SPEED, FAN_SPEED_VALUE);
        try {
            mManager.getFloatProperty(HVAC_FAN_SPEED, 0);
        } catch (IllegalArgumentException expected) {
            // Expected, the property is an integer value.
        }
    }

    @Test
    public void carPropertyManager_float() {
        mController.addProperty(HVAC_TEMPERATURE_CURRENT, TEMPERATURE_VALUE);
        assertThat(mManager.getFloatProperty(HVAC_TEMPERATURE_CURRENT, 0))
                .isEqualTo(TEMPERATURE_VALUE);
    }

    @Test
    public void carPropertyManager_floatThrows() {
        mController.addProperty(HVAC_TEMPERATURE_CURRENT, TEMPERATURE_VALUE);
        try {
            mManager.getIntProperty(HVAC_TEMPERATURE_CURRENT, 0);
        } catch (IllegalArgumentException expected) {
            // Expected, the property is an integer value.
        }
    }
}
