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

package com.android.car.dialer.ui.calllog;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.dialer.R;
import com.android.car.dialer.livedata.CallHistoryLiveData;
import com.android.car.dialer.telecom.UiCallManager;
import com.android.car.dialer.ui.common.entity.UiCallLog;
import com.android.car.dialer.ui.view.ContactAvatarOutputlineProvider;
import com.android.car.dialer.widget.CallTypeIconsView;
import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.InMemoryPhoneBook;
import com.android.car.telephony.common.PhoneCallLog;
import com.android.car.telephony.common.TelecomUtils;

/**
 * {@link RecyclerView.ViewHolder} for call history list item, responsible for presenting and
 * resetting the UI on recycle.
 */
public class CallLogViewHolder extends RecyclerView.ViewHolder {

    private CallLogAdapter.OnShowContactDetailListener mOnShowContactDetailListener;
    private View mPlaceCallView;
    private ImageView mAvatarView;
    private TextView mTitleView;
    private TextView mCallCountTextView;
    private TextView mTextView;
    private CallTypeIconsView mCallTypeIconsView;
    private View mActionButton;
    private View mDivider;

    public CallLogViewHolder(@NonNull View itemView,
            CallLogAdapter.OnShowContactDetailListener onShowContactDetailListener) {
        super(itemView);
        mOnShowContactDetailListener = onShowContactDetailListener;
        mPlaceCallView = itemView.findViewById(R.id.call_action_id);
        mAvatarView = itemView.findViewById(R.id.icon);
        mAvatarView.setOutlineProvider(ContactAvatarOutputlineProvider.get());
        mTitleView = itemView.findViewById(R.id.title);
        mCallCountTextView = itemView.findViewById(R.id.call_count_text);
        mTextView = itemView.findViewById(R.id.text);
        mCallTypeIconsView = itemView.findViewById(R.id.call_type_icons);
        mActionButton = itemView.findViewById(R.id.calllog_action_button);
        mDivider = itemView.findViewById(R.id.divider);
    }

    public void onBind(UiCallLog uiCallLog) {
        TelecomUtils.setContactBitmapAsync(
                mAvatarView.getContext(),
                mAvatarView,
                uiCallLog.getAvatarUri(),
                uiCallLog.getTitle());
        mTitleView.setText(uiCallLog.getTitle());
        if (uiCallLog.getMostRecentCallType() == CallHistoryLiveData.CallType.MISSED_TYPE) {
            mTitleView.setTextAppearance(R.style.TextAppearance_CallLogTitleMissedCall);
        } else {
            mTitleView.setTextAppearance(R.style.TextAppearance_CallLogTitleDefault);
        }

        for (PhoneCallLog.Record record : uiCallLog.getCallRecords()) {
            mCallTypeIconsView.add(record.getCallType());
        }

        mCallCountTextView.setText(mCallTypeIconsView.getCallCountText());
        mCallCountTextView.setVisibility(
                mCallTypeIconsView.getCallCountText() == null ? View.GONE : View.VISIBLE);
        mTextView.setText(uiCallLog.getText());

        mPlaceCallView.setOnClickListener(
                view -> UiCallManager.get().placeCall(uiCallLog.getNumber()));

        setUpActionButton(uiCallLog);
    }

    public void onRecycle() {
        mCallTypeIconsView.clear();
    }

    private void setUpActionButton(UiCallLog uiCallLog) {
        if (mActionButton == null) {
            return;
        }

        Contact contact = InMemoryPhoneBook.get().lookupContactEntry(uiCallLog.getNumber());

        if (contact == null) {
            mActionButton.setVisibility(View.GONE);
            mDivider.setVisibility(View.GONE);
            return;
        }
        mDivider.setVisibility(View.VISIBLE);
        mActionButton.setVisibility(View.VISIBLE);
        mActionButton.setOnClickListener(
                view -> mOnShowContactDetailListener.onShowContactDetail(contact));
    }
}
