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

package com.android.car.settings.datausage;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.text.format.Time;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;

/**
 * Preference which opens a {@link UsageCycleResetDayOfMonthPickerDialog} in order to pick the date
 * on which the data warning/limit cycle should end.
 */
public class CycleResetDayOfMonthPickerPreferenceController extends
        DataWarningAndLimitBasePreferenceController<Preference> implements
        UsageCycleResetDayOfMonthPickerDialog.ResetDayOfMonthPickedListener {

    private static final String CYCLE_PICKER_DIALOG_TAG = "cycle_picker_dialog_tag";

    public CycleResetDayOfMonthPickerPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void onCreateInternal() {
        UsageCycleResetDayOfMonthPickerDialog dialog =
                (UsageCycleResetDayOfMonthPickerDialog) getFragmentController().findDialogByTag(
                        CYCLE_PICKER_DIALOG_TAG);
        if (dialog != null) {
            dialog.setResetDayOfMonthPickedListener(/* listener= */ this);
        }
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {

        UsageCycleResetDayOfMonthPickerDialog dialog =
                UsageCycleResetDayOfMonthPickerDialog.newInstance(
                        getNetworkPolicyEditor().getPolicyCycleDay(getNetworkTemplate()));
        dialog.setResetDayOfMonthPickedListener(/* listener= */ this);
        getFragmentController().showDialog(dialog, CYCLE_PICKER_DIALOG_TAG);
        return true;
    }

    @Override
    public void onDayOfMonthPicked(int dayOfMonth) {
        getNetworkPolicyEditor().setPolicyCycleDay(getNetworkTemplate(), dayOfMonth,
                new Time().timezone);
    }
}
