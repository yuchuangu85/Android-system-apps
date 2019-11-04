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

package com.android.car.settings.applications.defaultapps;

import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.applications.DefaultAppInfo;

/**
 * Base preference which handles the logic to display the currently selected default app.
 *
 * @param <V> the upper bound on the type of {@link Preference} on which the controller expects to
 *            operate.
 */
public abstract class DefaultAppEntryBasePreferenceController<V extends Preference> extends
        PreferenceController<V> {

    private static final Logger LOG = new Logger(
            DefaultAppEntryBasePreferenceController.class);
    private final CarUserManagerHelper mCarUserManagerHelper;

    public DefaultAppEntryBasePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
    }

    @Override
    protected void updateState(V preference) {
        CharSequence defaultAppLabel = getDefaultAppLabel();
        if (!TextUtils.isEmpty(defaultAppLabel)) {
            preference.setSummary(defaultAppLabel);
            DefaultAppUtils.setSafeIcon(preference, getDefaultAppIcon(),
                    getContext().getResources().getInteger(R.integer.default_app_safe_icon_size));
        } else {
            LOG.d("No default app");
            preference.setSummary(R.string.app_list_preference_none);
            preference.setIcon(null);
        }
    }

    /** Specifies the currently selected default app. */
    @Nullable
    protected abstract DefaultAppInfo getCurrentDefaultAppInfo();

    /** Gets the current process user id. */
    protected int getCurrentProcessUserId() {
        return mCarUserManagerHelper.getCurrentProcessUserId();
    }

    private Drawable getDefaultAppIcon() {
        DefaultAppInfo app = getCurrentDefaultAppInfo();
        if (app != null) {
            return app.loadIcon();
        }
        return null;
    }

    private CharSequence getDefaultAppLabel() {
        DefaultAppInfo app = getCurrentDefaultAppInfo();
        if (app != null) {
            return app.loadLabel();
        }
        return null;
    }
}
