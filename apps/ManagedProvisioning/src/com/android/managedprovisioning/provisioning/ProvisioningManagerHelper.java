/*
 * Copyright 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.provisioning;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Pair;

import com.android.internal.annotations.GuardedBy;
import com.android.managedprovisioning.common.Globals;
import com.android.managedprovisioning.common.ProvisionLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for ProvisioningManager.
 */
// TODO(b/123288153): Rearrange provisioning activity, manager, controller classes.
public class ProvisioningManagerHelper {

    private static final int CALLBACK_NONE = 0;
    private static final int CALLBACK_ERROR = 1;
    private static final int CALLBACK_PRE_FINALIZED = 2;

    private static final Intent SERVICE_INTENT = new Intent().setComponent(new ComponentName(
            Globals.MANAGED_PROVISIONING_PACKAGE_NAME,
            ProvisioningService.class.getName()));

    private final Context mContext;
    private final Handler mUiHandler;

    @GuardedBy("this")
    private List<ProvisioningManagerCallback> mCallbacks = new ArrayList<>();

    private int mLastCallback = CALLBACK_NONE;
    private Pair<Pair<Integer, Integer>, Boolean> mLastError; // TODO: refactor
    private HandlerThread mHandlerThread;

    public ProvisioningManagerHelper(Context context) {
        mContext = context;
        mUiHandler = new Handler(Looper.getMainLooper());
    }

    public void startNewProvisioningLocked(AbstractProvisioningController controller) {
        if (mHandlerThread == null) {
            mHandlerThread = new HandlerThread(
                    String.format("%s Worker", controller.getClass().getName()));
            mHandlerThread.start();
            mContext.startService(SERVICE_INTENT);
        }
        mLastCallback = CALLBACK_NONE;
        mLastError = null;

        controller.start(mHandlerThread.getLooper());
    }

    public void registerListener(ProvisioningManagerCallback callback) {
        synchronized (this) {
            mCallbacks.add(callback);
            callLastCallbackLocked(callback);
        }
    }

    public void unregisterListener(ProvisioningManagerCallback callback) {
        synchronized (this) {
            mCallbacks.remove(callback);
        }
    }

    public void error(int titleId, int messageId, boolean factoryResetRequired) {
        synchronized (this) {
            for (ProvisioningManagerCallback callback : mCallbacks) {
                postCallbackToUiHandler(callback, () -> {
                    callback.error(titleId, messageId, factoryResetRequired);
                });
            }
            mLastCallback = CALLBACK_ERROR;
            mLastError = Pair.create(Pair.create(titleId, messageId), factoryResetRequired);
        }
    }

    private void callLastCallbackLocked(ProvisioningManagerCallback callback) {
        switch (mLastCallback) {
            case CALLBACK_ERROR:
                final Pair<Pair<Integer, Integer>, Boolean> error = mLastError;
                postCallbackToUiHandler(callback, () -> {
                    callback.error(error.first.first, error.first.second, error.second);
                });
                break;
            case CALLBACK_PRE_FINALIZED:
                postCallbackToUiHandler(callback, callback::preFinalizationCompleted);
                break;
            default:
                ProvisionLogger.logd("No previous callback");
        }
    }

    public boolean cancelProvisioning(AbstractProvisioningController controller) {
        synchronized (this) {
            if (controller != null) {
                controller.cancel();
                return true;
            } else {
                ProvisionLogger.loge("Trying to cancel provisioning, but controller is null");
                return false;
            }
        }
    }

    public void notifyPreFinalizationCompleted() {
        synchronized (this) {
            for (ProvisioningManagerCallback callback : mCallbacks) {
                postCallbackToUiHandler(callback, callback::preFinalizationCompleted);
            }
            mLastCallback = CALLBACK_PRE_FINALIZED;
        }
    }

    public void clearResourcesLocked() {
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread = null;
            mContext.stopService(SERVICE_INTENT);
        }
    }

    /**
     * Executes the callback method on the main thread.
     *
     * <p>Inside the main thread, we have to first verify the callback is still present on the
     * callbacks list. This is because when a config change happens (e.g. a different locale was
     * specified), {@link ProvisioningActivity} is recreated and the old
     * {@link ProvisioningActivity} instance is left in a bad state. Any callbacks posted before
     * this happens will still be executed. Fixes b/131719633.
     */
    private void postCallbackToUiHandler(ProvisioningManagerCallback callback,
            Runnable callbackRunnable) {
        mUiHandler.post(() -> {
            synchronized (ProvisioningManagerHelper.this) {
                if (isCallbackStillRequired(callback)) {
                    callbackRunnable.run();
                }
            }
        });
    }

    private boolean isCallbackStillRequired(ProvisioningManagerCallback callback) {
        return mCallbacks.contains(callback);
    }
}
