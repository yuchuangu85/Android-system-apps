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

import android.content.Context;
import android.text.TextUtils;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.dialer.R;
import com.android.car.telephony.common.Contact;

import java.util.ArrayList;
import java.util.List;

/** Adapter for contact list. */
public class ContactListAdapter extends RecyclerView.Adapter<ContactListViewHolder> {
    private static final String TAG = "CD.ContactListAdapter";

    interface OnShowContactDetailListener {
        void onShowContactDetail(Contact contact);
    }

    private final Context mContext;
    private final List<Contact> mContactList = new ArrayList<>();
    private final OnShowContactDetailListener mOnShowContactDetailListener;

    private Integer mSortMethod;

    public ContactListAdapter(Context context,
            OnShowContactDetailListener onShowContactDetailListener) {
        mContext = context;
        mOnShowContactDetailListener = onShowContactDetailListener;
    }

    /**
     * Sets {@link #mContactList} based on live data.
     */
    public void setContactList(Pair<Integer, List<Contact>> contactListPair) {
        mContactList.clear();
        if (contactListPair != null) {
            mContactList.addAll(contactListPair.second);
            mSortMethod = contactListPair.first;
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ContactListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(mContext).inflate(R.layout.contact_list_item, parent,
                false);
        return new ContactListViewHolder(itemView, mOnShowContactDetailListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactListViewHolder holder, int position) {
        Contact contact = mContactList.get(position);
        String header = getHeader(contact);

        boolean showHeader = position == 0
                || (!header.equals(getHeader(mContactList.get(position - 1))));
        holder.onBind(contact, showHeader, header);
    }

    @Override
    public int getItemCount() {
        return  mContactList.size();
    }

    private String getHeader(Contact contact) {
        String label;
        if (mSortMethod.equals(ContactListViewModel.SORT_BY_LAST_NAME)) {
            label = contact.getPhonebookLabelAlt();
        } else {
            label = contact.getPhonebookLabel();
        }

        return !TextUtils.isEmpty(label) ? label
                : mContext.getString(R.string.header_for_type_other);
    }
}
