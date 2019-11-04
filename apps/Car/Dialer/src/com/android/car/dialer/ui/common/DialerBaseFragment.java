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

package com.android.car.dialer.ui.common;

import android.app.ActionBar;
import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.car.apps.common.util.Themes;
import com.android.car.dialer.R;
import com.android.car.dialer.ui.TelecomActivity;

/** The base class for top level dialer content {@link Fragment}s. */
public abstract class DialerBaseFragment extends Fragment {

    /**
     * Interface for Dialer top level fragment's parent to implement.
     */
    public interface DialerFragmentParent {

        /** Sets the background drawable. */
        void setBackground(Drawable background);

        /** Push a fragment to the back stack. Update action bar accordingly. */
        void pushContentFragment(Fragment fragment, String fragmentTag);
    }

    /** Customizes the action bar. Can be overridden in subclasses. */
    public void setupActionBar(@NonNull ActionBar actionBar) {
        actionBar.setTitle(getActionBarTitle());
        actionBar.setCustomView(null);
        setActionBarBackground(getContext().getDrawable(R.color.app_bar_background_color));
    }

    /** Push a fragment to the back stack. Update action bar accordingly. */
    protected void pushContentFragment(@NonNull Fragment fragment, String fragmentTag) {
        Activity parentActivity = getActivity();
        if (parentActivity instanceof DialerFragmentParent) {
            ((DialerFragmentParent) parentActivity).pushContentFragment(fragment, fragmentTag);
        }
    }

    /** Return the action bar title. */
    protected CharSequence getActionBarTitle() {
        return getString(R.string.default_toolbar_title);
    }

    protected int getTopBarHeight() {
        View toolbar = getActivity().findViewById(R.id.car_toolbar);

        int backStackEntryCount =
                getActivity().getSupportFragmentManager().getBackStackEntryCount();
        int topBarHeight = Themes.getAttrDimensionPixelSize(getContext(),
                android.R.attr.actionBarSize);
        // Tabs are not child of the toolbar and tabs are visible.
        if (toolbar.findViewById(R.id.tab_layout) == null && backStackEntryCount == 1) {
            topBarHeight += topBarHeight;
        }
        return topBarHeight;
    }

    protected final void setActionBarBackground(@Nullable Drawable drawable) {
        Activity activity = getActivity();
        if (activity instanceof TelecomActivity) {
            ((TelecomActivity) activity).setActionBarBackground(drawable);
        }
    }
}
