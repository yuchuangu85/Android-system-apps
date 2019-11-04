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

package com.android.car.dialer.ui.contact;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.view.View;
import android.widget.TextView;

import androidx.lifecycle.MutableLiveData;

import com.android.car.apps.common.widget.PagedRecyclerView;
import com.android.car.dialer.CarDialerRobolectricTestRunner;
import com.android.car.dialer.FragmentTestActivity;
import com.android.car.dialer.R;
import com.android.car.dialer.telecom.UiCallManager;
import com.android.car.dialer.testutils.ShadowAndroidViewModelFactory;
import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.InMemoryPhoneBook;
import com.android.car.telephony.common.PhoneNumber;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;

@Config(shadows = {ShadowAndroidViewModelFactory.class}, qualifiers = "h610dp")
@RunWith(CarDialerRobolectricTestRunner.class)
public class ContactDetailsFragmentTest {
    private static final String DISPLAY_NAME = "NAME";
    private static final String[] RAW_NUMBERS = {"6505550000", "6502370000"};

    private ContactDetailsFragment mContactDetailsFragment;
    private FragmentTestActivity mFragmentTestActivity;
    private PagedRecyclerView mListView;
    @Mock
    private ContactDetailsViewModel mMockContactDetailsViewModel;
    @Mock
    private Contact mMockContact;
    @Mock
    private PhoneNumber mMockPhoneNumber1;
    @Mock
    private PhoneNumber mMockPhoneNumber2;
    @Mock
    private UiCallManager mMockUiCallManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        InMemoryPhoneBook.init(RuntimeEnvironment.application);

        when(mMockContact.getDisplayName()).thenReturn(DISPLAY_NAME);
        when(mMockPhoneNumber1.getRawNumber()).thenReturn(RAW_NUMBERS[0]);
        when(mMockPhoneNumber2.getRawNumber()).thenReturn(RAW_NUMBERS[1]);
        when(mMockContact.getNumbers()).thenReturn(
                Arrays.asList(mMockPhoneNumber1, mMockPhoneNumber2));

        UiCallManager.set(mMockUiCallManager);

        MutableLiveData<Contact> contactDetails = new MutableLiveData<>();
        contactDetails.setValue(mMockContact);
        ShadowAndroidViewModelFactory.add(ContactDetailsViewModel.class,
                mMockContactDetailsViewModel);
        when(mMockContactDetailsViewModel.getContactDetails(mMockContact)).thenReturn(
                contactDetails);
    }

    @After
    public void tearDown() {
        InMemoryPhoneBook.tearDown();
    }

    @Test
    public void testCreateWithContact() {
        mContactDetailsFragment = ContactDetailsFragment.newInstance(mMockContact);

        setUpFragment();

        verifyHeader();
        verifyPhoneNumber(1);
        verifyPhoneNumber(2);
    }

    private void setUpFragment() {
        mFragmentTestActivity = Robolectric.buildActivity(
                FragmentTestActivity.class).create().resume().get();
        mFragmentTestActivity.setFragment(mContactDetailsFragment);

        mListView = mContactDetailsFragment.getView().findViewById(R.id.list_view);
        // Set up layout for recyclerView
        mListView.layoutBothForTesting(0, 0, 100, 1000);
    }

    /**
     * Verify the title of the Contact
     */
    private void verifyHeader() {
        View firstChild = mListView.findViewHolderForLayoutPosition(0).itemView;
        assertThat(((TextView) firstChild.findViewById(R.id.title)).getText().toString()).isEqualTo(
                DISPLAY_NAME);
        assertThat(firstChild.hasOnClickListeners()).isFalse();
    }

    /**
     * Verify the phone numbers for the Contact
     */
    private void verifyPhoneNumber(int position) {
        View child = mListView.findViewHolderForLayoutPosition(position).itemView;
        View callButton = child.findViewById(R.id.call_action_id);

        assertThat(((TextView) child.findViewById(R.id.title)).getText().toString()).isEqualTo(
                RAW_NUMBERS[position - 1]);
        assertThat(callButton.hasOnClickListeners()).isTrue();

        int invocations = Mockito.mockingDetails(mMockUiCallManager).getInvocations().size();

        callButton.performClick();

        verify(mMockUiCallManager, times(invocations + 1)).placeCall(Mockito.any());
    }
}
