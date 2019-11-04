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

package com.android.car.settings.units;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.preference.ListPreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowCar;
import com.android.car.settings.testutils.ShadowCarUnitsManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCar.class, ShadowCarUnitsManager.class})
public class UnitsBasePreferenceControllerTest {

    private static final int TEST_PROPERTY_ID = -1;
    private static final Unit[] AVAILABLE_UNITS =
            {UnitsMap.YEAR, UnitsMap.SECS, UnitsMap.NANO_SECS};
    private static final Unit DEFAULT_UNIT = UnitsMap.YEAR;

    private Context mContext;
    private ListPreference mPreference;
    private PreferenceControllerTestHelper<TestUnitsBasePreferenceController> mControllerHelper;
    private TestUnitsBasePreferenceController mController;
    private CarUnitsManager mCarUnitsManager;

    @Mock
    private CarPropertyManager mCarPropertyManager;
    @Mock
    private CarPropertyValue mCarPropertyValue;

    private static class TestUnitsBasePreferenceController extends UnitsBasePreferenceController {

        private TestUnitsBasePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected int getPropertyId() {
            return TEST_PROPERTY_ID;
        }


        @Override
        protected Class<ListPreference> getPreferenceType() {
            return ListPreference.class;
        }
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        ShadowCarUnitsManager.setUnitsSupportedByProperty(TEST_PROPERTY_ID, AVAILABLE_UNITS);
        mCarUnitsManager = new CarUnitsManager(mContext);
        mCarUnitsManager.setUnitUsedByProperty(TEST_PROPERTY_ID, DEFAULT_UNIT.getId());
        mPreference = new ListPreference(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(
                mContext, TestUnitsBasePreferenceController.class, mPreference);
        mController = mControllerHelper.getController();
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @After
    public void tearDown() {
        ShadowCarUnitsManager.reset();
    }


    @Test
    public void onCreate_connectsCarUnitsManager() {
        assertThat(ShadowCarUnitsManager.isConnected()).isTrue();
    }

    @Test
    public void onCreate_registersCarServiceListener() {
        assertThat(ShadowCarUnitsManager.getListener())
                .isEqualTo(mController.mOnCarServiceListener);
    }

    @Test
    public void onCreate_preferenceIsConditionallyUnavailable() {
        assertThat(mController.getAvailabilityStatus())
                .isEqualTo(PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onCarServiceConnected_availableUnitsExist_preferenceIsAvailable() {
        mController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(PreferenceController.AVAILABLE);
    }

    @Test
    public void onCarServiceConnected_noAvailableUnits_preferenceIsConditionallyUnavailable() {
        ShadowCarUnitsManager.setUnitsSupportedByProperty(TEST_PROPERTY_ID, null);
        mController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);

        assertThat(mController.getAvailabilityStatus())
                .isEqualTo(PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onCarServiceConnected_setsEntriesOfSupportedUnits() {
        mController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);
        CharSequence[] expectedEntries = mController.getEntriesOfSupportedUnits();

        assertThat(mPreference.getEntries()).isEqualTo(expectedEntries);
    }

    @Test
    public void onCarServiceConnected_setsSupportedUnitsIdsAsEntryValues() {
        mController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);
        CharSequence[] expectedEntryValues = mController.getIdsOfSupportedUnits();

        assertThat(mPreference.getEntryValues()).isEqualTo(expectedEntryValues);
    }

    @Test
    public void onCarServiceConnected_setsUnitBeingUsedAsPreferenceValue() {
        mController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);
        String expectedValue = Integer.toString(DEFAULT_UNIT.getId());

        assertThat(mPreference.getValue()).isEqualTo(expectedValue);
    }

    @Test
    public void onPreferenceChanged_runsSetUnitUsedByPropertyWithNewUnit() {
        mController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);
        mController.handlePreferenceChanged(mPreference, Integer.toString(UnitsMap.SECS.getId()));

        assertThat(mCarUnitsManager.getUnitUsedByProperty(TEST_PROPERTY_ID))
                .isEqualTo(UnitsMap.SECS);
    }

    @Test
    public void onPropertyChanged_propertyStatusIsAvailable_setsNewUnitIdAsValue() {
        mController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);
        Unit newUnit = UnitsMap.SECS;
        when(mCarPropertyValue.getStatus()).thenReturn(CarPropertyValue.STATUS_AVAILABLE);
        when(mCarPropertyValue.getValue()).thenReturn(newUnit.getId());
        mController.mCarPropertyEventCallback.onChangeEvent(mCarPropertyValue);

        assertThat(mPreference.getValue()).isEqualTo(Integer.toString(newUnit.getId()));
    }

    @Test
    public void onPropertyChanged_propertyStatusIsAvailable_setsNewUnitAbbreviationAsSummary() {
        mController.mOnCarServiceListener.handleServiceConnected(mCarPropertyManager);
        Unit newUnit = UnitsMap.SECS;
        when(mCarPropertyValue.getStatus()).thenReturn(CarPropertyValue.STATUS_AVAILABLE);
        when(mCarPropertyValue.getValue()).thenReturn(newUnit.getId());
        mController.mCarPropertyEventCallback.onChangeEvent(mCarPropertyValue);

        assertThat(mPreference.getSummary())
                .isEqualTo(mContext.getString(newUnit.getAbbreviationResId()));
    }

    @Test
    public void onDestroy_disconnectsCarUnitsManager() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_DESTROY);

        assertThat(ShadowCarUnitsManager.isConnected()).isFalse();
    }

    @Test
    public void onDestroy_unregistersCarServiceListener() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_DESTROY);

        assertThat(ShadowCarUnitsManager.getListener()).isNull();
    }
}
