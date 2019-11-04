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

import android.util.Pair;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.MutableLiveData;

import com.android.car.apps.common.widget.PagedRecyclerView;
import com.android.car.dialer.CarDialerRobolectricTestRunner;
import com.android.car.dialer.FragmentTestActivity;
import com.android.car.dialer.R;
import com.android.car.dialer.telecom.UiCallManager;
import com.android.car.dialer.testutils.ShadowAndroidViewModelFactory;
import com.android.car.dialer.ui.favorite.FavoriteViewModel;
import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.PhoneNumber;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

import java.util.Arrays;
import java.util.List;

@Config(shadows = {ShadowAndroidViewModelFactory.class}, qualifiers = "h610dp")
@RunWith(CarDialerRobolectricTestRunner.class)
public class ContactListFragmentTest {
    private static final String RAW_NUMBNER = "6502530000";

    private ContactListFragment mContactListFragment;
    private FragmentTestActivity mFragmentTestActivity;
    private ContactListViewHolder mViewHolder;
    @Mock
    private UiCallManager mMockUiCallManager;
    @Mock
    private ContactListViewModel mMockContactListViewModel;
    @Mock
    private ContactDetailsViewModel mMockContactDetailsViewModel;
    @Mock
    private FavoriteViewModel mMockFavoriteViewModel;
    @Mock
    private Contact mMockContact1;
    @Mock
    private Contact mMockContact2;
    @Mock
    private Contact mMockContact3;
    @Mock
    private PhoneNumber mMockPhoneNumber;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        MutableLiveData<Pair<Integer, List<Contact>>> contactList = new MutableLiveData<>();
        contactList.setValue(new Pair<>(ContactListViewModel.SORT_BY_LAST_NAME,
                Arrays.asList(mMockContact1, mMockContact2, mMockContact3)));
        ShadowAndroidViewModelFactory.add(ContactListViewModel.class, mMockContactListViewModel);
        when(mMockContactListViewModel.getAllContacts()).thenReturn(contactList);
        MutableLiveData<Contact> contactDetail = new MutableLiveData<>();
        contactDetail.setValue(mMockContact1);
        ShadowAndroidViewModelFactory.add(ContactDetailsViewModel.class,
                mMockContactDetailsViewModel);
        when(mMockContactDetailsViewModel.getContactDetails(any())).thenReturn(contactDetail);

        ShadowAndroidViewModelFactory.add(FavoriteViewModel.class, mMockFavoriteViewModel);
        when(mMockFavoriteViewModel.getFavoriteContacts()).thenReturn(new MutableLiveData<>());
    }

    @Test
    public void testClickCallActionButton_ContactHasOneNumber_placeCall() {
        UiCallManager.set(mMockUiCallManager);
        when(mMockContact1.getNumbers()).thenReturn(Arrays.asList(mMockPhoneNumber));
        when(mMockPhoneNumber.getRawNumber()).thenReturn(RAW_NUMBNER);
        setUpFragment();

        View callActionView = mViewHolder.itemView.findViewById(R.id.call_action_id);
        assertThat(callActionView.hasOnClickListeners()).isTrue();

        callActionView.performClick();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mMockUiCallManager).placeCall(captor.capture());
        assertThat(captor.getValue()).isEqualTo(RAW_NUMBNER);
    }

    @Test
    public void testClickCallActionButton_ContactHasMultipleNumbers_showAlertDialog() {
        PhoneNumber otherMockPhoneNumber = mock(PhoneNumber.class);
        when(mMockContact1.getNumbers()).thenReturn(
                Arrays.asList(mMockPhoneNumber, otherMockPhoneNumber));
        setUpFragment();

        assertThat(ShadowAlertDialog.getLatestAlertDialog()).isNull();
        View callActionView = mViewHolder.itemView.findViewById(R.id.call_action_id);
        callActionView.performClick();

        verify(mMockUiCallManager, never()).placeCall(any());
        assertThat(ShadowAlertDialog.getLatestAlertDialog()).isNotNull();
    }

    @Test
    public void testClickShowContactDetailView_showContactDetail() {
        setUpFragment();

        View showContactDetailActionView = mViewHolder.itemView.findViewById(
                R.id.show_contact_detail_id);
        assertThat(showContactDetailActionView.hasOnClickListeners()).isTrue();

        showContactDetailActionView.performClick();

        // verify contact detail is shown.
        verifyShowContactDetail();
    }

    private void setUpFragment() {
        mContactListFragment = ContactListFragment.newInstance();
        mFragmentTestActivity = Robolectric.buildActivity(
                FragmentTestActivity.class).create().resume().get();
        mFragmentTestActivity.setFragment(mContactListFragment);

        PagedRecyclerView recyclerView = mContactListFragment.getView()
                .findViewById(R.id.list_view);
        //Force RecyclerView to layout to ensure findViewHolderForLayoutPosition works.
        recyclerView.layoutBothForTesting(0, 0, 100, 1000);
        mViewHolder = (ContactListViewHolder) recyclerView.findViewHolderForLayoutPosition(0);
    }

    private void verifyShowContactDetail() {
        FragmentManager manager = mFragmentTestActivity.getSupportFragmentManager();
        String tag = manager.getBackStackEntryAt(manager.getBackStackEntryCount() - 1).getName();
        Fragment fragment = manager.findFragmentByTag(tag);
        assertThat(fragment instanceof ContactDetailsFragment).isTrue();
    }
}
