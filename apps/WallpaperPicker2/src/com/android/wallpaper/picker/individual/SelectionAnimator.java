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

/**
 * Interface for a class which adds a visual selection state (which may be animated between the
 * selected and deselected state) to a ViewHolder.
 */
public interface SelectionAnimator {
    boolean isSelected();

    /**
     * Sets the UI to selected immediately with no animation.
     */
    void selectImmediately();

    /**
     * Sets the UI to deselected immediately with no animation.
     */
    void deselectImmediately();

    /**
     * Sets the UI to selected with a smooth animation.
     */
    void animateSelected();

    /**
     * Sets the UI to deselected with a smooth animation.
     */
    void animateDeselected();

    /**
     * Sets the UI to show a loading indicator.
     */
    void showLoading();

    /**
     * Sets the UI to hide the loading indicator.
     */
    void showNotLoading();
}
