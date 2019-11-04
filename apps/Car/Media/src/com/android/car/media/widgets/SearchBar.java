/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.media.widgets;

import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.fragment.app.FragmentActivity;

import com.android.car.media.R;
import com.android.car.media.common.MediaAppSelectorWidget;

/**
 * This widget represents a search  bar that shows the media source's icon as part of the search bar
 */
public class SearchBar extends LinearLayout {

    private final MediaAppSelectorWidget mAppIcon;
    private final EditText mSearchText;
    private final ImageView mCloseIcon;

    private AppBarView.AppBarListener mListener;

    public SearchBar(Context context) {
        this(context, null);
    }

    public SearchBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchBar(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public SearchBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.search_bar, this, true);

        mSearchText = findViewById(R.id.search_bar);
        mCloseIcon = findViewById(R.id.search_close);
        mCloseIcon.setOnClickListener(view -> mSearchText.getText().clear());
        mAppIcon = findViewById(R.id.app_icon_container);

        mSearchText.setOnFocusChangeListener(
                (view, hasFocus) -> {
                    if (hasFocus) {
                        mSearchText.setCursorVisible(true);
                        ((InputMethodManager)
                                context.getSystemService(Context.INPUT_METHOD_SERVICE))
                                .showSoftInput(view, 0);
                    } else {
                        mSearchText.setCursorVisible(false);
                        ((InputMethodManager)
                                context.getSystemService(Context.INPUT_METHOD_SERVICE))
                                .hideSoftInputFromWindow(view.getWindowToken(), 0);
                    }
                });
        mSearchText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                onSearch(editable.toString());
            }
        });
        mSearchText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                mSearchText.setCursorVisible(false);
            }
            return false;
        });
    }

    /** Calling this is required so the widget can show the icon of the primary media source. */
    public void setFragmentActivity(FragmentActivity activity) {

        mAppIcon.setFragmentActivity(activity);
    }

    public void setAppBarListener(AppBarView.AppBarListener listener) {
        mListener = listener;
    }

    public void showSearchBar(boolean visible) {
        if (visible) {
            setVisibility(VISIBLE);
            mSearchText.requestFocus();
        } else{
            setVisibility(GONE);
            mSearchText.getText().clear();
        }
    }

    private void onSearch(String query) {
        if (mListener == null || TextUtils.isEmpty(query)) {
            mCloseIcon.setVisibility(GONE);
            return;
        }
        mCloseIcon.setVisibility(VISIBLE);
        mListener.onSearch(query);
    }
}
