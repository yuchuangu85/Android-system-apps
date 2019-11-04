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
 * A view model that provides a list of activities that can be launched.
 */
public class AppListViewModel extends AndroidViewModel {

    private final AppListLiveData mLiveData;
    private final PackageIntentReceiver mPackageIntentReceiver;

    public AppListViewModel(Application application) {
        super(application);
        mLiveData = new AppListLiveData(application);
        mPackageIntentReceiver = new PackageIntentReceiver(mLiveData, application);
    }

    public LiveData<List<AppEntry>> getAppList() {
        return mLiveData;
    }

    protected void onCleared() {
        getApplication().unregisterReceiver(mPackageIntentReceiver);
    }
}
