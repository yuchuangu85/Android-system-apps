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
package com.android.ons;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.AvailableNetworkInfo;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.IOns;
import com.android.internal.telephony.ISetOpportunisticDataCallback;
import com.android.internal.telephony.IUpdateAvailableNetworksCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.Mockito.any;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.test.runner.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class OpportunisticNetworkServiceTest extends ONSBaseTest {
    private static final String TAG = "ONSTest";
    private String pkgForDebug;
    private int mResult;
    private IOns iOpportunisticNetworkService;
    private Looper mLooper;
    private OpportunisticNetworkService mOpportunisticNetworkService;
    private static final String CARRIER_APP_CONFIG_NAME = "carrierApp";
    private static final String SYSTEM_APP_CONFIG_NAME = "systemApp";

    @Mock
    private HashMap<String, ONSConfigInput> mockONSConfigInputHashMap;

    @Before
    public void setUp() throws Exception {
        super.setUp("ONSTest");
        pkgForDebug = mContext != null ? mContext.getOpPackageName() : "<unknown>";
        Intent intent = new Intent(mContext, OpportunisticNetworkService.class);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mOpportunisticNetworkService = new OpportunisticNetworkService();
                mContext.startService(intent);
                mOpportunisticNetworkService.initialize(mContext);
                mOpportunisticNetworkService.mContext = mContext;
                mOpportunisticNetworkService.mSubscriptionManager = mSubscriptionManager;
                mLooper = Looper.myLooper();
                Looper.loop();
            }
        }).start();
        iOpportunisticNetworkService = getIOns();
        for (int i = 0; i < 5; i++) {
            if (iOpportunisticNetworkService == null) {
                waitForMs(500);
                iOpportunisticNetworkService = getIOns();
            } else {
                break;
            }
        }
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        mOpportunisticNetworkService.onDestroy();
        if (mLooper != null) {
            mLooper.quit();
            mLooper.getThread().join();
        }
    }

    @Test
    public void testCheckEnable() {
        boolean isEnable = true;
        try {
            iOpportunisticNetworkService.setEnable(false, pkgForDebug);
            isEnable = iOpportunisticNetworkService.isEnabled(pkgForDebug);
        } catch (RemoteException ex) {
            Log.e(TAG, "RemoteException", ex);
        }
        assertEquals(false, isEnable);
    }

    @Test
    public void testHandleSimStateChange() {
        mResult = -1;
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
                new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos =
                new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);
        IUpdateAvailableNetworksCallback mCallback = new IUpdateAvailableNetworksCallback.Stub() {
            @Override
            public void onComplete(int result) {
                mResult = result;
                Log.d(TAG, "result: " + result);
            }
        };
        ONSConfigInput onsConfigInput = new ONSConfigInput(availableNetworkInfos, mCallback);
        onsConfigInput.setPrimarySub(1);
        onsConfigInput.setPreferredDataSub(availableNetworkInfos.get(0).getSubId());
        ArrayList<SubscriptionInfo> subscriptionInfos = new ArrayList<SubscriptionInfo>();

        // Case 1: There is no Carrier app using ONS.
        doReturn(null).when(mockONSConfigInputHashMap).get(CARRIER_APP_CONFIG_NAME);
        mOpportunisticNetworkService.mIsEnabled = true;
        mOpportunisticNetworkService.mONSConfigInputHashMap = mockONSConfigInputHashMap;
        mOpportunisticNetworkService.handleSimStateChange();
        waitForMs(500);
        verify(mockONSConfigInputHashMap, never()).get(SYSTEM_APP_CONFIG_NAME);

        // Case 2: There is a Carrier app using ONS and no System app input.
        doReturn(subscriptionInfos).when(mSubscriptionManager).getActiveSubscriptionInfoList(false);
        doReturn(onsConfigInput).when(mockONSConfigInputHashMap).get(CARRIER_APP_CONFIG_NAME);
        doReturn(null).when(mockONSConfigInputHashMap).get(SYSTEM_APP_CONFIG_NAME);
        mOpportunisticNetworkService.mIsEnabled = true;
        mOpportunisticNetworkService.mONSConfigInputHashMap = mockONSConfigInputHashMap;
        mOpportunisticNetworkService.handleSimStateChange();
        waitForMs(50);
        verify(mockONSConfigInputHashMap,times(1)).get(SYSTEM_APP_CONFIG_NAME);
    }

    @Test
    public void testSystemPreferredDataWhileCarrierAppIsActive() {
        mResult = -1;
        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
            new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos =
            new ArrayList<AvailableNetworkInfo>();
        availableNetworkInfos.add(availableNetworkInfo);
        IUpdateAvailableNetworksCallback mCallback = new IUpdateAvailableNetworksCallback.Stub() {
            @Override
            public void onComplete(int result) {
                mResult = result;
                Log.d(TAG, "result: " + result);
            }
        };
        ONSConfigInput onsConfigInput = new ONSConfigInput(availableNetworkInfos, mCallback);
        onsConfigInput.setPrimarySub(1);
        onsConfigInput.setPreferredDataSub(availableNetworkInfos.get(0).getSubId());
        ArrayList<SubscriptionInfo> subscriptionInfos = new ArrayList<SubscriptionInfo>();

        doReturn(subscriptionInfos).when(mSubscriptionManager).getActiveSubscriptionInfoList(false);
        doReturn(onsConfigInput).when(mockONSConfigInputHashMap).get(CARRIER_APP_CONFIG_NAME);
        mOpportunisticNetworkService.mIsEnabled = true;
        mOpportunisticNetworkService.mONSConfigInputHashMap = mockONSConfigInputHashMap;

        mResult = -1;
        ISetOpportunisticDataCallback callbackStub = new ISetOpportunisticDataCallback.Stub() {
            @Override
            public void onComplete(int result) {
                Log.d(TAG, "callbackStub, mResult end:" + result);
                mResult = result;
            }
        };

        try {
            IOns onsBinder = (IOns)mOpportunisticNetworkService.onBind(null);
            onsBinder.setPreferredDataSubscriptionId(
                    SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, false, callbackStub,
                    pkgForDebug);
        } catch (RemoteException ex) {
            Log.e(TAG, "RemoteException", ex);
        }
        waitForMs(50);
        assertEquals(TelephonyManager.SET_OPPORTUNISTIC_SUB_VALIDATION_FAILED, mResult);
    }

    @Test
    public void testSetPreferredDataSubscriptionId() {
        mResult = -1;
        ISetOpportunisticDataCallback callbackStub = new ISetOpportunisticDataCallback.Stub() {
            @Override
            public void onComplete(int result) {
                Log.d(TAG, "callbackStub, mResult end:" + result);
                mResult = result;
            }
        };

        try {
            iOpportunisticNetworkService.setPreferredDataSubscriptionId(5, false, callbackStub,
                    pkgForDebug);
        } catch (RemoteException ex) {
            Log.e(TAG, "RemoteException", ex);
        }
        assertEquals(TelephonyManager.SET_OPPORTUNISTIC_SUB_INACTIVE_SUBSCRIPTION, mResult);
    }

    @Test
    public void testGetPreferredDataSubscriptionId() {
        assertNotNull(iOpportunisticNetworkService);
        mResult = -1;
        try {
            mResult = iOpportunisticNetworkService.getPreferredDataSubscriptionId(pkgForDebug);
            Log.d(TAG, "testGetPreferredDataSubscriptionId: " + mResult);
            assertNotNull(mResult);
        } catch (RemoteException ex) {
            Log.e(TAG, "RemoteException", ex);
        }
    }

    @Test
    public void testUpdateAvailableNetworksWithInvalidArguments() {
        mResult = -1;
        IUpdateAvailableNetworksCallback mCallback = new IUpdateAvailableNetworksCallback.Stub() {
            @Override
            public void onComplete(int result) {
                Log.d(TAG, "mResult end:" + result);
                mResult = result;
            }
        };

        ArrayList<String> mccMncs = new ArrayList<>();
        mccMncs.add("310210");
        AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(1, 1, mccMncs,
                new ArrayList<Integer>());
        ArrayList<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<>();
        availableNetworkInfos.add(availableNetworkInfo);

        try {
            iOpportunisticNetworkService.updateAvailableNetworks(availableNetworkInfos, mCallback,
                    pkgForDebug);
        } catch (RemoteException ex) {
            Log.e(TAG, "RemoteException", ex);
        }
        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_INVALID_ARGUMENTS, mResult);
    }

    @Test
    public void testUpdateAvailableNetworksWithSuccess() {
        mResult = -1;
        IUpdateAvailableNetworksCallback mCallback = new IUpdateAvailableNetworksCallback.Stub() {
            @Override
            public void onComplete(int result) {
                Log.d(TAG, "mResult end:" + result);
                mResult = result;
            }
        };
        ArrayList<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<>();
        try {
            iOpportunisticNetworkService.setEnable(false, pkgForDebug);
            iOpportunisticNetworkService.updateAvailableNetworks(availableNetworkInfos, mCallback,
                    pkgForDebug);
        } catch (RemoteException ex) {
            Log.e(TAG, "RemoteException", ex);
        }
        assertEquals(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS, mResult);
    }

    private IOns getIOns() {
        return IOns.Stub.asInterface(ServiceManager.getService("ions"));
    }

    public static void waitForMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Log.d(TAG, "InterruptedException while waiting: " + e);
        }
    }
}
