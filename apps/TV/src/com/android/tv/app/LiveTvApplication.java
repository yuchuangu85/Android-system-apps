/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tv.app;

import com.android.tv.TvApplication;
import com.android.tv.TvSingletons;
import com.android.tv.analytics.Analytics;
import com.android.tv.analytics.StubAnalytics;
import com.android.tv.analytics.Tracker;
import com.android.tv.common.dagger.ApplicationModule;
import com.android.tv.common.experiments.ExperimentLoader;
import com.android.tv.common.flags.impl.DefaultBackendKnobsFlags;
import com.android.tv.common.flags.impl.DefaultCloudEpgFlags;
import com.android.tv.common.flags.impl.DefaultConcurrentDvrPlaybackFlags;
import com.android.tv.common.flags.impl.DefaultUiFlags;
import com.android.tv.common.singletons.HasSingletons;
import com.android.tv.data.epg.EpgReader;
import com.android.tv.data.epg.StubEpgReader;
import com.android.tv.modules.TvSingletonsModule;
import com.android.tv.perf.PerformanceMonitor;
import com.android.tv.perf.PerformanceMonitorManagerFactory;
import com.android.tv.tunerinputcontroller.BuiltInTunerManager;
import com.android.tv.util.account.AccountHelper;
import com.android.tv.util.account.AccountHelperImpl;
import com.google.common.base.Optional;
import dagger.android.AndroidInjector;
import javax.inject.Provider;

/** The top level application for Live TV. */
public class LiveTvApplication extends TvApplication implements HasSingletons<TvSingletons> {

    static {
        PERFORMANCE_MONITOR_MANAGER.getStartupMeasure().onAppClassLoaded();
    }

    private final Provider<EpgReader> mEpgReaderProvider =
            new Provider<EpgReader>() {

                @Override
                public EpgReader get() {
                    return new StubEpgReader(LiveTvApplication.this);
                }
            };

    private final DefaultBackendKnobsFlags mBackendKnobsFlags = new DefaultBackendKnobsFlags();
    private final DefaultCloudEpgFlags mCloudEpgFlags = new DefaultCloudEpgFlags();
    private final DefaultUiFlags mUiFlags = new DefaultUiFlags();
    private final DefaultConcurrentDvrPlaybackFlags mConcurrentDvrPlaybackFlags =
            new DefaultConcurrentDvrPlaybackFlags();
    private AccountHelper mAccountHelper;
    private Analytics mAnalytics;
    private Tracker mTracker;
    private ExperimentLoader mExperimentLoader;
    private PerformanceMonitor mPerformanceMonitor;

    @Override
    protected AndroidInjector<LiveTvApplication> applicationInjector() {
        return DaggerLiveTvApplicationComponent.builder()
                .applicationModule(new ApplicationModule(this))
                .tvSingletonsModule(new TvSingletonsModule(this))
                .build();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PERFORMANCE_MONITOR_MANAGER.getStartupMeasure().onAppCreate(this);
    }

    /** Returns the {@link AccountHelperImpl}. */
    @Override
    public AccountHelper getAccountHelper() {
        if (mAccountHelper == null) {
            mAccountHelper = new AccountHelperImpl(getApplicationContext());
        }
        return mAccountHelper;
    }

    @Override
    public synchronized PerformanceMonitor getPerformanceMonitor() {
        if (mPerformanceMonitor == null) {
            mPerformanceMonitor = PerformanceMonitorManagerFactory.create().initialize(this);
        }
        return mPerformanceMonitor;
    }

    @Override
    public Provider<EpgReader> providesEpgReader() {
        return mEpgReaderProvider;
    }

    @Override
    public ExperimentLoader getExperimentLoader() {
        mExperimentLoader = new ExperimentLoader();
        return mExperimentLoader;
    }

    @Override
    public DefaultBackendKnobsFlags getBackendKnobs() {
        return mBackendKnobsFlags;
    }

    /** Returns the {@link Analytics}. */
    @Override
    public synchronized Analytics getAnalytics() {
        if (mAnalytics == null) {
            mAnalytics = StubAnalytics.getInstance(this);
        }
        return mAnalytics;
    }

    /** Returns the default tracker. */
    @Override
    public synchronized Tracker getTracker() {
        if (mTracker == null) {
            mTracker = getAnalytics().getDefaultTracker();
        }
        return mTracker;
    }

    @Override
    public DefaultCloudEpgFlags getCloudEpgFlags() {
        return mCloudEpgFlags;
    }

    @Override
    public DefaultUiFlags getUiFlags() {
        return mUiFlags;
    }

    @Override
    public Optional<BuiltInTunerManager> getBuiltInTunerManager() {
        return Optional.absent();
    }

    @Override
    public BuildType getBuildType() {
        return BuildType.AOSP;
    }

    @Override
    public DefaultConcurrentDvrPlaybackFlags getConcurrentDvrPlaybackFlags() {
        return mConcurrentDvrPlaybackFlags;
    }

    @Override
    public TvSingletons singletons() {
        return this;
    }
}
