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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccSlotInfo;

import com.android.car.settings.CarSettingsRobolectricTestRunner;

import com.google.android.collect.Lists;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class SubscriptionUtilsTest {

    @Test
    public void getAvailableSubscriptions_hasSubscriptionsFromSubscriptionManager_valueReturned() {
        SubscriptionManager subscriptionManager = mock(SubscriptionManager.class);
        TelephonyManager telephonyManager = mock(TelephonyManager.class);

        SubscriptionInfo info = createSubscriptionInfo(/* subId= */ 1,
                /* simSlotIndex= */ 1, /* cardString= */"", "mncString");
        List<SubscriptionInfo> selectable = Lists.newArrayList(info);
        when(subscriptionManager.getSelectableSubscriptionInfoList()).thenReturn(selectable);
        when(telephonyManager.getUiccSlotsInfo()).thenReturn(new UiccSlotInfo[0]);

        List<SubscriptionInfo> infos = SubscriptionUtils.getAvailableSubscriptions(
                subscriptionManager, telephonyManager);

        assertThat(infos).contains(info);
    }

    @Test
    public void getAvailableSubscriptions_hasSimSlotNotInSubscriptionManager_valueReturned() {
        SubscriptionManager subscriptionManager = mock(SubscriptionManager.class);
        TelephonyManager telephonyManager = mock(TelephonyManager.class);

        int simSlotIdx = 1;
        String cardString = "testString";
        SubscriptionInfo info = createSubscriptionInfo(/* subId= */ 1, simSlotIdx, cardString,
                "mncString");
        UiccSlotInfo slotInfo = createUuicSlotInfo(/* isActive= */ false, /* isEsim= */ false,
                simSlotIdx, cardString, UiccSlotInfo.CARD_STATE_INFO_PRESENT);
        List<SubscriptionInfo> allSims = Lists.newArrayList(info);
        when(subscriptionManager.getAllSubscriptionInfoList()).thenReturn(allSims);
        when(telephonyManager.getUiccSlotsInfo()).thenReturn(new UiccSlotInfo[]{slotInfo});

        List<SubscriptionInfo> infos = SubscriptionUtils.getAvailableSubscriptions(
                subscriptionManager, telephonyManager);

        assertThat(infos).contains(info);
    }

    @Test
    public void getAvailableSubscriptions_hasInactiveInsertedPSim_valueRemoved() {
        SubscriptionManager subscriptionManager = mock(SubscriptionManager.class);
        TelephonyManager telephonyManager = mock(TelephonyManager.class);

        SubscriptionInfo info = createSubscriptionInfo(/* subId= */ 1,
                /* simSlotIndex= */ 1, /* cardString= */"", "");
        List<SubscriptionInfo> selectable = Lists.newArrayList(info);
        when(subscriptionManager.getSelectableSubscriptionInfoList()).thenReturn(selectable);
        when(telephonyManager.getUiccSlotsInfo()).thenReturn(new UiccSlotInfo[0]);

        List<SubscriptionInfo> infos = SubscriptionUtils.getAvailableSubscriptions(
                subscriptionManager, telephonyManager);

        assertThat(infos).doesNotContain(info);
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

    private UiccSlotInfo createUuicSlotInfo(boolean isActive, boolean isEsim, int simSlotIndex,
            String cardId, int cardStateInfo) {
        return new UiccSlotInfo(isActive, isEsim, cardId, cardStateInfo, simSlotIndex,
                /* isExtendedApduSupported= */ false, /* isRemoveable= */ true);
    }
}
