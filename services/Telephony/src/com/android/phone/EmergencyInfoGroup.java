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

package com.android.phone;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.android.internal.util.UserIcons;

import java.util.List;

/**
 * EmergencyInfoGroup display user icon and user name. And it is an entry point to
 * Emergency Information.
 */
public class EmergencyInfoGroup extends FrameLayout implements View.OnClickListener {
    // Time to hide view of confirmation.
    private static final long HIDE_DELAY_MS = 3000;
    private static final int[] ICON_VIEWS =
            {R.id.emergency_info_image, R.id.confirmed_emergency_info_image};

    private TextView mEmergencyInfoName;
    private View mEmergencyInfoButton;
    private View mEmergencyInfoConfirmButton;

    private MotionEvent mPendingTouchEvent;
    private OnConfirmClickListener mOnConfirmClickListener;

    private boolean mConfirmViewHiding;

    public EmergencyInfoGroup(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Interface definition for a callback to be invoked when the view of confirmation on emergency
     * info button is clicked.
     */
    public interface OnConfirmClickListener {
        /**
         * Called when the view of confirmation on emergency info button has been clicked.
         *
         * @param button The shortcut button that was clicked.
         */
        void onConfirmClick(EmergencyInfoGroup button);
    }

    /**
     * Register a callback {@link OnConfirmClickListener} to be invoked when view of confirmation
     * is clicked.
     *
     * @param onConfirmClickListener The callback that will run.
     */
    public void setOnConfirmClickListener(OnConfirmClickListener onConfirmClickListener) {
        mOnConfirmClickListener = onConfirmClickListener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mEmergencyInfoButton = findViewById(R.id.emergency_info_view);
        mEmergencyInfoName = (TextView) findViewById(R.id.emergency_info_name);

        mEmergencyInfoConfirmButton = findViewById(R.id.emergency_info_confirm_view);

        mEmergencyInfoButton.setOnClickListener(this);
        mEmergencyInfoConfirmButton.setOnClickListener(this);

        mConfirmViewHiding = true;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == View.VISIBLE) {
            setupButtonInfo();
        }
    }

    private void setupButtonInfo() {
        List<ResolveInfo> infos;

        if (TelephonyManager.EMERGENCY_ASSISTANCE_ENABLED) {
            infos = EmergencyAssistanceHelper.resolveAssistPackageAndQueryActivities(getContext());
        } else {
            infos = null;
        }

        boolean visible = false;

        if (infos != null && infos.size() > 0) {
            final String packageName = infos.get(0).activityInfo.packageName;
            final Intent intent = new Intent(
                    EmergencyAssistanceHelper.getIntentAction())
                    .setPackage(packageName);
            setTag(R.id.tag_intent, intent);
            setUserIcon();

            visible = true;
        }
        mEmergencyInfoName.setText(getUserName());

        setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setUserIcon() {
        for (int iconView : ICON_VIEWS) {
            ImageView userIcon = findViewById(iconView);
            userIcon.setImageDrawable(getCircularUserIcon());
        }
    }

    /**
     * Get user icon.
     *
     * @return user icon, or default user icon if user do not set photo.
     */
    private Drawable getCircularUserIcon() {
        final UserManager userManager = (UserManager) getContext().getSystemService(
                Context.USER_SERVICE);
        Bitmap bitmapUserIcon = userManager.getUserIcon(UserHandle.getCallingUserId());

        if (bitmapUserIcon == null) {
            // get default user icon.
            final Drawable defaultUserIcon = UserIcons.getDefaultUserIcon(
                    getContext().getResources(), UserHandle.myUserId(), false);
            bitmapUserIcon = UserIcons.convertToBitmap(defaultUserIcon);
        }
        RoundedBitmapDrawable drawableUserIcon = RoundedBitmapDrawableFactory.create(
                getContext().getResources(), bitmapUserIcon);
        drawableUserIcon.setCircular(true);

        return drawableUserIcon;
    }

    private CharSequence getUserName() {
        final UserManager userManager = (UserManager) getContext().getSystemService(
                Context.USER_SERVICE);
        final String userName = userManager.getUserName();

        return TextUtils.isEmpty(userName) ? getContext().getText(
                R.string.emergency_information_owner_hint) : userName;
    }

    /**
     * Called by the activity before a touch event is dispatched to the view hierarchy.
     */
    public void onPreTouchEvent(MotionEvent event) {
        mPendingTouchEvent = event;
    }

    /**
     * Called by the activity after a touch event is dispatched to the view hierarchy.
     */
    public void onPostTouchEvent(MotionEvent event) {
        // Hide the confirmation button if a touch event was delivered to the activity but not to
        // this view.
        if (mPendingTouchEvent != null) {
            hideSelectedButton();
        }
        mPendingTouchEvent = null;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        boolean handled = super.dispatchTouchEvent(event);
        if (mPendingTouchEvent == event && handled) {
            mPendingTouchEvent = null;
        }
        return handled;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.emergency_info_view:
                if (AccessibilityManager.getInstance(mContext).isTouchExplorationEnabled()) {
                    if (mOnConfirmClickListener != null) {
                        mOnConfirmClickListener.onConfirmClick(this);
                    }
                } else {
                    revealSelectedButton();
                }
                break;
            case R.id.emergency_info_confirm_view:
                if (mOnConfirmClickListener != null) {
                    mOnConfirmClickListener.onConfirmClick(this);
                }
                break;
            default:
                break;
        }
    }

    private void revealSelectedButton() {
        mConfirmViewHiding = false;

        mEmergencyInfoConfirmButton.setVisibility(View.VISIBLE);
        int centerX = mEmergencyInfoButton.getLeft() + mEmergencyInfoButton.getWidth() / 2;
        int centerY = mEmergencyInfoButton.getTop() + mEmergencyInfoButton.getHeight() / 2;
        Animator reveal = ViewAnimationUtils.createCircularReveal(
                mEmergencyInfoConfirmButton,
                centerX,
                centerY,
                0,
                Math.max(centerX, mEmergencyInfoConfirmButton.getWidth() - centerX)
                        + Math.max(centerY, mEmergencyInfoConfirmButton.getHeight() - centerY));
        reveal.start();

        postDelayed(mCancelSelectedButtonRunnable, HIDE_DELAY_MS);
        mEmergencyInfoConfirmButton.requestFocus();
    }

    private void hideSelectedButton() {
        if (mConfirmViewHiding || mEmergencyInfoConfirmButton.getVisibility() != VISIBLE) {
            return;
        }

        mConfirmViewHiding = true;

        removeCallbacks(mCancelSelectedButtonRunnable);
        int centerX =
                mEmergencyInfoConfirmButton.getLeft() + mEmergencyInfoConfirmButton.getWidth() / 2;
        int centerY =
                mEmergencyInfoConfirmButton.getTop() + mEmergencyInfoConfirmButton.getHeight() / 2;
        Animator reveal = ViewAnimationUtils.createCircularReveal(
                mEmergencyInfoConfirmButton,
                centerX,
                centerY,
                Math.max(centerX, mEmergencyInfoButton.getWidth() - centerX)
                        + Math.max(centerY, mEmergencyInfoButton.getHeight() - centerY),
                0);
        reveal.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mEmergencyInfoConfirmButton.setVisibility(INVISIBLE);
            }
        });
        reveal.start();

        mEmergencyInfoButton.requestFocus();
    }

    private final Runnable mCancelSelectedButtonRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAttachedToWindow()) return;
            hideSelectedButton();
        }
    };
}
