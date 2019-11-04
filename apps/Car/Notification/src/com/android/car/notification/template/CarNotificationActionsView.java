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

import android.app.Notification;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.service.notification.StatusBarNotification;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.android.car.assist.client.CarAssistUtils;
import com.android.car.notification.NotificationClickHandlerFactory;
import com.android.car.notification.NotificationDataManager;
import com.android.car.notification.PreprocessingManager;
import com.android.car.notification.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Notification actions view that contains the buttons that fire actions.
 */
public class CarNotificationActionsView extends RelativeLayout implements
        PreprocessingManager.CallStateListener {

    private static final String TAG = "CarNotificationAction";
    // Maximum 3 actions
    // https://developer.android.com/reference/android/app/Notification.Builder.html#addAction
    private static final int MAX_NUM_ACTIONS = 3;
    private static final int FIRST_MESSAGE_ACTION_BUTTON_INDEX = 0;
    private static final int SECOND_MESSAGE_ACTION_BUTTON_INDEX = 1;

    private final List<Button> mActionButtons = new ArrayList<>();

    private boolean mIsCategoryCall;
    private boolean mIsInCall;
    private Context mContext;

    public CarNotificationActionsView(Context context) {
        super(context);
        PreprocessingManager.getInstance(context).addCallStateListener(this::onCallStateChanged);
    }

    public CarNotificationActionsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init(attrs);
    }

    public CarNotificationActionsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        init(attrs);
    }

    public CarNotificationActionsView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mContext = context;
        init(attrs);
    }

    {
        inflate(getContext(), R.layout.car_notification_actions_view, /* root= */ this);
    }

    private void init(AttributeSet attrs) {
        TypedArray attributes =
                getContext().obtainStyledAttributes(attrs, R.styleable.CarNotificationActionsView);
        mIsCategoryCall =
                attributes.getBoolean(R.styleable.CarNotificationActionsView_categoryCall, false);
        attributes.recycle();
        PreprocessingManager.getInstance(getContext()).addCallStateListener(
                this::onCallStateChanged);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mActionButtons.add(findViewById(R.id.action_1));
        mActionButtons.add(findViewById(R.id.action_2));
        mActionButtons.add(findViewById(R.id.action_3));
    }

    /**
     * Binds the notification action buttons.
     *
     * @param clickHandlerFactory factory class used to generate {@link OnClickListener}s.
     * @param statusBarNotification the notification that contains the actions.
     */
    public void bind(
            NotificationClickHandlerFactory clickHandlerFactory,
            StatusBarNotification statusBarNotification) {

        Notification notification = statusBarNotification.getNotification();
        Notification.Action[] actions = notification.actions;
        if (actions == null || actions.length == 0) {
            return;
        }

        if (CarAssistUtils.isCarCompatibleMessagingNotification(statusBarNotification)) {
            createPlayButton(clickHandlerFactory, statusBarNotification);
            createMuteButton(clickHandlerFactory, statusBarNotification);
            return;
        }

        int length = Math.min(actions.length, MAX_NUM_ACTIONS);
        for (int i = 0; i < length; i++) {
            Notification.Action action = actions[i];
            Button button = mActionButtons.get(i);
            button.setVisibility(View.VISIBLE);
            // clear spannables and only use the text
            button.setText(action.title.toString());

            if (action.actionIntent != null) {
                button.setOnClickListener(clickHandlerFactory.getActionClickHandler(
                        statusBarNotification, i));
            }
        }

        if (mIsCategoryCall) {
            Drawable acceptButton = mContext.getResources().getDrawable(
                    R.drawable.call_action_button_background);
            acceptButton.setColorFilter(
                    new PorterDuffColorFilter(mContext.getColor(R.color.call_accept_button),
                            PorterDuff.Mode.SRC_IN));
            mActionButtons.get(0).setBackground(acceptButton);

            Drawable declineButton = mContext.getResources().getDrawable(
                    R.drawable.call_action_button_background);
            declineButton.setColorFilter(
                    new PorterDuffColorFilter(mContext.getColor(R.color.call_decline_button),
                            PorterDuff.Mode.SRC_IN));
            mActionButtons.get(1).setBackground(declineButton);
        }
    }

    /**
     * The Play button triggers the assistant to read the message aloud, optionally prompting the
     * user to reply to the message afterwards.
     */
    private void createPlayButton(NotificationClickHandlerFactory clickHandlerFactory,
            StatusBarNotification statusBarNotification) {
        if (mIsInCall) return;

        Button button = mActionButtons.get(FIRST_MESSAGE_ACTION_BUTTON_INDEX);
        button.setText(mContext.getString(R.string.assist_action_play_label));
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(clickHandlerFactory.getPlayClickHandler(statusBarNotification));
    }

    /**
     * The Mute button allows users to toggle whether or not incoming notification with the same
     * statusBarNotification key will be shown with a HUN and trigger a notification sound.
     */
    private void createMuteButton(NotificationClickHandlerFactory clickHandlerFactory,
            StatusBarNotification statusBarNotification) {
        int index = SECOND_MESSAGE_ACTION_BUTTON_INDEX;
        if (mIsInCall) index = FIRST_MESSAGE_ACTION_BUTTON_INDEX;

        Button button = mActionButtons.get(index);
        NotificationDataManager manager = clickHandlerFactory.getNotificationDataManager();
        button.setText((manager != null && manager.isMessageNotificationMuted(
                statusBarNotification))
                ? mContext.getString(R.string.action_unmute_long)
                : mContext.getString(R.string.action_mute_long));
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(clickHandlerFactory.getMuteClickHandler(
                button, statusBarNotification));
    }

    /** Implementation of {@link PreprocessingManager.CallStateListener} **/
    @Override
    public void onCallStateChanged(boolean isInCall) {
        mIsInCall = isInCall;
    }

    /**
     * Resets the notification actions empty for recycling.
     */
    public void reset() {
        for (Button button : mActionButtons) {
            button.setVisibility(View.GONE);
            button.setText(null);
            button.setOnClickListener(null);
        }
        PreprocessingManager.getInstance(getContext()).removeCallStateListener(
                this::onCallStateChanged);
    }
}
