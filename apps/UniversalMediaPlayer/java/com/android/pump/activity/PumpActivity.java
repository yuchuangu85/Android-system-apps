/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.pump.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.android.pump.R;
import com.android.pump.fragment.AlbumFragment;
import com.android.pump.fragment.ArtistFragment;
import com.android.pump.fragment.AudioFragment;
import com.android.pump.fragment.GenreFragment;
import com.android.pump.fragment.HomeFragment;
import com.android.pump.fragment.MovieFragment;
import com.android.pump.fragment.OtherFragment;
import com.android.pump.fragment.PermissionFragment;
import com.android.pump.fragment.PlaylistFragment;
import com.android.pump.fragment.SeriesFragment;
import com.android.pump.util.Globals;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomnavigation.BottomNavigationView.OnNavigationItemSelectedListener;
import com.google.android.material.tabs.TabLayout;

@UiThread
public class PumpActivity extends AppCompatActivity implements OnNavigationItemSelectedListener {
    // TODO The following should be a non-static member
    private static boolean sIsMissingPermissions = true;

    private static final Pages[] PAGES_LIST = {
        new Pages(R.id.menu_home, new Page[] {
            new Page(HomeFragment::newInstance, "Home")
        }),
        new Pages(R.id.menu_video, new Page[] {
            new Page(MovieFragment::newInstance, "Movies"),
            new Page(SeriesFragment::newInstance, "TV Shows"),
            new Page(OtherFragment::newInstance, "Personal"),
            new Page(HomeFragment::newInstance, "All videos")
        }),
        new Pages(R.id.menu_audio, new Page[] {
            new Page(AudioFragment::newInstance, "All audios"),
            new Page(PlaylistFragment::newInstance, "Playlists"),
            new Page(AlbumFragment::newInstance, "Albums"),
            new Page(GenreFragment::newInstance, "Genres"),
            new Page(ArtistFragment::newInstance, "Artists")
        }),
        new Pages(R.id.menu_favorite, new Page[] {
            new Page(HomeFragment::newInstance, "Videos"),
            new Page(HomeFragment::newInstance, "Audios")
        })
    };

    private boolean mInitialized = false;

    private ActivityPagerAdapter mActivityPagerAdapter;

    private DrawerLayout mDrawerLayout;
    private ViewPager mViewPager;
    private TabLayout mTabLayout;
    private BottomNavigationView mBottomNavigationView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // NOTE: If you are facing StrictMode violation by setContentView please disable instant run
        setContentView(R.layout.activity_pump);

        setSupportActionBar(findViewById(R.id.activity_pump_toolbar));

        mActivityPagerAdapter = new ActivityPagerAdapter(getSupportFragmentManager());

        mDrawerLayout = findViewById(R.id.activity_pump_drawer_layout);
        mViewPager = findViewById(R.id.activity_pump_view_pager);
        mTabLayout = findViewById(R.id.activity_pump_tab_layout);
        mBottomNavigationView = findViewById(R.id.activity_pump_bottom_navigation_view);

        mBottomNavigationView.setOnNavigationItemSelectedListener(this);
        mBottomNavigationView.setSelectedItemId(R.id.menu_home);
        mViewPager.setAdapter(mActivityPagerAdapter);
        mTabLayout.setupWithViewPager(mViewPager);
    }

    @Override
    public void onResume() {
        super.onResume();

        initialize();
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.activity_pump, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            mDrawerLayout.openDrawer(GravityCompat.START);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        for (Pages pages : PAGES_LIST) {
            if (pages.getId() == item.getItemId()) {
                selectPages(item.getTitle(), pages);
                return true;
            }
        }
        return false;
    }

    // TODO This should not be public
    public void initialize() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            if (!mInitialized) {
                mInitialized = true;

                sIsMissingPermissions = false;
                mActivityPagerAdapter.notifyDataSetChanged();

                Globals.getMediaDb(this).load();
            }
        }
    }

    private void selectPages(@NonNull CharSequence title, @NonNull Pages pages) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
        }

        Pages current = mActivityPagerAdapter.getPages();
        if (current != null) {
            current.setCurrent(mViewPager.getCurrentItem());
        }

        mActivityPagerAdapter.setPages(pages);
        int count = mActivityPagerAdapter.getCount();
        mTabLayout.setVisibility(count <= 1 ? View.GONE : View.VISIBLE);
        mTabLayout.setTabMode(count <= 4 ? TabLayout.MODE_FIXED : TabLayout.MODE_SCROLLABLE);
        mViewPager.setCurrentItem(pages.getCurrent());
    }

    private static class ActivityPagerAdapter extends FragmentPagerAdapter {
        private Pages mPages;

        ActivityPagerAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        void setPages(@NonNull Pages pages) {
            mPages = pages;
            notifyDataSetChanged();
        }

        @Nullable Pages getPages() {
            return mPages;
        }

        @Override
        public int getCount() {
            return mPages.getPages().length;
        }

        @Override
        public @NonNull Fragment getItem(int position) {
            return mPages.getPages()[position].createFragment();
        }

        @Override
        public long getItemId(int position) {
            return mPages.getPages()[position].getId();
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            return POSITION_NONE;
        }

        @Override
        public @NonNull CharSequence getPageTitle(int position) {
            return mPages.getPages()[position].getTitle();
        }
    }

    private static class Page {
        private static int sId = 0;

        private final int mId;
        private final PageCreator mPageCreator;
        private final String mTitle;

        Page(@NonNull PageCreator pageCreator, @NonNull String title) {
            mId = sId++;
            mPageCreator = pageCreator;
            mTitle = title;
        }

        int getId() {
            if (isMissingPermissions()) {
                return ~mId;
            }
            return mId;
        }

        @NonNull Fragment createFragment() {
            if (isMissingPermissions()) {
                return PermissionFragment.newInstance();
            }
            return mPageCreator.newInstance();
        }

        @NonNull String getTitle() {
            return mTitle;
        }

        private boolean isMissingPermissions() {
            return sIsMissingPermissions;
        }
    }

    private static class Pages {
        private final int mId;
        private final Page[] mPages;

        private int mCurrent;

        Pages(@IdRes int id, @NonNull Page[] pages) {
            mId = id;
            mPages = pages;
        }

        int getId() {
            return mId;
        }

        @NonNull Page[] getPages() {
            return mPages;
        }

        void setCurrent(int current) {
            mCurrent = current;
        }

        int getCurrent() {
            return mCurrent;
        }
    }

    @FunctionalInterface
    private interface PageCreator {
        @NonNull Fragment newInstance();
    }
}
