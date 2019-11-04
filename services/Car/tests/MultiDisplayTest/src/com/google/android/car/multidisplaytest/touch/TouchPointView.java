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

package com.google.android.car.multidisplaytest.touch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Copied from EmbeddedKitchenSinkApp
 * Show touch points as circles; allow multiple touch points
 * Set LOG_ONLY = false to test on physical device.
 */
public class TouchPointView extends View {
    private static final String TAG = TouchPointView.class.getSimpleName();
    private static final boolean LOG_ONLY = false;
    private static final int[] COLORS = {
        Color.RED,
        Color.GREEN,
        Color.BLUE,
        Color.YELLOW,
        Color.MAGENTA,
        Color.BLACK,
        Color.DKGRAY
    };

    private final List<Finger> mFingers;
    private final Paint mPaint;

    public TouchPointView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TouchPointView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mFingers = new ArrayList<Finger>();

        mPaint = new Paint();
        mPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (LOG_ONLY) {
            logTouchEvents(event);
            return true;
        }
        mFingers.clear();
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            invalidate();
            return true;
        }
        for (int i = 0; i < event.getPointerCount(); i++) {
            int pointerId = event.getPointerId(i);
            int pointerIndex = event.findPointerIndex(pointerId);
            Finger finger = new Finger();
            finger.point =  new Point((int) event.getX(pointerIndex),
                    (int) event.getY(pointerIndex));
            finger.pointerId = pointerId;

            mFingers.add(finger);
        }
        invalidate();
        return true;
    }

    private void logTouchEvents(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_UP) {
            return;
        }

        for (int i = 0; i < event.getPointerCount(); i++) {
            int pointerId = event.getPointerId(i);
            int pointerIndex = event.findPointerIndex(pointerId);
            long downTime = event.getDownTime();
            long eventTime = event.getEventTime();
            Log.d(TAG, "TouchUp [x=" + event.getX(pointerIndex) + ", y=" + event.getY(pointerIndex)
                    + " , pointerId=" + pointerId + ", pointerIndex=" + pointerIndex + ", duration="
                    + (eventTime - downTime) + "]");
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (LOG_ONLY) {
            return;
        }
        int radius = canvas.getWidth() / 100;
        for (Finger finger: mFingers) {
            Point point = finger.point;
            int color = COLORS[finger.pointerId % COLORS.length];
            mPaint.setColor(color);
            canvas.drawCircle(point.x, point.y, radius, mPaint);
        }
    }

    private static final class Finger {
        public Point point;
        public int pointerId;
    }
}
