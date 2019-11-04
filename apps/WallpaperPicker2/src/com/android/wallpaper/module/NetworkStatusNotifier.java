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
package com.android.wallpaper.module;

import androidx.annotation.IntDef;

/**
 * Provides current status of the network. Abstraction layer above Android's ConnectivityManager
 * used for testability.
 */
public interface NetworkStatusNotifier {
    static int NETWORK_NOT_CONNECTED = 0;
    static int NETWORK_CONNECTED = 1;

    @NetworkStatus
    int getNetworkStatus();

    /**
     * Registers a listener on network changes and immediately calls {@code onNetworkChanged} on it
     * with the current network status.
     */
    void registerListener(Listener listener);

    void unregisterListener(Listener listener);

    /**
     * Possible network statuses .
     */
    @IntDef({
            NETWORK_NOT_CONNECTED,
            NETWORK_CONNECTED
    })
    @interface NetworkStatus {
    }

    /**
     * Listener for network status change notification.
     */
    interface Listener {
        void onNetworkChanged(@NetworkStatus int newNetworkStatus);
    }
}
