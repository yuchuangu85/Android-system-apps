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
package com.android.car.notification.template;

import android.annotation.ColorInt;
import android.app.Notification;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.view.View;

import androidx.cardview.widget.CardView;

import com.android.car.notification.NotificationClickHandlerFactory;
import com.android.car.notification.R;

/**
 * Notification view template that displays a car emergency notification.
 */
public class EmergencyNotificationViewHolder extends CarNotificationBaseViewHolder {
    private final CardView mCardView;
    private final CarNotificationHeaderView mHeaderView;
    private final CarNotificationActionsView mActionsView;
    private final CarNotificationBodyView mBodyView;
    @ColorInt
    private final int mEmergencyBackgroundColor;
    private NotificationClickHandlerFactory mClickHandlerFactory;

    public EmergencyNotificationViewHolder(View view,
            NotificationClickHandlerFactory clickHandlerFactory) {
        super(view, clickHandlerFactory);
        mCardView = view.findViewById(R.id.card_view);
        mHeaderView = view.findViewById(R.id.notification_header);
        mBodyView = view.findViewById(R.id.notification_body);
        mActionsView = view.findViewById(R.id.notification_actions);
        mEmergencyBackgroundColor = view.getContext().getColor(R.color.emergency_background_color);
        mClickHandlerFactory = clickHandlerFactory;
    }

    @Override
    boolean hasCustomBackgroundColor() {
        return true;
    }

    /**
     * Binds a {@link StatusBarNotification} to a car emergency notification template.
     */
    @Override
    public void bind(StatusBarNotification statusBarNotification, boolean isInGroup,
            boolean isHeadsUp) {
        super.bind(statusBarNotification, isInGroup, isHeadsUp);

        Notification notification = statusBarNotification.getNotification();

        mCardView.setCardBackgroundColor(mEmergencyBackgroundColor);
        mHeaderView.bind(statusBarNotification, isInGroup);
        mActionsView.bind(mClickHandlerFactory, statusBarNotification);

        Bundle extraData = notification.extras;
        CharSequence title = extraData.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extraData.getCharSequence(Notification.EXTRA_TEXT);
        Icon icon = notification.getLargeIcon();
        mBodyView.bind(title, text, icon);
    }
}
