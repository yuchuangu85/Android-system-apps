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

package com.android.car.telephony.common;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@RunWith(RobolectricTestRunner.class)
public class ContactTest {

    private static final int DISPLAY_NAME_COLUMN = 1;

    private static final String NULL_NAME = null;
    private static final String EMPTY_NAME = "";
    private static final String LETTER_NAME_1 = "test";
    private static final String LETTER_NAME_2 = "ta";
    private static final String DIGIT_NAME_1 = "123";
    private static final String DIGIT_NAME_2 = "321";
    private static final String SPEC_CHAR_NAME = "-";

    private static final int COMPARE_RESULT_EQUAL = 0;
    private static final int COMPARE_RESULT_SMALLER = -1;
    private static final int COMPARE_RESULT_LARGER = 1;

    @Mock
    private Context mMockContext;
    @Mock
    private Cursor mMockCursor;
    @Mock
    private TelephonyManager mMockTelephonyManager;

    private Contact mNullName;
    private Contact mEmptyName;
    private Contact mLetterName1;
    private Contact mLetterName2;
    private Contact mDigitName1;
    private Contact mDigitName2;
    private Contact mSpecCharName;

    private int mDisplayNameColumn;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        when(mMockCursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)).thenReturn(
                DISPLAY_NAME_COLUMN);
        when(mMockContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(
                mMockTelephonyManager);
        when(mMockTelephonyManager.getSimCountryIso()).thenReturn("");

        when(mMockCursor.getString(DISPLAY_NAME_COLUMN)).thenReturn(NULL_NAME);
        mNullName = Contact.fromCursor(mMockContext, mMockCursor);
        when(mMockCursor.getString(DISPLAY_NAME_COLUMN)).thenReturn(EMPTY_NAME);
        mEmptyName = Contact.fromCursor(mMockContext, mMockCursor);
        when(mMockCursor.getString(DISPLAY_NAME_COLUMN)).thenReturn(LETTER_NAME_1);
        mLetterName1 = Contact.fromCursor(mMockContext, mMockCursor);
        when(mMockCursor.getString(DISPLAY_NAME_COLUMN)).thenReturn(LETTER_NAME_2);
        mLetterName2 = Contact.fromCursor(mMockContext, mMockCursor);
        when(mMockCursor.getString(DISPLAY_NAME_COLUMN)).thenReturn(DIGIT_NAME_1);
        mDigitName1 = Contact.fromCursor(mMockContext, mMockCursor);
        when(mMockCursor.getString(DISPLAY_NAME_COLUMN)).thenReturn(DIGIT_NAME_2);
        mDigitName2 = Contact.fromCursor(mMockContext, mMockCursor);
        when(mMockCursor.getString(DISPLAY_NAME_COLUMN)).thenReturn(SPEC_CHAR_NAME);
        mSpecCharName = Contact.fromCursor(mMockContext, mMockCursor);
    }

    @Test
    public void compareTo_TwoNullStrings_Equal() {
        int compareResult = mNullName.compareTo(mNullName);
        assertEquals(COMPARE_RESULT_EQUAL, compareResult);
    }

    @Test
    public void compareTo_TwoEmptyStrings_Equal() {
        int compareResult = mEmptyName.compareTo(mEmptyName);
        assertEquals(COMPARE_RESULT_EQUAL, compareResult);
    }

    @Test
    public void compareTo_TwoLetterStrings_Larger() {
        int compareResult = mLetterName1.compareTo(mLetterName2);
        assertEquals(COMPARE_RESULT_LARGER, compareResult);
    }

    @Test
    public void compareTo_TwoDigitStrings_Smaller() {
        int compareResult = mDigitName1.compareTo(mDigitName2);
        assertEquals(COMPARE_RESULT_SMALLER, compareResult);
    }

    @Test
    public void compareTo_LetterAndDigitStrings_Smaller() {
        int compareResult = mLetterName1.compareTo(mDigitName1);
        assertEquals(COMPARE_RESULT_SMALLER, compareResult);
    }

    @Test
    public void compareTo_LetterAndSpecialCharStrings_Smaller() {
        int compareResult = mLetterName1.compareTo(mSpecCharName);
        assertEquals(COMPARE_RESULT_SMALLER, compareResult);
    }

    @Test
    public void compareTo_LetterAndEmptyStrings_Smaller() {
        int compareResult = mLetterName1.compareTo(mEmptyName);
        assertEquals(COMPARE_RESULT_SMALLER, compareResult);
    }

    @Test
    public void compareTo_LetterAndNullStrings_Smaller() {
        int compareResult = mLetterName1.compareTo(mNullName);
        assertEquals(COMPARE_RESULT_SMALLER, compareResult);
    }

    @Test
    public void compareTo_DigitAndSpecialCharStrings_Smaller() {
        int compareResult = mDigitName1.compareTo(mSpecCharName);
        assertEquals(COMPARE_RESULT_SMALLER, compareResult);
    }

    @Test
    public void compareTo_DigitAndEmptyStrings_Smaller() {
        int compareResult = mDigitName1.compareTo(mEmptyName);
        assertEquals(COMPARE_RESULT_SMALLER, compareResult);
    }

    @Test
    public void compareTo_DigitAndNullStrings_Smaller() {
        int compareResult = mDigitName1.compareTo(mNullName);
        assertEquals(COMPARE_RESULT_SMALLER, compareResult);
    }

    @Test
    public void compareTo_SpecialCharAndEmptyStrings_Any() {
        int compareResult = mSpecCharName.compareTo(mEmptyName);
        assertThat(compareResult).isAnyOf(COMPARE_RESULT_SMALLER, COMPARE_RESULT_EQUAL,
                COMPARE_RESULT_LARGER);
    }

    @Test
    public void compareTo_SpecialCharAndNullStrings_Any() {
        int compareResult = mSpecCharName.compareTo(mNullName);
        assertThat(compareResult).isAnyOf(COMPARE_RESULT_SMALLER, COMPARE_RESULT_EQUAL,
                COMPARE_RESULT_LARGER);
    }

    @Test
    public void compareTo_EmptyAndNullStrings_Equal() {
        int compareResult = mEmptyName.compareTo(mNullName);
        assertEquals(COMPARE_RESULT_EQUAL, compareResult);
    }

    @Test
    public void sortContactTest() {
        List<Contact> sortResultList = new ArrayList<>();
        sortResultList.add(mLetterName2);
        sortResultList.add(mLetterName1);
        sortResultList.add(mDigitName1);
        sortResultList.add(mDigitName2);
        sortResultList.add(mEmptyName);
        sortResultList.add(mSpecCharName);
        List<Contact> contactList = new ArrayList<>();
        contactList.addAll(sortResultList);

        Collections.shuffle(contactList);
        Collections.sort(contactList);
        assertArrayEquals(sortResultList.toArray(), contactList.toArray());
    }
}
