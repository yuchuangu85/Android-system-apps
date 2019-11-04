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
package com.android.tv.common.dagger;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Looper;
import com.android.tv.common.dagger.annotations.ApplicationContext;
import com.android.tv.common.dagger.annotations.MainLooper;
import dagger.Module;
import dagger.Provides;

/**
 * Provides application-scope qualifiers for the {@link Application}, the application context, and
 * the application's main looper.
 */
@Module
public final class ApplicationModule {
    private final Application mApplication;

    public ApplicationModule(Application application) {
        mApplication = application;
    }

    @Provides
    Application provideApplication() {
        return mApplication;
    }

    @Provides
    @ApplicationContext
    Context provideContext() {
        return mApplication.getApplicationContext();
    }

    @Provides
    @MainLooper
    static Looper provideMainLooper() {
        return Looper.getMainLooper();
    }

    @Provides
    ContentResolver provideContentResolver() {
        return mApplication.getContentResolver();
    }
}
