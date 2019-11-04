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

package android.car;

import android.car.ICarBugreportCallback;

/**
 * Binder interface for {@link android.car.CarBugreportManager}.
 *
 * @hide
 */
 interface ICarBugreportService {

    /**
     * Starts bugreport service to capture a zipped bugreport. The caller needs to provide
     * two file descriptors. The "output" file descriptor will be used to provide the actual
     * zip file. The "extra_output" file descriptor will be provided to add files that does not
     * exist in the original file.
     * The file descriptor is written by the service and will be read by the client.
     */
    void requestBugreport(in ParcelFileDescriptor output,
            in ParcelFileDescriptor extraOutput, ICarBugreportCallback callback) = 1;
 }
