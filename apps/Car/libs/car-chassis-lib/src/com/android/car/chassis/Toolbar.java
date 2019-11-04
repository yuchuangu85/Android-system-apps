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

import android.annotation.StringRes;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A toolbar for Android Automotive OS apps.
 *
 * This isn't a toolbar in the android framework sense, it's merely a custom view that can be
 * added to a layout. (You can't call
 * {@link android.app.Activity#setActionBar(android.widget.Toolbar)} with it)
 *
 * The toolbar supports a navigation button, title, tabs, search, and custom buttons.
 */
public class Toolbar extends FrameLayout {

    /** Enum of states the toolbar can be in. Controls what elements of the toolbar are displayed */
    public enum State {
        /**
         * In the HOME state, the logo will be displayed if there is one, and no navigation icon
         * will be displayed. The tab bar will be visible. The title will be displayed if there
         * is space. Custom buttons will be displayed.
         */
        HOME,
        /**
         * In the SUBPAGE state, the logo will be replaced with a back button, the tab bar won't
         * be visible. The title and custom buttons will be displayed.
         */
        SUBPAGE,
        /**
         * In the SUBPAGE_CUSTOM state, everything is the same as SUBPAGE except the title will
         * be hidden and the custom view will be shown.
         */
        SUBPAGE_CUSTOM,
        /**
         * In the SEARCH state, only the back button and the search bar will be visible.
         */
        SEARCH,
    }

    private ImageView mNavIcon;
    private ImageView mLogo;
    private ViewGroup mNavIconContainer;
    private TextView mTitle;
    private TabLayout mTabLayout;
    private LinearLayout mButtonsContainer;
    private FrameLayout mCustomViewContainer;
    private Set<Listener> mListeners = new HashSet<>();
    private SearchView mSearchView;
    private boolean mHasLogo = false;
    private int[] mCurrentButtons;
    private boolean mShowButtonsWhileSearching;
    private View mSearchButton;
    private State mState = State.HOME;

    public Toolbar(Context context) {
        this(context, null);
    }

    public Toolbar(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.chassisToolbarStyle);
    }

    public Toolbar(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public Toolbar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.chassis_toolbar, this, true);

        mTabLayout = requireViewById(R.id.tabs);
        mNavIcon = requireViewById(R.id.nav_icon);
        mLogo = requireViewById(R.id.logo);
        mNavIconContainer = requireViewById(R.id.nav_icon_container);
        mButtonsContainer = requireViewById(R.id.buttons_container);
        mTitle = requireViewById(R.id.title);
        mSearchView = requireViewById(R.id.search_view);
        mCustomViewContainer = requireViewById(R.id.custom_view_container);

        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.ChassisToolbar, defStyleAttr, defStyleRes);

        mTitle.setText(a.getString(R.styleable.ChassisToolbar_title));
        setLogo(a.getResourceId(R.styleable.ChassisToolbar_logo, 0));
        setButtons(a.getResourceId(R.styleable.ChassisToolbar_buttons, 0));
        mShowButtonsWhileSearching = a.getBoolean(
                R.styleable.ChassisToolbar_showButtonsWhileSearching, false);
        String searchHint = a.getString(R.styleable.ChassisToolbar_searchHint);
        if (searchHint != null) {
            setSearchHint(searchHint);
        }

        a.recycle();

        // If an android:background attribute wasn't given, set the default one
        TypedArray viewAttributes = context.obtainStyledAttributes(
                attrs, com.android.internal.R.styleable.View, defStyleAttr, defStyleRes);
        if (viewAttributes.getDrawable(com.android.internal.R.styleable.View_background) == null) {
            setBackground(context.getDrawable(R.color.toolbar_background_color));
        }

        mTabLayout.addListener(new TabLayout.Listener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                forEachListener(listener -> listener.onTabSelected(tab));
            }
        });
    }

    /**
     * Sets the title of the toolbar to a string resource.
     *
     * The title may not always be shown, for example in landscape with tabs.
     */
    public void setTitle(@StringRes int title) {
        mTitle.setText(title);
    }

    /**
     * Sets the title of the toolbar to a CharSequence.
     *
     * The title may not always be shown, for example in landscape with tabs.
     */
    public void setTitle(CharSequence title) {
        mTitle.setText(title);
    }

    /**
     * Gets the {@link TabLayout} for this toolbar.
     */
    public TabLayout getTabLayout() {
        return mTabLayout;
    }

    /**
     * Adds a tab to this toolbar. You can listen for when it is selected via
     * {@link #addListener(Listener)}.
     */
    public void addTab(TabLayout.Tab tab) {
        mTabLayout.addTab(tab);
    }

    /**
     * Gets a tab added to this toolbar. See
     * {@link #addTab(TabLayout.Tab)}.
     */
    public TabLayout.Tab getTab(int position) {
        return mTabLayout.get(position);
    }

    /**
     * Selects a tab added to this toolbar. See
     * {@link #addTab(TabLayout.Tab)}.
     */
    public void selectTab(int position) {
        mTabLayout.selectTab(position);
    }

    /**
     * Sets the logo to display in this toolbar.
     * Will not be displayed if a navigation icon is currently being displayed.
     */
    public void setLogo(int resId) {
        if (resId != 0) {
            mLogo.setImageResource(resId);
            mHasLogo = true;
        } else {
            mHasLogo = false;
        }
        setState(mState);
    }

    /**
     * Sets the hint for the search bar.
     */
    public void setSearchHint(int resId) {
        mSearchView.setHint(resId);
    }

    /**
     * Sets the hint for the search bar.
     */
    public void setSearchHint(CharSequence hint) {
        mSearchView.setHint(hint);
    }

    /**
     * Sets the buttons to be shown. Click events for these buttons will be received in
     * {@link Listener#onCustomButtonPressed(View)}.
     *
     * Buttons are encouraged to use @drawable/chassis_toolbar_button_background as their
     * background. In the default implementation it's a ripple that is sized appropriately to fit
     * the toolbar.
     *
     * R.layout.chassis_toolbar_search_button can be used to add a search button, which will have an
     * id of R.id.search in {@link Listener#onCustomButtonPressed(View)} callback.
     *
     * R.layout.chassis_toolbar_settings_button can be used to add a search button, which will have
     * an id of R.id.settings in {@link Listener#onCustomButtonPressed(View)} callback.
     *
     * @param buttons An array of layout ids specifying the buttons to show. Toolbar will keep
     *                a reference to this array, so don't modify it afterwards.
     */
    public void setButtons(@Nullable int[] buttons) {
        if (!Arrays.equals(buttons, mCurrentButtons)) {
            mButtonsContainer.removeAllViews();

            if (buttons != null) {
                LayoutInflater inflater = (LayoutInflater) getContext()
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                for (int button : buttons) {
                    View v = inflater.inflate(button, mButtonsContainer, false);
                    mButtonsContainer.addView(v);

                    setupCustomButton(v);
                }
            }

            mSearchButton = mButtonsContainer.findViewById(R.id.search);
            mCurrentButtons = buttons;
            setState(mState);
        }
    }

    private void setButtons(TypedArray buttons) {
        int[] layouts = new int[buttons.length()];
        for (int i = 0; i < buttons.length(); ++i) {
            layouts[i] = buttons.getResourceId(i, 0);
        }
        setButtons(layouts);
    }

    /**
     * Sets the buttons to be shown, based on an XML array of layouts. See
     * {@link #setButtons(int[])} for more info.
     *
     * @param arrayId A resource id of an array of layouts.
     */
    public void setButtons(int arrayId) {
        if (arrayId == 0) {
            mButtonsContainer.removeAllViews();
            mCurrentButtons = null;
        } else {
            setButtons(getContext().getResources().obtainTypedArray(arrayId));
        }
    }

    private void setupCustomButton(View v) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.rightMargin = getContext().getResources().getDimensionPixelSize(
                R.dimen.chassis_toolbar_custom_button_margin);
        lp.leftMargin = lp.rightMargin;
        lp.gravity = Gravity.CENTER_VERTICAL;

        v.setOnClickListener(x -> forEachListener(listener -> listener.onCustomButtonPressed(x)));
    }

    /**
     * Set whether or not to show the custom buttons while searching. Default false.
     * Even if this is set to true, if there is a button with the id "search", such as the button
     * added by R.layout.chassis_toolbar_search_button, that buttons will always be hidden.
     */
    public void setShowButtonsWhileSearching(boolean showButtons) {
        mShowButtonsWhileSearching = showButtons;
        setState(mState);
    }

    /**
     * Sets the search query.
     */
    public void setSearchQuery(String query) {
        mSearchView.setSearchQuery(query);
    }

    /**
     * Sets a custom view to display, and sets the current state to {@link State#SUBPAGE_CUSTOM}.
     *
     * @param resId A layout id of the view to display.
     * @return The inflated custom view.
     */
    public View setCustomView(int resId) {
        mCustomViewContainer.removeAllViews();
        LayoutInflater inflater = (LayoutInflater) getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(resId, mCustomViewContainer, false);
        mCustomViewContainer.addView(v);
        setState(State.SUBPAGE_CUSTOM);
        return v;
    }

    /**
     * Sets the state of the toolbar. This will show/hide the appropriate elements of the toolbar
     * for the desired state.
     */
    public void setState(State state) {
        mState = state;

        View.OnClickListener backClickListener = (v) -> forEachListener(Listener::onBack);
        mNavIcon.setVisibility(state != State.HOME ? VISIBLE : INVISIBLE);
        mNavIcon.setImageResource(state != State.HOME ? R.drawable.ic_arrow_back : 0);
        mLogo.setVisibility(state == State.HOME && mHasLogo ? VISIBLE : INVISIBLE);
        mNavIconContainer.setVisibility(state != State.HOME || mHasLogo ? VISIBLE : GONE);
        mNavIconContainer.setClickable(state != State.HOME);
        mNavIconContainer.setOnClickListener(state != State.HOME ? backClickListener : null);
        mTitle.setVisibility(state == State.HOME || state == State.SUBPAGE ? VISIBLE : GONE);
        mTabLayout.setVisibility(state == State.HOME ? VISIBLE : GONE);
        mSearchView.setVisibility(state == State.SEARCH ? VISIBLE : GONE);
        mButtonsContainer.setVisibility(state != State.SEARCH || mShowButtonsWhileSearching
                ? VISIBLE : GONE);
        if (mSearchButton != null) {
            mSearchButton.setVisibility(state != State.SEARCH ? VISIBLE : GONE);
        }
        mCustomViewContainer.setVisibility(state == State.SUBPAGE_CUSTOM ? VISIBLE : GONE);
        if (state != State.SUBPAGE_CUSTOM) {
            mCustomViewContainer.removeAllViews();
        }
    }

    /**
     * Toolbar listener.
     */
    public interface Listener {
        /**
         * Invoked when the user selects an item from the tabs
         */
        default void onTabSelected(TabLayout.Tab item) {}

        /**
         * Invoked when the user clicks on the back button
         */
        default void onBack() {}

        /**
         * Invoked when the user submits a search query.
         */
        default void onSearch(String query) {}

        /**
         * Invoked when the user clicks on a custom button
         * @param v The button that was clicked
         */
        default void onCustomButtonPressed(View v) {}
    }

    /**
     * Adds a {@link Listener} to this toolbar.
     */
    public void addListener(Listener listener) {
        mListeners.add(listener);
        mSearchView.addToolbarListener(listener);
    }

    /**
     * Removes a {@link Listener} from this toolbar.
     */
    public boolean removeListener(Listener listener) {
        mSearchView.removeToolbarListener(listener);
        return mListeners.remove(listener);
    }

    private void forEachListener(Consumer<Listener> callback) {
        List<Listener> listenersCopy = new ArrayList<>(mListeners);
        for (Listener listener : listenersCopy) {
            callback.accept(listener);
        }
    }
}
