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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.android.car.dialer.CarDialerRobolectricTestRunner;
import com.android.car.dialer.R;
import com.android.car.dialer.telecom.UiCallManager;
import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.PhoneNumber;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

import java.util.Arrays;

@Config(qualifiers = "h610dp")
@RunWith(CarDialerRobolectricTestRunner.class)
public class ContactListViewHolderTest {
    private static final String DISPLAY_NAME = "Display Name";
    private static final String LABEL_1 = "Work";
    private static final String LABEL_2 = "Mobile";
    private static final String PHONE_NUMBER_1 = "6502530000";
    private static final String PHONE_NUMBER_2 = "6505550000";
    private static final int TYPE = ContactsContract.CommonDataKinds.Phone.TYPE_MAIN;

    private Context mContext;
    private View mItemView;
    private ContactListViewHolder mContactListViewHolder;
    @Mock
    private Contact mMockContact;
    @Mock
    private UiCallManager mMockUiCallManager;
    @Mock
    private ContactListAdapter.OnShowContactDetailListener mMockListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;

        mItemView = LayoutInflater.from(mContext)
                .inflate(R.layout.contact_list_item, null, false);
        mContactListViewHolder = new ContactListViewHolder(mItemView, mMockListener);
    }

    @Test
    public void testDisplayName() {
        when(mMockContact.getDisplayName()).thenReturn(DISPLAY_NAME);
        mContactListViewHolder.onBind(mMockContact, false, "");

        assertThat(((TextView) mItemView.findViewById(R.id.title)).getText()).isEqualTo(
                DISPLAY_NAME);
    }

    @Test
    public void testLabel_withOnlyOneNumber_showLabel() {
        PhoneNumber phoneNumber = PhoneNumber.newInstance(mContext, PHONE_NUMBER_1, 0, LABEL_1,
                false, 0, null, null, 0);
        when(mMockContact.getNumbers()).thenReturn(Arrays.asList(phoneNumber));
        mContactListViewHolder.onBind(mMockContact, false, "");

        assertThat(((TextView) mItemView.findViewById(R.id.text)).getText()).isEqualTo(LABEL_1);
    }

    @Test
    public void testLabel_withOneNumberAndNumberHasNullLabel_showTypeLabel() {
        PhoneNumber phoneNumber = PhoneNumber.newInstance(mContext, PHONE_NUMBER_1, TYPE, null,
                false, 0, null, null, 0);
        when(mMockContact.getNumbers()).thenReturn(Arrays.asList(phoneNumber));
        mContactListViewHolder.onBind(mMockContact, false, "");

        assertThat(((TextView) mItemView.findViewById(R.id.text)).getText()).isEqualTo(
                mContext.getResources().getText(
                        ContactsContract.CommonDataKinds.Phone.getTypeLabelResource(TYPE)));
    }

    @Test
    public void testLabel_withOneNumberAndGetNullLabel_showEmptyString() {
        // There will not be situations where PhoneNumber gets a null label.
        // But we keep this unit test to make sure the logic is correct.
        PhoneNumber phoneNumber = mock(PhoneNumber.class);
        when(phoneNumber.getLabel()).thenReturn(null);
        when(mMockContact.getNumbers()).thenReturn(Arrays.asList(phoneNumber));
        mContactListViewHolder.onBind(mMockContact, false, "");

        assertThat(((TextView) mItemView.findViewById(R.id.text)).getText()).isEqualTo("");
    }

    @Test
    public void testLabel_withMultipleNumbersAndNoPrimaryNumber_showMultipleLabel() {
        PhoneNumber phoneNumber1 = PhoneNumber.newInstance(mContext, PHONE_NUMBER_1, 0, LABEL_1,
                false, 0, null, null, 0);
        PhoneNumber phoneNumber2 = PhoneNumber.newInstance(mContext, PHONE_NUMBER_2, 0, LABEL_2,
                false, 0, null, null, 0);
        when(mMockContact.getNumbers()).thenReturn(Arrays.asList(phoneNumber1, phoneNumber2));
        when(mMockContact.hasPrimaryPhoneNumber()).thenReturn(false);
        mContactListViewHolder.onBind(mMockContact, false, "");

        assertThat(((TextView) mItemView.findViewById(R.id.text)).getText()).isEqualTo(
                mContext.getString(R.string.type_multiple));
    }

    @Test
    public void testLabel_withMultipleNumbersAndHasPrimaryNumber_showPrimaryNumberLabel() {
        PhoneNumber phoneNumber1 = PhoneNumber.newInstance(mContext, PHONE_NUMBER_1, 0, LABEL_1,
                false, 0, null, null, 0);
        PhoneNumber phoneNumber2 = PhoneNumber.newInstance(mContext, PHONE_NUMBER_2, 0, LABEL_2,
                false, 0, null, null, 0);
        when(mMockContact.getNumbers()).thenReturn(Arrays.asList(phoneNumber1, phoneNumber2));
        when(mMockContact.hasPrimaryPhoneNumber()).thenReturn(true);
        when(mMockContact.getPrimaryPhoneNumber()).thenReturn(phoneNumber2);
        mContactListViewHolder.onBind(mMockContact, false, "");

        assertThat(((TextView) mItemView.findViewById(R.id.text)).getText()).isEqualTo(
                mContext.getString(R.string.primary_number_description, LABEL_2));
    }

    @Test
    public void testLabel_HasPrimaryNumberAndPrimaryNumberHasNullLabel_showPrimaryNumberLabel() {
        PhoneNumber phoneNumber1 = PhoneNumber.newInstance(mContext, PHONE_NUMBER_1, 0, LABEL_1,
                false, 0, null, null, 0);
        PhoneNumber phoneNumber2 = PhoneNumber.newInstance(mContext, PHONE_NUMBER_2, TYPE, null,
                false, 0, null, null, 0);
        when(mMockContact.getNumbers()).thenReturn(Arrays.asList(phoneNumber1, phoneNumber2));
        when(mMockContact.hasPrimaryPhoneNumber()).thenReturn(true);
        when(mMockContact.getPrimaryPhoneNumber()).thenReturn(phoneNumber2);
        mContactListViewHolder.onBind(mMockContact, false, "");

        assertThat(((TextView) mItemView.findViewById(R.id.text)).getText()).isEqualTo(
                mContext.getString(R.string.primary_number_description,
                        mContext.getResources().getText(
                                ContactsContract.CommonDataKinds.Phone.getTypeLabelResource(
                                        TYPE))));
    }

    @Test
    public void testLabel_HasPrimaryNumberButGetNullLabel_showMultipleLabel() {
        // There will not be situations where PhoneNumber gets a null label.
        // But we keep this unit test to make sure the logic is correct.
        PhoneNumber phoneNumber1 = mock(PhoneNumber.class);
        PhoneNumber phoneNumber2 = mock(PhoneNumber.class);
        when(phoneNumber2.getLabel()).thenReturn(null);
        when(mMockContact.getNumbers()).thenReturn(Arrays.asList(phoneNumber1, phoneNumber2));
        when(mMockContact.hasPrimaryPhoneNumber()).thenReturn(true);
        when(mMockContact.getPrimaryPhoneNumber()).thenReturn(phoneNumber2);
        mContactListViewHolder.onBind(mMockContact, false, "");

        assertThat(((TextView) mItemView.findViewById(R.id.text)).getText()).isEqualTo(
                mContext.getString(R.string.primary_number_description, "null"));
    }

    @Test
    public void testClickCallActionButton_ContactHasOneNumber_placeCall() {
        UiCallManager.set(mMockUiCallManager);
        PhoneNumber phoneNumber = PhoneNumber.newInstance(mContext, PHONE_NUMBER_1, 0, LABEL_1,
                false, 0, null, null, 0);
        when(mMockContact.getNumbers()).thenReturn(Arrays.asList(phoneNumber));
        mContactListViewHolder.onBind(mMockContact, false, "");

        View callActionView = mItemView.findViewById(R.id.call_action_id);
        assertThat(callActionView.hasOnClickListeners()).isTrue();

        callActionView.performClick();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mMockUiCallManager).placeCall(captor.capture());
        assertThat(captor.getValue()).isEqualTo(PHONE_NUMBER_1);
    }

    @Test
    public void testClickCallActionButton_HasMultipleNumbersAndNoPrimaryNumber_showAlertDialog() {
        UiCallManager.set(mMockUiCallManager);
        PhoneNumber phoneNumber1 = PhoneNumber.newInstance(mContext, PHONE_NUMBER_1, 0, LABEL_1,
                false, 0, null, null, 0);
        PhoneNumber phoneNumber2 = PhoneNumber.newInstance(mContext, PHONE_NUMBER_2, 0, LABEL_2,
                false, 0, null, null, 0);
        when(mMockContact.getNumbers()).thenReturn(Arrays.asList(phoneNumber1, phoneNumber2));
        when(mMockContact.hasPrimaryPhoneNumber()).thenReturn(false);
        mContactListViewHolder.onBind(mMockContact, false, "");

        assertThat(ShadowAlertDialog.getLatestAlertDialog()).isNull();
        View callActionView = mItemView.findViewById(R.id.call_action_id);
        callActionView.performClick();

        verify(mMockUiCallManager, never()).placeCall(any());
        assertThat(ShadowAlertDialog.getLatestAlertDialog()).isNotNull();
    }

    @Test
    public void testClickCallActionButton_HasMultipleNumbersAndPrimaryNumber_callPrimaryNumber() {
        UiCallManager.set(mMockUiCallManager);
        PhoneNumber phoneNumber1 = PhoneNumber.newInstance(mContext, PHONE_NUMBER_1, 0, LABEL_1,
                false, 0, null, null, 0);
        PhoneNumber phoneNumber2 = PhoneNumber.newInstance(mContext, PHONE_NUMBER_2, 0, LABEL_2,
                false, 0, null, null, 0);
        when(mMockContact.getNumbers()).thenReturn(Arrays.asList(phoneNumber1, phoneNumber2));
        when(mMockContact.hasPrimaryPhoneNumber()).thenReturn(true);
        when(mMockContact.getPrimaryPhoneNumber()).thenReturn(phoneNumber2);
        mContactListViewHolder.onBind(mMockContact, false, "");

        View callActionView = mItemView.findViewById(R.id.call_action_id);
        assertThat(callActionView.hasOnClickListeners()).isTrue();

        callActionView.performClick();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mMockUiCallManager).placeCall(captor.capture());
        assertThat(captor.getValue()).isEqualTo(PHONE_NUMBER_2);
    }

    @Test
    public void testClickShowContactDetailView_showContactDetail() {
        mContactListViewHolder.onBind(mMockContact, false, "");

        View showContactDetailActionView = mItemView.findViewById(R.id.show_contact_detail_id);
        assertThat(showContactDetailActionView.hasOnClickListeners()).isTrue();

        showContactDetailActionView.performClick();

        ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
        verify(mMockListener).onShowContactDetail(captor.capture());
        assertThat(captor.getValue()).isEqualTo(mMockContact);
    }
}
