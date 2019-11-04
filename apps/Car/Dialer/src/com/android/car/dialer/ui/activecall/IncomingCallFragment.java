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

package com.android.car.dialer.ui.activecall;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProviders;

import com.android.car.dialer.R;
import com.android.car.telephony.common.CallDetail;

/** Fragment that presents the incoming call. */
public class IncomingCallFragment extends InCallFragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View fragmentView = inflater.inflate(R.layout.incoming_call_fragment, container, false);
        TextView mCallStateView = fragmentView.findViewById(R.id.user_profile_call_state);
        mCallStateView.setText(getString(R.string.call_state_call_ringing));

        InCallViewModel inCallViewModel = ViewModelProviders.of(getActivity()).get(
                InCallViewModel.class);
        inCallViewModel.getIncomingCall().observe(this, call -> bindUserProfileView(
                call == null ? null : CallDetail.fromTelecomCallDetail(call.getDetails())));
        return fragmentView;
    }
}
