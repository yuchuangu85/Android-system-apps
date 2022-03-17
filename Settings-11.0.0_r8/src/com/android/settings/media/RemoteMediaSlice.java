/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.settings.media;

import static android.app.slice.Slice.EXTRA_RANGE_VALUE;

import static com.android.settings.slices.CustomSliceRegistry.REMOTE_MEDIA_SLICE_URI;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.RoutingSessionInfo;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.graphics.drawable.IconCompat;
import androidx.slice.Slice;
import androidx.slice.builders.ListBuilder;
import androidx.slice.builders.ListBuilder.InputRangeBuilder;
import androidx.slice.builders.SliceAction;

import com.android.settings.R;
import com.android.settings.SubSettings;
import com.android.settings.Utils;
import com.android.settings.notification.SoundSettings;
import com.android.settings.slices.CustomSliceable;
import com.android.settings.slices.SliceBackgroundWorker;
import com.android.settings.slices.SliceBroadcastReceiver;
import com.android.settings.slices.SliceBuilderUtils;
import com.android.settingslib.media.MediaOutputSliceConstants;

import java.util.List;

/**
 * Display the Remote Media device information.
 */
public class RemoteMediaSlice implements CustomSliceable {

    private static final String TAG = "RemoteMediaSlice";
    private static final String MEDIA_ID = "media_id";

    private final Context mContext;

    private MediaDeviceUpdateWorker mWorker;

    public RemoteMediaSlice(Context context) {
        mContext = context;
    }

    @Override
    public void onNotifyChange(Intent intent) {
        final int newPosition = intent.getIntExtra(EXTRA_RANGE_VALUE, -1);
        final String id = intent.getStringExtra(MEDIA_ID);
        if (!TextUtils.isEmpty(id)) {
            getWorker().adjustSessionVolume(id, newPosition);
        }
    }

    @Override
    public Slice getSlice() {
        final ListBuilder listBuilder = new ListBuilder(mContext, getUri(), ListBuilder.INFINITY)
                .setAccentColor(COLOR_NOT_TINTED);
        if (getWorker() == null) {
            Log.e(TAG, "Unable to get the slice worker.");
            return listBuilder.build();
        }
        // Only displaying remote devices
        final List<RoutingSessionInfo> infos = getWorker().getActiveRemoteMediaDevice();
        if (infos.isEmpty()) {
            Log.d(TAG, "No active remote media device");
            return listBuilder.build();
        }
        final CharSequence castVolume = mContext.getText(R.string.remote_media_volume_option_title);
        final IconCompat icon = IconCompat.createWithResource(mContext,
                R.drawable.ic_volume_remote);
        // To create an empty icon to indent the row
        final IconCompat emptyIcon = createEmptyIcon();
        int requestCode = 0;
        for (RoutingSessionInfo info : infos) {
            final int maxVolume = info.getVolumeMax();
            if (maxVolume <= 0) {
                Log.d(TAG, "Unable to add Slice. " + info.getName() + ": max volume is "
                        + maxVolume);
                continue;
            }
            final CharSequence outputTitle = mContext.getString(R.string.media_output_label_title,
                    Utils.getApplicationLabel(mContext, info.getClientPackageName()));
            listBuilder.addInputRange(new InputRangeBuilder()
                    .setTitleItem(icon, ListBuilder.ICON_IMAGE)
                    .setTitle(castVolume)
                    .setInputAction(getSliderInputAction(requestCode++, info.getId()))
                    .setPrimaryAction(getSoundSettingAction(castVolume, icon, info.getId()))
                    .setMax(maxVolume)
                    .setValue(info.getVolume()));
            listBuilder.addRow(new ListBuilder.RowBuilder()
                    .setTitle(outputTitle)
                    .setSubtitle(info.getName())
                    .setTitleItem(emptyIcon, ListBuilder.ICON_IMAGE)
                    .setPrimaryAction(getMediaOutputSliceAction(info.getClientPackageName())));
        }
        return listBuilder.build();
    }

    private IconCompat createEmptyIcon() {
        final Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        return IconCompat.createWithBitmap(bitmap);
    }

    private PendingIntent getSliderInputAction(int requestCode, String id) {
        final Intent intent = new Intent(getUri().toString())
                .setData(getUri())
                .putExtra(MEDIA_ID, id)
                .setClass(mContext, SliceBroadcastReceiver.class);
        return PendingIntent.getBroadcast(mContext, requestCode, intent, 0);
    }

    private SliceAction getSoundSettingAction(CharSequence actionTitle, IconCompat icon,
            String id) {
        final Uri contentUri = new Uri.Builder().appendPath(id).build();
        final Intent intent = SliceBuilderUtils.buildSearchResultPageIntent(mContext,
                SoundSettings.class.getName(),
                id,
                mContext.getText(R.string.sound_settings).toString(), 0);
        intent.setClassName(mContext.getPackageName(), SubSettings.class.getName());
        intent.setData(contentUri);
        final PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
        final SliceAction primarySliceAction = SliceAction.createDeeplink(pendingIntent, icon,
                ListBuilder.ICON_IMAGE, actionTitle);
        return primarySliceAction;
    }

    private SliceAction getMediaOutputSliceAction(String packageName) {
        final Intent intent = new Intent()
                .setAction(MediaOutputSliceConstants.ACTION_MEDIA_OUTPUT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MediaOutputSliceConstants.EXTRA_PACKAGE_NAME, packageName);
        final IconCompat icon = IconCompat.createWithResource(mContext,
                R.drawable.ic_volume_remote);
        final PendingIntent primaryActionIntent = PendingIntent.getActivity(mContext,
                0 /* requestCode */, intent, 0 /* flags */);
        final SliceAction primarySliceAction = SliceAction.createDeeplink(
                primaryActionIntent, icon, ListBuilder.ICON_IMAGE,
                mContext.getString(R.string.media_output_label_title,
                        Utils.getApplicationLabel(mContext, packageName)));
        return primarySliceAction;
    }

    @Override
    public Uri getUri() {
        return REMOTE_MEDIA_SLICE_URI;
    }

    @Override
    public Intent getIntent() {
        return null;
    }

    @Override
    public Class getBackgroundWorkerClass() {
        return MediaDeviceUpdateWorker.class;
    }

    private MediaDeviceUpdateWorker getWorker() {
        if (mWorker == null) {
            mWorker = SliceBackgroundWorker.getInstance(getUri());
        }
        return mWorker;
    }
}
