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

package com.android.car.dialer.storage;

import androidx.annotation.NonNull;

/**
 * A wrapper which is used by {@link androidx.room.TypeConverter} to encrypt and decrypt. By using
 * this wrapper, we can use {@link androidx.room.TypeConverter} to do encryption before writing and
 * do decryption after reading.
 */
class CipherWrapper<T> {
    private final T mObject;

    CipherWrapper(@NonNull T object) {
        mObject = object;
    }

    @NonNull
    T get() {
        return mObject;
    }
}
