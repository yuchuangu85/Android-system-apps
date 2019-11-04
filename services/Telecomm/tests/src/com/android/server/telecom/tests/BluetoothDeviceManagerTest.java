/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.os.Parcel;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.telecom.BluetoothAdapterProxy;
import com.android.server.telecom.BluetoothHeadsetProxy;
import com.android.server.telecom.bluetooth.BluetoothDeviceManager;
import com.android.server.telecom.bluetooth.BluetoothRouteManager;
import com.android.server.telecom.bluetooth.BluetoothStateReceiver;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

@RunWith(JUnit4.class)
public class BluetoothDeviceManagerTest extends TelecomTestCase {
    @Mock BluetoothRouteManager mRouteManager;
    @Mock BluetoothHeadsetProxy mHeadsetProxy;
    @Mock BluetoothAdapterProxy mAdapterProxy;
    @Mock BluetoothHearingAid mBluetoothHearingAid;

    BluetoothDeviceManager mBluetoothDeviceManager;
    BluetoothProfile.ServiceListener serviceListenerUnderTest;
    BroadcastReceiver receiverUnderTest;

    private BluetoothDevice device1;
    private BluetoothDevice device2;
    private BluetoothDevice device3;
    private BluetoothDevice device4;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        device1 = makeBluetoothDevice("00:00:00:00:00:01");
        // hearing aid
        device2 = makeBluetoothDevice("00:00:00:00:00:02");
        device3 = makeBluetoothDevice("00:00:00:00:00:03");
        // hearing aid
        device4 = makeBluetoothDevice("00:00:00:00:00:04");

        when(mBluetoothHearingAid.getHiSyncId(device2)).thenReturn(100L);
        when(mBluetoothHearingAid.getHiSyncId(device4)).thenReturn(100L);

        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();
        mBluetoothDeviceManager = new BluetoothDeviceManager(mContext, mAdapterProxy);
        mBluetoothDeviceManager.setBluetoothRouteManager(mRouteManager);

        ArgumentCaptor<BluetoothProfile.ServiceListener> serviceCaptor =
                ArgumentCaptor.forClass(BluetoothProfile.ServiceListener.class);
        verify(mAdapterProxy).getProfileProxy(eq(mContext),
                serviceCaptor.capture(), eq(BluetoothProfile.HEADSET));
        serviceListenerUnderTest = serviceCaptor.getValue();

        receiverUnderTest = new BluetoothStateReceiver(mBluetoothDeviceManager,
                null /* route mgr not needed here */);

        mBluetoothDeviceManager.setHeadsetServiceForTesting(mHeadsetProxy);
        mBluetoothDeviceManager.setHearingAidServiceForTesting(mBluetoothHearingAid);
    }

    @SmallTest
    @Test
    public void testSingleDeviceConnectAndDisconnect() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1, false));
        assertEquals(1, mBluetoothDeviceManager.getNumConnectedDevices());
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device1, false));
        assertEquals(0, mBluetoothDeviceManager.getNumConnectedDevices());
    }

    @SmallTest
    @Test
    public void testAddDeviceFailsWhenServicesAreNull() {
        mBluetoothDeviceManager.setHeadsetServiceForTesting(null);
        mBluetoothDeviceManager.setHearingAidServiceForTesting(null);

        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1, false));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2, true));

        assertEquals(0, mBluetoothDeviceManager.getNumConnectedDevices());
    }
    
    @SmallTest
    @Test
    public void testMultiDeviceConnectAndDisconnect() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1, false));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2, true));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device1, false));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device3, false));
        assertEquals(2, mBluetoothDeviceManager.getNumConnectedDevices());
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device3, false));
        assertEquals(1, mBluetoothDeviceManager.getNumConnectedDevices());
    }

    @SmallTest
    @Test
    public void testHearingAidDedup() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1, false));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2, true));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device4, true));
        assertEquals(3, mBluetoothDeviceManager.getNumConnectedDevices());
        assertEquals(2, mBluetoothDeviceManager.getUniqueConnectedDevices().size());
    }

    @SmallTest
    @Test
    public void testHeadsetServiceDisconnect() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1, false));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device3, false));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2, true));
        serviceListenerUnderTest.onServiceDisconnected(BluetoothProfile.HEADSET);

        verify(mRouteManager).onActiveDeviceChanged(isNull(), eq(false));
        verify(mRouteManager).onDeviceLost(device1.getAddress());
        verify(mRouteManager).onDeviceLost(device3.getAddress());
        verify(mRouteManager, never()).onDeviceLost(device2.getAddress());
        assertNull(mBluetoothDeviceManager.getHeadsetService());
        assertEquals(1, mBluetoothDeviceManager.getNumConnectedDevices());
    }

    @SmallTest
    @Test
    public void testHearingAidServiceDisconnect() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1, false));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device3, false));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2, true));
        serviceListenerUnderTest.onServiceDisconnected(BluetoothProfile.HEARING_AID);

        verify(mRouteManager).onActiveDeviceChanged(isNull(), eq(true));
        verify(mRouteManager).onDeviceLost(device2.getAddress());
        verify(mRouteManager, never()).onDeviceLost(device1.getAddress());
        verify(mRouteManager, never()).onDeviceLost(device3.getAddress());
        assertNull(mBluetoothDeviceManager.getHearingAidService());
        assertEquals(2, mBluetoothDeviceManager.getNumConnectedDevices());
    }

    @SmallTest
    @Test
    public void testConnectDisconnectAudioHeadset() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1, false));
        when(mHeadsetProxy.setActiveDevice(nullable(BluetoothDevice.class))).thenReturn(true);
        mBluetoothDeviceManager.connectAudio(device1.getAddress());
        verify(mHeadsetProxy).setActiveDevice(device1);
        verify(mHeadsetProxy).connectAudio();
        verify(mBluetoothHearingAid, never()).setActiveDevice(nullable(BluetoothDevice.class));

        mBluetoothDeviceManager.disconnectAudio();
        verify(mHeadsetProxy).disconnectAudio();
    }

    @SmallTest
    @Test
    public void testConnectDisconnectAudioHearingAid() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2, true));
        mBluetoothDeviceManager.connectAudio(device2.getAddress());
        verify(mBluetoothHearingAid).setActiveDevice(device2);
        verify(mHeadsetProxy, never()).connectAudio();
        verify(mHeadsetProxy, never()).setActiveDevice(nullable(BluetoothDevice.class));

        when(mBluetoothHearingAid.getActiveDevices()).thenReturn(Arrays.asList(device2, null));

        mBluetoothDeviceManager.disconnectAudio();
        verify(mBluetoothHearingAid).setActiveDevice(null);
        verify(mHeadsetProxy).disconnectAudio();
    }

    private Intent buildConnectionActionIntent(int state, BluetoothDevice device,
            boolean isHearingAid) {
        Intent i = new Intent(isHearingAid
                ? BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED
                : BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        i.putExtra(BluetoothHeadset.EXTRA_STATE, state);
        i.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        return i;
    }

    private BluetoothDevice makeBluetoothDevice(String address) {
        Parcel p1 = Parcel.obtain();
        p1.writeString(address);
        p1.setDataPosition(0);
        BluetoothDevice device = BluetoothDevice.CREATOR.createFromParcel(p1);
        p1.recycle();
        return device;
    }
}
