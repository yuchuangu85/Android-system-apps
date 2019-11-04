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
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Icon;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.car.notification.R;
import com.android.car.notification.ThemesUtil;

/**
 * Common notification body that consists of a title line, a content text line, and an image icon on
 * the end.
 *
 * <p> For example, for a messaging notification, the title is the sender's name,
 * the content is the message, and the image icon is the sender's avatar.
 */
public class CarNotificationBodyView extends RelativeLayout {
    @ColorInt
    private final int mDefaultPrimaryTextColor;
    @ColorInt
    private final int mDefaultSecondaryTextColor;
    private boolean mShowBigIcon;
    private TextView mTitleView;
    private TextView mContentView;
    private ImageButton mIconView;

    public CarNotificationBodyView(Context context) {
        super(context);
    }

    public CarNotificationBodyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public CarNotificationBodyView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    public CarNotificationBodyView(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    {
        mDefaultPrimaryTextColor =
                ThemesUtil.getAttrColor(getContext(), android.R.attr.textColorPrimary);
        mDefaultSecondaryTextColor =
                ThemesUtil.getAttrColor(getContext(), android.R.attr.textColorSecondary);
        inflate(getContext(), R.layout.car_notification_body_view, /* root= */ this);
    }

    private void init(AttributeSet attrs) {
        TypedArray attributes =
                getContext().obtainStyledAttributes(attrs, R.styleable.CarNotificationBodyView);
        mShowBigIcon =
                attributes.getBoolean(R.styleable.CarNotificationBodyView_showBigIcon,
                        /* defValue= */ false);
        attributes.recycle();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTitleView = findViewById(R.id.notification_body_title);
        mContentView = findViewById(R.id.notification_body_content);
        mIconView = findViewById(R.id.notification_body_icon);
    }

    /**
     * Binds the notification body.
     *
     * @param title the primary text.
     * @param content the secondary text.
     * @param icon the large icon, usually used for avatars.
     */
    public void bind(CharSequence title, @Nullable CharSequence content, @Nullable Icon icon) {
        setVisibility(View.VISIBLE);

        mTitleView.setVisibility(View.VISIBLE);
        mTitleView.setText(title);

        if (!TextUtils.isEmpty(content)) {
            mContentView.setVisibility(View.VISIBLE);
            mContentView.setText(content);
        }

        if (icon != null && mShowBigIcon) {
            mIconView.setVisibility(View.VISIBLE);
            mIconView.setImageIcon(icon);
        }
    }

    public void bindTitleAndMessage(CharSequence title, CharSequence content) {
        setVisibility(View.VISIBLE);

        mTitleView.setVisibility(View.VISIBLE);
        mTitleView.setText(title);
        if (!TextUtils.isEmpty(content)) {
            mContentView.setVisibility(View.VISIBLE);
            mContentView.setText(content);
            mIconView.setVisibility(View.GONE);
        }
    }

    /**
     * Sets the primary text color.
     */
    public void setSecondaryTextColor(@ColorInt int color) {
        mContentView.setTextColor(color);
    }

    /**
     * Sets the secondary text color.
     */
    public void setPrimaryTextColor(@ColorInt int color) {
        mTitleView.setTextColor(color);
    }

    /**
     * Resets the notification actions empty for recycling.
     */
    public void reset() {
        setVisibility(View.GONE);
        mTitleView.setVisibility(View.GONE);
        mContentView.setVisibility(View.GONE);
        mIconView.setVisibility(View.GONE);
        setPrimaryTextColor(mDefaultPrimaryTextColor);
        setSecondaryTextColor(mDefaultSecondaryTextColor);
    }
}
