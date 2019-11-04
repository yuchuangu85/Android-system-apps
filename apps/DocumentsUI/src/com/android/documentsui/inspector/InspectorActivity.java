/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.documentsui.inspector;

import static androidx.core.util.Preconditions.checkArgument;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.loader.app.LoaderManager;

import com.android.documentsui.R;
import com.android.documentsui.base.Shared;
import com.android.documentsui.inspector.InspectorController.DataSupplier;

public class InspectorActivity extends AppCompatActivity {

    private InspectorController mController;
    private View mView;
    private Toolbar mToolbar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ToDo Create tool to check resource version before applyStyle for the theme
        // If version code is not match, we should reset overlay package to default,
        // in case Activity continueusly encounter resource not found exception
        getTheme().applyStyle(R.style.DocumentsDefaultTheme, false);

        setContentView(R.layout.inspector_activity);

        setContainer();

        mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        final DataSupplier loader = new RuntimeDataSupplier(this, LoaderManager.getInstance(this));

        mController = new InspectorController(this, loader, mView,
                getIntent().getStringExtra(Intent.EXTRA_TITLE),
                getIntent().getBooleanExtra(Shared.EXTRA_SHOW_DEBUG, false));
    }

    @Override
    protected void onStart() {
        super.onStart();
        Uri uri = getIntent().getData();
        checkArgument(uri.getScheme().equals("content"));
        mController.loadInfo(uri);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mController.reset();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setContainer() {
        mView = findViewById(R.id.inspector_root);
        mView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        mView.setOnApplyWindowInsetsListener((v, insets) -> {
            mView.setPadding(insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), 0);

            View container = findViewById(R.id.inspector_container);
            container.setPadding(0, 0, 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        getWindow().setNavigationBarDividerColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarContrastEnforced(true);
        } else {
            getWindow().setNavigationBarColor(getColor(R.color.nav_bar_translucent));
        }
    }
}