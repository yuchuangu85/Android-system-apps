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

package com.android.car.dialer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.telecom.CallAudioState;

import com.android.car.dialer.notification.InCallNotificationController;
import com.android.car.dialer.notification.MissedCallNotificationController;
import com.android.car.dialer.telecom.InCallServiceImpl;
import com.android.car.dialer.telecom.UiCallManager;

/** Robolectric runtime application for Dialer. Must be Test + application class name. */
public class TestDialerApplication extends Application {

    private InCallServiceImpl.LocalBinder mLocalBinder;

    @Override
    public void onCreate() {
        super.onCreate();
        shadowOf(this).setSystemService(
                Context.NOTIFICATION_SERVICE, mock(NotificationManager.class));
        InCallNotificationController.init(this);
        MissedCallNotificationController.init(this);

        mLocalBinder = mock(InCallServiceImpl.LocalBinder.class);
        shadowOf(this).setComponentNameAndServiceForBindService(
                new ComponentName(this, InCallServiceImpl.class), mLocalBinder);
    }

    public void initUiCallManager() {
        UiCallManager.init(this);
    }

    public void setupInCallServiceImpl() {
        InCallServiceImpl inCallService = mock(InCallServiceImpl.class);
        CallAudioState callAudioState = mock(CallAudioState.class);
        when(callAudioState.getRoute()).thenReturn(CallAudioState.ROUTE_BLUETOOTH);
        when(inCallService.getCallAudioState()).thenReturn(callAudioState);
        when(mLocalBinder.getService()).thenReturn(inCallService);
    }

    public void setupInCallServiceImpl(InCallServiceImpl inCallServiceImpl) {
        when(mLocalBinder.getService()).thenReturn(inCallServiceImpl);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        InCallNotificationController.tearDown();
        MissedCallNotificationController.get().tearDown();
    }

}
