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
 * limitations under the License
 */

package com.android.server.telecom.testapps;

import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.telecom.CallRedirectionService;
import android.telecom.Log;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;

public class TestCallRedirectionService extends CallRedirectionService {

    public static TestCallRedirectionService getInstance() {
        return sTestCallRedirectionService;
    }

    private static TestCallRedirectionService sTestCallRedirectionService;

    private static final Uri SAMPLE_HANDLE = Uri.fromParts(PhoneAccount.SCHEME_TEL, "0001112222",
            null);

    private static final PhoneAccountHandle SAMPLE_PHONE_ACCOUNT = new PhoneAccountHandle(
            new ComponentName("com.android.server.telecom.testapps",
                    "com.android.server.telecom.testapps.TestCallRedirectionService"),
            "TELECOM_TEST_APP_PHONE_ACCOUNT_ID");

    private PhoneAccountHandle mDestinationPhoneAccount = SAMPLE_PHONE_ACCOUNT;

    /**
     * Handles request from the system to redirect an outgoing call.
     */
    @Override
    public void onPlaceCall(@NonNull Uri handle, @NonNull PhoneAccountHandle initialPhoneAccount,
                            boolean allowInteractiveResponse) {
        Log.i(this, "onPlaceCall: received call %s", handle);
        sTestCallRedirectionService = this;
        mDestinationPhoneAccount = initialPhoneAccount;
        Intent intent = new Intent(this, CallRedirectionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void tryRedirectCallAndAskToConfirm() {
        // Provide call identification
        redirectCall(SAMPLE_HANDLE, mDestinationPhoneAccount, true);
    }
}
