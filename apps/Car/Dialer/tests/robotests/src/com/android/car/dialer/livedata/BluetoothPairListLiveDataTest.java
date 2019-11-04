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
import static org.robolectric.Shadows.shadowOf;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.android.car.dialer.CarDialerRobolectricTestRunner;
import com.android.car.dialer.LiveDataObserver;
import com.android.car.dialer.testutils.BroadcastReceiverVerifier;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowBluetoothAdapter;

import java.util.HashSet;
import java.util.Set;

@RunWith(CarDialerRobolectricTestRunner.class)
public class BluetoothPairListLiveDataTest {
    private static final String INTENT_ACTION = BluetoothDevice.ACTION_BOND_STATE_CHANGED;
    private static final String BLUETOOTH_DEVICE_ALIAS_1 = "BluetoothDevice 1";
    private static final String BLUETOOTH_DEVICE_ALIAS_2 = "BluetoothDevice 2";

    private BluetoothPairListLiveData mBluetoothPairListLiveData;
    private LifecycleRegistry mLifecycleRegistry;
    private BroadcastReceiverVerifier mReceiverVerifier;
    @Mock
    private LifecycleOwner mMockLifecycleOwner;
    @Mock
    private LiveDataObserver<Set<BluetoothDevice>> mMockObserver;
    @Captor
    private ArgumentCaptor<Set<BluetoothDevice>> mValueCaptor;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mBluetoothPairListLiveData = new BluetoothPairListLiveData(RuntimeEnvironment.application);
        mLifecycleRegistry = new LifecycleRegistry(mMockLifecycleOwner);
        when(mMockLifecycleOwner.getLifecycle()).thenReturn(mLifecycleRegistry);

        mReceiverVerifier = new BroadcastReceiverVerifier(RuntimeEnvironment.application);
    }

    @Test
    public void testOnActive() {
        mBluetoothPairListLiveData.observe(mMockLifecycleOwner,
                (value) -> mMockObserver.onChanged(value));
        verify(mMockObserver, never()).onChanged(any());

        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
        mReceiverVerifier.verifyReceiverRegistered(INTENT_ACTION);
        verify(mMockObserver).onChanged(any());
    }

    @Test
    public void testOnBluetoothConnected() {
        // Set up Bluetooth devices
        BluetoothDevice bluetoothDevice1 = mock(BluetoothDevice.class);
        bluetoothDevice1.setAlias(BLUETOOTH_DEVICE_ALIAS_1);
        Set<BluetoothDevice> bondedDevices = new HashSet<BluetoothDevice>();
        bondedDevices.add(bluetoothDevice1);
        ShadowBluetoothAdapter shadowBluetoothAdapter = shadowOf(
                BluetoothAdapter.getDefaultAdapter());
        shadowBluetoothAdapter.setBondedDevices(bondedDevices);

        doNothing().when(mMockObserver).onChanged(mValueCaptor.capture());
        mBluetoothPairListLiveData.observe(mMockLifecycleOwner,
                (value) -> mMockObserver.onChanged(value));
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
        verifyBondedDevices(bondedDevices);

        // Update Bluetooth devices
        BluetoothDevice bluetoothDevice2 = mock(BluetoothDevice.class);
        bluetoothDevice2.setAlias(BLUETOOTH_DEVICE_ALIAS_2);
        bondedDevices.add(bluetoothDevice2);
        shadowBluetoothAdapter.setBondedDevices(bondedDevices);

        mReceiverVerifier.getBroadcastReceiverFor(INTENT_ACTION)
                .onReceive(mock(Context.class), mock(Intent.class));
        verifyBondedDevices(bondedDevices);
    }

    @Test
    public void testOnInactiveUnregister() {
        mBluetoothPairListLiveData.observe(mMockLifecycleOwner,
                value -> mMockObserver.onChanged(value));
        int preNumber = mReceiverVerifier.getReceiverNumber();

        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);

        assertThat(mReceiverVerifier.getReceiverNumber()).isEqualTo(preNumber);
    }

    private void verifyBondedDevices(Set bondedDevices) {
        // Verify Bonded Devices for BluetoothAdapter
        assertThat(BluetoothAdapter.getDefaultAdapter().getBondedDevices().containsAll(
                bondedDevices)).isTrue();
        assertThat(BluetoothAdapter.getDefaultAdapter().getBondedDevices().size()).isEqualTo(
                bondedDevices.size());
        // Verify Bonded Devices for LiveData
        assertThat(mBluetoothPairListLiveData.getValue().containsAll(bondedDevices)).isTrue();
        assertThat(mBluetoothPairListLiveData.getValue().size()).isEqualTo(bondedDevices.size());
        // Verify Bonded Devices for Observer
        assertThat(mValueCaptor.getValue().containsAll(bondedDevices)).isTrue();
        assertThat(mValueCaptor.getValue().size()).isEqualTo(bondedDevices.size());
    }
}
