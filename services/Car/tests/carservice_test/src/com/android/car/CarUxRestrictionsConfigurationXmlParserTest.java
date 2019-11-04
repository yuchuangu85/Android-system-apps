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
package com.android.car;

import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_IDLING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_MOVING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_PARKED;
import static android.car.drivingstate.CarUxRestrictionsManager.UX_RESTRICTION_MODE_BASELINE;
import static android.car.drivingstate.CarUxRestrictionsManager.UX_RESTRICTION_MODE_PASSENGER;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsConfiguration;
import android.content.Context;
import android.util.ArraySet;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class CarUxRestrictionsConfigurationXmlParserTest {
    private Context getContext() {
        return InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testParsingDefaultConfiguration() throws IOException, XmlPullParserException {
        CarUxRestrictionsConfigurationXmlParser.parse(getContext(), R.xml.car_ux_restrictions_map);
    }

    @Test
    public void testParsingParameters() throws IOException, XmlPullParserException {
        CarUxRestrictionsConfiguration config = CarUxRestrictionsConfigurationXmlParser.parse(
                getContext(), R.xml.ux_restrictions_only_parameters).get(0);

        CarUxRestrictions r = config.getUxRestrictions(DRIVING_STATE_PARKED, 0f);
        assertEquals(1, r.getMaxContentDepth());
        assertEquals(1, r.getMaxCumulativeContentItems());
        assertEquals(1, r.getMaxRestrictedStringLength());
    }

    @Test
    public void testParsingNonMovingState() throws IOException, XmlPullParserException {
        CarUxRestrictionsConfiguration config = CarUxRestrictionsConfigurationXmlParser.parse(
                getContext(), R.xml.ux_restrictions_non_moving_state).get(0);

        CarUxRestrictions parked = config.getUxRestrictions(DRIVING_STATE_PARKED, 0f);
        assertFalse(parked.isRequiresDistractionOptimization());

        CarUxRestrictions idling = config.getUxRestrictions(DRIVING_STATE_IDLING, 0f);
        assertTrue(idling.isRequiresDistractionOptimization());
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO, idling.getActiveRestrictions());
    }

    @Test
    public void testParsingMovingState_NoSpeedRange() throws IOException, XmlPullParserException {
        CarUxRestrictionsConfiguration config = CarUxRestrictionsConfigurationXmlParser.parse(
                getContext(), R.xml.ux_restrictions_moving_state_no_speed_range).get(0);

        CarUxRestrictions r = config.getUxRestrictions(DRIVING_STATE_MOVING, 1f);
        assertTrue(r.isRequiresDistractionOptimization());
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO, r.getActiveRestrictions());
    }

    @Test
    public void testParsingMovingState_SingleSpeedRange()
            throws IOException, XmlPullParserException {
        CarUxRestrictionsConfiguration config = CarUxRestrictionsConfigurationXmlParser.parse(
                getContext(), R.xml.ux_restrictions_moving_state_single_speed_range).get(0);

        CarUxRestrictions r = config.getUxRestrictions(DRIVING_STATE_MOVING, 1f);
        assertTrue(r.isRequiresDistractionOptimization());
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO, r.getActiveRestrictions());
    }

    @Test
    public void testParsingMovingState_MultiSpeedRange()
            throws IOException, XmlPullParserException {
        CarUxRestrictionsConfiguration config = CarUxRestrictionsConfigurationXmlParser.parse(
                getContext(), R.xml.ux_restrictions_moving_state_single_speed_range).get(0);

        CarUxRestrictions slow = config.getUxRestrictions(DRIVING_STATE_MOVING, 1f);
        assertTrue(slow.isRequiresDistractionOptimization());
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO, slow.getActiveRestrictions());

        CarUxRestrictions fast = config.getUxRestrictions(DRIVING_STATE_MOVING, 6f);
        assertTrue(fast.isRequiresDistractionOptimization());
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO, fast.getActiveRestrictions());
    }

    @Test
    public void testParsingPassengerState() throws IOException, XmlPullParserException {
        CarUxRestrictionsConfiguration config = CarUxRestrictionsConfigurationXmlParser.parse(
                getContext(), R.xml.ux_restrictions_passenger_mode).get(0);

        CarUxRestrictions moving = config.getUxRestrictions(
                DRIVING_STATE_MOVING, 1f, UX_RESTRICTION_MODE_PASSENGER);
        assertFalse(moving.isRequiresDistractionOptimization());

        CarUxRestrictions idling = config.getUxRestrictions(
                DRIVING_STATE_IDLING, 0f, UX_RESTRICTION_MODE_PASSENGER);
        assertFalse(idling.isRequiresDistractionOptimization());

        CarUxRestrictions parked = config.getUxRestrictions(
                DRIVING_STATE_PARKED, 0f, UX_RESTRICTION_MODE_PASSENGER);
        assertFalse(parked.isRequiresDistractionOptimization());
    }

    @Test
    public void testParsingPassengerMode_ValuesInBaselineAreNotAffected()
            throws IOException, XmlPullParserException {
        CarUxRestrictionsConfiguration config = CarUxRestrictionsConfigurationXmlParser.parse(
                getContext(), R.xml.ux_restrictions_passenger_mode).get(0);

        CarUxRestrictions moving = config.getUxRestrictions(
                DRIVING_STATE_MOVING, 1f, UX_RESTRICTION_MODE_BASELINE);
        assertTrue(moving.isRequiresDistractionOptimization());
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO, moving.getActiveRestrictions());

        CarUxRestrictions idling = config.getUxRestrictions(
                DRIVING_STATE_IDLING, 0f, UX_RESTRICTION_MODE_BASELINE);
        assertTrue(idling.isRequiresDistractionOptimization());
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO, idling.getActiveRestrictions());

        CarUxRestrictions parked = config.getUxRestrictions(
                DRIVING_STATE_PARKED, 0f, UX_RESTRICTION_MODE_BASELINE);
        assertTrue(parked.isRequiresDistractionOptimization());
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO, parked.getActiveRestrictions());
    }

    @Test
    public void testParsingMultipleConfigurations()
            throws IOException, XmlPullParserException {
        List<CarUxRestrictionsConfiguration> configs =
                CarUxRestrictionsConfigurationXmlParser.parse(
                        getContext(), R.xml.ux_restrictions_multiple_display_ports);

        assertEquals(configs.size(), 2);

        // 1 and 2 are specified in test xml.
        Set<Byte> expected = new ArraySet<>();
        expected.add((byte) 1);
        expected.add((byte) 2);
        for (CarUxRestrictionsConfiguration config: configs) {
            assertTrue(expected.contains(config.getPhysicalPort()));
        }
    }

    @Test
    public void testMultipleConfigurationsShareParameters()
            throws IOException, XmlPullParserException {
        List<CarUxRestrictionsConfiguration> configs =
                CarUxRestrictionsConfigurationXmlParser.parse(
                        getContext(), R.xml.ux_restrictions_multiple_display_ports);

        for (CarUxRestrictionsConfiguration config: configs) {
            CarUxRestrictions r = config.getUxRestrictions(DRIVING_STATE_PARKED, 0f);
            assertEquals(1, r.getMaxContentDepth());
            assertEquals(1, r.getMaxCumulativeContentItems());
            assertEquals(1, r.getMaxRestrictedStringLength());
        }
    }
}
