/**
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

package com.android.car.radio.util;

import android.os.RemoteException;

/**
 * Helper functions to assist remote calls.
 */
public abstract class Remote {
    private static final String TAG = "BcRadioApp.Remote";

    /**
     * Throwing void function.
     */
    public interface RemoteVoidFunction {
        /**
         * The actual throwing function.
         */
        void call() throws RemoteException;
    }

    /**
     * Throwing function that returns some value.
     *
     * @param <V> Return type for the function.
     */
    public interface RemoteFunction<V> {
        /**
         * The actual throwing function.
         */
        V call() throws RemoteException;
    }

    /**
     * Wraps remote function and rethrows {@link RemoteException}.
     */
    public static <V> V exec(RemoteFunction<V> func) {
        try {
            return func.call();
        } catch (RemoteException e) {
            throw new RuntimeException("Failed to execute remote call", e);
        }
    }

    /**
     * Wraps remote void function and rethrows {@link RemoteException}.
     */
    public static void exec(RemoteVoidFunction func) {
        try {
            func.call();
        } catch (RemoteException e) {
            throw new RuntimeException("Failed to execute remote call", e);
        }
    }

    /**
     * Wraps remote function and logs in case of {@link RemoteException}.
     */
    public static <V> void tryExec(RemoteFunction<V> func) {
        try {
            func.call();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to execute remote call", e);
        }
    }

    /**
     * Wraps remote void function and logs in case of {@link RemoteException}.
     */
    public static void tryExec(RemoteVoidFunction func) {
        try {
            func.call();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to execute remote call", e);
        }
    }
}
