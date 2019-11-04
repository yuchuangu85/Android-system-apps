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
package com.android.customization.picker;

import static androidx.core.view.ViewCompat.LAYOUT_DIRECTION_RTL;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.view.ViewCompat;
import androidx.viewpager.widget.PagerAdapter;

import com.android.customization.picker.BasePreviewAdapter.PreviewPage;
import com.android.customization.widget.PreviewPager;

import java.util.ArrayList;
import java.util.List;

/**
 * Base adapter for {@link PreviewPager}.
 * It can be used as-is by creating an extension of {@link PreviewPage} and adding pages via
 * {@link #addPage(PreviewPage)}
 * @param <T> the type of {@link PreviewPage} that this adapter will handle.
 */
public class BasePreviewAdapter<T extends PreviewPage> extends PagerAdapter {

    private final int mPreviewCardResId;
    private final Context mContext;
    private final LayoutInflater mInflater;
    protected final List<T> mPages = new ArrayList<>();

    protected BasePreviewAdapter(Context context, @LayoutRes int previewCardResId) {
        mContext = context;
        mPreviewCardResId = previewCardResId;
        mInflater = LayoutInflater.from(mContext);
    }

    protected void addPage(T p) {
        mPages.add(p);
    }

    @Override
    public int getCount() {
        return mPages.size();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == ((PreviewPage) object).card;
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        if (ViewCompat.getLayoutDirection(container) == LAYOUT_DIRECTION_RTL) {
            position = mPages.size() - 1 - position;
        }
        CardView card = (CardView) mInflater.inflate(mPreviewCardResId, container, false);
        T page = mPages.get(position);

        page.setCard(card);
        page.bindPreviewContent();
        if (card.getParent() != null) {
            container.removeView(card);
        }
        container.addView(card);
        return page;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position,
            @NonNull Object object) {
        View card = ((PreviewPage) object).card;
        ((PreviewPage) object).card = null;
        if (card.getParent() == container) {
            container.removeView(card);
        }
    }

    /**
     * Represents a possible page in a {@link PreviewPager}, based on a CardView container.
     * Override {@link #bindPreviewContent()} to bind the contents of the page when instantiated.
     */
    public static abstract class PreviewPage {
        protected final String title;
        protected CardView card;

        protected PreviewPage(String title) {
            this.title = title;
        }

        public void setCard(CardView card) {
            this.card = card;
        }

        public abstract void bindPreviewContent();
    }
}
