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
package com.google.android.car.uxr.sample;

import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_IDLING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_MOVING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_PARKED;
import static android.car.drivingstate.CarUxRestrictionsManager.UX_RESTRICTION_MODE_BASELINE;
import static android.car.drivingstate.CarUxRestrictionsManager.UX_RESTRICTION_MODE_PASSENGER;

import android.app.AlertDialog;
import android.car.Car;
import android.car.content.pm.CarPackageManager;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarDrivingStateManager;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsConfiguration;
import android.car.drivingstate.CarUxRestrictionsConfiguration.DrivingStateRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.os.Bundle;
import android.util.JsonWriter;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import java.io.CharArrayWriter;

/**
 * Sample app that uses components in car support library to demonstrate Car drivingstate UXR
 * status.
 */
public class MainActivity extends AppCompatActivity
        implements ConfigurationDialogFragment.OnConfirmListener {

    public static final String TAG = "UxRDemo";

    private static final String DIALOG_FRAGMENT_TAG = "dialog_fragment_tag";

    private Car mCar;
    private CarDrivingStateManager mCarDrivingStateManager;
    private CarUxRestrictionsManager mCarUxRestrictionsManager;
    private CarPackageManager mCarPackageManager;

    private CarUxRestrictionsManager.OnUxRestrictionsChangedListener mUxRChangeListener =
            this::updateUxRText;
    private CarDrivingStateManager.CarDrivingStateEventListener mDrvStateChangeListener =
            this::updateDrivingStateText;

    private TextView mDrvStatus;
    private TextView mDistractionOptStatus;
    private TextView mUxrStatus;
    private Button mToggleButton;
    private Button mSaveUxrConfigButton;
    private Button mShowStagedConfig;
    private Button mShowProdConfig;
    private Button mTogglePassengerMode;

    private boolean mEnableUxR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activity);

        mDrvStatus = findViewById(R.id.driving_state);
        mDistractionOptStatus = findViewById(R.id.do_status);
        mUxrStatus = findViewById(R.id.uxr_status);

        mToggleButton = findViewById(R.id.toggle_status);
        mToggleButton.setOnClickListener(v -> updateToggleUxREnable());

        mSaveUxrConfigButton = findViewById(R.id.save_uxr_config);
        mSaveUxrConfigButton.setOnClickListener(v -> showConfigurationDialog());

        mShowStagedConfig = findViewById(R.id.show_staged_config);
        mShowStagedConfig.setOnClickListener(v -> showStagedUxRestrictionsConfig());
        mShowProdConfig = findViewById(R.id.show_prod_config);
        mShowProdConfig.setOnClickListener(v -> showProdUxRestrictionsConfig());

        mTogglePassengerMode = findViewById(R.id.toggle_passenger_mode);
        mTogglePassengerMode.setOnClickListener(v -> togglePassengerMode());

        // Connect to car service
        mCar = Car.createCar(this);

        mCarDrivingStateManager = (CarDrivingStateManager) mCar.getCarManager(
                Car.CAR_DRIVING_STATE_SERVICE);
        mCarUxRestrictionsManager = (CarUxRestrictionsManager) mCar.getCarManager(
                Car.CAR_UX_RESTRICTION_SERVICE);
        mCarPackageManager = (CarPackageManager) mCar.getCarManager(
                Car.PACKAGE_SERVICE);
        if (mCarDrivingStateManager != null) {
            mCarDrivingStateManager.registerListener(mDrvStateChangeListener);
            updateDrivingStateText(
                    mCarDrivingStateManager.getCurrentCarDrivingState());
        }
        if (mCarUxRestrictionsManager != null) {
            mCarUxRestrictionsManager.registerListener(mUxRChangeListener);
            updateUxRText(mCarUxRestrictionsManager.getCurrentCarUxRestrictions());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCarUxRestrictionsManager != null) {
            mCarUxRestrictionsManager.unregisterListener();
        }
        if (mCarDrivingStateManager != null) {
            mCarDrivingStateManager.unregisterListener();
        }
        if (mCar != null) {
            mCar.disconnect();
        }
    }

    private void updateUxRText(CarUxRestrictions restrictions) {
        mDistractionOptStatus.setText(
                restrictions.isRequiresDistractionOptimization()
                        ? "Requires Distraction Optimization"
                        : "No Distraction Optimization required");

        mUxrStatus.setText("Active Restrictions : 0x"
                + Integer.toHexString(restrictions.getActiveRestrictions())
                + " - "
                + Integer.toBinaryString(restrictions.getActiveRestrictions()));

        mDistractionOptStatus.requestLayout();
        mUxrStatus.requestLayout();
    }

    private void updateToggleUxREnable() {
        if (mCarPackageManager == null) {
            return;
        }
        mCarPackageManager.setEnableActivityBlocking(mEnableUxR);
        if (mEnableUxR) {
            mToggleButton.setText("Disable UX Restrictions");
        } else {
            mToggleButton.setText("Enable UX Restrictions");
        }
        mEnableUxR = !mEnableUxR;
        mToggleButton.requestLayout();

    }

    private void updateDrivingStateText(CarDrivingStateEvent state) {
        if (state == null) {
            return;
        }
        String displayText;
        switch (state.eventValue) {
            case DRIVING_STATE_PARKED:
                displayText = "Parked";
                break;
            case DRIVING_STATE_IDLING:
                displayText = "Idling";
                break;
            case DRIVING_STATE_MOVING:
                displayText = "Moving";
                break;
            default:
                displayText = "Unknown";
        }
        mDrvStatus.setText("Driving State: " + displayText);
        mDrvStatus.requestLayout();
    }

    private void togglePassengerMode() {
        if (mCarUxRestrictionsManager == null) {
            return;
        }

        int mode = mCarUxRestrictionsManager.getRestrictionMode();
        switch (mode) {
            case UX_RESTRICTION_MODE_BASELINE:
                mCarUxRestrictionsManager.setRestrictionMode(UX_RESTRICTION_MODE_PASSENGER);
                mTogglePassengerMode.setText(R.string.disable_passenger_mode);
                break;
            case UX_RESTRICTION_MODE_PASSENGER:
                mCarUxRestrictionsManager.setRestrictionMode(UX_RESTRICTION_MODE_BASELINE);
                mTogglePassengerMode.setText(R.string.enable_passenger_mode);
                break;
            default:
                throw new IllegalStateException("Unrecognized restriction mode " + mode);
        }
    }

    private void showConfigurationDialog() {
        DialogFragment dialogFragment = ConfigurationDialogFragment.newInstance();
        dialogFragment.show(getSupportFragmentManager(), DIALOG_FRAGMENT_TAG);
    }

    @Override
    public void onConfirm(int baseline, int passenger) {

        CarUxRestrictionsConfiguration config = new CarUxRestrictionsConfiguration.Builder()
                .setUxRestrictions(DRIVING_STATE_PARKED, false, 0)
                .setUxRestrictions(DRIVING_STATE_MOVING,
                        new DrivingStateRestrictions()
                                .setDistractionOptimizationRequired(baseline != 0)
                                .setRestrictions(baseline))
                .setUxRestrictions(DRIVING_STATE_MOVING,
                        new DrivingStateRestrictions()
                                .setMode(CarUxRestrictionsManager.UX_RESTRICTION_MODE_PASSENGER)
                                .setDistractionOptimizationRequired(passenger != 0)
                                .setRestrictions(passenger))
                .setUxRestrictions(DRIVING_STATE_IDLING,
                        new DrivingStateRestrictions()
                                .setDistractionOptimizationRequired(baseline != 0)
                                .setRestrictions(baseline))
                .setUxRestrictions(DRIVING_STATE_IDLING,
                        new DrivingStateRestrictions()
                                .setMode(CarUxRestrictionsManager.UX_RESTRICTION_MODE_PASSENGER)
                                .setDistractionOptimizationRequired(passenger != 0)
                                .setRestrictions(passenger))
                .build();

        mCarUxRestrictionsManager.saveUxRestrictionsConfigurationForNextBoot(config);
    }

    private void showStagedUxRestrictionsConfig() {
        try {
            CarUxRestrictionsConfiguration stagedConfig =
                    mCarUxRestrictionsManager.getStagedConfigs().get(0);
            if (stagedConfig == null) {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.no_staged_config)
                        .show();
                return;
            }
            CharArrayWriter charWriter = new CharArrayWriter();
            JsonWriter writer = new JsonWriter(charWriter);
            writer.setIndent("\t");
            stagedConfig.writeJson(writer);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.staged_config_title)
                    .setMessage(charWriter.toString())
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showProdUxRestrictionsConfig() {
        try {
            CarUxRestrictionsConfiguration prodConfig =
                    mCarUxRestrictionsManager.getConfigs().get(0);
            if (prodConfig == null) {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.no_prod_config)
                        .show();
                return;
            }
            CharArrayWriter charWriter = new CharArrayWriter();
            JsonWriter writer = new JsonWriter(charWriter);
            writer.setIndent("\t");
            prodConfig.writeJson(writer);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.prod_config_title)
                    .setMessage(charWriter.toString())
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
