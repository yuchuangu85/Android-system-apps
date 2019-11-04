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

package com.android.car.dialer.ui.search;

import android.app.ActionBar;
import android.app.SearchManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.dialer.R;
import com.android.car.dialer.log.L;
import com.android.car.dialer.ui.common.DialerListBaseFragment;
import com.android.car.dialer.ui.contact.ContactDetailsFragment;
import com.android.car.telephony.common.Contact;

/**
 * A fragment that will take a search query, look up contacts that match and display those
 * results as a list.
 */
public class ContactResultsFragment extends DialerListBaseFragment implements
        ContactResultsAdapter.OnShowContactDetailListener {

    /**
     * Creates a new instance of the {@link ContactResultsFragment}.
     *
     * @param initialSearchQuery An optional search query that will be inputted when the fragment
     *                           starts up.
     */
    public static ContactResultsFragment newInstance(@Nullable String initialSearchQuery) {
        ContactResultsFragment fragment = new ContactResultsFragment();
        Bundle args = new Bundle();
        args.putString(SEARCH_QUERY, initialSearchQuery);
        fragment.setArguments(args);
        return fragment;
    }

    public static final String FRAGMENT_TAG = "ContactResultsFragment";

    private static final String TAG = "CD.ContactResultsFragment";
    private static final String SEARCH_QUERY = "SearchQuery";
    private static final String KEY_KEYBOARD_SHOWN = "KeyboardShown";

    private ContactResultsViewModel mContactResultsViewModel;
    private final ContactResultsAdapter mAdapter = new ContactResultsAdapter(this);

    private RecyclerView.OnScrollListener mOnScrollChangeListener;
    private SearchView mSearchView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mContactResultsViewModel = ViewModelProviders.of(this).get(
                ContactResultsViewModel.class);
        mContactResultsViewModel.getContactSearchResults().observe(this,
                contactResults -> mAdapter.setData(contactResults));

        // Set the initial search query, if one was provided from a Intent.ACTION_SEARCH
        if (getArguments() != null) {
            String initialQuery = getArguments().getString(SEARCH_QUERY);
            if (!TextUtils.isEmpty(initialQuery)) {
                setSearchQuery(initialQuery);
            }
            getArguments().clear();
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        getRecyclerView().setAdapter(mAdapter);

        mOnScrollChangeListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy != 0) {
                    mSearchView.clearFocus();
                }
            }
        };
        getRecyclerView().addOnScrollListener(mOnScrollChangeListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getRecyclerView().removeOnScrollListener(mOnScrollChangeListener);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_contacts_search).setVisible(false);
    }

    @Override
    public void setupActionBar(@NonNull ActionBar actionBar) {
        super.setupActionBar(actionBar);

        // We have to use the setCustomView that accepts a LayoutParams to get the SearchView
        // to take up the full height and width of the action bar.
        View v = getLayoutInflater().inflate(R.layout.search_view, null);
        actionBar.setCustomView(v, new ActionBar.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        SearchView searchView = actionBar.getCustomView().findViewById(R.id.search_view);
        SearchManager searchManager =
                (SearchManager) getContext().getSystemService(Context.SEARCH_SERVICE);
        searchView.setSearchableInfo(
                searchManager.getSearchableInfo(getActivity().getComponentName()));

        // We need to call setIconified(false) so the SearchView is a text box instead of just
        // an icon, but doing so also focuses on it and shows the keyboard.
        searchView.setIconified(false);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                L.d(TAG, "onQueryTextSubmit: %s", query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                L.d(TAG, "onQueryTextChange: %s", newText);
                onNewQuery(newText);
                return false;
            }
        });

        // Don't collapse the search view by clicking the clear button on an empty input
        searchView.setOnCloseListener(() -> true);

        mSearchView = searchView;
        setSearchQuery(mContactResultsViewModel.getSearchQuery());
    }

    @Override
    public void onStop() {
        super.onStop();
        mSearchView.clearFocus();
    }

    /**
     * Sets the search query that should be used to filter contacts.
     */
    public void setSearchQuery(String query) {
        if (mSearchView != null) {
            // This will update the search field and trigger the onNewQuery.
            // "submit" flag is false so it won't send search intent and ending in infinite loop.
            mSearchView.setQuery(query, /* submit= */false);
        } else {
            onNewQuery(query);
        }
    }

    /** Triggered by search view text change. */
    private void onNewQuery(String newQuery) {
        mContactResultsViewModel.setSearchQuery(newQuery);
    }

    @Override
    protected CharSequence getActionBarTitle() {
        return null;
    }

    @Override
    public void onShowContactDetail(Contact contact) {
        Fragment contactDetailsFragment = ContactDetailsFragment.newInstance(contact);
        pushContentFragment(contactDetailsFragment, ContactDetailsFragment.FRAGMENT_TAG);
    }
}
