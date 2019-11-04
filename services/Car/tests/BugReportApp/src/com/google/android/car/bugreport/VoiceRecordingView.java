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
package com.google.android.car.bugreport;

import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaRecorder;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * A view that draws MIC icon and an animated ellipsoid. The ellipsoid animation shows the sound
 * amplitude from {@link MediaRecorder}.
 *
 * <p>All the constant values are chosen experimentally.
 */
public class VoiceRecordingView extends View {
    private static final String TAG = VoiceRecordingView.class.getSimpleName();

    private static final float DROPOFF_STEP = 10f;
    private static final long ANIMATION_INTERVAL_MS = 70;
    private static final float RECORDER_AMPLITUDE_NORMALIZER_COEF = 16192.0f;

    private final Paint mPaint;
    private final BitmapDrawable mMicIconDrawable;

    private float mCurrentRadius;
    private MediaRecorder mRecorder;

    public VoiceRecordingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mMicIconDrawable = (BitmapDrawable) context.getDrawable(
                android.R.drawable.ic_btn_speak_now);

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(Color.LTGRAY);
        mPaint.setStyle(Paint.Style.FILL);
    }

    /** Sets MediaRecorder that will be used to animate the ellipsoid. */
    public void setRecorder(@Nullable MediaRecorder recorder) {
        mRecorder = recorder;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);

        float micIconWidth = mMicIconDrawable.getBitmap().getWidth();
        float micIconHeight = mMicIconDrawable.getBitmap().getHeight();
        int micIconDrawableWidth = (int) (micIconWidth / micIconHeight * h);
        int micIconDrawableLeft = (w - micIconDrawableWidth) / 2;
        mMicIconDrawable.setBounds(
                new Rect(micIconDrawableLeft, 0, micIconDrawableLeft + micIconDrawableWidth, h));
    }

    private void updateCurrentRadius(int width) {
        final float maxRadius = width / 4;
        float radius = 0;

        if (mRecorder != null) {
            try {
                radius += maxRadius * mRecorder.getMaxAmplitude()
                        / RECORDER_AMPLITUDE_NORMALIZER_COEF;
            } catch (IllegalStateException e) {
                Log.v(TAG, "Failed to get max amplitude from MediaRecorder");
            }
        }

        if (radius > mCurrentRadius) {
            mCurrentRadius = radius;
        } else {
            mCurrentRadius = Math.max(radius, mCurrentRadius - DROPOFF_STEP);
        }
        mCurrentRadius = Math.min(maxRadius, mCurrentRadius);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final int width = canvas.getWidth();
        final int height = canvas.getHeight();

        updateCurrentRadius(width);

        // Draws an ellipsoid with horizontal radius calculated from MediaRecorder's amplitude.
        final int mx = width / 2;
        final int my = height / 2;
        canvas.drawCircle(mx, my, height / 2, mPaint);
        canvas.drawCircle(mx - mCurrentRadius, my, height / 2, mPaint);
        canvas.drawCircle(mx + mCurrentRadius, my, height / 2, mPaint);
        canvas.drawRect(mx - mCurrentRadius, 0, mx + mCurrentRadius, height, mPaint);

        if (mRecorder != null) {
            postInvalidateDelayed(ANIMATION_INTERVAL_MS);
        }

        mMicIconDrawable.draw(canvas);
    }
}
