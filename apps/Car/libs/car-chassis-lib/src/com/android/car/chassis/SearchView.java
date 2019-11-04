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
package com.android.car.chassis;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.HashSet;
import java.util.Set;

/**
 * A search view used by {@link Toolbar}.
 */
public class SearchView extends ConstraintLayout {
    private final ImageView mIcon;
    private final EditText mSearchText;

    private Set<Toolbar.Listener> mListeners = new HashSet<>();

    public SearchView(Context context) {
        this(context, null);
    }

    public SearchView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.chassis_search_view, this, true);

        mSearchText = requireViewById(R.id.search_bar);
        View closeIcon = requireViewById(R.id.search_close);
        closeIcon.setOnClickListener(view -> mSearchText.getText().clear());
        mIcon = requireViewById(R.id.icon);

        mSearchText.setOnFocusChangeListener(
                (view, hasFocus) -> {
                    if (hasFocus) {
                        ((InputMethodManager)
                                context.getSystemService(Context.INPUT_METHOD_SERVICE))
                                .showSoftInput(view, 0);
                    } else {
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
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_SEARCH) {
                mSearchText.clearFocus();
            }
            return false;
        });
    }

    /**
     * Adds a listener for the search text changing.
     * See also {@link #removeToolbarListener(Toolbar.Listener)}
     */
    public void addToolbarListener(Toolbar.Listener listener) {
        mListeners.add(listener);
    }

    /**
     * Removes a listener.
     * See also {@link #addToolbarListener(Toolbar.Listener)}
     * @param listener
     */
    public void removeToolbarListener(Toolbar.Listener listener) {
        mListeners.remove(listener);
    }

    /**
     * Sets the search hint.
     * @param resId A string resource id of the search hint.
     */
    public void setHint(int resId) {
        mSearchText.setHint(resId);
    }

    /**
     * Sets the search hint
     * @param hint A CharSequence of the search hint.
     */
    public void setHint(CharSequence hint) {
        mSearchText.setHint(hint);
    }

    /**
     * Sets a custom icon to display in the search box.
     */
    public void setIcon(Bitmap b) {
        mIcon.setImageBitmap(b);
    }

    /**
     * Sets a custom icon to display in the search box.
     */
    public void setIcon(Drawable d) {
        mIcon.setImageDrawable(d);
    }

    /**
     * Sets a custom icon to display in the search box.
     */
    public void setIcon(int resId) {
        mIcon.setImageResource(resId);
    }

    private void onSearch(String query) {
        for (Toolbar.Listener listener : mListeners) {
            listener.onSearch(query);
        }
    }

    /**
     * Sets the text being searched.
     */
    public void setSearchQuery(String query) {
        mSearchText.setText(query);
        mSearchText.setSelection(mSearchText.getText().length());
    }
}
