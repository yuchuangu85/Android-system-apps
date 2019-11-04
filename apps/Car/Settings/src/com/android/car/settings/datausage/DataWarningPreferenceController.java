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

import static android.net.NetworkPolicy.WARNING_DISABLED;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.datausage.UsageBytesThresholdPickerDialog.BytesThresholdPickedListener;
import com.android.settingslib.net.DataUsageController;

/** Controls setting the data warning threshold. */
public class DataWarningPreferenceController extends
        DataWarningAndLimitBasePreferenceController<PreferenceGroup> implements
        Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private final DataUsageController mDataUsageController;
    private final BytesThresholdPickedListener mThresholdPickedListener = numBytes -> {
        getNetworkPolicyEditor().setPolicyWarningBytes(getNetworkTemplate(), numBytes);
        refreshUi();
    };

    private TwoStatePreference mEnableDataWarningPreference;
    private Preference mSetDataWarningPreference;

    public DataWarningPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mDataUsageController = new DataUsageController(getContext());
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected void onCreateInternal() {
        mEnableDataWarningPreference = (TwoStatePreference) getPreference().findPreference(
                getContext().getString(R.string.pk_data_set_warning));
        mEnableDataWarningPreference.setOnPreferenceChangeListener(this);
        mSetDataWarningPreference = getPreference().findPreference(
                getContext().getString(R.string.pk_data_warning));
        mSetDataWarningPreference.setOnPreferenceClickListener(this);

        UsageBytesThresholdPickerDialog dialog =
                (UsageBytesThresholdPickerDialog) getFragmentController().findDialogByTag(
                        UsageBytesThresholdPickerDialog.TAG);
        if (dialog != null) {
            dialog.setBytesThresholdPickedListener(mThresholdPickedListener);
        }
    }

    @Override
    protected void updateState(PreferenceGroup preference) {
        long warningBytes = getNetworkPolicyEditor().getPolicyWarningBytes(getNetworkTemplate());
        if (warningBytes == WARNING_DISABLED) {
            mSetDataWarningPreference.setSummary(null);
            mEnableDataWarningPreference.setChecked(false);
        } else {
            mSetDataWarningPreference.setSummary(
                    DataUsageUtils.bytesToIecUnits(getContext(), warningBytes));
            mEnableDataWarningPreference.setChecked(true);
        }

        mSetDataWarningPreference.setEnabled(mEnableDataWarningPreference.isChecked());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean enabled = (Boolean) newValue;
        getNetworkPolicyEditor().setPolicyWarningBytes(getNetworkTemplate(),
                enabled ? mDataUsageController.getDefaultWarningLevel() : WARNING_DISABLED);
        refreshUi();
        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        UsageBytesThresholdPickerDialog dialog = UsageBytesThresholdPickerDialog.newInstance(
                R.string.data_usage_warning_editor_title,
                getNetworkPolicyEditor().getPolicyWarningBytes(getNetworkTemplate()));
        dialog.setBytesThresholdPickedListener(mThresholdPickedListener);
        getFragmentController().showDialog(dialog, UsageBytesThresholdPickerDialog.TAG);
        return true;
    }
}
