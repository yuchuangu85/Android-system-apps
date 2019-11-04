/*
 * Copyright (C) 2019 The Android Open Source Project.
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

package com.android.car.themeplayground;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Handles the menu for the theme playground app
 */
public abstract class AbstractSampleActivity extends Activity implements
        PopupMenu.OnMenuItemClickListener {

    private UiModeManager mUiModeManager;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        mUiModeManager = (UiModeManager) this.getSystemService(Context.UI_MODE_SERVICE);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.text_elements:
                return startSampleActivity(TextSamples.class);
            case R.id.panel_elements:
                return startSampleActivity(ColorSamples.class);
            case R.id.dialog_elements:
                return startSampleActivity(DialogSamples.class);
            case R.id.toggle_theme:
                return toggleDayNight();
            case R.id.widgets:
                return startSampleActivity(WidgetsSamples.class);
            case R.id.recycler_view:
                return startSampleActivity(RecyclerViewSamples.class);
            case R.id.default_themes:
                return startSampleActivity(DefaultThemeSamples.class);
            case R.id.multiple_intent:
                return startSampleActivity(MultipleIntentSamples.class);
            default:
                return true;
        }
    }

    /**
     * Will show the menu onclick of the menu button. This button will only appear when the theme is
     * set to NoActionBar.
     */
    private void showPopupMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.setOnMenuItemClickListener(this);
        popup.inflate(R.menu.menu_main);
        popup.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        return onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindMenuButton();
    }


    /**
     * When theme is set to NoActionBar then the menu also disappears blocking the user to navigate
     * between the activities. At that point this method will bring up the menu button that will
     * help user to navigate between activities.
     */
    private void bindMenuButton() {
        Button buttonMenu = findViewById(R.id.button_menu);
        if (Utils.sThemeName.equals("Theme.DeviceDefault.NoActionBar")) {
            buttonMenu.setVisibility(View.VISIBLE);
        } else {
            buttonMenu.setVisibility(View.GONE);
        }
        buttonMenu.setOnClickListener(v -> {
            showPopupMenu(v);
        });
    }

    /**
     * Launch the given sample activity
     */
    private boolean startSampleActivity(Class<?> cls) {
        Intent dialogIntent = new Intent(this, cls);
        startActivity(dialogIntent);
        return true;
    }

    private boolean toggleDayNight() {
        mUiModeManager.setNightMode(
                (mUiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_YES)
                        ? UiModeManager.MODE_NIGHT_NO : UiModeManager.MODE_NIGHT_YES);
        return true;
    }

    void setupBackgroundColorControls(int backgroundLayoutId) {
        Button colorSetButton = findViewById(R.id.set_background_color);
        ((EditText) findViewById(R.id.background_input_color)).setText(
                R.string.default_background_color,
                TextView.BufferType.EDITABLE);
        colorSetButton.setOnClickListener(v -> {
            String value = ((EditText) findViewById(R.id.background_input_color)).getText()
                    .toString();
            try {
                int color = Color.parseColor(value);
                View dialogLayout = findViewById(backgroundLayoutId);
                dialogLayout.setBackgroundColor(color);
            } catch (Exception e) {
                Toast.makeText(this, "not a color", Toast.LENGTH_LONG).show();
            }
        });
        Button colorResetButton = findViewById(R.id.reset);
        colorResetButton.setOnClickListener(v -> {
            try {
                View dialogLayout = findViewById(backgroundLayoutId);
                dialogLayout.setBackgroundColor(android.R.color.black);
            } catch (Exception e) {
                Toast.makeText(this, "Something went Wrong. Try again later.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}
