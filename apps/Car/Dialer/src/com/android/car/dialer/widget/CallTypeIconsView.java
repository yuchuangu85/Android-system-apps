/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.car.dialer.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.android.car.dialer.R;
import com.android.car.dialer.livedata.CallHistoryLiveData;

import java.util.ArrayList;
import java.util.List;

/**
 * View that draws one or more symbols for different types of calls (missed calls, outgoing etc).
 * The symbols are set up horizontally. As this view doesn't create subviews, it is better suited
 * for ListView-recycling that a regular LinearLayout using ImageViews.
 */
public class CallTypeIconsView extends View {
    // Limit the icons up to 3 and if there are more than 3 calls, append the call count at the end.
    private static final int MAX_CALL_TYPE_ICONS = 3;
    private static final String CALL_COUNT_FORMAT = "(%d)";

    private final List<Integer> mCallTypes = new ArrayList<>();
    private final IconResources mIconResources;
    private final int mSingleIconSize;
    private int mIconWidth;
    private int mIconHeight;
    private String mCallCountText;

    public CallTypeIconsView(Context context) {
        this(context, null);
    }

    public CallTypeIconsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CallTypeIconsView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public CallTypeIconsView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mIconResources = new IconResources(context);
        mIconResources.voicemail.setColorFilter(context.getColor(R.color.primary_icon_color),
                PorterDuff.Mode.SRC_IN);
        mSingleIconSize = getResources().getDimensionPixelSize(R.dimen.inline_icon_size);
    }

    public void clear() {
        setText(null);
        mCallTypes.clear();
        mIconWidth = 0;
        mIconHeight = 0;
        requestLayout();
    }

    /**
     * Call type icons are added in reverse chronological order that the most recent call will be
     * the first icon.
     */
    public void add(int callType) {
        mCallTypes.add(callType);
        if (mCallTypes.size() > MAX_CALL_TYPE_ICONS) {
            setText(String.format(CALL_COUNT_FORMAT, mCallTypes.size()));
            return;
        }

        setText(null);
        mIconWidth += mSingleIconSize + mIconResources.iconMargin;
        mIconHeight = Math.max(mIconHeight, mSingleIconSize + mIconResources.iconMargin);
        requestLayout();
    }

    public String getCallCountText() {
        return mCallCountText;
    }

    public int getCallType(int index) {
        return mCallTypes.get(index);
    }

    private Drawable getCallTypeDrawable(int callType) {
        switch (callType) {
            case CallHistoryLiveData.CallType.INCOMING_TYPE:
                return mIconResources.incoming;
            case CallHistoryLiveData.CallType.OUTGOING_TYPE:
                return mIconResources.outgoing;
            case CallHistoryLiveData.CallType.MISSED_TYPE:
                return mIconResources.missed;
            case CallHistoryLiveData.CallType.VOICEMAIL_TYPE:
                return mIconResources.voicemail;
            default:
                // It is possible for users to end up with calls with unknown call types in their
                // call history, possibly due to 3rd party call log implementations (e.g. to
                // distinguish between rejected and missed calls). Instead of crashing, just
                // assume that all unknown call types are missed calls.
                return mIconResources.missed;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mWidth = mIconWidth;
        int mHeight = Math.max(getMeasuredHeight(), mIconHeight);
        // Add extra end margin if show the count text.
        if (mCallTypes.size() > MAX_CALL_TYPE_ICONS) {
            mWidth += mIconResources.iconMargin;
        }
        setMeasuredDimension(mWidth, mHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw up to 3 icons.
        int left = 0;
        int iconCount = Math.min(MAX_CALL_TYPE_ICONS, mCallTypes.size());
        for (int i = 0; i < iconCount; i++) {
            final Drawable drawable = getCallTypeDrawable(mCallTypes.get(i));
            final int right = left + mSingleIconSize;
            drawable.setBounds(left, mIconResources.iconMargin, right,
                    mSingleIconSize + mIconResources.iconMargin);
            drawable.draw(canvas);
            left = right + mIconResources.iconMargin;
        }
    }

    private void setText(String text) {
        mCallCountText = text;
    }

    private static class IconResources {
        public final Drawable incoming;
        public final Drawable outgoing;
        public final Drawable missed;
        public final Drawable voicemail;
        public final int iconMargin;

        public IconResources(Context context) {
            incoming = context.getDrawable(R.drawable.ic_call_received);
            outgoing = context.getDrawable(R.drawable.ic_call_made);
            missed = context.getDrawable(R.drawable.ic_call_missed);
            voicemail = context.getDrawable(R.drawable.ic_voicemail);
            iconMargin = context.getResources().getDimensionPixelSize(R.dimen.call_log_icon_margin);
        }
    }
}
