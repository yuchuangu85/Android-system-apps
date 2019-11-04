/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.settings.applications.specialaccess;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.Nullable;

import com.android.settingslib.applications.ApplicationsState;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages a list of {@link ApplicationsState.AppEntry} instances by syncing in the background and
 * providing updates via a {@link Callback}. Clients may provide an {@link ExtraInfoBridge} to
 * populate the {@link ApplicationsState.AppEntry#extraInfo} field with use case sepecific data.
 * Clients may also provide an {@link ApplicationsState.AppFilter} via an {@link AppFilterProvider}
 * to determine which entries will appear in the list updates.
 *
 * <p>Clients should call {@link #init(ExtraInfoBridge, AppFilterProvider, Callback)} to specify
 * behavior and then {@link #start()} to begin loading. {@link #stop()} will cancel loading, and
 * {@link #destroy()} will clean up resources when this class will no longer be used.
 */
public class AppEntryListManager {

    /** Callback for receiving events from {@link AppEntryListManager}. */
    public interface Callback {
        /**
         * Called when the list of {@link ApplicationsState.AppEntry} instances or the {@link
         * ApplicationsState.AppEntry#extraInfo} fields have changed.
         */
        void onAppEntryListChanged(List<ApplicationsState.AppEntry> entries);
    }

    /**
     * Provides an {@link ApplicationsState.AppFilter} to tailor the entries in the list updates.
     */
    public interface AppFilterProvider {
        /**
         * Returns the filter that should be used to trim the entries list before callback delivery.
         */
        ApplicationsState.AppFilter getAppFilter();
    }

    /** Bridges extra information to {@link ApplicationsState.AppEntry#extraInfo}. */
    public interface ExtraInfoBridge {
        /**
         * Populates the {@link ApplicationsState.AppEntry#extraInfo} field on the {@code enrties}
         * with the relevant data for the implementation.
         */
        void loadExtraInfo(List<ApplicationsState.AppEntry> entries);
    }

    private final ApplicationsState.Callbacks mSessionCallbacks =
            new ApplicationsState.Callbacks() {
                @Override
                public void onRunningStateChanged(boolean running) {
                    // No op.
                }

                @Override
                public void onPackageListChanged() {
                    forceUpdate();
                }

                @Override
                public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> apps) {
                    if (mCallback != null) {
                        mCallback.onAppEntryListChanged(apps);
                    }
                }

                @Override
                public void onPackageIconChanged() {
                    // No op.
                }

                @Override
                public void onPackageSizeChanged(String packageName) {
                    // No op.
                }

                @Override
                public void onAllSizesComputed() {
                    // No op.
                }

                @Override
                public void onLauncherInfoChanged() {
                    // No op.
                }

                @Override
                public void onLoadEntriesCompleted() {
                    mHasReceivedLoadEntries = true;
                    forceUpdate();
                }
            };

    private final ApplicationsState mApplicationsState;
    private final BackgroundHandler mBackgroundHandler;
    private final MainHandler mMainHandler;

    private ExtraInfoBridge mExtraInfoBridge;
    private AppFilterProvider mFilterProvider;
    private Callback mCallback;
    private ApplicationsState.Session mSession;

    private boolean mHasReceivedLoadEntries;
    private boolean mHasReceivedExtraInfo;

    public AppEntryListManager(Context context) {
        mApplicationsState = ApplicationsState.getInstance(
                (Application) context.getApplicationContext());
        // Run on the same background thread as the ApplicationsState to make sure updates don't
        // conflict.
        mBackgroundHandler = new BackgroundHandler(new WeakReference<>(this),
                mApplicationsState.getBackgroundLooper());
        mMainHandler = new MainHandler(new WeakReference<>(this));
    }

    /**
     * Specifies the behavior of this manager.
     *
     * @param extraInfoBridge an optional bridge to load information into the entries.
     * @param filterProvider  provides a filter to tailor the contents of the list updates.
     * @param callback        callback to which updated lists are delivered.
     */
    public void init(@Nullable ExtraInfoBridge extraInfoBridge,
            @Nullable AppFilterProvider filterProvider,
            Callback callback) {
        if (mSession != null) {
            destroy();
        }
        mExtraInfoBridge = extraInfoBridge;
        mFilterProvider = filterProvider;
        mCallback = callback;
        mSession = mApplicationsState.newSession(mSessionCallbacks);
    }

    /**
     * Starts loading the information in the background. When loading is finished, the {@link
     * Callback} will be notified on the main thread.
     */
    public void start() {
        mSession.onResume();
    }

    /**
     * Stops any pending loading.
     */
    public void stop() {
        mSession.onPause();
        clearHandlers();
    }

    /**
     * Cleans up internal state when this will no longer be used.
     */
    public void destroy() {
        mSession.onDestroy();
        clearHandlers();
        mExtraInfoBridge = null;
        mFilterProvider = null;
        mCallback = null;
    }

    /**
     * Schedules updates for all {@link ApplicationsState.AppEntry} instances. When loading is
     * finished, the {@link Callback} will be notified on the main thread.
     */
    public void forceUpdate() {
        mBackgroundHandler.sendEmptyMessage(BackgroundHandler.MSG_LOAD_ALL);
    }

    /**
     * Schedules an update for the given {@code entry}. When loading is finished, the {@link
     * Callback} will be notified on the main thread.
     */
    public void forceUpdate(ApplicationsState.AppEntry entry) {
        mBackgroundHandler.obtainMessage(BackgroundHandler.MSG_LOAD_PKG,
                entry).sendToTarget();
    }

    private void rebuild() {
        if (!mHasReceivedLoadEntries || !mHasReceivedExtraInfo) {
            // Don't rebuild the list until all the app entries are loaded.
            return;
        }
        mSession.rebuild((mFilterProvider != null) ? mFilterProvider.getAppFilter()
                        : ApplicationsState.FILTER_EVERYTHING,
                ApplicationsState.ALPHA_COMPARATOR, /* foreground= */ false);
    }

    private void clearHandlers() {
        mBackgroundHandler.removeMessages(BackgroundHandler.MSG_LOAD_ALL);
        mBackgroundHandler.removeMessages(BackgroundHandler.MSG_LOAD_PKG);
        mMainHandler.removeMessages(MainHandler.MSG_INFO_UPDATED);
    }

    private void loadInfo(List<ApplicationsState.AppEntry> entries) {
        if (mExtraInfoBridge != null) {
            mExtraInfoBridge.loadExtraInfo(entries);
        }
        for (ApplicationsState.AppEntry entry : entries) {
            mApplicationsState.ensureIcon(entry);
        }
    }

    private static class BackgroundHandler extends Handler {
        private static final int MSG_LOAD_ALL = 1;
        private static final int MSG_LOAD_PKG = 2;

        private final WeakReference<AppEntryListManager> mOuter;

        BackgroundHandler(WeakReference<AppEntryListManager> outer, Looper looper) {
            super(looper);
            mOuter = outer;
        }

        @Override
        public void handleMessage(Message msg) {
            AppEntryListManager outer = mOuter.get();
            if (outer == null) {
                return;
            }
            switch (msg.what) {
                case MSG_LOAD_ALL:
                    outer.loadInfo(outer.mSession.getAllApps());
                    outer.mMainHandler.sendEmptyMessage(MainHandler.MSG_INFO_UPDATED);
                    break;
                case MSG_LOAD_PKG:
                    ApplicationsState.AppEntry entry = (ApplicationsState.AppEntry) msg.obj;
                    outer.loadInfo(Collections.singletonList(entry));
                    outer.mMainHandler.sendEmptyMessage(MainHandler.MSG_INFO_UPDATED);
                    break;
            }
        }
    }

    private static class MainHandler extends Handler {
        private static final int MSG_INFO_UPDATED = 1;

        private final WeakReference<AppEntryListManager> mOuter;

        MainHandler(WeakReference<AppEntryListManager> outer) {
            mOuter = outer;
        }

        @Override
        public void handleMessage(Message msg) {
            AppEntryListManager outer = mOuter.get();
            if (outer == null) {
                return;
            }
            switch (msg.what) {
                case MSG_INFO_UPDATED:
                    outer.mHasReceivedExtraInfo = true;
                    outer.rebuild();
                    break;
            }
        }
    }
}
