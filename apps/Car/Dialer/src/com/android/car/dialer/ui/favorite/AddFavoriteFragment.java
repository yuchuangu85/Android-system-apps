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

package com.android.car.dialer.ui.favorite;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.dialer.R;
import com.android.car.dialer.ui.common.FavoritePhoneNumberListAdapter;
import com.android.car.dialer.ui.search.ContactResultsFragment;
import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.PhoneNumber;

import java.util.HashSet;
import java.util.Set;

/** A fragment that allows the user to search for and select favorite phone numbers */
public class AddFavoriteFragment extends ContactResultsFragment {

    /** Creates a new instance of AddFavoriteFragment */
    public static AddFavoriteFragment newInstance() {
        return new AddFavoriteFragment();
    }

    private AlertDialog mCurrentDialog;
    private FavoritePhoneNumberListAdapter mDialogAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FavoriteViewModel favoriteViewModel = ViewModelProviders.of(getActivity()).get(
                FavoriteViewModel.class);
        Set<PhoneNumber> selectedNumbers = new HashSet<>();

        mDialogAdapter = new FavoritePhoneNumberListAdapter(getContext(),
                (phoneNumber, itemView) -> {
                    boolean isActivated = itemView.isActivated();
                    itemView.setActivated(!isActivated);
                    if (isActivated) {
                        selectedNumbers.remove(phoneNumber);
                    } else {
                        selectedNumbers.add(phoneNumber);
                    }
                }
        );

        View dialogView = LayoutInflater.from(getContext()).inflate(
                R.layout.add_to_favorite_dialog, null, false);
        RecyclerView recyclerView = dialogView.findViewById(R.id.list);
        recyclerView.setAdapter(mDialogAdapter);

        mCurrentDialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.select_number_dialog_title)
                .setView(dialogView)
                .setNegativeButton(R.string.cancel_add_favorites_dialog, null)
                .setPositiveButton(R.string.confirm_add_favorites_dialog,
                        (d, which) -> {
                            for (PhoneNumber number : selectedNumbers) {
                                favoriteViewModel.addToFavorite(mDialogAdapter.getContact(),
                                        number);
                            }
                            selectedNumbers.clear();
                            getFragmentManager().popBackStackImmediate();
                        })
                .create();
    }

    @Override
    public void onShowContactDetail(Contact contact) {
        if (contact == null) {
            mCurrentDialog.dismiss();
            return;
        }

        mDialogAdapter.setPhoneNumbers(contact, contact.getNumbers());
        mCurrentDialog.show();
    }
}
