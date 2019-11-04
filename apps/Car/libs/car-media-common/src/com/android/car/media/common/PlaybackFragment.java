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

import static com.android.car.arch.common.LiveDataFunctions.mapNonNull;

import android.app.Application;
import android.car.Car;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProviders;

import com.android.car.apps.common.BitmapUtils;
import com.android.car.apps.common.CrossfadeImageView;
import com.android.car.apps.common.imaging.ImageBinder;
import com.android.car.apps.common.imaging.ImageBinder.PlaceholderType;
import com.android.car.apps.common.util.ViewUtils;
import com.android.car.media.common.playback.PlaybackViewModel;
import com.android.car.media.common.source.MediaSource;
import com.android.car.media.common.source.MediaSourceViewModel;
import com.android.internal.util.Preconditions;


/**
 * {@link Fragment} that can be used to display and control the currently playing media item. Its
 * requires the android.Manifest.permission.MEDIA_CONTENT_CONTROL permission be held by the hosting
 * application.
 */
public class PlaybackFragment extends Fragment {

    private MediaSourceViewModel mMediaSourceViewModel;
    private ImageBinder<MediaItemMetadata.ArtworkRef> mAlbumArtBinder;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        FragmentActivity activity = requireActivity();
        PlaybackViewModel playbackViewModel = PlaybackViewModel.get(activity.getApplication());
        mMediaSourceViewModel = MediaSourceViewModel.get(activity.getApplication());

        ViewModel innerViewModel = ViewModelProviders.of(activity).get(ViewModel.class);
        innerViewModel.init(mMediaSourceViewModel, playbackViewModel);

        View view = inflater.inflate(R.layout.playback_fragment, container, false);

        PlaybackControlsActionBar playbackControls = view.findViewById(R.id.playback_controls);
        playbackControls.setModel(playbackViewModel, getViewLifecycleOwner());
        playbackViewModel.getPlaybackStateWrapper().observe(getViewLifecycleOwner(),
                state -> ViewUtils.setVisible(playbackControls,
                        (state != null) && state.shouldDisplay()));

        TextView appName = view.findViewById(R.id.app_name);
        innerViewModel.getAppName().observe(getViewLifecycleOwner(), appName::setText);

        TextView title = view.findViewById(R.id.title);
        innerViewModel.getTitle().observe(getViewLifecycleOwner(), title::setText);

        TextView subtitle = view.findViewById(R.id.subtitle);
        innerViewModel.getSubtitle().observe(getViewLifecycleOwner(), subtitle::setText);

        CrossfadeImageView albumBackground = view.findViewById(R.id.album_background);
        albumBackground.setOnClickListener(
                // Let the Media center trampoline figure out what to open.
                v -> startActivity(new Intent(Car.CAR_INTENT_ACTION_MEDIA_TEMPLATE)));

        int max = activity.getResources().getInteger(R.integer.playback_widget_bitmap_max_size_px);
        Size maxArtSize = new Size(max, max);
        mAlbumArtBinder = new ImageBinder<>(PlaceholderType.FOREGROUND, maxArtSize,
                drawable -> {
                    Bitmap bitmap = (drawable != null)
                            ? BitmapUtils.fromDrawable(drawable, maxArtSize) : null;
                    albumBackground.setImageBitmap(bitmap, true);
                });

        playbackViewModel.getMetadata().observe(getViewLifecycleOwner(),
                item -> mAlbumArtBinder.setImage(PlaybackFragment.this.getContext(),
                        item != null ? item.getArtworkKey() : null));

        MediaAppSelectorWidget appSelector = view.findViewById(R.id.app_switch_container);
        Preconditions.checkNotNull(appSelector);
        appSelector.setFragmentActivity(getActivity());

        return view;
    }

    /**
     * ViewModel for the PlaybackFragment
     */
    public static class ViewModel extends AndroidViewModel {

        private LiveData<MediaSource> mMediaSource;
        private LiveData<CharSequence> mAppName;
        private LiveData<Bitmap> mAppIcon;
        private LiveData<CharSequence> mTitle;
        private LiveData<CharSequence> mSubtitle;

        private PlaybackViewModel mPlaybackViewModel;
        private MediaSourceViewModel mMediaSourceViewModel;

        public ViewModel(Application application) {
            super(application);
        }

        void init(MediaSourceViewModel mediaSourceViewModel, PlaybackViewModel playbackViewModel) {
            if (mMediaSourceViewModel == mediaSourceViewModel
                    && mPlaybackViewModel == playbackViewModel) {
                return;
            }
            mPlaybackViewModel = playbackViewModel;
            mMediaSourceViewModel = mediaSourceViewModel;
            mMediaSource = mMediaSourceViewModel.getPrimaryMediaSource();
            mAppName = mapNonNull(mMediaSource, MediaSource::getDisplayName);
            mAppIcon = mapNonNull(mMediaSource, MediaSource::getRoundPackageIcon);
            mTitle = mapNonNull(playbackViewModel.getMetadata(), MediaItemMetadata::getTitle);
            mSubtitle = mapNonNull(playbackViewModel.getMetadata(), MediaItemMetadata::getArtist);
        }

        LiveData<CharSequence> getAppName() {
            return mAppName;
        }

        LiveData<Bitmap> getAppIcon() {
            return mAppIcon;
        }

        LiveData<CharSequence> getTitle() {
            return mTitle;
        }

        LiveData<CharSequence> getSubtitle() {
            return mSubtitle;
        }
    }
}
