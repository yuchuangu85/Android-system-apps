/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.users;

import android.app.ActivityManager;
import android.app.Service;
import android.app.UserSwitchObserver;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import com.android.internal.widget.LockPatternUtils;

public class UserSwitchListenerService extends Service {

    private static final boolean DEBUG = false;
    private static final String TAG = "RestrictedProfile";

    private static final String RESTRICTED_PROFILE_LAUNCHER_ENTRY_ACTIVITY =
            "com.android.tv.settings.users.RestrictedProfileActivityLauncherEntry";
    private static final String SHARED_PREFERENCES_NAME = "RestrictedProfileSharedPreferences";
    private static final String
            ON_BOOT_USER_ID_PREFERENCE = "UserSwitchOnBootBroadcastReceiver.userId";

    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, Intent intent) {

            boolean isSystemUser = UserManager.get(context).isSystemUser();

            if (isSystemUser) {
                context.startService(new Intent(context, UserSwitchListenerService.class));
                if (DEBUG) {
                    Log.d(TAG, "boot completed, user is " + UserHandle.myUserId()
                            + " boot user id: " + getBootUser(context));
                }
            }
        }
    }

    private BroadcastReceiver mUserUnlockedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switchToLastUserIfUnlocked();
        }
    };

    public static void updateLaunchPoint(Context context, boolean enableLaunchPoint) {
        if (DEBUG) {
            Log.d(TAG, "updating launch point: " + enableLaunchPoint);
        }

        PackageManager pm = context.getPackageManager();
        ComponentName compName = new ComponentName(context,
                RESTRICTED_PROFILE_LAUNCHER_ENTRY_ACTIVITY);
        pm.setComponentEnabledSetting(compName,
                enableLaunchPoint ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    static void setBootUser(Context context, int userId) {
        SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_PREFERENCES_NAME,
                Context.MODE_PRIVATE).edit();
        editor.putInt(ON_BOOT_USER_ID_PREFERENCE, userId);
        editor.apply();
    }

    static int getBootUser(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME,
                Context.MODE_PRIVATE);
        return prefs.getInt(ON_BOOT_USER_ID_PREFERENCE, UserHandle.USER_SYSTEM);
    }

    private static void switchUserNow(int userId) {
        try {
            ActivityManager.getService().switchUser(userId);
        } catch (RemoteException re) {
            Log.e(TAG, "Caught exception while switching user! ", re);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            ActivityManager.getService().registerUserSwitchObserver(
                    new UserSwitchObserver() {
                        @Override
                        public void onUserSwitchComplete(int newUserId) throws RemoteException {
                            if (DEBUG) {
                                Log.d(TAG, "user has been foregrounded: " + newUserId);
                            }
                            setBootUser(UserSwitchListenerService.this, newUserId);
                        }
                    }, UserSwitchListenerService.class.getName());
        } catch (RemoteException e) {
        }

        registerReceiver(mUserUnlockedReceiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        switchToLastUserIfUnlocked();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mUserUnlockedReceiver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void switchToLastUserIfUnlocked() {
        if (!UserManager.get(this).isUserUnlocked()) return;

        boolean isFbeEnabled = LockPatternUtils.isFileEncryptionEnabled();
        boolean hasLockscreenSecurity = new LockPatternUtils(this).isSecure(getUserId());

        // If the device is FBE-enabled and has a lockscreen password, the user is asked
        // to enter the PIN on boot so there is no need to switch back to the last user.
        if (!isFbeEnabled || !hasLockscreenSecurity) {
            int bootUserId = getBootUser(this);
            if (UserHandle.myUserId() != bootUserId) {
                switchUserNow(bootUserId);
            }
        }

        updateLaunchPoint(this, new RestrictedProfileModel(this).getUser() != null);
    }
}
