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
package com.google.android.car.multidisplaytest;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.car.multidisplaytest.draw.DrawTestFragment;
import com.google.android.car.multidisplaytest.ime.InputTestFragment;
import com.google.android.car.multidisplaytest.present.PresentTestFragment;
import com.google.android.car.multidisplaytest.touch.TouchTestFragment;

import java.util.Arrays;
import java.util.List;

/**
 * Mostly copied from EmbeddedKitchenSinkApp with modifications on Fragments
 */
public class MDTest extends FragmentActivity {
    private static final String TAG = MDTest.class.getSimpleName();
    private FragmentManager mFragmentManager;
    private Button mMenuButton;
    private RecyclerView mMenu;
    private View mMenuContent;

    private interface ClickHandler {
        void onClick();
    }

    private abstract class MenuEntry implements ClickHandler {
        abstract String getText();
    }

    private final class FragmentMenuEntry<T extends Fragment> extends MenuEntry {
        private final class MenuFragment<T extends Fragment> {
            private final Class<T> mClazz;
            private T mMenuFragment = null;

            MenuFragment(Class<T> clazz) {
                mClazz = clazz;
            }

            T getFragment() {
                if (mMenuFragment == null) {
                    try {
                        mMenuFragment = mClazz.newInstance();
                    } catch (InstantiationException | IllegalAccessException e) {
                        Log.e(TAG, "unable to create fragment", e);
                    }
                }
                return mMenuFragment;
            }
        }

        private final String mText;
        private final MenuFragment<T> mFragment;

        FragmentMenuEntry(String text, Class<T> clazz) {
            mText = text;
            mFragment = new MenuFragment<>(clazz);
        }

        @Override
        String getText() {
            return mText;
        }

        @Override
        public void onClick() {
            Fragment fragment = mFragment.getFragment();
            if (fragment != null) {
                mFragmentManager.beginTransaction()
                    .replace(R.id.menu_content, fragment)
                    .commit();
                // MDTest.this.showFragment(fragment);
                toggleMenuVisibility();
            } else {
                Log.e(TAG, "cannot show fragment for " + getText());
            }
        }
    }

    // list of test fragments
    private final List<MenuEntry> mMenuEntries = Arrays.asList(
            new FragmentMenuEntry("Touch test", TouchTestFragment.class),
            new FragmentMenuEntry("IME test", InputTestFragment.class),
            new FragmentMenuEntry("Draw test", DrawTestFragment.class),
            new FragmentMenuEntry("Present test", PresentTestFragment.class)
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMenuContent = findViewById(R.id.menu_content);

        mMenu = findViewById(R.id.menu);
        mMenu.setAdapter(new MenuAdapter(this));
        mMenu.setLayoutManager(new GridLayoutManager(this, 3));

        mMenuButton = findViewById(R.id.menu_button);
        mMenuButton.setOnClickListener(view -> toggleMenuVisibility());
        Log.i(TAG, "Creating MDTest activity view");
        mFragmentManager = MDTest.this.getSupportFragmentManager();
        onNewIntent(getIntent());
    }

    private void toggleMenuVisibility() {
        boolean menuVisible = mMenu.getVisibility() == View.VISIBLE;
        mMenu.setVisibility(menuVisible ? View.GONE : View.VISIBLE);
        mMenuContent.setVisibility(menuVisible ? View.VISIBLE : View.GONE);
        mMenuButton.setText(menuVisible ? "Show Test Menu" : "Hide Test Menu");
    }

    private final class MenuAdapter extends RecyclerView.Adapter<ItemViewHolder> {
        private final LayoutInflater mLayoutInflator;

        MenuAdapter(Context context) {
            mLayoutInflator = LayoutInflater.from(context);
        }

        @Override
        public ItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = mLayoutInflator.inflate(R.layout.menu_item, parent, false);
            return new ItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ItemViewHolder holder, int position) {
            holder.mTitle.setText(mMenuEntries.get(position).getText());
            holder.mTitle.setOnClickListener(v -> mMenuEntries.get(position).onClick());
        }

        @Override
        public int getItemCount() {
            return mMenuEntries.size();
        }
    }

    private final class ItemViewHolder extends RecyclerView.ViewHolder {
        private TextView mTitle;

        ItemViewHolder(View itemView) {
            super(itemView);
            mTitle = itemView.findViewById(R.id.title);
        }
    }
}
