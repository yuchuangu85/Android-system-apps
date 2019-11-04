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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;
import android.telecom.Call;
import android.telecom.DisconnectCause;
import android.telecom.GatewayInfo;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.android.car.dialer.CarDialerRobolectricTestRunner;
import com.android.car.dialer.LiveDataObserver;
import com.android.car.telephony.common.CallDetail;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(CarDialerRobolectricTestRunner.class)
public class CallDetailLiveDataTest {

    private CallDetailLiveData mCallDetailLiveData;
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

        mCallDetailLiveData = new CallDetailLiveData(mMockCall);
        mLifecycleRegistry = new LifecycleRegistry(mMockLifecycleOwner);
        when(mMockLifecycleOwner.getLifecycle()).thenReturn(mLifecycleRegistry);
    }

    @Test
    public void testOnActiveRegistry() {
        mCallDetailLiveData.onActive();

        verify(mMockCall).registerCallback(any());
    }

    @Test
    public void testOnLifecycleStart() {
        mCallDetailLiveData.observe(mMockLifecycleOwner, (value) -> mMockObserver.onChanged(value));
        verify(mMockObserver, never()).onChanged(any());
        assertThat(mCallDetailLiveData.hasObservers()).isTrue();
        assertThat(mCallDetailLiveData.hasActiveObservers()).isFalse();

        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
        assertThat(mCallDetailLiveData.hasActiveObservers()).isTrue();
        verify(mMockObserver).onChanged(any());
        verify(mMockCall).registerCallback(any());
    }

    @Test
    public void testOnDetailsChanged() {
        ArgumentCaptor<CallDetail> valueCaptor = ArgumentCaptor.forClass(CallDetail.class);
        doNothing().when(mMockObserver).onChanged(valueCaptor.capture());
        startObserving();

        // Set up updated details
        String number = "6505551234";
        Uri uri = Uri.fromParts("tel", number, null);
        GatewayInfo gatewayInfo = new GatewayInfo("", uri, uri);
        CharSequence label = "DisconnectCause";
        DisconnectCause disconnectCause = new DisconnectCause(1, label, null, "");
        long connectTimeMillis = 5000;
        Call.Details updatedDetails = mock(Call.Details.class);
        when(updatedDetails.getHandle()).thenReturn(uri);
        when(updatedDetails.getDisconnectCause()).thenReturn(disconnectCause);
        when(updatedDetails.getGatewayInfo()).thenReturn(gatewayInfo);
        when(updatedDetails.getConnectTimeMillis()).thenReturn(connectTimeMillis);

        when(mMockCall.getDetails()).thenReturn(updatedDetails);
        mCallbackCaptor.getValue().onDetailsChanged(mMockCall, updatedDetails);

        CallDetail observedValue = valueCaptor.getValue();
        assertThat(observedValue.getNumber()).isEqualTo(number);
        assertThat(observedValue.getConnectTimeMillis()).isEqualTo(connectTimeMillis);
        assertThat(observedValue.getDisconnectCause()).isEqualTo(label);
        assertThat(observedValue.getGatewayInfoOriginalAddress()).isEqualTo(uri);
    }

    @Test
    public void testOnInactiveUnregister() {
        startObserving();
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);

        verify(mMockCall).unregisterCallback(mCallbackCaptor.getValue());
        assertThat(mCallDetailLiveData.hasObservers()).isFalse();
        assertThat(mCallDetailLiveData.hasActiveObservers()).isFalse();
    }

    private void startObserving() {
        mCallDetailLiveData.observe(mMockLifecycleOwner, (value) -> mMockObserver.onChanged(value));
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
    }
}
