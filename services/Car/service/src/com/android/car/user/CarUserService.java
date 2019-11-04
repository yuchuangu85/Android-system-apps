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

package com.android.car.user;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.IActivityManager;
import android.car.settings.CarSettings;
import android.car.userlib.CarUserManagerHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

import com.android.car.CarServiceBase;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User service for cars. Manages users at boot time. Including:
 *
 * <ol>
 *   <li> Creates a secondary admin user on first run.
 *   <li> Log in to the last active user.
 * <ol/>
 */
public class CarUserService extends BroadcastReceiver implements CarServiceBase {
    private static final String TAG = "CarUserService";
    private final Context mContext;
    private final CarUserManagerHelper mCarUserManagerHelper;
    private final IActivityManager mAm;

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private boolean mUser0Unlocked;
    @GuardedBy("mLock")
    private final ArrayList<Runnable> mUser0UnlockTasks = new ArrayList<>();
    /**
     * Background users that will be restarted in garage mode. This list can include the
     * current foreground user bit the current foreground user should not be restarted.
     */
    @GuardedBy("mLock")
    private final ArrayList<Integer> mBackgroundUsersToRestart = new ArrayList<>();
    /**
     * Keep the list of background users started here. This is wholly for debugging purpose.
     */
    @GuardedBy("mLock")
    private final ArrayList<Integer> mBackgroundUsersRestartedHere = new ArrayList<>();

    private final int mMaxRunningUsers;

    private final UserManager mUserManager;


    private final CopyOnWriteArrayList<UserCallback> mUserCallbacks = new CopyOnWriteArrayList<>();

    /** Interface for callbacks related to user activities. */
    public interface UserCallback {
        /** Gets called when user lock status has been changed. */
        void onUserLockChanged(int userId, boolean unlocked);
        /** Called when new foreground user started to boot. */
        void onSwitchUser(int userId);
    }

    public CarUserService(
                @Nullable Context context, @Nullable CarUserManagerHelper carUserManagerHelper,
                IActivityManager am, int maxRunningUsers) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "constructed");
        }
        mContext = context;
        mCarUserManagerHelper = carUserManagerHelper;
        mAm = am;
        mMaxRunningUsers = maxRunningUsers;
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
    }

    @Override
    public void init() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "init");
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_SWITCHED);

        mContext.registerReceiver(this, filter);
    }

    @Override
    public void release() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "release");
        }
        mContext.unregisterReceiver(this);
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println(TAG);
        boolean user0Unlocked;
        ArrayList<Integer> backgroundUsersToRestart;
        ArrayList<Integer> backgroundUsersRestarted;
        synchronized (mLock) {
            user0Unlocked = mUser0Unlocked;
            backgroundUsersToRestart = new ArrayList<>(mBackgroundUsersToRestart);
            backgroundUsersRestarted = new ArrayList<>(mBackgroundUsersRestartedHere);

        }
        writer.println("User0Unlocked: " + user0Unlocked);
        writer.println("maxRunningUsers:" + mMaxRunningUsers);
        writer.println("BackgroundUsersToRestart:" + backgroundUsersToRestart);
        writer.println("BackgroundUsersRestarted:" + backgroundUsersRestarted);
    }

    private void updateDefaultUserRestriction() {
        // We want to set restrictions on system and guest users only once. These are persisted
        // onto disk, so it's sufficient to do it once + we minimize the number of disk writes.
        if (Settings.Global.getInt(mContext.getContentResolver(),
                CarSettings.Global.DEFAULT_USER_RESTRICTIONS_SET, /* default= */ 0) == 0) {
            // Only apply the system user restrictions if the system user is headless.
            if (mCarUserManagerHelper.isHeadlessSystemUser()) {
                setSystemUserRestrictions();
            }
            mCarUserManagerHelper.initDefaultGuestRestrictions();
            Settings.Global.putInt(mContext.getContentResolver(),
                    CarSettings.Global.DEFAULT_USER_RESTRICTIONS_SET, 1);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onReceive " + intent);
        }

        if (Intent.ACTION_USER_SWITCHED.equals(intent.getAction())) {
            // Update last active user if the switched-to user is a persistent, non-system user.
            final int currentUser = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
            if (currentUser > UserHandle.USER_SYSTEM
                        && mCarUserManagerHelper.isPersistentUser(currentUser)) {
                mCarUserManagerHelper.setLastActiveUser(currentUser);
            }
        }
    }

    /** Add callback to listen to user activity events. */
    public void addUserCallback(UserCallback callback) {
        mUserCallbacks.add(callback);
    }

    /** Removes previously added callback to listen user events. */
    public void removeUserCallback(UserCallback callback) {
        mUserCallbacks.remove(callback);
    }

    /**
     * Set user lock / unlocking status. This is coming from system server through ICar binder call.
     * @param userHandle Handle of user
     * @param unlocked unlocked (=true) or locked (=false)
     */
    public void setUserLockStatus(int userHandle, boolean unlocked) {
        for (UserCallback callback : mUserCallbacks) {
            callback.onUserLockChanged(userHandle, unlocked);
        }
        if (!unlocked) { // nothing else to do when it is locked back.
            return;
        }
        ArrayList<Runnable> tasks = null;
        synchronized (mLock) {
            if (userHandle == UserHandle.USER_SYSTEM) {
                if (!mUser0Unlocked) { // user 0, unlocked, do this only once
                    updateDefaultUserRestriction();
                    tasks = new ArrayList<>(mUser0UnlockTasks);
                    mUser0UnlockTasks.clear();
                    mUser0Unlocked = unlocked;
                }
            } else { // none user0
                Integer user = userHandle;
                if (mCarUserManagerHelper.isPersistentUser(userHandle)) {
                    // current foreground user should stay in top priority.
                    if (userHandle == mCarUserManagerHelper.getCurrentForegroundUserId()) {
                        mBackgroundUsersToRestart.remove(user);
                        mBackgroundUsersToRestart.add(0, user);
                    }
                    // -1 for user 0
                    if (mBackgroundUsersToRestart.size() > (mMaxRunningUsers - 1)) {
                        final int userToDrop = mBackgroundUsersToRestart.get(
                                mBackgroundUsersToRestart.size() - 1);
                        Log.i(TAG, "New user unlocked:" + userHandle
                                + ", dropping least recently user from restart list:" + userToDrop);
                        // Drop the least recently used user.
                        mBackgroundUsersToRestart.remove(mBackgroundUsersToRestart.size() - 1);
                    }
                }
            }
        }
        if (tasks != null && tasks.size() > 0) {
            Log.d(TAG, "User0 unlocked, run queued tasks:" + tasks.size());
            for (Runnable r : tasks) {
                r.run();
            }
        }
    }

    /**
     * Start all background users that were active in system.
     * @return list of background users started successfully.
     */
    public ArrayList<Integer> startAllBackgroundUsers() {
        ArrayList<Integer> users;
        synchronized (mLock) {
            users = new ArrayList<>(mBackgroundUsersToRestart);
            mBackgroundUsersRestartedHere.clear();
            mBackgroundUsersRestartedHere.addAll(mBackgroundUsersToRestart);
        }
        ArrayList<Integer> startedUsers = new ArrayList<>();
        for (Integer user : users) {
            if (user == mCarUserManagerHelper.getCurrentForegroundUserId()) {
                continue;
            }
            try {
                if (mAm.startUserInBackground(user)) {
                    if (mUserManager.isUserUnlockingOrUnlocked(user)) {
                        // already unlocked / unlocking. No need to unlock.
                        startedUsers.add(user);
                    } else if (mAm.unlockUser(user, null, null, null)) {
                        startedUsers.add(user);
                    } else { // started but cannot unlock
                        Log.w(TAG, "Background user started but cannot be unlocked:" + user);
                        if (mUserManager.isUserRunning(user)) {
                            // add to started list so that it can be stopped later.
                            startedUsers.add(user);
                        }
                    }
                }
            } catch (RemoteException e) {
                // ignore
            }
        }
        // Keep only users that were re-started in mBackgroundUsersRestartedHere
        synchronized (mLock) {
            ArrayList<Integer> usersToRemove = new ArrayList<>();
            for (Integer user : mBackgroundUsersToRestart) {
                if (!startedUsers.contains(user)) {
                    usersToRemove.add(user);
                }
            }
            mBackgroundUsersRestartedHere.removeAll(usersToRemove);
        }
        return startedUsers;
    }

    /**
     * Stop all background users that were active in system.
     * @return true if stopping succeeds.
     */
    public boolean stopBackgroundUser(int userId) {
        if (userId == UserHandle.USER_SYSTEM) {
            return false;
        }
        if (userId == mCarUserManagerHelper.getCurrentForegroundUserId()) {
            Log.i(TAG, "stopBackgroundUser, already a fg user:" + userId);
            return false;
        }
        try {
            int r = mAm.stopUser(userId, true, null);
            if (r == ActivityManager.USER_OP_SUCCESS) {
                synchronized (mLock) {
                    Integer user = userId;
                    mBackgroundUsersRestartedHere.remove(user);
                }
            } else if (r == ActivityManager.USER_OP_IS_CURRENT) {
                return false;
            } else {
                Log.i(TAG, "stopBackgroundUser failed, user:" + userId + " err:" + r);
                return false;
            }
        } catch (RemoteException e) {
            // ignore
        }
        return true;
    }

    /**
     * Called when new foreground user started to boot.
     *
     * @param userHandle user handle of new user
     */
    public void onSwitchUser(int userHandle) {
        for (UserCallback callback : mUserCallbacks) {
            callback.onSwitchUser(userHandle);
        }
    }

    /**
     * Run give runnable when user 0 is unlocked. If user 0 is already unlocked, it is
     * run inside this call.
     * @param r Runnable to run.
     */
    public void runOnUser0Unlock(Runnable r) {
        boolean runNow = false;
        synchronized (mLock) {
            if (mUser0Unlocked) {
                runNow = true;
            } else {
                mUser0UnlockTasks.add(r);
            }
        }
        if (runNow) {
            r.run();
        }
    }

    @VisibleForTesting
    protected ArrayList<Integer> getBackgroundUsersToRestart() {
        ArrayList<Integer> backgroundUsersToRestart;
        synchronized (mLock) {
            backgroundUsersToRestart = new ArrayList<>(mBackgroundUsersToRestart);
        }
        return backgroundUsersToRestart;
    }

    private void setSystemUserRestrictions() {
        // Disable adding accounts for system user.
        mCarUserManagerHelper.setUserRestriction(mCarUserManagerHelper.getSystemUserInfo(),
                UserManager.DISALLOW_MODIFY_ACCOUNTS, /* enable= */ true);

        // Disable Location service for system user.
        LocationManager locationManager =
                (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        locationManager.setLocationEnabledForUser(
                /* enabled= */ false, UserHandle.of(UserHandle.USER_SYSTEM));
    }
}
