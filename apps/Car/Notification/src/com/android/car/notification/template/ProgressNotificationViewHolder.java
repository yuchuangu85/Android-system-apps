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
import android.content.res.ColorStateList;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.widget.ProgressBar;

import com.android.car.notification.NotificationClickHandlerFactory;
import com.android.car.notification.R;
import com.android.car.notification.ThemesUtil;

/**
 * Basic notification view template that displays a progress bar notification.
 * This template is only used in notification center and never as a heads-up notification.
 */
public class ProgressNotificationViewHolder extends CarNotificationBaseViewHolder {
    private final CarNotificationHeaderView mHeaderView;
    private final CarNotificationBodyView mBodyView;
    private final CarNotificationActionsView mActionsView;
    private final ProgressBar mProgressBarView;
    @ColorInt
    private final int mCardBackgroundColor;
    private NotificationClickHandlerFactory mClickHandlerFactory;

    public ProgressNotificationViewHolder(View view,
            NotificationClickHandlerFactory clickHandlerFactory) {
        super(view, clickHandlerFactory);
        mHeaderView = view.findViewById(R.id.notification_header);
        mBodyView = view.findViewById(R.id.notification_body);
        mActionsView = view.findViewById(R.id.notification_actions);
        mProgressBarView = view.findViewById(R.id.progress_bar);
        mCardBackgroundColor = ThemesUtil.getAttrColor(view.getContext(),
                android.R.attr.colorPrimary);
        mClickHandlerFactory = clickHandlerFactory;
    }

    /**
     * Binds a {@link StatusBarNotification} to a car progress notification template.
     */
    @Override
    public void bind(StatusBarNotification statusBarNotification, boolean isInGroup,
            boolean isHeadsUp) {
        super.bind(statusBarNotification, isInGroup, isHeadsUp);
        bindBody(statusBarNotification);
        mHeaderView.bind(statusBarNotification, isInGroup);
        mActionsView.bind(mClickHandlerFactory, statusBarNotification);
    }

    /**
     * Private method that binds the data to the view.
     */
    private void bindBody(StatusBarNotification statusBarNotification) {
        Notification notification = statusBarNotification.getNotification();

        Bundle extraData = notification.extras;
        CharSequence title = extraData.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extraData.getCharSequence(Notification.EXTRA_TEXT);
        Icon icon = notification.getLargeIcon();
        mBodyView.bind(title, text, icon);

        mProgressBarView.setVisibility(View.VISIBLE);
        boolean isIndeterminate = extraData.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE);
        int progress = extraData.getInt(Notification.EXTRA_PROGRESS);
        int progressMax = extraData.getInt(Notification.EXTRA_PROGRESS_MAX);
        mProgressBarView.setIndeterminate(isIndeterminate);
        mProgressBarView.setMax(progressMax);
        mProgressBarView.setProgress(progress);

        // optional color
        if (notification.color != Notification.COLOR_DEFAULT) {
            int calculatedColor = NotificationColorUtil.resolveContrastColor(
                    notification.color, mCardBackgroundColor);
            ColorStateList colorStateList = ColorStateList.valueOf(calculatedColor);
            mProgressBarView.setProgressTintList(colorStateList);
        }
    }

    /**
     * Resets the basic notification view empty for recycling.
     */
    @Override
    void reset() {
        super.reset();
        mProgressBarView.setProgress(0);
        mProgressBarView.setVisibility(View.GONE);
        mProgressBarView.setProgressTintList(null);
    }
}
