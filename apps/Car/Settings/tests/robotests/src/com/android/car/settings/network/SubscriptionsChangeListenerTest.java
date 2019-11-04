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

package com.android.car.settings.network;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.telephony.SubscriptionManager;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.testutils.ShadowSubscriptionManager;
import com.android.internal.telephony.TelephonyIntents;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowApplication;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowSubscriptionManager.class})
public class SubscriptionsChangeListenerTest {

    private Context mContext;
    private SubscriptionsChangeListener mSubscriptionsChangeListener;
    @Mock
    private SubscriptionsChangeListener.SubscriptionsChangeAction mSubscriptionsChangeAction;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = RuntimeEnvironment.application;
        mSubscriptionsChangeListener = new SubscriptionsChangeListener(mContext,
                mSubscriptionsChangeAction);
    }

    @After
    public void tearDown() {
        ShadowSubscriptionManager.reset();
    }

    @Test
    public void start_registersListener() {
        assertThat(getShadowSubscriptionManager().getOnSubscriptionChangedListeners()).isEmpty();
        mSubscriptionsChangeListener.start();
        assertThat(getShadowSubscriptionManager().getOnSubscriptionChangedListeners()).isNotEmpty();
    }

    @Test
    public void onSubscriptionChange_triggersAction() {
        mSubscriptionsChangeListener.start();
        // This is a way to trigger subscription change on the shadows.
        getShadowSubscriptionManager().setActiveSubscriptionInfoList(null);

        verify(mSubscriptionsChangeAction).onSubscriptionsChanged();
    }

    @Test
    public void stop_unregistersListener() {
        mSubscriptionsChangeListener.start();
        mSubscriptionsChangeListener.stop();
        assertThat(getShadowSubscriptionManager().getOnSubscriptionChangedListeners()).isEmpty();
    }

    @Test
    public void start_registersReceiver() {
        mSubscriptionsChangeListener.start();

        boolean hasMatch = false;
        for (ShadowApplication.Wrapper wrapper :
                ShadowApplication.getInstance().getRegisteredReceivers()) {
            if (wrapper.getIntentFilter().getAction(0)
                    == TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED) {
                hasMatch = true;
            }
        }
        assertThat(hasMatch).isTrue();
    }

    @Test
    public void onReceive_triggersAction() {
        mSubscriptionsChangeListener.start();
        mContext.sendBroadcast(new Intent(TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED));

        verify(mSubscriptionsChangeAction).onSubscriptionsChanged();
    }

    @Test
    public void stop_unregistersReceiver() {
        mSubscriptionsChangeListener.start();
        mSubscriptionsChangeListener.stop();

        boolean hasMatch = false;
        for (ShadowApplication.Wrapper wrapper :
                ShadowApplication.getInstance().getRegisteredReceivers()) {
            if (wrapper.getIntentFilter().getAction(0)
                    == TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED) {
                hasMatch = true;
            }
        }
        assertThat(hasMatch).isFalse();
    }

    private ShadowSubscriptionManager getShadowSubscriptionManager() {
        return Shadow.extract(mContext.getSystemService(SubscriptionManager.class));
    }
}
