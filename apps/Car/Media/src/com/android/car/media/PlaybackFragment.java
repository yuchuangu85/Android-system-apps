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

package com.android.car.media;

import android.annotation.Nullable;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.apps.common.BackgroundImageView;
import com.android.car.apps.common.imaging.ImageBinder;
import com.android.car.apps.common.imaging.ImageBinder.PlaceholderType;
import com.android.car.apps.common.imaging.ImageViewBinder;
import com.android.car.apps.common.util.ViewUtils;
import com.android.car.media.common.MediaAppSelectorWidget;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.MetadataController;
import com.android.car.media.common.PlaybackControlsActionBar;
import com.android.car.media.common.playback.PlaybackViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;


/**
 * A {@link Fragment} that implements both the playback and the content forward browsing experience.
 * It observes a {@link PlaybackViewModel} and updates its information depending on the currently
 * playing media source through the {@link android.media.session.MediaSession} API.
 */
public class PlaybackFragment extends Fragment {
    private static final String TAG = "PlaybackFragment";

    private ImageBinder<MediaItemMetadata.ArtworkRef> mAlbumArtBinder;
    private BackgroundImageView mAlbumBackground;
    private View mBackgroundScrim;
    private View mControlBarScrim;
    private PlaybackControlsActionBar mPlaybackControls;
    private QueueItemsAdapter mQueueAdapter;
    private RecyclerView mQueue;
    private ConstraintLayout mMetadataContainer;
    private SeekBar mSeekBar;
    private View mQueueButton;
    private ViewGroup mNavIconContainer;
    private List<View> mViewsToHideForCustomActions;

    private DefaultItemAnimator mItemAnimator;

    private MetadataController mMetadataController;

    private PlaybackFragmentListener mListener;

    private PlaybackViewModel.PlaybackController mController;
    private Long mActiveQueueItemId;

    private boolean mHasQueue;
    private boolean mQueueIsVisible;
    private boolean mShowTimeForActiveQueueItem;
    private boolean mShowIconForActiveQueueItem;
    private boolean mShowThumbnailForQueueItem;

    private boolean mShowLinearProgressBar;

    private int mFadeDuration;

    /**
     * PlaybackFragment listener
     */
    public interface PlaybackFragmentListener {
        /**
         * Invoked when the user clicks on the collapse button
         */
        void onCollapse();
    }

    public class QueueViewHolder extends RecyclerView.ViewHolder {

        private final View mView;
        private final ViewGroup mThumbnailContainer;
        private final ImageView mThumbnail;
        private final View mSpacer;
        private final TextView mTitle;
        private final TextView mCurrentTime;
        private final TextView mMaxTime;
        private final TextView mTimeSeparator;
        private final ImageView mActiveIcon;

        private final ImageViewBinder<MediaItemMetadata.ArtworkRef> mThumbnailBinder;

        QueueViewHolder(View itemView) {
            super(itemView);
            mView = itemView;
            mThumbnailContainer = itemView.findViewById(R.id.thumbnail_container);
            mThumbnail = itemView.findViewById(R.id.thumbnail);
            mSpacer = itemView.findViewById(R.id.spacer);
            mTitle = itemView.findViewById(R.id.title);
            mCurrentTime = itemView.findViewById(R.id.current_time);
            mMaxTime = itemView.findViewById(R.id.max_time);
            mTimeSeparator = itemView.findViewById(R.id.separator);
            mActiveIcon = itemView.findViewById(R.id.now_playing_icon);

            Size maxArtSize = MediaAppConfig.getMediaItemsBitmapMaxSize(itemView.getContext());
            mThumbnailBinder = new ImageViewBinder<>(maxArtSize, mThumbnail);
        }

        boolean bind(MediaItemMetadata item) {
            mView.setOnClickListener(v -> onQueueItemClicked(item));

            ViewUtils.setVisible(mThumbnailContainer, mShowThumbnailForQueueItem);
            if (mShowThumbnailForQueueItem) {
                Context context = mView.getContext();
                mThumbnailBinder.setImage(context, item != null ? item.getArtworkKey() : null);
            }

            ViewUtils.setVisible(mSpacer, !mShowThumbnailForQueueItem);

            mTitle.setText(item.getTitle());

            boolean active = mActiveQueueItemId != null && Objects.equals(mActiveQueueItemId,
                    item.getQueueId());
            if (active) {
                mCurrentTime.setText(mQueueAdapter.getCurrentTime());
                mMaxTime.setText(mQueueAdapter.getMaxTime());
            }
            boolean shouldShowTime =
                    mShowTimeForActiveQueueItem && active && mQueueAdapter.getTimeVisible();
            ViewUtils.setVisible(mCurrentTime, shouldShowTime);
            ViewUtils.setVisible(mMaxTime, shouldShowTime);
            ViewUtils.setVisible(mTimeSeparator, shouldShowTime);

            boolean shouldShowIcon = mShowIconForActiveQueueItem && active;
            ViewUtils.setVisible(mActiveIcon, shouldShowIcon);

            return active;
        }
    }


    private class QueueItemsAdapter extends RecyclerView.Adapter<QueueViewHolder> {

        private List<MediaItemMetadata> mQueueItems;
        private String mCurrentTimeText;
        private String mMaxTimeText;
        private Integer mActiveItemPos;
        private boolean mTimeVisible;

        void setItems(@Nullable List<MediaItemMetadata> items) {
            mQueueItems = new ArrayList<>(items != null ? items : Collections.emptyList());
            notifyDataSetChanged();
        }

        void setCurrentTime(String currentTime) {
            mCurrentTimeText = currentTime;
            if (mActiveItemPos != null) {
                notifyItemChanged(mActiveItemPos.intValue());
            }
        }

        void setMaxTime(String maxTime) {
            mMaxTimeText = maxTime;
            if (mActiveItemPos != null) {
                notifyItemChanged(mActiveItemPos.intValue());
            }
        }

        void setTimeVisible(boolean visible) {
            mTimeVisible = visible;
            if (mActiveItemPos != null) {
                notifyItemChanged(mActiveItemPos.intValue());
            }
        }

        String getCurrentTime() {
            return mCurrentTimeText;
        }

        String getMaxTime() {
            return mMaxTimeText;
        }

        boolean getTimeVisible() {
            return mTimeVisible;
        }

        @Override
        public QueueViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            return new QueueViewHolder(inflater.inflate(R.layout.queue_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(QueueViewHolder holder, int position) {
            int size = mQueueItems.size();
            if (0 <= position && position < size) {
                boolean active = holder.bind(mQueueItems.get(position));
                if (active) {
                    mActiveItemPos = position;
                }
            } else {
                Log.e(TAG, "onBindViewHolder invalid position " + position + " of " + size);
            }
        }

        @Override
        public int getItemCount() {
            return mQueueItems.size();
        }

        void refresh() {
            // TODO: Perform a diff between current and new content and trigger the proper
            // RecyclerView updates.
            this.notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            return mQueueItems.get(position).getQueueId();
        }
    }

    private class QueueTopItemDecoration extends RecyclerView.ItemDecoration {
        int mHeight;
        int mDecorationPosition;

        QueueTopItemDecoration(int height, int decorationPosition) {
            mHeight = height;
            mDecorationPosition = decorationPosition;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            if (parent.getChildAdapterPosition(view) == mDecorationPosition) {
                outRect.top = mHeight;
            }
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playback, container, false);
        mAlbumBackground = view.findViewById(R.id.playback_background);
        mQueue = view.findViewById(R.id.queue_list);
        mMetadataContainer = view.findViewById(R.id.metadata_container);
        mSeekBar = view.findViewById(R.id.seek_bar);
        mQueueButton = view.findViewById(R.id.queue_button);
        mQueueButton.setOnClickListener(button -> toggleQueueVisibility());
        mNavIconContainer = view.findViewById(R.id.nav_icon_container);
        mNavIconContainer.setOnClickListener(nav -> onCollapse());
        mBackgroundScrim = view.findViewById(R.id.background_scrim);
        ViewUtils.setVisible(mBackgroundScrim, false);
        mControlBarScrim = view.findViewById(R.id.control_bar_scrim);
        ViewUtils.setVisible(mControlBarScrim, false);
        mControlBarScrim.setOnClickListener(scrim -> mPlaybackControls.close());
        mControlBarScrim.setClickable(false);

        Resources res = getResources();
        mShowTimeForActiveQueueItem = res.getBoolean(
                R.bool.show_time_for_now_playing_queue_list_item);
        mShowIconForActiveQueueItem = res.getBoolean(
                R.bool.show_icon_for_now_playing_queue_list_item);
        mShowThumbnailForQueueItem = getContext().getResources().getBoolean(
                R.bool.show_thumbnail_for_queue_list_item);
        mShowLinearProgressBar = getContext().getResources().getBoolean(
                R.bool.show_linear_progress_bar);

        if (mSeekBar != null) {
            if (mShowLinearProgressBar) {
                boolean useMediaSourceColor = res.getBoolean(
                        R.bool.use_media_source_color_for_progress_bar);
                int defaultColor = res.getColor(R.color.progress_bar_highlight, null);
                if (useMediaSourceColor) {
                    getPlaybackViewModel().getMediaSourceColors().observe(getViewLifecycleOwner(),
                            sourceColors -> {
                                int color = sourceColors != null ? sourceColors.getAccentColor(
                                        defaultColor)
                                        : defaultColor;
                                mSeekBar.setThumbTintList(ColorStateList.valueOf(color));
                                mSeekBar.setProgressTintList(ColorStateList.valueOf(color));
                            });
                } else {
                    mSeekBar.setThumbTintList(ColorStateList.valueOf(defaultColor));
                    mSeekBar.setProgressTintList(ColorStateList.valueOf(defaultColor));
                }
            } else {
                mSeekBar.setVisibility(View.GONE);
            }
        }

        MediaAppSelectorWidget appIcon = view.findViewById(R.id.app_icon_container);
        appIcon.setFragmentActivity(getActivity());

        getPlaybackViewModel().getPlaybackController().observe(getViewLifecycleOwner(),
                controller -> mController = controller);
        initPlaybackControls(view.findViewById(R.id.playback_controls));
        initMetadataController(view);
        initQueue();

        TypedArray hideViewIds =
                res.obtainTypedArray(R.array.playback_views_to_hide_when_showing_custom_actions);
        mViewsToHideForCustomActions = new ArrayList<>(hideViewIds.length());
        for (int i = 0; i < hideViewIds.length(); i++) {
            int viewId = hideViewIds.getResourceId(i, 0);
            if (viewId != 0) {
                View viewToHide = view.findViewById(viewId);
                if (viewToHide != null && (mShowLinearProgressBar || viewToHide != mSeekBar)) {
                    mViewsToHideForCustomActions.add(viewToHide);
                }
            }
        }
        hideViewIds.recycle();

        mAlbumArtBinder = new ImageBinder<>(
                PlaceholderType.BACKGROUND,
                MediaAppConfig.getMediaItemsBitmapMaxSize(getContext()),
                drawable -> mAlbumBackground.setBackgroundDrawable(drawable));

        getPlaybackViewModel().getMetadata().observe(getViewLifecycleOwner(),
                item -> mAlbumArtBinder.setImage(PlaybackFragment.this.getContext(),
                        item != null ? item.getArtworkKey() : null));

        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    private void initPlaybackControls(PlaybackControlsActionBar playbackControls) {
        mPlaybackControls = playbackControls;
        mPlaybackControls.setModel(getPlaybackViewModel(), getViewLifecycleOwner());
        mPlaybackControls.registerExpandCollapseCallback((expanding) -> {
            mControlBarScrim.setClickable(expanding);

            Resources res = getContext().getResources();
            int millis = expanding ? res.getInteger(R.integer.control_bar_expand_anim_duration) :
                    res.getInteger(R.integer.control_bar_collapse_anim_duration);

            if (expanding) {
                ViewUtils.showViewAnimated(mControlBarScrim, millis);
            } else {
                ViewUtils.hideViewAnimated(mControlBarScrim, millis);
            }

            if (!mQueueIsVisible) {
                for (View view : mViewsToHideForCustomActions) {
                    if (expanding) {
                        ViewUtils.hideViewAnimated(view, millis);
                    } else {
                        ViewUtils.showViewAnimated(view, millis);
                    }
                }
            }
        });
    }

    private void initQueue() {
        mFadeDuration = getResources().getInteger(
                R.integer.fragment_playback_queue_fade_duration_ms);

        int decorationHeight = getResources().getDimensionPixelSize(
                R.dimen.playback_queue_list_padding_top);
        // Put the decoration above the first item.
        int decorationPosition = 0;
        mQueue.addItemDecoration(new QueueTopItemDecoration(decorationHeight, decorationPosition));

        mQueue.setVerticalFadingEdgeEnabled(
                getResources().getBoolean(R.bool.queue_fading_edge_length_enabled));
        mQueueAdapter = new QueueItemsAdapter();

        getPlaybackViewModel().getPlaybackStateWrapper().observe(getViewLifecycleOwner(),
                state -> {
                    Long itemId = (state != null) ? state.getActiveQueueItemId() : null;
                    if (!Objects.equals(mActiveQueueItemId, itemId)) {
                        mActiveQueueItemId = itemId;
                        mQueueAdapter.refresh();
                    }
                });
        mQueue.setAdapter(mQueueAdapter);
        mQueue.setLayoutManager(new LinearLayoutManager(getContext()));

        // Disable item changed animation.
        mItemAnimator = new DefaultItemAnimator();
        mItemAnimator.setSupportsChangeAnimations(false);
        mQueue.setItemAnimator(mItemAnimator);

        getPlaybackViewModel().getQueue().observe(this, this::setQueue);

        getPlaybackViewModel().hasQueue().observe(getViewLifecycleOwner(), hasQueue -> {
            boolean enableQueue = (hasQueue != null) && hasQueue;
            setHasQueue(enableQueue);
            if (mQueueIsVisible && !enableQueue) {
                toggleQueueVisibility();
            }
        });
        getPlaybackViewModel().getProgress().observe(getViewLifecycleOwner(),
                playbackProgress ->
                {
                    mQueueAdapter.setCurrentTime(playbackProgress.getCurrentTimeText().toString());
                    mQueueAdapter.setMaxTime(playbackProgress.getMaxTimeText().toString());
                    mQueueAdapter.setTimeVisible(playbackProgress.hasTime());
                });
    }

    private void setQueue(List<MediaItemMetadata> queueItems) {
        mQueueAdapter.setItems(queueItems);
        mQueueAdapter.refresh();
    }

    private void initMetadataController(View view) {
        ImageView albumArt = view.findViewById(R.id.album_art);
        TextView title = view.findViewById(R.id.title);
        TextView artist = view.findViewById(R.id.artist);
        TextView albumTitle = view.findViewById(R.id.album_title);
        TextView outerSeparator = view.findViewById(R.id.outer_separator);
        TextView curTime = view.findViewById(R.id.current_time);
        TextView innerSeparator = view.findViewById(R.id.inner_separator);
        TextView maxTime = view.findViewById(R.id.max_time);
        SeekBar seekbar = mShowLinearProgressBar ? mSeekBar : null;

        Size maxArtSize = MediaAppConfig.getMediaItemsBitmapMaxSize(view.getContext());
        mMetadataController = new MetadataController(getViewLifecycleOwner(),
                getPlaybackViewModel(), title, artist, albumTitle, outerSeparator,
                curTime, innerSeparator, maxTime, seekbar, albumArt, maxArtSize);
    }

    /**
     * Hides or shows the playback queue.
     */
    private void toggleQueueVisibility() {
        mQueueIsVisible = !mQueueIsVisible;
        mQueueButton.setActivated(mQueueIsVisible);
        mQueueButton.setSelected(mQueueIsVisible);
        if (mQueueIsVisible) {
            ViewUtils.hideViewAnimated(mMetadataContainer, mFadeDuration);
            ViewUtils.showViewAnimated(mQueue, mFadeDuration);
            ViewUtils.showViewAnimated(mBackgroundScrim, mFadeDuration);
            if (mShowLinearProgressBar) {
                ViewUtils.hideViewAnimated(mSeekBar, mFadeDuration);
            }
        } else {
            ViewUtils.hideViewAnimated(mQueue, mFadeDuration);
            ViewUtils.showViewAnimated(mMetadataContainer, mFadeDuration);
            ViewUtils.hideViewAnimated(mBackgroundScrim, mFadeDuration);
            if (mShowLinearProgressBar) {
                ViewUtils.showViewAnimated(mSeekBar, mFadeDuration);
            }
        }
    }

    /** Sets whether the source has a queue. */
    private void setHasQueue(boolean hasQueue) {
        mHasQueue = hasQueue;
        updateQueueVisibility();
    }

    private void updateQueueVisibility() {
        mQueueButton.setVisibility(mHasQueue ? View.VISIBLE : View.GONE);
    }

    private void onQueueItemClicked(MediaItemMetadata item) {
        if (mController != null) {
            mController.skipToQueueItem(item.getQueueId());
        }
        boolean switchToPlayback = getResources().getBoolean(
                R.bool.switch_to_playback_view_when_playable_item_is_clicked);
        if (switchToPlayback) {
            toggleQueueVisibility();
        }
    }

    /**
     * Collapses the playback controls.
     */
    public void closeOverflowMenu() {
        mPlaybackControls.close();
    }

    private PlaybackViewModel getPlaybackViewModel() {
        return PlaybackViewModel.get(getActivity().getApplication());
    }

    /**
     * Sets a listener of this PlaybackFragment events. In order to avoid memory leaks, consumers
     * must reset this reference by setting the listener to null.
     */
    public void setListener(PlaybackFragmentListener listener) {
        mListener = listener;
    }

    private void onCollapse() {
        if (mListener != null) {
            mListener.onCollapse();
        }
    }
}
