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
import android.net.NetworkTemplate;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.NetworkPolicyEditor;

/**
 * Defines the shared getters and setters used by {@link PreferenceController} implementations
 * related to data usage warning and limit.
 *
 * @param <V> the upper bound on the type of {@link Preference} on which the controller
 *            expects to operate.
 */
public abstract class DataWarningAndLimitBasePreferenceController<V extends Preference> extends
        PreferenceController<V> {

    private NetworkPolicyEditor mNetworkPolicyEditor;
    private NetworkTemplate mNetworkTemplate;

    public DataWarningAndLimitBasePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    /** Gets the {@link NetworkPolicyEditor}. */
    public NetworkPolicyEditor getNetworkPolicyEditor() {
        return mNetworkPolicyEditor;
    }

    /** Sets the {@link NetworkPolicyEditor}. */
    public void setNetworkPolicyEditor(NetworkPolicyEditor networkPolicyEditor) {
        mNetworkPolicyEditor = networkPolicyEditor;
    }

    /** Gets the {@link NetworkTemplate}. */
    public NetworkTemplate getNetworkTemplate() {
        return mNetworkTemplate;
    }

    /** Sets the {@link NetworkTemplate}. */
    public void setNetworkTemplate(NetworkTemplate networkTemplate) {
        mNetworkTemplate = networkTemplate;
    }
}
