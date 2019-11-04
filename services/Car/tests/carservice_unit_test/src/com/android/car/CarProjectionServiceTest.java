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

package com.android.car;

import static android.car.projection.ProjectionStatus.PROJECTION_STATE_ACTIVE_FOREGROUND;
import static android.car.projection.ProjectionStatus.PROJECTION_STATE_INACTIVE;
import static android.car.projection.ProjectionStatus.PROJECTION_TRANSPORT_USB;
import static android.car.projection.ProjectionStatus.PROJECTION_TRANSPORT_WIFI;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.CarProjectionManager;
import android.car.ICarProjectionKeyEventHandler;
import android.car.ICarProjectionStatusListener;
import android.car.projection.ProjectionOptions;
import android.car.projection.ProjectionStatus;
import android.car.projection.ProjectionStatus.MobileDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.net.wifi.WifiScanner;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class CarProjectionServiceTest {
    private static final int MD_ID1 = 1;
    private static final int MD_ID2 = 2;
    private static final String MD_NAME1 = "Device1";
    private static final String MD_NAME2 = "Device2";
    private static final int DEFAULT_TIMEOUT_MS = 1000;
    private static final String MD_EXTRA_KEY = "com.some.key.md";
    private static final String MD_EXTRA_VALUE = "this is dummy value";
    private static final String STATUS_EXTRA_KEY = "com.some.key.status";
    private static final String STATUS_EXTRA_VALUE = "additional status value";

    private final IBinder mToken = new Binder();

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    private CarProjectionService mService;

    @Spy
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Mock
    private Resources mResources;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    @Mock
    private CarInputService mCarInputService;
    @Mock
    private CarBluetoothService mCarBluetoothService;

    @Mock
    WifiScanner mWifiScanner;

    @Before
    public void setUp() {
        mService = new CarProjectionService(mContext, mHandler, mCarInputService,
                mCarBluetoothService);
    }

    @Test
    public void updateProjectionStatus_defaultState() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);

        mService.registerProjectionStatusListener(new ICarProjectionStatusListener.Stub() {
            @Override
            public void onProjectionStatusChanged(int projectionState,
                    String activeProjectionPackageName, List<ProjectionStatus> details) {
                assertThat(projectionState).isEqualTo(PROJECTION_STATE_INACTIVE);
                assertThat(activeProjectionPackageName).isNull();
                assertThat(details).isEmpty();

                latch.countDown();
            }
        });

        latch.await(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @Test
    public void updateProjectionStatus_subscribeAfterUpdate() throws Exception {
        final ProjectionStatus status = createProjectionStatus();
        mService.updateProjectionStatus(status, mToken);

        final CountDownLatch latch = new CountDownLatch(1);

        mService.registerProjectionStatusListener(new ICarProjectionStatusListener.Stub() {
            @Override
            public void onProjectionStatusChanged(int projectionState,
                    String activeProjectionPackageName, List<ProjectionStatus> details) {
                assertThat(projectionState).isEqualTo(PROJECTION_STATE_ACTIVE_FOREGROUND);
                assertThat(activeProjectionPackageName).isEqualTo(mContext.getPackageName());
                assertThat(details).hasSize(1);
                assertThat(details.get(0)).isEqualTo(status);
                ProjectionStatus status = details.get(0);
                assertThat(status.getTransport()).isEqualTo(PROJECTION_TRANSPORT_WIFI);
                assertThat(status.getExtras()).isNotNull();
                assertThat(status.getExtras().getString(STATUS_EXTRA_KEY))
                        .isEqualTo(STATUS_EXTRA_VALUE);
                assertThat(status.getConnectedMobileDevices()).hasSize(2);
                MobileDevice md1 = status.getConnectedMobileDevices().get(0);
                assertThat(md1.getId()).isEqualTo(MD_ID1);
                assertThat(md1.getName()).isEqualTo(MD_NAME1);
                assertThat(md1.getExtras()).isNotNull();
                assertThat(md1.getExtras().getString(MD_EXTRA_KEY)).isEqualTo(MD_EXTRA_VALUE);
                assertThat(md1.getAvailableTransports()).hasSize(1);
                assertThat(md1.getAvailableTransports()).containsExactly(
                        PROJECTION_TRANSPORT_USB);

                MobileDevice md2 = status.getConnectedMobileDevices().get(1);
                assertThat(md2.getId()).isEqualTo(MD_ID2);
                assertThat(md2.getName()).isEqualTo(MD_NAME2);
                assertThat(md2.getExtras()).isNotNull();
                assertThat(md2.getExtras().isEmpty()).isTrue();
                assertThat(md2.getAvailableTransports()).containsExactly(
                        PROJECTION_TRANSPORT_USB, PROJECTION_TRANSPORT_WIFI);

                latch.countDown();
            }
        });

        latch.await(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @Test
    public void updateProjectionStatus_subscribeBeforeUpdate() throws Exception {

        // We will receive notification twice - with default value and with updated one.
        final CountDownLatch latch = new CountDownLatch(2);

        mService.registerProjectionStatusListener(new ICarProjectionStatusListener.Stub() {
            @Override
            public void onProjectionStatusChanged(int projectionState,
                    String activeProjectionPackageName, List<ProjectionStatus> details) {
                if (latch.getCount() == 2) {
                    assertThat(projectionState).isEqualTo(PROJECTION_STATE_INACTIVE);
                    assertThat(activeProjectionPackageName).isNull();
                } else {
                    assertThat(projectionState).isEqualTo(PROJECTION_STATE_ACTIVE_FOREGROUND);
                    assertThat(activeProjectionPackageName).isEqualTo(mContext.getPackageName());
                }

                latch.countDown();
            }
        });
        mService.updateProjectionStatus(createProjectionStatus(), mToken);

        latch.await(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @Test
    public void getProjectionOptions_defaults() {
        when(mContext.getResources()).thenReturn(mResources);
        final int uiMode = ProjectionOptions.UI_MODE_FULL_SCREEN;

        when(mResources.getInteger(com.android.car.R.integer.config_projectionUiMode))
                .thenReturn(uiMode);
        when(mResources.getString(com.android.car.R.string.config_projectionConsentActivity))
                .thenReturn("");
        when(mResources.getInteger(com.android.car.R.integer.config_projectionActivityDisplayId))
                .thenReturn(-1);
        when(mResources.getIntArray(com.android.car.R.array.config_projectionActivityLaunchBounds))
                .thenReturn(new int[0]);

        Bundle bundle = mService.getProjectionOptions();

        ProjectionOptions options = new ProjectionOptions(bundle);
        assertThat(options.getActivityOptions()).isNull();
        assertThat(options.getConsentActivity()).isNull();
        assertThat(options.getUiMode()).isEqualTo(uiMode);
    }

    @Test
    public void getProjectionOptions_nonDefaults() {
        when(mContext.getResources()).thenReturn(mResources);
        final int uiMode = ProjectionOptions.UI_MODE_BLENDED;
        final String consentActivity = "com.my.app/.MyActivity";
        final int[] bounds = new int[] {1, 2, 3, 4};
        final int displayId = 1;

        when(mResources.getInteger(com.android.car.R.integer.config_projectionUiMode))
                .thenReturn(uiMode);
        when(mResources.getString(com.android.car.R.string.config_projectionConsentActivity))
                .thenReturn(consentActivity);
        when(mResources.getInteger(com.android.car.R.integer.config_projectionActivityDisplayId))
                .thenReturn(displayId);
        when(mResources.getIntArray(com.android.car.R.array.config_projectionActivityLaunchBounds))
                .thenReturn(bounds);

        Bundle bundle = mService.getProjectionOptions();

        ProjectionOptions options = new ProjectionOptions(bundle);
        assertThat(options.getActivityOptions().getLaunchDisplayId()).isEqualTo(displayId);
        assertThat(options.getActivityOptions().getLaunchBounds())
                .isEqualTo(new Rect(bounds[0], bounds[1], bounds[2], bounds[3]));
        assertThat(options.getConsentActivity()).isEqualTo(
                ComponentName.unflattenFromString(consentActivity));
        assertThat(options.getUiMode()).isEqualTo(uiMode);
    }

    @Test
    public void getWifiChannels() {
        List<Integer> expectedWifiChannels = Arrays.asList(2400, 5600);
        when(mWifiScanner.getAvailableChannels(anyInt())).thenReturn(expectedWifiChannels);
        when(mContext.getSystemService(WifiScanner.class)).thenReturn(mWifiScanner);

        int[] wifiChannels = mService.getAvailableWifiChannels(WifiScanner.WIFI_BAND_BOTH_WITH_DFS);
        assertThat(wifiChannels).isNotNull();
        assertThat(wifiChannels).asList().containsExactlyElementsIn(expectedWifiChannels);
    }

    @Test
    public void addedKeyEventHandler_getsDispatchedEvents() throws RemoteException {
        ICarProjectionKeyEventHandler eventHandler = createMockKeyEventHandler();

        BitSet eventSet = bitSetOf(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP);
        mService.registerKeyEventHandler(eventHandler, eventSet.toByteArray());

        mService.onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP);
        verify(eventHandler)
                .onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP);
    }

    @Test
    public void addedKeyEventHandler_registersWithCarInputService() throws RemoteException {
        ICarProjectionKeyEventHandler eventHandler1 = createMockKeyEventHandler();
        ICarProjectionKeyEventHandler eventHandler2 = createMockKeyEventHandler();
        InOrder inOrder = inOrder(mCarInputService);

        BitSet bitSet = bitSetOf(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP);

        bitSet.set(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP);
        mService.registerKeyEventHandler(eventHandler1, bitSet.toByteArray());

        ArgumentCaptor<CarProjectionManager.ProjectionKeyEventHandler> eventListenerCaptor =
                ArgumentCaptor.forClass(CarProjectionManager.ProjectionKeyEventHandler.class);
        inOrder.verify(mCarInputService)
                .setProjectionKeyEventHandler(
                        eventListenerCaptor.capture(),
                        eq(bitSetOf(
                                CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP)));

        mService.registerKeyEventHandler(
                eventHandler2,
                bitSetOf(
                        CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP,
                        CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_DOWN
                ).toByteArray());
        inOrder.verify(mCarInputService).setProjectionKeyEventHandler(
                eventListenerCaptor.getValue(),
                bitSetOf(
                        CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP,
                        CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_DOWN
                ));

        // Fire handler interface sent to CarInputService, and ensure that correct events fire.
        eventListenerCaptor.getValue()
                .onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP);
        verify(eventHandler1)
                .onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP);
        verify(eventHandler2)
                .onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP);

        eventListenerCaptor.getValue()
                .onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_DOWN);
        verify(eventHandler1, never())
                .onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_DOWN);
        verify(eventHandler2)
                .onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_DOWN);

        // Deregister event handlers, and check that CarInputService is updated appropriately.
        mService.unregisterKeyEventHandler(eventHandler2);
        inOrder.verify(mCarInputService).setProjectionKeyEventHandler(
                eventListenerCaptor.getValue(),
                bitSetOf(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP));

        mService.unregisterKeyEventHandler(eventHandler1);
        inOrder.verify(mCarInputService).setProjectionKeyEventHandler(eq(null), any());
    }

    private ProjectionStatus createProjectionStatus() {
        Bundle statusExtra = new Bundle();
        statusExtra.putString(STATUS_EXTRA_KEY, STATUS_EXTRA_VALUE);
        Bundle mdExtra = new Bundle();
        mdExtra.putString(MD_EXTRA_KEY, MD_EXTRA_VALUE);

        return ProjectionStatus
                .builder(mContext.getPackageName(), PROJECTION_STATE_ACTIVE_FOREGROUND)
                .setExtras(statusExtra)
                .setProjectionTransport(PROJECTION_TRANSPORT_WIFI)
                .addMobileDevice(MobileDevice
                        .builder(MD_ID1, MD_NAME1)
                        .addTransport(PROJECTION_TRANSPORT_USB)
                        .setExtras(mdExtra)
                        .build())
                .addMobileDevice(MobileDevice
                        .builder(MD_ID2, MD_NAME2)
                        .addTransport(PROJECTION_TRANSPORT_USB)
                        .addTransport(PROJECTION_TRANSPORT_WIFI)
                        .setProjecting(true)
                        .build())
                .build();
    }

    private static ICarProjectionKeyEventHandler createMockKeyEventHandler() {
        ICarProjectionKeyEventHandler listener = mock(ICarProjectionKeyEventHandler.Stub.class);
        when(listener.asBinder()).thenCallRealMethod();
        return listener;
    }

    private static BitSet bitSetOf(@CarProjectionManager.KeyEventNum int... events) {
        BitSet bitSet = new BitSet();
        for (int event : events) {
            bitSet.set(event);
        }
        return bitSet;
    }
}
