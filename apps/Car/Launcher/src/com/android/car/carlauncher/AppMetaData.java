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

package com.android.car.carlauncher;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;

/**
 * Meta data of an app including the display name, the component name, the icon drawable, and an
 * intent to either open the app or the media center (for media services).
 */

final class AppMetaData {
    // The display name of the app
    @Nullable
    private final String mDisplayName;
    // The component name of the app
    private final ComponentName mComponentName;
    private final Drawable mIcon;
    private final boolean mIsDistractionOptimized;
    private final Intent mMainLaunchIntent;
    private final Intent mAlternateLaunchIntent;

    /**
     * AppMetaData
     *
     * @param displayName            the name to display in the launcher
     * @param componentName          the component name
     * @param icon                   the application's icon
     * @param isDistractionOptimized whether mainLaunchIntent is safe for driving
     * @param mainLaunchIntent       what to open by default (goes to the media center for media
     *                               apps)
     * @param alternateLaunchIntent  temporary allowance for media apps that still need to show UI
     *                               beyond sign in and settings
     */
    AppMetaData(
            CharSequence displayName,
            ComponentName componentName,
            Drawable icon,
            boolean isDistractionOptimized,
            Intent mainLaunchIntent,
            @Nullable Intent alternateLaunchIntent) {
        mDisplayName = displayName == null ? "" : displayName.toString();
        mComponentName = componentName;
        mIcon = icon;
        mIsDistractionOptimized = isDistractionOptimized;
        mMainLaunchIntent = mainLaunchIntent;
        mAlternateLaunchIntent = alternateLaunchIntent;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public String getPackageName() {
        return getComponentName().getPackageName();
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    Intent getMainLaunchIntent() {
        return mMainLaunchIntent;
    }

    Intent getAlternateLaunchIntent() {
        return mAlternateLaunchIntent;
    }

    public Drawable getIcon() {
        return mIcon;
    }

    boolean getIsDistractionOptimized() {
        return mIsDistractionOptimized;
    }

    /**
     * The equality of two AppMetaData is determined by whether the component names are the same.
     *
     * @param o Object that this AppMetaData object is compared against
     * @return {@code true} when two AppMetaData have the same component name
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AppMetaData)) {
            return false;
        } else {
            return ((AppMetaData) o).getComponentName().equals(mComponentName);
        }
    }

    @Override
    public int hashCode() {
        return mComponentName.hashCode();
    }
}
