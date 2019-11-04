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
import android.widget.Button;

import androidx.annotation.NonNull;

import com.android.car.radio.R;
import com.android.car.radio.bands.ProgramType;

import java.util.ArrayList;
import java.util.List;

/**
 * A band selector that shows a flat list of band buttons.
 */
public class BandSelectorFlat extends BandSelector {
    private final Object mLock = new Object();

    private final List<Button> mButtons = new ArrayList<>();

    public BandSelectorFlat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BandSelectorFlat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setSupportedProgramTypes(@NonNull List<ProgramType> supported) {
        synchronized (mLock) {
            super.setSupportedProgramTypes(supported);

            final LayoutInflater inflater = LayoutInflater.from(getContext());

            mButtons.clear();
            removeAllViews();
            for (ProgramType pt : supported) {
                Button btn = (Button) inflater.inflate(R.layout.band_selector_flat_button, null);
                btn.setText(pt.getLocalizedName());
                btn.setTag(pt);
                btn.setOnClickListener(v -> switchTo(pt));
                addView(btn);
                mButtons.add(btn);
            }
        }
    }

    @Override
    public void setType(@NonNull ProgramType ptype) {
        synchronized (mLock) {
            super.setType(ptype);

            Context ctx = getContext();
            for (Button btn : mButtons) {
                boolean active = btn.getTag() == ptype;
                btn.setTextColor(ctx.getColor(active
                        ? R.color.band_selector_flat_text_color_selected
                        : R.color.band_selector_flat_text_color));
                btn.setBackground(ctx.getDrawable(active
                        ? R.drawable.manual_tuner_button_background
                        : R.drawable.radio_control_background));
            }
        }
    }
}
