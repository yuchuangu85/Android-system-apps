/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.picker;

/**
 * Interface for activities that launch an Android custom image picker.
 */
public interface MyPhotosStarter {

    /**
     * Requests that this Activity show the Android custom photo picker for the sake of picking a
     * photo to set as the device's wallpaper.
     */
    void requestCustomPhotoPicker(PermissionChangedListener listener);

    /**
     * Interface for clients to implement in order to be notified of permissions grant status changes.
     */
    interface PermissionChangedListener {
        /**
         * Notifies that the user granted permissions.
         */
        void onPermissionsGranted();

        /**
         * Notifies that the user denied permissions.
         *
         * @param dontAskAgain True if user checked "Don't ask again" on the most recent permissions
         *                     request prior to denying it.
         */
        void onPermissionsDenied(boolean dontAskAgain);
    }

    interface MyPhotosStarterProvider {

        MyPhotosStarter getMyPhotosStarter();
    }
}
