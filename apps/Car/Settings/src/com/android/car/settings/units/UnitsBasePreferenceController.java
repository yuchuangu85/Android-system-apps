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

import android.car.CarNotConnectedException;
import android.car.drivingstate.CarUxRestrictions;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.preference.ListPreference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Shared business logic for preference controllers related to Units.
 */
public abstract class UnitsBasePreferenceController extends PreferenceController<ListPreference> {

    @VisibleForTesting
    protected final CarUnitsManager.OnCarServiceListener mOnCarServiceListener =
            new CarUnitsManager.OnCarServiceListener() {
                @Override
                public void handleServiceConnected(CarPropertyManager carPropertyManager) {
                    try {
                        if (carPropertyManager != null) {
                            carPropertyManager.registerCallback(mCarPropertyEventCallback,
                                    getPropertyId(), CarPropertyManager.SENSOR_RATE_ONCHANGE);
                        }
                        mSupportedUnits = mCarUnitsManager.getUnitsSupportedByProperty(
                                getPropertyId());
                        if (mSupportedUnits != null && mSupportedUnits.length > 0) {
                            // first element in the config array is the default Unit per VHAL spec.
                            mDefaultUnit = mSupportedUnits[0];
                            getPreference().setEntries(getEntriesOfSupportedUnits());
                            getPreference().setEntryValues(getIdsOfSupportedUnits());
                            getPreference().setValue(
                                    Integer.toString(getUnitUsedByThisProperty().getId()));
                            refreshUi();
                        }

                        mIsCarUnitsManagerStarted = true;
                    } catch (CarNotConnectedException e) {
                    }
                }

                @Override
                public void handleServiceDisconnected() {
                    mIsCarUnitsManagerStarted = false;
                }
            };

    @VisibleForTesting
    protected final CarPropertyManager.CarPropertyEventCallback mCarPropertyEventCallback =
            new CarPropertyManager.CarPropertyEventCallback() {
                @Override
                public void onChangeEvent(CarPropertyValue value) {
                    if (value != null && value.getStatus() == CarPropertyValue.STATUS_AVAILABLE) {
                        mUnitBeingUsed = UnitsMap.MAP.get(value.getValue());
                        refreshUi();
                    }
                }

                @Override
                public void onErrorEvent(int propId, int zone) {
                }
            };

    private Unit[] mSupportedUnits;
    private Unit mUnitBeingUsed;
    private Unit mDefaultUnit;
    private boolean mIsCarUnitsManagerStarted = false;
    private CarUnitsManager mCarUnitsManager;

    public UnitsBasePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    @CallSuper
    protected void onCreateInternal() {
        super.onCreateInternal();
        mCarUnitsManager = new CarUnitsManager(getContext());
        mCarUnitsManager.connect();
        mCarUnitsManager.registerCarServiceListener(mOnCarServiceListener);
    }

    @Override
    @CallSuper
    protected void onDestroyInternal() {
        super.onDestroyInternal();
        mCarUnitsManager.disconnect();
        mCarUnitsManager.unregisterCarServiceListener();
    }

    @Override
    @CallSuper
    protected void updateState(ListPreference preference) {
        if (mIsCarUnitsManagerStarted && mUnitBeingUsed != null) {
            preference.setSummary(generateSummaryFromUnit(mUnitBeingUsed));
            preference.setValue(Integer.toString(mUnitBeingUsed.getId()));
        }
    }

    @Override
    @CallSuper
    public boolean handlePreferenceChanged(ListPreference preference, Object newValue) {
        int unitId = Integer.parseInt((String) newValue);
        mCarUnitsManager.setUnitUsedByProperty(getPropertyId(), unitId);
        return true;
    }

    @Override
    protected int getAvailabilityStatus() {
        return mSupportedUnits != null && mSupportedUnits.length > 0
                ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    protected abstract int getPropertyId();

    protected String[] getEntriesOfSupportedUnits() {
        String[] names = new String[mSupportedUnits.length];
        for (int i = 0; i < names.length; i++) {
            Unit unit = mSupportedUnits[i];
            names[i] = generateEntryStringFromUnit(unit);
        }
        return names;
    }

    protected String generateSummaryFromUnit(Unit unit) {
        return getContext().getString(unit.getAbbreviationResId());
    }

    protected String generateEntryStringFromUnit(Unit unit) {
        return getContext().getString(R.string.units_list_entry,
                getContext().getString(unit.getAbbreviationResId()),
                getContext().getString(unit.getNameResId()));
    }

    protected String[] getIdsOfSupportedUnits() {
        String[] ids = new String[mSupportedUnits.length];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = Integer.toString(mSupportedUnits[i].getId());
        }
        return ids;
    }

    protected CarUnitsManager getCarUnitsManager() {
        return mCarUnitsManager;
    }

    private Unit getUnitUsedByThisProperty() {
        Unit savedUnit = mCarUnitsManager.getUnitUsedByProperty(getPropertyId());
        if (savedUnit == null) {
            return mDefaultUnit;
        }
        return savedUnit;
    }

}
