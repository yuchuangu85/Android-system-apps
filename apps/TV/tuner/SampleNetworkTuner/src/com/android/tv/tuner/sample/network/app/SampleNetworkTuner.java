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

package com.android.tv.tuner.sample.network.app;

import android.content.ComponentName;
import android.media.tv.TvContract;
import com.android.tv.common.BaseApplication;
import com.android.tv.common.singletons.HasSingletons;
import com.android.tv.tuner.modules.TunerSingletonsModule;
import com.android.tv.tuner.sample.network.singletons.SampleNetworkSingletons;
import com.android.tv.tuner.sample.network.tvinput.SampleNetworkTunerTvInputService;
import com.android.tv.tuner.tvinput.factory.TunerSessionFactory;
import com.android.tv.tuner.tvinput.factory.TunerSessionFactoryImpl;
import dagger.android.AndroidInjector;
import com.android.tv.common.flags.CloudEpgFlags;
import com.android.tv.common.flags.ConcurrentDvrPlaybackFlags;
import javax.inject.Inject;

/** The top level application for Sample DVB Tuner. */
public class SampleNetworkTuner extends BaseApplication
        implements SampleNetworkSingletons, HasSingletons<SampleNetworkSingletons> {

    private String mEmbeddedInputId;
    @Inject CloudEpgFlags mCloudEpgFlags;
    @Inject ConcurrentDvrPlaybackFlags mConcurrentDvrPlaybackFlags;
    @Inject TunerSessionFactoryImpl mTunerSessionFactory;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    protected AndroidInjector<SampleNetworkTuner> applicationInjector() {
        return DaggerSampleNetworkTunerComponent.builder()
                .sampleNetworkTunerModule(new SampleNetworkTunerModule(this))
                .tunerSingletonsModule(new TunerSingletonsModule(this))
                .build();
    }

    @Override
    public synchronized String getEmbeddedTunerInputId() {
        if (mEmbeddedInputId == null) {
            mEmbeddedInputId =
                    TvContract.buildInputId(
                            new ComponentName(this, SampleNetworkTunerTvInputService.class));
        }
        return mEmbeddedInputId;
    }

    @Override
    public CloudEpgFlags getCloudEpgFlags() {
        return mCloudEpgFlags;
    }

    @Override
    public BuildType getBuildType() {
        return BuildType.ENG;
    }

    @Override
    public ConcurrentDvrPlaybackFlags getConcurrentDvrPlaybackFlags() {
        return mConcurrentDvrPlaybackFlags;
    }

    @Override
    public SampleNetworkSingletons singletons() {
        return this;
    }

    public TunerSessionFactory getTunerSessionFactory() {
        return mTunerSessionFactory;
    }
}
