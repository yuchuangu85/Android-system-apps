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
package com.android.customization.model.clock;

import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import com.android.customization.model.CustomizationManager;
import com.android.customization.model.CustomizationOption;
import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;

public class Clockface implements CustomizationOption<Clockface> {

    private final String mTitle;
    private final String mId;
    private final Asset mPreview;
    private final Asset mThumbnail;

    private Clockface(String title, String id, Asset preview, Asset thumbnail) {
        mTitle = title;
        mId = id;
        mPreview = preview;
        mThumbnail = thumbnail;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public void bindThumbnailTile(View view) {
        ImageView thumbView = view.findViewById(R.id.clock_option_thumbnail);
        mThumbnail.loadDrawableWithTransition(thumbView.getContext(), thumbView, 50, null,
                thumbView.getResources().getColor(android.R.color.transparent, null));
    }

    @Override
    public boolean isActive(CustomizationManager<Clockface> manager) {
        String currentClock = ((BaseClockManager) manager).getCurrentClock();
        // Empty clock Id is the default system clock
        return (TextUtils.isEmpty(currentClock) && TextUtils.isEmpty(mId))
                || (mId != null && mId.equals(currentClock));
    }

    @Override
    public int getLayoutResId() {
        return R.layout.clock_option;
    }

    public Asset getPreviewAsset() {
        return mPreview;
    }

    public String getId() {
        return mId;
    }

    public static class Builder {
        private String mTitle;
        private String mId;
        private Asset mPreview;
        private Asset mThumbnail;

        public Clockface build() {
            return new Clockface(mTitle, mId, mPreview, mThumbnail);
        }

        public Builder setTitle(String title) {
            mTitle = title;
            return this;
        }

        public Builder setId(String id) {
            mId = id;
            return this;
        }

        public Builder setPreview(Asset preview) {
            mPreview = preview;
            return this;
        }

        public Builder setThumbnail(Asset thumbnail) {
            mThumbnail = thumbnail;
            return this;
        }
    }
}
