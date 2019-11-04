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


package com.android.car.linkviewer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.car.widget.CarToolbar;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

/**
 * LinkViewerActivity responds to intents to display an HTTP or HTTPS URL by showing the link and
 * also a QR code.
 *
 */
public class LinkViewerActivity extends Activity {
    private static final String TAG = LinkViewerActivity.class.getSimpleName();

    private TextView mUrlText;
    private ImageView mUrlImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String url = getUrlFromIntent(getIntent());
        if (url == null) {
            finish();
            return;
        }

        setContentView(R.layout.link_viewer_activity);
        mUrlText = findViewById(R.id.url_text);
        mUrlImage = findViewById(R.id.url_image);

        CarToolbar toolbar = findViewById(R.id.car_toolbar);
        toolbar.setNavigationIconOnClickListener(v -> {
            finish();
        });

        showUrl(url);
    }

    private static String getUrlFromIntent(Intent intent) {
        return intent != null ? intent.getDataString() : null;
    }

    private void showUrl(String url) {
        mUrlText.setText(url);

        QRCode qrCode;
        try {
            qrCode = Encoder.encode(url, ErrorCorrectionLevel.H);
        } catch (WriterException e) {
            Log.e(TAG, "Error encoding URL: " + url, e);
            return;
        }

        BitmapDrawable drawable = new BitmapDrawable(getResources(), qrToBitmap(qrCode));
        drawable.setAntiAlias(false);
        drawable.setFilterBitmap(false);
        mUrlImage.setImageDrawable(drawable);
    }

    private Bitmap qrToBitmap(QRCode qrCode) {
        ByteMatrix matrix = qrCode.getMatrix();
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int[] colors = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                colors[y * width + x] = (matrix.get(x, y) != 0)
                    ? Color.WHITE
                    : Color.TRANSPARENT;
            }
        }

        return Bitmap.createBitmap(colors, 0, width, width, height, Bitmap.Config.ALPHA_8);
    }
}

