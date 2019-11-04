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

package com.android.car.media.common;

import android.annotation.Nullable;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.fragment.app.FragmentActivity;

import com.android.car.apps.common.util.ViewUtils;
import com.android.car.media.common.source.MediaSourceViewModel;

/**
 * Widget showing the icon of the currently selected media application as well as an arrow
 * indicating whether the selection UI is shown. The widget can be embedded both in an application
 * bar where tapping it opens an {@link AppSelectionFragment}, and also in the UI of the selection
 * fragment to provide visual continuity and a way to close the fragment without selecting an
 * application.
 * In order for the widget to connect to {@link MediaSourceViewModel} (so it can update its icon),
 * {@link #setFragmentActivity} must be called by the code that creates a view containing this
 * widget.
 */
public class MediaAppSelectorWidget extends LinearLayout {

    private final boolean mFullScreenDialog;
    private final boolean mSwitchingEnabled;
    private final ImageView mAppIcon;
    private final ImageView mAppSwitchIcon;
    private final Drawable mDefaultIcon;
    private final Drawable mArrowDropDown;
    private final Drawable mArrowDropUp;

    private FragmentActivity mActivity;

    /** The fragment that owns the widget (only set when in display only mode). */
    @Nullable
    private AppSelectionFragment mFragmentOwner;
    private boolean mFragmentIsOpen;

    public MediaAppSelectorWidget(Context context) {
        this(context, null);
    }

    public MediaAppSelectorWidget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MediaAppSelectorWidget(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public MediaAppSelectorWidget(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.MediaAppSelectorWidget, defStyleAttr, 0 /* defStyleRes */);
        mFullScreenDialog = a.getBoolean(R.styleable.MediaAppSelectorWidget_fullScreenDialog, true);
        mSwitchingEnabled = a.getBoolean(R.styleable.MediaAppSelectorWidget_switchingEnabled, true);
        int size = (int) a.getDimension(R.styleable.MediaAppSelectorWidget_appIconSize,
                getResources().getDimension(R.dimen.app_switch_widget_app_icon_size));
        a.recycle();

        mDefaultIcon = getResources().getDrawable(R.drawable.ic_music);
        mArrowDropDown = getResources().getDrawable(R.drawable.ic_media_select_arrow_drop_down,
                null);
        mArrowDropUp = getResources().getDrawable(R.drawable.ic_media_select_arrow_drop_up, null);

        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.app_switch_widget, this, true);

        mAppIcon = findViewById(R.id.app_icon);
        mAppIcon.getLayoutParams().height = size;
        mAppIcon.getLayoutParams().width = size;
        mAppSwitchIcon = findViewById(R.id.app_switch_icon);

        setFragmentOwner(null);
        if (mSwitchingEnabled) {
            setOnClickListener(view -> onAppSwitchClicked());
        } else {
            ViewUtils.setVisible(mAppSwitchIcon, false);
        }
    }

    /** Calling this is required so the widget can show the icon of the primary media source. */
    public void setFragmentActivity(FragmentActivity activity) {
        mActivity = activity;
        MediaSourceViewModel model = MediaSourceViewModel.get(activity.getApplication());
        model.getPrimaryMediaSource().observe(activity, source -> {
            if (source == null) {
                setAppIcon(null);
            } else {
                setAppIcon(source.getRoundPackageIcon());
            }
        });
    }

    /** Opens the {@link AppSelectionFragment}. */
    public void open() {
        if (mSwitchingEnabled && !mFragmentIsOpen) {
            onAppSwitchClicked();
        }
    }

    /** Closes the {@link AppSelectionFragment}. */
    public void close() {
        if (mSwitchingEnabled && mFragmentIsOpen) {
            onAppSwitchClicked();
        }
    }

    /** Sets whether the widget is shown as part of an {@link AppSelectionFragment} UI. */
    void setFragmentOwner(@Nullable AppSelectionFragment fragmentOwner) {
        mFragmentOwner = fragmentOwner;
        setIsOpen(mFragmentOwner != null);
    }

    void setIsOpen(boolean fragmentIsOpen) {
        if (mSwitchingEnabled) {
            mFragmentIsOpen = fragmentIsOpen;
            mAppSwitchIcon.setImageDrawable(fragmentIsOpen ? mArrowDropUp : mArrowDropDown);
        }
    }

    /**
     * Updates the application icon to show next to the application switcher.
     */
    private void setAppIcon(Bitmap icon) {
        if (icon != null) {
            mAppIcon.setImageBitmap(icon);
        } else {
            mAppIcon.setImageDrawable(mDefaultIcon);
        }
    }

    private void onAppSwitchClicked() {
        if (mFragmentOwner != null) {
            mFragmentOwner.dismiss();
        } else {
            setIsOpen(true);
            AppSelectionFragment newFragment = AppSelectionFragment.create(this, mFullScreenDialog);
            newFragment.show(mActivity.getSupportFragmentManager(), null);
        }

    }
}
