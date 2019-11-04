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

package com.android.documentsui.ui;

import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

/**
 * A utility class for working with Views.
 */
public final class Views {

    private Views() {}

    /**
     * Return whether the event is in the view's region
     * @param event the motion event
     * @param view the view to check the selection region
     * @return True, if the event is in the region. Otherwise, return false.
     */
    public static boolean isEventOver(MotionEvent event, View view) {
        if (view == null || event == null || !view.isAttachedToWindow()) {
            return false;
        }

        final int[] coord = new int[2];
        view.getLocationOnScreen(coord);

        final Rect viewRect = new Rect(coord[0], coord[1], coord[0] + view.getMeasuredWidth(),
                coord[1] + view.getMeasuredHeight());

        return viewRect.contains((int) event.getRawX(), (int) event.getRawY());
    }
}
