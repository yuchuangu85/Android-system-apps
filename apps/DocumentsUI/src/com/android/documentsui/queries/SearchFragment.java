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

package com.android.documentsui.queries;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.android.documentsui.BaseActivity;
import com.android.documentsui.Injector;
import com.android.documentsui.R;

import java.util.List;

public class SearchFragment extends DialogFragment
        implements SearchView.OnQueryTextListener{

    private static final String TAG = "SearchFragment";
    private static final String KEY_QUERY = "query";
    private static final int MAX_DISPLAY_ITEMS = 8;

    private SearchViewManager mSearchViewManager;

    private SearchView mSearchView;
    private ViewGroup mSearchChipGroup;
    private ListView mListView;
    private ArrayAdapter<String> mAdapter;

    private List<String> mHistoryList;

    public static void showFragment(FragmentManager fm, String initQuery) {
        final SearchFragment fragment = new SearchFragment();
        final Bundle args = new Bundle();
        args.putString(KEY_QUERY, initQuery);
        fragment.setArguments(args);
        fragment.setStyle(DialogFragment.STYLE_NO_FRAME, R.style.DocumentsTheme);
        fragment.show(fm, TAG);
    }

    public static SearchFragment get(FragmentManager fm) {
        final Fragment fragment = fm.findFragmentByTag(TAG);
        return fragment instanceof SearchFragment
                ? (SearchFragment) fragment
                : null;
    }

    private void onChipClicked(View view) {
        final Object tag = view.getTag();
        if (tag instanceof SearchChipData) {
            mSearchViewManager.onMirrorChipClick((SearchChipData) tag);
            dismiss();
        }
    }

    private void onHistoryItemClicked(AdapterView<?> parent, View view, int position, long id) {
        final String item = mHistoryList.get(position);
        mSearchViewManager.setHistorySearch();
        mSearchView.setQuery(item, true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_search, container, false);

        final Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            mSearchViewManager.cancelSearch();
            dismiss();
        });

        mSearchView = view.findViewById(R.id.search_view);
        mSearchChipGroup = view.findViewById(R.id.search_chip_group);
        mListView = view.findViewById(R.id.history_list);

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final BaseActivity activity = (BaseActivity) getActivity();
        final Injector injector = activity.getInjector();
        mSearchViewManager = injector.searchManager;

        final String currentQuery = getArguments().getString(KEY_QUERY, "");

        mSearchView.onActionViewExpanded();
        mSearchView.setQuery(currentQuery, false);
        mSearchView.setOnQueryTextListener(this);
        mHistoryList = SearchHistoryManager.getInstance(
                getContext().getApplicationContext()).getHistoryList(currentQuery);

        mSearchViewManager.bindChips(mSearchChipGroup);
        if (mSearchChipGroup.getVisibility() == View.VISIBLE) {
            for (int i = 0, size = mSearchChipGroup.getChildCount(); i < size; i++) {
                mSearchChipGroup.getChildAt(i).setOnClickListener(this::onChipClicked);
            }
        }

        mAdapter = new HistoryListAdapter(getContext(), mHistoryList);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this::onHistoryItemClicked);
    }

    @Override
    public void onStart() {
        super.onStart();
        getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new Dialog(getActivity(), getTheme()){
            @Override
            public void onBackPressed() {
                if (TextUtils.isEmpty(mSearchView.getQuery())) {
                    mSearchViewManager.cancelSearch();
                } else {
                    mSearchViewManager.restoreSearch(false);
                }
                dismiss();
            }
        };
    }

    @Override
    public boolean onQueryTextSubmit(String s) {
        if (!TextUtils.isEmpty(mSearchView.getQuery())) {
            mSearchViewManager.setCurrentSearch(s);
            mSearchViewManager.restoreSearch(false);
            mSearchViewManager.recordHistory();
            dismiss();
        }
        return true;
    }

    @Override
    public boolean onQueryTextChange(String s) {
        if (!TextUtils.isEmpty(mSearchView.getQuery())) {
            mSearchViewManager.setCurrentSearch(s);
            mSearchViewManager.restoreSearch(true);
            dismiss();
        } else {
            mHistoryList = SearchHistoryManager.getInstance(
                    mSearchView.getContext().getApplicationContext()).getHistoryList("");
            mAdapter.clear();
            mAdapter.addAll(mHistoryList);
        }
        return true;
    }

    private class HistoryListAdapter extends ArrayAdapter<String> {

        public HistoryListAdapter(Context context, List<String> list) {
            super(context, R.layout.item_history, list);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_history, parent, false);
            }
            final String history = getItem(position);
            final TextView text = convertView.findViewById(android.R.id.title);
            final View button = convertView.findViewById(android.R.id.icon);

            text.setText(history);
            button.setOnClickListener(v -> {
                mSearchViewManager.removeHistory(history);
                mHistoryList.remove(history);
                notifyDataSetChanged();
            });
            button.setContentDescription(
                    getContext().getString(R.string.delete_search_history, history));

            return convertView;
        }

        @Override
        public int getCount() {
            final int count = super.getCount();
            return count > MAX_DISPLAY_ITEMS ? MAX_DISPLAY_ITEMS : count;
        }
    }
}
