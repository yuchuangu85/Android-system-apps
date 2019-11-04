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
package com.google.android.car.uxr.sample;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.ListFragment;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

/**
 * DialogFragment that selects UX restrictions for configuration.
 *
 * <p>Supports baseline and passenger mode.
 */
public class ConfigurationDialogFragment extends DialogFragment {

    static ConfigurationDialogFragment newInstance() {
        return new ConfigurationDialogFragment();
    }

    /**
     * Callback to be invoked when "confirm" button in the dialog is clicked.
     */
    interface OnConfirmListener {
        /**
         * Called when "confirm" button in the dialog is clicked.
         *
         * @param baseline Restrictions selected for baseline mode. See
         *                 {@link CarUxRestrictions#getActiveRestrictions()}.
         * @param passenger Restrictions selected for baseline mode.
         */
        void onConfirm(int baseline, int passenger);
    }

    private Button mPositiveButton;
    private TabLayout mTabLayout;
    private ViewPager mViewPager;
    private UxRestrictionsListFragmentPagerAdapter mAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_configuration_dialog, container,
                /* attachToRoot= */ false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mViewPager = view.findViewById(R.id.view_pager);
        mViewPager.setAdapter(mAdapter);

        mTabLayout = view.findViewById(R.id.tab_layout);
        mTabLayout.setupWithViewPager(mViewPager);

        mPositiveButton = view.findViewById(R.id.positive_button);
        mPositiveButton.setOnClickListener(v -> {
            ((OnConfirmListener) getActivity()).onConfirm(
                    mAdapter.mBaselineRestrictions, mAdapter.mPassengerRestrictions);
            dismiss();
        });
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mAdapter = new UxRestrictionsListFragmentPagerAdapter(context, getChildFragmentManager());
    }

    public static class UxRestrictionsListFragment extends ListFragment {

        public interface OnUxRestrictionsSelectedListener {
            void onUxRestrictionsSelected(int restrictions);
        }

        static UxRestrictionsListFragment newInstance() {
            return new UxRestrictionsListFragment();
        }

        /**
         * This field translate a UX restriction value to a string name.
         *
         * <p>Order of strings should be fixed. Index of string is based on number of bits shifted
         * in value of the constants. Namely, "NO_VIDEO" at index 4 maps to value of
         * {@link android.car.drivingstate.CarUxRestrictions#UX_RESTRICTIONS_NO_VIDEO} (0x1 << 4).
         */
        private static final CharSequence[] UX_RESTRICTION_NAMES = new CharSequence[]{
                "NO_DIALPAD",
                "NO_FILTERING",
                "LIMIT_STRING_LENGTH",
                "NO_KEYBOARD",
                "NO_VIDEO",
                "LIMIT_CONTENT",
                "NO_SETUP",
                "NO_TEXT_MESSAGE",
                "NO_VOICE_TRANSCRIPTION",
        };
        private OnUxRestrictionsSelectedListener mListener;

        public void setOnUxRestrictionsSelectedListener(OnUxRestrictionsSelectedListener listener) {
            mListener = listener;
        }

        private int getSelectedUxRestrictions() {
            int selectedRestrictions = 0;
            SparseBooleanArray selected = getListView().getCheckedItemPositions();
            for (int i = 0; i < UX_RESTRICTION_NAMES.length; i++) {
                if (selected.get(i)) {
                    selectedRestrictions += 1 << i;
                }
            }
            return selectedRestrictions;
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
            getListView().setOnItemClickListener((parent, v, position, id) -> {
                if (mListener != null) {
                    mListener.onUxRestrictionsSelected(getSelectedUxRestrictions());
                }
            });
            setListAdapter(new ArrayAdapter<CharSequence>(
                    getContext(),
                    android.R.layout.simple_list_item_multiple_choice,
                    UX_RESTRICTION_NAMES));
        }
    }

    private static class UxRestrictionsListFragmentPagerAdapter extends FragmentPagerAdapter {
        private final Context mContext;
        int mBaselineRestrictions;
        int mPassengerRestrictions;

        private final int[] mPageTitles = new int[]{
                R.string.tab_baseline, R.string.tab_passenger};

        UxRestrictionsListFragmentPagerAdapter(Context context, FragmentManager fragmentManager) {
            super(fragmentManager);
            mContext = context;
        }

        @Override
        public int getCount() {
            return mPageTitles.length;
        }

        @Override
        public Fragment getItem(int index) {
            return UxRestrictionsListFragment.newInstance();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mContext.getString(mPageTitles[position]);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            UxRestrictionsListFragment fragment = (UxRestrictionsListFragment)
                    super.instantiateItem(container, position);
            switch (position) {
                case 0:
                    fragment.setOnUxRestrictionsSelectedListener(
                            restrictions -> mBaselineRestrictions = restrictions);
                    break;
                case 1:
                    fragment.setOnUxRestrictionsSelectedListener(
                            restrictions -> mPassengerRestrictions = restrictions);
                    break;
                default:
                    throw new IllegalStateException("Unsupported page index " + position);
            }
            return fragment;
        }

    }
}
