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

package com.android.car.dialer.ui.activecall;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.telecom.Call;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.android.car.arch.common.LiveDataFunctions;
import com.android.car.dialer.livedata.AudioRouteLiveData;
import com.android.car.dialer.livedata.CallDetailLiveData;
import com.android.car.dialer.livedata.CallStateLiveData;
import com.android.car.dialer.log.L;
import com.android.car.dialer.telecom.InCallServiceImpl;
import com.android.car.telephony.common.CallDetail;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * View model for {@link InCallActivity} and {@link OngoingCallFragment}. UI that doesn't belong to
 * in call page should use a different ViewModel.
 */
public class InCallViewModel extends AndroidViewModel implements
        InCallServiceImpl.ActiveCallListChangedCallback {
    private static final String TAG = "CD.InCallViewModel";

    private final MutableLiveData<List<Call>> mCallListLiveData;
    private final LiveData<List<Call>> mOngoingCallListLiveData;
    private final Comparator<Call> mCallComparator;

    private final LiveData<Call> mIncomingCallLiveData;

    private final LiveData<CallDetail> mCallDetailLiveData;
    private final LiveData<Integer> mCallStateLiveData;
    private final LiveData<Call> mPrimaryCallLiveData;
    private final LiveData<Call> mSecondaryCallLiveData;
    private final LiveData<CallDetail> mSecondaryCallDetailLiveData;
    private final LiveData<Integer> mAudioRouteLiveData;
    private LiveData<Long> mCallConnectTimeLiveData;
    private LiveData<Pair<Integer, Long>> mCallStateAndConnectTimeLiveData;
    private final Context mContext;

    private InCallServiceImpl mInCallService;
    private final ServiceConnection mInCallServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            L.d(TAG, "onServiceConnected: %s, service: %s", name, binder);
            mInCallService = ((InCallServiceImpl.LocalBinder) binder).getService();
            for (Call call : mInCallService.getCalls()) {
                call.registerCallback(mCallStateChangedCallback);
            }
            updateCallList();
            mInCallService.addActiveCallListChangedCallback(InCallViewModel.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            L.d(TAG, "onServiceDisconnected: %s", name);
            mInCallService = null;
        }
    };

    // Reuse the same instance so the callback won't be registered more than once.
    private final Call.Callback mCallStateChangedCallback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            // Sets value to trigger the live data for incoming call and active call list to update.
            mCallListLiveData.setValue(mCallListLiveData.getValue());
        }
    };

    public InCallViewModel(@NonNull Application application) {
        super(application);
        mContext = application.getApplicationContext();

        mCallListLiveData = new MutableLiveData<>();
        mCallComparator = new CallComparator();

        mIncomingCallLiveData = Transformations.map(mCallListLiveData,
                callList -> firstMatch(callList,
                        call -> call != null && call.getState() == Call.STATE_RINGING));

        mOngoingCallListLiveData = Transformations.map(mCallListLiveData,
                callList -> {
                    List<Call> activeCallList = filter(callList,
                            call -> call != null && call.getState() != Call.STATE_RINGING);
                    activeCallList.sort(mCallComparator);
                    return activeCallList;
                });

        mPrimaryCallLiveData = Transformations.map(mOngoingCallListLiveData,
                input -> input.isEmpty() ? null : input.get(0));
        mCallDetailLiveData = Transformations.switchMap(mPrimaryCallLiveData,
                input -> input != null ? new CallDetailLiveData(input) : null);
        mCallStateLiveData = Transformations.switchMap(mPrimaryCallLiveData,
                input -> input != null ? new CallStateLiveData(input) : null);
        mCallConnectTimeLiveData = Transformations.map(mCallDetailLiveData, (details) -> {
            if (details == null) {
                return 0L;
            }
            return details.getConnectTimeMillis();
        });
        mCallStateAndConnectTimeLiveData =
                LiveDataFunctions.pair(mCallStateLiveData, mCallConnectTimeLiveData);

        mSecondaryCallLiveData = Transformations.map(mOngoingCallListLiveData,
                callList -> (callList != null && callList.size() > 1) ? callList.get(1) : null);

        mSecondaryCallDetailLiveData = Transformations.switchMap(mSecondaryCallLiveData,
                input -> input != null ? new CallDetailLiveData(input) : null);

        mAudioRouteLiveData = new AudioRouteLiveData(mContext);

        Intent intent = new Intent(mContext, InCallServiceImpl.class);
        intent.setAction(InCallServiceImpl.ACTION_LOCAL_BIND);
        mContext.bindService(intent, mInCallServiceConnection, Context.BIND_AUTO_CREATE);
    }

    /** Returns the live data which monitors the current incoming call. */
    public LiveData<Call> getIncomingCall() {
        return mIncomingCallLiveData;
    }

    /** Returns {@link LiveData} for the ongoing call list which excludes the ringing call. */
    public LiveData<List<Call>> getOngoingCallList() {
        return mOngoingCallListLiveData;
    }

    /**
     * Returns the live data which monitors the primary call details.
     */
    public LiveData<CallDetail> getPrimaryCallDetail() {
        return mCallDetailLiveData;
    }

    /**
     * Returns the live data which monitors the primary call state.
     */
    public LiveData<Integer> getPrimaryCallState() {
        return mCallStateLiveData;
    }

    /**
     * Returns the live data which monitors the primary call state and the start time of the call.
     */
    public LiveData<Pair<Integer, Long>> getCallStateAndConnectTime() {
        return mCallStateAndConnectTimeLiveData;
    }

    /**
     * Returns the live data which monitor the primary call.
     * A primary call in the first call in the ongoing call list,
     * which is sorted based on {@link CallComparator}.
     */
    public LiveData<Call> getPrimaryCall() {
        return mPrimaryCallLiveData;
    }

    /**
     * Returns the live data which monitor the secondary call.
     * A secondary call in the second call in the ongoing call list,
     * which is sorted based on {@link CallComparator}.
     * The value will be null if there is no second call in the call list.
     */
    public LiveData<Call> getSecondaryCall() {
        return mSecondaryCallLiveData;
    }

    /**
     * Returns the live data which monitors the secondary call details.
     */
    public LiveData<CallDetail> getSecondaryCallDetail() {
        return mSecondaryCallDetailLiveData;
    }

    /**
     * Returns current audio route.
     */
    public LiveData<Integer> getAudioRoute() {
        return mAudioRouteLiveData;
    }

    @Override
    public boolean onTelecomCallAdded(Call telecomCall) {
        L.i(TAG, "onTelecomCallAdded %s %s", telecomCall, this);
        telecomCall.registerCallback(mCallStateChangedCallback);
        updateCallList();
        return false;
    }

    @Override
    public boolean onTelecomCallRemoved(Call telecomCall) {
        L.i(TAG, "onTelecomCallRemoved %s %s", telecomCall, this);
        telecomCall.unregisterCallback(mCallStateChangedCallback);
        updateCallList();
        return false;
    }

    private void updateCallList() {
        List<Call> callList = new ArrayList<>();
        callList.addAll(mInCallService.getCalls());
        mCallListLiveData.setValue(callList);
    }

    @Override
    protected void onCleared() {
        mContext.unbindService(mInCallServiceConnection);
        if (mInCallService != null) {
            for (Call call : mInCallService.getCalls()) {
                call.unregisterCallback(mCallStateChangedCallback);
            }
            mInCallService.removeActiveCallListChangedCallback(this);
        }
        mInCallService = null;
    }

    private static class CallComparator implements Comparator<Call> {
        /**
         * The rank of call state. Used for sorting active calls. Rank is listed from lowest to
         * highest.
         */
        private static final List<Integer> CALL_STATE_RANK = Lists.newArrayList(
                Call.STATE_RINGING,
                Call.STATE_DISCONNECTED,
                Call.STATE_DISCONNECTING,
                Call.STATE_NEW,
                Call.STATE_CONNECTING,
                Call.STATE_SELECT_PHONE_ACCOUNT,
                Call.STATE_HOLDING,
                Call.STATE_ACTIVE,
                Call.STATE_DIALING);

        @Override
        public int compare(Call call, Call otherCall) {
            boolean callHasParent = call.getParent() != null;
            boolean otherCallHasParent = otherCall.getParent() != null;

            if (callHasParent && !otherCallHasParent) {
                return 1;
            } else if (!callHasParent && otherCallHasParent) {
                return -1;
            }
            int carCallRank = CALL_STATE_RANK.indexOf(call.getState());
            int otherCarCallRank = CALL_STATE_RANK.indexOf(otherCall.getState());

            return otherCarCallRank - carCallRank;
        }
    }

    private static Call firstMatch(List<Call> callList, Predicate<Call> predicate) {
        List<Call> filteredResults = filter(callList, predicate);
        return filteredResults.isEmpty() ? null : filteredResults.get(0);
    }

    private static List<Call> filter(List<Call> callList, Predicate<Call> predicate) {
        if (callList == null || predicate == null) {
            return Collections.emptyList();
        }

        List<Call> filteredResults = new ArrayList<>();
        for (Call call : callList) {
            if (predicate.apply(call)) {
                filteredResults.add(call);
            }
        }
        return filteredResults;
    }
}
