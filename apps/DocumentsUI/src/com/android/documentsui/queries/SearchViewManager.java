/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.base.State.ACTION_GET_CONTENT;
import static com.android.documentsui.base.State.ACTION_OPEN;
import static com.android.documentsui.base.State.ActionType;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnActionExpandListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.SearchView.OnQueryTextListener;

import com.android.documentsui.MetricConsts;
import com.android.documentsui.Metrics;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.EventHandler;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Manages searching UI behavior.
 */
public class SearchViewManager implements
        SearchView.OnCloseListener, OnQueryTextListener, OnClickListener, OnFocusChangeListener,
        OnActionExpandListener {

    private static final String TAG = "SearchManager";

    // How long we wait after the user finishes typing before kicking off a search.
    public static final int SEARCH_DELAY_MS = 750;

    private final SearchManagerListener mListener;
    private final EventHandler<String> mCommandProcessor;
    private final SearchChipViewManager mChipViewManager;
    private final Timer mTimer;
    private final Handler mUiHandler;

    private final Object mSearchLock;
    @GuardedBy("mSearchLock")
    private @Nullable Runnable mQueuedSearchRunnable;
    @GuardedBy("mSearchLock")
    private @Nullable TimerTask mQueuedSearchTask;
    private @Nullable String mCurrentSearch;
    private String mQueryContentFromIntent;
    private boolean mSearchExpanded;
    private boolean mIgnoreNextClose;
    private boolean mFullBar;
    private boolean mIsHistorySearch;
    private boolean mShowSearchBar;

    private Menu mMenu;
    private MenuItem mMenuItem;
    private SearchView mSearchView;

    public SearchViewManager(
            SearchManagerListener listener,
            EventHandler<String> commandProcessor,
            ViewGroup chipGroup,
            @Nullable Bundle savedState) {
        this(listener, commandProcessor, new SearchChipViewManager(chipGroup), savedState,
                new Timer(), new Handler(Looper.getMainLooper()));
    }

    @VisibleForTesting
    protected SearchViewManager(
            SearchManagerListener listener,
            EventHandler<String> commandProcessor,
            SearchChipViewManager chipViewManager,
            @Nullable Bundle savedState,
            Timer timer,
            Handler handler) {
        assert (listener != null);
        assert (commandProcessor != null);

        mSearchLock = new Object();
        mListener = listener;
        mCommandProcessor = commandProcessor;
        mTimer = timer;
        mUiHandler = handler;
        mChipViewManager = chipViewManager;
        mChipViewManager.setSearchChipViewManagerListener(this::onChipCheckedStateChanged);

        if (savedState != null) {
            mCurrentSearch = savedState.getString(Shared.EXTRA_QUERY);
            mChipViewManager.restoreCheckedChipItems(savedState);
        } else {
            mCurrentSearch = null;
        }
    }

    private void onChipCheckedStateChanged(View v) {
        mListener.onSearchChipStateChanged(v);
        performSearch(mCurrentSearch);
    }

    /**
     * Parse the query content from Intent. If the action is not {@link State#ACTION_GET_CONTENT}
     * or {@link State#ACTION_OPEN}, don't perform search.
     * @param intent the intent to parse.
     * @param action the action to check.
     * @return True, if get the query content from the intent. Otherwise, false.
     */
    public boolean parseQueryContentFromIntent(Intent intent, @ActionType int action) {
        if (action == ACTION_OPEN || action == ACTION_GET_CONTENT) {
            final String queryString = intent.getStringExtra(Intent.EXTRA_CONTENT_QUERY);
            if (!TextUtils.isEmpty(queryString)) {
                mQueryContentFromIntent = queryString;
                return true;
            }
        }
        return false;
    }

    /**
     * Build the bundle of query arguments.
     * Example: search string and mime types
     *
     * @return the bundle of query arguments
     */
    public Bundle buildQueryArgs() {
        final Bundle queryArgs = new Bundle();
        if (!TextUtils.isEmpty(mCurrentSearch)) {
            queryArgs.putString(DocumentsContract.QUERY_ARG_DISPLAY_NAME, mCurrentSearch);
        }

        final String[] checkedMimeTypes = mChipViewManager.getCheckedMimeTypes();
        if (checkedMimeTypes != null && checkedMimeTypes.length > 0) {
            queryArgs.putStringArray(DocumentsContract.QUERY_ARG_MIME_TYPES, checkedMimeTypes);
        }
        return queryArgs;
    }

    /**
     * Initialize the search chips base on the acceptMimeTypes.
     *
     * @param acceptMimeTypes use to filter chips
     */
    public void initChipSets(String[] acceptMimeTypes) {
        mChipViewManager.initChipSets(acceptMimeTypes);
    }

    /**
     * Update the search chips base on the acceptMimeTypes.
     * If the count of matched chips is less than two, we will
     * hide the chip row.
     *
     * @param acceptMimeTypes use to filter chips
     */
    public void updateChips(String[] acceptMimeTypes) {
        mChipViewManager.updateChips(acceptMimeTypes);
    }

    /**
     * Bind chip data in ChipViewManager on other view groups
     *
     * @param chipGroup target view group for bind ChipViewManager data
     */
    public void bindChips(ViewGroup chipGroup) {
        mChipViewManager.bindMirrorGroup(chipGroup);
    }

    /**
     * Click behavior when chip in synced chip group click.
     *
     * @param data SearchChipData synced in mirror group
     */
    public void onMirrorChipClick(SearchChipData data) {
        mChipViewManager.onMirrorChipClick(data);
        mSearchView.clearFocus();
    }

    /**
     * Initailize search view by option menu.
     *
     * @param menu the menu include search view
     * @param isFullBarSearch whether hide other menu when search view expand
     * @param isShowSearchBar whether replace collapsed search view by search hint text
     */
    public void install(Menu menu, boolean isFullBarSearch, boolean isShowSearchBar) {
        mMenu = menu;
        mMenuItem = mMenu.findItem(R.id.option_menu_search);
        mSearchView = (SearchView) mMenuItem.getActionView();

        mSearchView.setOnQueryTextListener(this);
        mSearchView.setOnCloseListener(this);
        mSearchView.setOnSearchClickListener(this);
        mSearchView.setOnQueryTextFocusChangeListener(this);
        final View clearButton = mSearchView.findViewById(R.id.search_close_btn);
        if (clearButton != null) {
            clearButton.setOnClickListener(v -> {
                mSearchView.setQuery("", false);
                mListener.onSearchViewClearClicked();
            });
        }

        mFullBar = isFullBarSearch;
        mShowSearchBar = isShowSearchBar;
        mSearchView.setMaxWidth(Integer.MAX_VALUE);
        mMenuItem.setOnActionExpandListener(this);

        restoreSearch(false);
    }

    /**
     * Used to hide menu icons, when the search is being restored. Needed because search restoration
     * is done before onPrepareOptionsMenu(Menu menu) that is overriding the icons visibility.
     */
    public void updateMenu() {
        if (isExpanded() && mFullBar) {
            mMenu.setGroupVisible(R.id.group_hide_when_searching, false);
        }
    }

    /**
     * @param stack New stack.
     */
    public void update(DocumentStack stack) {
        if (mMenuItem == null) {
            if (DEBUG) {
                Log.d(TAG, "update called before Search MenuItem installed.");
            }
            return;
        }

        if (mCurrentSearch != null) {
            mMenuItem.expandActionView();

            mSearchView.setIconified(false);
            mSearchView.clearFocus();
            mSearchView.setQuery(mCurrentSearch, false);
        } else {
            mSearchView.clearFocus();
            if (!mSearchView.isIconified()) {
                mIgnoreNextClose = true;
                mSearchView.setIconified(true);
            }

            if (mMenuItem.isActionViewExpanded()) {
                mMenuItem.collapseActionView();
            }
        }

        showMenu(stack);
    }

    public void showMenu(@Nullable DocumentStack stack) {
        final DocumentInfo cwd = stack != null ? stack.peek() : null;

        boolean supportsSearch = true;

        // Searching in archives is not enabled, as archives are backed by
        // a different provider than the root provider.
        if (cwd != null && cwd.isInArchive()) {
            supportsSearch = false;
        }

        final RootInfo root = stack != null ? stack.getRoot() : null;
        if (root == null || !root.supportsSearch()) {
            supportsSearch = false;
        }

        if (mMenuItem == null) {
            if (DEBUG) {
                Log.d(TAG, "showMenu called before Search MenuItem installed.");
            }
            return;
        }

        if (!supportsSearch) {
            mCurrentSearch = null;
        }

        // Recent root show open search bar, do not show duplicate search icon.
        mMenuItem.setVisible(supportsSearch && (!stack.isRecents() || !mShowSearchBar));

        mChipViewManager.setChipsRowVisible(supportsSearch && root.supportsMimeTypesSearch());
    }

    /**
     * Cancels current search operation. Triggers clearing and collapsing the SearchView.
     *
     * @return True if it cancels search. False if it does not operate search currently.
     */
    public boolean cancelSearch() {
        if (mSearchView != null && (isExpanded() || isSearching())) {
            cancelQueuedSearch();
            // If the query string is not empty search view won't get iconified
            mSearchView.setQuery("", false);

            if (mFullBar) {
                onClose();
            } else {
                // Causes calling onClose(). onClose() is triggering directory content update.
                mSearchView.setIconified(true);
            }

            return true;
        }
        return false;
    }

    private void cancelQueuedSearch() {
        synchronized (mSearchLock) {
            if (mQueuedSearchTask != null) {
                mQueuedSearchTask.cancel();
            }
            mQueuedSearchTask = null;
            mUiHandler.removeCallbacks(mQueuedSearchRunnable);
            mQueuedSearchRunnable = null;
            mIsHistorySearch = false;
        }
    }

    /**
     * Sets search view into the searching state. Used to restore state after device orientation
     * change.
     */
    public void restoreSearch(boolean keepFocus) {
        if (isTextSearching()) {
            onSearchBarClicked();
            mSearchView.setQuery(mCurrentSearch, false);

            if (keepFocus) {
                mSearchView.requestFocus();
            } else {
                mSearchView.clearFocus();
            }
        }
    }

    public void onSearchBarClicked() {
        mMenuItem.expandActionView();
        onSearchExpanded();
    }

    private void onSearchExpanded() {
        mSearchExpanded = true;
        if (mFullBar) {
            mMenu.setGroupVisible(R.id.group_hide_when_searching, false);
        }

        mListener.onSearchViewChanged(true);
    }

    /**
     * Clears the search. Triggers refreshing of the directory content.
     *
     * @return True if the default behavior of clearing/dismissing SearchView should be overridden.
     *         False otherwise.
     */
    @Override
    public boolean onClose() {
        mSearchExpanded = false;
        if (mIgnoreNextClose) {
            mIgnoreNextClose = false;
            return false;
        }

        // Refresh the directory if a search was done
        if (mCurrentSearch != null || mChipViewManager.hasCheckedItems()) {
            // Clear checked chips
            mChipViewManager.clearCheckedChips();
            mCurrentSearch = null;
            mListener.onSearchChanged(mCurrentSearch);
        }

        if (mFullBar) {
            mMenuItem.collapseActionView();
        }
        mListener.onSearchFinished();

        mListener.onSearchViewChanged(false);

        return false;
    }

    /**
     * Called when owning activity is saving state to be used to restore state during creation.
     *
     * @param state Bundle to save state too
     */
    public void onSaveInstanceState(Bundle state) {
        state.putString(Shared.EXTRA_QUERY, mCurrentSearch);
        mChipViewManager.onSaveInstanceState(state);
    }

    /**
     * Sets mSearchExpanded. Called when search icon is clicked to start search for both search view
     * modes.
     */
    @Override
    public void onClick(View v) {
        onSearchExpanded();
    }

    @Override
    public boolean onQueryTextSubmit(String query) {

        if (mCommandProcessor.accept(query)) {
            mSearchView.setQuery("", false);
        } else {
            cancelQueuedSearch();
            // Don't kick off a search if we've already finished it.
            if (!TextUtils.equals(mCurrentSearch, query)) {
                mCurrentSearch = query;
                mListener.onSearchChanged(mCurrentSearch);
            }
            recordHistory();
            mSearchView.clearFocus();
        }

        return true;
    }

    /**
     * Used to detect and handle back button pressed event when search is expanded.
     */
    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (!hasFocus && !mChipViewManager.hasCheckedItems()) {
            if (mCurrentSearch == null) {
                mSearchView.setIconified(true);
            } else if (TextUtils.isEmpty(mSearchView.getQuery())) {
                cancelSearch();
            }
        }
        mListener.onSearchViewFocusChanged(hasFocus);
    }

    @VisibleForTesting
    protected TimerTask createSearchTask(String newText) {
        return new TimerTask() {
            @Override
            public void run() {
                // Do the actual work on the main looper.
                synchronized (mSearchLock) {
                    mQueuedSearchRunnable = () -> {
                        mCurrentSearch = newText;
                        if (mCurrentSearch != null && mCurrentSearch.isEmpty()) {
                            mCurrentSearch = null;
                        }
                        logTextSearchMetric();
                        mListener.onSearchChanged(mCurrentSearch);
                    };
                    mUiHandler.post(mQueuedSearchRunnable);
                }
            }
        };
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        //Skip first search when search expanded
        if (!(mCurrentSearch == null && newText.isEmpty())) {
            performSearch(newText);
        }
        return true;
    }

    private void performSearch(String newText) {
        cancelQueuedSearch();
        synchronized (mSearchLock) {
            mQueuedSearchTask = createSearchTask(newText);

            mTimer.schedule(mQueuedSearchTask, SEARCH_DELAY_MS);
        }
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        mMenu.setGroupVisible(R.id.group_hide_when_searching, true);

        // Handles case when search view is collapsed by using the arrow on the left of the bar
        if (isExpanded() || isSearching()) {
            cancelSearch();
            return false;
        }
        return true;
    }

    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        return true;
    }

    public String getCurrentSearch() {
        return mCurrentSearch;
    }

    /**
     * Get current text on search view.
     *
     * @return  Cuttent string on search view
     */
    public String getSearchViewText() {
        return mSearchView.getQuery().toString();
    }

    /**
     * Record current search for history.
     */
    public void recordHistory() {
        SearchHistoryManager.getInstance(
                mSearchView.getContext().getApplicationContext()).addHistory(mCurrentSearch);
    }

    /**
     * Remove specific text item in history list.
     *
     * @param history target string for removed.
     */
    public void removeHistory(String history) {
        SearchHistoryManager.getInstance(
                mSearchView.getContext().getApplicationContext()).deleteHistory(history);
    }

    private void logTextSearchMetric() {
        if (isTextSearching()) {
            Metrics.logUserAction(mIsHistorySearch
                    ? MetricConsts.USER_ACTION_SEARCH_HISTORY : MetricConsts.USER_ACTION_SEARCH);
            Metrics.logSearchType(mIsHistorySearch
                    ? MetricConsts.TYPE_SEARCH_HISTORY : MetricConsts.TYPE_SEARCH_STRING);
            mIsHistorySearch = false;
        }
    }

    /**
     * Get the query content from intent.
     * @return If has query content, return the query content. Otherwise, return null
     * @see #parseQueryContentFromIntent(Intent, int)
     */
    public String getQueryContentFromIntent() {
        return mQueryContentFromIntent;
    }

    public void setCurrentSearch(String queryString) {
        mCurrentSearch = queryString;
    }

    /**
     * Set next search type is history search.
     */
    public void setHistorySearch() {
        mIsHistorySearch = true;
    }

    public boolean isSearching() {
        return mCurrentSearch != null || mChipViewManager.hasCheckedItems();
    }

    private boolean isTextSearching() {
        return mCurrentSearch != null;
    }

    public boolean hasCheckedChip() {
        return mChipViewManager.hasCheckedItems();
    }

    public boolean isExpanded() {
        return mSearchExpanded;
    }

    public interface SearchManagerListener {
        void onSearchChanged(@Nullable String query);

        void onSearchFinished();

        void onSearchViewChanged(boolean opened);

        void onSearchChipStateChanged(View v);

        void onSearchViewFocusChanged(boolean hasFocus);

        /**
         * Call back when search view clear button clicked
         */
        void onSearchViewClearClicked();
    }
}
