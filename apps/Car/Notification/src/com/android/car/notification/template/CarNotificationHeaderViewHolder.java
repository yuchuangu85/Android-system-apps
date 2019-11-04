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
package com.android.car.notification.template;

import android.annotation.CallSuper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.android.car.notification.NotificationClickHandlerFactory;
import com.android.car.notification.R;

/**
 * Header template for the notification shade. This templates supports the clear all button with id
 * clear_all_button, a header text with id notification_header_text when the notification list is
 * not empty and a secondary header text with id empty_notification_header_text when notification
 * list is empty.
 */
public class CarNotificationHeaderViewHolder extends RecyclerView.ViewHolder {

    private final TextView mNotificationHeaderText;
    private final Button mClearAllButton;
    private final TextView mEmptyNotificationHeaderText;
    private final NotificationClickHandlerFactory mClickHandlerFactory;

    public CarNotificationHeaderViewHolder(View view,
            NotificationClickHandlerFactory clickHandlerFactory) {
        super(view);

        mNotificationHeaderText = view.findViewById(R.id.notification_header_text);
        mClearAllButton = view.findViewById(R.id.clear_all_button);
        mEmptyNotificationHeaderText = view.findViewById(R.id.empty_notification_header_text);
        mClickHandlerFactory = clickHandlerFactory;
    }

    @CallSuper
    public void bind(boolean containsNotification) {
        if (containsNotification) {
            mNotificationHeaderText.setVisibility(View.VISIBLE);
            mEmptyNotificationHeaderText.setVisibility(View.GONE);

            if (mClearAllButton == null) {
                return;
            }

            mClearAllButton.setVisibility(View.VISIBLE);
            if (!mClearAllButton.hasOnClickListeners()) {
                mClearAllButton.setOnClickListener(
                        view -> mClickHandlerFactory.clearAllNotifications());
            }
            return;
        }
        mNotificationHeaderText.setVisibility(View.GONE);
        mEmptyNotificationHeaderText.setVisibility(View.VISIBLE);

        if (mClearAllButton != null) {
            mClearAllButton.setVisibility(View.GONE);
        }
    }
}
