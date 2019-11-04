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
package com.android.car.notification;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * NotificationListenerService that fetches all notifications from system.
 */
public class CarNotificationListener extends NotificationListenerService {
    private static final String TAG = "CarNotificationListener";
    static final String ACTION_LOCAL_BINDING = "local_binding";
    static final int NOTIFY_NOTIFICATION_POSTED = 1;
    static final int NOTIFY_NOTIFICATION_REMOVED = 2;
    /** Temporary {@link Ranking} object that serves as a reused value holder */
    final private Ranking mTemporaryRanking = new Ranking();

    private Handler mHandler;
    private RankingMap mRankingMap;
    private CarHeadsUpNotificationManager mHeadsUpManager;
    private NotificationDataManager mNotificationDataManager;

    /**
     * Map that contains all the active notifications. These notifications may or may not be
     * visible to the user if they get filtered out. The only time these will be removed from the
     * map is when the {@llink NotificationListenerService} calls the onNotificationRemoved method.
     * New notifications will be added to the map from {@link CarHeadsUpNotificationManager}.
     */
    private Map<String, StatusBarNotification> mActiveNotifications = new HashMap<>();

    /**
     * Call this if to register this service as a system service and connect to HUN. This is useful
     * if the notification service is being used as a lib instead of a standalone app. The
     * standalone app version has a manifest entry that will have the same effect.
     * @param context Context required for registering the service.
     * @param carUxRestrictionManagerWrapper will have the heads up manager registered with it.
     * @param carHeadsUpNotificationManager HUN controller.
     * @param notificationDataManager used for keeping track of additional notification states.
     */
    public void registerAsSystemService(Context context,
            CarUxRestrictionManagerWrapper carUxRestrictionManagerWrapper,
            CarHeadsUpNotificationManager carHeadsUpNotificationManager,
            NotificationDataManager notificationDataManager) {
        try {
        mNotificationDataManager = notificationDataManager;
            registerAsSystemService(context,
                    new ComponentName(context.getPackageName(), getClass().getCanonicalName()),
                    ActivityManager.getCurrentUser());
            mHeadsUpManager = carHeadsUpNotificationManager;
            carUxRestrictionManagerWrapper.setCarHeadsUpNotificationManager(carHeadsUpNotificationManager);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to register notification listener", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationDataManager = new NotificationDataManager();
        NotificationApplication app = (NotificationApplication) getApplication();
        app.getClickHandlerFactory().setNotificationDataManager(mNotificationDataManager);

        mHeadsUpManager = new CarHeadsUpNotificationManager(/* context= */this,
                app.getClickHandlerFactory(),
                mNotificationDataManager);
        app.getCarUxRestrictionWrapper().setCarHeadsUpNotificationManager(mHeadsUpManager);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return ACTION_LOCAL_BINDING.equals(intent.getAction())
                ? new LocalBinder() : super.onBind(intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        Log.d(TAG, "onNotificationPosted: " + sbn);
        if (!isNotificationForCurrentUser(sbn)) {
            return;
        }
        mRankingMap = rankingMap;
        notifyNotificationPosted(sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.d(TAG, "onNotificationRemoved: " + sbn);
        mActiveNotifications.remove(sbn.getKey());
        mHeadsUpManager.maybeRemoveHeadsUp(sbn);
        notifyNotificationRemoved(sbn);
    }

    @Override
    public void onNotificationRankingUpdate(RankingMap rankingMap) {
        mRankingMap = rankingMap;
        for (StatusBarNotification sbn : mActiveNotifications.values()) {
            if (!mRankingMap.getRanking(sbn.getKey(), mTemporaryRanking)) {
                continue;
            }
            String oldOverrideGroupKey = sbn.getOverrideGroupKey();
            String newOverrideGroupKey = getOverrideGroupKey(sbn.getKey());
            if (!Objects.equals(oldOverrideGroupKey, newOverrideGroupKey)) {
                sbn.setOverrideGroupKey(newOverrideGroupKey);
            }
        }
    }

    /**
     * Get the override group key of a {@link StatusBarNotification} given its key.
     */
    @Nullable
    private String getOverrideGroupKey(String key) {
        if (mRankingMap != null) {
            mRankingMap.getRanking(key, mTemporaryRanking);
            return mTemporaryRanking.getOverrideGroupKey();
        }
        return null;
    }

    /**
     * Get all active notifications.
     *
     * @return a map of all active notifications with key being the notification key.
     */
    Map<String, StatusBarNotification> getNotifications() {
        return mActiveNotifications.entrySet().stream()
                .filter(x -> (isNotificationForCurrentUser(x.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public RankingMap getCurrentRanking() {
        return mRankingMap;
    }

    @Override
    public void onListenerConnected() {
        mActiveNotifications = Stream.of(getActiveNotifications()).collect(
                Collectors.toMap(StatusBarNotification::getKey, sbn -> sbn));
        mRankingMap = super.getCurrentRanking();
    }

    @Override
    public void onListenerDisconnected() {
    }

    public void setHandler(Handler handler) {
        mHandler = handler;
    }

    private boolean isNotificationForCurrentUser(StatusBarNotification sbn) {
        // Notifications should only be shown for the current user and the the notifications from
        // the system when CarNotification is running as SystemUI component.
        return (sbn.getUser().getIdentifier() == ActivityManager.getCurrentUser()
                || sbn.getUser().getIdentifier() == UserHandle.USER_ALL);
    }

    private void notifyNotificationRemoved(StatusBarNotification sbn) {
        if (mHandler == null) {
            return;
        }
        Message msg = Message.obtain(mHandler);
        msg.what = NOTIFY_NOTIFICATION_REMOVED;
        msg.obj = sbn;
        mHandler.sendMessage(msg);
    }

    private void notifyNotificationPosted(StatusBarNotification sbn) {
        mNotificationDataManager.addNewMessageNotification(sbn);
        mHeadsUpManager.maybeShowHeadsUp(sbn, getCurrentRanking(), mActiveNotifications);
        if (mHandler == null) {
            return;
        }
        Message msg = Message.obtain(mHandler);
        msg.what = NOTIFY_NOTIFICATION_POSTED;
        msg.obj = sbn;
        mHandler.sendMessage(msg);
    }

    class LocalBinder extends Binder {
        public CarNotificationListener getService() {
            return CarNotificationListener.this;
        }
    }
}
