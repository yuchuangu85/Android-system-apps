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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.android.car.dialer.CarDialerRobolectricTestRunner;
import com.android.car.dialer.LiveDataObserver;
import com.android.car.dialer.testutils.BroadcastReceiverVerifier;
import com.android.car.dialer.testutils.ShadowBluetoothAdapterForDialer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

@RunWith(CarDialerRobolectricTestRunner.class)
@Config(shadows = ShadowBluetoothAdapterForDialer.class)
public class BluetoothHfpStateLiveDataTest {
    private static final String INTENT_ACTION =
            BluetoothHeadsetClient.ACTION_CONNECTION_STATE_CHANGED;

    private BluetoothHfpStateLiveData mBluetoothHfpStateLiveData;
    private LifecycleRegistry mLifecycleRegistry;
    private BroadcastReceiverVerifier mReceiverVerifier;
    @Mock
    private LifecycleOwner mMockLifecycleOwner;
    @Mock
    private LiveDataObserver<Integer> mMockObserver;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mBluetoothHfpStateLiveData = new BluetoothHfpStateLiveData(RuntimeEnvironment.application);
        mLifecycleRegistry = new LifecycleRegistry(mMockLifecycleOwner);
        when(mMockLifecycleOwner.getLifecycle()).thenReturn(mLifecycleRegistry);

        mReceiverVerifier = new BroadcastReceiverVerifier(RuntimeEnvironment.application);
    }

    @Test
    public void testOnActive() {
        mBluetoothHfpStateLiveData.observe(mMockLifecycleOwner,
                (value) -> mMockObserver.onChanged(value));
        verify(mMockObserver, never()).onChanged(any());

        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
        mReceiverVerifier.verifyReceiverRegistered(INTENT_ACTION);
        verify(mMockObserver).onChanged(any());
    }

    @Test
    public void testOnBluetoothHfpStateChange() {
        ArgumentCaptor<Integer> valueCaptor = ArgumentCaptor.forClass(Integer.class);
        doNothing().when(mMockObserver).onChanged(valueCaptor.capture());

        ShadowBluetoothAdapterForDialer shadowBluetoothAdapter =
                (ShadowBluetoothAdapterForDialer) Shadow.extract(
                        BluetoothAdapter.getDefaultAdapter());
        shadowBluetoothAdapter.setState(BluetoothAdapter.STATE_ON);
        shadowBluetoothAdapter.setProfileConnectionState(BluetoothProfile.HEADSET_CLIENT,
                BluetoothProfile.STATE_CONNECTED);

        mBluetoothHfpStateLiveData.observe(mMockLifecycleOwner,
                (value) -> mMockObserver.onChanged(value));
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
        assertThat(BluetoothAdapter.getDefaultAdapter().getProfileConnectionState(
                BluetoothProfile.HEADSET_CLIENT)).isEqualTo(BluetoothProfile.STATE_CONNECTED);
        assertThat(mBluetoothHfpStateLiveData.getValue()).isEqualTo(
                BluetoothProfile.STATE_CONNECTED);
        assertThat(valueCaptor.getValue()).isEqualTo(BluetoothProfile.STATE_CONNECTED);

        shadowBluetoothAdapter.setProfileConnectionState(BluetoothProfile.HEADSET_CLIENT,
                BluetoothProfile.STATE_DISCONNECTED);
        mReceiverVerifier.getBroadcastReceiverFor(INTENT_ACTION)
                .onReceive(mock(Context.class), mock(Intent.class));
        assertThat(BluetoothAdapter.getDefaultAdapter().getProfileConnectionState(
                BluetoothProfile.HEADSET_CLIENT)).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mBluetoothHfpStateLiveData.getValue()).isEqualTo(
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(valueCaptor.getValue()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    @Test
    public void testOnInactiveUnregister() {
        mBluetoothHfpStateLiveData.observe(mMockLifecycleOwner,
                (value) -> mMockObserver.onChanged(value));
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
        int preNumber = mReceiverVerifier.getReceiverNumber();

        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
        mReceiverVerifier.verifyReceiverUnregistered(INTENT_ACTION, preNumber);
    }
}
