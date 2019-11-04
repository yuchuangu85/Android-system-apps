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
package android.car.cluster;

import static com.android.car.arch.common.LiveDataFunctions.mapNonNull;

import android.app.Application;
import android.graphics.Bitmap;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.android.car.media.common.source.MediaSource;
import com.android.car.media.common.source.MediaSourceViewModel;

/**
 * View model for {@link MusicFragment}
 */
public final class MusicFragmentViewModel extends AndroidViewModel {

    private LiveData<MediaSource> mMediaSource;
    private LiveData<CharSequence> mAppName;
    private LiveData<Bitmap> mAppIcon;

    private MediaSourceViewModel mMediaSourceViewModel;

    public MusicFragmentViewModel(Application application) {
        super(application);
    }

    void init(MediaSourceViewModel mediaSourceViewModel) {
        if (mMediaSourceViewModel == mediaSourceViewModel) {
            return;
        }
        mMediaSourceViewModel = mediaSourceViewModel;
        mMediaSource = mMediaSourceViewModel.getPrimaryMediaSource();
        mAppName = mapNonNull(mMediaSource, MediaSource::getDisplayName);
        mAppIcon = mapNonNull(mMediaSource, MediaSource::getRoundPackageIcon);
    }

    LiveData<CharSequence> getAppName() {
        return mAppName;
    }

    LiveData<Bitmap> getAppIcon() {
        return mAppIcon;
    }
}
