/**
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.dialer.ui.activecall;

import android.content.Intent;
import android.os.Bundle;
import android.telecom.Call;

import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProviders;

import com.android.car.arch.common.LiveDataFunctions;
import com.android.car.dialer.Constants;
import com.android.car.dialer.R;
import com.android.car.dialer.log.L;
import com.android.car.dialer.notification.InCallNotificationController;

import java.util.List;

/** Activity for ongoing call and incoming call. */
public class InCallActivity extends FragmentActivity {
    private static final String TAG = "CD.InCallActivity";
    private Fragment mOngoingCallFragment;
    private Fragment mIncomingCallFragment;

    private MutableLiveData<Boolean> mShowIncomingCall;
    private LiveData<Call> mIncomingCallLiveData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        L.d(TAG, "onCreate");

        setContentView(R.layout.in_call_activity);

        mOngoingCallFragment = getSupportFragmentManager().findFragmentById(
                R.id.ongoing_call_fragment);
        mIncomingCallFragment = getSupportFragmentManager().findFragmentById(
                R.id.incoming_call_fragment);

        mShowIncomingCall = new MutableLiveData();
        InCallViewModel inCallViewModel = ViewModelProviders.of(this).get(InCallViewModel.class);
        mIncomingCallLiveData = LiveDataFunctions.iff(mShowIncomingCall,
                inCallViewModel.getIncomingCall());
        mIncomingCallLiveData.observe(this, this::updateIncomingCallVisibility);
        LiveDataFunctions.pair(inCallViewModel.getOngoingCallList(), mIncomingCallLiveData).observe(
                this, this::maybeFinishActivity);

        handleIntent();
    }

    @Override
    protected void onStop() {
        super.onStop();
        L.d(TAG, "onStop");
        if (mShowIncomingCall.getValue()) {
            InCallNotificationController.get()
                    .showInCallNotification(mIncomingCallLiveData.getValue());
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        L.d(TAG, "onNewIntent");
        setIntent(i);
        handleIntent();
    }

    private void maybeFinishActivity(Pair<List<Call>, Call> callList) {
        if ((callList.first == null || callList.first.isEmpty()) && callList.second == null) {
            L.d(TAG, "No call to show. Finish InCallActivity");
            finish();
        }
    }

    private void handleIntent() {
        Intent intent = getIntent();

        if (intent != null && getIntent().getBooleanExtra(
                Constants.Intents.EXTRA_SHOW_INCOMING_CALL, false)) {
            mShowIncomingCall.setValue(true);
        } else {
            mShowIncomingCall.setValue(false);
        }
    }

    private void updateIncomingCallVisibility(Call incomingCall) {
        if (incomingCall == null) {
            getSupportFragmentManager().beginTransaction().show(mOngoingCallFragment).hide(
                    mIncomingCallFragment).commit();
            mShowIncomingCall.setValue(false);
            setIntent(null);
        } else {
            getSupportFragmentManager().beginTransaction().show(mIncomingCallFragment).hide(
                    mOngoingCallFragment).commit();
        }
    }
}
