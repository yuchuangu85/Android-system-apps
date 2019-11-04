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
package com.android.wallpaper.module;

import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.UserHandle;

import java.util.HashMap;
import java.util.Map;

/**
 * Default version of {@link PackageStatusNotifier} that uses {@link LauncherApps}
 */
public class DefaultPackageStatusNotifier implements PackageStatusNotifier {

    private final Map<Listener, ListenerWrapper> mListeners = new HashMap<>();
    private final Context mAppContext;
    private final LauncherApps mLauncherApps;


    public DefaultPackageStatusNotifier(Context context) {
        mAppContext = context.getApplicationContext();
        mLauncherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
    }

    @Override
    public void addListener(Listener listener, String action) {
        ListenerWrapper wrapper = new ListenerWrapper(mAppContext, action, listener);
        removeListenerAndMaybeUnregisterCallback(listener);
        mListeners.put(listener, wrapper);
        mLauncherApps.registerCallback(wrapper);
    }

    @Override
    public void removeListener(Listener listener) {
        removeListenerAndMaybeUnregisterCallback(listener);
    }

    private void removeListenerAndMaybeUnregisterCallback(Listener listener) {
        ListenerWrapper oldWrapper = mListeners.remove(listener);
        if (oldWrapper != null) {
            mLauncherApps.unregisterCallback(oldWrapper);
        }
    }

    private static class ListenerWrapper extends LauncherApps.Callback {

        private final Context mAppContext;
        private final Intent mIntentFilter;
        private final Listener mListener;

        ListenerWrapper(Context context, String action, Listener listener) {
            mAppContext = context.getApplicationContext();
            mIntentFilter = new Intent(action);
            mListener = listener;
        }

        private boolean isValidPackage(String packageName) {
            mIntentFilter.setPackage(packageName);
            PackageManager pm = mAppContext.getPackageManager();
            return !pm.queryIntentServices(mIntentFilter, 0).isEmpty()
                    || !pm.queryIntentActivities(mIntentFilter, 0).isEmpty();
        }


        @Override
        public void onPackageRemoved(String packageName, UserHandle userHandle) {
            // We can't check if the removed package is "valid" for the given action, as it's not
            // there any more, so trigger REMOVED for all cases.
            mListener.onPackageChanged(packageName, PackageStatus.REMOVED);
        }

        @Override
        public void onPackageAdded(String packageName, UserHandle userHandle) {
            if (isValidPackage(packageName)) {
                mListener.onPackageChanged(packageName, PackageStatus.ADDED);
            }
        }

        @Override
        public void onPackageChanged(String packageName, UserHandle userHandle) {
            if (isValidPackage(packageName)) {
                mListener.onPackageChanged(packageName, PackageStatus.CHANGED);
            }
        }

        @Override
        public void onPackagesAvailable(String[] packageNames, UserHandle userHandle,
                                        boolean replacing) {
            for (String packageName : packageNames) {
                if (isValidPackage(packageName)) {
                    mListener.onPackageChanged(packageName,
                            replacing ? PackageStatus.CHANGED : PackageStatus.ADDED);
                }
            }
        }

        @Override
        public void onPackagesUnavailable(String[] packageNames, UserHandle userHandle,
                                          boolean replacing) {
            for (String packageName : packageNames) {
                if (!replacing && isValidPackage(packageName)) {
                    mListener.onPackageChanged(packageName, PackageStatus.REMOVED);
                }
            }
        }

        @Override
        public void onPackagesSuspended(String[] packageNames, UserHandle user) {
            for (String packageName : packageNames) {
                if (isValidPackage(packageName)) {
                    mListener.onPackageChanged(packageName, PackageStatus.REMOVED);
                }
            }
        }

        @Override
        public void onPackagesUnsuspended(String[] packageNames, UserHandle user) {
            for (String packageName : packageNames) {
                if (isValidPackage(packageName)) {
                    mListener.onPackageChanged(packageName, PackageStatus.ADDED);
                }
            }
        }
    }
}
