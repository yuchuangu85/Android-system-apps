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
package android.car.cluster;

import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProviders;

import com.android.car.telephony.common.TelecomUtils;

/**
 * Displays ongoing call information.
 */
public class PhoneFragment extends Fragment {
    private View mUserProfileContainerView;

    private PhoneFragmentViewModel mViewModel;

    public PhoneFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        FragmentActivity activity = requireActivity();
        mViewModel = ViewModelProviders.of(activity).get(
                PhoneFragmentViewModel.class);

        View fragmentView = inflater.inflate(R.layout.fragment_phone, container, false);
        mUserProfileContainerView = fragmentView.findViewById(R.id.user_profile_container);

        TextView body = mUserProfileContainerView.findViewById(R.id.body);
        ImageView avatar = mUserProfileContainerView.findViewById(R.id.avatar);
        TextView nameView = mUserProfileContainerView.findViewById(R.id.title);

        mViewModel.getContactInfo().observe(getViewLifecycleOwner(), (contactInfo) -> {
            nameView.setText(contactInfo.getDisplayName());
            TelecomUtils.setContactBitmapAsync(getContext(),
                    avatar, contactInfo.getContact(), contactInfo.getNumber());
        });
        mViewModel.getBody().observe(getViewLifecycleOwner(), body::setText);
        mViewModel.getState().observe(getViewLifecycleOwner(), (state) -> {
            if (state == TelephonyManager.CALL_STATE_IDLE) {
                mUserProfileContainerView.setVisibility(View.GONE);
            } else {
                mUserProfileContainerView.setVisibility(View.VISIBLE);
            }
        });

        return fragmentView;
    }
}
