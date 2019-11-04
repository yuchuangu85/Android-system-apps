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

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.android.car.dialer.R;
import com.android.car.dialer.ui.view.ContactAvatarOutputlineProvider;
import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.TelecomUtils;

/**
 * A {@link androidx.recyclerview.widget.RecyclerView.ViewHolder} that will parse relevant
 * views out of a {@code contact_result} layout.
 */
public class ContactResultViewHolder extends RecyclerView.ViewHolder {
    private final Context mContext;
    private final View mContactCard;
    private final TextView mContactName;
    private final ImageView mContactPicture;
    private final ContactResultsAdapter.OnShowContactDetailListener mOnShowContactDetailListener;

    public ContactResultViewHolder(View view,
            ContactResultsAdapter.OnShowContactDetailListener onShowContactDetailListener) {
        super(view);
        mContext = view.getContext();
        mContactCard = view.findViewById(R.id.contact_result);
        mContactName = view.findViewById(R.id.contact_name);
        mContactPicture = view.findViewById(R.id.contact_picture);
        mContactPicture.setOutlineProvider(ContactAvatarOutputlineProvider.get());
        mOnShowContactDetailListener = onShowContactDetailListener;
    }

    /**
     * Populates the view that is represented by this ViewHolder with the information in the
     * provided {@link Contact}.
     */
    public void bind(Contact contact) {
        mContactCard.setOnClickListener(
                v -> mOnShowContactDetailListener.onShowContactDetail(contact));

        mContactName.setText(contact.getDisplayName());
        TelecomUtils.setContactBitmapAsync(mContext, mContactPicture, contact.getAvatarUri(),
                contact.getDisplayName());
    }
}
