/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.customization.widget;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.core.text.TextUtilsCompat;
import androidx.core.view.ViewCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;

import com.android.wallpaper.R;

import java.util.Locale;

/**
 * A Widget consisting of a ViewPager linked to a PageIndicator and previous/next arrows that can be
 * used to page over that ViewPager.
 * To use it, set a {@link PagerAdapter} using {@link #setAdapter(PagerAdapter)}, and optionally use
 * a {@link #setOnPageChangeListener(OnPageChangeListener)} to listen for page changes.
 */
public class PreviewPager extends LinearLayout {

    private static final int STYLE_PEEKING = 0;
    private static final int STYLE_ASPECT_RATIO = 1;

    private final ViewPager mViewPager;
    private final PageIndicator mPageIndicator;
    private final View mPreviousArrow;
    private final View mNextArrow;
    private final ViewPager.OnPageChangeListener mPageListener;
    private int mPageStyle;

    private PagerAdapter mAdapter;
    private ViewPager.OnPageChangeListener mExternalPageListener;
    private float mScreenAspectRatio;

    public PreviewPager(Context context) {
        this(context, null);
    }

    public PreviewPager(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PreviewPager(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.preview_pager, this);
        Resources res = context.getResources();
        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.PreviewPager, defStyleAttr, 0);

        mPageStyle = a.getInteger(R.styleable.PreviewPager_card_style, STYLE_PEEKING);

        a.recycle();

        mViewPager = findViewById(R.id.preview_viewpager);
        mViewPager.setPageMargin(res.getDimensionPixelOffset(R.dimen.preview_page_gap));
        mViewPager.setClipToPadding(false);
        if (mPageStyle == STYLE_PEEKING) {
            int screenWidth = mViewPager.getResources().getDisplayMetrics().widthPixels;
            int hMargin = res.getDimensionPixelOffset(R.dimen.preview_page_horizontal_margin);
            hMargin = Math.max(hMargin, screenWidth/8);
            mViewPager.setPadding(
                    hMargin,
                    res.getDimensionPixelOffset(R.dimen.preview_page_top_margin),
                    hMargin,
                    res.getDimensionPixelOffset(R.dimen.preview_page_bottom_margin));
        } else if (mPageStyle == STYLE_ASPECT_RATIO) {
            DisplayMetrics dm = res.getDisplayMetrics();
            mScreenAspectRatio = dm.heightPixels > dm.widthPixels
                    ? (float) dm.heightPixels / dm.widthPixels
                    : (float) dm.widthPixels / dm.heightPixels;
            mViewPager.setPadding(
                    0,
                    res.getDimensionPixelOffset(R.dimen.preview_page_top_margin),
                    0,
                    res.getDimensionPixelOffset(R.dimen.preview_page_bottom_margin));
        }
        mPageIndicator = findViewById(R.id.page_indicator);
        mPreviousArrow = findViewById(R.id.arrow_previous);
        mPreviousArrow.setOnClickListener(v -> {
            final int previousPos = mViewPager.getCurrentItem() - 1;
            mViewPager.setCurrentItem(previousPos, true);
        });
        mNextArrow = findViewById(R.id.arrow_next);
        mNextArrow.setOnClickListener(v -> {
            final int NextPos = mViewPager.getCurrentItem() + 1;
            mViewPager.setCurrentItem(NextPos, true);
        });
        mPageListener = createPageListener();
        mViewPager.addOnPageChangeListener(mPageListener);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mPageStyle == STYLE_ASPECT_RATIO) {
            int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
            int availableHeight = MeasureSpec.getSize(heightMeasureSpec);
            int indicatorHeight = mPageIndicator.getVisibility() == VISIBLE
                    ? ((View) mPageIndicator.getParent()).getLayoutParams().height
                    : 0;
            int pagerHeight = availableHeight - indicatorHeight;
            if (availableWidth > 0) {
                int absoluteCardWidth = (int) ((pagerHeight - mViewPager.getPaddingBottom()
                        - mViewPager.getPaddingTop())/ mScreenAspectRatio);
                int hPadding = (availableWidth / 2) - (absoluteCardWidth / 2);
                mViewPager.setPaddingRelative(
                        hPadding,
                        mViewPager.getPaddingTop(),
                        hPadding,
                        mViewPager.getPaddingBottom());
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void forceCardWidth(int widthPixels) {
        mViewPager.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int hPadding = (mViewPager.getWidth() - widthPixels) / 2;
                mViewPager.setPadding(hPadding, mViewPager.getPaddingTop(),
                        hPadding, mViewPager.getPaddingBottom());
                mViewPager.removeOnLayoutChangeListener(this);
            }
        });
        mViewPager.invalidate();
    }

    /**
     * Call this method to set the {@link PagerAdapter} backing the {@link ViewPager} in this
     * widget.
     */
    public void setAdapter(PagerAdapter adapter) {
        int initialPage = 0;
        if (mViewPager.getAdapter() != null) {
            initialPage = isRtl() ? mAdapter.getCount() - 1 - mViewPager.getCurrentItem()
                    : mViewPager.getCurrentItem();
        }
        mAdapter = adapter;
        mViewPager.setAdapter(adapter);
        mViewPager.setCurrentItem(isRtl() ? mAdapter.getCount() - 1 - initialPage : initialPage);
        mAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                initIndicator();
            }
        });
        initIndicator();
        updateIndicator(mViewPager.getCurrentItem());
    }

    private boolean isRtl() {
        if (ViewCompat.isLayoutDirectionResolved(mViewPager)) {
            return ViewCompat.getLayoutDirection(mViewPager) == ViewCompat.LAYOUT_DIRECTION_RTL;
        }
        return TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault())
                == ViewCompat.LAYOUT_DIRECTION_RTL;
    }

    /**
     * Set a {@link OnPageChangeListener} to be notified when the ViewPager's page state changes
     */
    public void setOnPageChangeListener(@Nullable ViewPager.OnPageChangeListener listener) {
        mExternalPageListener = listener;
    }

    private void initIndicator() {
        mPageIndicator.setNumPages(mAdapter.getCount());
        mPageIndicator.setLocation(mViewPager.getCurrentItem());
    }

    private ViewPager.OnPageChangeListener createPageListener() {
        return new ViewPager.OnPageChangeListener() {
             @Override
             public void onPageScrolled(
                     int position, float positionOffset, int positionOffsetPixels) {
                 // For certain sizes, positionOffset never makes it to 1, so round it as we don't
                 // need that much precision
                 float location = (float) Math.round((position + positionOffset) * 100) / 100;
                 mPageIndicator.setLocation(location);
                 if (mExternalPageListener != null) {
                     mExternalPageListener.onPageScrolled(position, positionOffset,
                             positionOffsetPixels);
                 }
             }

             @Override
             public void onPageSelected(int position) {
                 int adapterCount = mAdapter.getCount();
                 if (position < 0 || position >= adapterCount) {
                     return;
                 }

                 updateIndicator(position);
                 if (mExternalPageListener != null) {
                     mExternalPageListener.onPageSelected(position);
                 }
             }

             @Override
             public void onPageScrollStateChanged(int state) {
                 if (mExternalPageListener != null) {
                     mExternalPageListener.onPageScrollStateChanged(state);
                 }
             }
        };
    }

    private void updateIndicator(int position) {
        int adapterCount = mAdapter.getCount();
        if (adapterCount > 1) {
            mPreviousArrow.setVisibility(position != 0 ? View.VISIBLE : View.GONE);
            mNextArrow.setVisibility(position != (adapterCount - 1) ? View.VISIBLE : View.GONE);
        } else {
            mPageIndicator.setVisibility(View.GONE);
            mPreviousArrow.setVisibility(View.GONE);
            mNextArrow.setVisibility(View.GONE);
        }
    }
}
