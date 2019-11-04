package com.android.car.messenger.bluetooth;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothMapClient;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class BluetoothMonitorTest {

    @Mock
    BluetoothMapClient mockMapClient;
    @Mock
    BluetoothMonitor.OnBluetoothEventListener mockBluetoothEventListener;

    private Context mContext;
    private BluetoothMonitor mBluetoothMonitor;
    private BluetoothProfile.ServiceListener mServiceListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mBluetoothMonitor = new BluetoothMonitor(mContext);
        mServiceListener = mBluetoothMonitor.getServiceListener();
        mBluetoothMonitor.registerListener(mockBluetoothEventListener);
    }

    @Test
    public void testServiceListener() {
        mServiceListener.onServiceConnected(BluetoothProfile.MAP_CLIENT, mockMapClient);
        verify(mockBluetoothEventListener).onMapConnected(mockMapClient);

        mServiceListener.onServiceDisconnected(BluetoothProfile.MAP_CLIENT);
        verify(mockBluetoothEventListener).onMapDisconnected(BluetoothProfile.MAP_CLIENT);
    }

    @Test
    public void testRegisterListener() {
        assertThat(mBluetoothMonitor.registerListener(mockBluetoothEventListener)).isFalse();
        assertThat(mBluetoothMonitor.unregisterListener(mockBluetoothEventListener)).isTrue();
        assertThat(mBluetoothMonitor.registerListener(mockBluetoothEventListener)).isTrue();
        mBluetoothMonitor.cleanup();
        assertThat(mBluetoothMonitor.registerListener(mockBluetoothEventListener)).isTrue();
    }
}
