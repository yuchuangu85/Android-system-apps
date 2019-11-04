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

import android.content.Context;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.widget.TextView;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.FragmentController;
import com.android.car.settings.testutils.ShadowSubscriptionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

import java.util.Collections;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowSubscriptionManager.class})
public class MobileNetworkFragmentTest {

    private static final int SUB_ID = 1;
    private static final String TEST_NAME = "Test Name";

    private Context mContext;
    private FragmentController<MobileNetworkFragment> mFragmentController;
    private MobileNetworkFragment mFragment;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
    }

    @After
    public void tearDown() {
        ShadowSubscriptionManager.reset();
    }

    @Test
    public void onMobileNetworkUpdated_startWithArgument_updateTitle() {
        setUpFragmentWithSubId(SUB_ID, TEST_NAME);
        getShadowSubscriptionManager().setSelectableSubscriptionInfoList(
                Collections.singletonList(createSubscriptionInfo(SUB_ID, TEST_NAME)));
        getShadowSubscriptionManager().setActiveSubscriptionInfos(
                createSubscriptionInfo(SUB_ID + 1, TEST_NAME + "_1"),
                createSubscriptionInfo(SUB_ID + 2, TEST_NAME + "_2"));
        mFragmentController.setup();

        TextView textView = mFragment.requireActivity().findViewById(R.id.title);
        assertThat(textView.getText()).isEqualTo(TEST_NAME);
    }

    @Test
    public void onMobileNetworkUpdated_noArgumentProvided_updateTitle() {
        mFragment = new MobileNetworkFragment();
        mFragmentController = FragmentController.of(mFragment);
        getShadowSubscriptionManager().setSelectableSubscriptionInfoList(
                Collections.singletonList(createSubscriptionInfo(SUB_ID, TEST_NAME)));
        getShadowSubscriptionManager().setActiveSubscriptionInfos(
                createSubscriptionInfo(SUB_ID + 1, TEST_NAME + "_1"),
                createSubscriptionInfo(SUB_ID + 2, TEST_NAME + "_2"));
        mFragmentController.setup();

        TextView textView = mFragment.requireActivity().findViewById(R.id.title);
        assertThat(textView.getText()).isEqualTo(TEST_NAME + "_1");
    }

    private void setUpFragmentWithSubId(int subId, String name) {
        SubscriptionInfo info = createSubscriptionInfo(subId, name);
        getShadowSubscriptionManager().setSelectableSubscriptionInfoList(
                Collections.singletonList(info));

        mFragment = MobileNetworkFragment.newInstance(subId);
        mFragmentController = FragmentController.of(mFragment);
    }

    private SubscriptionInfo createSubscriptionInfo(int subId, String name) {
        SubscriptionInfo subInfo = new SubscriptionInfo(subId, /* iccId= */ "",
                /* simSlotIndex= */ 0, /* displayName= */ name, /* carrierName= */ "",
                /* nameSource= */ 0, /* iconTint= */ 0, /* number= */ "",
                /* roaming= */ 0, /* icon= */ null, /* mcc= */ "", /* mnc= */ "",
                /* countryIso= */ "", /* isEmbedded= */ false,
                /* accessRules= */ null, /* cardString= */ "");
        return subInfo;
    }

    private ShadowSubscriptionManager getShadowSubscriptionManager() {
        return Shadow.extract(mContext.getSystemService(SubscriptionManager.class));
    }
}
