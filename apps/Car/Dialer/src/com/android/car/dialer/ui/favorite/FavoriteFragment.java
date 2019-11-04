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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import com.android.car.dialer.R;
import com.android.car.dialer.ui.common.DialerBaseFragment;

/** Contains either the "You haven't added any favorites yet" screen, or FavoriteListFragment */
public class FavoriteFragment extends DialerBaseFragment {

    public static FavoriteFragment newInstance() {
        return new FavoriteFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.favorite_fragment, container, false);
        View emptyPage = view.findViewById(R.id.empty_page_container);
        Fragment listFragment =
                getChildFragmentManager().findFragmentById(R.id.favorite_list_fragment);

        FavoriteViewModel favoriteViewModel = ViewModelProviders.of(getActivity()).get(
                FavoriteViewModel.class);
        favoriteViewModel.getFavoriteContacts().observe(this, contacts -> {
            if (contacts == null || contacts.isEmpty()) {
                emptyPage.setVisibility(View.VISIBLE);
                getChildFragmentManager().beginTransaction()
                        .hide(listFragment)
                        .commit();
            } else {
                emptyPage.setVisibility(View.GONE);
                getChildFragmentManager().beginTransaction()
                        .show(listFragment)
                        .commit();
            }
        });

        emptyPage.findViewById(R.id.add_favorite_button).setOnClickListener(v ->
                pushContentFragment(AddFavoriteFragment.newInstance(), null));

        return view;
    }
}
