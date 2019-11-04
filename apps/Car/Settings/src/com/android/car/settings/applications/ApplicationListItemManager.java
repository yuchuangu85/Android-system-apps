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
package com.android.car.settings.applications;

import android.graphics.drawable.Drawable;
import android.os.storage.VolumeInfo;

import androidx.lifecycle.Lifecycle;

import com.android.car.settings.common.Logger;
import com.android.settingslib.applications.ApplicationsState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Class used to load the applications installed on the system with their metadata.
 */
// TODO: consolidate with AppEntryListManager.
public class ApplicationListItemManager implements ApplicationsState.Callbacks {
    /**
     * Callback that is called once the list of applications are loaded.
     */
    public interface AppListItemListener {
        /**
         * Called when the data is successfully loaded from {@link ApplicationsState.Callbacks} and
         * icon, title and summary are set for all the applications.
         */
        void onDataLoaded(ArrayList<ApplicationsState.AppEntry> apps);
    }

    private static final Logger LOG = new Logger(ApplicationListItemManager.class);

    private final VolumeInfo mVolumeInfo;
    private final Lifecycle mLifecycle;
    private final ApplicationsState mAppState;
    private final List<AppListItemListener> mAppListItemListeners = new ArrayList<>();

    private ApplicationsState.Session mSession;
    private ApplicationsState.AppFilter mAppFilter;
    private Comparator<ApplicationsState.AppEntry> mAppEntryComparator;

    public ApplicationListItemManager(VolumeInfo volumeInfo, Lifecycle lifecycle,
            ApplicationsState appState) {
        mVolumeInfo = volumeInfo;
        mLifecycle = lifecycle;
        mAppState = appState;
    }

    /**
     * Registers a listener that will be notified once the data is loaded.
     */
    public void registerListener(AppListItemListener appListItemListener) {
        if (!mAppListItemListeners.contains(appListItemListener) && appListItemListener != null) {
            mAppListItemListeners.add(appListItemListener);
        }
    }

    /**
     * Unregisters the listener.
     */
    public void unregisterlistener(AppListItemListener appListItemListener) {
        mAppListItemListeners.remove(appListItemListener);
    }

    /**
     * Resumes the session on fragment start.
     */
    public void onFragmentStart() {
        mSession.onResume();
    }

    /**
     * Pause the session on fragment stop.
     */
    public void onFragmentStop() {
        mSession.onPause();
    }

    /**
     * Starts the new session and start loading the list of installed applications on the device.
     * This list will be filtered out based on the {@link ApplicationsState.AppFilter} provided.
     * Once the list is ready, {@link AppListItemListener#onDataLoaded} will be called.
     *
     * @param appFilter          based on which the list of applications will be filtered before
     *                           returning.
     * @param appEntryComparator comparator based on which the application list will be sorted.
     */
    public void startLoading(ApplicationsState.AppFilter appFilter,
            Comparator<ApplicationsState.AppEntry> appEntryComparator) {
        if (mSession != null) {
            LOG.w("Loading already started but restart attempted.");
            return; // Prevent leaking sessions.
        }
        mAppFilter = appFilter;
        mAppEntryComparator = appEntryComparator;
        mSession = mAppState.newSession(this, mLifecycle);
    }

    /**
     * Rebuilds the list of applications using the provided {@link ApplicationsState.AppFilter}.
     * The filter will be used for all subsequent loading. Once the list is ready, {@link
     * AppListItemListener#onDataLoaded} will be called.
     */
    public void rebuildWithFilter(ApplicationsState.AppFilter appFilter) {
        mAppFilter = appFilter;
        rebuild();
    }

    @Override
    public void onPackageIconChanged() {
        rebuild();
    }

    @Override
    public void onPackageSizeChanged(String packageName) {
        rebuild();
    }

    @Override
    public void onAllSizesComputed() {
        rebuild();
    }

    @Override
    public void onLauncherInfoChanged() {
        rebuild();
    }

    @Override
    public void onLoadEntriesCompleted() {
        rebuild();
    }

    @Override
    public void onRunningStateChanged(boolean running) {
    }

    @Override
    public void onPackageListChanged() {
        rebuild();
    }

    @Override
    public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> apps) {
        List<String> successfullyLoadedApplications = new ArrayList<>();
        for (ApplicationsState.AppEntry appEntry : apps) {
            String key = appEntry.label + appEntry.sizeStr;
            if (isLoaded(appEntry.label,
                    appEntry.sizeStr, appEntry.icon)) {
                successfullyLoadedApplications.add(key);
            }
        }

        if (successfullyLoadedApplications.size() == apps.size()) {
            for (AppListItemListener appListItemListener : mAppListItemListeners) {
                appListItemListener.onDataLoaded(apps);
            }
        }
    }

    private boolean isLoaded(String title, String summary, Drawable icon) {
        return title != null && summary != null && icon != null;
    }

    ApplicationsState.AppFilter getCompositeFilter(String volumeUuid) {
        if (mAppFilter == null) {
            return null;
        }
        ApplicationsState.AppFilter filter = new ApplicationsState.VolumeFilter(volumeUuid);
        filter = new ApplicationsState.CompoundFilter(mAppFilter, filter);
        return filter;
    }

    private void rebuild() {
        ApplicationsState.AppFilter filterObj = ApplicationsState.FILTER_EVERYTHING;

        filterObj = new ApplicationsState.CompoundFilter(filterObj,
                ApplicationsState.FILTER_NOT_HIDE);
        ApplicationsState.AppFilter compositeFilter = getCompositeFilter(mVolumeInfo.getFsUuid());
        if (compositeFilter != null) {
            filterObj = new ApplicationsState.CompoundFilter(filterObj, compositeFilter);
        }
        ApplicationsState.AppFilter finalFilterObj = filterObj;
        mSession.rebuild(finalFilterObj, mAppEntryComparator, /* foreground= */ false);
    }
}
