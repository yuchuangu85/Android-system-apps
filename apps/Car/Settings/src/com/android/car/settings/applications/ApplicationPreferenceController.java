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

package com.android.car.settings.applications;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.applications.ApplicationsState.AppEntry;

/** Business logic for the Application field in the application details page. */
public class ApplicationPreferenceController extends PreferenceController<Preference> {

    private AppEntry mAppEntry;
    private ApplicationsState mApplicationsState;

    public ApplicationPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    /** Sets the {@link AppEntry} which is used to load the app name and icon. */
    public ApplicationPreferenceController setAppEntry(AppEntry appEntry) {
        mAppEntry = appEntry;
        return this;
    }

    /** Sets the {@link ApplicationsState} which is used to load the app name and icon. */
    public ApplicationPreferenceController setAppState(ApplicationsState applicationsState) {
        mApplicationsState = applicationsState;
        return this;
    }

    @Override
    protected void checkInitialized() {
        if (mAppEntry == null || mApplicationsState == null) {
            throw new IllegalStateException(
                    "AppEntry and AppState should be set before calling this function");
        }
    }

    @Override
    protected void updateState(Preference preference) {
        preference.setTitle(getAppName());
        preference.setIcon(getAppIcon());
    }

    protected String getAppName() {
        mAppEntry.ensureLabel(getContext());
        return mAppEntry.label;
    }

    protected Drawable getAppIcon() {
        mApplicationsState.ensureIcon(mAppEntry);
        return mAppEntry.icon;
    }

    protected String getAppVersion() {
        return mAppEntry.getVersion(getContext());
    }
}
