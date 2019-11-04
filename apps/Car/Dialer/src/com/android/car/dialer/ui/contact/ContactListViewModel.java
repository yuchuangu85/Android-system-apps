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
import android.content.Context;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.android.car.dialer.R;
import com.android.car.dialer.livedata.SharedPreferencesLiveData;
import com.android.car.dialer.widget.WorkerExecutor;
import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.InMemoryPhoneBook;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Future;

/**
 * View model for {@link ContactListFragment}.
 */
public class ContactListViewModel extends AndroidViewModel {

    public static final int SORT_BY_FIRST_NAME = 1;
    public static final int SORT_BY_LAST_NAME = 2;

    private final Context mContext;
    private final LiveData<Pair<Integer, List<Contact>>> mSortedContactListLiveData;

    public ContactListViewModel(@NonNull Application application) {
        super(application);
        mContext = application.getApplicationContext();

        SharedPreferencesLiveData preferencesLiveData =
                new SharedPreferencesLiveData(mContext, R.string.sort_order_key);
        LiveData<List<Contact>> contactListLiveData = InMemoryPhoneBook.get().getContactsLiveData();
        mSortedContactListLiveData = new SortedContactListLiveData(
                mContext, contactListLiveData, preferencesLiveData);
    }

    /**
     * Returns a live data which represents a list of all contacts.
     */
    public LiveData<Pair<Integer, List<Contact>>> getAllContacts() {
        return mSortedContactListLiveData;
    }

    private static class SortedContactListLiveData
            extends MediatorLiveData<Pair<Integer, List<Contact>>> {

        private final LiveData<List<Contact>> mContactListLiveData;
        private final SharedPreferencesLiveData mPreferencesLiveData;
        private final Context mContext;

        private Future<?> mRunnableFuture;

        /**
         * Sort by the default display order of a name. For western names it will be "Given Family".
         * For unstructured names like east asian this will be the only order.
         * Phone Dialer uses the same method for sorting given names.
         *
         * @see android.provider.ContactsContract.Contacts#DISPLAY_NAME_PRIMARY
         */
        private final Comparator<Contact> mFirstNameComparator =
                (o1, o2) -> o1.compareByDisplayName(o2);

        /**
         * Sort by the alternative display order of a name. For western names it will be "Family,
         * Given". For unstructured names like east asian this order will be ignored and treated as
         * primary.
         * Phone Dialer uses the same method for sorting family names.
         *
         * @see android.provider.ContactsContract.Contacts#DISPLAY_NAME_ALTERNATIVE
         */
        private final Comparator<Contact> mLastNameComparator =
                (o1, o2) -> o1.compareByAltDisplayName(o2);

        private SortedContactListLiveData(Context context,
                @NonNull LiveData<List<Contact>> contactListLiveData,
                @NonNull SharedPreferencesLiveData sharedPreferencesLiveData) {
            mContext = context;
            mContactListLiveData = contactListLiveData;
            mPreferencesLiveData = sharedPreferencesLiveData;

            addSource(mPreferencesLiveData, (trigger) -> updateSortedContactList());
            addSource(mContactListLiveData, (trigger) -> updateSortedContactList());
        }

        private void updateSortedContactList() {
            if (mContactListLiveData.getValue() == null) {
                setValue(null);
                return;
            }

            String key = mPreferencesLiveData.getKey();
            String firstNameSort = mContext.getResources().getString(
                    R.string.give_name_first_title);

            List<Contact> contactList = mContactListLiveData.getValue();
            Comparator<Contact> comparator;
            Integer sortMethod;
            if (mPreferencesLiveData.getValue() == null
                    || firstNameSort.equals(
                    mPreferencesLiveData.getValue().getString(key, firstNameSort))) {
                comparator = mFirstNameComparator;
                sortMethod = SORT_BY_FIRST_NAME;
            } else {
                comparator = mLastNameComparator;
                sortMethod = SORT_BY_LAST_NAME;
            }

            // SingleThreadPoolExecutor is used here to avoid multiple threads sorting the list
            // at the same time.
            if (mRunnableFuture != null) {
                mRunnableFuture.cancel(true);
            }

            Runnable runnable = () -> {
                Collections.sort(contactList, comparator);
                postValue(new Pair<>(sortMethod, contactList));
            };
            mRunnableFuture = WorkerExecutor.getInstance().getSingleThreadExecutor().submit(
                    runnable);
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
