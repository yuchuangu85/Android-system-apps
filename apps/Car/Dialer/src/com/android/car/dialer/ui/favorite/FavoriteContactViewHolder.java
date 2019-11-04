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

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.android.car.dialer.R;
import com.android.car.dialer.log.L;
import com.android.car.dialer.ui.view.ContactAvatarOutputlineProvider;
import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.PhoneNumber;
import com.android.car.telephony.common.TelecomUtils;

import java.util.List;

import javax.annotation.Nonnull;

/**
 * A {@link RecyclerView.ViewHolder ViewHolder} that will hold layouts for favorite contacts list
 * items.
 */
class FavoriteContactViewHolder extends RecyclerView.ViewHolder {
    private static final String TAG = "CD.FavoriteContactVH";

    private final ImageView mIcon;
    private final TextView mTitle;
    private final TextView mText;

    FavoriteContactViewHolder(View v) {
        super(v);
        mIcon = v.findViewById(R.id.icon);
        if (mIcon != null) {
            mIcon.setOutlineProvider(ContactAvatarOutputlineProvider.get());
        }
        mTitle = v.findViewById(R.id.title);
        mText = v.findViewById(R.id.text);
    }

    /**
     * Binds view with favorite contact.
     */
    public void onBind(@Nonnull Contact contact) {
        Context context = itemView.getContext();
        String displayName = contact.getDisplayName();
        mTitle.setText(displayName);

        List<PhoneNumber> contactPhoneNumbers = contact.getNumbers();
        if (contactPhoneNumbers.isEmpty()) {
            L.w(TAG, "contact %s doesn't have any phone number", contact.getDisplayName());
            return;
        }

        String secondaryText;
        if (!contact.isVoicemail() && contactPhoneNumbers.size() > 1) {
            if (contact.hasPrimaryPhoneNumber()) {
                secondaryText = context.getString(R.string.primary_number_description,
                        contact.getPrimaryPhoneNumber().getReadableLabel(context.getResources()));
            } else {
                secondaryText = context.getString(R.string.type_multiple);
            }
        } else {
            secondaryText = String.valueOf(
                    contactPhoneNumbers.get(0).getReadableLabel(context.getResources()));
        }
        mText.setText(secondaryText);

        TelecomUtils.setContactBitmapAsync(context, mIcon, contact, null);
    }
}
