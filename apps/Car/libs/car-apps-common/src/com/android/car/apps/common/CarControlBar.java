/*
 * Copyright 2019 The Android Open Source Project
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
import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;

/**
 * Interface for managing buttons to be shown in a control bar
 */
public interface CarControlBar {

    /**
     * Sets or clears the view to be shown at the given slot position. The view may not be shown if
     * the provided slot position is hidden
     */
    void setView(@Nullable View view, @SlotPosition int slotPosition);

    /**
     * Sets a list of views to be shown in the control bar. Not all the views are guaranteed to be
     * shown, only as many as the control bar has space to show.
     */
    void setViews(@Nullable View[] views);

    /**
     * Create an ImageButton with the provided icon to be used in this control bar.
     */
    ImageButton createIconButton(Drawable icon);

    /**
     * Create an ImageButton with the provided icon to be used in this control bar, and the view
     * id to inflate.
     */
    ImageButton createIconButton(Drawable icon, int viewId);


    @Retention(SOURCE)
    @IntDef({SLOT_MAIN, SLOT_LEFT, SLOT_RIGHT, SLOT_EXPAND_COLLAPSE})
    @interface SlotPosition {}

    /** Slot used for main actions {@link ControlBar}, usually at the bottom center */
    int SLOT_MAIN = 0;
    /** Slot used to host 'move left', 'rewind', 'previous' or similar secondary actions,
     * usually at the left of the main action on the bottom row */
    int SLOT_LEFT = 1;
    /** Slot used to host 'move right', 'fast-forward', 'next' or similar secondary actions,
     * usually at the right of the main action on the bottom row */
    int SLOT_RIGHT = 2;
    /** Slot reserved for the expand/collapse button */
    int SLOT_EXPAND_COLLAPSE = 3;


    /**
     * Returns an index for a well-known slot position, adapted to the number of columns.
     */
    static int getSlotIndex(@SlotPosition int slotPosition, int numColumns) {
        switch (slotPosition) {
            case SLOT_MAIN:
                return numColumns / 2;
            case SLOT_LEFT:
                return numColumns < 3 ? -1 : (numColumns / 2) - 1;
            case SLOT_RIGHT:
                return numColumns < 2 ? -1 : (numColumns / 2) + 1;
            case SLOT_EXPAND_COLLAPSE:
                return numColumns - 1;
            default:
                throw new IllegalArgumentException("Unknown position: " + slotPosition);
        }
    }
}
