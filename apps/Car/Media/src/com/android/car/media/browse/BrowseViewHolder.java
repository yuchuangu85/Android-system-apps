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

package com.android.car.media.browse;

import android.content.Context;
import android.text.TextUtils;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.android.car.apps.common.imaging.ImageViewBinder;
import com.android.car.apps.common.util.ViewUtils;
import com.android.car.media.MediaAppConfig;
import com.android.car.media.common.MediaItemMetadata;

/**
 * Generic {@link RecyclerView.ViewHolder} to use for all views in the {@link BrowseAdapter}
 */
class BrowseViewHolder extends RecyclerView.ViewHolder {
    private final TextView mTitle;
    private final TextView mSubtitle;
    private final ImageView mAlbumArt;
    private final ViewGroup mContainer;
    private final ImageView mRightArrow;
    private final ImageView mTitleDownloadIcon;
    private final ImageView mTitleExplicitIcon;
    private final ImageView mSubTitleDownloadIcon;
    private final ImageView mSubTitleExplicitIcon;

    private final ImageViewBinder<MediaItemMetadata.ArtworkRef> mAlbumArtBinder;

    /**
     * Creates a {@link BrowseViewHolder} for the given view.
     */
    BrowseViewHolder(View itemView) {
        super(itemView);
        mTitle = itemView.findViewById(com.android.car.media.R.id.title);
        mSubtitle = itemView.findViewById(com.android.car.media.R.id.subtitle);
        mAlbumArt = itemView.findViewById(com.android.car.media.R.id.thumbnail);
        mContainer = itemView.findViewById(com.android.car.media.R.id.container);
        mRightArrow = itemView.findViewById(com.android.car.media.R.id.right_arrow);
        mTitleDownloadIcon = itemView.findViewById(
                com.android.car.media.R.id.download_icon_with_title);
        mTitleExplicitIcon = itemView.findViewById(
                com.android.car.media.R.id.explicit_icon_with_title);
        mSubTitleDownloadIcon = itemView.findViewById(
                com.android.car.media.R.id.download_icon_with_subtitle);
        mSubTitleExplicitIcon = itemView.findViewById(
                com.android.car.media.R.id.explicit_icon_with_subtitle);

        Size maxArtSize = MediaAppConfig.getMediaItemsBitmapMaxSize(itemView.getContext());
        mAlbumArtBinder = new ImageViewBinder<>(maxArtSize, mAlbumArt);
    }


    /**
     * Updates this {@link BrowseViewHolder} with the given data
     */
    public void bind(Context context, BrowseViewData data) {

        boolean hasMediaItem = data.mMediaItem != null;
        boolean showSubtitle = hasMediaItem && !TextUtils.isEmpty(data.mMediaItem.getSubtitle());

        if (mTitle != null) {
            mTitle.setText(data.mText != null ? data.mText :
                    hasMediaItem ? data.mMediaItem.getTitle() : null);
        }
        if (mSubtitle != null) {
            mSubtitle.setText(hasMediaItem ? data.mMediaItem.getSubtitle() : null);
            ViewUtils.setVisible(mSubtitle, showSubtitle);
        }

        mAlbumArtBinder.setImage(context, hasMediaItem ? data.mMediaItem.getArtworkKey() : null);

        if (mContainer != null && data.mOnClickListener != null) {
            mContainer.setOnClickListener(data.mOnClickListener);
        }
        ViewUtils.setVisible(mRightArrow, hasMediaItem && data.mMediaItem.isBrowsable());

        // Adjust the positioning of the explicit and downloaded icons. If there is a subtitle, then
        // the icons should show on the subtitle row, otherwise they should show on the title row.
        boolean downloaded = hasMediaItem && data.mMediaItem.isDownloaded();
        boolean explicit = hasMediaItem && data.mMediaItem.isExplicit();
        ViewUtils.setVisible(mTitleDownloadIcon, !showSubtitle && downloaded);
        ViewUtils.setVisible(mTitleExplicitIcon, !showSubtitle && explicit);
        ViewUtils.setVisible(mSubTitleDownloadIcon, showSubtitle && downloaded);
        ViewUtils.setVisible(mSubTitleExplicitIcon, showSubtitle && explicit);
    }
}
