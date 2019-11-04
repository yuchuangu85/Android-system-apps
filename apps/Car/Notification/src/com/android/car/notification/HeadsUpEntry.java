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

package com.android.car.notification;

import android.content.Context;
import android.os.Handler;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.car.notification.template.CarNotificationBaseViewHolder;

/**
 * Class to store the state for Heads Up Notifications. Each notification will have its own post
 * time, handler, and Layout. This class ensures to store it as a separate state so that each Heads
 * up notification can be controlled independently.
 */
public class HeadsUpEntry {

    private final StatusBarNotification mStatusBarNotification;
    private long mPostTime;
    private final Handler mHandler;
    protected boolean isAlertAgain;
    protected boolean isNewHeadsUp;
    private View mNotificationView;
    private NotificationClickHandlerFactory mClickHandlerFactory;
    private CarNotificationBaseViewHolder mCarNotificationBaseViewHolder;

    HeadsUpEntry(StatusBarNotification statusBarNotification) {
        mStatusBarNotification = statusBarNotification;
        mPostTime = calculatePostTime();
        mHandler = new Handler();
    }

    /**
     * Calculate what the post time of a notification is at some current time.
     *
     * @return the post time
     */
    private long calculatePostTime() {
        return System.currentTimeMillis();
    }

    /**
     * Updates the current post time for the Heads up notification.
     */
    protected void updatePostTime() {
        mPostTime = calculatePostTime();
    }

    protected StatusBarNotification getStatusBarNotification() {
        return mStatusBarNotification;
    }

    /**
     * Handler will used the method {@link Handler#postDelayed(Runnable, long)} which will control
     * he dismiss time for the Heads Up notification. All the notifications should have their own
     * handler o control this time.
     */
    protected Handler getHandler() {
        return mHandler;
    }


    protected long getPostTime() {
        return mPostTime;
    }

    /**
     * View that holds the actual card for heads up notification.
     */
    protected void setNotificationView(View notificationView) {
        mNotificationView = notificationView;
    }

    protected View getNotificationView() {
        return mNotificationView;
    }

    protected NotificationClickHandlerFactory getClickHandlerFactory() {
        return mClickHandlerFactory;
    }

    protected void setClickHandlerFactory(
            NotificationClickHandlerFactory clickHandlerFactory) {
        mClickHandlerFactory = clickHandlerFactory;
    }

    protected void setViewHolder(CarNotificationBaseViewHolder viewHolder) {
        mCarNotificationBaseViewHolder = viewHolder;
    }

    protected CarNotificationBaseViewHolder getViewHolder() {
        return mCarNotificationBaseViewHolder;
    }
}
