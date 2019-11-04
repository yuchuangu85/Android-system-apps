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
package com.android.customization.model.theme.custom;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.UserHandle;

import com.android.customization.model.CustomizationManager.OptionsFetchedListener;
import com.android.customization.model.ResourceConstants;
import com.android.customization.model.theme.OverlayManagerCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class used to retrieve Custom Theme Component options (eg, different fonts)
 * from the system.
 */
public abstract class ThemeComponentOptionProvider<T extends ThemeComponentOption> {

    protected final Context mContext;
    protected final List<String> mOverlayPackages;
    protected List<T> mOptions;

    public ThemeComponentOptionProvider(Context context, OverlayManagerCompat manager,
            String... categories) {
        mContext = context;
        mOverlayPackages = new ArrayList<>();
        for (String category : categories) {
            mOverlayPackages.addAll(manager.getOverlayPackagesForCategory(category,
                    UserHandle.myUserId(), ResourceConstants.getPackagesToOverlay(mContext)));
        }
    }

    /**
     * Returns whether there are options for this component available in the current setup.
     */
    public boolean isAvailable() {
        return !mOverlayPackages.isEmpty();
    }

    /**
     * Retrieve the available options for this component.
     * @param callback called when the themes have been retrieved (or immediately if cached)
     * @param reload whether to reload themes if they're cached.
     */
    public void fetch(OptionsFetchedListener<T> callback, boolean reload) {
        if (mOptions == null || reload) {
            mOptions = new ArrayList<>();
            loadOptions();
        }

        if(callback != null) {
            callback.onOptionsLoaded(mOptions);
        }
    }

    protected abstract void loadOptions();

    protected Resources getOverlayResources(String overlayPackage) throws NameNotFoundException {
        return mContext.getPackageManager().getResourcesForApplication(overlayPackage);
    }
}
