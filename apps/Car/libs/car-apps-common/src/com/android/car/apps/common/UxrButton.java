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

package com.android.car.apps.common;

import android.annotation.Nullable;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * UX Restrictions compliant Button.
 * This class will automatically listen to Car UXRestrictions, and respond to click event
 * accordingly. You can set one or multiple restrictions in the layout file, e.g.,
 * app:carUxRestrictions="UX_RESTRICTIONS_NO_SETUP|UX_RESTRICTIONS_NO_KEYBOARD"
 * If not set, it'll use UX_RESTRICTIONS_FULLY_RESTRICTED as fallback.
 * If no restriction is enforced, this Button will work as a normal Button; otherwise, its
 * OnClickListener will be disabled if any, and a blocking message will be displayed.
 *
 * This class extends from TextView instead of Button because only TextView supports gradient
 * truncate for now.
 */
public class UxrButton extends TextView {
    private static final int[] STATE_UX_RESTRICTED = {R.attr.state_ux_restricted};

    private CarUxRestrictionsUtil mCarUxRestrictionsUtil;
    private CarUxRestrictions mActiveCarUxRestrictions;
    private View.OnClickListener mOnClickListenerDelegate;

    @CarUxRestrictions.CarUxRestrictionsInfo
    private int mRestrictions;

    private final Handler mHandler = new Handler();

    private final CarUxRestrictionsUtil.OnUxRestrictionsChangedListener mListener =
            this::updateActiveCarUxRestrictions;

    private final View.OnClickListener mOnClickListenerWrapper = (View v) -> {
        if (mOnClickListenerDelegate == null) {
            return;
        }
        if (isRestricted()) {
            showBlockingMessage();
        } else {
            mOnClickListenerDelegate.onClick(v);
        }
    };

    public UxrButton(Context context) {
        super(context);
        init(context, null);
    }

    public UxrButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public UxrButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public UxrButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        mCarUxRestrictionsUtil = CarUxRestrictionsUtil.getInstance(context);
        super.setOnClickListener(mOnClickListenerWrapper);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.UxrButton);
        try {
            mRestrictions = a.getInteger(R.styleable.UxrButton_carUxRestrictions,
                    CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED);
        } finally {
            a.recycle();
        }
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (isRestricted()) {
            mergeDrawableStates(drawableState, STATE_UX_RESTRICTED);
        }
        return drawableState;
    }

    @Override
    public void setOnClickListener(@Nullable View.OnClickListener listener) {
        mOnClickListenerDelegate = listener;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mCarUxRestrictionsUtil.register(mListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mCarUxRestrictionsUtil.unregister(mListener);
    }

    private boolean isRestricted() {
        return CarUxRestrictionsUtil.isRestricted(mRestrictions, mActiveCarUxRestrictions);
    }

    private void updateActiveCarUxRestrictions(CarUxRestrictions carUxRestrictions) {
        mActiveCarUxRestrictions = carUxRestrictions;
        mHandler.post(() -> refreshDrawableState());
    }

    /**
     * Shows a message to inform the user that the current feature is not available when driving.
     */
    protected void showBlockingMessage() {
        Toast.makeText(getContext(), R.string.restricted_while_driving,
                Toast.LENGTH_SHORT).show();
    }
}
