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

package com.android.pump.widget;

import android.content.Context;
import android.content.res.Resources;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.AppCompatSpinner;

import com.android.pump.R;

@UiThread
public class SortOrderSpinner extends AppCompatSpinner {
    public SortOrderSpinner(@NonNull Context context) {
        super(context);
        initialize();
    }

    public SortOrderSpinner(@NonNull Context context, int mode) {
        super(context, mode);
        initialize();
    }

    public SortOrderSpinner(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public SortOrderSpinner(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    public SortOrderSpinner(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr, int mode) {
        super(context, attrs, defStyleAttr, mode);
        initialize();
    }

    public SortOrderSpinner(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr, int mode, @Nullable Resources.Theme popupTheme) {
        super(context, attrs, defStyleAttr, mode, popupTheme);
        initialize();
    }

    private void initialize() {
        String[] options = {
            "name",
            "modified"
        };
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(
                getContext(), android.R.layout.simple_spinner_item, options) {
            @Override
            public @NonNull View getView(int position, @Nullable View convertView,
                    @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);

                CharSequence text = textView.getText();
                SpannableString content = new SpannableString("Sort by " + text);
                content.setSpan(new UnderlineSpan(), content.length() - text.length(),
                        content.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                textView.setText(content);

                return view;
            }
        };
        adapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);

        setAdapter(adapter);
    }
}
