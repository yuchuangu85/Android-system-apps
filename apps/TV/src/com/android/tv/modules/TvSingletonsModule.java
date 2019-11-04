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
package com.android.tv.modules;

import com.android.tv.TvSingletons;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.ProgramDataManager;
import dagger.Module;
import dagger.Provides;

/**
 * Provides bindings for items provided by {@link TvSingletons}.
 *
 * <p>Use this module to inject items directly instead of using {@code TvSingletons}.
 */
@Module
@SuppressWarnings("deprecation")
public class TvSingletonsModule {
    private final TvSingletons mTvSingletons;

    public TvSingletonsModule(TvSingletons mTvSingletons) {
        this.mTvSingletons = mTvSingletons;
    }

    @Provides
    ChannelDataManager providesChannelDataManager() {
        return mTvSingletons.getChannelDataManager();
    }

    @Provides
    ProgramDataManager providesProgramDataManager() {
        return mTvSingletons.getProgramDataManager();
    }
}
