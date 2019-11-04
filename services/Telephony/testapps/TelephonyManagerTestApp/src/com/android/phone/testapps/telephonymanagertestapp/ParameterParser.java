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

package com.android.phone.testapps.telephonymanagertestapp;

import android.content.Context;
import android.telephony.NumberVerificationCallback;
import android.telephony.PhoneNumberRange;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;

class ParameterParser {
    private static ParameterParser sInstance;

    static ParameterParser get(Context context) {
        if (sInstance == null) {
            sInstance = new ParameterParser(context);
        }
        return sInstance;
    }

    private final Context mContext;
    private final Map<Class, Function<String, Object>> mParsers =
            new HashMap<Class, Function<String, Object>>() {{
                put(PhoneNumberRange.class, ParameterParser::parsePhoneNumberRange);
                put(Executor.class, s -> parseExecutor(s));
                put(NumberVerificationCallback.class, s -> parseNumberVerificationCallback(s));
            }};

    private ParameterParser(Context context) {
        mContext = context;
    }

    Object executeParser(Class type, String input) {
        return mParsers.getOrDefault(type, s -> null).apply(input);
    }

    private static PhoneNumberRange parsePhoneNumberRange(String input) {
        String[] parts = input.split(" ");
        if (parts.length != 4) {
            return null;
        }
        return new PhoneNumberRange(parts[0], parts[1], parts[2], parts[3]);
    }

    private Executor parseExecutor(String input) {
        return mContext.getMainExecutor();
    }

    private NumberVerificationCallback parseNumberVerificationCallback(String input) {
        return new NumberVerificationCallback() {
            @Override
            public void onCallReceived(String phoneNumber) {
                Toast.makeText(mContext, "Received verification " + phoneNumber,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onVerificationFailed(int reason) {
                Toast.makeText(mContext, "Verification failed " + reason,
                        Toast.LENGTH_SHORT).show();
            }
        };
    }
}
