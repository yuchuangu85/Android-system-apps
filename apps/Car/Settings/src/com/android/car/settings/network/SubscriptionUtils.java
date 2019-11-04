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

import static android.telephony.UiccSlotInfo.CARD_STATE_INFO_PRESENT;

import static com.android.internal.util.CollectionUtils.emptyIfNull;

import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccSlotInfo;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Shared logic related to dealing with multiple subscriptions. */
public final class SubscriptionUtils {

    private SubscriptionUtils() {
    }

    /**
     * Returns the list of available subscriptions, accounting for duplicates possible through a
     * virtual network provider.
     */
    public static List<SubscriptionInfo> getAvailableSubscriptions(
            SubscriptionManager subscriptionManager, TelephonyManager telephonyManager) {
        List<SubscriptionInfo> subscriptions = new ArrayList<>(
                emptyIfNull(subscriptionManager.getSelectableSubscriptionInfoList()));

        // Look for inactive but present physical SIMs that are missing from the selectable list.
        List<UiccSlotInfo> missing = new ArrayList<>();
        UiccSlotInfo[] slotsInfo = telephonyManager.getUiccSlotsInfo();
        for (int i = 0; slotsInfo != null && i < slotsInfo.length; i++) {
            UiccSlotInfo slotInfo = slotsInfo[i];
            if (isInactiveInsertedPSim(slotInfo)) {
                int index = slotInfo.getLogicalSlotIdx();
                String cardId = slotInfo.getCardId();

                boolean found = subscriptions.stream().anyMatch(
                        info -> index == info.getSimSlotIndex() && cardId.equals(
                                info.getCardString()));
                if (!found) {
                    missing.add(slotInfo);
                }
            }
        }
        if (!missing.isEmpty()) {
            for (SubscriptionInfo info : subscriptionManager.getAllSubscriptionInfoList()) {
                for (UiccSlotInfo slotInfo : missing) {
                    if (info.getSimSlotIndex() == slotInfo.getLogicalSlotIdx()
                            && info.getCardString().equals(slotInfo.getCardId())) {
                        subscriptions.add(info);
                        break;
                    }
                }
            }
        }

        // With some carriers such as Google Fi which provide a sort of virtual service that spans
        // across multiple underlying networks, we end up with subscription entries for the
        // underlying networks that need to be hidden from the user in the UI.
        for (Iterator<SubscriptionInfo> iter = subscriptions.iterator(); iter.hasNext(); ) {
            SubscriptionInfo info = iter.next();
            if (TextUtils.isEmpty(info.getMncString())) {
                iter.remove();
            }
        }
        return subscriptions;
    }

    private static boolean isInactiveInsertedPSim(UiccSlotInfo slotInfo) {
        return !slotInfo.getIsEuicc() && !slotInfo.getIsActive()
                && slotInfo.getCardStateInfo() == CARD_STATE_INFO_PRESENT;
    }
}
