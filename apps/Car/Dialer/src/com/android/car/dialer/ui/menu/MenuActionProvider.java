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

package com.android.car.dialer.ui.menu;

import android.content.Context;
import android.view.ActionProvider;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import com.android.car.dialer.R;

/**
 * Dialer's {@link ActionProvider} which provides a custom action view. Right now, it requires an
 * intent set in {@link com.android.car.dialer.ui.TelecomActivity#onCreateOptionsMenu(Menu)} and on
 * click will launch the intent.
 */
public class MenuActionProvider extends ActionProvider {
    private final Context mContext;

    public MenuActionProvider(Context context) {
        super(context);
        mContext = context;
    }

    /** @deprecated in parent, see {@link ActionProvider#onCreateActionView()} */
    @Deprecated
    @Override
    public View onCreateActionView() {
        return null;
    }

    @Override
    public View onCreateActionView(MenuItem forItem) {
        View actionView = LayoutInflater.from(mContext).inflate(R.layout.menu_action_view, null);
        actionView.setContentDescription(forItem.getTitle());
        ImageView icon = actionView.findViewById(R.id.menu_icon);
        icon.setImageDrawable(forItem.getIcon());
        if (forItem.getIconTintMode() != null) {
            icon.setImageTintMode(forItem.getIconTintMode());
        }
        if (forItem.getIconTintList() != null) {
            icon.setImageTintList(forItem.getIconTintList());
        }

        actionView.setOnClickListener(v -> {
            if (forItem.getIntent() != null) {
                mContext.startActivity(forItem.getIntent());
            }
        });
        return actionView;
    }
}
