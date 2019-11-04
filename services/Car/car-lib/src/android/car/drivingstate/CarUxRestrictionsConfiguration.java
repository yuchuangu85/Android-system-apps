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
package android.car.drivingstate;

import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_IDLING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_MOVING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_PARKED;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_UNKNOWN;
import static android.car.drivingstate.CarUxRestrictionsManager.UX_RESTRICTION_MODE_BASELINE;
import static android.car.drivingstate.CarUxRestrictionsManager.UX_RESTRICTION_MODE_PASSENGER;

import android.annotation.FloatRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.drivingstate.CarDrivingStateEvent.CarDrivingState;
import android.car.drivingstate.CarUxRestrictionsManager.UxRestrictionMode;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;
import android.util.Log;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for Car UX Restrictions service.
 *
 * @hide
 */
public final class CarUxRestrictionsConfiguration implements Parcelable {
    private static final String TAG = "CarUxRConfig";

    // Constants used by json de/serialization.
    private static final String JSON_NAME_PHYSICAL_PORT = "physical_port";
    private static final String JSON_NAME_MAX_CONTENT_DEPTH = "max_content_depth";
    private static final String JSON_NAME_MAX_CUMULATIVE_CONTENT_ITEMS =
            "max_cumulative_content_items";
    private static final String JSON_NAME_MAX_STRING_LENGTH = "max_string_length";
    private static final String JSON_NAME_MOVING_RESTRICTIONS = "moving_restrictions";
    private static final String JSON_NAME_IDLING_RESTRICTIONS = "idling_restrictions";
    private static final String JSON_NAME_PARKED_RESTRICTIONS = "parked_restrictions";
    private static final String JSON_NAME_UNKNOWN_RESTRICTIONS = "unknown_restrictions";
    private static final String JSON_NAME_PASSENGER_MOVING_RESTRICTIONS =
            "passenger_moving_restrictions";
    private static final String JSON_NAME_PASSENGER_IDLING_RESTRICTIONS =
            "passenger_idling_restrictions";
    private static final String JSON_NAME_PASSENGER_PARKED_RESTRICTIONS =
            "passenger_parked_restrictions";
    private static final String JSON_NAME_PASSENGER_UNKNOWN_RESTRICTIONS =
            "passenger_unknown_restrictions";
    private static final String JSON_NAME_REQ_OPT = "req_opt";
    private static final String JSON_NAME_RESTRICTIONS = "restrictions";
    private static final String JSON_NAME_SPEED_RANGE = "speed_range";
    private static final String JSON_NAME_MIN_SPEED = "min_speed";
    private static final String JSON_NAME_MAX_SPEED = "max_speed";

    private final int mMaxContentDepth;
    private final int mMaxCumulativeContentItems;
    private final int mMaxStringLength;
    private final Map<Integer, List<RestrictionsPerSpeedRange>> mPassengerUxRestrictions =
            new ArrayMap<>(DRIVING_STATES.length);
    private final Map<Integer, List<RestrictionsPerSpeedRange>> mBaselineUxRestrictions =
            new ArrayMap<>(DRIVING_STATES.length);

    // null means the port is not configured. It should apply to default display.
    @Nullable
    private final Byte mPhysicalPort;

    private CarUxRestrictionsConfiguration(CarUxRestrictionsConfiguration.Builder builder) {
        mPhysicalPort = builder.mPhysicalPort;

        mMaxContentDepth = builder.mMaxContentDepth;
        mMaxCumulativeContentItems = builder.mMaxCumulativeContentItems;
        mMaxStringLength = builder.mMaxStringLength;

        for (int drivingState : DRIVING_STATES) {
            List<RestrictionsPerSpeedRange> baseline = new ArrayList<>();
            for (RestrictionsPerSpeedRange r : builder.mBaselineUxRestrictions.get(drivingState)) {
                baseline.add(r);
            }
            mBaselineUxRestrictions.put(drivingState, baseline);

            List<RestrictionsPerSpeedRange> passenger = new ArrayList<>();
            for (RestrictionsPerSpeedRange r : builder.mPassengerUxRestrictions.get(drivingState)) {
                passenger.add(r);
            }
            mPassengerUxRestrictions.put(drivingState, passenger);
        }
    }

    /**
     * Returns the restrictions for
     * {@link UxRestrictionMode#UX_RESTRICTION_MODE_BASELINE}
     * based on current driving state.
     *
     * @param drivingState Driving state.
     *                     See values in {@link CarDrivingStateEvent.CarDrivingState}.
     * @param currentSpeed Current speed in meter per second.
     */
    public CarUxRestrictions getUxRestrictions(
            @CarDrivingState int drivingState, float currentSpeed) {
        return getUxRestrictions(drivingState, currentSpeed, UX_RESTRICTION_MODE_BASELINE);
    }

    /**
     * Returns the restrictions based on current driving state and restriction mode.
     *
     * <p>Restriction mode allows a different set of restrictions to be applied in the same driving
     * state. See values in {@link UxRestrictionMode}.
     *
     * @param drivingState Driving state.
     *                     See values in {@link CarDrivingStateEvent.CarDrivingState}.
     * @param currentSpeed Current speed in meter per second.
     * @param mode Current UX Restriction mode.
     */
    public CarUxRestrictions getUxRestrictions(@CarDrivingState int drivingState,
            float currentSpeed, @UxRestrictionMode int mode) {
        RestrictionsPerSpeedRange restriction = null;
        if (mode == UX_RESTRICTION_MODE_PASSENGER) {
            restriction = findUxRestrictionsInList(
                    currentSpeed, mPassengerUxRestrictions.get(drivingState));
        }
        if (restriction == null) {
            // Mode is baseline, or passenger mode does not specify restrictions for current driving
            // state.
            restriction = findUxRestrictionsInList(
                    currentSpeed, mBaselineUxRestrictions.get(drivingState));
        }

        if (restriction == null) {
            if (Build.IS_ENG || Build.IS_USERDEBUG) {
                throw new IllegalStateException("No restrictions for driving state "
                        + getDrivingStateName(drivingState));
            }
            return createDefaultUxRestrictionsEvent();
        }
        return createUxRestrictionsEvent(restriction.mReqOpt, restriction.mRestrictions);
    }

    /**
     * Returns the port this configuration applies to.
     *
     * <p>Returns {@code null} if port is not set, meaning this configuration will apply
     * to default display {@link android.view.Display#DEFAULT_DISPLAY}.
     */
    @Nullable
    public Byte getPhysicalPort() {
        return mPhysicalPort;
    }

    /**
     * Returns the restrictions based on current driving state and speed.
     */
    @Nullable
    private static RestrictionsPerSpeedRange findUxRestrictionsInList(float currentSpeed,
            List<RestrictionsPerSpeedRange> restrictions) {
        if (restrictions.isEmpty()) {
            return null;
        }

        if (restrictions.size() == 1 && restrictions.get(0).mSpeedRange == null) {
            // Single restriction with no speed range implies it covers all.
            return restrictions.get(0);
        }

        for (RestrictionsPerSpeedRange r : restrictions) {
            if (r.mSpeedRange != null && r.mSpeedRange.includes(currentSpeed)) {
                return r;
            }
        }
        return null;
    }

    private CarUxRestrictions createDefaultUxRestrictionsEvent() {
        return createUxRestrictionsEvent(true,
                CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED);
    }

    /**
     * Creates CarUxRestrictions with restrictions parameters from current configuration.
     */
    private CarUxRestrictions createUxRestrictionsEvent(boolean requiresOpt,
            @CarUxRestrictions.CarUxRestrictionsInfo int uxr) {
        // In case the UXR is not baseline, set requiresDistractionOptimization to true since it
        // doesn't make sense to have an active non baseline restrictions without
        // requiresDistractionOptimization set to true.
        if (uxr != CarUxRestrictions.UX_RESTRICTIONS_BASELINE) {
            requiresOpt = true;
        }
        CarUxRestrictions.Builder builder = new CarUxRestrictions.Builder(requiresOpt, uxr,
                SystemClock.elapsedRealtimeNanos());
        if (mMaxStringLength != Builder.UX_RESTRICTIONS_UNKNOWN) {
            builder.setMaxStringLength(mMaxStringLength);
        }
        if (mMaxCumulativeContentItems != Builder.UX_RESTRICTIONS_UNKNOWN) {
            builder.setMaxCumulativeContentItems(mMaxCumulativeContentItems);
        }
        if (mMaxContentDepth != Builder.UX_RESTRICTIONS_UNKNOWN) {
            builder.setMaxContentDepth(mMaxContentDepth);
        }
        return builder.build();
    }

    // Json de/serialization methods.

    /**
     * Writes current configuration as Json.
     */
    public void writeJson(JsonWriter writer) throws IOException {
        // We need to be lenient to accept infinity number (as max speed).
        writer.setLenient(true);

        writer.beginObject();
        if (mPhysicalPort == null) {
            writer.name(JSON_NAME_PHYSICAL_PORT).nullValue();
        } else {
            writer.name(JSON_NAME_PHYSICAL_PORT).value((int) mPhysicalPort.byteValue());
        }
        writer.name(JSON_NAME_MAX_CONTENT_DEPTH).value(mMaxContentDepth);
        writer.name(JSON_NAME_MAX_CUMULATIVE_CONTENT_ITEMS).value(
                mMaxCumulativeContentItems);
        writer.name(JSON_NAME_MAX_STRING_LENGTH).value(mMaxStringLength);

        writer.name(JSON_NAME_PARKED_RESTRICTIONS);
        writeRestrictionsList(writer,
                mBaselineUxRestrictions.get(DRIVING_STATE_PARKED));

        writer.name(JSON_NAME_IDLING_RESTRICTIONS);
        writeRestrictionsList(writer,
                mBaselineUxRestrictions.get(DRIVING_STATE_IDLING));

        writer.name(JSON_NAME_MOVING_RESTRICTIONS);
        writeRestrictionsList(writer,
                mBaselineUxRestrictions.get(DRIVING_STATE_MOVING));

        writer.name(JSON_NAME_UNKNOWN_RESTRICTIONS);
        writeRestrictionsList(writer,
                mBaselineUxRestrictions.get(DRIVING_STATE_UNKNOWN));

        writer.name(JSON_NAME_PASSENGER_PARKED_RESTRICTIONS);
        writeRestrictionsList(writer,
                mPassengerUxRestrictions.get(DRIVING_STATE_PARKED));

        writer.name(JSON_NAME_PASSENGER_IDLING_RESTRICTIONS);
        writeRestrictionsList(writer,
                mPassengerUxRestrictions.get(DRIVING_STATE_IDLING));

        writer.name(JSON_NAME_PASSENGER_MOVING_RESTRICTIONS);
        writeRestrictionsList(writer,
                mPassengerUxRestrictions.get(DRIVING_STATE_MOVING));

        writer.name(JSON_NAME_PASSENGER_UNKNOWN_RESTRICTIONS);
        writeRestrictionsList(writer,
                mPassengerUxRestrictions.get(DRIVING_STATE_UNKNOWN));

        writer.endObject();
    }

    private void writeRestrictionsList(JsonWriter writer, List<RestrictionsPerSpeedRange> messages)
            throws IOException {
        writer.beginArray();
        for (RestrictionsPerSpeedRange restrictions : messages) {
            writeRestrictions(writer, restrictions);
        }
        writer.endArray();
    }

    private void writeRestrictions(JsonWriter writer, RestrictionsPerSpeedRange restrictions)
            throws IOException {
        writer.beginObject();
        writer.name(JSON_NAME_REQ_OPT).value(restrictions.mReqOpt);
        writer.name(JSON_NAME_RESTRICTIONS).value(restrictions.mRestrictions);
        if (restrictions.mSpeedRange != null) {
            writer.name(JSON_NAME_SPEED_RANGE);
            writer.beginObject();
            writer.name(JSON_NAME_MIN_SPEED).value(restrictions.mSpeedRange.mMinSpeed);
            writer.name(JSON_NAME_MAX_SPEED).value(restrictions.mSpeedRange.mMaxSpeed);
            writer.endObject();
        }
        writer.endObject();
    }

    @Override
    public String toString() {
        CharArrayWriter charWriter = new CharArrayWriter();
        JsonWriter writer = new JsonWriter(charWriter);
        writer.setIndent("\t");
        try {
            writeJson(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return charWriter.toString();
    }

    /**
     * Reads Json as UX restriction configuration.
     */
    public static CarUxRestrictionsConfiguration readJson(JsonReader reader) throws IOException {
        // We need to be lenient to accept infinity number (as max speed).
        reader.setLenient(true);

        Builder builder = new Builder();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case JSON_NAME_PHYSICAL_PORT:
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull();
                    } else {
                        builder.setPhysicalPort(Builder.validatePort(reader.nextInt()));
                    }
                    break;
                case JSON_NAME_MAX_CONTENT_DEPTH:
                    builder.setMaxContentDepth(reader.nextInt());
                    break;
                case JSON_NAME_MAX_CUMULATIVE_CONTENT_ITEMS:
                    builder.setMaxCumulativeContentItems(reader.nextInt());
                    break;
                case JSON_NAME_MAX_STRING_LENGTH:
                    builder.setMaxStringLength(reader.nextInt());
                    break;
                case JSON_NAME_PARKED_RESTRICTIONS:
                    readRestrictionsList(reader, DRIVING_STATE_PARKED,
                            UX_RESTRICTION_MODE_BASELINE, builder);
                    break;
                case JSON_NAME_IDLING_RESTRICTIONS:
                    readRestrictionsList(reader, DRIVING_STATE_IDLING,
                            UX_RESTRICTION_MODE_BASELINE, builder);
                    break;
                case JSON_NAME_MOVING_RESTRICTIONS:
                    readRestrictionsList(reader, DRIVING_STATE_MOVING,
                            UX_RESTRICTION_MODE_BASELINE, builder);
                    break;
                case JSON_NAME_UNKNOWN_RESTRICTIONS:
                    readRestrictionsList(reader, DRIVING_STATE_UNKNOWN,
                            UX_RESTRICTION_MODE_BASELINE, builder);
                    break;
                case JSON_NAME_PASSENGER_PARKED_RESTRICTIONS:
                    readRestrictionsList(reader, DRIVING_STATE_PARKED,
                            UX_RESTRICTION_MODE_PASSENGER, builder);
                    break;
                case JSON_NAME_PASSENGER_IDLING_RESTRICTIONS:
                    readRestrictionsList(reader, DRIVING_STATE_IDLING,
                            UX_RESTRICTION_MODE_PASSENGER, builder);
                    break;
                case JSON_NAME_PASSENGER_MOVING_RESTRICTIONS:
                    readRestrictionsList(reader, DRIVING_STATE_MOVING,
                            UX_RESTRICTION_MODE_PASSENGER, builder);
                    break;
                case JSON_NAME_PASSENGER_UNKNOWN_RESTRICTIONS:
                    readRestrictionsList(reader, DRIVING_STATE_UNKNOWN,
                            UX_RESTRICTION_MODE_PASSENGER, builder);
                    break;
                default:
                    Log.e(TAG, "Unknown name parsing json config: " + name);
                    reader.skipValue();
            }
        }
        reader.endObject();
        return builder.build();
    }

    private static void readRestrictionsList(JsonReader reader, @CarDrivingState int drivingState,
            @UxRestrictionMode int mode, Builder builder) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            DrivingStateRestrictions drivingStateRestrictions = readRestrictions(reader);
            drivingStateRestrictions.setMode(mode);

            builder.setUxRestrictions(drivingState, drivingStateRestrictions);
        }
        reader.endArray();
    }

    private static DrivingStateRestrictions readRestrictions(JsonReader reader) throws IOException {
        reader.beginObject();
        boolean reqOpt = false;
        int restrictions = CarUxRestrictions.UX_RESTRICTIONS_BASELINE;
        Builder.SpeedRange speedRange = null;
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals(JSON_NAME_REQ_OPT)) {
                reqOpt = reader.nextBoolean();
            } else if (name.equals(JSON_NAME_RESTRICTIONS)) {
                restrictions = reader.nextInt();
            } else if (name.equals(JSON_NAME_SPEED_RANGE)) {
                reader.beginObject();
                // Okay to set min initial value as MAX_SPEED because SpeedRange() won't allow it.
                float minSpeed = Builder.SpeedRange.MAX_SPEED;
                float maxSpeed = Builder.SpeedRange.MAX_SPEED;

                while (reader.hasNext()) {
                    String n = reader.nextName();
                    if (n.equals(JSON_NAME_MIN_SPEED)) {
                        minSpeed = Double.valueOf(reader.nextDouble()).floatValue();
                    } else if (n.equals(JSON_NAME_MAX_SPEED)) {
                        maxSpeed = Double.valueOf(reader.nextDouble()).floatValue();
                    } else {
                        Log.e(TAG, "Unknown name parsing json config: " + n);
                        reader.skipValue();
                    }
                }
                speedRange = new Builder.SpeedRange(minSpeed, maxSpeed);
                reader.endObject();
            }
        }
        reader.endObject();
        DrivingStateRestrictions drivingStateRestrictions = new DrivingStateRestrictions()
                .setDistractionOptimizationRequired(reqOpt)
                .setRestrictions(restrictions);
        if (speedRange != null) {
            drivingStateRestrictions.setSpeedRange(speedRange);
        }
        return drivingStateRestrictions;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPhysicalPort,
                mMaxStringLength, mMaxCumulativeContentItems, mMaxContentDepth,
                mBaselineUxRestrictions, mPassengerUxRestrictions);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CarUxRestrictionsConfiguration)) {
            return false;
        }

        CarUxRestrictionsConfiguration other = (CarUxRestrictionsConfiguration) obj;

        return mPhysicalPort == other.mPhysicalPort
                && hasSameParameters(other)
                && mBaselineUxRestrictions.equals(other.mBaselineUxRestrictions)
                && mPassengerUxRestrictions.equals(other.mPassengerUxRestrictions);
    }

    /**
     * Compares {@code this} configuration object with {@code other} on restriction parameters.
     */
    public boolean hasSameParameters(CarUxRestrictionsConfiguration other) {
        return mMaxContentDepth == other.mMaxContentDepth
                && mMaxCumulativeContentItems == other.mMaxCumulativeContentItems
                && mMaxStringLength == other.mMaxStringLength;
    }

    /**
     * Dump the driving state to UX restrictions mapping.
     */
    public void dump(PrintWriter writer) {
        writer.println("Physical display port: " + mPhysicalPort);

        writer.println("===========================================");
        writer.println("Baseline mode UXR:");
        writer.println("-------------------------------------------");
        dumpRestrictions(writer, mBaselineUxRestrictions);

        writer.println("Passenger mode UXR:");
        writer.println("-------------------------------------------");
        dumpRestrictions(writer, mPassengerUxRestrictions);

        writer.println("Max String length: " + mMaxStringLength);
        writer.println("Max Cumulative Content Items: " + mMaxCumulativeContentItems);
        writer.println("Max Content depth: " + mMaxContentDepth);
        writer.println("===========================================");
    }

    private void dumpRestrictions(
            PrintWriter writer, Map<Integer, List<RestrictionsPerSpeedRange>> restrictions) {
        for (Integer state : restrictions.keySet()) {
            List<RestrictionsPerSpeedRange> list = restrictions.get(state);
            writer.println("State:" + getDrivingStateName(state)
                    + " num restrictions:" + list.size());
            for (RestrictionsPerSpeedRange r : list) {
                writer.println("Requires DO? " + r.mReqOpt
                        + "\nRestrictions: 0x" + Integer.toHexString(r.mRestrictions)
                        + "\nSpeed Range: "
                        + (r.mSpeedRange == null
                        ? "None"
                        : (r.mSpeedRange.mMinSpeed + " - " + r.mSpeedRange.mMaxSpeed)));
                writer.println("-------------------------------------------");
            }
        }
    }

    private static String getDrivingStateName(@CarDrivingState int state) {
        switch (state) {
            case DRIVING_STATE_PARKED:
                return "parked";
            case DRIVING_STATE_IDLING:
                return "idling";
            case DRIVING_STATE_MOVING:
                return "moving";
            case DRIVING_STATE_UNKNOWN:
                return "unknown";
            default:
                throw new IllegalArgumentException("Unrecognized state value: " + state);
        }
    }

    // Parcelable methods/fields.

    // Used by Parcel methods to ensure de/serialization order.
    private static final int[] DRIVING_STATES = new int[] {
            DRIVING_STATE_UNKNOWN,
            DRIVING_STATE_PARKED,
            DRIVING_STATE_IDLING,
            DRIVING_STATE_MOVING,
    };

    public static final Parcelable.Creator<CarUxRestrictionsConfiguration> CREATOR =
            new Parcelable.Creator<CarUxRestrictionsConfiguration>() {

        @Override
        public CarUxRestrictionsConfiguration createFromParcel(Parcel source) {
            return new CarUxRestrictionsConfiguration(source);
        }

        @Override
        public CarUxRestrictionsConfiguration[] newArray(int size) {
            return new CarUxRestrictionsConfiguration[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    private CarUxRestrictionsConfiguration(Parcel in) {
        for (int drivingState : DRIVING_STATES) {
            List<RestrictionsPerSpeedRange> restrictions = new ArrayList<>();
            in.readTypedList(restrictions, RestrictionsPerSpeedRange.CREATOR);
            mBaselineUxRestrictions.put(drivingState, restrictions);
        }
        for (int drivingState : DRIVING_STATES) {
            List<RestrictionsPerSpeedRange> restrictions = new ArrayList<>();
            in.readTypedList(restrictions, RestrictionsPerSpeedRange.CREATOR);
            mPassengerUxRestrictions.put(drivingState, restrictions);
        }
        boolean nullPhysicalPort = in.readBoolean();
        byte physicalPort = in.readByte();
        mPhysicalPort = nullPhysicalPort ? null : physicalPort;

        mMaxContentDepth = in.readInt();
        mMaxCumulativeContentItems = in.readInt();
        mMaxStringLength = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        for (int drivingState : DRIVING_STATES) {
            dest.writeTypedList(mBaselineUxRestrictions.get(drivingState));
        }
        for (int drivingState : DRIVING_STATES) {
            dest.writeTypedList(mPassengerUxRestrictions.get(drivingState));
        }
        boolean nullPhysicalPort = mPhysicalPort == null;
        dest.writeBoolean(nullPhysicalPort);
        // When physical port is null, 0 should be skipped.
        dest.writeByte(nullPhysicalPort ? ((byte) 0) : mPhysicalPort.byteValue());

        dest.writeInt(mMaxContentDepth);
        dest.writeInt(mMaxCumulativeContentItems);
        dest.writeInt(mMaxStringLength);
    }

    /**
     * @hide
     */
    public static final class Builder {

        /**
         * Validates integer value for port is within the value range of a byte.
         *
         * Throws exception if input value is outside the range.
         *
         * @return {@code port} as a byte.
         */
        public static byte validatePort(int port) {
            if (Byte.MIN_VALUE <= port && port <= Byte.MAX_VALUE) {
                return (byte) port;
            }
            throw new IllegalArgumentException(
                    "Port value should be within the range of a byte. Input is " + port);
        }

        private static final int UX_RESTRICTIONS_UNKNOWN = -1;

        /**
         * {@code null} means port is not set.
         */
        private Byte mPhysicalPort;

        private int mMaxContentDepth = UX_RESTRICTIONS_UNKNOWN;
        private int mMaxCumulativeContentItems = UX_RESTRICTIONS_UNKNOWN;
        private int mMaxStringLength = UX_RESTRICTIONS_UNKNOWN;

        public Map<Integer, List<RestrictionsPerSpeedRange>> mPassengerUxRestrictions =
                new ArrayMap<>(DRIVING_STATES.length);
        public Map<Integer, List<RestrictionsPerSpeedRange>> mBaselineUxRestrictions =
                new ArrayMap<>(DRIVING_STATES.length);

        public Builder() {
            for (int drivingState : DRIVING_STATES) {
                mBaselineUxRestrictions.put(drivingState, new ArrayList<>());
                mPassengerUxRestrictions.put(drivingState, new ArrayList<>());
            }
        }

        /**
         * Sets the display this configuration will apply to.
         *
         * <p>The display is identified by the physical {@code port}.
         *
         * @param port Port that is connected to a display.
         *             See {@link android.view.DisplayAddress.Physical#getPort()}.
         */
        public Builder setPhysicalPort(byte port) {
            mPhysicalPort = port;
            return this;
        }

        /**
         * Sets ux restrictions for driving state.
         */
        public Builder setUxRestrictions(@CarDrivingState int drivingState,
                boolean requiresOptimization,
                @CarUxRestrictions.CarUxRestrictionsInfo int restrictions) {
            return this.setUxRestrictions(drivingState, new DrivingStateRestrictions()
                    .setDistractionOptimizationRequired(requiresOptimization)
                    .setRestrictions(restrictions));
        }

        /**
         * Sets UX restrictions with speed range.
         *
         * @param drivingState Restrictions will be set for this Driving state.
         *                     See constants in {@link CarDrivingStateEvent}.
         * @param speedRange If set, restrictions will only apply when current speed is within
         *                   the range. Only {@link CarDrivingStateEvent#DRIVING_STATE_MOVING}
         *                   supports speed range. {@code null} implies the full speed range,
         *                   i.e. zero to {@link SpeedRange#MAX_SPEED}.
         * @param requiresOptimization Whether distraction optimization (DO) is required for this
         *                             driving state.
         * @param restrictions See constants in {@link CarUxRestrictions}.
         *
         * @deprecated Use {@link #setUxRestrictions(int, DrivingStateRestrictions)} instead.
         */
        @Deprecated
        public Builder setUxRestrictions(@CarDrivingState int drivingState,
                @NonNull SpeedRange speedRange, boolean requiresOptimization,
                @CarUxRestrictions.CarUxRestrictionsInfo int restrictions) {
            return setUxRestrictions(drivingState, new DrivingStateRestrictions()
                    .setDistractionOptimizationRequired(requiresOptimization)
                    .setRestrictions(restrictions)
                    .setSpeedRange(speedRange));
        }

        /**
         * Sets UX restriction.
         *
         * @param drivingState Restrictions will be set for this Driving state.
         *                     See constants in {@link CarDrivingStateEvent}.
         * @param drivingStateRestrictions Restrictions to set.
         *
         * @return This builder object for method chaining.
         */
        public Builder setUxRestrictions(
                int drivingState, DrivingStateRestrictions drivingStateRestrictions) {
            SpeedRange speedRange = drivingStateRestrictions.mSpeedRange;

            if (drivingState != DRIVING_STATE_MOVING && speedRange != null) {
                throw new IllegalArgumentException(
                        "Non-moving driving state should not specify speed range.");
            }

            List<RestrictionsPerSpeedRange> restrictions;
            switch (drivingStateRestrictions.mMode) {
                case UX_RESTRICTION_MODE_BASELINE:
                    restrictions = mBaselineUxRestrictions.get(drivingState);
                    break;
                case UX_RESTRICTION_MODE_PASSENGER:
                    restrictions = mPassengerUxRestrictions.get(drivingState);
                    break;
                default:
                    String mode = CarUxRestrictionsManager.modeToString(
                            drivingStateRestrictions.mMode);
                    throw new IllegalArgumentException("Unrecognized restriction mode " + mode);
            }
            restrictions.add(new RestrictionsPerSpeedRange(
                    drivingStateRestrictions.mMode, drivingStateRestrictions.mReqOpt,
                    drivingStateRestrictions.mRestrictions, speedRange));
            return this;
        }


        /**
         * Sets max string length.
         */
        public Builder setMaxStringLength(int maxStringLength) {
            mMaxStringLength = maxStringLength;
            return this;
        }

        /**
         * Sets max cumulative content items.
         */
        public Builder setMaxCumulativeContentItems(int maxCumulativeContentItems) {
            mMaxCumulativeContentItems = maxCumulativeContentItems;
            return this;
        }

        /**
         * Sets max content depth.
         */
        public Builder setMaxContentDepth(int maxContentDepth) {
            mMaxContentDepth = maxContentDepth;
            return this;
        }

        /**
         * @return CarUxRestrictionsConfiguration based on builder configuration.
         */
        public CarUxRestrictionsConfiguration build() {
            // Unspecified driving state should be fully restricted to be safe.
            addDefaultRestrictionsToBaseline();

            validateBaselineModeRestrictions();
            validatePassengerModeRestrictions();

            return new CarUxRestrictionsConfiguration(this);
        }

        private void addDefaultRestrictionsToBaseline() {
            for (int drivingState : DRIVING_STATES) {
                List<RestrictionsPerSpeedRange> restrictions =
                        mBaselineUxRestrictions.get(drivingState);
                if (restrictions.size() == 0) {
                    Log.i(TAG, "Using default restrictions for driving state: "
                            + getDrivingStateName(drivingState));
                    restrictions.add(new RestrictionsPerSpeedRange(
                            true, CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED));
                }
            }
        }

        private void validateBaselineModeRestrictions() {
            for (int drivingState : DRIVING_STATES) {
                List<RestrictionsPerSpeedRange> restrictions =
                        mBaselineUxRestrictions.get(drivingState);
                if (drivingState != DRIVING_STATE_MOVING) {
                    // Note: For non-moving state, setUxRestrictions() rejects UxRestriction with
                    // speed range, so we don't check here.
                    if (restrictions.size() != 1) {
                        throw new IllegalStateException("Non-moving driving state should "
                                + "contain one set of restriction rules.");
                    }
                }

                // If there are multiple restrictions, each one should specify speed range.
                if (restrictions.size() > 1 && restrictions.stream().anyMatch(
                        restriction -> restriction.mSpeedRange == null)) {
                    StringBuilder error = new StringBuilder();
                    for (RestrictionsPerSpeedRange restriction : restrictions) {
                        error.append(restriction.toString()).append('\n');
                    }
                    throw new IllegalStateException(
                            "Every restriction in MOVING state should contain driving state.\n"
                                    + error.toString());
                }

                // Sort restrictions based on speed range.
                Collections.sort(restrictions,
                        Comparator.comparing(RestrictionsPerSpeedRange::getSpeedRange));

                validateRangeOfSpeed(restrictions);
                validateContinuousSpeedRange(restrictions);
            }
        }

        private void validatePassengerModeRestrictions() {
            List<RestrictionsPerSpeedRange> passengerMovingRestrictions =
                    mPassengerUxRestrictions.get(DRIVING_STATE_MOVING);
            Collections.sort(passengerMovingRestrictions,
                    Comparator.comparing(RestrictionsPerSpeedRange::getSpeedRange));

            validateContinuousSpeedRange(passengerMovingRestrictions);
        }

        /**
         * Validates if combined speed ranges of given restrictions.
         *
         * <p>Restrictions are considered to contain valid speed ranges if:
         * <ul>
         * <li>None contains a speed range - implies full range; or
         * <li>Combination covers range [0 - MAX_SPEED]
         * </ul>
         *
         * Throws exception on invalidate input.
         *
         * @param restrictions Restrictions to be checked. Must be sorted.
         */
        private void validateRangeOfSpeed(List<RestrictionsPerSpeedRange> restrictions) {
            if (restrictions.size() == 1) {
                SpeedRange speedRange = restrictions.get(0).mSpeedRange;
                if (speedRange == null) {
                    // Single restriction with null speed range implies that
                    // it applies to the entire driving state.
                    return;
                }
            }
            if (Float.compare(restrictions.get(0).mSpeedRange.mMinSpeed, 0) != 0) {
                throw new IllegalStateException(
                        "Speed range min speed should start at 0.");
            }
            float lastMaxSpeed = restrictions.get(restrictions.size() - 1).mSpeedRange.mMaxSpeed;
            if (Float.compare(lastMaxSpeed, SpeedRange.MAX_SPEED) != 0) {
                throw new IllegalStateException(
                        "Max speed of last restriction should be MAX_SPEED.");
            }
        }

        /**
         * Validates if combined speed ranges of given restrictions are continuous, meaning they:
         * <ul>
         * <li>Do not overlap; and
         * <li>Do not contain gap
         * </ul>
         *
         * <p>Namely the max speed of current range equals the min speed of next range.
         *
         * Throws exception on invalidate input.
         *
         * @param restrictions Restrictions to be checked. Must be sorted.
         */
        private void validateContinuousSpeedRange(List<RestrictionsPerSpeedRange> restrictions) {
            for (int i = 1; i < restrictions.size(); i++) {
                RestrictionsPerSpeedRange prev = restrictions.get(i - 1);
                RestrictionsPerSpeedRange curr = restrictions.get(i);
                // If current min != prev.max, there's either an overlap or a gap in speed range.
                if (Float.compare(curr.mSpeedRange.mMinSpeed, prev.mSpeedRange.mMaxSpeed) != 0) {
                    throw new IllegalArgumentException(
                            "Mis-configured speed range. Possibly speed range overlap or gap.");
                }
            }
        }

        /**
         * Speed range is defined by min and max speed. When there is no upper bound for max speed,
         * set it to {@link SpeedRange#MAX_SPEED}.
         */
        public static final class SpeedRange implements Comparable<SpeedRange> {
            public static final float MAX_SPEED = Float.POSITIVE_INFINITY;

            private float mMinSpeed;
            private float mMaxSpeed;

            /**
             * Defaults max speed to {@link SpeedRange#MAX_SPEED}.
             */
            public SpeedRange(@FloatRange(from = 0.0) float minSpeed) {
                this(minSpeed, MAX_SPEED);
            }

            public SpeedRange(@FloatRange(from = 0.0) float minSpeed,
                    @FloatRange(from = 0.0) float maxSpeed) {
                if (Float.compare(minSpeed, 0) < 0 || Float.compare(maxSpeed, 0) < 0) {
                    throw new IllegalArgumentException("Speed cannot be negative.");
                }
                if (minSpeed == MAX_SPEED) {
                    throw new IllegalArgumentException("Min speed cannot be MAX_SPEED.");
                }
                if (minSpeed > maxSpeed) {
                    throw new IllegalArgumentException("Min speed " + minSpeed
                            + " should not be greater than max speed " + maxSpeed);
                }
                mMinSpeed = minSpeed;
                mMaxSpeed = maxSpeed;
            }

             /**
             * Return if the given speed is in the range of [minSpeed, maxSpeed).
             *
             * @param speed Speed to check
             * @return {@code true} if in range; {@code false} otherwise.
             */
            public boolean includes(float speed) {
                return mMinSpeed <= speed && speed < mMaxSpeed;
            }

            @Override
            public int compareTo(SpeedRange other) {
                // First compare min speed; then max speed.
                int minSpeedComparison = Float.compare(mMinSpeed, other.mMinSpeed);
                if (minSpeedComparison != 0) {
                    return minSpeedComparison;
                }

                return Float.compare(mMaxSpeed, other.mMaxSpeed);
            }

            @Override
            public int hashCode() {
                return Objects.hash(mMinSpeed, mMaxSpeed);
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (!(obj instanceof SpeedRange)) {
                    return false;
                }
                SpeedRange other = (SpeedRange) obj;

                return compareTo(other) == 0;
            }

            @Override
            public String toString() {
                return new StringBuilder()
                        .append("[min: ").append(mMinSpeed)
                        .append("; max: ").append(mMaxSpeed == MAX_SPEED ? "max_speed" : mMaxSpeed)
                        .append("]")
                        .toString();
            }
        }
    }

    /**
     * UX restrictions to be applied to a driving state through {@link
     * Builder#setUxRestrictions(int, CarUxRestrictionsConfiguration.DrivingStateRestrictions)}.
     * These UX restrictions can also specified to be only applicable to certain speed range and
     * restriction mode.
     *
     * @see UxRestrictionMode
     * @see Builder.SpeedRange
     *
     * @hide
     */
    public static final class DrivingStateRestrictions {
        private int mMode = UX_RESTRICTION_MODE_BASELINE;
        private boolean mReqOpt = true;
        private int mRestrictions = CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED;
        @Nullable private Builder.SpeedRange mSpeedRange;

        /**
         * Sets whether Distraction Optimization (DO) is required. Defaults to {@code true}.
         */
        public DrivingStateRestrictions setDistractionOptimizationRequired(
                boolean distractionOptimizationRequired) {
            mReqOpt = distractionOptimizationRequired;
            return this;
        }

        /**
         * Sets active restrictions.
         * Defaults to {@link CarUxRestrictions#UX_RESTRICTIONS_FULLY_RESTRICTED}.
         */
        public DrivingStateRestrictions setRestrictions(
                @CarUxRestrictions.CarUxRestrictionsInfo int restrictions) {
            mRestrictions = restrictions;
            return this;
        }

        /**
         * Sets restriction mode to apply to.
         * Defaults to {@link CarUxRestrictionsManager#UX_RESTRICTION_MODE_BASELINE}.
         */
        public DrivingStateRestrictions setMode(@UxRestrictionMode int mode) {
            mMode = mode;
            return this;
        }

        /**
         * Sets speed range to apply to. Optional value. Not setting one means the restrictions
         * apply to full speed range, namely {@code 0} to {@link Builder.SpeedRange#MAX_SPEED}.
         */
        public DrivingStateRestrictions setSpeedRange(@NonNull Builder.SpeedRange speedRange) {
            mSpeedRange = speedRange;
            return this;
        }

        @Override
        public String toString() {
            return new StringBuilder()
                    .append("Mode: ").append(CarUxRestrictionsManager.modeToString(mMode))
                    .append(". Requires DO? ").append(mReqOpt)
                    .append(". Restrictions: ").append(Integer.toBinaryString(mRestrictions))
                    .append(". SpeedRange: ")
                    .append(mSpeedRange == null ? "null" : mSpeedRange.toString())
                    .toString();
        }
    }

    /**
     * Container for UX restrictions for a speed range.
     * Speed range is valid only for the {@link CarDrivingStateEvent#DRIVING_STATE_MOVING}.
     */
    private static final class RestrictionsPerSpeedRange implements Parcelable {
        @UxRestrictionMode
        final int mMode;
        final boolean mReqOpt;
        final int mRestrictions;
        @Nullable
        final Builder.SpeedRange mSpeedRange;

        RestrictionsPerSpeedRange(boolean reqOpt, int restrictions) {
            this(UX_RESTRICTION_MODE_BASELINE, reqOpt, restrictions, null);
        }

        RestrictionsPerSpeedRange(@UxRestrictionMode int mode, boolean reqOpt, int restrictions,
                @Nullable Builder.SpeedRange speedRange) {
            if (!reqOpt && restrictions != CarUxRestrictions.UX_RESTRICTIONS_BASELINE) {
                throw new IllegalArgumentException(
                        "Driving optimization is not required but UX restrictions is required.");
            }
            mMode = mode;
            mReqOpt = reqOpt;
            mRestrictions = restrictions;
            mSpeedRange = speedRange;
        }

        public Builder.SpeedRange getSpeedRange() {
            return mSpeedRange;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mMode, mReqOpt, mRestrictions, mSpeedRange);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || !(obj instanceof RestrictionsPerSpeedRange)) {
                return false;
            }
            RestrictionsPerSpeedRange other = (RestrictionsPerSpeedRange) obj;
            return mMode == other.mMode
                    && mReqOpt == other.mReqOpt
                    && mRestrictions == other.mRestrictions
                    && Objects.equals(mSpeedRange, other.mSpeedRange);
        }

        @Override
        public String toString() {
            return new StringBuilder()
                    .append("[Mode is ").append(CarUxRestrictionsManager.modeToString(mMode))
                    .append("; Requires DO? ").append(mReqOpt)
                    .append("; Restrictions: ").append(Integer.toBinaryString(mRestrictions))
                    .append("; Speed range: ")
                    .append(mSpeedRange == null ? "null" : mSpeedRange.toString())
                    .append(']')
                    .toString();
        }

        // Parcelable methods/fields.

        public static final Creator<RestrictionsPerSpeedRange> CREATOR =
                new Creator<RestrictionsPerSpeedRange>() {
                    @Override
                    public RestrictionsPerSpeedRange createFromParcel(Parcel in) {
                        return new RestrictionsPerSpeedRange(in);
                    }

                    @Override
                    public RestrictionsPerSpeedRange[] newArray(int size) {
                        return new RestrictionsPerSpeedRange[size];
                    }
                };

        @Override
        public int describeContents() {
            return 0;
        }

        protected RestrictionsPerSpeedRange(Parcel in) {
            mMode = in.readInt();
            mReqOpt = in.readBoolean();
            mRestrictions = in.readInt();
            // Whether speed range is specified.
            Builder.SpeedRange speedRange = null;
            if (in.readBoolean()) {
                float minSpeed = in.readFloat();
                float maxSpeed = in.readFloat();
                speedRange = new Builder.SpeedRange(minSpeed, maxSpeed);
            }
            mSpeedRange = speedRange;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mMode);
            dest.writeBoolean(mReqOpt);
            dest.writeInt(mRestrictions);
            // Whether speed range is specified.
            dest.writeBoolean(mSpeedRange != null);
            if (mSpeedRange != null) {
                dest.writeFloat(mSpeedRange.mMinSpeed);
                dest.writeFloat(mSpeedRange.mMaxSpeed);
            }
        }
    }
}
