/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.dialer.ui.dialpad;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.car.dialer.R;

/**
 * A View that represents a single button on the keypad. This View display a number above letters
 * or an image.
 */
public class KeypadButton extends FrameLayout {
    private static final int INVALID_IMAGE_RES = -1;

    private String mNumberText;
    private String mLetterText;
    private int mImageRes = INVALID_IMAGE_RES;

    public KeypadButton(Context context) {
        super(context);
        init(context, null);
    }

    public KeypadButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public KeypadButton(Context context, AttributeSet attrs, int defStyleAttrs) {
        super(context, attrs, defStyleAttrs);
        init(context, attrs);
    }

    public KeypadButton(Context context, AttributeSet attrs, int defStyleAttrs, int defStyleRes) {
        super(context, attrs, defStyleAttrs, defStyleRes);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        inflate(context, R.layout.keypad_button, this /* root */);

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.KeypadButton);

        try {
            mNumberText = ta.getString(R.styleable.KeypadButton_numberText);
            mLetterText = ta.getString(R.styleable.KeypadButton_letterText);
            mImageRes = ta.getResourceId(R.styleable.KeypadButton_image, INVALID_IMAGE_RES);
        } finally {
            if (ta != null) {
                ta.recycle();
            }
        }
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        // Using null check instead of a TextUtils.isEmpty() check so that an empty number/letter
        // can be used to keep the positioning of non-empty numbers/letters consistent.
        if (mNumberText != null) {
            TextView numberTextView = (TextView) findViewById(R.id.keypad_number);
            numberTextView.setText(mNumberText);
            numberTextView.setVisibility(VISIBLE);
        }

        if (mLetterText != null) {
            TextView letterTextView = (TextView) findViewById(R.id.keypad_letters);
            letterTextView.setText(mLetterText);
            letterTextView.setVisibility(VISIBLE);
        }

        if (mImageRes != INVALID_IMAGE_RES) {
            ImageView imageView = (ImageView) findViewById(R.id.keypad_image);
            imageView.setImageResource(mImageRes);
            imageView.setVisibility(VISIBLE);
        }
    }
}
