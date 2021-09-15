/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.deskclock.actionbarmenu

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.Menu.FIRST
import android.view.Menu.NONE
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener

import com.android.deskclock.R

/**
 * [MenuItemController] for search menu.
 */
class SearchMenuItemController(
    private val context: Context,
    private val queryListener: OnQueryTextListener,
    savedState: Bundle?
) : MenuItemController {

    override val id: Int = R.id.menu_item_search

    private val mSearchModeChangeListener: SearchModeChangeListener = SearchModeChangeListener()

    var queryText = ""
    private var mSearchMode = false

    init {
        if (savedState != null) {
            mSearchMode = savedState.getBoolean(KEY_SEARCH_MODE, false)
            queryText = savedState.getString(KEY_SEARCH_QUERY, "")
        }
    }

    fun saveInstance(outState: Bundle) {
        outState.putString(KEY_SEARCH_QUERY, queryText)
        outState.putBoolean(KEY_SEARCH_MODE, mSearchMode)
    }

    override fun onCreateOptionsItem(menu: Menu) {
        val searchView = SearchView(context)
        searchView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI)
        searchView.setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        searchView.setQuery(queryText, false)
        searchView.setOnCloseListener(mSearchModeChangeListener)
        searchView.setOnSearchClickListener(mSearchModeChangeListener)
        searchView.setOnQueryTextListener(queryListener)

        menu.add(NONE, id, FIRST, android.R.string.search_go)
                .setActionView(searchView)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)

        if (mSearchMode) {
            searchView.requestFocus()
            searchView.setIconified(false)
        }
    }

    override fun onPrepareOptionsItem(item: MenuItem) {
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // The search view is handled by {@link #mSearchListener}. Skip handling here.
        return false
    }

    /**
     * Listener for user actions on search view.
     */
    private inner class SearchModeChangeListener
        : View.OnClickListener, SearchView.OnCloseListener {

        override fun onClick(v: View?) {
            mSearchMode = true
        }

        override fun onClose(): Boolean {
            mSearchMode = false
            return false
        }
    }

    companion object {
        private const val KEY_SEARCH_QUERY = "search_query"
        private const val KEY_SEARCH_MODE = "search_mode"
    }
}