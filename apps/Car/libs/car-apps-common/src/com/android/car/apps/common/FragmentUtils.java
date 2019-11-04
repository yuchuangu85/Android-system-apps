/*
 * Copyright 2018 The Android Open Source Project
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

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import java.util.Objects;

/** Utility methods for working with Fragments */
public class FragmentUtils {

    private FragmentUtils() {
        // no instances
    }

    private static Object sParentForTesting;

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public static void setParentForTesting(Object parentForTesting) {
        FragmentUtils.sParentForTesting = parentForTesting;
    }

    /**
     * Returns the parent of {@code fragment} that implements the {@code parentType} or {@code null}
     * if no such parent can be found.
     */
    @CheckResult(suggest = "#checkParent(Fragment, Class)}")
    @Nullable
    public static <T> T getParent(@NonNull Fragment fragment, @NonNull Class<T> parentType) {
        if (parentType.isInstance(sParentForTesting)) {
            @SuppressWarnings("unchecked") // Casts are checked using runtime methods
                    T parent = (T) sParentForTesting;
            return parent;
        }

        Fragment parentFragment = fragment.getParentFragment();
        if (parentType.isInstance(parentFragment)) {
            @SuppressWarnings("unchecked") // Casts are checked using runtime methods
                    T parent = (T) parentFragment;
            return parent;
        }

        FragmentActivity activity = fragment.getActivity();
        if (parentType.isInstance(activity)) {
            @SuppressWarnings("unchecked") // Casts are checked using runtime methods
                    T parent = (T) activity;
            return parent;
        }
        return null;
    }

    /**
     * Returns the parent or throws. Should call {@link #checkParent(Fragment, Class)} or otherwise
     * enforce parent type elsewhere (e.g. {@link Fragment#onAttach(android.content.Context)
     * onAttach(Context)} or factory method).
     */
    @CheckResult(suggest = "#checkParent(Fragment, Class)}")
    @NonNull
    public static <T> T requireParent(@NonNull Fragment fragment, @NonNull Class<T> parentType) {
        return Objects.requireNonNull(getParent(fragment, parentType));
    }

    /**
     * Ensures {@code fragment} has a parent that implements the corresponding {@code parentType}
     *
     * @param fragment   The Fragment whose parents are to be checked
     * @param parentType The interface class that a parent should implement
     * @throws AssertionError if no parents are found that implement {@code parentType}
     */
    public static void checkParent(@NonNull Fragment fragment, @NonNull Class<?> parentType)
            throws AssertionError {
        if (sParentForTesting != null) {
            return;
        }
        if (FragmentUtils.getParent(fragment, parentType) != null) {
            return;
        }
        String parent;
        if (fragment.getParentFragment() == null) {
            if (fragment.getActivity() != null) {
                parent = fragment.getActivity().getClass().getName();
            } else if (fragment.getHost() != null) {
                parent = fragment.getHost().getClass().getName();
            } else {
                throw new AssertionError(fragment.getClass().getName()
                        + " must be added to a parent that implements "
                        + parentType.getName()
                        + " but is currently unattached to a parent.");
            }
        } else {
            parent = fragment.getParentFragment().getClass().getName();
        }
        throw new AssertionError(
                fragment.getClass().getName()
                        + " must be added to a parent that implements "
                        + parentType.getName()
                        + ". Instead found "
                        + parent);
    }
}
