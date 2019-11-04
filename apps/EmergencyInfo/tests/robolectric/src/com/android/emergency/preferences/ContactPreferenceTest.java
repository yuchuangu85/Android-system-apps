/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.emergency.preferences;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import com.android.emergency.ContactTestUtils;
import com.android.emergency.EmergencyContactManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;

/** Unit tests for {@link ContactPreference}. */
@RunWith(RobolectricTestRunner.class)
public class ContactPreferenceTest {

    private static final String NAME = "Jake";
    private static final String PHONE_NUMBER = "123456";

    @Mock private ContactPreference.ContactFactory mContactFactory;
    @Mock private EmergencyContactManager.Contact mContact;
    private ContactPreference mPreference;
    private Uri mPhoneUri;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        final ContentResolver contentResolver = RuntimeEnvironment.application.getContentResolver();
        mPhoneUri = ContactTestUtils.createContact(contentResolver, NAME, PHONE_NUMBER);

        when(mContactFactory.getContact(any(), any())).thenReturn(mContact);
        when(mContact.getName()).thenReturn(NAME);
        when(mContact.getPhoneUri()).thenReturn(mPhoneUri);
        when(mContact.getPhoneNumber()).thenReturn(PHONE_NUMBER);
        when(mContact.getContactLookupUri()).thenReturn(mPhoneUri);

        final Activity activity = Robolectric.setupActivity(Activity.class);
        mPreference = new ContactPreference(activity, mPhoneUri, mContactFactory);
    }

    @Test
    public void testContactPreference() {
        assertThat(mPreference.getPhoneUri()).isEqualTo(mPhoneUri);
        assertThat(mPreference.getContact().getName()).isEqualTo(NAME);
        assertThat(mPreference.getContact().getPhoneNumber()).isEqualTo(PHONE_NUMBER);

        assertThat(mPreference.getRemoveContactDialog()).isNull();
        mPreference.setRemoveContactPreferenceListener(
            preference -> {
                // Do nothing
            });
        assertThat(mPreference.getRemoveContactDialog()).isNotNull();
    }

    @Test
    public void testDisplayContact() {
        mPreference.displayContact();

        final Intent expected = new Intent(Intent.ACTION_VIEW).setData(mPhoneUri);
        final Application application = RuntimeEnvironment.application;
        final Intent actual = Shadows.shadowOf(application).getNextStartedActivity();
        assertThat(actual.filterEquals(expected)).isTrue();
    }
}
