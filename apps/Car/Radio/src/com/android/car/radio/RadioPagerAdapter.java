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

package com.android.car.radio;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

/**
 * Adapter containing all fragments used in the view pager
 */
public class RadioPagerAdapter extends FragmentPagerAdapter {

    private static final int DEFAULT_PAGE_COUNT = 2;
    private static final int[] TAB_LABELS =
            new int[]{R.string.favorites_tab, R.string.tune_tab, R.string.browse_tab};
    private static final int[] TAB_ICONS = new int[]{R.drawable.ic_star_filled,
            R.drawable.ic_input_antenna, R.drawable.ic_list};

    private RadioController mRadioController;
    private Context mContext;
    private int mPageCount;
    private Fragment mBrowseFragment;

    public RadioPagerAdapter(Context context, FragmentManager fragmentManager,
            RadioController controller) {
        super(fragmentManager);
        mRadioController = controller;
        mContext = context;
        mPageCount = DEFAULT_PAGE_COUNT;
    }

    @Override
    public Fragment getItem(int i) {
        switch (i) {
            case 0:
                return FavoritesFragment.newInstance(mRadioController);
            case 1:
                return ManualTunerFragment.newInstance(mRadioController);
            case 2:
                return mBrowseFragment;
        }
        return null;
    }

    @Override
    public int getCount() {
        return mPageCount;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return mContext.getResources().getString(TAB_LABELS[position]);
    }

    /**
     * Returns drawable of the icon to use for the tab at position
     */
    public Drawable getPageIcon(int position) {
        return mContext.getDrawable(TAB_ICONS[position]);
    }

    /**
     * @return true if browse tab is added to this adapter, false if tab already exists
     */
    public boolean addBrowseTab() {
        if (mBrowseFragment == null) {
            mPageCount++;
            mBrowseFragment = BrowseFragment.newInstance(mRadioController);
            notifyDataSetChanged();
            return true;
        }
        return false;
    }
}
