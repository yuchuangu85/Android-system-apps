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
package android.car.cluster;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityManager.StackInfo;
import android.app.IActivityManager;
import android.app.IProcessObserver;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Top activity monitor, allows listeners to be notified when a new activity comes to the foreground
 * on a particular device.
 */
public class ActivityMonitor {
    private static final String TAG = "Cluster.ActivityMonitor";

    /**
     * Listener of activity changes
     */
    public interface ActivityListener {
        /**
         * Invoked when a new activity becomes the top activity on a particular display.
         */
        void onTopActivityChanged(int displayId, @Nullable ComponentName activity);
    }

    private IActivityManager mActivityManager;
    // Listeners of top activity changes, indexed by the displayId they are interested on.
    private final Map<Integer, Set<ActivityListener>> mListeners = new HashMap<>();
    private final Handler mHandler = new Handler();
    private final IProcessObserver.Stub mProcessObserver = new IProcessObserver.Stub() {
        @Override
        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
            notifyTopActivities();
        }

        @Override
        public void onForegroundServicesChanged(int pid, int uid, int serviceTypes) { }

        @Override
        public void onProcessDied(int pid, int uid) {
            notifyTopActivities();
        }
    };
    private final TaskStackListener mTaskStackListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            Log.i(TAG, "onTaskStackChanged");
            notifyTopActivities();
        }
    };

    /**
     * Registers a new listener to receive activity updates on a particular display
     *
     * @param displayId identifier of the display to monitor
     * @param listener listener to be notified
     */
    public void addListener(int displayId, ActivityListener listener) {
        mListeners.computeIfAbsent(displayId, k -> new HashSet<>()).add(listener);
    }

    /**
     * Unregisters a listener previously registered with {@link #addListener(int, ActivityListener)}
     */
    public void removeListener(int displayId, ActivityListener listener) {
        mListeners.computeIfAbsent(displayId, k -> new HashSet<>()).remove(listener);
    }

    /**
     * Starts monitoring activity changes. {@link #stop()} should be invoked to release resources.
     */
    public void start() {
        mActivityManager = ActivityManager.getService();
        // Monitoring both listeners are necessary as there are cases where one listener cannot
        // monitor activity change.
        try {
            mActivityManager.registerProcessObserver(mProcessObserver);
            mActivityManager.registerTaskStackListener(mTaskStackListener);
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot register activity monitoring", e);
            throw new RuntimeException(e);
        }
        notifyTopActivities();
    }

    /**
     * Stops monitoring activity changes. Should be invoked when this monitor is not longer used.
     */
    public void stop() {
        if (mActivityManager == null) {
            return;
        }
        try {
            mActivityManager.unregisterProcessObserver(mProcessObserver);
            mActivityManager.unregisterTaskStackListener(mTaskStackListener);
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot unregister activity monitoring. Ignoring", e);
        }
        mActivityManager = null;
    }

    /**
     * Notifies listeners on changes of top activities. {@link ActivityManager} might trigger
     * updates on threads different than UI.
     */
    private void notifyTopActivities() {
        mHandler.post(() -> {
            try {
                List<StackInfo> infos = mActivityManager.getAllStackInfos();
                for (StackInfo info : infos) {
                    Set<ActivityListener> listeners = mListeners.get(info.displayId);
                    if (listeners != null && !listeners.isEmpty()) {
                        for (ActivityListener listener : listeners) {
                            listener.onTopActivityChanged(info.displayId, info.topActivity);
                        }
                    }
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot getTasks", e);
            }
        });
    }
}
