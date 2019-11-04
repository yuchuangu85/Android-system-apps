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

package android.car.trust;

import android.bluetooth.BluetoothDevice;

/**
 * Callback interface for state changes during Trusted device enrollment.
 *
 * @hide
 */
oneway interface ICarTrustAgentEnrollmentCallback {
    /**
     * Communicate about failure/timeouts in the handshake process.
     */
    void onEnrollmentHandshakeFailure(in BluetoothDevice device, in int errorCode);

    /**
     * Present the pairing/authentication string to the user.
     */
    void onAuthStringAvailable(in BluetoothDevice device, in String authString);

    /**
     * Escrow token was received and the Trust Agent framework has generated a corresponding handle.
     */
    void onEscrowTokenAdded(in long handle);

    /**
     * Escrow token was removed as a result of a call to
     * {@link CarTrustAgentEnrollmentManager#removeEscrowToken(long handle, int uid)}. The peer
     * device associated with this token is not trusted for authentication anymore.
     */
      void onEscrowTokenRemoved(in long handle);

    /**
     * Escrow token's active state changed.
     */
    void onEscrowTokenActiveStateChanged(in long handle, in boolean active);

}
