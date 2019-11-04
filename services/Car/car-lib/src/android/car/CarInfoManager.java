/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.annotation.NonNull;
import android.car.annotation.ValueTypeDef;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.ICarProperty;
import android.os.Bundle;
import android.os.IBinder;


/**
 * Utility to retrieve various static information from car. Each data are grouped as {@link Bundle}
 * and relevant data can be checked from {@link Bundle} using pre-specified keys.
 */
public final class CarInfoManager implements CarManagerBase{

    private final CarPropertyManager mCarPropertyMgr;
    /**
     * Key for manufacturer of the car. Passed in basic info Bundle.
     * @hide
     */
    @ValueTypeDef(type = Integer.class)
    public static final int BASIC_INFO_KEY_MANUFACTURER = 0x11100101;
    /**
     * Key for model name of the car. This information may not necessarily allow distinguishing
     * different car models as the same name may be used for different cars depending on
     * manufacturers. Passed in basic info Bundle.
     * @hide
     */
    @ValueTypeDef(type = Integer.class)
    public static final int BASIC_INFO_KEY_MODEL = 0x11100102;
    /**
     * Key for model year of the car in AD. Passed in basic info Bundle.
     * @hide
     */
    @ValueTypeDef(type = Integer.class)
    public static final int BASIC_INFO_KEY_MODEL_YEAR = 0x11400103;
    /**
     * Key for unique identifier for the car. This is not VIN, and id is persistent until user
     * resets it. Passed in basic info Bundle.
     * @hide
     */
    @ValueTypeDef(type = String.class)
    public static final String BASIC_INFO_KEY_VEHICLE_ID = "android.car.vehicle-id";
    /**
     * Key for product configuration info.
     * @FutureFeature Cannot drop due to usage in non-flag protected place.
     * @hide
     */
    @ValueTypeDef(type = String.class)
    public static final String INFO_KEY_PRODUCT_CONFIGURATION = "android.car.product-config";
    /**
     * Key for driver seat of the car.
     * @hide
     */
    @ValueTypeDef(type = Integer.class)
    public static final int BASIC_INFO_DRIVER_SEAT = 0x1540010a;
    /**
     * Key for EV port location of vehicle.
     * @hide
     */
    @ValueTypeDef(type = Integer.class)
    public static final int BASIC_INFO_EV_PORT_LOCATION = 0x11400109;
    /**
     * Key for fuel door location of vehicle.
     * @hide
     */
    @ValueTypeDef(type = Integer.class)
    public static final int BASIC_INFO_FUEL_DOOR_LOCATION = 0x11400108;
    /**
     * Key for Fuel Capacity in milliliters.  Passed in basic info Bundle.
     * @hide
     */
    @ValueTypeDef(type = Integer.class)
    public static final int BASIC_INFO_FUEL_CAPACITY = 0x11600104;
    /**
     * Key for Fuel Types.  This is an array of fuel types the vehicle supports.
     * Passed in basic info Bundle.
     * @hide
     */
    @ValueTypeDef(type = Integer.class)
    public static final int BASIC_INFO_FUEL_TYPES = 0x11410105;
    /**
     * Key for EV Battery Capacity in WH.  Passed in basic info Bundle.
     * @hide
     */
    @ValueTypeDef(type = Integer.class)
    public static final int BASIC_INFO_EV_BATTERY_CAPACITY = 0x11600106;
    /**
     * Key for EV Connector Types.  This is an array of connector types the vehicle supports.
     * Passed in basic info Bundle.
     * @hide
     */
    @ValueTypeDef(type = Integer[].class)
    public static final int BASIC_INFO_EV_CONNECTOR_TYPES = 0x11410107;

    /**
     * @return Manufacturer of the car.  Empty if not available.
     */
    @NonNull
    public String getManufacturer() {
        CarPropertyValue<String> carProp = mCarPropertyMgr.getProperty(String.class,
                BASIC_INFO_KEY_MANUFACTURER, 0);
        return carProp != null ? carProp.getValue() : "";
    }

    /**
     * @return Model name of the car, empty if not available.  This information
     * may not necessarily allow distinguishing different car models as the same
     * name may be used for different cars depending on manufacturers.
     */
    @NonNull
    public String getModel() {
        CarPropertyValue<String> carProp = mCarPropertyMgr.getProperty(
                String.class, BASIC_INFO_KEY_MODEL, 0);
        return carProp != null ? carProp.getValue() : "";
    }

    /**
     * @return Model year of the car in AD.  Empty if not available.
     * @deprecated Use {@link #getModelYearInInteger()} instead.
     */
    @Deprecated
    @NonNull
    public String getModelYear() {
        int year =  mCarPropertyMgr.getIntProperty(BASIC_INFO_KEY_MODEL_YEAR, 0);
        return year == 0 ? "" : Integer.toString(year);
    }

    /**
     * @return Model year of the car in AD.  0 if not available.
     */
    public int getModelYearInInteger() {
        return mCarPropertyMgr.getIntProperty(BASIC_INFO_KEY_MODEL_YEAR, 0);
    }

    /**
     * @return always return empty string.
     * @deprecated no support for car's identifier
     */
    @Deprecated
    public String getVehicleId() {
        return "";
    }

    /**
     * @return Fuel capacity of the car in milliliters.  0 if car doesn't run on
     *         fuel.
     */
    public float getFuelCapacity() {
        return mCarPropertyMgr.getFloatProperty(BASIC_INFO_FUEL_CAPACITY, 0);
    }

    /**
     * @return Array of FUEL_TYPEs available in the car.  Empty array if no fuel
     * types available.
     */
    public @FuelType.Enum int[] getFuelTypes() {
        return mCarPropertyMgr.getIntArrayProperty(BASIC_INFO_FUEL_TYPES, 0);
    }

    /**
     *
     * @return Battery capacity of the car in Watt-Hour(Wh). Return 0 if car doesn't run on battery.
     */
    public float getEvBatteryCapacity() {
        CarPropertyValue<Float> carProp = mCarPropertyMgr.getProperty(Float.class,
                BASIC_INFO_EV_BATTERY_CAPACITY, 0);
        return carProp != null ? carProp.getValue() : 0f;
    }

    /**
     * @return Array of EV_CONNECTOR_TYPEs available in the car.  Empty array if
     *         no connector types available.
     */
    public @EvConnectorType.Enum int[] getEvConnectorTypes() {
        int[] valueInHal =
                mCarPropertyMgr.getIntArrayProperty(BASIC_INFO_EV_CONNECTOR_TYPES, 0);
        int[] connectorTypes = new int[valueInHal.length];
        for (int i = 0; i < valueInHal.length; i++) {
            switch (valueInHal[i]) {
                case 1: // IEC_TYPE_1_AC
                    connectorTypes[i] = EvConnectorType.J1772;
                    break;
                case 2: // IEC_TYPE_2_AC
                    connectorTypes[i] = EvConnectorType.MENNEKES;
                    break;
                case 3: // IEC_TYPE_3_AC
                    connectorTypes[i] = 11;
                    break;
                case 4: // IEC_TYPE_4_DC
                    connectorTypes[i] = EvConnectorType.CHADEMO;
                    break;
                case 5: // IEC_TYPE_1_CCS_DC
                    connectorTypes[i] = EvConnectorType.COMBO_1;
                    break;
                case 6: // IEC_TYPE_2_CCS_DC
                    connectorTypes[i] = EvConnectorType.COMBO_2;
                    break;
                case 7: // TESLA_ROADSTER
                    connectorTypes[i] = EvConnectorType.TESLA_ROADSTER;
                    break;
                case 8: // TESLA_HPWC
                    connectorTypes[i] = EvConnectorType.TESLA_HPWC;
                    break;
                case 9: // TESLA_SUPERCHARGER
                    connectorTypes[i] = EvConnectorType.TESLA_SUPERCHARGER;
                    break;
                case 10: // GBT_AC
                    connectorTypes[i] = EvConnectorType.GBT;
                    break;
                case 11: // GBT_DC
                    connectorTypes[i] = 10;
                    break;
                case 101: // OTHER
                    connectorTypes[i] = EvConnectorType.OTHER;
                    break;
                default:
                    connectorTypes[i] = EvConnectorType.UNKNOWN;
            }
        }
        return connectorTypes;
    }

    /**
     * @return Driver seat's location.
     */
    public @VehicleAreaSeat.Enum int getDriverSeat() {
        return mCarPropertyMgr.getIntProperty(BASIC_INFO_DRIVER_SEAT, 0);
    }

    /**
     * @return EV port location of the car.
     */
    public @PortLocationType.Enum int getEvPortLocation() {
        return mCarPropertyMgr.getIntProperty(BASIC_INFO_EV_PORT_LOCATION, 0);
    }

    /**
     * @return Fuel door location of the car.
     */
    public @PortLocationType.Enum int getFuelDoorLocation() {
        return mCarPropertyMgr.getIntProperty(BASIC_INFO_FUEL_DOOR_LOCATION, 0);
    }

    /** @hide */
    CarInfoManager(IBinder service) {
        ICarProperty mCarPropertyService = ICarProperty.Stub.asInterface(service);
        mCarPropertyMgr = new CarPropertyManager(mCarPropertyService, null);
    }

    /** @hide */
    public void onCarDisconnected() {
        mCarPropertyMgr.onCarDisconnected();
    }


}
