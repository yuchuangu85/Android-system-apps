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

package android.car;

import static android.car.CarProjectionManager.ProjectionAccessPointCallback.ERROR_GENERIC;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.car.CarProjectionManager.ProjectionAccessPointCallback;
import android.car.testapi.CarProjectionController;
import android.car.testapi.FakeCar;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.util.ArraySet;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class CarProjectionManagerTest {
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Captor
    private ArgumentCaptor<Intent> mIntentArgumentCaptor;

    @Spy
    private final Context mContext = ApplicationProvider.getApplicationContext();

    private static final int DEFAULT_TIMEOUT_MS = 1000;

    /** An {@link Executor} that immediately runs its callbacks synchronously. */
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    private CarProjectionManager mProjectionManager;
    private CarProjectionController mController;
    private ApCallback mApCallback;

    @Before
    public void setUp() {
        FakeCar fakeCar = FakeCar.createFakeCar(mContext);
        mController = fakeCar.getCarProjectionController();
        mProjectionManager =
                (CarProjectionManager) fakeCar.getCar().getCarManager(Car.PROJECTION_SERVICE);
        assertThat(mProjectionManager).isNotNull();

        mApCallback = new ApCallback();
    }

    @Test
    public void startAp_fail() throws InterruptedException {
        mController.setWifiConfiguration(null);

        mProjectionManager.startProjectionAccessPoint(mApCallback);
        mApCallback.mFailed.await(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertThat(mApCallback.mFailureReason).isEqualTo(ERROR_GENERIC);
    }

    @Test
    public void startAp_success() throws InterruptedException {
        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.SSID = "Hello";
        wifiConfiguration.BSSID = "AA:BB:CC:CC:DD:EE";
        wifiConfiguration.preSharedKey = "password";

        mController.setWifiConfiguration(wifiConfiguration);

        mProjectionManager.startProjectionAccessPoint(mApCallback);
        mApCallback.mStarted.await(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertThat(mApCallback.mWifiConfiguration).isEqualTo(wifiConfiguration);
    }

    @Test
    public void registerProjectionRunner() throws CarNotConnectedException {
        Intent intent = new Intent("my_action");
        intent.setPackage("my.package");
        mProjectionManager.registerProjectionRunner(intent);

        verify(mContext).bindService(mIntentArgumentCaptor.capture(), any(),
                eq(Context.BIND_AUTO_CREATE));
        assertThat(mIntentArgumentCaptor.getValue()).isEqualTo(intent);
    }

    @Test
    public void keyEventListener_registerMultipleEventListeners() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler1 =
                mock(CarProjectionManager.ProjectionKeyEventHandler.class);
        CarProjectionManager.ProjectionKeyEventHandler eventHandler2 =
                mock(CarProjectionManager.ProjectionKeyEventHandler.class);

        mProjectionManager.addKeyEventHandler(
                Collections.singleton(CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP),
                DIRECT_EXECUTOR,
                eventHandler1);
        mProjectionManager.addKeyEventHandler(
                new ArraySet<>(
                        Arrays.asList(
                                CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP,
                                CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_DOWN)),
                DIRECT_EXECUTOR,
                eventHandler2);

        mController.fireKeyEvent(CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP);
        verify(eventHandler1).onKeyEvent(CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP);
        verify(eventHandler2).onKeyEvent(CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP);

        mController.fireKeyEvent(CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_DOWN);
        verify(eventHandler1, never())
                .onKeyEvent(CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_DOWN);
        verify(eventHandler2).onKeyEvent(CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_DOWN);

        mController.fireKeyEvent(CarProjectionManager.KEY_EVENT_CALL_KEY_DOWN);
        verify(eventHandler1, never()).onKeyEvent(CarProjectionManager.KEY_EVENT_CALL_KEY_DOWN);
        verify(eventHandler2, never()).onKeyEvent(CarProjectionManager.KEY_EVENT_CALL_KEY_DOWN);
    }

    @Test
    public void keyEventHandler_canRegisterAllEvents() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                mock(CarProjectionManager.ProjectionKeyEventHandler.class);

        Set<Integer> events = new ArraySet<>(CarProjectionManager.NUM_KEY_EVENTS);
        for (int evt = 0; evt < CarProjectionManager.NUM_KEY_EVENTS; evt++) {
            events.add(evt);
        }

        mProjectionManager.addKeyEventHandler(events, DIRECT_EXECUTOR, eventHandler);

        for (int evt : events) {
            mController.fireKeyEvent(evt);
            verify(eventHandler).onKeyEvent(evt);
        }
    }

    @Test
    public void keyEventHandler_eventsOutOfRange_throw() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                mock(CarProjectionManager.ProjectionKeyEventHandler.class);

        try {
            mProjectionManager.addKeyEventHandler(Collections.singleton(-1), eventHandler);
            fail();
        } catch (IllegalArgumentException expected) { }

        try {
            mProjectionManager.addKeyEventHandler(
                    Collections.singleton(CarProjectionManager.NUM_KEY_EVENTS), eventHandler);
            fail();
        } catch (IllegalArgumentException expected) { }
    }

    @Test
    public void keyEventHandler_whenRegisteredAgain_replacesEventList() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                mock(CarProjectionManager.ProjectionKeyEventHandler.class);
        InOrder inOrder = inOrder(eventHandler);

        mProjectionManager.addKeyEventHandler(
                Collections.singleton(CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP),
                DIRECT_EXECUTOR,
                eventHandler);
        mController.fireKeyEvent(CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP);
        inOrder.verify(eventHandler)
                .onKeyEvent(CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP);

        mProjectionManager.addKeyEventHandler(
                Collections.singleton(CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_DOWN),
                DIRECT_EXECUTOR,
                eventHandler);
        mController.fireKeyEvent(CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP);
        inOrder.verify(eventHandler, never())
                .onKeyEvent(CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP);
    }

    @Test
    public void keyEventHandler_removed_noLongerFires() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                mock(CarProjectionManager.ProjectionKeyEventHandler.class);

        mProjectionManager.addKeyEventHandler(
                Collections.singleton(CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP),
                DIRECT_EXECUTOR,
                eventHandler);
        mProjectionManager.removeKeyEventHandler(eventHandler);

        mController.fireKeyEvent(CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP);
        verify(eventHandler, never())
                .onKeyEvent(CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP);
    }

    @Test
    public void keyEventHandler_withAlternateExecutor_usesExecutor() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                mock(CarProjectionManager.ProjectionKeyEventHandler.class);
        Executor executor = mock(Executor.class);
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

        mProjectionManager.addKeyEventHandler(
                Collections.singleton(
                        CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP),
                executor,
                eventHandler);

        mController.fireKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP);
        verify(eventHandler, never()).onKeyEvent(anyInt());
        verify(executor).execute(runnableCaptor.capture());

        runnableCaptor.getValue().run();
        verify(eventHandler)
                .onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP);
    }

    private static class ApCallback extends ProjectionAccessPointCallback {
        CountDownLatch mStarted = new CountDownLatch(1);
        CountDownLatch mFailed = new CountDownLatch(1);
        int mFailureReason = -1;
        WifiConfiguration mWifiConfiguration;

        @Override
        public void onStarted(WifiConfiguration wifiConfiguration) {
            mWifiConfiguration = wifiConfiguration;
            mStarted.countDown();
        }

        @Override
        public void onStopped() {
        }

        @Override
        public void onFailed(int reason) {
            mFailureReason = reason;
            mFailed.countDown();
        }
    }
}
