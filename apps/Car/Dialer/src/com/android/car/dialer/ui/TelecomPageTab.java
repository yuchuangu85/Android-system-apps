/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.dialer.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.android.car.apps.common.widget.CarTabLayout;
import com.android.car.dialer.R;
import com.android.car.dialer.ui.calllog.CallHistoryFragment;
import com.android.car.dialer.ui.contact.ContactListFragment;
import com.android.car.dialer.ui.dialpad.DialpadFragment;
import com.android.car.dialer.ui.favorite.FavoriteFragment;

import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;

/** Tab presenting fragments. */
public class TelecomPageTab extends CarTabLayout.CarTab {

    /** Note: the strings must be consist with the items in string array tabs_config */
    @StringDef({
            TelecomPageTab.Page.FAVORITES,
            TelecomPageTab.Page.CALL_HISTORY,
            TelecomPageTab.Page.CONTACTS,
            TelecomPageTab.Page.DIAL_PAD
    })
    public @interface Page {
        String FAVORITES = "FAVORITE";
        String CALL_HISTORY = "CALL_HISTORY";
        String CONTACTS = "CONTACTS";
        String DIAL_PAD = "DIAL_PAD";
    }

    private final Factory mFactory;
    private Fragment mFragment;
    private String mFragmentTag;
    private boolean mWasFragmentRestored;

    private TelecomPageTab(@Nullable Drawable icon, @Nullable CharSequence text, Factory factory) {
        super(icon, text);
        mFactory = factory;
    }

    /**
     * Either restore fragment from saved state or create new instance.
     */
    private void initFragment(FragmentManager fragmentManager, @Page String page) {
        mFragmentTag = makeFragmentTag(page);
        mFragment = fragmentManager.findFragmentByTag(mFragmentTag);
        if (mFragment == null) {
            mFragment = mFactory.createFragment(page);
            mWasFragmentRestored = false;
            return;
        }
        mWasFragmentRestored = true;
    }

    /** Returns true if the fragment for this tab is restored from a saved state. */
    public boolean wasFragmentRestored() {
        return mWasFragmentRestored;
    }

    /** Returns the fragment for this tab. */
    public Fragment getFragment() {
        return mFragment;
    }

    /** Returns the fragment tag for this tab. */
    public String getFragmentTag() {
        return mFragmentTag;
    }

    private String makeFragmentTag(@Page String page) {
        return String.format("%s:%s", getClass().getSimpleName(), page);
    }

    /** Responsible for creating the top tab items and their fragments. */
    public static class Factory {

        private static final ImmutableMap<String, Integer> TAB_LABELS =
                ImmutableMap.<String, Integer>builder()
                        .put(Page.FAVORITES, R.string.favorites_title)
                        .put(Page.CALL_HISTORY, R.string.call_history_title)
                        .put(Page.CONTACTS, R.string.contacts_title)
                        .put(Page.DIAL_PAD, R.string.dialpad_title)
                        .build();

        private static final ImmutableMap<String, Integer> TAB_ICONS =
                ImmutableMap.<String, Integer>builder()
                        .put(Page.FAVORITES, R.drawable.ic_favorite)
                        .put(Page.CALL_HISTORY, R.drawable.ic_history)
                        .put(Page.CONTACTS, R.drawable.ic_contact)
                        .put(Page.DIAL_PAD, R.drawable.ic_dialpad)
                        .build();

        private final FragmentManager mFragmentManager;
        private final Map<String, Integer> mTabPageIndexMap;
        private final String[] mTabs;

        public Factory(Context context, FragmentManager fragmentManager) {
            mFragmentManager = fragmentManager;

            mTabs = context.getResources().getStringArray(R.array.tabs_config);

            mTabPageIndexMap = new HashMap<>();
            for (int i = 0; i < getTabCount(); i++) {
                mTabPageIndexMap.put(mTabs[i], i);
            }
        }

        private Fragment createFragment(@Page String page) {
            switch (page) {
                case Page.FAVORITES:
                    return FavoriteFragment.newInstance();
                case Page.CALL_HISTORY:
                    return CallHistoryFragment.newInstance();
                case Page.CONTACTS:
                    return ContactListFragment.newInstance();
                case Page.DIAL_PAD:
                    return DialpadFragment.newPlaceCallDialpad();
                default:
                    throw new UnsupportedOperationException("Tab is not supported.");
            }
        }

        public TelecomPageTab createTab(Context context, int tabIndex) {
            String page = mTabs[tabIndex];
            TelecomPageTab telecomPageTab = new TelecomPageTab(
                    context.getDrawable(TAB_ICONS.get(page)),
                    context.getString(TAB_LABELS.get(page)), this);
            telecomPageTab.initFragment(mFragmentManager, page);
            return telecomPageTab;
        }

        public int getTabCount() {
            return mTabs.length;
        }

        public int getTabIndex(@Page String page) {
            return mTabPageIndexMap.containsKey(page) ? mTabPageIndexMap.get(page) : -1;
        }
    }
}
