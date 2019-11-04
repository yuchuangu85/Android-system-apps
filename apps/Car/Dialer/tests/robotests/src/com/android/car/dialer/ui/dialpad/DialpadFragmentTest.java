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

import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.android.car.dialer.CarDialerRobolectricTestRunner;
import com.android.car.dialer.FragmentTestActivity;
import com.android.car.dialer.R;
import com.android.car.dialer.TestDialerApplication;
import com.android.car.dialer.telecom.UiCallManager;
import com.android.car.dialer.testutils.ShadowCallLogCalls;
import com.android.car.dialer.testutils.ShadowInMemoryPhoneBook;
import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.InMemoryPhoneBook;
import com.android.car.telephony.common.TelecomUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

@RunWith(CarDialerRobolectricTestRunner.class)
@Config(shadows = {ShadowCallLogCalls.class, ShadowInMemoryPhoneBook.class})
public class DialpadFragmentTest {
    private static final String DIAL_NUMBER = "6505551234";
    private static final String DIAL_NUMBER_LONG = "650555123465055512346505551234";
    private static final String SINGLE_DIGIT = "0";
    private static final String SPEC_CHAR = "123=_=%^&";
    private static final String DISPALY_NAME = "Display Name";

    private DialpadFragment mDialpadFragment;
    @Mock
    private Contact mMockContact;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        Context context = RuntimeEnvironment.application;
        ((TestDialerApplication) context).setupInCallServiceImpl();
        ((TestDialerApplication) context).initUiCallManager();
        InMemoryPhoneBook.init(context);
    }

    @After
    public void tearDown() {
        UiCallManager.get().tearDown();
        InMemoryPhoneBook.tearDown();
    }

    @Test
    public void testOnCreateView_modeDialWithNormalDialNumber() {
        mDialpadFragment = DialpadFragment.newPlaceCallDialpad();
        startPlaceCallActivity();
        mDialpadFragment.setDialedNumber(DIAL_NUMBER);

        verifyButtonVisibility(View.VISIBLE, View.VISIBLE);
        verifyTitleText(DIAL_NUMBER);
    }

    @Test
    public void testOnCreateView_modeDialWithLongDialNumber() {
        mDialpadFragment = DialpadFragment.newPlaceCallDialpad();
        startPlaceCallActivity();
        mDialpadFragment.setDialedNumber(DIAL_NUMBER_LONG);

        verifyButtonVisibility(View.VISIBLE, View.VISIBLE);
        verifyTitleText(DIAL_NUMBER_LONG.substring(
                DIAL_NUMBER_LONG.length() - DialpadFragment.MAX_DIAL_NUMBER));
    }

    @Test
    public void testOnCreateView_modeDialWithNullDialNumber() {
        mDialpadFragment = DialpadFragment.newPlaceCallDialpad();
        startPlaceCallActivity();
        mDialpadFragment.setDialedNumber(null);

        verifyButtonVisibility(View.VISIBLE, View.GONE);
        verifyTitleText(mDialpadFragment.getContext().getString(R.string.dial_a_number));
    }

    @Test
    public void testOnCreateView_modeDialWithEmptyDialNumber() {
        mDialpadFragment = DialpadFragment.newPlaceCallDialpad();
        startPlaceCallActivity();
        mDialpadFragment.setDialedNumber("");

        verifyButtonVisibility(View.VISIBLE, View.GONE);
        verifyTitleText(mDialpadFragment.getContext().getString(R.string.dial_a_number));
    }

    @Test
    public void testOnCreateView_modeDialWithSpecialChar() {
        mDialpadFragment = DialpadFragment.newPlaceCallDialpad();
        startPlaceCallActivity();
        mDialpadFragment.setDialedNumber(SPEC_CHAR);

        verifyButtonVisibility(View.VISIBLE, View.VISIBLE);
        verifyTitleText(SPEC_CHAR);
    }

    @Test
    public void testDeleteButton_normalString() {
        mDialpadFragment = DialpadFragment.newPlaceCallDialpad();
        startPlaceCallActivity();
        mDialpadFragment.setDialedNumber(DIAL_NUMBER);

        ImageButton deleteButton = mDialpadFragment.getView().findViewById(R.id.delete_button);
        deleteButton.performClick();

        verifyTitleText(DIAL_NUMBER.substring(0, DIAL_NUMBER.length() - 1));
    }

    @Test
    public void testDeleteButton_oneDigit() {
        mDialpadFragment = DialpadFragment.newPlaceCallDialpad();
        startPlaceCallActivity();
        mDialpadFragment.setDialedNumber(SINGLE_DIGIT);

        ImageButton deleteButton = mDialpadFragment.getView().findViewById(R.id.delete_button);
        deleteButton.performClick();
        verifyTitleText(mDialpadFragment.getContext().getString(R.string.dial_a_number));
    }

    @Test
    public void testDeleteButton_emptyString() {
        mDialpadFragment = DialpadFragment.newPlaceCallDialpad();
        startPlaceCallActivity();
        mDialpadFragment.setDialedNumber("");

        ImageButton deleteButton = mDialpadFragment.getView().findViewById(R.id.delete_button);
        deleteButton.performClick();
        verifyTitleText(mDialpadFragment.getContext().getString(R.string.dial_a_number));
    }

    @Test
    public void testLongPressDeleteButton() {
        mDialpadFragment = DialpadFragment.newPlaceCallDialpad();
        startPlaceCallActivity();
        mDialpadFragment.setDialedNumber(DIAL_NUMBER);

        ImageButton deleteButton = mDialpadFragment.getView().findViewById(R.id.delete_button);

        deleteButton.performLongClick();
        verifyTitleText(mDialpadFragment.getContext().getString(R.string.dial_a_number));
    }

    @Test
    public void testCallButton_emptyString() {
        ShadowCallLogCalls.setLastOutgoingCall(DIAL_NUMBER);

        mDialpadFragment = DialpadFragment.newPlaceCallDialpad();
        startPlaceCallActivity();
        mDialpadFragment.setDialedNumber("");

        View callButton = mDialpadFragment.getView().findViewById(R.id.call_button);
        callButton.performClick();
        verifyTitleText(DIAL_NUMBER);
    }

    @Test
    public void testOnKeyLongPressed_KeyCode0() {
        mDialpadFragment = DialpadFragment.newPlaceCallDialpad();
        startPlaceCallActivity();
        mDialpadFragment.setDialedNumber(DIAL_NUMBER);

        mDialpadFragment.onKeypadKeyLongPressed(KeyEvent.KEYCODE_0);
        verifyTitleText(DIAL_NUMBER.substring(0, DIAL_NUMBER.length() - 1) + "+");
    }

    @Test
    public void testDisplayName() {
        ShadowInMemoryPhoneBook phoneBook = Shadow.extract(InMemoryPhoneBook.get());
        when(mMockContact.getDisplayName()).thenReturn(DISPALY_NAME);
        phoneBook.add(DIAL_NUMBER, mMockContact);

        mDialpadFragment = DialpadFragment.newPlaceCallDialpad();
        startPlaceCallActivity();
        mDialpadFragment.setDialedNumber(DIAL_NUMBER);

        TextView displayName = mDialpadFragment.getView().findViewById(R.id.display_name);
        assertThat(displayName.getText()).isEqualTo(DISPALY_NAME);
    }

    private void startPlaceCallActivity() {
        FragmentTestActivity fragmentTestActivity;
        fragmentTestActivity = Robolectric.buildActivity(FragmentTestActivity.class)
                .create().start().resume().get();
        fragmentTestActivity.setFragment(mDialpadFragment);
    }

    private void verifyButtonVisibility(int callButtonVisibility, int deleteButtonVisibility) {
        View callButton = mDialpadFragment.getView().findViewById(R.id.call_button);
        ImageButton deleteButton = mDialpadFragment.getView().findViewById(R.id.delete_button);

        assertThat(callButton.getVisibility()).isEqualTo(callButtonVisibility);
        assertThat(deleteButton.getVisibility()).isEqualTo(deleteButtonVisibility);
    }

    private void verifyTitleText(String expectedText) {
        expectedText = TelecomUtils.getFormattedNumber(mDialpadFragment.getContext(), expectedText);
        TextView mTitleView = mDialpadFragment.getView().findViewById(R.id.title);
        TelecomUtils.getFormattedNumber(mDialpadFragment.getContext(), null);
        assertThat(mTitleView.getText().toString()).isEqualTo(expectedText);
    }
}
