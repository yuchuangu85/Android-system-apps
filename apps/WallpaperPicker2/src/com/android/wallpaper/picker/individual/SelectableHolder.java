/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.picker.individual;

import androidx.annotation.IntDef;

/**
 * Interface for a ViewHolder class which has a selection state which can be set by a caller.
 */
public interface SelectableHolder {
    int SELECTION_STATE_DESELECTED = 0;
    int SELECTION_STATE_LOADING = 1;
    int SELECTION_STATE_SELECTED = 2;

    void setSelectionState(@SelectionState int selectionState);

    /**
     * Possible selection states.
     */
    @IntDef({
            SELECTION_STATE_DESELECTED,
            SELECTION_STATE_LOADING,
            SELECTION_STATE_SELECTED,
    })
    @interface SelectionState {
    }
}
