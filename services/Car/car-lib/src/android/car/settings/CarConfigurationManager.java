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

package android.car.settings;

import android.car.CarManagerBase;
import android.os.IBinder;
import android.os.RemoteException;

/**
 * Manager that exposes car configuration values that are stored on the system.
 */
public class CarConfigurationManager implements CarManagerBase {
    private static final String TAG = "CarConfigurationManager";

    private final ICarConfigurationManager mConfigurationService;

    /** @hide */
    public CarConfigurationManager(IBinder service) {
        mConfigurationService = ICarConfigurationManager.Stub.asInterface(service);
    }

    /**
     * Returns a configuration for Speed Bump that will determine when it kicks in.
     *
     * @return A {@link SpeedBumpConfiguration} that contains the configuration values.
     */
    public SpeedBumpConfiguration getSpeedBumpConfiguration() {
        try {
            return mConfigurationService.getSpeedBumpConfiguration();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        // Nothing to release.
    }
}
