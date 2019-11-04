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
package com.android.ons;

import android.content.Context;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.test.AndroidTestCase;

import androidx.test.InstrumentationRegistry;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public abstract class ONSBaseTest extends AndroidTestCase {
    protected TelephonyManager mTelephonyManager;
    @Mock
    protected SubscriptionManager mSubscriptionManager;
    @Mock
    protected TelephonyManager mMockTelephonyManager;
    protected Context mContext;
    protected boolean mReady  = false;
    private Object mLock = new Object();
    private static final int MAX_INIT_WAIT_MS = 5000; // 5 seconds
    private static final String TAG = "ONSBaseTest";

    protected void waitUntilReady() {
        waitUntilReady(MAX_INIT_WAIT_MS);
    }

    protected void waitUntilReady(int time) {
        synchronized (mLock) {
            if (!mReady) {
                try {
                    mLock.wait(time);
                } catch (InterruptedException ie) {
                }

                if (!mReady) {
                    Rlog.d(TAG, "ONS tests failed to set ready state");
                }
            }
        }
    }

    protected void setReady(boolean ready) {
        synchronized (mLock) {
            mReady = ready;
            mLock.notifyAll();
        }
    }

    protected void setUp(String tag) throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        MockitoAnnotations.initMocks(this);
        setReady(false);
    }
}