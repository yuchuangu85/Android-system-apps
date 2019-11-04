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

package com.android.car.dialer.ui.dialpad;

import android.app.ActionBar;
import android.os.Bundle;
import android.os.SystemClock;
import android.telecom.Call;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProviders;

import com.android.car.dialer.R;
import com.android.car.dialer.log.L;
import com.android.car.dialer.ui.activecall.InCallViewModel;
import com.android.car.telephony.common.TelecomUtils;

/** Dialpad fragment used in the ongoing call page. */
public class InCallDialpadFragment extends AbstractDialpadFragment {
    private static final String TAG = "CD.InCallDialpadFragment";

    private TextView mTitleView;
    private Chronometer mCallStateView;

    /** An active call which this fragment is serving for. */
    private LiveData<Call> mActiveCall;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.incall_dialpad_fragment,
                container,
                false);

        mTitleView = rootView.findViewById(R.id.title);
        mCallStateView = rootView.findViewById(R.id.call_state);

        InCallViewModel viewModel = ViewModelProviders.of(getActivity()).get(InCallViewModel.class);
        viewModel.getCallStateAndConnectTime().observe(this, (pair) -> {
            if (pair == null) {
                mCallStateView.stop();
                mCallStateView.setText("");
                return;
            }
            if (pair.first == Call.STATE_ACTIVE) {
                mCallStateView.setBase(pair.second
                        - System.currentTimeMillis() + SystemClock.elapsedRealtime());
                mCallStateView.start();
            } else {
                mCallStateView.stop();
                mCallStateView.setText(TelecomUtils.callStateToUiString(
                        getContext(), pair.first));
            }
        });
        mActiveCall = viewModel.getPrimaryCall();

        return rootView;
    }

    @Override
    void presentDialedNumber(@NonNull StringBuffer number) {
        if (getActivity() == null) {
            return;
        }

        if (number.length() == 0) {
            mTitleView.setText("");
        } else {
            mTitleView.setText(number);
        }
    }

    @Override
    void playTone(int keycode) {
        L.d(TAG, "start DTMF tone for %s", keycode);
        if (mActiveCall.getValue() != null) {
            mActiveCall.getValue().playDtmfTone(sDialValueMap.get(keycode));
        }
    }

    @Override
    void stopAllTones() {
        if (mActiveCall.getValue() != null) {
            L.d(TAG, "stop DTMF tone");
            mActiveCall.getValue().stopDtmfTone();
        }
    }

    @Override
    public void setupActionBar(ActionBar actionBar) {
        // No-op
    }

    @Override
    public void onKeypadKeyLongPressed(int keycode) {
        // No-op
    }
}
