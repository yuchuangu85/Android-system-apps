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

package com.android.car.dialer.ui.contact;

import android.app.Application;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.car.dialer.storage.FavoriteNumberRepository;
import com.android.car.dialer.widget.WorkerExecutor;
import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.InMemoryPhoneBook;
import com.android.car.telephony.common.PhoneNumber;

import java.util.List;
import java.util.concurrent.Future;

/** View model for the contact details page. */
public class ContactDetailsViewModel extends AndroidViewModel {
    private final FavoriteNumberRepository mFavoriteNumberRepository;

    public ContactDetailsViewModel(@NonNull Application application) {
        super(application);
        mFavoriteNumberRepository = FavoriteNumberRepository.getRepository(application);
    }

    /**
     * Builds the {@link LiveData} for the given contact which will update upon contact change and
     * favorite repository change.
     *
     * @param contact The contact entry. It might be out of date and should update when the {@link
     *                InMemoryPhoneBook} changes. It always uses the in memory instance to get the
     *                favorite state for phone numbers.
     */
    public LiveData<Contact> getContactDetails(@Nullable Contact contact) {
        if (contact == null) {
            MutableLiveData<Contact> deletedContactDetailsLiveData = new MutableLiveData<>();
            deletedContactDetailsLiveData.setValue(null);
            return deletedContactDetailsLiveData;
        }

        return new ContactDetailsLiveData(getApplication(), contact);
    }

    /**
     * Adds the phone number to favorite.
     *
     * @param contact     The contact the phone number belongs to.
     * @param phoneNumber The phone number to add to favorite.
     */
    public void addToFavorite(Contact contact, PhoneNumber phoneNumber) {
        mFavoriteNumberRepository.addToFavorite(contact, phoneNumber);
    }

    /**
     * Removes the phone number from favorite.
     *
     * @param contact     The contact the phone number belongs to.
     * @param phoneNumber The phone number to remove from favorite.
     */
    public void removeFromFavorite(Contact contact, PhoneNumber phoneNumber) {
        mFavoriteNumberRepository.removeFromFavorite(contact, phoneNumber);
    }

    private class ContactDetailsLiveData extends MediatorLiveData<Contact> {
        private final WorkerExecutor mWorkerExecutor;
        private final Context mContext;
        private Contact mContact;
        private Future<?> mRunnableFuture;

        private ContactDetailsLiveData(Context context, Contact contact) {
            mContext = context;
            mWorkerExecutor = WorkerExecutor.getInstance();
            mContact = contact;
            addSource(InMemoryPhoneBook.get().getContactsLiveData(), this::onContactListChanged);
            addSource(mFavoriteNumberRepository.getFavoriteContacts(),
                    this::onFavoriteContactsChanged);
        }

        private void onContactListChanged(List<Contact> contacts) {
            if (mContact == null) {
                return;
            }

            Contact inMemoryContact = InMemoryPhoneBook.get().lookupContactByKey(
                    mContact.getLookupKey());
            if (inMemoryContact != null) {
                setValue(inMemoryContact);
                return;
            }

            if (mRunnableFuture != null) {
                mRunnableFuture.cancel(false);
            }
            mRunnableFuture = mWorkerExecutor.getSingleThreadExecutor().submit(
                    () -> {
                        Uri refreshedContactLookupUri = ContactsContract.Contacts.getLookupUri(
                                mContext.getContentResolver(), mContact.getLookupUri());
                        if (refreshedContactLookupUri == null) {
                            postValue(null);
                            return;
                        }
                        long contactId = ContentUris.parseId(refreshedContactLookupUri);
                        try (Cursor cursor = mContext.getContentResolver().query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                /* projection= */null,
                                /* selection= */
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ? ",
                                new String[]{String.valueOf(contactId)},
                                /* orderBy= */null)) {
                            if (cursor == null) {
                                postValue(null);
                                return;
                            }

                            if (cursor.moveToFirst()) {
                                String lookupKey = cursor.getString(cursor.getColumnIndex(
                                        ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY));
                                Contact contact = InMemoryPhoneBook.get().lookupContactByKey(
                                        lookupKey);
                                postValue(contact);
                            }
                        }
                    }
            );
        }

        private void onFavoriteContactsChanged(List<Contact> favoriteContacts) {
            if (mContact == null) {
                return;
            }
            Contact inMemoryContact = InMemoryPhoneBook.get().lookupContactByKey(
                    mContact.getLookupKey());
            setValue(inMemoryContact);
        }

        @Override
        public void setValue(Contact contact) {
            mContact = contact;
            super.setValue(contact);
        }

        @Override
        protected void onInactive() {
            super.onInactive();
            if (mRunnableFuture != null) {
                mRunnableFuture.cancel(true);
            }
        }
    }
}
