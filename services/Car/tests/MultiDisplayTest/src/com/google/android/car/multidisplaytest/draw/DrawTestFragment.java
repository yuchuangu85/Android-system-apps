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

package com.google.android.car.multidisplaytest.draw;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.fragment.app.Fragment;

import com.google.android.car.multidisplaytest.R;

public class DrawTestFragment extends Fragment {
    private CanvasView mCanvas;
    private Button mClearButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        View view = inflater.inflate(R.layout.draw, container, false);

        mCanvas = view.findViewById(R.id.drawCanvas);
        mClearButton = view.findViewById(R.id.clearButton);
        setClearButtonListener();

        return view;
    }

    private void setClearButtonListener() {
        mClearButton.setOnClickListener(view -> mCanvas.clearCanvas());
    }
}
