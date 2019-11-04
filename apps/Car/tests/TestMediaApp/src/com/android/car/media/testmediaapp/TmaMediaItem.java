/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.media.testmediaapp;

import androidx.annotation.Nullable;

import static android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_ID;

import static com.android.car.media.common.MediaConstants.CONTENT_STYLE_BROWSABLE_HINT;
import static com.android.car.media.common.MediaConstants.CONTENT_STYLE_GRID_ITEM_HINT_VALUE;
import static com.android.car.media.common.MediaConstants.CONTENT_STYLE_LIST_ITEM_HINT_VALUE;
import static com.android.car.media.common.MediaConstants.CONTENT_STYLE_PLAYABLE_HINT;

import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaBrowserCompat.MediaItem.Flags;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Our internal representation of media items. */
public class TmaMediaItem {

    private static final String CUSTOM_ACTION_PREFIX = "com.android.car.media.testmediaapp.";

    /** The name of each entry is the value used in the json file. */
    public enum ContentStyle {
        NONE (0),
        LIST (CONTENT_STYLE_LIST_ITEM_HINT_VALUE),
        GRID (CONTENT_STYLE_GRID_ITEM_HINT_VALUE);
        final int mBundleValue;
        ContentStyle(int value) {
            mBundleValue = value;
        }
    }

    public enum TmaCustomAction {
        HEART_PLUS_PLUS(CUSTOM_ACTION_PREFIX + "heart_plus_plus", R.string.heart_plus_plus,
                R.drawable.ic_heart_plus_plus),
        HEART_LESS_LESS(CUSTOM_ACTION_PREFIX + "heart_less_less", R.string.heart_less_less,
                R.drawable.ic_heart_less_less);

        final String mId;
        final int mNameId;
        final int mIcon;

        TmaCustomAction(String id, int name, int icon) {
            mId = id;
            mNameId = name;
            mIcon = icon;
        }

    }

    private final @MediaItem.Flags int mFlags;
    private final MediaMetadataCompat mMediaMetadata;
    private final ContentStyle mPlayableStyle;
    private final ContentStyle mBrowsableStyle;

    /** Read only list. */
    final List<TmaMediaItem> mChildren;
    /** Read only list. */
    private final List<TmaMediaItem> mPlayableChildren;
    /** Read only list. */
    final List<TmaCustomAction> mCustomActions;
    /** Read only list. Events triggered when starting the playback. */
    final List<TmaMediaEvent> mMediaEvents;
    /** References another json file where to get extra children from. */
    final String mInclude;

    private @Nullable TmaMediaItem mParent;
    int mHearts;


    public TmaMediaItem(@Flags int flags, ContentStyle playableStyle, ContentStyle browsableStyle,
            MediaMetadataCompat metadata, List<TmaCustomAction> customActions,
            List<TmaMediaEvent> mediaEvents,
            List<TmaMediaItem> children, String include) {
        mFlags = flags;
        mPlayableStyle = playableStyle;
        mBrowsableStyle = browsableStyle;
        mMediaMetadata = metadata;
        mCustomActions = Collections.unmodifiableList(customActions);
        mChildren = Collections.unmodifiableList(children);
        mMediaEvents = Collections.unmodifiableList(mediaEvents);
        mInclude = include;
        List<TmaMediaItem> playableChildren = new ArrayList<>(children.size());
        for (TmaMediaItem child: mChildren) {
            child.setParent(this);
            if ((child.mFlags & FLAG_PLAYABLE) != 0) {
                playableChildren.add(child);
            }
        }
        mPlayableChildren = Collections.unmodifiableList(playableChildren);
    }

    private void setParent(@Nullable TmaMediaItem parent) {
        mParent = parent;
    }

    @Nullable
    TmaMediaItem getParent() {
        return mParent;
    }

    TmaMediaItem getPlayableByIndex(long index) {
        return mPlayableChildren.get((int)index);
    }

    @Nullable
    TmaMediaItem getPrevious() {
        if (mParent == null) return null;
        List<TmaMediaItem> queueItems = mParent.mPlayableChildren;
        int myIndex = queueItems.indexOf(this);
        return (myIndex > 0) ? queueItems.get(myIndex - 1) : null;
    }

    @Nullable
    TmaMediaItem getNext() {
        if (mParent == null) return null;
        List<TmaMediaItem> queueItems = mParent.mPlayableChildren;
        int myIndex = queueItems.indexOf(this);
        return (myIndex < queueItems.size() - 1) ? queueItems.get(myIndex + 1) : null;
    }

    String getMediaId() {
        return mMediaMetadata.getString(METADATA_KEY_MEDIA_ID);
    }

    /** Returns -1 if the duration key is unspecified or <= 0. */
    long getDuration() {
        long result = mMediaMetadata.getLong(METADATA_KEY_DURATION);
        if (result <= 0) return -1;
        return result;
    }

    TmaMediaItem append(List<TmaMediaItem> children) {
        List<TmaMediaItem> allChildren = new ArrayList<>(mChildren.size() + children.size());
        allChildren.addAll(mChildren);
        allChildren.addAll(children);
        return new TmaMediaItem(mFlags, mPlayableStyle, mBrowsableStyle, mMediaMetadata,
                mCustomActions, mMediaEvents, allChildren, null);
    }

    void updateSessionMetadata(MediaSessionCompat session) {
        session.setMetadata(mMediaMetadata);
    }

    MediaItem toMediaItem() {
        return new MediaItem(buildDescription(), mFlags);
    }

    List<QueueItem> buildQueue() {
        int count = mPlayableChildren.size();
        List<QueueItem> queue = new ArrayList<>(count);
        for (int i = 0 ; i < count; i++) {
            TmaMediaItem child = mPlayableChildren.get(i);
            queue.add(new QueueItem(child.buildDescription(), i));
        }
        return queue;
    }

    /** Returns the id of the item in the queue. */
    long getQueueId() {
        if (mParent != null) {
            int index = mParent.mPlayableChildren.indexOf(this);
            if (index >= 0) return index;
        }
        return MediaSessionCompat.QueueItem.UNKNOWN_ID;
    }

    private MediaDescriptionCompat buildDescription() {

        // Use the default media description but add our extras.
        MediaDescriptionCompat metadataDescription = mMediaMetadata.getDescription();

        MediaDescriptionCompat.Builder bob = new MediaDescriptionCompat.Builder();
        bob.setMediaId(metadataDescription.getMediaId());
        bob.setTitle(metadataDescription.getTitle());
        bob.setSubtitle(metadataDescription.getSubtitle());
        bob.setDescription(metadataDescription.getDescription());
        bob.setIconBitmap(metadataDescription.getIconBitmap());
        bob.setIconUri(metadataDescription.getIconUri());
        bob.setMediaUri(metadataDescription.getMediaUri());

        Bundle extras = new Bundle();
        if (metadataDescription.getExtras() != null) {
            extras.putAll(metadataDescription.getExtras());
        }

        extras.putInt(CONTENT_STYLE_PLAYABLE_HINT, mPlayableStyle.mBundleValue);
        extras.putInt(CONTENT_STYLE_BROWSABLE_HINT, mBrowsableStyle.mBundleValue);

        bob.setExtras(extras);
        return bob.build();
    }
}
