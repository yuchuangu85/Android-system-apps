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
 * limitations under the License
 */
package com.android.car.notification;

import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.android.car.assist.client.CarAssistUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps track of the additional state of notifications. This class is not thread safe and should
 * only be called from the main thread.
 */
public class NotificationDataManager {
    private static String TAG = "NotificationDataManager";

    /**
     * Map that contains the key of all message notifications, mapped to whether or not the key's
     * notification should be muted.
     *
     * Muted notifications should show an "Unmute" button on their notification and should not
     * trigger the HUN when new notifications arrive with the same key. Unmuted should show a "Mute"
     * button on their notification and should trigger the HUN. Both should update the notification
     * in the Notification Center.
     */
    private final Map<String, Boolean> mMessageNotificationToMuteStateMap = new HashMap<>();

    /**
     * Map that contains the key of all unseen notifications.
     */
    private final Map<String, Boolean> mUnseenNotificationMap = new HashMap<>();

    private OnUnseenCountUpdateListener mOnUnseenCountUpdateListener;

    /**
     * Interface for listeners that want to register for receiving updates to the notification
     * unseen count.
     */
    public interface OnUnseenCountUpdateListener {
        /**
         * Called when unseen notification count is changed.
         */
        void onUnseenCountUpdate();
    }

    public NotificationDataManager() {
        clearAll();
    }

    /**
     * Sets listener for unseen notification count change event.
     * @param listener UnseenCountUpdateListener
     */
    public void setOnUnseenCountUpdateListener(OnUnseenCountUpdateListener listener) {
        mOnUnseenCountUpdateListener = listener;
    }

    void addNewMessageNotification(StatusBarNotification notification) {
        if (CarAssistUtils.isCarCompatibleMessagingNotification(notification)) {
            mMessageNotificationToMuteStateMap
                    .putIfAbsent(notification.getKey(), /* muteState= */
                            false);
        }
    }

    void updateUnseenNotification(List<NotificationGroup> notificationGroups) {
        Set<String> currentNotificationKeys = new HashSet<>();

        Collections.addAll(currentNotificationKeys,
                mUnseenNotificationMap.keySet().toArray(new String[0]));

        for (NotificationGroup group : notificationGroups) {
            for (StatusBarNotification sbn : group.getChildNotifications()) {
                // add new notifications
                mUnseenNotificationMap.putIfAbsent(sbn.getKey(), true);

                // sbn exists in both sets.
                currentNotificationKeys.remove(sbn.getKey());
            }
        }

        // These keys were removed from notificationGroups. Remove from mUnseenNotificationMap.
        for (String notificationKey : currentNotificationKeys) {
            mUnseenNotificationMap.remove(notificationKey);
        }

        if (mOnUnseenCountUpdateListener != null) {
            mOnUnseenCountUpdateListener.onUnseenCountUpdate();
        }
    }

    /**
     * Returns the mute state of the notification, or false if notification does not have a mute
     * state. Only message notifications can be muted.
     **/
    public boolean isMessageNotificationMuted(StatusBarNotification notification) {
        if (!mMessageNotificationToMuteStateMap.containsKey(notification.getKey())) {
            addNewMessageNotification(notification);
        }
        return mMessageNotificationToMuteStateMap.getOrDefault(notification.getKey(), false);
    }

    /**
     * If {@param sbn} is a messaging notification, this function will toggle its mute state. This
     * state determines whether or not a HUN will be shown on future updates to the notification.
     * It also determines the title of the notification's "Mute" button.
     **/
    public void toggleMute(StatusBarNotification sbn) {
        if (CarAssistUtils.isCarCompatibleMessagingNotification(sbn)) {
            String sbnKey = sbn.getKey();
            Boolean currentMute = mMessageNotificationToMuteStateMap.get(sbnKey);
            if (currentMute != null) {
                mMessageNotificationToMuteStateMap.put(sbnKey, !currentMute);
            } else {
                Log.e(TAG, "Msg notification was not initially added to the mute state map: "
                        + sbn.getKey());
            }
        }
    }

    /**
     * Clear unseen and mute notification state information.
     */
    public void clearAll() {
        mMessageNotificationToMuteStateMap.clear();
        mUnseenNotificationMap.clear();

        if (mOnUnseenCountUpdateListener != null) {
            mOnUnseenCountUpdateListener.onUnseenCountUpdate();
        }
    }

    void setNotificationAsSeen(StatusBarNotification sbn) {
        if (mUnseenNotificationMap.containsKey(sbn.getKey())) {
            mUnseenNotificationMap.put(sbn.getKey(), false);
        }

        if (mOnUnseenCountUpdateListener != null) {
            mOnUnseenCountUpdateListener.onUnseenCountUpdate();
        }
    }

    /**
     * Returns unseen notification count.
     */
    public int getUnseenNotificationCount() {
        int unseenCount = 0;
        for (Boolean value : mUnseenNotificationMap.values()) {
            if (value) {
                unseenCount++;
            }
        }
        return unseenCount;
    }
}
