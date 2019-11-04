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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.android.car.dialer.R;
import com.android.car.dialer.log.L;
import com.android.car.dialer.ui.common.OnItemClickedListener;
import com.android.car.telephony.common.Contact;

import java.util.Collections;
import java.util.List;

/**
 * Adapter class for binding favorite contacts.
 */
public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteContactViewHolder> {
    private static final String TAG = "CD.FavoriteAdapter";
    private static final int TYPE_CONTACT = 0;
    private static final int TYPE_ADD_FAVORITE = 1;

    /** Listener interface for when the add favorite button is clicked */
    public interface OnAddFavoriteClickedListener {
        /** Called when the add favorite button is clicked */
        void onAddFavoriteClicked();
    }

    private List<Contact> mFavoriteContacts = Collections.emptyList();
    private OnItemClickedListener<Contact> mListener;
    private OnAddFavoriteClickedListener mAddFavoriteListener;

    /** Sets the favorite contact list. */
    public void setFavoriteContacts(List<Contact> favoriteContacts) {
        L.d(TAG, "setFavoriteContacts %s", favoriteContacts);
        mFavoriteContacts = (favoriteContacts != null) ? favoriteContacts : Collections.emptyList();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mFavoriteContacts.size() + 1; // +1 for the "Add a favorite" button
    }

    @Override
    public int getItemViewType(int position) {
        return position < mFavoriteContacts.size()
                ? TYPE_CONTACT
                : TYPE_ADD_FAVORITE;
    }

    @Override
    public FavoriteContactViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        if (viewType == TYPE_CONTACT) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.favorite_contact_list_item, parent, false);
        } else {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.add_favorite_list_item, parent, false);
        }

        return new FavoriteContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(FavoriteContactViewHolder viewHolder, int position) {
        if (getItemViewType(position) == TYPE_CONTACT) {
            Contact contact = mFavoriteContacts.get(position);
            viewHolder.onBind(contact);
            viewHolder.itemView.setOnClickListener((v) -> onItemViewClicked(contact));
        } else {
            viewHolder.itemView.setOnClickListener((v) -> {
                if (mAddFavoriteListener != null) {
                    mAddFavoriteListener.onAddFavoriteClicked();
                }
            });
        }
    }

    private void onItemViewClicked(Contact contact) {
        if (mListener != null) {
            mListener.onItemClicked(contact);
        }
    }

    /**
     * Sets a {@link OnItemClickedListener listener} which will be called when an item is clicked.
     */
    public void setOnListItemClickedListener(OnItemClickedListener<Contact> listener) {
        mListener = listener;
    }

    /**
     * Sets a {@link OnAddFavoriteClickedListener listener} which will be called when the
     * "Add favorite" button is clicked.
     */
    public void setOnAddFavoriteClickedListener(OnAddFavoriteClickedListener listener) {
        mAddFavoriteListener = listener;
    }
}
