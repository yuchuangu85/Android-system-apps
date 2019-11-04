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
package com.android.phone;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.mockito.Mockito.when;

import androidx.test.filters.FlakyTest;
import androidx.test.rule.ActivityTestRule;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;

public class CallFeaturesSettingTest {
    @Mock
    Phone mMockPhone;
    @Mock
    IccCard mMockIccCard;
    @Rule
    public ActivityTestRule<CallFeaturesSetting> mRule =
            new ActivityTestRule<>(CallFeaturesSetting.class);
    private CallFeaturesSetting mActivity;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mActivity = mRule.getActivity();
    }

    @FlakyTest
    @Test
    public void onResume_fdnIsAvailable_shouldShowFdnMenu() throws NoSuchFieldException,
            IllegalAccessException {
        when(mMockPhone.getPhoneType()).thenReturn(PhoneConstants.PHONE_TYPE_GSM);
        when(mMockPhone.getIccCard()).thenReturn(mMockIccCard);
        when(mMockIccCard.getIccFdnAvailable()).thenReturn(true);
        getField("mPhone").set(mActivity, mMockPhone);

        mActivity.onResume();

        // Check the FDN menu is displayed.
        onView(withText(R.string.fdn)).check(matches(isDisplayed()));
    }

    @FlakyTest
    @Test
    public void onResume_iccCardIsNull_shouldNotShowFdnMenu() throws NoSuchFieldException,
            IllegalAccessException {
        when(mMockPhone.getPhoneType()).thenReturn(PhoneConstants.PHONE_TYPE_GSM);
        when(mMockPhone.getIccCard()).thenReturn(null);
        getField("mPhone").set(mActivity, mMockPhone);

        mActivity.onResume();

        // Check the FDN menu is not displayed.
        onView(withText(R.string.fdn)).check(doesNotExist());
    }

    @FlakyTest
    @Test
    public void onResume_fdnIsNotAvailable_shouldNotShowFdnMenu() throws NoSuchFieldException,
            IllegalAccessException {
        when(mMockPhone.getPhoneType()).thenReturn(PhoneConstants.PHONE_TYPE_GSM);
        when(mMockPhone.getIccCard()).thenReturn(mMockIccCard);
        when(mMockIccCard.getIccFdnAvailable()).thenReturn(false);
        getField("mPhone").set(mActivity, mMockPhone);

        mActivity.onResume();

        // Check the FDN menu is not displayed.
        onView(withText(R.string.fdn)).check(doesNotExist());
    }

    private Field getField(String fieldName) throws NoSuchFieldException {
        Field field = mActivity.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }
}
