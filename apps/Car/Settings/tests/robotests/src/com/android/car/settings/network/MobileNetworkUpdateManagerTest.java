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
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.testutils.BaseTestActivity;
import com.android.car.settings.testutils.ShadowSubscriptionManager;
import com.android.internal.telephony.TelephonyIntents;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowApplication;

import java.util.Collections;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowSubscriptionManager.class})
public class MobileNetworkUpdateManagerTest {

    private static final int SUB_ID = 1;

    private Context mContext;
    private ActivityController<BaseTestActivity> mActivityController;
    private BaseTestActivity mActivity;
    private MobileNetworkUpdateManager mMobileNetworkUpdateManager;

    @Mock
    private MobileNetworkUpdateManager.MobileNetworkUpdateListener mMobileNetworkUpdateListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mActivity = new BaseTestActivity();
        mActivityController = ActivityController.of(mActivity);
    }

    @Test
    public void onStart_receiverRegistered() {
        setupMobileNetworkUpdateManager(SUB_ID);
        mActivityController.create().start().visible();

        assertThat(ShadowApplication.getInstance().getRegisteredReceivers().size()).isGreaterThan(
                0);

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
    public void onStop_receiverUnregistered() {
        setupMobileNetworkUpdateManager(SUB_ID);
        mActivityController.create().start().visible();
        int prevSize = ShadowApplication.getInstance().getRegisteredReceivers().size();

        mActivityController.stop();
        assertThat(ShadowApplication.getInstance().getRegisteredReceivers().size()).isLessThan(
                prevSize);

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

    @Test
    public void onStart_subscriptionListenerRegistered() {
        setupMobileNetworkUpdateManager(SUB_ID);
        assertThat(getShadowSubscriptionManager().getOnSubscriptionChangedListeners()).isEmpty();
        mActivityController.create().start().visible();

        assertThat(getShadowSubscriptionManager().getOnSubscriptionChangedListeners()).isNotEmpty();
    }

    @Test
    public void onStop_subscriptionListenerUnregistered() {
        setupMobileNetworkUpdateManager(SUB_ID);
        assertThat(getShadowSubscriptionManager().getOnSubscriptionChangedListeners()).isEmpty();
        mActivityController.create().start().visible();
        assertThat(getShadowSubscriptionManager().getOnSubscriptionChangedListeners()).isNotEmpty();
        mActivityController.stop();

        assertThat(getShadowSubscriptionManager().getOnSubscriptionChangedListeners()).isEmpty();
    }

    @Test
    public void onMobileNetworkUpdated_withInitialSubId_pickSubId() {
        setupMobileNetworkUpdateManager(SUB_ID);
        getShadowSubscriptionManager().setActiveSubscriptionInfos(
                createSubscriptionInfo(SUB_ID + 1), createSubscriptionInfo(SUB_ID + 2));
        mActivityController.setup();

        verify(mMobileNetworkUpdateListener).onMobileNetworkUpdated(SUB_ID);
    }

    @Test
    public void onMobileNetworkUpdated_withoutInitialSubId_pickDefaultSubId() {
        setupMobileNetworkUpdateManager(MobileNetworkUpdateManager.SUB_ID_NULL);
        getShadowSubscriptionManager().setActiveSubscriptionInfos(
                createSubscriptionInfo(SUB_ID + 1), createSubscriptionInfo(SUB_ID + 2));
        mActivityController.setup();

        verify(mMobileNetworkUpdateListener).onMobileNetworkUpdated(SUB_ID + 1);
    }

    private void setupMobileNetworkUpdateManager(int subId) {
        mMobileNetworkUpdateManager = new MobileNetworkUpdateManager(mContext, subId);
        mActivity.getLifecycle().addObserver(mMobileNetworkUpdateManager);
        mMobileNetworkUpdateManager.registerListener(mMobileNetworkUpdateListener);

        getShadowSubscriptionManager().setSelectableSubscriptionInfoList(
                Collections.singletonList(createSubscriptionInfo(subId)));
    }

    private ShadowSubscriptionManager getShadowSubscriptionManager() {
        return Shadow.extract(mContext.getSystemService(SubscriptionManager.class));
    }

    private SubscriptionInfo createSubscriptionInfo(int subId) {
        SubscriptionInfo subInfo = new SubscriptionInfo(/* id= */ subId, /* iccId= */ "",
                /* simSlotIndex= */ 0, /* displayName= */ "", /* carrierName= */ "",
                /* nameSource= */ 0, /* iconTint= */ 0, /* number= */ "",
                /* roaming= */ 0, /* icon= */ null, /* mcc= */ "", /* mnc= */ "",
                /* countryIso= */ "", /* isEmbedded= */ false,
                /* accessRules= */ null, /* cardString= */ "");
        return subInfo;
    }
}
