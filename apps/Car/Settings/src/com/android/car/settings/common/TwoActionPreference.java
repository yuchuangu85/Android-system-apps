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

package com.android.car.settings.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.car.settings.R;

/**
 * A preference which can perform two actions. The secondary action is shown by default.
 * {@link #showAction(boolean)} may be used to manually set the visibility of the action.
 */
public abstract class TwoActionPreference extends Preference {

    private boolean mIsActionShown;

    public TwoActionPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    public TwoActionPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    public TwoActionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public TwoActionPreference(Context context) {
        super(context);
        init(/* attrs= */ null);
    }

    private void init(AttributeSet attrs) {
        setLayoutResource(R.layout.two_action_preference);
        TypedArray preferenceAttributes = getContext().obtainStyledAttributes(attrs,
                R.styleable.TwoActionPreference);
        mIsActionShown = preferenceAttributes.getBoolean(
                R.styleable.TwoActionPreference_actionShown, true);
    }

    /**
     * Sets whether the secondary action is visible in the preference.
     *
     * @param isShown {@code true} if the secondary action should be shown.
     */
    public void showAction(boolean isShown) {
        mIsActionShown = isShown;
        notifyChanged();
    }

    /** Returns {@code true} if action is shown. */
    public boolean isActionShown() {
        return mIsActionShown;
    }

    @Override
    public final void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View actionConatiner = holder.findViewById(R.id.action_widget_container);
        View widgetFrame = holder.findViewById(android.R.id.widget_frame);
        if (mIsActionShown) {
            actionConatiner.setVisibility(View.VISIBLE);
            onBindWidgetFrame(widgetFrame);
        } else {
            actionConatiner.setVisibility(View.GONE);
        }
    }

    /**
     * Binds the created View for the second action.
     *
     * <p>This is a good place to set properties on any custom view.
     *
     * @param widgetFrame The widget frame which controls the 2nd action.
     */
    protected abstract void onBindWidgetFrame(View widgetFrame);
}
