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
package com.android.phone;

import static com.google.common.truth.Truth.assertThat;

import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.PhoneConstants;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CdmaOptionsTest {
    @Test
    public void shouldAddApnExpandPreference_doesNotExpandOnGsm() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_SHOW_APN_SETTING_CDMA_BOOL, true);
        assertThat(CdmaOptions.shouldAddApnExpandPreference(PhoneConstants.PHONE_TYPE_GSM, bundle))
                .isFalse();
    }

    @Test
    public void shouldAddApnExpandPreference_showExpandOnCdma() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_SHOW_APN_SETTING_CDMA_BOOL, true);
        assertThat(CdmaOptions.shouldAddApnExpandPreference(PhoneConstants.PHONE_TYPE_CDMA, bundle))
                .isTrue();
    }

    @Test
    public void shouldAddApnExpandPreference_doesNotExpandOnCdmaIfCarrierConfigDisabled() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_SHOW_APN_SETTING_CDMA_BOOL, false);
        assertThat(CdmaOptions.shouldAddApnExpandPreference(PhoneConstants.PHONE_TYPE_CDMA, bundle))
                .isFalse();
    }
}
