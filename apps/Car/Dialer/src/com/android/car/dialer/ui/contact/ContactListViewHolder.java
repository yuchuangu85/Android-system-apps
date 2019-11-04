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
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.dialer.R;
import com.android.car.dialer.telecom.UiCallManager;
import com.android.car.dialer.ui.common.DialerUtils;
import com.android.car.dialer.ui.view.ContactAvatarOutputlineProvider;
import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.PhoneNumber;
import com.android.car.telephony.common.TelecomUtils;

import java.util.List;

/**
 * {@link RecyclerView.ViewHolder} for contact list item, responsible for presenting and resetting
 * the UI on recycle.
 */
public class ContactListViewHolder extends RecyclerView.ViewHolder {
    private final ContactListAdapter.OnShowContactDetailListener mOnShowContactDetailListener;
    private final TextView mHeaderView;
    private final ImageView mAvatarView;
    private final TextView mTitleView;
    private final TextView mTextView;
    private final View mShowContactDetailView;
    private final View mCallActionView;

    public ContactListViewHolder(@NonNull View itemView,
            ContactListAdapter.OnShowContactDetailListener onShowContactDetailListener) {
        super(itemView);
        mOnShowContactDetailListener = onShowContactDetailListener;
        mHeaderView = itemView.findViewById(R.id.header);
        mAvatarView = itemView.findViewById(R.id.icon);
        mAvatarView.setOutlineProvider(ContactAvatarOutputlineProvider.get());
        mTitleView = itemView.findViewById(R.id.title);
        mTextView = itemView.findViewById(R.id.text);
        mShowContactDetailView = itemView.findViewById(R.id.show_contact_detail_id);
        mCallActionView = itemView.findViewById(R.id.call_action_id);
    }

    /**
     * Binds the view holder with relevant data.
     */
    public void onBind(Contact contact, boolean showHeader, String header) {
        TelecomUtils.setContactBitmapAsync(mAvatarView.getContext(), mAvatarView, contact, null);
        if (showHeader) {
            mHeaderView.setVisibility(View.VISIBLE);
            mHeaderView.setText(header);
        } else {
            mHeaderView.setVisibility(View.GONE);
        }
        mTitleView.setText(contact.getDisplayName());
        setLabelText(contact);
        mShowContactDetailView.setOnClickListener(
                view -> mOnShowContactDetailListener.onShowContactDetail(contact));
        mCallActionView.setOnClickListener(view -> {
            DialerUtils.promptForPrimaryNumber(itemView.getContext(), contact,
                    (phoneNumber, always) -> UiCallManager.get().placeCall(
                            phoneNumber.getRawNumber()));
        });
    }

    private void setLabelText(Contact contact) {
        if (mTextView == null) {
            return;
        }

        Context context = itemView.getContext();
        CharSequence readableLabel = "";
        List<PhoneNumber> numberList = contact.getNumbers();

        if (numberList.size() == 1) {
            readableLabel = numberList.get(0).getReadableLabel(context.getResources());
        } else if (numberList.size() > 1) {
            readableLabel = contact.hasPrimaryPhoneNumber()
                    ? context.getString(R.string.primary_number_description,
                    contact.getPrimaryPhoneNumber().getReadableLabel(context.getResources()))
                    : context.getString(R.string.type_multiple);
        }

        mTextView.setText(readableLabel);
    }
}
