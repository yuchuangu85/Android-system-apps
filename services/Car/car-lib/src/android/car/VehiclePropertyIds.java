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

package android.car;

/**
 * Copy from android.hardware.automotive.vehicle-V2.0-java_gen_java/gen/android/hardware/automotive
 * /vehicle/V2_0. Need to update this file when vehicle propertyId is changed in VHAL.
 * Use it as PropertyId in getProperty() and setProperty() in
 * {@link android.car.hardware.property.CarPropertyManager}
 */
public final class VehiclePropertyIds {
    /**
     * Undefined property.  */
    public static final int INVALID = 0;
    /**
     * VIN of vehicle
     * Requires permission: {@link Car#PERMISSION_IDENTIFICATION}.
     */
    public static final int INFO_VIN = 286261504;
    /**
     * Manufacturer of vehicle
     * Requires permission: {@link Car#PERMISSION_CAR_INFO}.
     */
    public static final int INFO_MAKE = 286261505;
    /**
     * Model of vehicle
     * Requires permission: {@link Car#PERMISSION_CAR_INFO}.
     */
    public static final int INFO_MODEL = 286261506;
    /**
     * Model year of vehicle.
     * Requires permission: {@link Car#PERMISSION_CAR_INFO}.
     */
    public static final int INFO_MODEL_YEAR = 289407235;
    /**
     * Fuel capacity of the vehicle in milliliters
     * Requires permission: {@link Car#PERMISSION_CAR_INFO}.
     */
    public static final int INFO_FUEL_CAPACITY = 291504388;
    /**
     * List of fuels the vehicle may use
     * Requires permission: {@link Car#PERMISSION_CAR_INFO}.
     */
    public static final int INFO_FUEL_TYPE = 289472773;
    /**
     * Battery capacity of the vehicle, if EV or hybrid.  This is the nominal
     * battery capacity when the vehicle is new.
     * Requires permission: {@link Car#PERMISSION_CAR_INFO}.
     */
    public static final int INFO_EV_BATTERY_CAPACITY = 291504390;
    /**
     * List of connectors this EV may use
     * Requires permission: {@link Car#PERMISSION_CAR_INFO}.
     */
    public static final int INFO_EV_CONNECTOR_TYPE = 289472775;
    /**
     * Fuel door location
     * Requires permission: {@link Car#PERMISSION_CAR_INFO}.
     */
    public static final int INFO_FUEL_DOOR_LOCATION = 289407240;
    /**
     * EV port location
     * Requires permission: {@link Car#PERMISSION_CAR_INFO}.
     */
    public static final int INFO_EV_PORT_LOCATION = 289407241;
    /**
     * Driver's seat location
     * Requires permission: {@link Car#PERMISSION_CAR_INFO}.
     */
    public static final int INFO_DRIVER_SEAT = 356516106;
    /**
     * Current odometer value of the vehicle
     * Requires permission: {@link Car#PERMISSION_MILEAGE}.
     */
    public static final int PERF_ODOMETER = 291504644;
    /**
     * Speed of the vehicle
     * Requires permission: {@link Car#PERMISSION_SPEED}.
     */
    public static final int PERF_VEHICLE_SPEED = 291504647;
    /**
     * Speed of the vehicle for displays
     *
     * Some cars display a slightly slower speed than the actual speed. This is
     * usually displayed on the speedometer.
     * Requires permission: {@link Car#PERMISSION_SPEED}.
     */
    public static final int PERF_VEHICLE_SPEED_DISPLAY = 291504648;
    /**
     * Steering angle of the vehicle
     *
     * Angle is in degrees. Left is negative.
     * Requires permission: {@link Car#PERMISSION_READ_STEERING_STATE}.
     */
    public static final int PERF_STEERING_ANGLE = 291504649;
    /**
     * Temperature of engine coolant
     * Requires permission: {@link Car#PERMISSION_CAR_ENGINE_DETAILED}.
     */
    public static final int ENGINE_COOLANT_TEMP = 291504897;
    /**
     * Engine oil level
     * Requires permission: {@link Car#PERMISSION_CAR_ENGINE_DETAILED}.
     */
    public static final int ENGINE_OIL_LEVEL = 289407747;
    /**
     * Temperature of engine oil
     * Requires permission: {@link Car#PERMISSION_CAR_ENGINE_DETAILED}.
     */
    public static final int ENGINE_OIL_TEMP = 291504900;
    /**
     * Engine rpm
     * Requires permission: {@link Car#PERMISSION_CAR_ENGINE_DETAILED}.
     */
    public static final int ENGINE_RPM = 291504901;
    /**
     * Reports wheel ticks
     * Requires permission: {@link Car#PERMISSION_SPEED}.
     */
    public static final int WHEEL_TICK = 290521862;
    /**
     * Fuel remaining in the the vehicle, in milliliters
     * Requires permission: {@link Car#PERMISSION_ENERGY}.
     */
    public static final int FUEL_LEVEL = 291504903;
    /**
     * Fuel door open
     * Requires permission: {@link Car#PERMISSION_ENERGY_PORTS}.
     */
    public static final int FUEL_DOOR_OPEN = 287310600;
    /**
     * EV battery level in WH, if EV or hybrid
     * Requires permission: {@link Car#PERMISSION_ENERGY}.
     */
    public static final int EV_BATTERY_LEVEL = 291504905;
    /**
     * EV charge port open
     * Requires permission: {@link Car#PERMISSION_ENERGY_PORTS}.
     */
    public static final int EV_CHARGE_PORT_OPEN = 287310602;
    /**
     * EV charge port connected
     * Requires permission: {@link Car#PERMISSION_ENERGY_PORTS}.
     */
    public static final int EV_CHARGE_PORT_CONNECTED = 287310603;
    /**
     * EV instantaneous charge rate in milliwatts
     * Requires permission: {@link Car#PERMISSION_ENERGY}.
     */
    public static final int EV_BATTERY_INSTANTANEOUS_CHARGE_RATE = 291504908;
    /**
     * Range remaining
     * Requires permission: {@link Car#PERMISSION_ENERGY}.
     */
    public static final int RANGE_REMAINING = 291504904;
    /**
     * Tire pressure
     *
     * min/max value indicates tire pressure sensor range.  Each tire will have a separate min/max
     * value denoted by its areaConfig.areaId.
     * Requires permission: {@link Car#PERMISSION_TIRES}.
     */
    public static final int TIRE_PRESSURE = 392168201;
    /**
     * Currently selected gear
     *
     * This is the gear selected by the user.
     * Requires permission: {@link Car#PERMISSION_POWERTRAIN}.
     */
    public static final int GEAR_SELECTION = 289408000;
    /**
     * Current gear. In non-manual case, selected gear may not
     * match the current gear. For example, if the selected gear is GEAR_DRIVE,
     * the current gear will be one of GEAR_1, GEAR_2 etc, which reflects
     * the actual gear the transmission is currently running in.
     * Requires permission: {@link Car#PERMISSION_POWERTRAIN}.
     */
    public static final int CURRENT_GEAR = 289408001;
    /**
     * Parking brake state.
     * Requires permission: {@link Car#PERMISSION_POWERTRAIN}.
     */
    public static final int PARKING_BRAKE_ON = 287310850;
    /**
     * Auto-apply parking brake.
     * Requires permission: {@link Car#PERMISSION_POWERTRAIN}.
     */
    public static final int PARKING_BRAKE_AUTO_APPLY = 287310851;
    /**
     * Warning for fuel low level.
     * Requires permission: {@link Car#PERMISSION_ENERGY}.
     */
    public static final int FUEL_LEVEL_LOW = 287310853;
    /**
     * Night mode
     * Requires permission: {@link Car#PERMISSION_EXTERIOR_ENVIRONMENT}.
     */
    public static final int NIGHT_MODE = 287310855;
    /**
     * State of the vehicles turn signals
     * Requires permission: {@link Car#PERMISSION_EXTERIOR_LIGHTS}.
     */
    public static final int TURN_SIGNAL_STATE = 289408008;
    /**
     * Represents ignition state
     * Requires permission: {@link Car#PERMISSION_POWERTRAIN}.
     */
    public static final int IGNITION_STATE = 289408009;
    /**
     * ABS is active
     * Requires permission: {@link Car#PERMISSION_CAR_DYNAMICS_STATE}.
     */
    public static final int ABS_ACTIVE = 287310858;
    /**
     * Traction Control is active
     * Requires permission: {@link Car#PERMISSION_CAR_DYNAMICS_STATE}.
     */
    public static final int TRACTION_CONTROL_ACTIVE = 287310859;
    /**
     * Fan speed setting
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_FAN_SPEED = 356517120;
    /**
     * Fan direction setting
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_FAN_DIRECTION = 356517121;
    /**
     * HVAC current temperature.
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_TEMPERATURE_CURRENT = 358614274;
    /**
     * HVAC, target temperature set.
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_TEMPERATURE_SET = 358614275;
    /**
     * On/off defrost for designated window
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_DEFROSTER = 320865540;
    /**
     * On/off AC for designated areaId
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_AC_ON = 354419973;
    /**
     * On/off max AC
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_MAX_AC_ON = 354419974;
    /**
     * On/off max defrost
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_MAX_DEFROST_ON = 354419975;
    /**
     * Recirculation on/off
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_RECIRC_ON = 354419976;
    /**
     * Enable temperature coupling between areas.
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_DUAL_ON = 354419977;
    /**
     * On/off automatic mode
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_AUTO_ON = 354419978;
    /**
     * Seat heating/cooling
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_SEAT_TEMPERATURE = 356517131;
    /**
     * Side Mirror Heat
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_SIDE_MIRROR_HEAT = 339739916;
    /**
     * Steering Wheel Heating/Cooling
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_STEERING_WHEEL_HEAT = 289408269;
    /**
     * Temperature units for display
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_TEMPERATURE_DISPLAY_UNITS = 289408270;
    /**
     * Actual fan speed
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_ACTUAL_FAN_SPEED_RPM = 356517135;
    /**
     * Represents global power state for HVAC. Setting this property to false
     * MAY mark some properties that control individual HVAC features/subsystems
     * to UNAVAILABLE state. Setting this property to true MAY mark some
     * properties that control individual HVAC features/subsystems to AVAILABLE
     * state (unless any/all of them are UNAVAILABLE on their own individual
     * merits).
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_POWER_ON = 354419984;
    /**
     * Fan Positions Available
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_FAN_DIRECTION_AVAILABLE = 356582673;
    /**
     * Automatic recirculation on/off
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_AUTO_RECIRC_ON = 354419986;
    /**
     * Seat ventilation
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_CLIMATE}.
     */
    public static final int HVAC_SEAT_VENTILATION = 356517139;
    /**
     * Distance units for display
     * Requires permission {@link Car#PERMISSION_READ_DISPLAY_UNITS} to read the property.
     * Requires permission {@link Car#PERMISSION_CONTROL_DISPLAY_UNITS} to write the property.
     */
    public static final int DISTANCE_DISPLAY_UNITS = 289408512;
    /**
     * Fuel volume units for display
     * Requires permission {@link Car#PERMISSION_READ_DISPLAY_UNITS} to read the property.
     * Requires permission {@link Car#PERMISSION_CONTROL_DISPLAY_UNITS} to write the property.
     */
    public static final int FUEL_VOLUME_DISPLAY_UNITS = 289408513;
    /**
     * Tire pressure units for display
     * Requires permission {@link Car#PERMISSION_READ_DISPLAY_UNITS} to read the property.
     * Requires permission {@link Car#PERMISSION_CONTROL_DISPLAY_UNITS} to write the property.
     */
    public static final int TIRE_PRESSURE_DISPLAY_UNITS = 289408514;
    /**
     * EV battery units for display
     * Requires permission {@link Car#PERMISSION_READ_DISPLAY_UNITS} to read the property.
     * Requires permission {@link Car#PERMISSION_CONTROL_DISPLAY_UNITS} to write the property.
     */
    public static final int EV_BATTERY_DISPLAY_UNITS = 289408515;
    /**
     * Speed Units for display
     * Requires permission {@link Car#PERMISSION_READ_DISPLAY_UNITS} to read the property.
     * Requires permission {@link Car#PERMISSION_CONTROL_DISPLAY_UNITS} to write the property.
     * @hide
     */
    public static final int VEHICLE_SPEED_DISPLAY_UNITS = 289408516;
    /**
     * Fuel consumption units for display
     * Requires permission {@link Car#PERMISSION_READ_DISPLAY_UNITS} to read the property.
     * Requires permission {@link Car#PERMISSION_CONTROL_DISPLAY_UNITS} to write the property.
     */
    public static final int FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME = 287311364;
    /**
     * Outside temperature
     * Requires permission: {@link Car#PERMISSION_EXTERIOR_ENVIRONMENT}.
     */
    public static final int ENV_OUTSIDE_TEMPERATURE = 291505923;
    /**
     * Property to control power state of application processor
     *
     * It is assumed that AP's power state is controller by separate power
     * controller.
     * Requires permission: {@link Car#PERMISSION_CAR_POWER}.
     */
    public static final int AP_POWER_STATE_REQ = 289475072;
    /**
     * Property to report power state of application processor
     *
     * It is assumed that AP's power state is controller by separate power
     * controller.
     * Requires permission: {@link Car#PERMISSION_CAR_POWER}.
     */
    public static final int AP_POWER_STATE_REPORT = 289475073;
    /**
     * Property to report bootup reason for the current power on. This is a
     * static property that will not change for the whole duration until power
     * off. For example, even if user presses power on button after automatic
     * power on with door unlock, bootup reason must stay with
     * VehicleApPowerBootupReason#USER_UNLOCK.
     * Requires permission: {@link Car#PERMISSION_CAR_POWER}.
     */
    public static final int AP_POWER_BOOTUP_REASON = 289409538;
    /**
     * Property to represent brightness of the display. Some cars have single
     * control for the brightness of all displays and this property is to share
     * change in that control.
     * Requires permission: {@link Car#PERMISSION_CAR_POWER}.
     */
    public static final int DISPLAY_BRIGHTNESS = 289409539;
    /**
     * Property to feed H/W input events to android
     */
    public static final int HW_KEY_INPUT = 289475088;
    /**
     * Door position
     *
     * This is an integer in case a door may be set to a particular position.
     * Max value indicates fully open, min value (0) indicates fully closed.
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_DOORS}.
     */
    public static final int DOOR_POS = 373295872;
    /**
     * Door move
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_DOORS}.
     */
    public static final int DOOR_MOVE = 373295873;
    /**
     * Door lock
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_DOORS}.
     */
    public static final int DOOR_LOCK = 371198722;
    /**
     * Mirror Z Position
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_MIRRORS}.
     */
    public static final int MIRROR_Z_POS = 339741504;
    /**
     * Mirror Z Move
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_MIRRORS}.
     */
    public static final int MIRROR_Z_MOVE = 339741505;
    /**
     * Mirror Y Position
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_MIRRORS}.
     */
    public static final int MIRROR_Y_POS = 339741506;
    /**
     * Mirror Y Move
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_MIRRORS}.
     */
    public static final int MIRROR_Y_MOVE = 339741507;
    /**
     * Mirror Lock
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_MIRRORS}.
     */
    public static final int MIRROR_LOCK = 287312708;
    /**
     * Mirror Fold
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_MIRRORS}.
     */
    public static final int MIRROR_FOLD = 287312709;
    /**
     * Seat memory select
     *
     * This parameter selects the memory preset to use to select the seat
     * position. The minValue is always 0, and the maxValue determines the
     * number of seat positions available.
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_MEMORY_SELECT = 356518784;
    /**
     * Seat memory set
     *
     * This setting allows the user to save the current seat position settings
     * into the selected preset slot.  The maxValue for each seat position
     * must match the maxValue for SEAT_MEMORY_SELECT.
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_MEMORY_SET = 356518785;
    /**
     * Seatbelt buckled
     *
     * True indicates belt is buckled.
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_BELT_BUCKLED = 354421634;
    /**
     * Seatbelt height position
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_BELT_HEIGHT_POS = 356518787;
    /**
     * Seatbelt height move
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_BELT_HEIGHT_MOVE = 356518788;
    /**
     * Seat fore/aft position
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_FORE_AFT_POS = 356518789;
    /**
     * Seat fore/aft move
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_FORE_AFT_MOVE = 356518790;
    /**
     * Seat backrest angle 1 position
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_BACKREST_ANGLE_1_POS = 356518791;
    /**
     * Seat backrest angle 1 move
     *
     * Moves the backrest forward or recline.
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_BACKREST_ANGLE_1_MOVE = 356518792;
    /**
     * Seat backrest angle 2 position
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_BACKREST_ANGLE_2_POS = 356518793;
    /**
     * Seat backrest angle 2 move
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_BACKREST_ANGLE_2_MOVE = 356518794;
    /**
     * Seat height position
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_HEIGHT_POS = 356518795;
    /**
     * Seat height move
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_HEIGHT_MOVE = 356518796;
    /**
     * Seat depth position
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_DEPTH_POS = 356518797;
    /**
     * Seat depth move
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_DEPTH_MOVE = 356518798;
    /**
     * Seat tilt position
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_TILT_POS = 356518799;
    /**
     * Seat tilt move
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_TILT_MOVE = 356518800;
    /**
     * Lumber fore/aft position
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_LUMBAR_FORE_AFT_POS = 356518801;
    /**
     * Lumbar fore/aft move
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_LUMBAR_FORE_AFT_MOVE = 356518802;
    /**
     * Lumbar side support position
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_LUMBAR_SIDE_SUPPORT_POS = 356518803;
    /**
     * Lumbar side support move
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_LUMBAR_SIDE_SUPPORT_MOVE = 356518804;
    /**
     * Headrest height position
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_HEADREST_HEIGHT_POS = 289409941;
    /**
     * Headrest height move
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_HEADREST_HEIGHT_MOVE = 356518806;
    /**
     * Headrest angle position
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_HEADREST_ANGLE_POS = 356518807;
    /**
     * Headrest angle move
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_HEADREST_ANGLE_MOVE = 356518808;
    /**
     * Headrest fore/aft position
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_HEADREST_FORE_AFT_POS = 356518809;
    /**
     * Headrest fore/aft move
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_HEADREST_FORE_AFT_MOVE = 356518810;
    /**
     * Seat Occupancy
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_SEATS}.
     */
    public static final int SEAT_OCCUPANCY = 356518832;
    /**
     * Window Position
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_WINDOWS}.
     */
    public static final int WINDOW_POS = 322964416;
    /**
     * Window Move
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_WINDOWS}.
     */
    public static final int WINDOW_MOVE = 322964417;
    /**
     * Window Lock
     * Requires permission: {@link Car#PERMISSION_CONTROL_CAR_WINDOWS}.
     */
    public static final int WINDOW_LOCK = 320867268;
    /**
     * Vehicle Maps Service (VMS) message
     * Requires one of permissions in {@link Car#PERMISSION_VMS_PUBLISHER},
     * {@link Car#PERMISSION_VMS_SUBSCRIBER}.
     */
    public static final int VEHICLE_MAP_SERVICE = 299895808;
    /**
     * OBD2 Live Sensor Data
     *
     * Reports a snapshot of the current (live) values of the OBD2 sensors available.
     * Requires permission: {@link Car#PERMISSION_CAR_DIAGNOSTIC_READ_ALL}.
     */
    public static final int OBD2_LIVE_FRAME = 299896064;
    /**
     * OBD2 Freeze Frame Sensor Data
     *
     * Reports a snapshot of the value of the OBD2 sensors available at the time that a fault
     * occurred and was detected.
     * Requires permission: {@link Car#PERMISSION_CAR_DIAGNOSTIC_READ_ALL}.
     */
    public static final int OBD2_FREEZE_FRAME = 299896065;
    /**
     * OBD2 Freeze Frame Information
     * Requires permission: {@link Car#PERMISSION_CAR_DIAGNOSTIC_READ_ALL}.
     */
    public static final int OBD2_FREEZE_FRAME_INFO = 299896066;
    /**
     * OBD2 Freeze Frame Clear
     *
     * This property allows deletion of any of the freeze frames stored in
     * vehicle memory, as described by OBD2_FREEZE_FRAME_INFO.
     * Requires permission: {@link Car#PERMISSION_CAR_DIAGNOSTIC_CLEAR}.
     */
    public static final int OBD2_FREEZE_FRAME_CLEAR = 299896067;
    /**
     * Headlights State
     * Requires permission: {@link Car#PERMISSION_EXTERIOR_LIGHTS}.
     */
    public static final int HEADLIGHTS_STATE = 289410560;
    /**
     * High beam lights state
     * Requires permission: {@link Car#PERMISSION_EXTERIOR_LIGHTS}.
     */
    public static final int HIGH_BEAM_LIGHTS_STATE = 289410561;
    /**
     * Fog light state
     * Requires permission: {@link Car#PERMISSION_EXTERIOR_LIGHTS}.
     */
    public static final int FOG_LIGHTS_STATE = 289410562;
    /**
     * Hazard light status
     * Requires permission: {@link Car#PERMISSION_EXTERIOR_LIGHTS}.
     */
    public static final int HAZARD_LIGHTS_STATE = 289410563;
    /**
     * Headlight switch
     * Requires permission: {@link Car#PERMISSION_CONTROL_EXTERIOR_LIGHTS}.
     */
    public static final int HEADLIGHTS_SWITCH = 289410576;
    /**
     * High beam light switch
     * Requires permission: {@link Car#PERMISSION_CONTROL_EXTERIOR_LIGHTS}.
     */
    public static final int HIGH_BEAM_LIGHTS_SWITCH = 289410577;
    /**
     * Fog light switch
     * Requires permission: {@link Car#PERMISSION_CONTROL_EXTERIOR_LIGHTS}.
     */
    public static final int FOG_LIGHTS_SWITCH = 289410578;
    /**
     * Hazard light switch
     * Requires permission: {@link Car#PERMISSION_CONTROL_EXTERIOR_LIGHTS}.
     */
    public static final int HAZARD_LIGHTS_SWITCH = 289410579;
    /**
     * Cabin lights
     * Requires permission: {@link Car#PERMISSION_READ_INTERIOR_LIGHTS}.
     */
    public static final int CABIN_LIGHTS_STATE = 289410817;
    /**
     * Cabin lights switch
     * Requires permission: {@link Car#PERMISSION_CONTROL_INTERIOR_LIGHTS}.
     */
    public static final int CABIN_LIGHTS_SWITCH = 289410818;
    /**
     * Reading lights
     * Requires permission: {@link Car#PERMISSION_READ_INTERIOR_LIGHTS}.
     */
    public static final int READING_LIGHTS_STATE = 356519683;
    /**
     * Reading lights switch
     * Requires permission: {@link Car#PERMISSION_CONTROL_INTERIOR_LIGHTS}.
     */
    public static final int READING_LIGHTS_SWITCH = 356519684;

    /**
     * @param o Integer
     * @return String
     */
    public static  String toString(int o) {
        if (o == INVALID) {
            return "INVALID";
        }
        if (o == INFO_VIN) {
            return "INFO_VIN";
        }
        if (o == INFO_MAKE) {
            return "INFO_MAKE";
        }
        if (o == INFO_MODEL) {
            return "INFO_MODEL";
        }
        if (o == INFO_MODEL_YEAR) {
            return "INFO_MODEL_YEAR";
        }
        if (o == INFO_FUEL_CAPACITY) {
            return "INFO_FUEL_CAPACITY";
        }
        if (o == INFO_FUEL_TYPE) {
            return "INFO_FUEL_TYPE";
        }
        if (o == INFO_EV_BATTERY_CAPACITY) {
            return "INFO_EV_BATTERY_CAPACITY";
        }
        if (o == INFO_EV_CONNECTOR_TYPE) {
            return "INFO_EV_CONNECTOR_TYPE";
        }
        if (o == INFO_FUEL_DOOR_LOCATION) {
            return "INFO_FUEL_DOOR_LOCATION";
        }
        if (o == INFO_EV_PORT_LOCATION) {
            return "INFO_EV_PORT_LOCATION";
        }
        if (o == INFO_DRIVER_SEAT) {
            return "INFO_DRIVER_SEAT";
        }
        if (o == PERF_ODOMETER) {
            return "PERF_ODOMETER";
        }
        if (o == PERF_VEHICLE_SPEED) {
            return "PERF_VEHICLE_SPEED";
        }
        if (o == PERF_VEHICLE_SPEED_DISPLAY) {
            return "PERF_VEHICLE_SPEED_DISPLAY";
        }
        if (o == PERF_STEERING_ANGLE) {
            return "PERF__STEERING_ANGLE";
        }
        if (o == ENGINE_COOLANT_TEMP) {
            return "ENGINE_COOLANT_TEMP";
        }
        if (o == ENGINE_OIL_LEVEL) {
            return "ENGINE_OIL_LEVEL";
        }
        if (o == ENGINE_OIL_TEMP) {
            return "ENGINE_OIL_TEMP";
        }
        if (o == ENGINE_RPM) {
            return "ENGINE_RPM";
        }
        if (o == WHEEL_TICK) {
            return "WHEEL_TICK";
        }
        if (o == FUEL_LEVEL) {
            return "FUEL_LEVEL";
        }
        if (o == FUEL_DOOR_OPEN) {
            return "FUEL_DOOR_OPEN";
        }
        if (o == EV_BATTERY_LEVEL) {
            return "EV_BATTERY_LEVEL";
        }
        if (o == EV_CHARGE_PORT_OPEN) {
            return "EV_CHARGE_PORT_OPEN";
        }
        if (o == EV_CHARGE_PORT_CONNECTED) {
            return "EV_CHARGE_PORT_CONNECTED";
        }
        if (o == EV_BATTERY_INSTANTANEOUS_CHARGE_RATE) {
            return "EV_BATTERY_INSTANTANEOUS_CHARGE_RATE";
        }
        if (o == RANGE_REMAINING) {
            return "RANGE_REMAINING";
        }
        if (o == TIRE_PRESSURE) {
            return "TIRE_PRESSURE";
        }
        if (o == GEAR_SELECTION) {
            return "GEAR_SELECTION";
        }
        if (o == CURRENT_GEAR) {
            return "CURRENT_GEAR";
        }
        if (o == PARKING_BRAKE_ON) {
            return "PARKING_BRAKE_ON";
        }
        if (o == PARKING_BRAKE_AUTO_APPLY) {
            return "PARKING_BRAKE_AUTO_APPLY";
        }
        if (o == FUEL_LEVEL_LOW) {
            return "FUEL_LEVEL_LOW";
        }
        if (o == NIGHT_MODE) {
            return "NIGHT_MODE";
        }
        if (o == TURN_SIGNAL_STATE) {
            return "TURN_SIGNAL_STATE";
        }
        if (o == IGNITION_STATE) {
            return "IGNITION_STATE";
        }
        if (o == ABS_ACTIVE) {
            return "ABS_ACTIVE";
        }
        if (o == TRACTION_CONTROL_ACTIVE) {
            return "TRACTION_CONTROL_ACTIVE";
        }
        if (o == HVAC_FAN_SPEED) {
            return "HVAC_FAN_SPEED";
        }
        if (o == HVAC_FAN_DIRECTION) {
            return "HVAC_FAN_DIRECTION";
        }
        if (o == HVAC_TEMPERATURE_CURRENT) {
            return "HVAC_TEMPERATURE_CURRENT";
        }
        if (o == HVAC_TEMPERATURE_SET) {
            return "HVAC_TEMPERATURE_SET";
        }
        if (o == HVAC_DEFROSTER) {
            return "HVAC_DEFROSTER";
        }
        if (o == HVAC_AC_ON) {
            return "HVAC_AC_ON";
        }
        if (o == HVAC_MAX_AC_ON) {
            return "HVAC_MAX_AC_ON";
        }
        if (o == HVAC_MAX_DEFROST_ON) {
            return "HVAC_MAX_DEFROST_ON";
        }
        if (o == HVAC_RECIRC_ON) {
            return "HVAC_RECIRC_ON";
        }
        if (o == HVAC_DUAL_ON) {
            return "HVAC_DUAL_ON";
        }
        if (o == HVAC_AUTO_ON) {
            return "HVAC_AUTO_ON";
        }
        if (o == HVAC_SEAT_TEMPERATURE) {
            return "HVAC_SEAT_TEMPERATURE";
        }
        if (o == HVAC_SIDE_MIRROR_HEAT) {
            return "HVAC_SIDE_MIRROR_HEAT";
        }
        if (o == HVAC_STEERING_WHEEL_HEAT) {
            return "HVAC_STEERING_WHEEL_HEAT";
        }
        if (o == HVAC_TEMPERATURE_DISPLAY_UNITS) {
            return "HVAC_TEMPERATURE_DISPLAY_UNITS";
        }
        if (o == HVAC_ACTUAL_FAN_SPEED_RPM) {
            return "HVAC_ACTUAL_FAN_SPEED_RPM";
        }
        if (o == HVAC_POWER_ON) {
            return "HVAC_POWER_ON";
        }
        if (o == HVAC_FAN_DIRECTION_AVAILABLE) {
            return "HVAC_FAN_DIRECTION_AVAILABLE";
        }
        if (o == HVAC_AUTO_RECIRC_ON) {
            return "HVAC_AUTO_RECIRC_ON";
        }
        if (o == HVAC_SEAT_VENTILATION) {
            return "HVAC_SEAT_VENTILATION";
        }
        if (o == DISTANCE_DISPLAY_UNITS) {
            return "DISTANCE_DISPLAY_UNITS";
        }
        if (o == FUEL_VOLUME_DISPLAY_UNITS) {
            return "FUEL_VOLUME_DISPLAY_UNITS";
        }
        if (o == TIRE_PRESSURE_DISPLAY_UNITS) {
            return "TIRE_PRESSURE_DISPLAY_UNITS";
        }
        if (o == EV_BATTERY_DISPLAY_UNITS) {
            return "EV_BATTERY_DISPLAY_UNITS";
        }
        if (o == FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME) {
            return "FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME";
        }
        if (o == ENV_OUTSIDE_TEMPERATURE) {
            return "ENV_OUTSIDE_TEMPERATURE";
        }
        if (o == AP_POWER_STATE_REQ) {
            return "AP_POWER_STATE_REQ";
        }
        if (o == AP_POWER_STATE_REPORT) {
            return "AP_POWER_STATE_REPORT";
        }
        if (o == AP_POWER_BOOTUP_REASON) {
            return "AP_POWER_BOOTUP_REASON";
        }
        if (o == DISPLAY_BRIGHTNESS) {
            return "DISPLAY_BRIGHTNESS";
        }
        if (o == HW_KEY_INPUT) {
            return "HW_KEY_INPUT";
        }
        if (o == DOOR_POS) {
            return "DOOR_POS";
        }
        if (o == DOOR_MOVE) {
            return "DOOR_MOVE";
        }
        if (o == DOOR_LOCK) {
            return "DOOR_LOCK";
        }
        if (o == MIRROR_Z_POS) {
            return "MIRROR_Z_POS";
        }
        if (o == MIRROR_Z_MOVE) {
            return "MIRROR_Z_MOVE";
        }
        if (o == MIRROR_Y_POS) {
            return "MIRROR_Y_POS";
        }
        if (o == MIRROR_Y_MOVE) {
            return "MIRROR_Y_MOVE";
        }
        if (o == MIRROR_LOCK) {
            return "MIRROR_LOCK";
        }
        if (o == MIRROR_FOLD) {
            return "MIRROR_FOLD";
        }
        if (o == SEAT_MEMORY_SELECT) {
            return "SEAT_MEMORY_SELECT";
        }
        if (o == SEAT_MEMORY_SET) {
            return "SEAT_MEMORY_SET";
        }
        if (o == SEAT_BELT_BUCKLED) {
            return "SEAT_BELT_BUCKLED";
        }
        if (o == SEAT_BELT_HEIGHT_POS) {
            return "SEAT_BELT_HEIGHT_POS";
        }
        if (o == SEAT_BELT_HEIGHT_MOVE) {
            return "SEAT_BELT_HEIGHT_MOVE";
        }
        if (o == SEAT_FORE_AFT_POS) {
            return "SEAT_FORE_AFT_POS";
        }
        if (o == SEAT_FORE_AFT_MOVE) {
            return "SEAT_FORE_AFT_MOVE";
        }
        if (o == SEAT_BACKREST_ANGLE_1_POS) {
            return "SEAT_BACKREST_ANGLE_1_POS";
        }
        if (o == SEAT_BACKREST_ANGLE_1_MOVE) {
            return "SEAT_BACKREST_ANGLE_1_MOVE";
        }
        if (o == SEAT_BACKREST_ANGLE_2_POS) {
            return "SEAT_BACKREST_ANGLE_2_POS";
        }
        if (o == SEAT_BACKREST_ANGLE_2_MOVE) {
            return "SEAT_BACKREST_ANGLE_2_MOVE";
        }
        if (o == SEAT_HEIGHT_POS) {
            return "SEAT_HEIGHT_POS";
        }
        if (o == SEAT_HEIGHT_MOVE) {
            return "SEAT_HEIGHT_MOVE";
        }
        if (o == SEAT_DEPTH_POS) {
            return "SEAT_DEPTH_POS";
        }
        if (o == SEAT_DEPTH_MOVE) {
            return "SEAT_DEPTH_MOVE";
        }
        if (o == SEAT_TILT_POS) {
            return "SEAT_TILT_POS";
        }
        if (o == SEAT_TILT_MOVE) {
            return "SEAT_TILT_MOVE";
        }
        if (o == SEAT_LUMBAR_FORE_AFT_POS) {
            return "SEAT_LUMBAR_FORE_AFT_POS";
        }
        if (o == SEAT_LUMBAR_FORE_AFT_MOVE) {
            return "SEAT_LUMBAR_FORE_AFT_MOVE";
        }
        if (o == SEAT_LUMBAR_SIDE_SUPPORT_POS) {
            return "SEAT_LUMBAR_SIDE_SUPPORT_POS";
        }
        if (o == SEAT_LUMBAR_SIDE_SUPPORT_MOVE) {
            return "SEAT_LUMBAR_SIDE_SUPPORT_MOVE";
        }
        if (o == SEAT_HEADREST_HEIGHT_POS) {
            return "SEAT_HEADREST_HEIGHT_POS";
        }
        if (o == SEAT_HEADREST_HEIGHT_MOVE) {
            return "SEAT_HEADREST_HEIGHT_MOVE";
        }
        if (o == SEAT_HEADREST_ANGLE_POS) {
            return "SEAT_HEADREST_ANGLE_POS";
        }
        if (o == SEAT_HEADREST_ANGLE_MOVE) {
            return "SEAT_HEADREST_ANGLE_MOVE";
        }
        if (o == SEAT_HEADREST_FORE_AFT_POS) {
            return "SEAT_HEADREST_FORE_AFT_POS";
        }
        if (o == SEAT_HEADREST_FORE_AFT_MOVE) {
            return "SEAT_HEADREST_FORE_AFT_MOVE";
        }
        if (o == SEAT_OCCUPANCY) {
            return "SEAT_OCCUPANCY";
        }
        if (o == WINDOW_POS) {
            return "WINDOW_POS";
        }
        if (o == WINDOW_MOVE) {
            return "WINDOW_MOVE";
        }
        if (o == WINDOW_LOCK) {
            return "WINDOW_LOCK";
        }
        if (o == VEHICLE_MAP_SERVICE) {
            return "VEHICLE_MAP_SERVICE";
        }
        if (o == OBD2_LIVE_FRAME) {
            return "OBD2_LIVE_FRAME";
        }
        if (o == OBD2_FREEZE_FRAME) {
            return "OBD2_FREEZE_FRAME";
        }
        if (o == OBD2_FREEZE_FRAME_INFO) {
            return "OBD2_FREEZE_FRAME_INFO";
        }
        if (o == OBD2_FREEZE_FRAME_CLEAR) {
            return "OBD2_FREEZE_FRAME_CLEAR";
        }
        if (o == HEADLIGHTS_STATE) {
            return "HEADLIGHTS_STATE";
        }
        if (o == HIGH_BEAM_LIGHTS_STATE) {
            return "HIGH_BEAM_LIGHTS_STATE";
        }
        if (o == FOG_LIGHTS_STATE) {
            return "FOG_LIGHTS_STATE";
        }
        if (o == HAZARD_LIGHTS_STATE) {
            return "HAZARD_LIGHTS_STATE";
        }
        if (o == HEADLIGHTS_SWITCH) {
            return "HEADLIGHTS_SWITCH";
        }
        if (o == HIGH_BEAM_LIGHTS_SWITCH) {
            return "HIGH_BEAM_LIGHTS_SWITCH";
        }
        if (o == FOG_LIGHTS_SWITCH) {
            return "FOG_LIGHTS_SWITCH";
        }
        if (o == HAZARD_LIGHTS_SWITCH) {
            return "HAZARD_LIGHTS_SWITCH";
        }
        if (o == CABIN_LIGHTS_STATE) {
            return "CABIN_LIGHTS_STATE";
        }
        if (o == CABIN_LIGHTS_SWITCH) {
            return "CABIN_LIGHTS_SWITCH";
        }
        if (o == READING_LIGHTS_STATE) {
            return "READING_LIGHTS_STATE";
        }
        if (o == READING_LIGHTS_SWITCH) {
            return "READING_LIGHTS_SWITCH";
        }
        if (o == VEHICLE_SPEED_DISPLAY_UNITS) {
            return "VEHICLE_SPEED_DISPLAY_UNITS";
        }
        return "0x" + Integer.toHexString(o);
    }
}
