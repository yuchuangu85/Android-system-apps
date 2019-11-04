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

import static com.google.common.truth.Truth.assertThat;

import android.widget.TextView;

import com.android.car.dialer.CarDialerRobolectricTestRunner;
import com.android.car.dialer.FragmentTestActivity;
import com.android.car.dialer.R;
import com.android.car.dialer.TestDialerApplication;
import com.android.car.dialer.testutils.ShadowCallLogCalls;
import com.android.car.dialer.testutils.ShadowInMemoryPhoneBook;
import com.android.car.dialer.ui.activecall.OngoingCallFragment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(CarDialerRobolectricTestRunner.class)
@Config(shadows = {ShadowCallLogCalls.class, ShadowInMemoryPhoneBook.class})
public class InCallDialpadFragmentTest {

    private InCallDialpadFragment mInCallDialpadFragment;

    @Before
    public void setup() {
        ((TestDialerApplication) RuntimeEnvironment.application).setupInCallServiceImpl();
        ((TestDialerApplication) RuntimeEnvironment.application).initUiCallManager();
    }

    @Test
    public void testOnCreateView_modeInCall() {
        startInCallActivity();

        TextView mTitleView = mInCallDialpadFragment.getView().findViewById(R.id.title);
        assertThat(mTitleView.getText()).isEqualTo("");
    }

    private void startInCallActivity() {
        OngoingCallFragment ongoingCallFragment = new OngoingCallFragment();
        FragmentTestActivity fragmentTestActivity = Robolectric.buildActivity(
                FragmentTestActivity.class).create().start().resume().get();
        fragmentTestActivity.setFragment(ongoingCallFragment);
        mInCallDialpadFragment =
                (InCallDialpadFragment) ongoingCallFragment.getChildFragmentManager()
                        .findFragmentById(R.id.incall_dialpad_fragment);
    }

}
