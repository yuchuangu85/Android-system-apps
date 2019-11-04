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

package com.android.car.dialer.ui.favorite;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.android.car.dialer.storage.FavoriteNumberRepository;
import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.PhoneNumber;

import java.util.List;

/**
 * View model for {@link FavoriteFragment}.
 */
public class FavoriteViewModel extends AndroidViewModel {
    private FavoriteNumberRepository mFavoriteNumberRepository;

    public FavoriteViewModel(Application application) {
        super(application);
        mFavoriteNumberRepository = FavoriteNumberRepository.getRepository(application);
    }

    /** Returns favorite contact list live data. */
    public LiveData<List<Contact>> getFavoriteContacts() {
        return mFavoriteNumberRepository.getFavoriteContacts();
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
}
