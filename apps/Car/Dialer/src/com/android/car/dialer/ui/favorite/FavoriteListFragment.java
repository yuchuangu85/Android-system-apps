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

import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.dialer.R;
import com.android.car.dialer.telecom.UiCallManager;
import com.android.car.dialer.ui.common.DialerListBaseFragment;
import com.android.car.dialer.ui.common.DialerUtils;
import com.android.car.telephony.common.Contact;

import java.util.List;

/** Contains a list of favorite contacts. */
public class FavoriteListFragment extends DialerListBaseFragment {

    /** Constructs a new FavoriteListFragment */
    public static FavoriteListFragment newInstance() {
        return new FavoriteListFragment();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        getRecyclerView().addItemDecoration(new ItemSpacingDecoration());
        getRecyclerView().setItemAnimator(null);

        FavoriteAdapter adapter = new FavoriteAdapter();
        adapter.setOnAddFavoriteClickedListener(this::onAddFavoriteClicked);

        FavoriteViewModel favoriteViewModel = ViewModelProviders.of(getActivity()).get(
                FavoriteViewModel.class);
        LiveData<List<Contact>> favoriteContacts = favoriteViewModel.getFavoriteContacts();
        adapter.setOnListItemClickedListener(this::onItemClicked);
        favoriteContacts.observe(this, adapter::setFavoriteContacts);

        getRecyclerView().setAdapter(adapter);
    }

    @NonNull
    @Override
    protected RecyclerView.LayoutManager createLayoutManager() {
        int numOfColumn = getContext().getResources().getInteger(
                R.integer.favorite_fragment_grid_column);
        return new GridLayoutManager(getContext(), numOfColumn);
    }

    private void onItemClicked(Contact contact) {
        DialerUtils.promptForPrimaryNumber(getContext(), contact, (phoneNumber, always) ->
                UiCallManager.get().placeCall(phoneNumber.getRawNumber()));
    }

    private void onAddFavoriteClicked() {
        pushContentFragment(AddFavoriteFragment.newInstance(), null);
    }

    private class ItemSpacingDecoration extends RecyclerView.ItemDecoration {

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            Resources resources = FavoriteListFragment.this.getContext().getResources();
            int numColumns = resources.getInteger(R.integer.favorite_fragment_grid_column);
            int leftPadding =
                    resources.getDimensionPixelOffset(R.dimen.favorite_card_space_horizontal);
            int verticalPadding =
                    resources.getDimensionPixelOffset(R.dimen.favorite_card_space_vertical);

            if (parent.getChildAdapterPosition(view) % numColumns == 0) {
                leftPadding = 0;
            }

            outRect.set(leftPadding, verticalPadding, 0, verticalPadding);
        }
    }
}
