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
import android.app.Notification;
import android.app.NotificationManager;
import android.car.CarNotConnectedException;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.car.notification.template.MessageNotificationViewHolder;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Manager that filters, groups and ranks the notifications in the notification center.
 *
 * <p> Note that heads-up notifications have a different filtering mechanism and is managed by
 * {@link CarHeadsUpNotificationManager}.
 */
public class PreprocessingManager {

    /** Listener that will be notified when a call state changes. **/
    public interface CallStateListener {
        /**
         * @param isInCall is true when user is currently in a call.
         */
        void onCallStateChanged(boolean isInCall);
    }

    private static final String TAG = "PreprocessingManager";

    private final String mEllipsizedString;
    private final Context mContext;

    private static PreprocessingManager sInstance;

    private int mMaxStringLength = Integer.MAX_VALUE;
    private Map<String, StatusBarNotification> mOldNotifications;
    private List<NotificationGroup> mOldProcessedNotifications;
    private NotificationListenerService.RankingMap mOldRankingMap;
    private Map<String, Integer> mRanking = new HashMap<>();

    private boolean mIsInCall;
    private List<CallStateListener> mCallStateListeners = new ArrayList<>();

    @VisibleForTesting
    final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                mIsInCall = TelephonyManager.EXTRA_STATE_OFFHOOK
                        .equals(intent.getStringExtra(TelephonyManager.EXTRA_STATE));
                for (CallStateListener listener : mCallStateListeners) {
                    listener.onCallStateChanged(mIsInCall);
                }
            }
        }
    };

    private PreprocessingManager(Context context) {
        mEllipsizedString = context.getString(R.string.ellipsized_string);
        mContext = context;

        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        context.registerReceiver(mIntentReceiver, filter);
    }

    public static PreprocessingManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PreprocessingManager(context);
        }
        return sInstance;
    }

    /**
     * Initialize the data when the UI becomes foreground.
     */
    public void init(Map<String, StatusBarNotification> notifications, RankingMap rankingMap) {
        mOldNotifications = notifications;
        mOldRankingMap = rankingMap;
        mOldProcessedNotifications =
                process(/* showLessImportantNotifications = */ false, notifications, rankingMap);
    }

    /**
     * Process the given notifications. In order for DiffUtil to work, the adapter needs a new
     * data object each time it updates, therefore wrapping the return value in a new list.
     *
     * @param showLessImportantNotifications whether less important notifications should be shown.
     * @param notifications the list of notifications to be processed.
     * @param rankingMap the ranking map for the notifications.
     * @return the processed notifications in a new list.
     */
    public List<NotificationGroup> process(
            boolean showLessImportantNotifications,
            Map<String, StatusBarNotification> notifications,
            RankingMap rankingMap) {

        return new ArrayList<>(
                rank(group(optimizeForDriving(
                        filter(showLessImportantNotifications,
                                new ArrayList<>(notifications.values()),
                                rankingMap))),
                        rankingMap));
    }

    /**
     * Create a new list of notifications based on existing list.
     *
     * @param showLessImportantNotifications whether less important notifications should be shown.
     * @param newRankingMap the latest ranking map for the notifications.
     * @return the new notification group list that should be shown to the user.
     */
    public List<NotificationGroup> updateNotifications(
            boolean showLessImportantNotifications,
            StatusBarNotification sbn,
            int updateType,
            RankingMap newRankingMap) {

        if (updateType == CarNotificationListener.NOTIFY_NOTIFICATION_REMOVED) {
            // removal of a notification is the same as a normal preprocessing
            mOldNotifications.remove(sbn.getKey());
            mOldProcessedNotifications =
                    process(showLessImportantNotifications, mOldNotifications, mOldRankingMap);
        }

        if (updateType == CarNotificationListener.NOTIFY_NOTIFICATION_POSTED) {
            StatusBarNotification notification = optimizeForDriving(sbn);
            boolean isUpdate = mOldNotifications.containsKey(notification.getKey());
            if (isUpdate) {
                // if is an update of the previous notification
                mOldNotifications.put(notification.getKey(), notification);
                mOldProcessedNotifications = process(showLessImportantNotifications,
                        mOldNotifications, mOldRankingMap);
            } else {
                // insert a new notification into the list
                mOldNotifications.put(notification.getKey(), notification);
                mOldProcessedNotifications = new ArrayList<>(
                        additionalRank(additionalGroup(notification), newRankingMap));
            }
        }

        return mOldProcessedNotifications;
    }

    /** Add {@link CallStateListener} in order to be notified when call state is changed. **/
    public void addCallStateListener(CallStateListener listener) {
        if (mCallStateListeners.contains(listener)) return;
        mCallStateListeners.add(listener);
        listener.onCallStateChanged(mIsInCall);
    }

    /** Remove {@link CallStateListener} to stop getting notified when call state is changed. **/
    public void removeCallStateListener(CallStateListener listener) {
        mCallStateListeners.remove(listener);
    }

    /**
     * Returns true if the current {@link StatusBarNotification} should be filtered out and not
     * added to the list.
     */
    boolean shouldFilter(StatusBarNotification sbn, RankingMap rankingMap) {
        return isLessImportantForegroundNotification(sbn, rankingMap)
                || isMediaOrNavigationNotification(sbn);
    }

    /**
     * Filter a list of {@link StatusBarNotification}s according to OEM's configurations.
     */
    private List<StatusBarNotification> filter(
            boolean showLessImportantNotifications,
            List<StatusBarNotification> notifications,
            RankingMap rankingMap) {
        // remove less important foreground service notifications for car
        if (!showLessImportantNotifications) {
            notifications.removeIf(statusBarNotification
                    -> isLessImportantForegroundNotification(statusBarNotification,
                    rankingMap));

            // remove media and navigation notifications in the notification center for car
            notifications.removeIf(statusBarNotification
                    -> isMediaOrNavigationNotification(statusBarNotification));
        }
        return notifications;
    }

    private boolean isLessImportantForegroundNotification(
            StatusBarNotification statusBarNotification, RankingMap rankingMap) {
        boolean isForeground =
                (statusBarNotification.getNotification().flags
                        & Notification.FLAG_FOREGROUND_SERVICE) != 0;

        if (!isForeground) {
            return false;
        }

        int importance = 0;
        NotificationListenerService.Ranking ranking =
                new NotificationListenerService.Ranking();
        if (rankingMap.getRanking(statusBarNotification.getKey(), ranking)) {
            importance = ranking.getImportance();
        }
        return importance < NotificationManager.IMPORTANCE_DEFAULT
                && NotificationUtils.isSystemPrivilegedOrPlatformKey(mContext,
                statusBarNotification);
    }

    private boolean isMediaOrNavigationNotification(StatusBarNotification statusBarNotification) {
        Notification notification = statusBarNotification.getNotification();
        return notification.isMediaNotification()
                || Notification.CATEGORY_NAVIGATION.equals(notification.category);
    }

    /**
     * Process a list of {@link StatusBarNotification}s to be driving optimized.
     *
     * <p> Note that the string length limit is always respected regardless of whether distraction
     * optimization is required.
     */
    private List<StatusBarNotification> optimizeForDriving(
            List<StatusBarNotification> notifications) {
        notifications.forEach(notification -> notification = optimizeForDriving(notification));
        return notifications;
    }

    /**
     * Helper method that optimize a single {@link StatusBarNotification} for driving.
     *
     * <p> Currently only trimming texts that have visual effects in car. Operation is done on
     * the original notification object passed in; no new object is created.
     *
     * <p> Note that message notifications are not trimmed, so that messages are preserved for
     * assistant read-out. Instead, {@link MessageNotificationViewHolder} will be responsible
     * for the presentation-level text truncation.
     */
    StatusBarNotification optimizeForDriving(StatusBarNotification notification) {
        if (Notification.CATEGORY_MESSAGE.equals(notification.getNotification().category)) {
            return notification;
        }

        Bundle extras = notification.getNotification().extras;
        for (String key : extras.keySet()) {
            switch (key) {
                case Notification.EXTRA_TITLE:
                case Notification.EXTRA_TEXT:
                case Notification.EXTRA_TITLE_BIG:
                case Notification.EXTRA_SUMMARY_TEXT:
                    CharSequence value = extras.getCharSequence(key);
                    extras.putCharSequence(key, trimText(value));
                default:
                    continue;
            }
        }
        return notification;
    }

    /**
     * Helper method that takes a string and trims the length to the maximum character allowed
     * by the {@link CarUxRestrictionsManager}.
     */
    @Nullable
    public CharSequence trimText(@Nullable CharSequence text) {
        if (TextUtils.isEmpty(text) || text.length() < mMaxStringLength) {
            return text;
        }
        int maxLength = mMaxStringLength - mEllipsizedString.length();
        return text.toString().substring(0, maxLength).concat(mEllipsizedString);
    }

    /**
     * Group notifications that have the same group key.
     *
     * <p> Automatically generated group summaries that contains no child notifications are removed.
     * This can happen if a notification group only contains less important notifications that are
     * filtered out in the previous {@link #filter} step.
     *
     * <p> A group of child notifications without a summary notification will not be grouped.
     *
     * @param list list of ungrouped {@link StatusBarNotification}s.
     * @return list of grouped notifications as {@link NotificationGroup}s.
     */
    @VisibleForTesting
    List<NotificationGroup> group(List<StatusBarNotification> list) {
        SortedMap<String, NotificationGroup> groupedNotifications = new TreeMap<>();

        // First pass: group all notifications according to their groupKey.
        for (int i = 0; i < list.size(); i++) {
            StatusBarNotification statusBarNotification = list.get(i);
            Notification notification = statusBarNotification.getNotification();

            String groupKey;
            if (Notification.CATEGORY_CALL.equals(notification.category)) {
                // DO NOT group CATEGORY_CALL.
                groupKey = UUID.randomUUID().toString();
            } else {
                groupKey = statusBarNotification.getGroupKey();
            }

            if (!groupedNotifications.containsKey(groupKey)) {
                NotificationGroup notificationGroup = new NotificationGroup();
                groupedNotifications.put(groupKey, notificationGroup);
            }
            if (notification.isGroupSummary()) {
                groupedNotifications.get(groupKey)
                        .setGroupSummaryNotification(statusBarNotification);
            } else {
                groupedNotifications.get(groupKey).addNotification(statusBarNotification);
            }
        }

        // Second pass: remove automatically generated group summary if it contains no child
        // notifications. This can happen if a notification group only contains less important
        // notifications that are filtered out in the previous filter step.
        List<NotificationGroup> groupList = new ArrayList<>(groupedNotifications.values());
        groupList.removeIf(
                notificationGroup -> {
                    StatusBarNotification summaryNotification =
                            notificationGroup.getGroupSummaryNotification();
                    return notificationGroup.getChildCount() == 0
                            && summaryNotification != null
                            && summaryNotification.getOverrideGroupKey() != null;
                });

        // Third pass: a notification group without a group summary should be restored back into
        // individual notifications.
        List<NotificationGroup> validGroupList = new ArrayList<>();
        groupList.forEach(
                group -> {
                    if (group.getChildCount() > 1 && group.getGroupSummaryNotification() == null) {
                        group.getChildNotifications().forEach(
                                notification -> {
                                    NotificationGroup newGroup = new NotificationGroup();
                                    newGroup.addNotification(notification);
                                    validGroupList.add(newGroup);
                                });
                    } else {
                        validGroupList.add(group);
                    }
                });

        // Fourth pass: if a notification is a group notification, update the timestamp if one of
        // the children notifications shows a timestamp.
        validGroupList.forEach(group -> {
            if (!group.isGroup()) {
                return;
            }

            StatusBarNotification groupSummaryNotification = group.getGroupSummaryNotification();
            boolean showWhen = false;
            long greatestTimestamp = 0;
            for (StatusBarNotification notification : group.getChildNotifications()) {
                if (notification.getNotification().showsTime()) {
                    showWhen = true;
                    greatestTimestamp = Math.max(greatestTimestamp,
                            notification.getNotification().when);
                }
            }

            if (showWhen) {
                groupSummaryNotification.getNotification().extras.putBoolean(
                        Notification.EXTRA_SHOW_WHEN, true);
                groupSummaryNotification.getNotification().when = greatestTimestamp;
            }
        });

        return validGroupList;
    }

    /**
     * Add new NotificationGroup to an existing list of NotificationGroups.
     *
     * @param newNotification the {@link StatusBarNotification} that should be added to the list.
     * @return list of grouped notifications as {@link NotificationGroup}s.
     */
    private List<NotificationGroup> additionalGroup(StatusBarNotification newNotification) {
        Notification notification = newNotification.getNotification();

        if (notification.isGroupSummary()) {
            // if child notifications already exist, ignore this insertion
            for (String key : mOldNotifications.keySet()) {
                if (hasSameGroupKey(mOldNotifications.get(key), newNotification)) {
                    return mOldProcessedNotifications;
                }
            }
            // if child notifications do not exist, insert the summary as a new notification
            NotificationGroup newGroup = new NotificationGroup();
            newGroup.setGroupSummaryNotification(newNotification);
            mOldProcessedNotifications.add(newGroup);
            return mOldProcessedNotifications;

        } else {
            for (int i = 0; i < mOldProcessedNotifications.size(); i++) {
                NotificationGroup oldGroup = mOldProcessedNotifications.get(i);
                // if a group already exists
                if (TextUtils.equals(oldGroup.getGroupKey(), newNotification.getGroupKey())) {
                    // if a standalone group summary exists, replace the group summary notification
                    if (oldGroup.getChildCount() == 0) {
                        mOldProcessedNotifications.add(i, new NotificationGroup(newNotification));
                        return mOldProcessedNotifications;
                    }
                    // if a group already exist with multiple children, insert outside of the group
                    mOldProcessedNotifications.add(new NotificationGroup(newNotification));
                    return mOldProcessedNotifications;
                }
            }
            // if it is a new notification, insert directly
            mOldProcessedNotifications.add(new NotificationGroup(newNotification));
            return mOldProcessedNotifications;
        }
    }

    private boolean hasSameGroupKey(
            StatusBarNotification notification1, StatusBarNotification notification2) {
        return TextUtils.equals(notification1.getGroupKey(), notification2.getGroupKey());
    }

    /**
     * Rank notifications according to the ranking key supplied by the notification.
     */
    private List<NotificationGroup> rank(List<NotificationGroup> notifications,
            RankingMap rankingMap) {

        Collections.sort(notifications, new NotificationComparator(rankingMap));

        // Rank within each group
        notifications.forEach(notificationGroup -> {
            if (notificationGroup.isGroup()) {
                Collections.sort(
                        notificationGroup.getChildNotifications(),
                        new InGroupComparator(rankingMap));
            }
        });
        return notifications;
    }

    /**
     * Only rank top-level notification groups because no children should be inserted into a group.
     */
    public List<NotificationGroup> additionalRank(
            List<NotificationGroup> notifications, RankingMap newRankingMap) {

        Collections.sort(
                notifications, new AdditionalNotificationComparator(newRankingMap));

        return notifications;
    }

    public void setCarUxRestrictionManagerWrapper(CarUxRestrictionManagerWrapper manager) {
        try {
            if (manager == null || manager.getCurrentCarUxRestrictions() == null) {
                return;
            }
            mMaxStringLength =
                    manager.getCurrentCarUxRestrictions().getMaxRestrictedStringLength();
        } catch (CarNotConnectedException e) {
            mMaxStringLength = Integer.MAX_VALUE;
            Log.e(TAG, "Failed to get UxRestrictions thus running unrestricted", e);
        }
    }

    /**
     * Comparator that sorts within the notification group by the sort key. If a sort key is not
     * supplied, sort by the global ranking order.
     */
    private static class InGroupComparator implements Comparator<StatusBarNotification> {
        private final RankingMap mRankingMap;

        InGroupComparator(RankingMap rankingMap) {
            mRankingMap = rankingMap;
        }

        @Override
        public int compare(StatusBarNotification left, StatusBarNotification right) {
            if (left.getNotification().getSortKey() != null
                    && right.getNotification().getSortKey() != null) {
                return left.getNotification().getSortKey().compareTo(
                        right.getNotification().getSortKey());
            }

            NotificationListenerService.Ranking leftRanking =
                    new NotificationListenerService.Ranking();
            mRankingMap.getRanking(left.getKey(), leftRanking);

            NotificationListenerService.Ranking rightRanking =
                    new NotificationListenerService.Ranking();
            mRankingMap.getRanking(right.getKey(), rightRanking);

            return leftRanking.getRank() - rightRanking.getRank();
        }
    }

    /**
     * Comparator that sorts the notification groups by their representative notification's rank.
     */
    private class NotificationComparator implements Comparator<NotificationGroup> {
        private final NotificationListenerService.RankingMap mRankingMap;

        NotificationComparator(NotificationListenerService.RankingMap rankingMap) {
            mRankingMap = rankingMap;
        }

        @Override
        public int compare(NotificationGroup left, NotificationGroup right) {
            NotificationListenerService.Ranking leftRanking =
                    new NotificationListenerService.Ranking();
            mRankingMap.getRanking(left.getNotificationForSorting().getKey(), leftRanking);

            NotificationListenerService.Ranking rightRanking =
                    new NotificationListenerService.Ranking();
            mRankingMap.getRanking(right.getNotificationForSorting().getKey(), rightRanking);

            return leftRanking.getRank() - rightRanking.getRank();
        }
    }

    /**
     * Comparator that sorts the notification groups by their representative notification's
     * rank using both of the initial ranking map and the current ranking map.
     *
     * <p>Cache the ranking value so that it doesn't change over time.</p>
     */
    private class AdditionalNotificationComparator implements Comparator<NotificationGroup> {
        private final RankingMap mNewRankingMap;

        AdditionalNotificationComparator(RankingMap newRankingMap) {
            mNewRankingMap = newRankingMap;
        }

        @Override
        public int compare(NotificationGroup left, NotificationGroup right) {
            int leftRankingNumber = getRanking(left, mNewRankingMap);
            int rightRankingNumber = getRanking(right, mNewRankingMap);
            return leftRankingNumber - rightRankingNumber;
        }
    }

    private int getRanking(NotificationGroup group, RankingMap newRankingMap) {
        int rankingNumber;

        if (mRanking.containsKey(group.getGroupKey())) {
            rankingNumber = mRanking.get(group.getGroupKey());
        } else {
            NotificationListenerService.Ranking rightRanking =
                    new NotificationListenerService.Ranking();
            if (!mOldRankingMap.getRanking(
                    group.getNotificationForSorting().getKey(), rightRanking)) {
                if (newRankingMap != null) {
                    newRankingMap.getRanking(
                            group.getNotificationForSorting().getKey(), rightRanking);
                }
            }
            rankingNumber = rightRanking.getRank();
        }
        mRanking.putIfAbsent(group.getGroupKey(), rankingNumber);
        return rankingNumber;
    }
}
