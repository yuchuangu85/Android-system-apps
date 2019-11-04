/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui.bots;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isClickable;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;

import android.content.Context;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.view.View;

import androidx.recyclerview.R;

import org.hamcrest.Matcher;

/**
 * A test helper class that provides support for controlling the search UI
 * programmatically, and making assertions against the state of the UI.
 * <p>
 * Support for working directly with Roots and Directory view can be found in the respective bots.
 */
public class SearchBot extends Bots.BaseBot {

    // Dumb search layout changes substantially between Ryu and Angler.
    @SuppressWarnings("unchecked")
    private static final Matcher<View> SEARCH_WIDGET = allOf(
            withId(R.id.option_menu_search),
            anyOf(isClickable(), hasDescendant(isClickable())));

    // Note that input is visible when the clicky button is not
    // present. So to clearly qualify the two...we explicitly
    // require this input be not clickable.
    @SuppressWarnings("unchecked")
    private static final Matcher<View> SEARCH_INPUT = allOf(
            withId(R.id.search_src_text),
            isDisplayed());

    public SearchBot(UiDevice device, Context context, int timeout) {
        super(device, context, timeout);
    }

    public void clickIcon() throws UiObjectNotFoundException {
        UiObject searchView = findSearchView();
        searchView.click();

        UiObject fragmentSearchView = findFragmentSearchView();
        assertTrue(fragmentSearchView.exists());
    }

    public void clickSearchViewClearButton() throws UiObjectNotFoundException {
        UiObject clear = findSearchViewClearButton();
        clear.click();
    }

    public void clickFragmentSearchViewClearButton() throws UiObjectNotFoundException {
        UiObject clear = findFragmentSearchClearButton();
        clear.click();
    }

    public void setInputText(String query) throws UiObjectNotFoundException {
        onView(SEARCH_INPUT).perform(typeText(query));
    }

    public void assertIconVisible(boolean visible) {
        if (visible) {
            assertTrue(
                    "Search icon should be visible.",
                    Matchers.present(SEARCH_WIDGET));
        } else {
            assertFalse(
                    "Search icon should not be visible.",
                    Matchers.present(SEARCH_WIDGET));
        }
    }

    public void assertInputEquals(String query)
            throws UiObjectNotFoundException {
        UiObject textField = findSearchViewTextField();

        assertTrue(textField.exists());
        assertEquals(query, textField.getText());
    }

    public void assertInputFocused(boolean focused)
            throws UiObjectNotFoundException {
        UiObject textField = findSearchViewTextField();

        assertTrue(textField.exists());
        assertEquals(focused, textField.isFocused());
    }

    public void assertInputExists(boolean exists)
            throws UiObjectNotFoundException {
        assertEquals(exists, findSearchViewTextField().exists());
    }

    public void assertFragmentInputFocused(boolean focused)
            throws UiObjectNotFoundException {
        UiObject textField = findFragmentSearchViewTextField();

        assertTrue(textField.exists());
        assertEquals(focused, textField.isFocused());
    }

    public void assertFragmentInputExists(boolean exists)
            throws UiObjectNotFoundException {
        assertEquals(exists, findFragmentSearchViewTextField().exists());
    }

    private UiObject findSearchView() {
        return findObject(mTargetPackage + ":id/option_menu_search");
    }

    private UiObject findSearchViewTextField() {
        return findObject(mTargetPackage + ":id/option_menu_search",
                mTargetPackage + ":id/search_src_text");
    }

    private UiObject findSearchViewClearButton() {
        return findObject(mTargetPackage + ":id/option_menu_search",
                mTargetPackage + ":id/search_close_btn");
    }

    private UiObject findFragmentSearchView() {
        return findObject(mTargetPackage + ":id/search_view");
    }

    private UiObject findFragmentSearchViewTextField() {
        return findObject(mTargetPackage + ":id/search_view",
                mTargetPackage + ":id/search_src_text");
    }

    private UiObject findFragmentSearchClearButton() {
        return findObject(mTargetPackage + ":id/search_view",
                mTargetPackage + ":id/search_close_btn");
    }

    private UiObject findSearchViewIcon() {
        return mContext.getResources().getBoolean(R.bool.full_bar_search_view)
                ? findObject(mTargetPackage + ":id/option_menu_search")
                : findObject(mTargetPackage + ":id/option_menu_search",
                        "android:id/search_button");
    }
}
