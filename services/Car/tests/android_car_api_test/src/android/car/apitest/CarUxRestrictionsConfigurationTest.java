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

package android.car.apitest;

import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_IDLING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_MOVING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_PARKED;
import static android.car.drivingstate.CarUxRestrictions.UX_RESTRICTIONS_BASELINE;
import static android.car.drivingstate.CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED;
import static android.car.drivingstate.CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO;
import static android.car.drivingstate.CarUxRestrictionsConfiguration.Builder.SpeedRange.MAX_SPEED;
import static android.car.drivingstate.CarUxRestrictionsManager.UX_RESTRICTION_MODE_BASELINE;
import static android.car.drivingstate.CarUxRestrictionsManager.UX_RESTRICTION_MODE_PASSENGER;

import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsConfiguration;
import android.car.drivingstate.CarUxRestrictionsConfiguration.Builder;
import android.car.drivingstate.CarUxRestrictionsConfiguration.DrivingStateRestrictions;
import android.os.Parcel;
import android.util.JsonReader;
import android.util.JsonWriter;

import androidx.test.filters.SmallTest;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/**
 * Unit test for UXR config and its subclasses.
 */
@SmallTest
public class CarUxRestrictionsConfigurationTest extends TestCase {

    // This test verifies the expected way to build config would succeed.
    public void testConstruction() {
        new Builder().build();

        new Builder()
                .setMaxStringLength(1)
                .build();

        new Builder()
                .setUxRestrictions(DRIVING_STATE_PARKED, false, UX_RESTRICTIONS_BASELINE)
                .build();

        new Builder()
                .setUxRestrictions(DRIVING_STATE_MOVING, true, UX_RESTRICTIONS_FULLY_RESTRICTED)
                .build();

        new Builder()
                .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                        .setDistractionOptimizationRequired(true)
                        .setRestrictions(UX_RESTRICTIONS_FULLY_RESTRICTED)
                        .setSpeedRange(new Builder.SpeedRange(0f, MAX_SPEED)))
                .build();

        new Builder()
                .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                        .setDistractionOptimizationRequired(true)
                        .setRestrictions(UX_RESTRICTIONS_FULLY_RESTRICTED)
                        .setSpeedRange(new Builder.SpeedRange(0f, 1f)))
                .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                        .setDistractionOptimizationRequired(true)
                        .setRestrictions(UX_RESTRICTIONS_FULLY_RESTRICTED)
                        .setSpeedRange(new Builder.SpeedRange(1f, MAX_SPEED)))
                .build();
    }

    public void testUnspecifiedDrivingStateUsesDefaultRestriction() {
        CarUxRestrictionsConfiguration config = new Builder().build();

        CarUxRestrictions parkedRestrictions = config.getUxRestrictions(DRIVING_STATE_PARKED, 0f);
        assertTrue(parkedRestrictions.isRequiresDistractionOptimization());
        assertEquals(parkedRestrictions.getActiveRestrictions(), UX_RESTRICTIONS_FULLY_RESTRICTED);

        CarUxRestrictions movingRestrictions = config.getUxRestrictions(DRIVING_STATE_MOVING, 1f);
        assertTrue(movingRestrictions.isRequiresDistractionOptimization());
        assertEquals(movingRestrictions.getActiveRestrictions(), UX_RESTRICTIONS_FULLY_RESTRICTED);
    }

    public void testBuilderValidation_UnspecifiedStateUsesRestrictiveDefault() {
        CarUxRestrictionsConfiguration config = new Builder()
                .setUxRestrictions(DRIVING_STATE_MOVING, true, UX_RESTRICTIONS_FULLY_RESTRICTED)
                .build();
        assertTrue(config.getUxRestrictions(DRIVING_STATE_PARKED, 0f)
                .isRequiresDistractionOptimization());
        assertTrue(config.getUxRestrictions(DRIVING_STATE_IDLING, 0f)
                .isRequiresDistractionOptimization());
    }

    public void testBuilderValidation_NonMovingStateHasOneRestriction() {
        Builder builder = new Builder();
        builder.setUxRestrictions(DRIVING_STATE_IDLING,
                true, UX_RESTRICTIONS_NO_VIDEO);
        builder.setUxRestrictions(DRIVING_STATE_IDLING,
                false, UX_RESTRICTIONS_BASELINE);
        try {
            builder.build();
            fail();
        } catch (Exception e) {
            // Expected exception.
        }
    }

    public void testBuilderValidation_PassengerModeNoSpeedRangeOverlap() {
        Builder builder = new Builder();
        builder.setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                    .setDistractionOptimizationRequired(true)
                    .setRestrictions(UX_RESTRICTIONS_FULLY_RESTRICTED)
                    .setSpeedRange(new Builder.SpeedRange(1f, 2f)));
        builder.setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                    .setDistractionOptimizationRequired(true)
                    .setRestrictions(UX_RESTRICTIONS_FULLY_RESTRICTED)
                    .setSpeedRange(new Builder.SpeedRange(1f)));
        try {
            builder.build();
            fail();
        } catch (Exception e) {
            // Expected exception.
        }
    }

    public void testBuilderValidation_PassengerModeCanSpecifySubsetOfSpeedRange() {
        CarUxRestrictionsConfiguration config = new Builder()
                .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                        .setDistractionOptimizationRequired(true)
                        .setRestrictions(UX_RESTRICTIONS_FULLY_RESTRICTED)
                        .setMode(UX_RESTRICTION_MODE_PASSENGER)
                        .setSpeedRange(new Builder.SpeedRange(1f, 2f)))
                .build();

        assertTrue(config.getUxRestrictions(DRIVING_STATE_MOVING, 1f, UX_RESTRICTION_MODE_PASSENGER)
                .isRequiresDistractionOptimization());
    }

    public void testBuilderValidation_MultipleSpeedRange_NonZeroStart() {
        Builder builder = new Builder();
        builder.setUxRestrictions(DRIVING_STATE_MOVING,
                new Builder.SpeedRange(1, 2),
                true, UX_RESTRICTIONS_FULLY_RESTRICTED);
        builder.setUxRestrictions(DRIVING_STATE_MOVING,
                new Builder.SpeedRange(2, MAX_SPEED),
                true, UX_RESTRICTIONS_FULLY_RESTRICTED);
        try {
            builder.build();
            fail();
        } catch (Exception e) {
            // Expected exception.
        }
    }

    public void testBuilderValidation_SpeedRange_NonZeroStart() {
        Builder builder = new Builder();
        builder.setUxRestrictions(DRIVING_STATE_MOVING,
                new Builder.SpeedRange(1, MAX_SPEED),
                true, UX_RESTRICTIONS_FULLY_RESTRICTED);
        try {
            builder.build();
            fail();
        } catch (Exception e) {
            // Expected exception.
        }
    }

    public void testBuilderValidation_SpeedRange_Overlap() {
        Builder builder = new Builder();
        builder.setUxRestrictions(DRIVING_STATE_MOVING,
                new Builder.SpeedRange(0, 5), true,
                UX_RESTRICTIONS_FULLY_RESTRICTED);
        builder.setUxRestrictions(DRIVING_STATE_MOVING,
                new Builder.SpeedRange(4), true,
                UX_RESTRICTIONS_FULLY_RESTRICTED);
        try {
            builder.build();
            fail();
        } catch (Exception e) {
            // Expected exception.
        }
    }

    public void testBuilderValidation_SpeedRange_Gap() {
        Builder builder = new Builder();
        builder.setUxRestrictions(DRIVING_STATE_MOVING,
                new Builder.SpeedRange(0, 5), true,
                UX_RESTRICTIONS_FULLY_RESTRICTED);
        builder.setUxRestrictions(DRIVING_STATE_MOVING,
                new Builder.SpeedRange(8), true,
                UX_RESTRICTIONS_FULLY_RESTRICTED);
        try {
            builder.build();
            fail();
        } catch (Exception e) {
            // Expected exception.
        }
    }

    public void testBuilderValidation_NonMovingStateCannotUseSpeedRange() {
        Builder builder = new Builder();
        try {
            builder.setUxRestrictions(DRIVING_STATE_PARKED,
                    new Builder.SpeedRange(0, 5), true,
                    UX_RESTRICTIONS_FULLY_RESTRICTED);
            fail();
        } catch (Exception e) {
            // Expected exception.
        }
    }

    public void testBuilderValidation_MultipleMovingRestrictionsShouldAllContainSpeedRange() {
        Builder builder = new Builder();
        builder.setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                .setDistractionOptimizationRequired(true)
                .setRestrictions(UX_RESTRICTIONS_FULLY_RESTRICTED));
        builder.setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                .setDistractionOptimizationRequired(true)
                .setRestrictions(UX_RESTRICTIONS_FULLY_RESTRICTED)
                .setSpeedRange(new Builder.SpeedRange(1f)));
        try {
            builder.build();
            fail();
        } catch (Exception e) {
            // Expected exception.
        }
    }

    public void testSpeedRange_Construction() {
        new Builder.SpeedRange(0f);
        new Builder.SpeedRange(0f, 1f);
        new Builder.SpeedRange(0f, MAX_SPEED);
    }

    public void testSpeedRange_NoNegativeMin() {
        try {
            new Builder.SpeedRange(-2f, 1f);
        } catch (Exception e) {
            // Expected exception.
        }
    }

    public void testSpeedRange_NoNegativeMax() {
        try {
            new Builder.SpeedRange(2f, -1f);
        } catch (Exception e) {
            // Expected exception.
        }
    }

    public void testSpeedRange_MinCannotBeMaxSpeed() {
        try {
            new Builder.SpeedRange(MAX_SPEED, 1f);
        } catch (Exception e) {
            // Expected exception.
        }
    }

    public void testSpeedRange_MinGreaterThanMax() {
        try {
            new Builder.SpeedRange(5f, 2f);
        } catch (Exception e) {
            // Expected exception.
        }
    }

    public void testSpeedRangeComparison_DifferentMin() {
        Builder.SpeedRange s1 =
                new Builder.SpeedRange(1f);
        Builder.SpeedRange s2 =
                new Builder.SpeedRange(2f);
        assertTrue(s1.compareTo(s2) < 0);
        assertTrue(s2.compareTo(s1) > 0);
    }

    public void testSpeedRangeComparison_SameMin() {
        Builder.SpeedRange s1 =
                new Builder.SpeedRange(1f);
        Builder.SpeedRange s2 =
                new Builder.SpeedRange(1f);
        assertTrue(s1.compareTo(s2) == 0);
    }

    public void testSpeedRangeComparison_SameMinDifferentMax() {
        Builder.SpeedRange s1 =
                new Builder.SpeedRange(0f, 1f);
        Builder.SpeedRange s2 =
                new Builder.SpeedRange(0f, 2f);
        assertTrue(s1.compareTo(s2) < 0);
        assertTrue(s2.compareTo(s1) > 0);
    }

    public void testSpeedRangeComparison_MaxSpeed() {
        Builder.SpeedRange s1 =
                new Builder.SpeedRange(0f, 1f);
        Builder.SpeedRange s2 =
                new Builder.SpeedRange(0f);
        assertTrue(s1.compareTo(s2) < 0);
        assertTrue(s2.compareTo(s1) > 0);
    }

    public void testSpeedRangeEquals() {
        Builder.SpeedRange s1, s2;

        s1 = new Builder.SpeedRange(0f);
        assertTrue(s1.equals(s1));

        s1 = new Builder.SpeedRange(1f);
        s2 = new Builder.SpeedRange(1f);
        assertTrue(s1.compareTo(s2) == 0);
        assertTrue(s1.equals(s2));

        s1 = new Builder.SpeedRange(0f, 1f);
        s2 = new Builder.SpeedRange(0f, 1f);
        assertTrue(s1.equals(s2));

        s1 = new Builder.SpeedRange(0f, MAX_SPEED);
        s2 = new Builder.SpeedRange(0f, MAX_SPEED);
        assertTrue(s1.equals(s2));

        s1 = new Builder.SpeedRange(0f);
        s2 = new Builder.SpeedRange(1f);
        assertFalse(s1.equals(s2));

        s1 = new Builder.SpeedRange(0f, 1f);
        s2 = new Builder.SpeedRange(0f, 2f);
        assertFalse(s1.equals(s2));
    }

    public void testJsonSerialization_DefaultConstructor() {
        CarUxRestrictionsConfiguration config =
                new Builder().build();

        verifyConfigThroughJsonSerialization(config);
    }

    public void testJsonSerialization_RestrictionParameters() {
        CarUxRestrictionsConfiguration config = new Builder()
                .setMaxStringLength(1)
                .setMaxCumulativeContentItems(1)
                .setMaxContentDepth(1)
                .build();

        verifyConfigThroughJsonSerialization(config);
    }

    public void testJsonSerialization_NonMovingStateRestrictions() {
        CarUxRestrictionsConfiguration config = new Builder()
                .setUxRestrictions(DRIVING_STATE_PARKED, false, UX_RESTRICTIONS_BASELINE)
                .build();

        verifyConfigThroughJsonSerialization(config);
    }

    public void testJsonSerialization_MovingStateNoSpeedRange() {
        CarUxRestrictionsConfiguration config = new Builder()
                .setUxRestrictions(DRIVING_STATE_MOVING, true, UX_RESTRICTIONS_FULLY_RESTRICTED)
                .build();

        verifyConfigThroughJsonSerialization(config);
    }

    public void testJsonSerialization_MovingStateWithSpeedRange() {
        CarUxRestrictionsConfiguration config = new Builder()
                .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                        .setDistractionOptimizationRequired(true)
                        .setRestrictions(UX_RESTRICTIONS_FULLY_RESTRICTED)
                        .setSpeedRange(new Builder.SpeedRange(0f, 5f)))
                .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                        .setDistractionOptimizationRequired(true)
                        .setRestrictions(UX_RESTRICTIONS_FULLY_RESTRICTED)
                        .setSpeedRange(new Builder.SpeedRange(5f, MAX_SPEED)))
                .build();

        verifyConfigThroughJsonSerialization(config);
    }

    public void testJsonSerialization_UxRestrictionMode() {
        CarUxRestrictionsConfiguration config = new Builder()
                // Passenger mode
                .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                        .setDistractionOptimizationRequired(false)
                        .setRestrictions(UX_RESTRICTIONS_BASELINE)
                        .setMode(UX_RESTRICTION_MODE_PASSENGER))
                // Explicitly specify baseline mode
                .setUxRestrictions(DRIVING_STATE_PARKED, new DrivingStateRestrictions()
                        .setDistractionOptimizationRequired(true)
                        .setRestrictions(UX_RESTRICTIONS_NO_VIDEO)
                        .setMode(UX_RESTRICTION_MODE_BASELINE))
                // Implicitly defaults to baseline mode
                .setUxRestrictions(DRIVING_STATE_IDLING, new DrivingStateRestrictions()
                        .setDistractionOptimizationRequired(true)
                        .setRestrictions(UX_RESTRICTIONS_NO_VIDEO))
                .build();

        verifyConfigThroughJsonSerialization(config);
    }

    public void testDump() {
        CarUxRestrictionsConfiguration[] configs = new CarUxRestrictionsConfiguration[] {
                // Driving state with no speed range
                new Builder()
                        .setUxRestrictions(DRIVING_STATE_PARKED, false, UX_RESTRICTIONS_BASELINE)
                        .setUxRestrictions(DRIVING_STATE_IDLING, true, UX_RESTRICTIONS_NO_VIDEO)
                        .setUxRestrictions(DRIVING_STATE_MOVING, true, UX_RESTRICTIONS_NO_VIDEO)
                        .build(),
                // Parameters
                new Builder()
                        .setMaxStringLength(1)
                        .setMaxContentDepth(1)
                        .setMaxCumulativeContentItems(1)
                        .build(),
                // Driving state with single speed range
                new Builder()
                        .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                                .setDistractionOptimizationRequired(true)
                                .setRestrictions(UX_RESTRICTIONS_NO_VIDEO)
                                .setSpeedRange(new Builder.SpeedRange(0f)))
                        .build(),
                // Driving state with multiple speed ranges
                new Builder()
                        .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                                .setDistractionOptimizationRequired(true)
                                .setRestrictions(UX_RESTRICTIONS_NO_VIDEO)
                                .setSpeedRange(new Builder.SpeedRange(0f, 1f)))
                        .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                                .setDistractionOptimizationRequired(true)
                                .setRestrictions(UX_RESTRICTIONS_NO_VIDEO)
                                .setSpeedRange(new Builder.SpeedRange(1f)))
                        .build(),
                // Driving state with passenger mode
                new Builder()
                        .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                                .setDistractionOptimizationRequired(false)
                                .setRestrictions(UX_RESTRICTIONS_BASELINE)
                                .setMode(UX_RESTRICTION_MODE_PASSENGER))
                        .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                                .setDistractionOptimizationRequired(true)
                                .setRestrictions(UX_RESTRICTIONS_NO_VIDEO)
                                .setMode(UX_RESTRICTION_MODE_BASELINE))
                        .build(),
        };

        for (CarUxRestrictionsConfiguration config : configs) {
            config.dump(new PrintWriter(new ByteArrayOutputStream()));
        }
    }

    public void testDumpContainsNecessaryInfo() {
        CarUxRestrictionsConfiguration config = new Builder()
                .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                        .setDistractionOptimizationRequired(true)
                        .setRestrictions(UX_RESTRICTIONS_NO_VIDEO)
                        .setSpeedRange(new Builder.SpeedRange(0f, 1f)))
                .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                        .setDistractionOptimizationRequired(true)
                        .setRestrictions(UX_RESTRICTIONS_NO_VIDEO)
                        .setSpeedRange(new Builder.SpeedRange(1f)))
                .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                        .setDistractionOptimizationRequired(false)
                        .setRestrictions(UX_RESTRICTIONS_BASELINE)
                        .setMode(UX_RESTRICTION_MODE_PASSENGER))
                .build();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(output)) {
            config.dump(writer);
        }

        String dump = new String(output.toByteArray());
        assertTrue(dump.contains("Max String length"));
        assertTrue(dump.contains("Max Cumulative Content Items"));
        assertTrue(dump.contains("Max Content depth"));
        assertTrue(dump.contains("State:moving"));
        assertTrue(dump.contains("Speed Range"));
        assertTrue(dump.contains("Requires DO?"));
        assertTrue(dump.contains("Restrictions"));
        assertTrue(dump.contains("Passenger mode"));
        assertTrue(dump.contains("Baseline mode"));
    }

    public void testSetUxRestrictions_UnspecifiedModeDefaultsToBaseline() {
        CarUxRestrictionsConfiguration config = new Builder()
                .setUxRestrictions(DRIVING_STATE_PARKED, new DrivingStateRestrictions()
                        .setDistractionOptimizationRequired(true)
                        .setRestrictions(UX_RESTRICTIONS_NO_VIDEO))
                .build();

        CarUxRestrictions restrictions = config.getUxRestrictions(DRIVING_STATE_PARKED, 0f);
        assertTrue(restrictions.isRequiresDistractionOptimization());
        assertEquals(UX_RESTRICTIONS_NO_VIDEO, restrictions.getActiveRestrictions());

        assertTrue(restrictions.isSameRestrictions(
                config.getUxRestrictions(DRIVING_STATE_PARKED, 0f, UX_RESTRICTIONS_BASELINE)));
    }

    public void testSetUxRestrictions_PassengerMode() {
        CarUxRestrictionsConfiguration config = new Builder()
                .setUxRestrictions(DRIVING_STATE_PARKED, new DrivingStateRestrictions()
                        .setDistractionOptimizationRequired(false)
                        .setRestrictions(UX_RESTRICTIONS_BASELINE)
                        .setMode(UX_RESTRICTION_MODE_PASSENGER))
                .setUxRestrictions(DRIVING_STATE_PARKED, new DrivingStateRestrictions()
                        .setDistractionOptimizationRequired(true)
                        .setRestrictions(UX_RESTRICTIONS_NO_VIDEO))
                .build();

        CarUxRestrictions passenger = config.getUxRestrictions(
                DRIVING_STATE_PARKED, 0f, UX_RESTRICTION_MODE_PASSENGER);
        assertFalse(passenger.isRequiresDistractionOptimization());

        CarUxRestrictions baseline = config.getUxRestrictions(
                DRIVING_STATE_PARKED, 0f, UX_RESTRICTION_MODE_BASELINE);
        assertTrue(baseline.isRequiresDistractionOptimization());
        assertEquals(UX_RESTRICTIONS_NO_VIDEO, baseline.getActiveRestrictions());
    }

    public void testPassengerModeFallbackToBaseline() {
        CarUxRestrictionsConfiguration config = new Builder()
                .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                        .setDistractionOptimizationRequired(true)
                        .setRestrictions(UX_RESTRICTIONS_NO_VIDEO))
                .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                        .setDistractionOptimizationRequired(false)
                        .setRestrictions(UX_RESTRICTIONS_BASELINE)
                        .setMode(UX_RESTRICTION_MODE_PASSENGER)
                        .setSpeedRange(new Builder.SpeedRange(3f)))
                .build();

        // Retrieve at speed within passenger mode range.
        CarUxRestrictions passenger = config.getUxRestrictions(
                DRIVING_STATE_MOVING, 5f, UX_RESTRICTION_MODE_PASSENGER);
        assertFalse(passenger.isRequiresDistractionOptimization());

        // Retrieve with passenger mode but outside speed range
        CarUxRestrictions baseline = config.getUxRestrictions(
                DRIVING_STATE_MOVING, 1f, UX_RESTRICTION_MODE_PASSENGER);
        assertTrue(baseline.isRequiresDistractionOptimization());
        assertEquals(UX_RESTRICTIONS_NO_VIDEO, baseline.getActiveRestrictions());
    }

    public void testHasSameParameters_SameParameters() {
        CarUxRestrictionsConfiguration one = new CarUxRestrictionsConfiguration.Builder()
                .setMaxStringLength(1)
                .setMaxCumulativeContentItems(1)
                .setMaxContentDepth(1)
                .build();

        CarUxRestrictionsConfiguration other = new CarUxRestrictionsConfiguration.Builder()
                .setMaxStringLength(1)
                .setMaxCumulativeContentItems(1)
                .setMaxContentDepth(1)
                .build();

        assertTrue(one.hasSameParameters(other));
    }

    public void testHasSameParameters_DifferentParameters() {
        CarUxRestrictionsConfiguration one = new CarUxRestrictionsConfiguration.Builder()
                .setMaxStringLength(2)
                .setMaxCumulativeContentItems(1)
                .setMaxContentDepth(1)
                .build();

        CarUxRestrictionsConfiguration other = new CarUxRestrictionsConfiguration.Builder()
                .setMaxStringLength(1)
                .setMaxCumulativeContentItems(1)
                .setMaxContentDepth(1)
                .build();

        assertFalse(one.hasSameParameters(other));
    }

    public void testConfigurationEquals() {
        CarUxRestrictionsConfiguration one = new CarUxRestrictionsConfiguration.Builder()
                .setMaxStringLength(2)
                .setMaxCumulativeContentItems(1)
                .setMaxContentDepth(1)
                .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions())
                .setUxRestrictions(DRIVING_STATE_PARKED,
                        new DrivingStateRestrictions().setRestrictions(UX_RESTRICTIONS_NO_VIDEO))
                .build();

        CarUxRestrictionsConfiguration other = new CarUxRestrictionsConfiguration.Builder()
                .setMaxStringLength(2)
                .setMaxCumulativeContentItems(1)
                .setMaxContentDepth(1)
                .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions())
                .setUxRestrictions(DRIVING_STATE_PARKED,
                        new DrivingStateRestrictions().setRestrictions(UX_RESTRICTIONS_NO_VIDEO))
                .build();

        assertTrue(one.equals(other));
        assertTrue(one.hashCode() == other.hashCode());
    }

    public void testConfigurationEquals_DifferentRestrictions() {
        CarUxRestrictionsConfiguration one = new CarUxRestrictionsConfiguration.Builder()
                .setMaxStringLength(2)
                .setMaxCumulativeContentItems(1)
                .setMaxContentDepth(1)
                .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions())
                .setUxRestrictions(DRIVING_STATE_PARKED,
                        new DrivingStateRestrictions().setRestrictions(
                                UX_RESTRICTIONS_FULLY_RESTRICTED))
                .build();

        CarUxRestrictionsConfiguration other = new CarUxRestrictionsConfiguration.Builder()
                .setMaxStringLength(2)
                .setMaxCumulativeContentItems(1)
                .setMaxContentDepth(1)
                .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions())
                .setUxRestrictions(DRIVING_STATE_PARKED,
                        new DrivingStateRestrictions().setRestrictions(UX_RESTRICTIONS_BASELINE))
                .build();

        assertFalse(one.equals(other));
    }

    public void testParcelableConfiguration() {
        CarUxRestrictionsConfiguration config = new CarUxRestrictionsConfiguration.Builder()
                .setPhysicalPort((byte) 1)
                .setMaxStringLength(1)
                .setMaxCumulativeContentItems(1)
                .setMaxContentDepth(1)
                .setUxRestrictions(DRIVING_STATE_PARKED,
                        new DrivingStateRestrictions().setRestrictions(
                                UX_RESTRICTIONS_FULLY_RESTRICTED))
                .setUxRestrictions(DRIVING_STATE_PARKED, new DrivingStateRestrictions()
                        .setRestrictions(UX_RESTRICTIONS_FULLY_RESTRICTED)
                        .setMode(UX_RESTRICTION_MODE_PASSENGER))
                .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions())
                .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions()
                        .setRestrictions(UX_RESTRICTIONS_FULLY_RESTRICTED)
                        .setMode(UX_RESTRICTION_MODE_PASSENGER)
                        .setSpeedRange(new Builder.SpeedRange(0f, 5f)))
                .build();
        Parcel parcel = Parcel.obtain();
        config.writeToParcel(parcel, 0);

        // Reset parcel data position for reading.
        parcel.setDataPosition(0);

        CarUxRestrictionsConfiguration deserialized =
                CarUxRestrictionsConfiguration.CREATOR.createFromParcel(parcel);
        assertEquals(deserialized, config);
    }

    public void testParcelableConfiguration_serializeNullPhysicalPort() {
        // Not setting physical port leaves it null.
        CarUxRestrictionsConfiguration config = new CarUxRestrictionsConfiguration.Builder()
                .setMaxStringLength(1)
                .setMaxCumulativeContentItems(1)
                .setMaxContentDepth(1)
                .setUxRestrictions(DRIVING_STATE_MOVING, new DrivingStateRestrictions())
                .setUxRestrictions(DRIVING_STATE_PARKED,
                        new DrivingStateRestrictions().setRestrictions(
                                UX_RESTRICTIONS_FULLY_RESTRICTED))
                .build();
        Parcel parcel = Parcel.obtain();
        config.writeToParcel(parcel, 0);

        // Reset parcel data position for reading.
        parcel.setDataPosition(0);

        CarUxRestrictionsConfiguration deserialized =
                CarUxRestrictionsConfiguration.CREATOR.createFromParcel(parcel);
        assertEquals(deserialized, config);
        assertTrue(deserialized.getPhysicalPort() == null);
    }

    /**
     * Writes input config as json, then reads a config out of json.
     * Asserts the deserialized config is the same as input.
     */
    private void verifyConfigThroughJsonSerialization(CarUxRestrictionsConfiguration config) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(out))) {
            config.writeJson(writer);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        try (JsonReader reader = new JsonReader(new InputStreamReader(in))) {
            CarUxRestrictionsConfiguration deserialized = CarUxRestrictionsConfiguration.readJson(
                    reader);
            assertTrue(config.equals(deserialized));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }
}
