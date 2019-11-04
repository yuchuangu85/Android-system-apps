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
import android.car.trust.ICarTrustAgentBleCallback;
import android.car.trust.ICarTrustAgentEnrollmentCallback;
import android.car.trust.TrustedDeviceInfo;

/**
 * Binder interface for CarTrustAgentEnrollmentService. The service implements the functionality
 * to communicate with the remote device securely to enroll the remote device as a trusted device.
 *
 * @hide
 */
interface ICarTrustAgentEnrollment {
    void startEnrollmentAdvertising();
    void stopEnrollmentAdvertising();
    void enrollmentHandshakeAccepted(in BluetoothDevice device);
    void terminateEnrollmentHandshake();
    boolean isEscrowTokenActive(in long handle, int uid);
    void removeEscrowToken(in long handle, int uid);
    void removeAllTrustedDevices(int uid);
    void setTrustedDeviceEnrollmentEnabled(boolean enable);
    void setTrustedDeviceUnlockEnabled(boolean enable);
    List<TrustedDeviceInfo> getEnrolledDeviceInfosForUser(in int uid);
    void registerEnrollmentCallback(in ICarTrustAgentEnrollmentCallback callback);
    void unregisterEnrollmentCallback(in ICarTrustAgentEnrollmentCallback callback);
    void registerBleCallback(in ICarTrustAgentBleCallback callback);
    void unregisterBleCallback(in ICarTrustAgentBleCallback callback);
}
