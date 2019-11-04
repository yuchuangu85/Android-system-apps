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

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import androidx.recyclerview.widget.DiffUtil;

import java.util.List;
import java.util.Objects;

/**
 * {@link DiffUtil} for car notifications.
 * This class is not intended for general usage except for the static methods.
 *
 * <p> Two notifications are considered the same if they have the same:
 * <ol>
 * <li> GroupKey
 * <li> Number of StatusBarNotifications contained
 * <li> The order of each StatusBarNotification
 * <li> The identifier of each individual StatusBarNotification contained
 * <li> The content of each individual StatusBarNotification contained
 * </ol>
 */
class CarNotificationDiff extends DiffUtil.Callback {
    private final Context mContext;
    private final List<NotificationGroup> mOldList;
    private final List<NotificationGroup> mNewList;

    CarNotificationDiff(
            Context context,
            List<NotificationGroup> oldList,
            List<NotificationGroup> newList) {
        mContext = context;
        mOldList = oldList;
        mNewList = newList;
    }

    @Override
    public int getOldListSize() {
        return mOldList.size();
    }

    @Override
    public int getNewListSize() {
        return mNewList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        NotificationGroup oldItem = mOldList.get(oldItemPosition);
        NotificationGroup newItem = mNewList.get(newItemPosition);
        return sameGroupUniqueIdentifiers(oldItem, newItem);
    }

    /**
     * Shallow comparison for {@link NotificationGroup}.
     * <p>
     * Checks if two grouped notifications have the same:
     * <ol>
     * <li> GroupKey
     * <li> GroupSummaryKey
     * </ol>
     * <p>
     * Checks for individual StatusBarNotification contained is not done because child will itself
     * take care of it.
     */
    static boolean sameGroupUniqueIdentifiers(
            NotificationGroup oldItem, NotificationGroup newItem) {

        if (oldItem == newItem) {
            return true;
        }

        if (!oldItem.getGroupKey().equals(newItem.getGroupKey())) {
            return false;
        }

        if (!sameNotificationKey(
                oldItem.getGroupSummaryNotification(), newItem.getGroupSummaryNotification())) {
            return false;
        }

        return true;
    }

    /**
     * Shallow comparison for {@link StatusBarNotification}: only comparing the unique IDs.
     *
     * <p> Returns true if two notifications have the same key.
     */
    static boolean sameNotificationKey(
            StatusBarNotification oldItem, StatusBarNotification newItem) {
        if (oldItem == newItem) {
            return true;
        }

        return oldItem != null
                && newItem != null
                && Objects.equals(oldItem.getKey(), newItem.getKey());
    }

    /**
     * Shallow comparison for {@link StatusBarNotification}: comparing the unique IDs and the
     * notification Flags.
     *
     * <p> Returns true if two notifications have the same key and notification flags.
     */
    static boolean sameNotificationKeyAndFlags(
            StatusBarNotification oldItem, StatusBarNotification newItem) {
        return sameNotificationKey(oldItem, newItem)
                && oldItem.getNotification().flags == newItem.getNotification().flags;
    }

    /**
     * Deep comparison for {@link NotificationGroup}.
     *
     * <p> Compare the size and contents of each StatusBarNotification inside the NotificationGroup.
     *
     * <p> This method will only be called if {@link #areItemsTheSame} returns true.
     */
    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        NotificationGroup oldItem = mOldList.get(oldItemPosition);
        NotificationGroup newItem = mNewList.get(newItemPosition);

        if (!sameNotificationContent(
                oldItem.getGroupSummaryNotification(), newItem.getGroupSummaryNotification())) {
            return false;
        }

        if (oldItem.getChildCount() != newItem.getChildCount()) {
            return false;
        }

        List<StatusBarNotification> oldChildNotifications = oldItem.getChildNotifications();
        List<StatusBarNotification> newChildNotifications = newItem.getChildNotifications();

        for (int i = 0; i < oldItem.getChildCount(); i++) {
            StatusBarNotification oldNotification = oldChildNotifications.get(i);
            StatusBarNotification newNotification = newChildNotifications.get(i);
            if (!sameNotificationContent(oldNotification, newNotification)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Deep comparison for {@link StatusBarNotification}.
     *
     * <p> We are only comparing a subset of the fields that have visible effects on our product.
     * Most of the deprecated fields are not compared.
     * Fields that do not have visible effects, e.g. privacy-related things are ignored for now.
     */
    private boolean sameNotificationContent(
            StatusBarNotification oldItem, StatusBarNotification newItem) {

        if (oldItem == newItem) {
            return true;
        }

        if (oldItem == null || newItem == null) {
            return false;
        }

        if (oldItem.isGroup() != newItem.isGroup()
                || oldItem.isClearable() != newItem.isClearable()
                || oldItem.isOngoing() != newItem.isOngoing()) {
            return false;
        }

        Notification oldNotification = oldItem.getNotification();
        Notification newNotification = newItem.getNotification();

        if (oldNotification.flags != newNotification.flags
                || oldNotification.category != newNotification.category
                || oldNotification.color != newNotification.color
                || !areBundlesEqual(oldNotification.extras, newNotification.extras)
                || !Objects.equals(oldNotification.contentIntent, newNotification.contentIntent)
                || !Objects.equals(oldNotification.deleteIntent, newNotification.deleteIntent)
                || !Objects.equals(
                        oldNotification.fullScreenIntent, newNotification.fullScreenIntent)
                || !Objects.deepEquals(oldNotification.actions, newNotification.actions)) {
            return false;
        }

        // Recover builders only until the above if-statements fail
        Notification.Builder oldBuilder =
                Notification.Builder.recoverBuilder(mContext, oldNotification);
        Notification.Builder newBuilder =
                Notification.Builder.recoverBuilder(mContext, newNotification);

        return !Notification.areStyledNotificationsVisiblyDifferent(oldBuilder, newBuilder);
    }

    private boolean areBundlesEqual(Bundle oldBundle, Bundle newBundle) {
        if (oldBundle.size() != newBundle.size()) {
            return false;
        }

        for (String key : oldBundle.keySet()) {
            if (!newBundle.containsKey(key)) {
                return false;
            }

            Object oldValue = oldBundle.get(key);
            Object newValue = newBundle.get(key);
            if (!Objects.equals(oldValue, newValue)) {
                return false;
            }
        }

        return true;
    }
}
