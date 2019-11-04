/**
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

package com.android.car.radio.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageButton;

import androidx.annotation.NonNull;

import com.android.car.radio.R;
import com.android.car.radio.bands.ProgramType;

/**
 * A band selector that cycles through bands with a single button.
 */
public class BandSelectorToggle extends BandSelector {
    private final ImageButton mButton;

    public BandSelectorToggle(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BandSelectorToggle(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        LayoutInflater.from(context).inflate(R.layout.band_selector_toggle, this, true);
        mButton = (ImageButton) getChildAt(0);  // ImageButton is the only child
        mButton.setOnClickListener(v -> switchToNext());
    }

    @Override
    public void setType(@NonNull ProgramType ptype) {
        super.setType(ptype);

        switch (ptype.id) {
            case ProgramType.ID_FM:
                mButton.setImageResource(R.drawable.ic_radio_fm);
                break;
            case ProgramType.ID_AM:
                mButton.setImageResource(R.drawable.ic_radio_am);
                break;
            default:
                mButton.setImageDrawable(null);
        }
    }
}
