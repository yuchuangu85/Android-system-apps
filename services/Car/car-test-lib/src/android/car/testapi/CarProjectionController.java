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

package android.car.testapi;

import android.car.CarProjectionManager;
import android.car.projection.ProjectionOptions;
import android.net.wifi.WifiConfiguration;

/** Controller to change behavior of {@link CarProjectionManager} */
public interface CarProjectionController {
    /** Set WifiConfiguration for wireless projection or null to simulate failure to start AP */
    void setWifiConfiguration(WifiConfiguration wifiConfiguration);

    /**
     * Sets {@link ProjectionOptions} object returns by
     * {@link CarProjectionManager#getProjectionOptions()} call
     */
    void setProjectionOptions(ProjectionOptions projectionOptions);

    /**
     * Fire a projection event to be received by registered
     * {@link CarProjectionManager.ProjectionKeyEventHandler}s.
     */
    void fireKeyEvent(@CarProjectionManager.KeyEventNum int event);
}
