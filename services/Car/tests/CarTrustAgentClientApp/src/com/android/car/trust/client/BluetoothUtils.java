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
package com.android.car.trust.client;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.UUID;

/**
 * A utility class holding methods related to Bluetooth.
 */
public class BluetoothUtils {
    private BluetoothUtils() {}

    /**
     * Returns a characteristic off the given {@link BluetoothGattService} mapped to the jUUID
     * specified. If the given service has multiple characteristics of the same UUID, then the
     * first instance is returned.
     *
     * @param  uuidRes The unique identifier for the characteristic.
     * @param  service The {@link BluetoothGattService} that contains the characteristic.
     * @param  context The current {@link Context}.
     * @return A {@link BluetoothGattCharacteristic} with a UUID matching {@code uuidRes} or
     * {@code null} if none exists.
     *
     * @see BluetoothGattService#getCharacteristic(UUID)
     */
    @Nullable
    public static BluetoothGattCharacteristic getCharacteristic(@StringRes int uuidRes,
            BluetoothGattService service, Context context) {
        return service.getCharacteristic(UUID.fromString(context.getString(uuidRes)));
    }
}
