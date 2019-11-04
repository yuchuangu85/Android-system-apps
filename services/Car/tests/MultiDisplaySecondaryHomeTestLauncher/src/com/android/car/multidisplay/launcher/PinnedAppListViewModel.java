/**
 * Copyright (c) 2018 The Android Open Source Project
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

package com.android.car.multidisplay.launcher;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

/**
 * A view model that provides a list of activities that were pinned by user to always display on
 * home screen.
 * The pinned activities are stored in {@link SharedPreferences} to keep the sample simple :).
 */
public class PinnedAppListViewModel extends AndroidViewModel {

    static final String PINNED_APPS_KEY = "pinned_apps";

    private final PinnedAppListLiveData mLiveData;

    public PinnedAppListViewModel(Application application) {
        super(application);
        mLiveData = new PinnedAppListLiveData(application);
    }

    public LiveData<List<AppEntry>> getPinnedAppList() {
        return mLiveData;
    }
}
