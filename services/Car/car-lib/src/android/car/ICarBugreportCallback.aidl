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

/**
  * Callback for carbugreport service
  * {@hide}
  */
oneway interface ICarBugreportCallback {

    /**
     * Called on an error condition. The error codes are defined as
     * {@link android.car.CarBugreportManager.CarBugreportManagerCallback.CarBugreportErrorCode}
     */
    void onError(int errorCode);

    /**
     * Called when the bugreport progress changes. Progress value is a number between 0.0 and 100.0.
     *
     * <p>Never called after {@link #onError()} or {@link onFinished()}.
     */
    void onProgress(float progress);

    /**
     * Called when taking bugreport finishes successfully.
     */
    void onFinished();

}
