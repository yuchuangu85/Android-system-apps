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

package android.car.cluster;

import android.annotation.Nullable;
import android.graphics.Rect;
import android.os.Bundle;

/**
 * Helper class that represents activity state in the cluster and can be serialized / deserialized
 * to/from bundle.
 *
 * @hide
 */
public class ClusterActivityState {
    private static final String KEY_VISIBLE = "android.car:activityState.visible";
    private static final String KEY_UNOBSCURED_BOUNDS = "android.car:activityState.unobscured";
    private static final String KEY_EXTRAS = "android.car:activityState.extras";

    private boolean mVisible = true;
    private Rect mUnobscuredBounds;
    private Bundle mExtras;

    /**
     * Returns true if the cluster is currently able to display content, or false if the content
     * area of the cluster is hidden.
     */
    public boolean isVisible() {
        return mVisible;
    }

    /**
     * Get a rectangle inside the cluster content area that is not covered by any decorations.
     * Activities designed to display content in the instrument cluster can use this information to
     * determine where to display user-relevant content, while using the rest of the window for
     * content bleeding. For example, a navigation activity could decide to display current road
     * inside this rectangle, while drawing additional map background outside this area.
     * <p>
     * All values of this {@link Rect} represent absolute coordinates inside the activity canvas.
     */
    @Nullable public Rect getUnobscuredBounds() {
        return mUnobscuredBounds;
    }

    /**
     * Get any custom extras that were set on this activity state.
     */
    @Nullable public Bundle getExtras() {
        return mExtras;
    }

    /**
     * Sets whether the cluster is currently able to display content, or false if content area of
     * the cluster is hidden.
     *
     * @return this instance for chaining.
     */
    public ClusterActivityState setVisible(boolean visible) {
        mVisible = visible;
        return this;
    }

    /**
     * Sets a rectangle inside that cluster content area that is not covered by any decorations.
     * Activities designed to display content in the cluster can use this to determine where to
     * display user-relevant content, while using the rest of the window for content bleeding.
     *
     * @param unobscuredBounds a {@link Rect} describing the area inside the activity canvas that is
     *                         not covered by any decorations. All values of this {@link Rect}
     *                         represent absolute coordinates inside the activity canvas.
     * @return this instance for chaining.
     */
    public ClusterActivityState setUnobscuredBounds(Rect unobscuredBounds) {
        mUnobscuredBounds = unobscuredBounds;
        return this;
    }

    /**
     * Set any custom extras to be included with the activity state.
     *
     * @return this instance for chaining.
     */
    public ClusterActivityState setExtras(Bundle bundle) {
        mExtras = bundle;
        return this;
    }

    /** Use factory methods instead. */
    private ClusterActivityState() {}

    /**
     * Creates a {@link ClusterActivityState} with the given visibility and unobscured bounds (see
     * {@link #setVisible(boolean)} and {@link #setUnobscuredBounds(Rect)} for more details)
     */
    public static ClusterActivityState create(boolean visible, Rect unobscuredBounds) {
        return new ClusterActivityState()
                .setVisible(visible)
                .setUnobscuredBounds(unobscuredBounds);
    }

    /**
     * Reconstructs a {@link ClusterActivityState} from a {@link Bundle}
     */
    public static ClusterActivityState fromBundle(Bundle bundle) {
        return new ClusterActivityState()
                .setVisible(bundle.getBoolean(KEY_VISIBLE, true))
                .setUnobscuredBounds((Rect) bundle.getParcelable(KEY_UNOBSCURED_BOUNDS))
                .setExtras(bundle.getBundle(KEY_EXTRAS));
    }

    /**
     * Returns a {@link Bundle} representation of this instance. This bundle can then be
     * deserialized using {@link #fromBundle(Bundle)}.
     */
    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putBoolean(KEY_VISIBLE, mVisible);
        b.putParcelable(KEY_UNOBSCURED_BOUNDS, mUnobscuredBounds);
        b.putBundle(KEY_EXTRAS, mExtras);
        return b;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " {"
                + "visible: " + mVisible + ", "
                + "unobscuredBounds: " + mUnobscuredBounds + ", "
                + "extras: " + mExtras
                + " }";
    }
}
