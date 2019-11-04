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

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity that shows different device default themes. Auto complete themes come from
 * theme_names.txt file in the raw folder. User can also input a valid theme in the textbox even if
 * the theme is not available in the auto-complete.
 */
public class DefaultThemeSamples extends AbstractSampleActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Utils.onActivityCreateSetTheme(this);
        setContentView(R.layout.device_default_theme_samples);
        Button buttonApply = findViewById(R.id.button_apply);
        AutoCompleteTextView textView = (AutoCompleteTextView) findViewById(R.id.theme_name);
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                        listAllThemes());
        textView.setAdapter(adapter);
        buttonApply.setOnClickListener(v -> {
            EditText input = findViewById(R.id.theme_name);
            String themeName = input.getText().toString();
            int themeResId = this.getResources().getIdentifier(themeName,
                    "style", "android");
            if (themeResId == 0) {
                Toast.makeText(this, "No such theme found. ",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Applying theme: " + themeName,
                    Toast.LENGTH_SHORT).show();
            Utils.changeToTheme(this, themeName, themeResId);
        });
    }

    private String[] listAllThemes() {
        String data;
        List<String> list = new ArrayList<>();
        InputStream is = this.getResources().openRawResource(R.raw.theme_names);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        try {
            while ((data = reader.readLine()) != null) {
                list.add(data);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list.toArray(new String[0]);
    }
}
