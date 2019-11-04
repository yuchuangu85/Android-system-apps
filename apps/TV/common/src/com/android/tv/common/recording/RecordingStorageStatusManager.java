/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.common.recording;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Environment;
import android.os.Looper;
import android.os.StatFs;
import android.support.annotation.AnyThread;
import android.support.annotation.IntDef;
import android.support.annotation.WorkerThread;
import android.util.Log;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.common.feature.CommonFeatures;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** Signals DVR storage status change such as plugging/unplugging. */
public class RecordingStorageStatusManager {
    private static final String TAG = "RecordingStorageStatusManager";
    private static final boolean DEBUG = false;

    /** Minimum storage size to support DVR */
    public static final long MIN_STORAGE_SIZE_FOR_DVR_IN_BYTES = 50 * 1024 * 1024 * 1024L; // 50GB

    private static final long MIN_FREE_STORAGE_SIZE_FOR_DVR_IN_BYTES =
            10 * 1024 * 1024 * 1024L; // 10GB
    private static final String RECORDING_DATA_SUB_PATH = "/recording";

    /** Storage status constants. */
    @IntDef({
        STORAGE_STATUS_OK,
        STORAGE_STATUS_TOTAL_CAPACITY_TOO_SMALL,
        STORAGE_STATUS_FREE_SPACE_INSUFFICIENT,
        STORAGE_STATUS_MISSING
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface StorageStatus {}

    /** Current storage is OK to record a program. */
    public static final int STORAGE_STATUS_OK = 0;

    /** Current storage's total capacity is smaller than DVR requirement. */
    public static final int STORAGE_STATUS_TOTAL_CAPACITY_TOO_SMALL = 1;

    /** Current storage's free space is insufficient to record programs. */
    public static final int STORAGE_STATUS_FREE_SPACE_INSUFFICIENT = 2;

    /** Current storage is missing. */
    public static final int STORAGE_STATUS_MISSING = 3;

    private final Context mContext;
    private final Set<OnStorageMountChangedListener> mOnStorageMountChangedListeners =
            new CopyOnWriteArraySet<>();
    private MountedStorageStatus mMountedStorageStatus;
    private boolean mStorageValid;

    private class MountedStorageStatus {
        private final boolean mStorageMounted;
        private final File mStorageMountedDir;
        private final long mStorageMountedCapacity;

        private MountedStorageStatus(boolean mounted, File mountedDir, long capacity) {
            mStorageMounted = mounted;
            mStorageMountedDir = mountedDir;
            mStorageMountedCapacity = capacity;
        }

        private boolean isValidForDvr() {
            return mStorageMounted && mStorageMountedCapacity >= MIN_STORAGE_SIZE_FOR_DVR_IN_BYTES;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof MountedStorageStatus)) {
                return false;
            }
            MountedStorageStatus status = (MountedStorageStatus) other;
            return mStorageMounted == status.mStorageMounted
                    && Objects.equals(mStorageMountedDir, status.mStorageMountedDir)
                    && mStorageMountedCapacity == status.mStorageMountedCapacity;
        }
    }

    public interface OnStorageMountChangedListener {

        /**
         * Listener for DVR storage status change.
         *
         * @param storageMounted {@code true} when DVR possible storage is mounted, {@code false}
         *     otherwise.
         */
        void onStorageMountChanged(boolean storageMounted);
    }

    private final class StorageStatusBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            MountedStorageStatus result = getStorageStatusInternal();
            if (mMountedStorageStatus.equals(result)) {
                return;
            }
            mMountedStorageStatus = result;
            if (result.mStorageMounted) {
                cleanUpDbIfNeeded();
            }
            boolean valid = result.isValidForDvr();
            if (valid == mStorageValid) {
                return;
            }
            mStorageValid = valid;
            for (OnStorageMountChangedListener l : mOnStorageMountChangedListeners) {
                l.onStorageMountChanged(valid);
            }
        }
    }

    /**
     * Creates RecordingStorageStatusManager.
     *
     * @param context {@link Context}
     */
    public RecordingStorageStatusManager(final Context context) {
        mContext = context;
        mMountedStorageStatus = getStorageStatusInternal();
        mStorageValid = mMountedStorageStatus.isValidForDvr();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);
        filter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        filter.addDataScheme(ContentResolver.SCHEME_FILE);
        mContext.registerReceiver(new StorageStatusBroadcastReceiver(), filter);
    }

    /**
     * Adds the listener for receiving storage status change.
     *
     * @param listener
     */
    public void addListener(OnStorageMountChangedListener listener) {
        mOnStorageMountChangedListeners.add(listener);
    }

    /** Removes the current listener. */
    public void removeListener(OnStorageMountChangedListener listener) {
        mOnStorageMountChangedListeners.remove(listener);
    }

    /** Returns true if a storage is mounted. */
    public boolean isStorageMounted() {
        return mMountedStorageStatus.mStorageMounted;
    }

    /** Returns the path to DVR recording data directory. This can take for a while sometimes. */
    @WorkerThread
    public File getRecordingRootDataDirectory() {
        SoftPreconditions.checkState(Looper.myLooper() != Looper.getMainLooper());
        if (mMountedStorageStatus.mStorageMountedDir == null) {
            return null;
        }
        File root = mContext.getExternalFilesDir(null);
        String rootPath;
        try {
            rootPath = root != null ? root.getCanonicalPath() : null;
        } catch (IOException | SecurityException e) {
            return null;
        }
        return rootPath == null ? null : new File(rootPath + RECORDING_DATA_SUB_PATH);
    }

    /**
     * Returns the current storage status for DVR recordings.
     *
     * @return {@link StorageStatus}
     */
    @AnyThread
    public @StorageStatus int getDvrStorageStatus() {
        MountedStorageStatus status = mMountedStorageStatus;
        if (status.mStorageMountedDir == null) {
            return STORAGE_STATUS_MISSING;
        }
        if (CommonFeatures.FORCE_RECORDING_UNTIL_NO_SPACE.isEnabled(mContext)) {
            return STORAGE_STATUS_OK;
        }
        if (status.mStorageMountedCapacity < MIN_STORAGE_SIZE_FOR_DVR_IN_BYTES) {
            return STORAGE_STATUS_TOTAL_CAPACITY_TOO_SMALL;
        }
        try {
            StatFs statFs = new StatFs(status.mStorageMountedDir.toString());
            if (statFs.getAvailableBytes() < MIN_FREE_STORAGE_SIZE_FOR_DVR_IN_BYTES) {
                return STORAGE_STATUS_FREE_SPACE_INSUFFICIENT;
            }
        } catch (IllegalArgumentException e) {
            // In rare cases, storage status change was not notified yet.
            Log.w(TAG, "Error getting Dvr Storage Status.", e);
            SoftPreconditions.checkState(false);
            return STORAGE_STATUS_FREE_SPACE_INSUFFICIENT;
        }
        return STORAGE_STATUS_OK;
    }

    /**
     * Returns whether the storage has sufficient storage.
     *
     * @return {@code true} when there is sufficient storage, {@code false} otherwise
     */
    public boolean isStorageSufficient() {
        return getDvrStorageStatus() == STORAGE_STATUS_OK;
    }

    /** APPs that want to clean up DB for recordings should override this method to do the job. */
    protected void cleanUpDbIfNeeded() {}

    private MountedStorageStatus getStorageStatusInternal() {
        boolean storageMounted =
                Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        File storageMountedDir = storageMounted ? Environment.getExternalStorageDirectory() : null;
        storageMounted = storageMounted && storageMountedDir != null;
        long storageMountedCapacity = 0L;
        if (storageMounted) {
            try {
                StatFs statFs = new StatFs(storageMountedDir.toString());
                storageMountedCapacity = statFs.getTotalBytes();
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Storage mount status was changed.", e);
                storageMounted = false;
                storageMountedDir = null;
            }
        }
        return new MountedStorageStatus(storageMounted, storageMountedDir, storageMountedCapacity);
    }
}
