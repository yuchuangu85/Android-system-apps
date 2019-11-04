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
package com.android.car.dialer.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;
import android.widget.RelativeLayout;

/**
 * A {@link RelativeLayout} that blocks click/focus event from its checkable child view and sync
 * the checked state with it. Easy to use with
 * {@link android.app.AlertDialog.Builder#setSingleChoiceItems}.
 */
public class CheckableRelativeLayout extends RelativeLayout implements Checkable {
    private boolean mChecked;
    private Checkable mCheckableChildView;

    public CheckableRelativeLayout(Context context) {
        super(context);
    }

    public CheckableRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CheckableRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CheckableRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void onFinishInflate() {
        for (int i = 0; i < getChildCount(); i++) {
            View childView = getChildAt(i);
            if (childView instanceof Checkable) {
                childView.setClickable(false);
                childView.setFocusable(false);
                childView.setBackground(null);
                childView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                mCheckableChildView = (Checkable) childView;
                mCheckableChildView.setChecked(isChecked());
                return;
            }
        }
        super.onFinishInflate();
    }

    @Override
    public void setChecked(boolean checked) {
        if (mChecked != checked) {
            mChecked = checked;
            if (mCheckableChildView != null) {
                mCheckableChildView.setChecked(checked);
            }
        }
    }

    @Override
    public boolean isChecked() {
        return mChecked;
    }

    @Override
    public void toggle() {
        setChecked(!mChecked);
    }
}
