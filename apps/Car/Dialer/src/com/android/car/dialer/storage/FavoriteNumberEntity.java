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

package com.android.car.dialer.storage;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Favorite number entity */
@Entity(tableName = "favorite_number_entity")
public class FavoriteNumberEntity {
    @PrimaryKey(autoGenerate = true)
    private int mIndex;

    private String mContactLookupKey;

    /** Needed to refresh the contact lookup uri. */
    private long mContactId;

    private CipherWrapper<String> mPhoneNumber;

    private String mAccountName;

    private String mAccountType;

    public void setIndex(int index) {
        mIndex = index;
    }

    public int getIndex() {
        return mIndex;
    }

    public void setContactLookupKey(String contactLookupKey) {
        mContactLookupKey = contactLookupKey;
    }

    public String getContactLookupKey() {
        return mContactLookupKey;
    }

    public void setContactId(long contactId) {
        mContactId = contactId;
    }

    public long getContactId() {
        return mContactId;
    }

    public void setPhoneNumber(@Nullable CipherWrapper<String> phoneNumber) {
        mPhoneNumber = phoneNumber;
    }

    @Nullable
    public CipherWrapper<String> getPhoneNumber() {
        return mPhoneNumber;
    }

    public void setAccountName(String accountName) {
        mAccountName = accountName;
    }

    public String getAccountName() {
        return mAccountName;
    }

    public void setAccountType(String accountType) {
        mAccountType = accountType;
    }

    public String getAccountType() {
        return mAccountType;
    }
}
