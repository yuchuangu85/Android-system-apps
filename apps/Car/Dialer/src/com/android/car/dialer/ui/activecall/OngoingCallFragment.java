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
import android.telecom.Call;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProviders;

import com.android.car.apps.common.BackgroundImageView;
import com.android.car.dialer.R;

import com.google.common.annotations.VisibleForTesting;

/**
 * A fragment that displays information about an on-going call with options to hang up.
 */
public class OngoingCallFragment extends InCallFragment {
    private Fragment mDialpadFragment;
    private Fragment mOnholdCallFragment;
    private View mUserProfileContainerView;
    private BackgroundImageView mBackgroundImage;
    private MutableLiveData<Boolean> mDialpadState;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View fragmentView = inflater.inflate(R.layout.ongoing_call_fragment, container, false);

        mUserProfileContainerView = fragmentView.findViewById(R.id.user_profile_container);
        mBackgroundImage = fragmentView.findViewById(R.id.background_image);
        mOnholdCallFragment = getChildFragmentManager().findFragmentById(R.id.onhold_user_profile);
        mDialpadFragment = getChildFragmentManager().findFragmentById(R.id.incall_dialpad_fragment);

        InCallViewModel inCallViewModel = ViewModelProviders.of(getActivity()).get(
                InCallViewModel.class);

        inCallViewModel.getPrimaryCallDetail().observe(this, this::bindUserProfileView);
        inCallViewModel.getCallStateAndConnectTime().observe(this, this::updateCallDescription);
        inCallViewModel.getSecondaryCall().observe(this, this::maybeShowOnholdCallFragment);

        OngoingCallStateViewModel ongoingCallStateViewModel = ViewModelProviders.of(
                getActivity()).get(OngoingCallStateViewModel.class);
        mDialpadState = ongoingCallStateViewModel.getDialpadState();
        mDialpadState.setValue(savedInstanceState == null ? false : !mDialpadFragment.isHidden());
        mDialpadState.observe(this, isDialpadOpen -> {
            if (isDialpadOpen) {
                onOpenDialpad();
            } else {
                onCloseDialpad();
            }
        });
        return fragmentView;
    }

    @VisibleForTesting
    void onOpenDialpad() {
        getChildFragmentManager().beginTransaction()
                .show(mDialpadFragment)
                .commit();
        mUserProfileContainerView.setVisibility(View.GONE);
        mBackgroundImage.setDimmed(true);
    }

    @VisibleForTesting
    void onCloseDialpad() {
        getChildFragmentManager().beginTransaction()
                .hide(mDialpadFragment)
                .commit();
        mUserProfileContainerView.setVisibility(View.VISIBLE);
        mBackgroundImage.setDimmed(false);
    }

    private void maybeShowOnholdCallFragment(@Nullable Call secondaryCall) {
        if (secondaryCall == null || secondaryCall.getState() != Call.STATE_HOLDING) {
            getChildFragmentManager().beginTransaction().hide(mOnholdCallFragment).commit();
        } else {
            getChildFragmentManager().beginTransaction().show(mOnholdCallFragment).commit();
        }
    }
}
