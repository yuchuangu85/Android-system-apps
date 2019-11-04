/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.base;

import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * Utility code for dealing with MotionEvents.
 */
public final class Events {

    public static boolean isMouseEvent(MotionEvent e) {
        return e.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE;
    }

    public static boolean isActionDown(MotionEvent e) {
        return e.getActionMasked() == MotionEvent.ACTION_DOWN;
    }

    public static boolean isActionUp(MotionEvent e) {
        return e.getActionMasked() == MotionEvent.ACTION_UP;
    }

    public static boolean isMultiPointerActionDown(MotionEvent e) {
        return e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN;
    }

    public static boolean isMultiPointerActionUp(MotionEvent e) {
        return e.getActionMasked() == MotionEvent.ACTION_POINTER_UP;
    }

    public static boolean isCtrlKeyPressed(MotionEvent e) {
        return hasBit(e.getMetaState(), KeyEvent.META_CTRL_ON);
    }

    public static boolean isAltKeyPressed(MotionEvent e) {
        return hasBit(e.getMetaState(), KeyEvent.META_ALT_ON);
    }

    public static boolean isShiftKeyPressed(MotionEvent e) {
        return hasBit(e.getMetaState(), KeyEvent.META_SHIFT_ON);
    }

    private static boolean hasBit(int metaState, int bit) {
        return (metaState & bit) != 0;
    }

    /**
     * @return true if keyCode is a known navigation code (e.g. up, down, home).
     */
    public static boolean isNavigationKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MOVE_HOME:
            case KeyEvent.KEYCODE_MOVE_END:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return true;
            default:
                return false;
        }
    }
}
