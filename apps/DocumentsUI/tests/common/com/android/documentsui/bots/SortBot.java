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
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.withChild;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.android.documentsui.sorting.SortDimension.SORT_DIRECTION_ASCENDING;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.hamcrest.Matchers.allOf;

import android.content.Context;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.view.View;

import androidx.annotation.StringRes;

import com.android.documentsui.R;
import com.android.documentsui.sorting.SortDimension;
import com.android.documentsui.sorting.SortDimension.SortDirection;
import com.android.documentsui.sorting.SortListFragment;
import com.android.documentsui.sorting.SortModel;
import com.android.documentsui.sorting.SortModel.SortDimensionId;

import org.hamcrest.Matcher;

/**
 * A test helper class that provides support for controlling the UI Breadcrumb
 * programmatically, and making assertions against the state of the UI.
 * <p>
 * Support for working directly with Roots and Directory view can be found in the respective bots.
 */
public class SortBot extends Bots.BaseBot {

    private final SortModel mSortModel = SortModel.createModel();
    private final ColumnSortBot mColumnBot;
    private final UiBot mUiBot;

    public SortBot(UiDevice device, Context context, int timeout, UiBot uiBot) {
        super(device, context, timeout);
        mColumnBot = new ColumnSortBot();
        mUiBot = uiBot;
    }

    public void sortBy(@SortDimensionId int id, @SortDirection int direction) {
        assert(direction != SortDimension.SORT_DIRECTION_NONE);

        final @StringRes int labelId = mSortModel.getDimensionById(id).getLabelId();
        final String label = mContext.getString(labelId);
        final boolean result;
        if (isHeaderShow()) {
            result = mColumnBot.sortBy(label, direction);
        } else {
            result = sortByMenu(id, direction);
        }

        assertTrue("Sorting by id: " + id + " in direction: " + direction + " failed.",
                result);
    }

    public boolean isHeaderShow() {
        return Matchers.present(mColumnBot.MATCHER);
    }

    public void assertHeaderHide() {
        assertFalse(Matchers.present(mColumnBot.MATCHER));
    }

    public void assertHeaderShow() {
        // BEWARE THOSE WHO TREAD IN THIS DARK CORNER.
        // Note that for some reason this doesn't work:
        // assertTrue(Matchers.present(mColumnBot.MATCHER));
        // Dunno why, something to do with our implementation
        // or with espresso. It's sad that I'm leaving you
        // with this little gremlin, but we all have to
        // move on and get stuff done :)
        assertTrue(Matchers.present(mColumnBot.MATCHER));
    }

    private boolean sortByMenu(@SortDimensionId int id, @SortDirection int direction) {
        assert(direction != SortDimension.SORT_DIRECTION_NONE);

        clickMenuSort();
        mDevice.waitForIdle();

        SortDimension dimension = mSortModel.getDimensionById(id);
        @StringRes int labelRes = SortListFragment.getSheetLabelId(dimension, direction);
        onView(withText(mContext.getString(labelRes))).perform(click());
        mDevice.waitForIdle();

        clickMenuSort();
        mDevice.waitForIdle();

        UiObject2 verifyLabel = mDevice.findObject(By.text(mContext.getString(labelRes)));
        boolean isSelected = verifyLabel.isChecked();
        onView(withText(mContext.getString(labelRes))).perform(click());

        return isSelected;
    }

    private void clickMenuSort() {
        mUiBot.clickToolbarOverflowItem(mContext.getString(R.string.menu_sort));
    }

    private static class ColumnSortBot {

        private static final Matcher<View> MATCHER = withId(R.id.table_header);

        private boolean sortBy(String label, @SortDirection int direction) {
            final Matcher<View> cellMatcher = allOf(
                    withChild(withText(label)),
                    isDescendantOfA(MATCHER));
            onView(cellMatcher).perform(click());

            final @SortDirection int viewDirection = getDirection(cellMatcher);

            if (viewDirection != direction) {
                onView(cellMatcher).perform(click());
            }

            return getDirection(cellMatcher) == direction;
        }

        private @SortDirection int getDirection(Matcher<View> cellMatcher) {
            final boolean ascending =
                    Matchers.present(
                            allOf(
                                    withContentDescription(R.string.sort_direction_ascending),
                                    withParent(cellMatcher)));
            if (ascending) {
                return SORT_DIRECTION_ASCENDING;
            }

            final boolean descending =
                    Matchers.present(
                            allOf(
                                    withContentDescription(R.string.sort_direction_descending),
                                    withParent(cellMatcher)));

            return descending
                    ? SortDimension.SORT_DIRECTION_DESCENDING
                    : SortDimension.SORT_DIRECTION_NONE;
        }
    }
}
