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

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.graphics.Rect;
import android.text.method.TransformationMethod;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

/**
 * UX Restrictions compliant TextView.
 * This class will automatically listen to Car UXRestrictions and truncate text accordingly.
 *
 * Attributes that trigger {@link TextView#setTransformationMethod} should NOT be set and calls to
 * the method should NOT be made to prevent overriding UX Restrictions.
 * This includes, but is not limited to:
 * {@link TextView#setInputType}
 * {@link TextView#setAllCaps}
 * {@link TextView#setSingleLine}
 * android:inputType="textPassword"
 * android:textAllCaps="true"
 * android:singleLine="true"
 */
public class UxrTextView extends TextView {
    private CarUxRestrictionsUtil mCarUxRestrictionsUtil;
    private CarUxRestrictions mCarUxRestrictions;
    private CarUxRestrictionsUtil.OnUxRestrictionsChangedListener mListener;

    public UxrTextView(Context context) {
        super(context);
        init(context);
    }

    public UxrTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public UxrTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public UxrTextView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        mCarUxRestrictionsUtil = CarUxRestrictionsUtil.getInstance(context);
        mListener = this::updateCarUxRestrictions;
    }

    private void updateCarUxRestrictions(CarUxRestrictions carUxRestrictions) {
        mCarUxRestrictions = carUxRestrictions;

        // setTransformationMethod doesn't do anything when passed the same instance...
        setTransformationMethod(new UXRTransformationMethod());
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

    @Override
    public void setAllCaps(boolean b) {
        // NOP
    }

    @Override
    public void setSingleLine(boolean b) {
        // NOP
    }

    @Override
    public void setInputType(int i) {
        // NOP
    }

    private class UXRTransformationMethod implements TransformationMethod {
        @Override
        public CharSequence getTransformation(CharSequence source, View view) {
            if (source == null) {
                return "";
            }
            return CarUxRestrictionsUtil.complyString(getContext(), source.toString(),
                    mCarUxRestrictions);
        }

        @Override
        public void onFocusChanged(View view, CharSequence sourceText, boolean focused,
                int direction, Rect previouslyFocusedRect) {
            // Not used
        }
    }
}
