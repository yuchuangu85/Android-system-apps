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

package com.android.car.dialer.ui.search;

import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.android.car.dialer.R;
import com.android.car.telephony.common.Contact;

import java.util.ArrayList;
import java.util.List;

/**
 * An adapter that will parse a list of contacts given by a {@link Cursor} that display the
 * results as a list.
 */
public class ContactResultsAdapter extends RecyclerView.Adapter<ContactResultViewHolder> {

    interface OnShowContactDetailListener {
        void onShowContactDetail(Contact contact);
    }

    private final List<Contact> mContacts = new ArrayList<>();
    private final OnShowContactDetailListener mOnShowContactDetailListener;

    public ContactResultsAdapter(OnShowContactDetailListener onShowContactDetailListener) {
        mOnShowContactDetailListener = onShowContactDetailListener;
    }

    /**
     * Clears all contact results from this adapter.
     */
    public void clear() {
        mContacts.clear();
        notifyDataSetChanged();
    }

    /**
     * Sets the list of contacts that should be displayed. The given {@link Cursor} can be safely
     * closed after this call.
     */
    public void setData(List<Contact> data) {
        mContacts.clear();
        mContacts.addAll(data);
        notifyDataSetChanged();
    }

    @Override
    public ContactResultViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.contact_result, parent, false);
        return new ContactResultViewHolder(view, mOnShowContactDetailListener);
    }

    @Override
    public void onBindViewHolder(ContactResultViewHolder holder, int position) {
        holder.bind(mContacts.get(position));
    }

    @Override
    public int getItemViewType(int position) {
        // Only one type of view is created, so no need for an individualized view type.
        return 0;
    }

    @Override
    public int getItemCount() {
        return mContacts.size();
    }
}
