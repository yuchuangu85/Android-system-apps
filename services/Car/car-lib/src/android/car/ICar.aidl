/*
 * Copyright (C) 2015 The Android Open Source Project
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

/** @hide */
interface ICar {
    // All oneway methods are called from system server and should be placed in top positions.
    // Do not change the order of oneway methods as system server make binder call based on this
    // order.

    /**
     * IBinder is ICarServiceHelper but passed as IBinder due to aidl hidden.
     *
     * This should be the 1st method. Do not change the order.
     */
    oneway void setCarServiceHelper(in IBinder helper) = 0;
    /**
     * Notify lock / unlock of user id to car service.
     * unlocked: 1 if unlocked 0 otherwise.
     *
     * This should be the 2nd method. Do not change the order.
     */
    oneway void setUserLockStatus(in int userHandle, in int unlocked) = 1;

    /**
     * Notify of user switching.  This is called only for foreground users when the user is starting
     * to boot.
     *
     * @param userHandle -  user handle of new user.
     *
     * This should be the 3rd method. Do not change the order.
     */
    oneway void onSwitchUser(in int userHandle) = 2;

    IBinder getCarService(in String serviceName) = 3;
    int getCarConnectionType() = 4;

}
