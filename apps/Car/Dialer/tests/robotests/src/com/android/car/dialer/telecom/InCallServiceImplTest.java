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

package com.android.car.dialer.telecom;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationManager;
import android.car.Car;
import android.car.CarProjectionManager;
import android.content.Context;
import android.content.Intent;
import android.telecom.Call;

import com.android.car.dialer.CarDialerRobolectricTestRunner;
import com.android.car.dialer.testutils.ShadowCar;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContextWrapper;
import org.robolectric.shadows.ShadowLooper;

/**
 * Tests for {@link InCallServiceImpl}.
 */
@Config(shadows = {ShadowCar.class})
@RunWith(CarDialerRobolectricTestRunner.class)
public class InCallServiceImplTest {
    private static final String TELECOM_CALL_ID = "TC@1234";

    private InCallServiceImpl mInCallServiceImpl;
    private Context mContext;

    @Mock
    Car mCar;
    @Mock
    CarProjectionManager mCarProjectionManager;
    @Mock
    private Call mMockTelecomCall;
    @Mock
    private Call.Details mMockCallDetails;
    @Mock
    private InCallServiceImpl.Callback mCallback;
    @Mock
    private InCallServiceImpl.ActiveCallListChangedCallback mActiveCallListChangedCallback;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = RuntimeEnvironment.application;

        when(mCar.getCarManager(Car.PROJECTION_SERVICE)).thenReturn(mCarProjectionManager);
        ShadowCar.setCar(mCar);

        ServiceController<InCallServiceImpl> inCallServiceController =
                Robolectric.buildService(InCallServiceImpl.class);
        inCallServiceController.create().bind();
        mInCallServiceImpl = inCallServiceController.get();

        mInCallServiceImpl.registerCallback(mCallback);
        mInCallServiceImpl.addActiveCallListChangedCallback(mActiveCallListChangedCallback);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        when(mMockTelecomCall.getDetails()).thenReturn(mMockCallDetails);
        when(mMockCallDetails.getTelecomCallId()).thenReturn(TELECOM_CALL_ID);
    }

    @Test
    public void onActiveCallAdded_startInCallActivity() {
        when(mMockTelecomCall.getState()).thenReturn(Call.STATE_ACTIVE);
        mInCallServiceImpl.onCallAdded(mMockTelecomCall);

        ArgumentCaptor<Call> callCaptor = ArgumentCaptor.forClass(Call.class);
        verify(mCallback).onTelecomCallAdded(callCaptor.capture());
        assertThat(callCaptor.getValue()).isEqualTo(mMockTelecomCall);

        verify(mActiveCallListChangedCallback).onTelecomCallAdded(callCaptor.capture());
        assertThat(callCaptor.getValue()).isEqualTo(mMockTelecomCall);

        ShadowContextWrapper shadowContextWrapper = shadowOf(RuntimeEnvironment.application);
        Intent intent = shadowContextWrapper.getNextStartedActivity();
        assertThat(intent).isNotNull();
    }

    @Test
    public void onCallRemoved() {
        when(mMockTelecomCall.getState()).thenReturn(Call.STATE_ACTIVE);
        mInCallServiceImpl.onCallRemoved(mMockTelecomCall);

        ArgumentCaptor<Call> callCaptor = ArgumentCaptor.forClass(Call.class);
        verify(mCallback).onTelecomCallRemoved(callCaptor.capture());
        assertThat(callCaptor.getValue()).isEqualTo(mMockTelecomCall);

        verify(mActiveCallListChangedCallback).onTelecomCallRemoved(callCaptor.capture());
        assertThat(callCaptor.getValue()).isEqualTo(mMockTelecomCall);
    }

    @Test
    public void onRingingCallAdded_showNotification() {
        when(mMockTelecomCall.getState()).thenReturn(Call.STATE_RINGING);
        mInCallServiceImpl.onCallAdded(mMockTelecomCall);

        ArgumentCaptor<Call> callCaptor = ArgumentCaptor.forClass(Call.class);
        verify(mCallback).onTelecomCallAdded(callCaptor.capture());
        assertThat(callCaptor.getValue()).isEqualTo(mMockTelecomCall);

        verify(mActiveCallListChangedCallback).onTelecomCallAdded(callCaptor.capture());
        assertThat(callCaptor.getValue()).isEqualTo(mMockTelecomCall);

        ArgumentCaptor<Call.Callback> callbackListCaptor = ArgumentCaptor.forClass(
                Call.Callback.class);
        verify(mMockTelecomCall).registerCallback(callbackListCaptor.capture());

        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        verify(notificationManager).notify(eq(TELECOM_CALL_ID), anyInt(), any(Notification.class));
    }

    @Test
    public void testUnregisterCallback() {
        mInCallServiceImpl.unregisterCallback(mCallback);

        mInCallServiceImpl.onCallAdded(mMockTelecomCall);
        verify(mCallback, never()).onTelecomCallAdded(any());

        mInCallServiceImpl.onCallRemoved(mMockTelecomCall);
        verify(mCallback, never()).onTelecomCallRemoved(any());
    }

    @Test
    public void testRemoveActiveCallListChangedCallback() {
        mInCallServiceImpl.removeActiveCallListChangedCallback(mActiveCallListChangedCallback);

        mInCallServiceImpl.onCallAdded(mMockTelecomCall);
        verify(mActiveCallListChangedCallback, never()).onTelecomCallAdded(any());

        mInCallServiceImpl.onCallRemoved(mMockTelecomCall);
        verify(mActiveCallListChangedCallback, never()).onTelecomCallRemoved(any());
    }
}
