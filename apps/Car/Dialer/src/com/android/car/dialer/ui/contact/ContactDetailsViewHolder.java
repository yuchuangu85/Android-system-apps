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
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.dialer.R;
import com.android.car.dialer.telecom.UiCallManager;
import com.android.car.dialer.ui.view.ContactAvatarOutputlineProvider;
import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.PhoneNumber;
import com.android.car.telephony.common.TelecomUtils;

/** ViewHolder for {@link ContactDetailsFragment}. */
class ContactDetailsViewHolder extends RecyclerView.ViewHolder {
    // Applies to all
    @NonNull
    private final TextView mTitle;

    // Applies to header
    @Nullable
    private final ImageView mAvatar;

    // Applies to phone number items
    @Nullable
    private final TextView mText;
    @Nullable
    private final View mCallActionView;
    @Nullable
    private final View mFavoriteActionView;

    @NonNull
    private final ContactDetailsAdapter.PhoneNumberPresenter mPhoneNumberPresenter;

    ContactDetailsViewHolder(
            View v,
            @NonNull ContactDetailsAdapter.PhoneNumberPresenter phoneNumberPresenter) {
        super(v);
        mCallActionView = v.findViewById(R.id.call_action_id);
        mFavoriteActionView = v.findViewById(R.id.contact_details_favorite_button);
        mTitle = v.findViewById(R.id.title);
        mText = v.findViewById(R.id.text);
        mAvatar = v.findViewById(R.id.avatar);
        if (mAvatar != null) {
            mAvatar.setOutlineProvider(ContactAvatarOutputlineProvider.get());
        }

        mPhoneNumberPresenter = phoneNumberPresenter;
    }

    public void bind(Context context, Contact contact) {
        TelecomUtils.setContactBitmapAsync(context, mAvatar, contact, null);

        if (contact == null) {
            mTitle.setText(R.string.error_contact_deleted);
            return;
        }

        mTitle.setText(contact.getDisplayName());
    }

    public void bind(Context context, Contact contact, PhoneNumber phoneNumber) {

        mTitle.setText(phoneNumber.getRawNumber());

        // Present the phone number type.
        CharSequence readableLabel = phoneNumber.getReadableLabel(context.getResources());
        if (phoneNumber.isPrimary()) {
            mText.setText(context.getString(R.string.primary_number_description, readableLabel));
            mText.setTextAppearance(R.style.TextAppearance_DefaultNumberLabel);
        } else {
            mText.setText(readableLabel);
            mText.setTextAppearance(R.style.TextAppearance_ContactDetailsListSubtitle);
        }

        mCallActionView.setOnClickListener(v -> placeCall(phoneNumber));
        mFavoriteActionView.setActivated(phoneNumber.isFavorite());
        mFavoriteActionView.setOnClickListener(v -> {
            mPhoneNumberPresenter.onClick(contact, phoneNumber);
            mFavoriteActionView.setActivated(!mFavoriteActionView.isActivated());
        });
    }

    private void placeCall(PhoneNumber number) {
        UiCallManager.get().placeCall(number.getRawNumber());
    }
}
