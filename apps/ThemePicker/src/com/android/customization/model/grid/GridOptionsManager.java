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
package com.android.customization.model.grid;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.customization.model.CustomizationManager;
import com.android.customization.module.ThemesUserEventLogger;

import java.util.Collections;
import java.util.List;

/**
 * {@link CustomizationManager} for interfacing with the launcher to handle {@link GridOption}s.
 */
public class GridOptionsManager implements CustomizationManager<GridOption> {

    private final LauncherGridOptionsProvider mProvider;
    private final ThemesUserEventLogger mEventLogger;

    public GridOptionsManager(LauncherGridOptionsProvider provider, ThemesUserEventLogger logger) {
        mProvider = provider;
        mEventLogger = logger;
    }

    @Override
    public boolean isAvailable() {
        return mProvider.areGridsAvailable();
    }

    @Override
    public void apply(GridOption option, Callback callback) {
        int updated = mProvider.applyGrid(option.name);
        if (updated == 1) {
            mEventLogger.logGridApplied(option);
            callback.onSuccess();
        } else {
            callback.onError(null);
        }
    }

    @Override
    public void fetchOptions(OptionsFetchedListener<GridOption> callback, boolean reload) {
        new FetchTask(mProvider, callback).execute();
    }

    private static class FetchTask extends AsyncTask<Void, Void, List<GridOption>> {
        private final LauncherGridOptionsProvider mProvider;
        @Nullable private final OptionsFetchedListener<GridOption> mCallback;

        private FetchTask(@NonNull LauncherGridOptionsProvider provider,
                @Nullable OptionsFetchedListener<GridOption> callback) {
            mCallback = callback;
            mProvider = provider;
        }

        @Override
        protected List<GridOption> doInBackground(Void[] params) {
            return mProvider.fetch(false);
        }

        @Override
        protected void onPostExecute(List<GridOption> gridOptions) {
            if (mCallback != null) {
                if (gridOptions != null && !gridOptions.isEmpty()) {
                    mCallback.onOptionsLoaded(gridOptions);
                } else {
                    mCallback.onError(null);
                }
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            if (mCallback != null) {
                mCallback.onError(null);
            }
        }
    }
}
