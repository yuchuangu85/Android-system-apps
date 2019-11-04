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

package com.google.android.car.kitchensink.hvac;

import static java.lang.Integer.toHexString;

import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.hvac.CarHvacManager;
import android.hardware.automotive.vehicle.V2_0.VehicleAreaSeat;
import android.hardware.automotive.vehicle.V2_0.VehicleAreaWindow;
import android.hardware.automotive.vehicle.V2_0.VehicleUnit;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.KitchenSinkActivity;
import com.google.android.car.kitchensink.R;

import java.util.List;

public class HvacTestFragment extends Fragment {
    private final boolean DBG = true;
    private final String TAG = "HvacTestFragment";
    private static final float TEMP_STEP = 0.5f;
    private RadioButton mRbFanPositionFace;
    private RadioButton mRbFanPositionFloor;
    private RadioButton mRbFanPositionFaceAndFloor;
    private RadioButton mRbFanPositionDefrost;
    private RadioButton mRbFanPositionDefrostAndFloor;
    private ToggleButton mTbAc;
    private ToggleButton mTbAuto;
    private ToggleButton mTbDefrostFront;
    private ToggleButton mTbDefrostRear;
    private ToggleButton mTbDual;
    private ToggleButton mTbMaxAc;
    private ToggleButton mTbMaxDefrost;
    private ToggleButton mTbRecirc;
    private ToggleButton mTbAutoRecirc;
    private ToggleButton mTbTempDisplayUnit;
    private ToggleButton mTbPower;
    private Button mTbPowerAndAc;
    private TextView mTvFanSpeed;
    private TextView mTvDTemp;
    private TextView mTvPTemp;
    private TextView mTvOutsideTemp;
    private int mCurFanSpeed;
    private int mMinFanSpeed;
    private int mMaxFanSpeed;
    private float mCurDTemp;
    private float mCurPTemp;
    private float mMinDTemp;
    private float mMinPTemp;
    private float mMaxDTemp;
    private float mMaxPTemp;
    private CarHvacManager mCarHvacManager;
    private int mZoneForAcOn;
    private int mZoneForSetTempD;
    private int mZoneForSetTempP;
    private int mZoneForFanSpeed;
    private int mZoneForFanPosition;
    private List<CarPropertyConfig> mCarPropertyConfigs;
    private View mHvacView;

    private final CarHvacManager.CarHvacEventCallback mHvacCallback =
            new CarHvacManager.CarHvacEventCallback() {
                @Override
                public void onChangeEvent(final CarPropertyValue value) {
                    int zones = value.getAreaId();
                    switch (value.getPropertyId()) {
                        case CarHvacManager.ID_OUTSIDE_AIR_TEMP:
                            mTvOutsideTemp.setText(String.valueOf(value.getValue()));
                            break;
                        case CarHvacManager.ID_ZONED_DUAL_ZONE_ON:
                            mTbDual.setChecked((boolean) value.getValue());
                            break;
                        case CarHvacManager.ID_ZONED_AC_ON:
                            mTbAc.setChecked((boolean) value.getValue());
                            break;
                        case CarHvacManager.ID_ZONED_AUTOMATIC_MODE_ON:
                            mTbAuto.setChecked((boolean) value.getValue());
                            break;
                        case CarHvacManager.ID_ZONED_FAN_DIRECTION:
                            switch ((int) value.getValue()) {
                                case CarHvacManager.FAN_DIRECTION_FACE:
                                    mRbFanPositionFace.setChecked(true);
                                    break;
                                case CarHvacManager.FAN_DIRECTION_FLOOR:
                                    mRbFanPositionFloor.setChecked(true);
                                    break;
                                case (CarHvacManager.FAN_DIRECTION_FACE |
                                        CarHvacManager.FAN_DIRECTION_FLOOR):
                                    mRbFanPositionFaceAndFloor.setChecked(true);
                                    break;
                                case CarHvacManager.FAN_DIRECTION_DEFROST:
                                    mRbFanPositionDefrost.setChecked(true);
                                    break;
                                case (CarHvacManager.FAN_DIRECTION_DEFROST |
                                        CarHvacManager.FAN_DIRECTION_FLOOR):
                                    mRbFanPositionDefrostAndFloor.setChecked(true);
                                    break;
                                default:
                                    if (DBG) {
                                        Log.e(TAG, "Unknown fan position: " + value.getValue());
                                    }
                                    break;
                            }
                            break;
                        case CarHvacManager.ID_ZONED_MAX_AC_ON:
                            mTbMaxAc.setChecked((boolean) value.getValue());
                            break;
                        case CarHvacManager.ID_ZONED_AIR_RECIRCULATION_ON:
                            mTbRecirc.setChecked((boolean) value.getValue());
                            break;
                        case CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT:
                            if ((zones & mZoneForFanSpeed) == mZoneForFanSpeed) {
                                mCurFanSpeed = (int) value.getValue();
                                mTvFanSpeed.setText(String.valueOf(mCurFanSpeed));
                            }
                            break;
                        case CarHvacManager.ID_ZONED_TEMP_SETPOINT:
                            if ((zones & mZoneForSetTempD) == mZoneForSetTempD) {
                                mCurDTemp = (float) value.getValue();
                                mTvDTemp.setText(String.valueOf(mCurDTemp));
                            }
                            if ((zones & mZoneForSetTempP) == mZoneForSetTempP) {
                                mCurPTemp = (float) value.getValue();
                                mTvPTemp.setText(String.valueOf(mCurPTemp));
                            }
                            break;
                        case CarHvacManager.ID_ZONED_MAX_DEFROST_ON:
                            mTbMaxDefrost.setChecked((boolean) value.getValue());
                            break;
                        case CarHvacManager.ID_WINDOW_DEFROSTER_ON:
                            if ((zones & VehicleAreaWindow.FRONT_WINDSHIELD)
                                    == VehicleAreaWindow.FRONT_WINDSHIELD) {
                                mTbDefrostFront.setChecked((boolean) value.getValue());
                            }
                            if ((zones & VehicleAreaWindow.REAR_WINDSHIELD)
                                    == VehicleAreaWindow.REAR_WINDSHIELD) {
                                mTbDefrostRear.setChecked((boolean) value.getValue());
                            }
                            break;
                        case CarHvacManager.ID_ZONED_HVAC_AUTO_RECIRC_ON:
                            mTbAutoRecirc.setChecked((boolean) value.getValue());
                            break;
                        case CarHvacManager.ID_TEMPERATURE_DISPLAY_UNITS:
                            mTbTempDisplayUnit.setChecked(
                                    ((Integer) value.getValue()).equals(VehicleUnit.FAHRENHEIT));
                            break;
                        case CarHvacManager.ID_ZONED_HVAC_POWER_ON:
                            mTbPower.setChecked((boolean) value.getValue());
                            break;
                        default:
                            Log.d(TAG, "onChangeEvent(): unknown property id = " + value
                                    .getPropertyId());
                    }
                }

                @Override
                public void onErrorEvent(final int propertyId, final int zone) {
                    Log.w(TAG, "Error:  propertyId=0x" + toHexString(propertyId)
                            + ", zone=0x" + toHexString(zone));
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCarHvacManager.unregisterCallback(mHvacCallback);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstance) {
        mHvacView = inflater.inflate(R.layout.hvac_test, container, false);
        final Runnable r = () -> {
            mCarHvacManager = ((KitchenSinkActivity) getActivity()).getHvacManager();
            mCarHvacManager.registerCallback(mHvacCallback);
            mCarPropertyConfigs = mCarHvacManager.getPropertyList();
            for (CarPropertyConfig prop : mCarPropertyConfigs) {
                int propId = prop.getPropertyId();

                if (DBG) {
                    Log.d(TAG, prop.toString());
                }

                switch (propId) {
                    case CarHvacManager.ID_OUTSIDE_AIR_TEMP:
                        configureOutsideTemp(mHvacView, prop);
                        break;
                    case CarHvacManager.ID_ZONED_DUAL_ZONE_ON:
                        configureDualOn(mHvacView, prop);
                        break;
                    case CarHvacManager.ID_ZONED_AC_ON:
                        configureAcOn(mHvacView, prop);
                        break;
                    case CarHvacManager.ID_ZONED_FAN_DIRECTION:
                        configureFanPosition(mHvacView, prop);
                        break;
                    case CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT:
                        configureFanSpeed(mHvacView, prop);
                        break;
                    case CarHvacManager.ID_ZONED_TEMP_SETPOINT:
                        configureTempSetpoint(mHvacView, prop);
                        break;
                    case CarHvacManager.ID_ZONED_AUTOMATIC_MODE_ON:
                        configureAutoModeOn(mHvacView, prop);
                        break;
                    case CarHvacManager.ID_ZONED_AIR_RECIRCULATION_ON:
                        configureRecircOn(mHvacView, prop);
                        break;
                    case CarHvacManager.ID_ZONED_MAX_AC_ON:
                        configureMaxAcOn(mHvacView, prop);
                        break;
                    case CarHvacManager.ID_ZONED_MAX_DEFROST_ON:
                        configureMaxDefrostOn(mHvacView, prop);
                        break;
                    case CarHvacManager.ID_WINDOW_DEFROSTER_ON:
                        configureDefrosterOn(mHvacView, prop);
                        break;
                    case CarHvacManager.ID_ZONED_HVAC_AUTO_RECIRC_ON:
                        configureAutoRecircOn(mHvacView, prop);
                        break;
                    case CarHvacManager.ID_TEMPERATURE_DISPLAY_UNITS:
                        configureTempDisplayUnit(mHvacView, prop);
                        break;
                    case CarHvacManager.ID_ZONED_HVAC_POWER_ON:
                        configurePowerOn(mHvacView, prop);
                        configurePowerAndAcOn(mHvacView, prop);
                        break;
                    default:
                        Log.w(TAG, "propertyId " + propId + " is not handled");
                        break;
                }
            }

            mTvFanSpeed = (TextView) mHvacView.findViewById(R.id.tvFanSpeed);
            mTvFanSpeed.setText(String.valueOf(mCurFanSpeed));
            mTvDTemp = (TextView) mHvacView.findViewById(R.id.tvDTemp);
            mTvDTemp.setText(String.valueOf(mCurDTemp));
            mTvPTemp = (TextView) mHvacView.findViewById(R.id.tvPTemp);
            mTvPTemp.setText(String.valueOf(mCurPTemp));
            mTvOutsideTemp = (TextView) mHvacView.findViewById(R.id.tvOutsideTemp);
            mTvOutsideTemp.setText("N/A");
        };

        ((KitchenSinkActivity) getActivity()).requestRefreshManager(r,
                new Handler(getContext().getMainLooper()));

        if (DBG) {
            Log.d(TAG, "Starting HvacTestFragment");
        }

        return mHvacView;
    }

    private void configureOutsideTemp(View v, CarPropertyConfig prop) {
        // Do nothing
    }

    private void configureDualOn(View v, CarPropertyConfig prop) {
        int temp = prop.getFirstAndOnlyAreaId();
        mTbDual = (ToggleButton) v.findViewById(R.id.tbDual);
        mTbDual.setEnabled(true);

        mTbDual.setOnClickListener(view -> {
            // TODO handle zone properly
            setBooleanProperty(CarHvacManager.ID_ZONED_DUAL_ZONE_ON, temp, mTbDual.isChecked());
        });
    }

    private void configureAcOn(View v, CarPropertyConfig prop) {
        mZoneForAcOn = prop.getFirstAndOnlyAreaId();
        mTbAc = (ToggleButton) v.findViewById(R.id.tbAc);
        mTbAc.setEnabled(true);

        mTbAc.setOnClickListener(view -> {
            // TODO handle zone properly
            setBooleanProperty(CarHvacManager.ID_ZONED_AC_ON, mZoneForAcOn, mTbAc.isChecked());
        });
    }

    private void configureAutoModeOn(View v, CarPropertyConfig prop) {
        int temp = prop.getFirstAndOnlyAreaId();
        mTbAuto = (ToggleButton) v.findViewById(R.id.tbAuto);
        mTbAuto.setEnabled(true);

        mTbAuto.setOnClickListener(view -> {
            // TODO handle zone properly
            setBooleanProperty(CarHvacManager.ID_ZONED_AUTOMATIC_MODE_ON, temp,
                    mTbAuto.isChecked());
        });
    }

    private void configureFanPosition(View v, CarPropertyConfig prop) {
        mZoneForFanPosition = prop.getFirstAndOnlyAreaId();
        RadioGroup rg = (RadioGroup) v.findViewById(R.id.rgFanPosition);
        rg.setOnCheckedChangeListener((group, checkedId) -> {
            int position;
            switch (checkedId) {
                case R.id.rbPositionFace:
                    position = CarHvacManager.FAN_DIRECTION_FACE;
                    break;
                case R.id.rbPositionFloor:
                    position = CarHvacManager.FAN_DIRECTION_FLOOR;
                    break;
                case R.id.rbPositionFaceAndFloor:
                    position = (CarHvacManager.FAN_DIRECTION_FACE |
                            CarHvacManager.FAN_DIRECTION_FLOOR);
                    break;
                case R.id.rbPositionDefrost:
                    position = CarHvacManager.FAN_DIRECTION_DEFROST;
                    break;
                case R.id.rbPositionDefrostAndFloor:
                    position = (CarHvacManager.FAN_DIRECTION_DEFROST |
                            CarHvacManager.FAN_DIRECTION_FLOOR);
                    break;
                default:
                    throw new IllegalStateException("Unexpected fan position: " + checkedId);
            }
            try {
                if (mCarHvacManager.isPropertyAvailable(CarHvacManager.ID_ZONED_FAN_DIRECTION,
                        mZoneForFanSpeed)) {
                    mCarHvacManager.setIntProperty(CarHvacManager.ID_ZONED_FAN_DIRECTION,
                            mZoneForFanPosition,
                            position);
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Failed to set HVAC integer property", e);
                Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        mRbFanPositionFace = (RadioButton) v.findViewById(R.id.rbPositionFace);
        mRbFanPositionFace.setClickable(true);
        mRbFanPositionFloor = (RadioButton) v.findViewById(R.id.rbPositionFloor);
        mRbFanPositionFloor.setClickable(true);
        mRbFanPositionFaceAndFloor = (RadioButton) v.findViewById(R.id.rbPositionFaceAndFloor);
        mRbFanPositionFaceAndFloor.setClickable(true);
        mRbFanPositionDefrost = (RadioButton) v.findViewById(R.id.rbPositionDefrost);
        mRbFanPositionDefrost.setClickable(true);
        mRbFanPositionDefrostAndFloor = (RadioButton) v.findViewById(
                R.id.rbPositionDefrostAndFloor);
        mRbFanPositionDefrostAndFloor.setClickable(true);
    }

    private void configureFanSpeed(View v, CarPropertyConfig prop) {

        mZoneForFanSpeed = prop.getFirstAndOnlyAreaId();
        mMaxFanSpeed = (Integer) prop.getMaxValue(mZoneForFanSpeed);
        mMinFanSpeed = (Integer) prop.getMinValue(mZoneForFanSpeed);

        try {
            mCurFanSpeed = mCarHvacManager.getIntProperty(
                    CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT,
                    mZoneForFanSpeed);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to get HVAC int property", e);
        }

        Button btnFanSpeedUp = (Button) v.findViewById(R.id.btnFanSpeedUp);
        btnFanSpeedUp.setEnabled(true);
        btnFanSpeedUp.setOnClickListener(view -> changeFanSpeed(1));

        Button btnFanSpeedDn = (Button) v.findViewById(R.id.btnFanSpeedDn);
        btnFanSpeedDn.setEnabled(true);
        btnFanSpeedDn.setOnClickListener(view -> changeFanSpeed(-1));
    }

    private void changeFanSpeed(int change) {
        int targetSpeed = mCurFanSpeed + change;
        if (mMinFanSpeed < targetSpeed && targetSpeed < mMaxFanSpeed) {
            mCurFanSpeed = targetSpeed;
            mTvFanSpeed.setText(String.valueOf(mCurFanSpeed));
            try {
                if (mCarHvacManager.isPropertyAvailable(
                        CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT, mZoneForFanSpeed)) {
                    mCarHvacManager.setIntProperty(CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT,
                            mZoneForFanSpeed, mCurFanSpeed);
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Failed to set HVAC int property", e);
                Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void configureTempSetpoint(View v, CarPropertyConfig prop) {

        mZoneForSetTempD = 0;
        if (prop.hasArea(VehicleAreaSeat.ROW_1_LEFT | VehicleAreaSeat.ROW_2_LEFT
                | VehicleAreaSeat.ROW_2_CENTER)) {
            mZoneForSetTempD = VehicleAreaSeat.ROW_1_LEFT | VehicleAreaSeat.ROW_2_LEFT
                    | VehicleAreaSeat.ROW_2_CENTER;
        }
        mZoneForSetTempP = 0;
        if (prop.hasArea(VehicleAreaSeat.ROW_1_RIGHT | VehicleAreaSeat.ROW_2_RIGHT)) {
            mZoneForSetTempP = VehicleAreaSeat.ROW_1_RIGHT | VehicleAreaSeat.ROW_2_RIGHT;
        }
        int[] areas = prop.getAreaIds();
        if (mZoneForSetTempD == 0 && areas.length > 1) {
            mZoneForSetTempD = areas[0];
        }
        if (mZoneForSetTempP == 0 && areas.length > 2) {
            mZoneForSetTempP = areas[1];
        }
        Button btnDTempUp = (Button) v.findViewById(R.id.btnDTempUp);
        if (mZoneForSetTempD != 0) {
            mMaxDTemp = (Float) prop.getMaxValue(mZoneForSetTempD);
            mMinDTemp = (Float) prop.getMinValue(mZoneForSetTempD);


            try {
                mCurDTemp = mCarHvacManager.getFloatProperty(CarHvacManager.ID_ZONED_TEMP_SETPOINT,
                        mZoneForSetTempD);
                if (mCurDTemp < mMinDTemp) {
                    mCurDTemp = mMinDTemp;
                } else if (mCurDTemp > mMaxDTemp) {
                    mCurDTemp = mMaxDTemp;
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Failed to get HVAC zoned temp property", e);
            }
            btnDTempUp.setEnabled(true);
            btnDTempUp.setOnClickListener(view -> changeDriverTemperature(TEMP_STEP));

            Button btnDTempDn = (Button) v.findViewById(R.id.btnDTempDn);
            btnDTempDn.setEnabled(true);
            btnDTempDn.setOnClickListener(view -> changeDriverTemperature(-TEMP_STEP));
        } else {
            btnDTempUp.setEnabled(false);
        }

        Button btnPTempUp = (Button) v.findViewById(R.id.btnPTempUp);
        if (mZoneForSetTempP != 0) {
            mMaxPTemp = (Float) prop.getMaxValue(mZoneForSetTempP);
            mMinPTemp = (Float) prop.getMinValue(mZoneForSetTempP);

            try {
                mCurPTemp = mCarHvacManager.getFloatProperty(CarHvacManager.ID_ZONED_TEMP_SETPOINT,
                        mZoneForSetTempP);
                if (mCurPTemp < mMinPTemp) {
                    mCurPTemp = mMinPTemp;
                } else if (mCurPTemp > mMaxPTemp) {
                    mCurPTemp = mMaxPTemp;
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Failed to get HVAC zoned temp property", e);
            }
            btnPTempUp.setEnabled(true);
            btnPTempUp.setOnClickListener(view -> changePassengerTemperature(TEMP_STEP));

            Button btnPTempDn = (Button) v.findViewById(R.id.btnPTempDn);
            btnPTempDn.setEnabled(true);
            btnPTempDn.setOnClickListener(view -> changePassengerTemperature(-TEMP_STEP));
        } else {
            btnPTempUp.setEnabled(false);
        }

        Button btnATempUp = (Button) v.findViewById(R.id.btnATempUp);
        if (mZoneForSetTempD != 0 && mZoneForSetTempP != 0) {
            btnATempUp.setEnabled(true);
            btnATempUp.setOnClickListener(view -> {
                changeDriverTemperature(TEMP_STEP);
                changePassengerTemperature(TEMP_STEP);
            });

            Button btnATempDn = (Button) v.findViewById(R.id.btnATempDn);
            btnATempDn.setEnabled(true);
            btnATempDn.setOnClickListener(view -> {
                changeDriverTemperature(-TEMP_STEP);
                changePassengerTemperature(-TEMP_STEP);
            });
        } else {
            btnATempUp.setEnabled(false);
        }
    }

    private void changeDriverTemperature(float tempStep) {
        float targetTemp = mCurDTemp + tempStep;
        if (mMinDTemp < targetTemp && targetTemp < mMaxDTemp) {
            mCurDTemp = targetTemp;
            mTvDTemp.setText(String.valueOf(mCurDTemp));
            setFloatProperty(CarHvacManager.ID_ZONED_TEMP_SETPOINT, mZoneForSetTempD, mCurDTemp);
        }
    }

    private void changePassengerTemperature(float tempStep) {
        float targetTemp = mCurPTemp + tempStep;
        if (mMinPTemp < targetTemp && targetTemp < mMaxPTemp) {
            mCurPTemp = targetTemp;
            mTvPTemp.setText(String.valueOf(mCurPTemp));
            setFloatProperty(CarHvacManager.ID_ZONED_TEMP_SETPOINT, mZoneForSetTempP, mCurPTemp);
        }
    }

    private void configureDefrosterOn(View v, CarPropertyConfig prop1) {
        if (prop1.hasArea(VehicleAreaWindow.FRONT_WINDSHIELD)) {
            mTbDefrostFront = (ToggleButton) v.findViewById(R.id.tbDefrostFront);
            mTbDefrostFront.setEnabled(true);
            mTbDefrostFront.setOnClickListener(view -> {
                setBooleanProperty(CarHvacManager.ID_WINDOW_DEFROSTER_ON,
                        VehicleAreaWindow.FRONT_WINDSHIELD, mTbDefrostFront.isChecked());
            });
        }
        if (prop1.hasArea(VehicleAreaWindow.REAR_WINDSHIELD)) {
            mTbDefrostRear = (ToggleButton) v.findViewById(R.id.tbDefrostRear);
            mTbDefrostRear.setEnabled(true);
            mTbDefrostRear.setOnClickListener(view -> {
                setBooleanProperty(CarHvacManager.ID_WINDOW_DEFROSTER_ON,
                        VehicleAreaWindow.REAR_WINDSHIELD, mTbDefrostRear.isChecked());
            });
        }
    }

    private void configureRecircOn(View v, CarPropertyConfig prop) {
        int temp = prop.getFirstAndOnlyAreaId();
        mTbRecirc = (ToggleButton) v.findViewById(R.id.tbRecirc);
        mTbRecirc.setEnabled(true);

        mTbRecirc.setOnClickListener(view -> {
            // TODO handle zone properly
            setBooleanProperty(CarHvacManager.ID_ZONED_AIR_RECIRCULATION_ON, temp,
                    mTbRecirc.isChecked());
        });
    }

    private void configureMaxAcOn(View v, CarPropertyConfig prop) {
        int temp = prop.getFirstAndOnlyAreaId();
        mTbMaxAc = (ToggleButton) v.findViewById(R.id.tbMaxAc);
        mTbMaxAc.setEnabled(true);

        mTbMaxAc.setOnClickListener(view -> {
            // TODO handle zone properly
            setBooleanProperty(CarHvacManager.ID_ZONED_MAX_AC_ON, temp, mTbMaxAc.isChecked());
        });
    }

    private void configureMaxDefrostOn(View v, CarPropertyConfig prop) {
        int temp = prop.getFirstAndOnlyAreaId();
        mTbMaxDefrost = (ToggleButton) v.findViewById(R.id.tbMaxDefrost);
        mTbMaxDefrost.setEnabled(true);

        mTbMaxDefrost.setOnClickListener(view -> {
            // TODO handle zone properly
            setBooleanProperty(CarHvacManager.ID_ZONED_MAX_DEFROST_ON, temp,
                    mTbMaxDefrost.isChecked());
        });
    }

    private void configureAutoRecircOn(View v, CarPropertyConfig prop) {
        // TODO handle areaId properly
        int areaId = prop.getFirstAndOnlyAreaId();
        mTbAutoRecirc = (ToggleButton) v.findViewById(R.id.tbAutoRecirc);
        mTbAutoRecirc.setEnabled(true);

        mTbAutoRecirc.setOnClickListener(view -> {
            setBooleanProperty(CarHvacManager.ID_ZONED_HVAC_AUTO_RECIRC_ON, areaId,
                    mTbAutoRecirc.isChecked());
        });
    }

    private void configureTempDisplayUnit(View v, CarPropertyConfig prop) {
        int areaId = prop.getFirstAndOnlyAreaId();
        mTbTempDisplayUnit = (ToggleButton) v.findViewById(R.id.tbTempDisplayUnit);
        mTbTempDisplayUnit.setEnabled(true);

        mTbTempDisplayUnit.setOnClickListener(view -> {
            int unit =
                    (mTbTempDisplayUnit.isChecked() ? VehicleUnit.FAHRENHEIT : VehicleUnit.CELSIUS);
            setIntProperty(CarHvacManager.ID_TEMPERATURE_DISPLAY_UNITS, areaId, unit);
        });
    }

    private void configurePowerOn(View v, CarPropertyConfig prop) {
        // TODO handle areaId properly
        int areaId = prop.getFirstAndOnlyAreaId();
        mTbPower = (ToggleButton) v.findViewById(R.id.tbPower);
        mTbPower.setEnabled(true);

        mTbPower.setOnClickListener(view -> {
            setBooleanProperty(CarHvacManager.ID_ZONED_HVAC_POWER_ON, areaId, mTbPower.isChecked());
        });
    }

    private void configurePowerAndAcOn(View v, CarPropertyConfig prop) {
        // TODO handle areaId properly
        int areaId = prop.getFirstAndOnlyAreaId();
        mTbPowerAndAc = (Button) v.findViewById(R.id.tbPowerAndAc);
        mTbPowerAndAc.setEnabled(true);

        mTbPowerAndAc.setOnClickListener(view -> {
            setBooleanProperty(CarHvacManager.ID_ZONED_HVAC_POWER_ON, areaId, true);
            setBooleanProperty(CarHvacManager.ID_ZONED_AC_ON, areaId, true);
        });
    }

    private void setBooleanProperty(int propertyId, int areaId, boolean value) {
        try {
            mCarHvacManager.setBooleanProperty(propertyId, areaId, value);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to set boolean property", e);
            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setIntProperty(int propertyId, int areaId, int value) {
        try {
            mCarHvacManager.setIntProperty(propertyId, areaId, value);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to set int property", e);
            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setFloatProperty(int propertyId, int areaId, float value) {
        try {
            mCarHvacManager.setFloatProperty(propertyId, areaId, value);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to set float property", e);
            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
