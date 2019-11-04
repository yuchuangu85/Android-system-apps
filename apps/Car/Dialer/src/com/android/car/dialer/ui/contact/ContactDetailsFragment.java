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

package com.android.car.dialer.ui.contact;

import android.app.ActionBar;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProviders;

import com.android.car.dialer.R;
import com.android.car.dialer.ui.common.DialerListBaseFragment;
import com.android.car.dialer.ui.common.DialerUtils;
import com.android.car.dialer.ui.view.ContactAvatarOutputlineProvider;
import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.PhoneNumber;
import com.android.car.telephony.common.TelecomUtils;

/**
 * A fragment that shows the name of the contact, the photo and all listed phone numbers. It is
 * primarily used to respond to the results of search queries but supplyig it with the content://
 * uri of a contact should work too.
 */
public class ContactDetailsFragment extends DialerListBaseFragment implements
        ContactDetailsAdapter.PhoneNumberPresenter {
    private static final String TAG = "CD.ContactDetailsFragment";
    public static final String FRAGMENT_TAG = "CONTACT_DETAIL_FRAGMENT_TAG";

    // Key to load and save the contact entity instance.
    private static final String KEY_CONTACT_ENTITY = "ContactEntity";

    private Contact mContact;
    private LiveData<Contact> mContactDetailsLiveData;
    private ImageView mAvatarView;
    private TextView mNameView;
    private ContactDetailsViewModel mContactDetailsViewModel;

    /** Creates a new ContactDetailsFragment using a {@link Contact}. */
    public static ContactDetailsFragment newInstance(Contact contact) {
        ContactDetailsFragment fragment = new ContactDetailsFragment();
        Bundle args = new Bundle();
        args.putParcelable(KEY_CONTACT_ENTITY, contact);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mContact = getArguments().getParcelable(KEY_CONTACT_ENTITY);
        if (mContact == null && savedInstanceState != null) {
            mContact = savedInstanceState.getParcelable(KEY_CONTACT_ENTITY);
        }
        mContactDetailsViewModel = ViewModelProviders.of(this).get(
                ContactDetailsViewModel.class);
        mContactDetailsLiveData = mContactDetailsViewModel.getContactDetails(mContact);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_contacts_search).setVisible(false);
        menu.findItem(R.id.menu_dialer_setting).setVisible(false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        ContactDetailsAdapter contactDetailsAdapter = new ContactDetailsAdapter(getContext(),
                mContact, this);
        getRecyclerView().setAdapter(contactDetailsAdapter);
        mContactDetailsLiveData.observe(this, contact -> {
            mContact = contact;
            onContactChanged(contact);
            contactDetailsAdapter.setContact(contact);
        });
    }

    private void onContactChanged(Contact contact) {
        getArguments().clear();
        if (mAvatarView != null) {
            mAvatarView.setOutlineProvider(ContactAvatarOutputlineProvider.get());
            TelecomUtils.setContactBitmapAsync(getContext(), mAvatarView, contact, null);
        }

        if (mNameView != null) {
            if (contact != null) {
                mNameView.setText(contact.getDisplayName());
            } else {
                mNameView.setText(R.string.error_contact_deleted);
            }
        }
    }

    @Override
    public void setupActionBar(@NonNull ActionBar actionBar) {
        actionBar.setCustomView(R.layout.contact_details_action_bar);
        actionBar.setTitle(null);

        // Will set these to null on screen sizes that don't have them in the action bar
        View customView = actionBar.getCustomView();
        mAvatarView = customView.findViewById(R.id.contact_details_action_bar_avatar);
        mNameView = customView.findViewById(R.id.contact_details_action_bar_name);

        // Remove the action bar background on non-short screens
        // On short screens the avatar and name is in the action bar so we keep it
        if (mAvatarView == null) {
            setActionBarBackground(null);
            getRecyclerView().setScrollBarPadding(actionBar.getHeight(), 0);
        } else {
            getRecyclerView().setScrollBarPadding(0, 0);
        }
    }

    @Override
    protected int getTopOffset() {
        if (DialerUtils.isShortScreen(getContext())) {
            return super.getTopOffset();
        } else {
            return 0;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_CONTACT_ENTITY, mContactDetailsLiveData.getValue());
    }

    @Override
    public void onClick(Contact contact, PhoneNumber phoneNumber) {
        boolean isFavorite = phoneNumber.isFavorite();
        if (isFavorite) {
            mContactDetailsViewModel.removeFromFavorite(contact, phoneNumber);
        } else {
            mContactDetailsViewModel.addToFavorite(contact, phoneNumber);
        }
    }
}
