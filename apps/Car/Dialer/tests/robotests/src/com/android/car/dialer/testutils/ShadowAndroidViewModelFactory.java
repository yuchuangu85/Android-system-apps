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

package com.android.car.dialer.testutils;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.HashMap;
import java.util.Map;

/**
 * Shadow class for {@link ViewModelProvider}.
 */
@Implements(ViewModelProvider.AndroidViewModelFactory.class)
public class ShadowAndroidViewModelFactory {

    private static final Map<Class, ViewModel> VIEW_MODEL_MAP = new HashMap<>();

    /**
     * Adds class and view model pairs to the map.
     */
    public static <T extends ViewModel> void add(Class<T> modelClass, T viewModel) {
        VIEW_MODEL_MAP.put(modelClass, viewModel);
    }

    /**
     * Returns a ViewModel from the map.
     */
    @Implementation
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) VIEW_MODEL_MAP.get(modelClass);
    }
}
