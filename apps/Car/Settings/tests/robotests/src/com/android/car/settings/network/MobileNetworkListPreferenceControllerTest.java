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

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowSubscriptionManager;

import com.google.android.collect.Lists;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowSubscriptionManager.class})
public class MobileNetworkListPreferenceControllerTest {

    private static final int SUB_ID = 1;
    private Context mContext;
    private PreferenceGroup mPreferenceGroup;
    private PreferenceControllerTestHelper<MobileNetworkListPreferenceController> mControllerHelper;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                MobileNetworkListPreferenceController.class, mPreferenceGroup);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @After
    public void tearDown() {
        ShadowSubscriptionManager.reset();
    }

    @Test
    public void refreshUi_containsElements() {
        SubscriptionInfo info = createSubscriptionInfo(/* subId= */ 1,
                /* simSlotIndex= */ 1, /* cardString= */"", "mncString");
        List<SubscriptionInfo> selectable = Lists.newArrayList(info);
        getShadowSubscriptionManager().setSelectableSubscriptionInfoList(selectable);

        mControllerHelper.getController().refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void onPreferenceClicked_launchesFragment() {
        SubscriptionInfo info = createSubscriptionInfo(SUB_ID, /* simSlotIndex= */ 1,
                /* cardString= */"", "mncString");
        List<SubscriptionInfo> selectable = Lists.newArrayList(info);
        getShadowSubscriptionManager().setSelectableSubscriptionInfoList(selectable);

        mControllerHelper.getController().refreshUi();
        Preference preference = mPreferenceGroup.getPreference(0);
        preference.performClick();

        ArgumentCaptor<MobileNetworkFragment> captor = ArgumentCaptor.forClass(
                MobileNetworkFragment.class);
        verify(mControllerHelper.getMockFragmentController()).launchFragment(captor.capture());

        assertThat(captor.getValue().getArguments().getInt(MobileNetworkFragment.ARG_NETWORK_SUB_ID,
                -1)).isEqualTo(SUB_ID);
    }

    private ShadowSubscriptionManager getShadowSubscriptionManager() {
        return Shadow.extract(mContext.getSystemService(SubscriptionManager.class));
    }

    private SubscriptionInfo createSubscriptionInfo(int subId, int simSlotIndex,
            String cardString, String mncString) {
        SubscriptionInfo subInfo = new SubscriptionInfo(subId, /* iccId= */ "",
                simSlotIndex, /* displayName= */ "", /* carrierName= */ "",
                /* nameSource= */ 0, /* iconTint= */ 0, /* number= */ "",
                /* roaming= */ 0, /* icon= */ null, /* mcc= */ "", mncString,
                /* countryIso= */ "", /* isEmbedded= */ false,
                /* accessRules= */ null, cardString);
        return subInfo;
    }
}
