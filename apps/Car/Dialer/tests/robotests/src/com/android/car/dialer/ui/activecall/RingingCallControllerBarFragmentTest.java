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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.telecom.Call;
import android.view.View;

import androidx.lifecycle.MutableLiveData;

import com.android.car.dialer.CarDialerRobolectricTestRunner;
import com.android.car.dialer.FragmentTestActivity;
import com.android.car.dialer.R;
import com.android.car.dialer.testutils.ShadowAndroidViewModelFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

@Config(shadows = {ShadowAndroidViewModelFactory.class})
@RunWith(CarDialerRobolectricTestRunner.class)
public class RingingCallControllerBarFragmentTest {

    private RingingCallControllerBarFragment mRingingCallControllerBarFragment;
    @Mock
    private InCallViewModel mMockInCallViewModel;
    @Mock
    private Call mMockCall;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        MutableLiveData<Call> callLiveData = new MutableLiveData<>();
        callLiveData.setValue(mMockCall);
        when(mMockInCallViewModel.getIncomingCall()).thenReturn(callLiveData);
        ShadowAndroidViewModelFactory.add(InCallViewModel.class, mMockInCallViewModel);

        FragmentTestActivity fragmentTestActivity = Robolectric.buildActivity(
                FragmentTestActivity.class).create().start().resume().get();
        mRingingCallControllerBarFragment = new RingingCallControllerBarFragment();
        fragmentTestActivity.setFragment(mRingingCallControllerBarFragment);
    }

    @Test
    public void testAnswerCallButton() {
        View answerCallButton = mRingingCallControllerBarFragment.getView().findViewById(
                R.id.answer_call_button);
        assertThat(answerCallButton.hasOnClickListeners()).isTrue();

        answerCallButton.performClick();

        verifyAnswerCall();
    }

    @Test
    public void testAnswerCallText() {
        View answerCallText = mRingingCallControllerBarFragment.getView().findViewById(
                R.id.answer_call_text);
        assertThat(answerCallText.hasOnClickListeners()).isTrue();

        answerCallText.performClick();

        verifyAnswerCall();
    }

    @Test
    public void testEndCallButton() {
        View endCallButton = mRingingCallControllerBarFragment.getView().findViewById(
                R.id.end_call_button);
        assertThat(endCallButton.hasOnClickListeners()).isTrue();

        endCallButton.performClick();

        verifyRejectCall();
    }

    @Test
    public void testEndCallText() {
        View endCallText = mRingingCallControllerBarFragment.getView().findViewById(
                R.id.end_call_text);
        assertThat(endCallText.hasOnClickListeners()).isTrue();

        endCallText.performClick();

        verifyRejectCall();
    }

    private void verifyAnswerCall() {
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockCall).answer(captor.capture());
        assertThat(captor.getValue()).isEqualTo(/* videoState= */0);
    }

    private void verifyRejectCall() {
        ArgumentCaptor<Boolean> booleanCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        verify(mMockCall).reject(booleanCaptor.capture(), stringCaptor.capture());
        // Make sure to reject a call without a message.
        assertThat(booleanCaptor.getValue()).isFalse();
        // verify the text message after rejecting the call is null.
        assertThat(stringCaptor.getValue()).isNull();
    }
}
