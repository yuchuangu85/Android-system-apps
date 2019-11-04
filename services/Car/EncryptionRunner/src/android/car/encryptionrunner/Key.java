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

package android.car.encryptionrunner;

import android.annotation.NonNull;

import java.security.SignatureException;

/**
 * Represents a serializable encryption key.
 */
public interface Key {
    /**
     * Returns a serialized encryption key.
     */
    @NonNull byte[] asBytes();

    /**
     * Encrypts data using this key.
     *
     * @param data the data to be encrypted
     * @return the encrypted data.
     */
    byte[] encryptData(@NonNull byte[] data);

    /**
     * Decrypts data using this key.
     *
     * @param encryptedData The encrypted data.
     * @return decrypted data.
     *
     * @throws SignatureException if encrypted data is not properly signed.
     */
    byte[] decryptData(@NonNull byte[] encryptedData) throws SignatureException;
}
