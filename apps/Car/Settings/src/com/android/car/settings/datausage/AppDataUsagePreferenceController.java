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
 * limitations under the License.
 */

package com.android.car.settings.datausage;

import static android.net.TrafficStats.UID_REMOVED;
import static android.net.TrafficStats.UID_TETHERING;

import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.UserInfo;
import android.net.NetworkStats;
import android.os.UserHandle;
import android.util.SparseArray;

import androidx.preference.PreferenceGroup;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.common.ProgressBarPreference;
import com.android.settingslib.AppItem;
import com.android.settingslib.net.UidDetail;
import com.android.settingslib.net.UidDetailProvider;
import com.android.settingslib.utils.ThreadUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

/**
 * Controller that adds all the applications using the data sorted by the amount of data used. The
 * first application that used most amount of data will be at the top with progress 100 percentage.
 * All other progress are calculated relatively.
 */
public class AppDataUsagePreferenceController extends
        PreferenceController<PreferenceGroup> implements AppsNetworkStatsManager.Callback {

    private final UidDetailProvider mUidDetailProvider;
    private final CarUserManagerHelper mCarUserManagerHelper;

    public AppDataUsagePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mUidDetailProvider = new UidDetailProvider(getContext());
        mCarUserManagerHelper = new CarUserManagerHelper(getContext());
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    public void onDataLoaded(@Nullable NetworkStats stats, @Nullable int[] restrictedUids) {
        List<AppItem> items = new ArrayList<>();
        long largest = 0;

        List<UserInfo> profiles = mCarUserManagerHelper.getAllUsers();
        SparseArray<AppItem> knownItems = new SparseArray<AppItem>();

        NetworkStats.Entry entry = null;
        if (stats != null) {
            for (int i = 0; i < stats.size(); i++) {
                entry = stats.getValues(i, entry);
                long size = aggregateDataUsage(knownItems, items, entry, profiles);
                largest = Math.max(size, largest);
            }
        }

        updateRestrictedState(restrictedUids, knownItems, items, profiles);
        sortAndAddPreferences(items, largest);
    }

    private long aggregateDataUsage(SparseArray<AppItem> knownItems, List<AppItem> items,
            NetworkStats.Entry entry, List<UserInfo> profiles) {
        int currentUserId = mCarUserManagerHelper.getCurrentProcessUserId();

        // Decide how to collapse items together.
        int uid = entry.uid;

        int collapseKey;
        int category;
        int userId = UserHandle.getUserId(uid);

        if (isUidValid(uid)) {
            collapseKey = uid;
            category = AppItem.CATEGORY_APP;
            return accumulate(collapseKey, knownItems, entry, category, items);
        }

        if (!UserHandle.isApp(uid)) {
            collapseKey = android.os.Process.SYSTEM_UID;
            category = AppItem.CATEGORY_APP;
            return accumulate(collapseKey, knownItems, entry, category, items);
        }

        if (profileContainsUserId(profiles, userId) && userId == currentUserId) {
            // Add to app item.
            collapseKey = uid;
            category = AppItem.CATEGORY_APP;
            return accumulate(collapseKey, knownItems, entry, category, items);
        }

        if (profileContainsUserId(profiles, userId) && userId != currentUserId) {
            // Add to a managed user item.
            int managedKey = UidDetailProvider.buildKeyForUser(userId);
            long usersLargest = accumulate(managedKey, knownItems, entry, AppItem.CATEGORY_USER,
                    items);
            collapseKey = uid;
            category = AppItem.CATEGORY_APP;
            long appLargest = accumulate(collapseKey, knownItems, entry, category, items);
            return Math.max(usersLargest, appLargest);
        }

        // If it is a removed user add it to the removed users' key.
        Optional<UserInfo> info = profiles.stream().filter(
                userInfo -> userInfo.id == userId).findFirst();
        if (!info.isPresent()) {
            collapseKey = UID_REMOVED;
            category = AppItem.CATEGORY_APP;
        } else {
            // Add to other user item.
            collapseKey = UidDetailProvider.buildKeyForUser(userId);
            category = AppItem.CATEGORY_USER;
        }

        return accumulate(collapseKey, knownItems, entry, category, items);
    }

    /**
     * UID does not belong to a regular app and maybe belongs to a removed application or
     * application using for tethering traffic.
     */
    private boolean isUidValid(int uid) {
        return !UserHandle.isApp(uid) && (uid == UID_REMOVED || uid == UID_TETHERING);
    }

    private boolean profileContainsUserId(List<UserInfo> profiles, int userId) {
        return profiles.stream().anyMatch(userInfo -> userInfo.id == userId);
    }

    private void updateRestrictedState(@Nullable int[] restrictedUids,
            SparseArray<AppItem> knownItems, List<AppItem> items, List<UserInfo> profiles) {
        if (restrictedUids == null) {
            return;
        }

        for (int i = 0; i < restrictedUids.length; ++i) {
            int uid = restrictedUids[i];
            // Only splice in restricted state for current user or managed users.
            if (!profileContainsUserId(profiles, uid)) {
                continue;
            }

            AppItem item = knownItems.get(uid);
            if (item == null) {
                item = new AppItem(uid);
                item.total = -1;
                items.add(item);
                knownItems.put(item.key, item);
            }
            item.restricted = true;
        }
    }

    private void sortAndAddPreferences(List<AppItem> items, long largest) {
        Collections.sort(items);
        for (int i = 0; i < items.size(); i++) {
            int percentTotal = largest != 0 ? (int) (items.get(i).total * 100 / largest) : 0;
            AppDataUsagePreference preference = new AppDataUsagePreference(getContext(),
                    items.get(i), percentTotal, mUidDetailProvider);
            getPreference().addPreference(preference);
        }
    }

    /**
     * Accumulate data usage of a network stats entry for the item mapped by the collapse key.
     * Creates the item if needed.
     *
     * @param collapseKey the collapse key used to map the item.
     * @param knownItems collection of known (already existing) items.
     * @param entry the network stats entry to extract data usage from.
     * @param itemCategory the item is categorized on the list view by this category. Must be
     */
    private static long accumulate(int collapseKey, SparseArray<AppItem> knownItems,
            NetworkStats.Entry entry, int itemCategory, List<AppItem> items) {
        int uid = entry.uid;
        AppItem item = knownItems.get(collapseKey);
        if (item == null) {
            item = new AppItem(collapseKey);
            item.category = itemCategory;
            items.add(item);
            knownItems.put(item.key, item);
        }
        item.addUid(uid);
        item.total += entry.rxBytes + entry.txBytes;
        return item.total;
    }

    private class AppDataUsagePreference extends ProgressBarPreference {

        private final AppItem mItem;
        private final int mPercent;
        private UidDetail mDetail;

        AppDataUsagePreference(Context context, AppItem item, int percent,
                UidDetailProvider provider) {
            super(context);
            mItem = item;
            mPercent = percent;
            setLayoutResource(R.layout.progress_bar_preference);
            setKey(String.valueOf(item.key));
            if (item.restricted && item.total <= 0) {
                setSummary(R.string.data_usage_app_restricted);
            } else {
                CharSequence s = DataUsageUtils.bytesToIecUnits(context, item.total);
                setSummary(s);
            }
            mDetail = provider.getUidDetail(item.key, false /* blocking */);
            if (mDetail != null) {
                setAppInfo();
            } else {
                ThreadUtils.postOnBackgroundThread(() -> {
                    mDetail = provider.getUidDetail(mItem.key, true /* blocking */);
                    ThreadUtils.postOnMainThread(() -> setAppInfo());
                });
            }
        }

        private void setAppInfo() {
            if (mDetail != null) {
                setIcon(mDetail.icon);
                setTitle(mDetail.label);
                setProgress(mPercent);
            } else {
                setIcon(null);
                setTitle(null);
            }
        }
    }
}
