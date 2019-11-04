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
package com.android.wallpaper.module;

import androidx.annotation.IntDef;


/**
 * Provides updates on the status of packages (installed, updated, removed).
 * Abstraction layer above Android's LauncherApps.
 */
public interface PackageStatusNotifier {

    /**
     * Possible app statuses.
     */
    @IntDef({
            PackageStatus.ADDED,
            PackageStatus.CHANGED,
            PackageStatus.REMOVED
    })
    @interface PackageStatus {
        int ADDED = 1;
        int CHANGED = 2;
        int REMOVED = 3;
    }

    /**
     * Interface to be notified when there's a package event.
     */
    interface Listener {
        /**
         * Called when a package status' change.
         * @param packageName name of the package that changed
         * @param status the new {@link PackageStatus} for that package
         */
        void onPackageChanged(String packageName, @PackageStatus int status);
    }

    /**
     * Add a {@link Listener} to be notified of package events. Only packages that declare an
     * Activity or Service responding to that Intent Action will trigger the Listener's callback,
     * except for the case of PackageStatus#REMOVED which will be triggered for any removed package
     * (since it will trigger after the package has been already removed)
     * @param listener Callback to be notified of changes
     * @param action Intent action to filter packages to be notified about
     *              (except for REMOVED status)
     */
    void addListener(Listener listener, String action);

    /**
     * Unregister the given listener.
     */
    void removeListener(Listener listener);
}
