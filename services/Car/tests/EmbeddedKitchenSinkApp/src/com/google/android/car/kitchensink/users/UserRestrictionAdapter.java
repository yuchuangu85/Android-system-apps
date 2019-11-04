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
package com.google.android.car.kitchensink.users;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;

import com.google.android.car.kitchensink.R;

import java.util.List;

/**
 * Adapter to display a set of user restrictions
 */
public class UserRestrictionAdapter extends BaseAdapter {

    private final Context mContext;
    private final List<UserRestrictionListItem> mItems;

    public UserRestrictionAdapter(Context context, List<UserRestrictionListItem> items) {
        mContext = context;
        mItems = items;
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public Object getItem(int index) {
        return mItems.get(index);
    }

    @Override
    public long getItemId(int index) {
        return index;
    }

    @Override
    public View getView(int index, View convertView, ViewGroup parent) {
        return convertView == null
                ? createCheckBox((UserRestrictionListItem) getItem(index))
                : convertView;
    }

    private CheckBox createCheckBox(UserRestrictionListItem item) {
        Resources resources = mContext.getResources();
        CheckBox checkBox = new CheckBox(mContext);
        checkBox.setTextSize(resources.getDimensionPixelSize(R.dimen.users_checkbox_text_size));
        int padding = resources.getDimensionPixelSize(R.dimen.users_checkbox_padding);
        checkBox.setPadding(padding, padding, padding, padding);
        checkBox.setText(item.getKey());
        checkBox.setOnCheckedChangeListener((v, isChecked) -> item.setIsChecked(isChecked));
        checkBox.setChecked(item.getIsChecked());
        return checkBox;
    }
}
