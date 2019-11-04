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

package com.android.car.media.common;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.TextUtils;

import androidx.annotation.VisibleForTesting;

import com.android.car.apps.common.BitmapUtils;
import com.android.car.apps.common.CommonFlags;
import com.android.car.apps.common.imaging.ImageBinder;
import com.android.car.apps.common.imaging.ImageBinder.PlaceholderType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Abstract representation of a media item metadata.
 *
 * For media art, only local uris are supported so downloads can be attributed to the media app.
 * Bitmaps are not supported because they slow down the binder.
 */
public class MediaItemMetadata implements Parcelable {
    private static final String TAG = "MediaItemMetadata";

    static final int INVALID_MEDIA_ART_TINT_COLOR = Color.argb(200, 255, 0, 0);

    @NonNull
    private final MediaDescriptionCompat mMediaDescription;
    @Nullable
    private final Long mQueueId;
    private final boolean mIsBrowsable;
    private final boolean mIsPlayable;
    private final String mAlbumTitle;
    private final String mArtist;
    private final ArtworkRef mArtworkKey = new ArtworkRef();


    /** Creates an instance based on a {@link MediaMetadataCompat} */
    public MediaItemMetadata(@NonNull MediaMetadataCompat metadata) {
        this(metadata.getDescription(), null, false, false,
                metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM),
                metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST));
    }

    /** Creates an instance based on a {@link MediaSessionCompat.QueueItem} */
    public MediaItemMetadata(@NonNull MediaSessionCompat.QueueItem queueItem) {
        this(queueItem.getDescription(), queueItem.getQueueId(), false, true, null, null);
    }

    /** Creates an instance based on a {@link MediaBrowserCompat.MediaItem} */
    public MediaItemMetadata(@NonNull MediaBrowserCompat.MediaItem item) {
        this(item.getDescription(), null, item.isBrowsable(), item.isPlayable(), null, null);
    }

    /** Creates an instance based on a {@link Parcel} */
    public MediaItemMetadata(@NonNull Parcel in) {
        mMediaDescription = (MediaDescriptionCompat) in.readValue(
                MediaDescriptionCompat.class.getClassLoader());
        mQueueId = in.readByte() == 0x00 ? null : in.readLong();
        mIsBrowsable = in.readByte() != 0x00;
        mIsPlayable = in.readByte() != 0x00;
        mAlbumTitle = in.readString();
        mArtist = in.readString();
    }

    @VisibleForTesting
    public MediaItemMetadata(MediaDescriptionCompat description, Long queueId, boolean isBrowsable,
            boolean isPlayable, String albumTitle, String artist) {
        mMediaDescription = description;
        mQueueId = queueId;
        mIsPlayable = isPlayable;
        mIsBrowsable = isBrowsable;
        mAlbumTitle = albumTitle;
        mArtist = artist;
    }

    /**
     * The key to access the image to display for this media item.
     * Implemented as a class so that later we can support showing different images for the same
     * item (eg: cover and author) by adding other keys.
     */
    public class ArtworkRef implements ImageBinder.ImageRef {

        private @Nullable Bitmap getBitmapToFlag(Context context) {
            CommonFlags flags = CommonFlags.getInstance(context);
            return (flags.shouldFlagImproperImageRefs() && (mMediaDescription != null))
                    ? mMediaDescription.getIconBitmap() : null;
        }

        private int getPlaceholderHash() {
            // Only the title is reliably populated in metadata, since the album/artist fields
            // aren't set in the items retrieved from the browse service (only Title/Subtitle).
            return (getTitle() != null) ? getTitle().hashCode() : 0;
        }

        @Override
        public String toString() {
            return "title: " + getTitle() + " uri: " + getNonEmptyArtworkUri();
        }

        @Override
        public @Nullable Uri getImageURI() {
            return getNonEmptyArtworkUri();
        }

        @Override
        public boolean equals(Context context, Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArtworkRef other = (ArtworkRef) o;

            Bitmap myBitmap = getBitmapToFlag(context);
            Bitmap otherBitmap = other.getBitmapToFlag(context);
            if ((myBitmap != null) && (myBitmap.equals(otherBitmap))) return true;

            Uri myUri = getImageURI();
            Uri otherUri = other.getImageURI();
            if ((myUri != null) && (myUri.equals(otherUri))) return true;

            return getPlaceholderHash() == other.getPlaceholderHash();
        }

        @Override
        public @Nullable Drawable getImage(Context context) {
            Bitmap bitmap = getBitmapToFlag(context);
            if (bitmap != null) {
                Resources res = context.getResources();
                return new BitmapDrawable(res, BitmapUtils.createTintedBitmap(bitmap,
                        context.getColor(
                                com.android.car.apps.common.R.color.improper_image_refs_tint_color
                        )));
            }
            return null;
        }

        @Override
        public Drawable getPlaceholder(Context context, @NonNull PlaceholderType type) {
            if (type == PlaceholderType.NONE) return null;

            List<Drawable> placeholders = getPlaceHolders(type, context);
            int random = Math.floorMod(getPlaceholderHash(), placeholders.size());
            return placeholders.get(random);
        }
    }

    /** @return media item id */
    @Nullable
    public String getId() {
        return mMediaDescription.getMediaId();
    }

    /** @return media item title */
    @Nullable
    public CharSequence getTitle() {
        return mMediaDescription.getTitle();
    }

    /** @return media item subtitle */
    @Nullable
    public CharSequence getSubtitle() {
        return mMediaDescription.getSubtitle();
    }

    /** @return the album title for the media */
    @Nullable
    public String getAlbumTitle() {
        return mAlbumTitle;
    }

    /** @return the artist of the media */
    @Nullable
    public CharSequence getArtist() {
        return mArtist;
    }

    /**
     * @return the id of this item in the session queue, or NULL if this is not a session queue
     * item.
     */
    @Nullable
    public Long getQueueId() {
        return mQueueId;
    }


    public ArtworkRef getArtworkKey() {
        return mArtworkKey;
    }

    /**
     * @return a {@link Uri} referencing the artwork's bitmap.
     */
    private @Nullable Uri getNonEmptyArtworkUri() {
        Uri uri = mMediaDescription.getIconUri();
        return (uri != null && !TextUtils.isEmpty(uri.toString())) ? uri : null;
    }

    /**
     * @return boolean that indicate if media is explicit.
     */
    public boolean isExplicit() {
        Bundle extras = mMediaDescription.getExtras();
        return extras != null && extras.getLong(MediaConstants.EXTRA_IS_EXPLICIT)
                == MediaConstants.EXTRA_METADATA_ENABLED_VALUE;
    }

    /**
     * @return boolean that indicate if media is downloaded.
     */
    public boolean isDownloaded() {
        Bundle extras = mMediaDescription.getExtras();
        return extras != null && extras.getLong(MediaConstants.EXTRA_DOWNLOAD_STATUS)
                == MediaDescriptionCompat.STATUS_DOWNLOADED;
    }

    private static Map<PlaceholderType, List<Drawable>> sPlaceHolders = new HashMap<>();

    private static List<Drawable> getPlaceHolders(PlaceholderType type, Context context) {
        List<Drawable> placeHolders = sPlaceHolders.get(type);
        if (placeHolders == null) {
            TypedArray placeholderImages = context.getResources().obtainTypedArray(
                    type == PlaceholderType.FOREGROUND
                            ? R.array.placeholder_images : R.array.placeholder_colors);

            if (placeholderImages == null) {
                throw new NullPointerException("No placeholders for " + type);
            }

            placeHolders = new ArrayList<>(placeholderImages.length());
            for (int i = 0; i < placeholderImages.length(); i++) {
                placeHolders.add(type == PlaceholderType.FOREGROUND
                        ? placeholderImages.getDrawable(i)
                        : new ColorDrawable(placeholderImages.getColor(i, 0)));
            }
            placeholderImages.recycle();
            sPlaceHolders.put(type, placeHolders);

            if (sPlaceHolders.size() <= 0) {
                throw new Resources.NotFoundException("Placeholders should not be empty " + type);
            }
        }
        return placeHolders;
    }

    public boolean isBrowsable() {
        return mIsBrowsable;
    }

    /**
     * @return Content style hint for browsable items, if provided as an extra, or
     * 0 as default value if not provided.
     */
    public int getBrowsableContentStyleHint() {
        Bundle extras = mMediaDescription.getExtras();
        if (extras != null) {
            if (extras.containsKey(MediaConstants.CONTENT_STYLE_BROWSABLE_HINT)) {
                return extras.getInt(MediaConstants.CONTENT_STYLE_BROWSABLE_HINT, 0);
            } else if (extras.containsKey(MediaConstants.CONTENT_STYLE_BROWSABLE_HINT_PRERELEASE)) {
                return extras.getInt(MediaConstants.CONTENT_STYLE_BROWSABLE_HINT_PRERELEASE, 0);
            }
        }
        return 0;
    }

    public boolean isPlayable() {
        return mIsPlayable;
    }

    /**
     * @return Content style hint for playable items, if provided as an extra, or
     * 0 as default value if not provided.
     */
    public int getPlayableContentStyleHint() {
        Bundle extras = mMediaDescription.getExtras();
        if (extras != null) {

            if (extras.containsKey(MediaConstants.CONTENT_STYLE_PLAYABLE_HINT)) {
                return extras.getInt(MediaConstants.CONTENT_STYLE_PLAYABLE_HINT, 0);
            } else if (extras.containsKey(MediaConstants.CONTENT_STYLE_PLAYABLE_HINT_PRERELEASE)) {
                return extras.getInt(MediaConstants.CONTENT_STYLE_PLAYABLE_HINT_PRERELEASE, 0);
            }
        }
        return 0;
    }

    /**
     * @return Content style title group this item belongs to, or null if not provided
     */
    public String getTitleGrouping() {
        Bundle extras = mMediaDescription.getExtras();
        if (extras != null) {
            if (extras.containsKey(MediaConstants.CONTENT_STYLE_GROUP_TITLE_HINT)) {
                return extras.getString(MediaConstants.CONTENT_STYLE_GROUP_TITLE_HINT, null);
            } else if (extras.containsKey(
                    MediaConstants.CONTENT_STYLE_GROUP_TITLE_HINT_PRERELEASE)) {
                return extras.getString(MediaConstants.CONTENT_STYLE_GROUP_TITLE_HINT_PRERELEASE,
                        null);
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaItemMetadata that = (MediaItemMetadata) o;
        return mIsBrowsable == that.mIsBrowsable
                && mIsPlayable == that.mIsPlayable
                && Objects.equals(getId(), that.getId())
                && Objects.equals(getTitle(), that.getTitle())
                && Objects.equals(getSubtitle(), that.getSubtitle())
                && Objects.equals(getAlbumTitle(), that.getAlbumTitle())
                && Objects.equals(getArtist(), that.getArtist())
                && Objects.equals(getNonEmptyArtworkUri(), that.getNonEmptyArtworkUri())
                && Objects.equals(mQueueId, that.mQueueId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mMediaDescription.getMediaId(), mQueueId, mIsBrowsable, mIsPlayable);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(mMediaDescription);
        if (mQueueId == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeLong(mQueueId);
        }
        dest.writeByte((byte) (mIsBrowsable ? 0x01 : 0x00));
        dest.writeByte((byte) (mIsPlayable ? 0x01 : 0x00));
        dest.writeString(mAlbumTitle);
        dest.writeString(mArtist);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<MediaItemMetadata> CREATOR =
            new Parcelable.Creator<MediaItemMetadata>() {
                @Override
                public MediaItemMetadata createFromParcel(Parcel in) {
                    return new MediaItemMetadata(in);
                }

                @Override
                public MediaItemMetadata[] newArray(int size) {
                    return new MediaItemMetadata[size];
                }
            };

    @Override
    public String toString() {
        return "[Id: "
                + (mMediaDescription != null ? mMediaDescription.getMediaId() : "-")
                + ", Queue Id: "
                + (mQueueId != null ? mQueueId : "-")
                + ", title: "
                + mMediaDescription != null ? mMediaDescription.getTitle().toString() : "-"
                + ", subtitle: "
                + mMediaDescription != null ? mMediaDescription.getSubtitle().toString() : "-"
                + ", album title: "
                + mAlbumTitle != null ? mAlbumTitle : "-"
                + ", artist: "
                + mArtist != null ? mArtist : "-"
                + ", album art URI: "
                + (mMediaDescription != null ? mMediaDescription.getIconUri() : "-")
                + "]";
    }
}
