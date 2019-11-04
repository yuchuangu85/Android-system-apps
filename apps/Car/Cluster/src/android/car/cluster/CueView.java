/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.car.cluster;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TextView;

import android.car.cluster.navigation.NavigationState.ImageReference;
import android.car.cluster.navigation.NavigationState.Cue;
import android.car.cluster.navigation.NavigationState.Cue.CueElement;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * View component that displays the Cue information on the instrument cluster display
 */
public class CueView extends TextView {
    private static final String TAG = "Cluster.CueView";

    private String mImageSpanText;
    private CompletableFuture<?> mFuture;
    private Handler mHandler = new Handler();
    private Cue mContent;

    public CueView(Context context) {
        super(context);
        init(context);
    }

    public CueView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CueView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        mImageSpanText = context.getString(R.string.span_image);
    }

    public void setCue(Cue cue, ImageResolver imageResolver) {
        if (cue == null) {
            setText(null);
            return;
        }

        if (mFuture != null && !Objects.equals(cue, mContent)) {
            mFuture.cancel(true);
        }

        List<ImageReference> imageReferences = cue.getElementsList().stream()
                .filter(element -> element.hasImage())
                .map(element -> element.getImage())
                .collect(Collectors.toList());
        mFuture = imageResolver
                .getBitmaps(imageReferences, 0, getLineHeight())
                .thenAccept(bitmaps -> {
                    mHandler.post(() -> update(cue, bitmaps));
                    mFuture = null;
                })
                .exceptionally(ex -> {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Unable to fetch images for cue: " + cue);
                    }
                    mHandler.post(() -> update(cue, Collections.emptyMap()));
                    return null;
                });
        mContent = cue;
    }

    private void update(Cue cue, Map<ImageReference, Bitmap> bitmaps) {
        SpannableStringBuilder builder = new SpannableStringBuilder();

        for (CueElement element : cue.getElementsList()) {
            if (element.hasImage()) {
                Bitmap bitmap = bitmaps.get(element.getImage());
                if (bitmap != null) {
                    String imageText = element.getText().isEmpty() ? mImageSpanText :
                            element.getText();
                    int start = builder.length();
                    int end = start + imageText.length();
                    builder.append(imageText);
                    BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
                    drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                    builder.setSpan(new ImageSpan(drawable), start, end, 0);
                }
            } else if (!element.getText().isEmpty()) {
                builder.append(element.getText());
            }
        }

        setText(builder);
    }
}
