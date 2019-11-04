/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom.callfiltering;

import android.content.Context;
import android.os.Bundle;

import com.android.internal.telephony.BlockChecker;

public class BlockCheckerAdapter {
    public BlockCheckerAdapter() { }

    /**
     * Check whether the number is blocked.
     *
     * @param context the context of the caller.
     * @param number the number to check.
     * @param extras the extra attribute of the number.
     * @return result code indicating if the number should be blocked, and if so why.
     *         Valid values are: {@link android.provider.BlockedNumberContract#STATUS_NOT_BLOCKED},
     *         {@link android.provider.BlockedNumberContract#STATUS_BLOCKED_IN_LIST},
     *         {@link android.provider.BlockedNumberContract#STATUS_BLOCKED_NOT_IN_CONTACTS},
     *         {@link android.provider.BlockedNumberContract#STATUS_BLOCKED_PAYPHONE},
     *         {@link android.provider.BlockedNumberContract#STATUS_BLOCKED_RESTRICTED},
     *         {@link android.provider.BlockedNumberContract#STATUS_BLOCKED_UNKNOWN_NUMBER}.
     */
    public int getBlockStatus(Context context, String number, Bundle extras) {
        return BlockChecker.getBlockStatus(context, number, extras);
    }
}
