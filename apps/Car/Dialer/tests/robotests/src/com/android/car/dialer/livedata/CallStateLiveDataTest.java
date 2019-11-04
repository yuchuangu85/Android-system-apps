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

package com.android.car.dialer.livedata;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.telecom.Call;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.android.car.dialer.CarDialerRobolectricTestRunner;
import com.android.car.dialer.LiveDataObserver;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(CarDialerRobolectricTestRunner.class)
public class CallStateLiveDataTest {

    private CallStateLiveData mCallStateLiveData;
    private LifecycleRegistry mLifecycleRegistry;
    @Mock
    private Call mMockCall;
    @Mock
    private LifecycleOwner mMockLifecycleOwner;
    @Mock
    private LiveDataObserver<Integer> mMockObserver;
    @Captor
    private ArgumentCaptor<Call.Callback> mCallbackCaptor;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        doNothing().when(mMockCall).registerCallback(mCallbackCaptor.capture());
        when(mMockCall.getState()).thenReturn(Call.STATE_NEW);

        mCallStateLiveData = new CallStateLiveData(mMockCall);
        mLifecycleRegistry = new LifecycleRegistry(mMockLifecycleOwner);
        when(mMockLifecycleOwner.getLifecycle()).thenReturn(mLifecycleRegistry);
    }

    @Test
    public void testOnActiveRegistry() {
        mCallStateLiveData.onActive();

        verify(mMockCall).registerCallback(any());
    }

    @Test
    public void testOnLifecycleStart() {
        mCallStateLiveData.observe(mMockLifecycleOwner, (value) -> mMockObserver.onChanged(value));
        verify(mMockObserver, never()).onChanged(any());
        assertThat(mCallStateLiveData.hasObservers()).isTrue();
        assertThat(mCallStateLiveData.hasActiveObservers()).isFalse();

        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
        verify(mMockObserver).onChanged(any());
        assertThat(mCallStateLiveData.hasActiveObservers()).isTrue();
        verify(mMockCall).registerCallback(any());
    }

    @Test
    public void testOnStateChanged() {
        ArgumentCaptor<Integer> valueCaptor = ArgumentCaptor.forClass(Integer.class);
        doNothing().when(mMockObserver).onChanged(valueCaptor.capture());

        mCallStateLiveData.observe(mMockLifecycleOwner, (value) -> mMockObserver.onChanged(value));
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(valueCaptor.getValue()).isEqualTo(Call.STATE_NEW);
        mCallbackCaptor.getValue().onStateChanged(mMockCall, Call.STATE_ACTIVE);
        assertThat(valueCaptor.getValue()).isEqualTo(Call.STATE_ACTIVE);
    }

    @Test
    public void testOnInactiveUnregister() {
        mCallStateLiveData.observe(mMockLifecycleOwner, (value) -> mMockObserver.onChanged(value));
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);

        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);

        verify(mMockCall).unregisterCallback(mCallbackCaptor.getValue());
        assertThat(mCallStateLiveData.hasObservers()).isFalse();
        assertThat(mCallStateLiveData.hasActiveObservers()).isFalse();
    }
}
