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

package com.android.car.settings.wifi;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.EditTextPreference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/** Business logic for adding/displaying the network name. */
public class NetworkNamePreferenceController extends PreferenceController<EditTextPreference> {

    /** Action used in the {@link Intent} sent by the {@link LocalBroadcastManager}. */
    public static final String ACTION_NAME_CHANGE =
            "com.android.car.settings.wifi.NameChangeAction";
    /** Key used to store the name of the network. */
    public static final String KEY_NETWORK_NAME = "network_name";

    public NetworkNamePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<EditTextPreference> getPreferenceType() {
        return EditTextPreference.class;
    }

    @Override
    protected void updateState(EditTextPreference preference) {
        preference.setSummary(TextUtils.isEmpty(preference.getText()) ? getContext().getString(
                R.string.default_network_name_summary) : preference.getText());
    }

    @Override
    protected boolean handlePreferenceChanged(EditTextPreference preference, Object newValue) {
        String name = newValue.toString();
        preference.setText(name);
        notifyNameChange(name);
        refreshUi();
        return true;
    }

    private void notifyNameChange(String newName) {
        Intent intent = new Intent(ACTION_NAME_CHANGE);
        intent.putExtra(KEY_NETWORK_NAME, newName);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcastSync(intent);
    }
}
