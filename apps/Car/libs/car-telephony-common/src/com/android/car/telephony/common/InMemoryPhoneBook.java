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

package com.android.car.telephony.common;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A singleton statically accessible helper class which pre-loads contacts list into memory so
 * that they can be accessed more easily and quickly.
 */
public class InMemoryPhoneBook implements Observer<List<Contact>> {
    private static final String TAG = "CD.InMemoryPhoneBook";
    private static InMemoryPhoneBook sInMemoryPhoneBook;

    private final Context mContext;
    private final AsyncQueryLiveData<List<Contact>> mContactListAsyncQueryLiveData;
    /** A map to speed up phone number searching. */
    private final Map<I18nPhoneNumberWrapper, Contact> mPhoneNumberContactMap = new HashMap<>();
    /** A map to look up contact by lookup key. */
    private final Map<String, Contact> mLookupKeyContactMap = new HashMap<>();
    private boolean mIsLoaded = false;

    /**
     * Initialize the globally accessible {@link InMemoryPhoneBook}.
     * Returns the existing {@link InMemoryPhoneBook} if already initialized.
     * {@link #tearDown()} must be called before init to reinitialize.
     */
    public static InMemoryPhoneBook init(Context context) {
        if (sInMemoryPhoneBook == null) {
            sInMemoryPhoneBook = new InMemoryPhoneBook(context);
            sInMemoryPhoneBook.onInit();
        }
        return get();
    }

    /**
     * Returns if the InMemoryPhoneBook is initialized.
     * get() won't return null or throw if this is true, but it doesn't
     * indicate whether or not contacts are loaded yet.
     *
     * See also: {@link #isLoaded()}
     */
    public static boolean isInitialized() {
        return sInMemoryPhoneBook != null;
    }

    /** Get the global {@link InMemoryPhoneBook} instance. */
    public static InMemoryPhoneBook get() {
        if (sInMemoryPhoneBook != null) {
            return sInMemoryPhoneBook;
        } else {
            throw new IllegalStateException("Call init before get InMemoryPhoneBook");
        }
    }

    /** Tears down the globally accessible {@link InMemoryPhoneBook}. */
    public static void tearDown() {
        sInMemoryPhoneBook.onTearDown();
        sInMemoryPhoneBook = null;
    }

    private InMemoryPhoneBook(Context context) {
        mContext = context;

        // TODO(b/138749585): clean up filtering once contact cloud sync is disabled.
        QueryParam contactListQueryParam = new QueryParam(
                ContactsContract.Data.CONTENT_URI,
                null,
                ContactsContract.Data.MIMETYPE + " = ? and "
                        + ContactsContract.RawContacts.ACCOUNT_TYPE + " != ?",
                new String[]{
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, "com.google"},
                ContactsContract.Contacts.DISPLAY_NAME + " ASC ");
        mContactListAsyncQueryLiveData = new AsyncQueryLiveData<List<Contact>>(mContext,
                QueryParam.of(contactListQueryParam)) {
            @Override
            protected List<Contact> convertToEntity(Cursor cursor) {
                return onCursorLoaded(cursor);
            }
        };
    }

    private void onInit() {
        mContactListAsyncQueryLiveData.observeForever(this);
    }

    private void onTearDown() {
        mContactListAsyncQueryLiveData.removeObserver(this);
    }

    public boolean isLoaded() {
        return mIsLoaded;
    }

    /**
     * Returns a {@link LiveData} which monitors the contact list changes.
     */
    public LiveData<List<Contact>> getContactsLiveData() {
        return mContactListAsyncQueryLiveData;
    }

    /**
     * Looks up a {@link Contact} by the given phone number. Returns null if can't find a Contact or
     * the {@link InMemoryPhoneBook} is still loading.
     */
    @Nullable
    public Contact lookupContactEntry(String phoneNumber) {
        Log.v(TAG, String.format("lookupContactEntry: %s", phoneNumber));
        if (!isLoaded()) {
            Log.w(TAG, "looking up a contact while loading.");
        }

        if (TextUtils.isEmpty(phoneNumber)) {
            Log.w(TAG, "looking up an empty phone number.");
            return null;
        }

        I18nPhoneNumberWrapper i18nPhoneNumber = I18nPhoneNumberWrapper.Factory.INSTANCE.get(
                mContext, phoneNumber);
        return mPhoneNumberContactMap.get(i18nPhoneNumber);
    }

    /**
     * Looks up a {@link Contact} by the given lookup key. Returns null if can't find the contact
     * entry.
     */
    @Nullable
    public Contact lookupContactByKey(String lookupKey) {
        if (!isLoaded()) {
            Log.w(TAG, "looking up a contact while loading.");
        }
        if (TextUtils.isEmpty(lookupKey)) {
            Log.w(TAG, "looking up an empty lookup key.");
            return null;
        }

        return mLookupKeyContactMap.get(lookupKey);
    }

    private List<Contact> onCursorLoaded(Cursor cursor) {
        Map<String, Contact> result = new LinkedHashMap<>();
        List<Contact> contacts = new ArrayList<>();

        while (cursor.moveToNext()) {
            Contact contact = Contact.fromCursor(mContext, cursor);
            String lookupKey = contact.getLookupKey();
            if (result.containsKey(lookupKey)) {
                Contact existingContact = result.get(lookupKey);
                existingContact.merge(contact);
            } else {
                result.put(lookupKey, contact);
            }
        }

        contacts.addAll(result.values());

        mLookupKeyContactMap.clear();
        mLookupKeyContactMap.putAll(result);

        mPhoneNumberContactMap.clear();
        for (Contact contact : contacts) {
            for (PhoneNumber phoneNumber : contact.getNumbers()) {
                mPhoneNumberContactMap.put(phoneNumber.getI18nPhoneNumberWrapper(), contact);
            }
        }
        return contacts;
    }

    @Override
    public void onChanged(List<Contact> contacts) {
        Log.d(TAG, "Contacts loaded:" + (contacts == null ? 0 : contacts.size()));
        mIsLoaded = true;
    }
}
