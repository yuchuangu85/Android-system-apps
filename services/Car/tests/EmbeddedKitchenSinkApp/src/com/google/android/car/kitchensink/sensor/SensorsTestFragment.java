/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.car.kitchensink.sensor;

import static java.lang.Integer.toHexString;

import android.Manifest;
import android.annotation.Nullable;
import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.ArraySet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.KitchenSinkActivity;
import com.google.android.car.kitchensink.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SensorsTestFragment extends Fragment {
    private static final String TAG = "CAR.SENSOR.KS";
    private static final boolean DBG = true;
    private static final boolean DBG_VERBOSE = true;
    private static final int KS_PERMISSIONS_REQUEST = 1;

    private final static String[] REQUIRED_PERMISSIONS = new String[]{
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Car.PERMISSION_MILEAGE,
        Car.PERMISSION_ENERGY,
        Car.PERMISSION_SPEED,
        Car.PERMISSION_CAR_DYNAMICS_STATE
    };

    private final CarPropertyManager.CarPropertyEventCallback mCarPropertyEventCallback =
            new CarPropertyManager.CarPropertyEventCallback() {
                @Override
                public void onChangeEvent(CarPropertyValue value) {
                    if (DBG_VERBOSE) {
                        Log.v(TAG, "New car property value: " + value);
                    }
                    if (value.getStatus() == CarPropertyValue.STATUS_AVAILABLE) {
                        mValueMap.put(value.getPropertyId(), value);
                    } else {
                        mValueMap.put(value.getPropertyId(), null);
                    }
                    refreshSensorInfoText();
                }
                @Override
                public void onErrorEvent(int propId, int zone) {
                    Log.e(TAG, "propId: " + propId + " zone: " + zone);
                }
            };

    private final Handler mHandler = new Handler();
    private final Map<Integer, CarPropertyValue> mValueMap = new ConcurrentHashMap<>();


    private final DateFormat mDateFormat = SimpleDateFormat.getDateTimeInstance();

    private KitchenSinkActivity mActivity;
    private Car mCar;

    private CarPropertyManager mCarPropertyManager;

    private LocationListeners mLocationListener;
    private String mNaString;

    private List<CarPropertyConfig> mPropertyList;

    private Set<String> mActivePermissions = new HashSet<String>();

    private TextView mSensorInfo;
    private TextView mLocationInfo;
    private TextView mAccelInfo;
    private TextView mGyroInfo;
    private TextView mMagInfo;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        if (DBG) {
            Log.i(TAG, "onCreateView");
        }

        View view = inflater.inflate(R.layout.sensors, container, false);
        mActivity = (KitchenSinkActivity) getHost();
        mSensorInfo = (TextView) view.findViewById(R.id.sensor_info);
        mSensorInfo.setMovementMethod(new ScrollingMovementMethod());
        mLocationInfo = (TextView) view.findViewById(R.id.location_info);
        mLocationInfo.setMovementMethod(new ScrollingMovementMethod());
        mAccelInfo = (TextView) view.findViewById(R.id.accel_info);
        mGyroInfo = (TextView) view.findViewById(R.id.gyro_info);
        mMagInfo = (TextView) view.findViewById(R.id.mag_info);

        mNaString = getContext().getString(R.string.sensor_na);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        final Runnable r = () -> {
            initPermissions();
        };
        ((KitchenSinkActivity) getActivity()).requestRefreshManager(r,
                new Handler(getContext().getMainLooper()));
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mCarPropertyManager != null) {
            mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback);
        }
        if (mLocationListener != null) {
            mLocationListener.stopListening();
        }
    }

    private void initSensors() {
        try {
            if (mCarPropertyManager == null) {
                mCarPropertyManager =
                    (CarPropertyManager) ((KitchenSinkActivity) getActivity()).getPropertyManager();
            }
            ArraySet<Integer> set = new ArraySet<>();
            set.add(VehiclePropertyIds.PERF_VEHICLE_SPEED);
            set.add(VehiclePropertyIds.ENGINE_RPM);
            set.add(VehiclePropertyIds.PERF_ODOMETER);
            set.add(VehiclePropertyIds.FUEL_LEVEL);
            set.add(VehiclePropertyIds.FUEL_DOOR_OPEN);
            set.add(VehiclePropertyIds.IGNITION_STATE);
            set.add(VehiclePropertyIds.PARKING_BRAKE_ON);
            set.add(VehiclePropertyIds.GEAR_SELECTION);
            set.add(VehiclePropertyIds.NIGHT_MODE);
            set.add(VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE);
            set.add(VehiclePropertyIds.WHEEL_TICK);
            set.add(VehiclePropertyIds.ABS_ACTIVE);
            set.add(VehiclePropertyIds.TRACTION_CONTROL_ACTIVE);
            set.add(VehiclePropertyIds.EV_BATTERY_LEVEL);
            set.add(VehiclePropertyIds.EV_CHARGE_PORT_OPEN);
            set.add(VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED);
            set.add(VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE);
            set.add(VehiclePropertyIds.ENGINE_OIL_LEVEL);

            mPropertyList = mCarPropertyManager.getPropertyList(set);

            for (CarPropertyConfig property : mPropertyList) {
                float rate = CarPropertyManager.SENSOR_RATE_NORMAL;
                if (property.getChangeMode()
                        == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE) {
                    rate = CarPropertyManager.SENSOR_RATE_ONCHANGE;
                }
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                              property.getPropertyId(), rate);
            }
        } catch (Exception e) {
            Log.e(TAG, "initSensors() exception caught SensorManager: ", e);
        }
        try {
            if (mLocationListener == null) {
                mLocationListener = new LocationListeners(getContext(),
                                                          new LocationInfoTextUpdateListener());
            }
            mLocationListener.startListening();
        } catch (Exception e) {
            Log.e(TAG, "initSensors() exception caught from LocationListeners: ", e);
        }
    }

    private void initPermissions() {
        Set<String> missingPermissions = checkExistingPermissions();
        if (!missingPermissions.isEmpty()) {
            requestPermissions(missingPermissions);
            // The callback with premission results will take care of calling initSensors for us
        } else {
            initSensors();
        }
    }

    private Set<String> checkExistingPermissions() {
        Set<String> missingPermissions = new HashSet<String>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (mActivity.checkSelfPermission(permission)
                == PackageManager.PERMISSION_GRANTED) {
                mActivePermissions.add(permission);
            } else {
                missingPermissions.add(permission);
            }
        }
        return missingPermissions;
    }

    private void requestPermissions(Set<String> permissions) {
        Log.d(TAG, "requesting additional permissions=" + permissions);

        requestPermissions(permissions.toArray(new String[permissions.size()]),
                KS_PERMISSIONS_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult reqCode=" + requestCode);
        if (KS_PERMISSIONS_REQUEST == requestCode) {
            for (int i=0; i<permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    mActivePermissions.add(permissions[i]);
                }
            }
            initSensors();
        }
    }

    private void refreshSensorInfoText() {
        String summaryString;
        synchronized (this) {
            List<String> summary = new ArrayList<>();
            for (CarPropertyConfig property : mPropertyList) {
                int propertyId = property.getPropertyId();
                CarPropertyValue value = mValueMap.get(propertyId);
                switch (propertyId) {
                    case VehiclePropertyIds.PERF_VEHICLE_SPEED: //0x11600207  291504647
                        summary.add(getContext().getString(R.string.sensor_speed,
                                getTimestamp(value),
                                value == null ? mNaString : (float) value.getValue()));
                        break;
                    case VehiclePropertyIds.ENGINE_RPM: //0x11600305  291504901
                        summary.add(getContext().getString(R.string.sensor_rpm,
                                getTimestamp(value),
                                value == null ? mNaString : (float) value.getValue()));
                        break;
                    case VehiclePropertyIds.PERF_ODOMETER: //0x11600204 291504644
                        summary.add(getContext().getString(R.string.sensor_odometer,
                                getTimestamp(value),
                                value == null ? mNaString : (float) value.getValue()));
                        break;
                    case VehiclePropertyIds.FUEL_LEVEL: //0x11600307 291504903
                        summary.add(getFuelLevel(value));
                        break;
                    case VehiclePropertyIds.FUEL_DOOR_OPEN: //0x11200308 287310600
                        summary.add(getFuelDoorOpen(value));
                        break;
                    case VehiclePropertyIds.IGNITION_STATE: //0x11400409 289408009
                        summary.add(getContext().getString(R.string.sensor_ignition_status,
                                getTimestamp(value),
                                value == null ? mNaString :
                                (int) value.getValue()));
                        break;
                    case VehiclePropertyIds.PARKING_BRAKE_ON: //0x11200402 287310850
                        summary.add(getContext().getString(R.string.sensor_parking_brake,
                                getTimestamp(value),
                                value == null ? mNaString :
                                value.getValue()));
                        break;
                    case VehiclePropertyIds.GEAR_SELECTION: //0x11400400 289408000
                        summary.add(getContext().getString(R.string.sensor_gear,
                                getTimestamp(value),
                                value == null ? mNaString : (int) value.getValue()));
                        break;
                    case VehiclePropertyIds.NIGHT_MODE: //0x11200407 287310855
                        summary.add(getContext().getString(R.string.sensor_night,
                                getTimestamp(value),
                                value == null ? mNaString : value.getValue()));
                        break;
                    case VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE: //0x11600703 291505923
                        String temperature = mNaString;
                        if (value != null) {
                            temperature = String.valueOf((float) value.getValue());
                        }
                        summary.add(getContext().getString(R.string.sensor_environment,
                                getTimestamp(value), temperature));
                        break;
                    case VehiclePropertyIds.WHEEL_TICK: //0x11510306 290521862
                        if (value != null) {
                            Long[] wheelTickData = (Long[]) value.getValue();
                            summary.add(getContext().getString(R.string.sensor_wheel_ticks,
                                    getTimestamp(value),
                                    wheelTickData[0],
                                    wheelTickData[1],
                                    wheelTickData[2],
                                    wheelTickData[3],
                                    wheelTickData[4]));
                        } else {
                            summary.add(getContext().getString(R.string.sensor_wheel_ticks,
                                    getTimestamp(value), mNaString, mNaString, mNaString, mNaString,
                                    mNaString));
                        }
                        List<Integer> wheelProperties = property.getConfigArray();
                        summary.add(getContext().getString(R.string.sensor_wheel_ticks_cfg,
                                    wheelProperties.get(0),
                                    wheelProperties.get(1),
                                    wheelProperties.get(2),
                                    wheelProperties.get(3),
                                    wheelProperties.get(4)));
                        break;
                    case VehiclePropertyIds.ABS_ACTIVE: //0x1120040a 287310858
                        summary.add(getContext().getString(R.string.sensor_abs_is_active,
                                getTimestamp(value), value == null ? mNaString :
                                    value.getValue()));
                        break;

                    case VehiclePropertyIds.TRACTION_CONTROL_ACTIVE: //0x1120040b 287310859
                        summary.add(
                            getContext().getString(R.string.sensor_traction_control_is_active,
                                getTimestamp(value), value == null ? mNaString :
                                    value.getValue()));
                        break;
                    case VehiclePropertyIds.EV_BATTERY_LEVEL: //0x11600309 291504905
                        summary.add(getEvBatteryLevel(value));
                        break;
                    case VehiclePropertyIds.EV_CHARGE_PORT_OPEN: //0x1120030a 287310602
                        summary.add(getEvChargePortOpen(value));
                        break;
                    case VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED: //0x1120030b 287310603
                        summary.add(getEvChargePortConnected(value));
                        break;
                    case VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE:
                        summary.add(getEvChargeRate(value));
                        break;
                    case VehiclePropertyIds.ENGINE_OIL_LEVEL: //0x11400303 289407747
                        summary.add(getEngineOilLevel(value));
                        break;
                    default:
                        // Should never happen.
                        Log.w(TAG, "Unrecognized event type: " + toHexString(propertyId));
                }
            }
            summaryString = TextUtils.join("\n", summary);
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mSensorInfo.setText(summaryString);
            }
        });
    }

    private String getTimestamp(CarPropertyValue value) {
        if (value == null) {
            return mNaString;
        }
        return Double.toString(value.getTimestamp() / (1000L * 1000L * 1000L)) + " seconds";
    }

    private String getTimestampNow() {
        return Double.toString(System.nanoTime() / (1000L * 1000L * 1000L)) + " seconds";
    }

    private String getFuelLevel(CarPropertyValue value) {
        String fuelLevel = mNaString;
        if (value != null) {
            fuelLevel = String.valueOf((float) value.getValue());
        }
        return getContext().getString(R.string.sensor_fuel_level, getTimestamp(value), fuelLevel);
    }

    private String getFuelDoorOpen(CarPropertyValue value) {
        String fuelDoorOpen = mNaString;
        if (value != null) {
            fuelDoorOpen = String.valueOf(value.getValue());
        }
        return getContext().getString(R.string.sensor_fuel_door_open, getTimestamp(value),
            fuelDoorOpen);
    }

    private String getEvBatteryLevel(CarPropertyValue value) {
        String evBatteryLevel = mNaString;
        if (value != null) {
            evBatteryLevel = String.valueOf((float) value.getValue());
        }
        return getContext().getString(R.string.sensor_ev_battery_level, getTimestamp(value),
            evBatteryLevel);
    }

    private String getEvChargePortOpen(CarPropertyValue value) {
        String evChargePortOpen = mNaString;
        if (value != null) {
            evChargePortOpen = String.valueOf((float) value.getValue());
        }
        return getContext().getString(R.string.sensor_ev_charge_port_is_open, getTimestamp(value),
            evChargePortOpen);
    }

    private String getEvChargePortConnected(CarPropertyValue value) {
        String evChargePortConnected = mNaString;
        if (value != null) {
            evChargePortConnected = String.valueOf((float) value.getValue());
        }
        return getContext().getString(R.string.sensor_ev_charge_port_is_connected,
            getTimestamp(value), evChargePortConnected);
    }

    private String getEvChargeRate(CarPropertyValue value) {
        String evChargeRate = mNaString;
        if (value != null) {
            evChargeRate = String.valueOf((float) value.getValue());
        }
        return getContext().getString(R.string.sensor_ev_charge_rate, getTimestamp(value),
            evChargeRate);
    }

    private String getEngineOilLevel(CarPropertyValue value) {
        String engineOilLevel = mNaString;
        if (value != null) {
            engineOilLevel = String.valueOf((float) value.getValue());
        }
        return  getContext().getString(R.string.sensor_oil_level, getTimestamp(value),
           engineOilLevel);
    }

    public class LocationInfoTextUpdateListener {
        public void setLocationField(String value) {
            setTimestampedTextField(mLocationInfo, value);
        }

        public void setAccelField(String value) {
            setTimestampedTextField(mAccelInfo, value);
        }

        public void setGyroField(String value) {
            setTimestampedTextField(mGyroInfo, value);
        }

        public void setMagField(String value) {
            setTimestampedTextField(mMagInfo, value);
        }

        private void setTimestampedTextField(TextView text, String value) {
            synchronized (SensorsTestFragment.this) {
                text.setText(getTimestampNow() + ": " + value);
                Log.d(TAG, "setText: " + value);
            }
        }
    }
}
