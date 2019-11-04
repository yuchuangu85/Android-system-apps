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

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.view.View;

import androidx.fragment.app.Fragment;

import com.android.car.dialer.CarDialerRobolectricTestRunner;
import com.android.car.dialer.FragmentTestActivity;
import com.android.car.dialer.R;
import com.android.car.dialer.TestDialerApplication;
import com.android.car.dialer.telecom.UiCallManager;
import com.android.car.telephony.common.InMemoryPhoneBook;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;

@RunWith(CarDialerRobolectricTestRunner.class)
public class OngoingCallFragmentTest {

    private OngoingCallFragment mOngoingCallFragment;
    private FragmentTestActivity mFragmentTestActivity;
    private View mUserProfileContainerView;
    private Fragment mInCallDialpadFragment;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        Context context = RuntimeEnvironment.application;
        ((TestDialerApplication) context).setupInCallServiceImpl();
        ((TestDialerApplication) context).initUiCallManager();
        InMemoryPhoneBook.init(context);

        mOngoingCallFragment = new OngoingCallFragment();
        mFragmentTestActivity = Robolectric.buildActivity(
                FragmentTestActivity.class).create().start().resume().get();
        mFragmentTestActivity.setFragment(mOngoingCallFragment);

        mUserProfileContainerView = mOngoingCallFragment.getView().findViewById(
                R.id.user_profile_container);
        mInCallDialpadFragment = mOngoingCallFragment.getChildFragmentManager().findFragmentById(
                R.id.incall_dialpad_fragment);
    }

    @After
    public void tearDown() {
        UiCallManager.get().tearDown();
        InMemoryPhoneBook.tearDown();
    }

    @Test
    public void testOnCreateView() {
        assertThat(mInCallDialpadFragment.isHidden()).isTrue();
        assertThat(mUserProfileContainerView.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testOnOpenDialpad() {
        mOngoingCallFragment.onOpenDialpad();

        assertThat(mInCallDialpadFragment.isHidden()).isFalse();
        assertThat(mUserProfileContainerView.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testOnCloseDialpad() {
        mOngoingCallFragment.onCloseDialpad();

        assertThat(mInCallDialpadFragment.isHidden()).isTrue();
        assertThat(mUserProfileContainerView.getVisibility()).isEqualTo(View.VISIBLE);
    }
}
