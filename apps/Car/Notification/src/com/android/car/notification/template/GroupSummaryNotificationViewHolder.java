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
import android.content.Context;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.widget.TextView;

import com.android.car.notification.NotificationClickHandlerFactory;
import com.android.car.notification.NotificationGroup;
import com.android.car.notification.R;
import com.android.car.notification.ThemesUtil;

import java.util.List;

/**
 * Group summary notification view template that displays an automatically generated
 * group summary notification.
 */
public class GroupSummaryNotificationViewHolder extends CarNotificationBaseViewHolder {
    private final TextView mTitle1View;
    private final TextView mTitle2View;
    private final Context mContext;
    @ColorInt
    private final int mCardBackgroundColor;
    @ColorInt
    private final int mDefaultTextColor;

    /**
     * Constructor of the GroupSummaryNotificationViewHolder with a group summary template view.
     *
     * @param view group summary template view supplied by the adapter
     * @param clickHandlerFactory factory to generate onClickListener
     */
    public GroupSummaryNotificationViewHolder(
            View view, NotificationClickHandlerFactory clickHandlerFactory) {
        super(view, clickHandlerFactory);
        mContext = view.getContext();
        mCardBackgroundColor = ThemesUtil.getAttrColor(mContext, android.R.attr.colorPrimary);
        mDefaultTextColor = ThemesUtil.getAttrColor(mContext, android.R.attr.textColorPrimary);
        mTitle1View = view.findViewById(R.id.child_notification_title_1);
        mTitle2View = view.findViewById(R.id.child_notification_title_2);
    }

    /**
     * Binds a {@link NotificationGroup} to a group summary notification template.
     *
     * <p> Group summary notification view holder is special in that it binds a
     * {@link NotificationGroup} instead of a {@link StatusBarNotification}, therefore the standard
     * bind() method is no used. Still calling super.bind() because the touch events/animations
     * need to work.
     */
    public void bind(NotificationGroup notificationGroup) {
        // isInGroup is always true for group summaries
        super.bind(notificationGroup.getSingleNotification(), /* isInGroup= */ true, false);

        List<String> titles = notificationGroup.getChildTitles();

        if (titles == null || titles.isEmpty()) {
            return;
        }
        mTitle1View.setVisibility(View.VISIBLE);
        mTitle1View.setText(titles.get(0));

        if (titles.size() <= 1) {
            return;
        }
        mTitle2View.setVisibility(View.VISIBLE);
        mTitle2View.setText(titles.get(1));
    }

    /**
     * Resets the notification view empty for recycling.
     */
    @Override
    void reset() {
        super.reset();
        mTitle1View.setText(null);
        mTitle1View.setVisibility(View.GONE);

        mTitle2View.setText(null);
        mTitle2View.setVisibility(View.GONE);
    }
}
