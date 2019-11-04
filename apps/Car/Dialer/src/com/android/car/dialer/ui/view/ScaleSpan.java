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

package com.android.car.dialer.ui.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;

/** {@link ReplacementSpan} that redraw the text with a scaled text size. */
public class ScaleSpan extends ReplacementSpan {
    private final float mStartTextSize;
    private float mTextSize;

    /**
     * This span is used in animation. To make sure the calculated width does not change during the
     * animation, we pass in the {@code startTextSize} to get the max width it will use.
     */
    public ScaleSpan(float startTextSize) {
        mStartTextSize = startTextSize;
    }

    /** Updates the current text size of this span. */
    public void setTextSize(float textSize) {
        mTextSize = textSize;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end,
            Paint.FontMetricsInt fm) {
        if (TextUtils.isEmpty(text)) {
            return 0;
        }
        TextPaint textPaint = new TextPaint(paint);
        textPaint.setTextSize(Math.max(mStartTextSize, paint.getTextSize()));
        // Remove span and measure, otherwise it will crash due to infinite loop
        float desiredWidth = StaticLayout.getDesiredWidth(text.toString(), start, end, textPaint);
        return (int) desiredWidth;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y,
            int bottom, Paint paint) {
        Rect targetRect = new Rect();
        paint.getTextBounds(text, start, end, targetRect);

        paint.setTextSize(mTextSize);
        Rect currentRect = new Rect();
        paint.getTextBounds(text, start, end, currentRect);

        int yShift = (currentRect.height() - targetRect.height()) / 2;
        canvas.drawText(text.subSequence(start, end).toString(), x, y + yShift, paint);
    }
}
