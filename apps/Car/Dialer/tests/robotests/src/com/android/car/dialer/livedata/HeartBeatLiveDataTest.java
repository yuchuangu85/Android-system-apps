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
package com.android.car.dialer.livedata;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.text.format.DateUtils;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.android.car.dialer.LiveDataObserver;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
public class HeartBeatLiveDataTest {

    private HeartBeatLiveData mHeartBeatLiveData;

    private LifecycleRegistry mLifecycleRegistry;
    private LifecycleOwner mLifecycleOwner;

    @Before
    public void setup() {
        mHeartBeatLiveData = new HeartBeatLiveData(DateUtils.SECOND_IN_MILLIS);
        mLifecycleOwner = mock(LifecycleOwner.class);
        mLifecycleRegistry = new LifecycleRegistry(mLifecycleOwner);
        when(mLifecycleOwner.getLifecycle()).thenReturn(mLifecycleRegistry);
    }

    @Test
    public void active_onLifecycleStart() {
        LiveDataObserver<HeartBeatLiveData> mockObserver = mock(LiveDataObserver.class);
        mHeartBeatLiveData.observe(mLifecycleOwner, (value) -> mockObserver.onChanged(value));
        verify(mockObserver, never()).onChanged(any());

        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
        ShadowLooper.runUiThreadTasks();

        verify(mockObserver).onChanged(any());
    }
}
