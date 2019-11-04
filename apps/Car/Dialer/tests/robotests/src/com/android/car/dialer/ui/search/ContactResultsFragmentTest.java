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

package com.android.car.dialer.ui.search;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.MutableLiveData;

import com.android.car.apps.common.widget.PagedRecyclerView;
import com.android.car.dialer.CarDialerRobolectricTestRunner;
import com.android.car.dialer.FragmentTestActivity;
import com.android.car.dialer.R;
import com.android.car.dialer.testutils.ShadowAndroidViewModelFactory;
import com.android.car.dialer.ui.contact.ContactDetailsFragment;
import com.android.car.dialer.ui.contact.ContactDetailsViewModel;
import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.InMemoryPhoneBook;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

@Config(shadows = {ShadowAndroidViewModelFactory.class})
@RunWith(CarDialerRobolectricTestRunner.class)
public class ContactResultsFragmentTest {

    private static final String INITIAL_SEARCH_QUERY = "";
    private static final String[] DISPLAY_NAMES = {"name1", "name2", "name3"};

    private ContactResultsFragment mContactResultsFragment;
    private FragmentTestActivity mFragmentTestActivity;
    private PagedRecyclerView mListView;
    private MutableLiveData<List<Contact>> mContactSearchResultsLiveData;
    @Mock
    private ContactResultsViewModel mMockContactResultsViewModel;
    @Mock
    private ContactDetailsViewModel mMockContactDetailsViewModel;
    @Mock
    private Contact mMockContact;
    @Mock
    private Contact mContact1, mContact2, mContact3;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        InMemoryPhoneBook.init(RuntimeEnvironment.application);
        mContactSearchResultsLiveData = new MutableLiveData<>();
        when(mMockContactResultsViewModel.getContactSearchResults())
                .thenReturn(mContactSearchResultsLiveData);
        ShadowAndroidViewModelFactory.add(
                ContactResultsViewModel.class, mMockContactResultsViewModel);

        when(mContact1.getDisplayName()).thenReturn(DISPLAY_NAMES[0]);
        when(mContact2.getDisplayName()).thenReturn(DISPLAY_NAMES[1]);
        when(mContact3.getDisplayName()).thenReturn(DISPLAY_NAMES[2]);
    }

    @After
    public void tearDown() {
        InMemoryPhoneBook.tearDown();
    }

    @Test
    public void testDisplaySearchResults_emptyResult() {
        mContactResultsFragment = ContactResultsFragment.newInstance(INITIAL_SEARCH_QUERY);
        setUpFragment();

        assertThat(mListView.findViewHolderForLayoutPosition(0)).isNull();
    }

    @Test
    public void testDisplaySearchResults_multipleResults() {
        mContactSearchResultsLiveData.setValue(
                Arrays.asList(mContact1, mContact2, mContact3));

        mContactResultsFragment = ContactResultsFragment.newInstance(INITIAL_SEARCH_QUERY);
        setUpFragment();

        verifyChildAt(0);
        verifyChildAt(1);
        verifyChildAt(2);
    }

    @Test
    public void testClickSearchResult_showContactDetailPage() {
        mContactSearchResultsLiveData.setValue(
                Arrays.asList(mContact1, mContact2, mContact3));

        MutableLiveData<Contact> contactDetailLiveData = new MutableLiveData<>();
        contactDetailLiveData.setValue(mMockContact);
        ShadowAndroidViewModelFactory
                .add(ContactDetailsViewModel.class, mMockContactDetailsViewModel);
        when(mMockContactDetailsViewModel.getContactDetails(any()))
                .thenReturn(contactDetailLiveData);

        mContactResultsFragment = ContactResultsFragment.newInstance(INITIAL_SEARCH_QUERY);
        setUpFragment();

        mListView.findViewHolderForLayoutPosition(1).itemView.findViewById(R.id.contact_result)
                .performClick();

        // verify contact detail is shown.
        verifyShowContactDetail();
    }

    private void setUpFragment() {
        mFragmentTestActivity = Robolectric.buildActivity(
                FragmentTestActivity.class).create().resume().get();
        mFragmentTestActivity.setFragment(mContactResultsFragment);

        mListView = mContactResultsFragment.getView().findViewById(R.id.list_view);
        // Set up layout for recyclerView
        mListView.layoutBothForTesting(0, 0, 100, 1000);
    }

    private void verifyChildAt(int position) {
        View childView = mListView.findViewHolderForLayoutPosition(position).itemView;

        assertThat(childView).isNotNull();
        assertThat(childView.findViewById(R.id.contact_result).hasOnClickListeners()).isTrue();
        assertThat(((TextView) childView.findViewById(R.id.contact_name)).getText())
                .isEqualTo(DISPLAY_NAMES[position]);
    }

    private void verifyShowContactDetail() {
        FragmentManager manager = mFragmentTestActivity.getSupportFragmentManager();
        String tag = manager.getBackStackEntryAt(manager.getBackStackEntryCount() - 1).getName();
        Fragment fragment = manager.findFragmentByTag(tag);
        assertThat(fragment instanceof ContactDetailsFragment).isTrue();
    }
}
