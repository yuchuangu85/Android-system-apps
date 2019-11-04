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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.telecom.Call;
import android.widget.TextView;

import androidx.lifecycle.MutableLiveData;

import com.android.car.dialer.CarDialerRobolectricTestRunner;
import com.android.car.dialer.FragmentTestActivity;
import com.android.car.dialer.R;
import com.android.car.dialer.testutils.ShadowAndroidViewModelFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

@Config(shadows = {ShadowAndroidViewModelFactory.class})
@RunWith(CarDialerRobolectricTestRunner.class)
public class IncomingCallFragmentTest {
    private IncomingCallFragment mIncomingCallFragment;
    @Mock
    private Call mMockCall;
    @Mock
    private InCallViewModel mMockInCallViewModel;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        ShadowAndroidViewModelFactory.add(InCallViewModel.class, mMockInCallViewModel);

        MutableLiveData<Call> callLiveData = new MutableLiveData<>();
        callLiveData.setValue(mMockCall);
        when(mMockInCallViewModel.getIncomingCall()).thenReturn(callLiveData);

        FragmentTestActivity fragmentTestActivity = Robolectric.buildActivity(
                FragmentTestActivity.class).create().start().resume().get();
        mIncomingCallFragment = new IncomingCallFragment();
        fragmentTestActivity.setFragment(mIncomingCallFragment);
    }

    @Test
    public void testCallStateIsRinging() {
        TextView callStateView = mIncomingCallFragment.getView().findViewById(
                R.id.user_profile_call_state);

        assertThat(callStateView.getText()).isEqualTo(
                mIncomingCallFragment.getResources().getString(
                        R.string.call_state_call_ringing));
    }
}
