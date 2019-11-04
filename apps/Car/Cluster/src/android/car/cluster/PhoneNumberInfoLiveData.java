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
package android.car.cluster;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.android.car.telephony.common.TelecomUtils;
import com.android.car.telephony.common.TelecomUtils.PhoneNumberInfo;

import java.util.concurrent.CompletableFuture;

class PhoneNumberInfoLiveData extends MediatorLiveData<PhoneNumberInfo> {

    private CompletableFuture<Void> mCurrentFuture;

    public PhoneNumberInfoLiveData(Context context, LiveData<String> numberLiveData) {
        addSource(numberLiveData, (number) -> {
            if (mCurrentFuture != null) {
                mCurrentFuture.cancel(true);
            }

            mCurrentFuture = TelecomUtils.getPhoneNumberInfo(context, number)
                .thenAccept(this::postValue);
        });
    }
}
