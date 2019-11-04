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
 * limitations under the License
 */

package com.android.server.telecom.testapps;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.telecom.Log;

public class TestCallScreeningService extends CallScreeningService {
    private Call.Details mDetails;
    private static TestCallScreeningService sTestCallScreeningService;

    public static TestCallScreeningService getInstance() {
        return sTestCallScreeningService;
    }

    /**
     * Handles request from the system to screen an incoming call.
     * @param callDetails Information about a new incoming call, see {@link Call.Details}.
     */
    @Override
    public void onScreenCall(Call.Details callDetails) {
        Log.i(this, "onScreenCall: received call %s", callDetails);
        sTestCallScreeningService = this;

        mDetails = callDetails;
        if (callDetails.getCallDirection() == Call.Details.DIRECTION_INCOMING) {
            Intent errorIntent = new Intent(this, CallScreeningActivity.class);
            errorIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(errorIntent);
        }
    }

    public void blockCall() {
        CallScreeningService.CallResponse
                response = new CallScreeningService.CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build();
        respondToCall(mDetails, response);
    }

    public void allowCall() {
        CallScreeningService.CallResponse
                response = new CallScreeningService.CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build();
        respondToCall(mDetails, response);
    }
}
