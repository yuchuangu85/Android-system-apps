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

package com.android.tv.guide;

import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerViewAccessibilityDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** AccessibilityDelegate for {@link ProgramRow} */
class ProgramRowAccessibilityDelegate extends RecyclerViewAccessibilityDelegate {
    private final ItemDelegate mItemDelegate;

    ProgramRowAccessibilityDelegate(RecyclerView recyclerView) {
        super(recyclerView);

        mItemDelegate =
                new ItemDelegate(this) {
                    @Override
                    public boolean performAccessibilityAction(View host, int action, Bundle args) {
                        // Prevent Accessibility service to move the Program Row elements
                        // Ignoring Accessibility action above Set Text
                        // (accessibilityActionShowOnScreen)
                        if (action > AccessibilityNodeInfo.ACTION_SET_TEXT) {
                            return false;
                        }

                        return super.performAccessibilityAction(host, action, args);
                    }
                };
    }

    @Override
    public ItemDelegate getItemDelegate() {
        return mItemDelegate;
    }

    @Override
    public boolean onRequestSendAccessibilityEvent(
            ViewGroup host, View child, AccessibilityEvent event) {
        // Forcing the next item to be visible for scrolling in forward direction
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            ((ProgramRow) host).focusSearchAccessibility(child, View.FOCUS_FORWARD);
        }
        return super.onRequestSendAccessibilityEvent(host, child, event);
    }
}
