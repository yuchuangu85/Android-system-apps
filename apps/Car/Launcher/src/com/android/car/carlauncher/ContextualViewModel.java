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

package com.android.car.carlauncher;

import android.app.Application;
import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.CarProjectionManager;
import android.car.CarProjectionManager.ProjectionStatusListener;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.UserManager;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Implementation {@link ViewModel} for {@link ContextualFragment}.
 *
 * Returns the first non-null {@link ContextualInfo} from a set of delegates.
 */
public class ContextualViewModel extends AndroidViewModel {
    private final MediatorLiveData<ContextualInfo> mContextualInfo = new MediatorLiveData<>();

    private final List<LiveData<ContextualInfo>> mInfoDelegates;

    public ContextualViewModel(Application application) {
        this(application, getCarProjectionManager(application));
    }

    private static CarProjectionManager getCarProjectionManager(Context context) {
        return (CarProjectionManager)
                Car.createCar(context).getCarManager(Car.PROJECTION_SERVICE);
    }

    @VisibleForTesting
    ContextualViewModel(Application application, CarProjectionManager carProjectionManager) {
        super(application);


        mInfoDelegates =
                Collections.unmodifiableList(Arrays.asList(
                        new ProjectionContextualInfoLiveData(application, carProjectionManager),
                        new WeatherContextualInfoLiveData(application)
                ));

        Observer<Object> observer = x -> updateLiveData();
        for (LiveData<ContextualInfo> delegate : mInfoDelegates) {
            mContextualInfo.addSource(delegate, observer);
        }
    }

    private void updateLiveData() {
        for (LiveData<ContextualInfo> delegate : mInfoDelegates) {
            ContextualInfo value = delegate.getValue();
            if (value != null) {
                mContextualInfo.setValue(value);
                return;
            }
        }

        mContextualInfo.setValue(null);
    }

    public LiveData<ContextualInfo> getContextualInfo() {
        return mContextualInfo;
    }
}
