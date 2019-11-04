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

package com.android.car.apps.common.imaging;

import android.annotation.UiThread;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.util.LruCache;

import com.android.car.apps.common.BitmapUtils;
import com.android.car.apps.common.CommonFlags;
import com.android.car.apps.common.R;
import com.android.car.apps.common.UriUtils;
import com.android.car.apps.common.util.CarAppsIOUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;


/**
 * A singleton that fetches images and offers a simple memory cache. The requests and the replies
 * all happen on the UI thread.
 */
public class LocalImageFetcher {

    private static final String TAG = "LocalImageFetcher";
    private static final boolean L_WARN = Log.isLoggable(TAG, Log.WARN);

    private static final int KB = 1024;
    private static final int MB = KB * KB;

    /** Should not be reset to null once created. */
    private static LocalImageFetcher sInstance;

    /** Returns the singleton. */
    public static LocalImageFetcher getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new LocalImageFetcher(context);
        }
        return sInstance;
    }

    private final Executor mThreadPool;

    private final Map<ImageKey, HashSet<BiConsumer<ImageKey, Drawable>>> mConsumers =
            new HashMap<>(20);
    private final Map<ImageKey, ImageLoadingTask> mTasks = new HashMap<>(20);

    private final LruCache<ImageKey, Drawable> mMemoryCache;

    private final boolean mFlagRemoteImages;

    @UiThread
    private LocalImageFetcher(Context context) {
        Resources res = context.getResources();
        int poolSize = res.getInteger(R.integer.image_fetcher_thread_pool_size);
        mThreadPool = Executors.newFixedThreadPool(poolSize);

        int cacheSizeMB = res.getInteger(R.integer.bitmap_memory_cache_max_size_mb);
        int drawableDefaultWeightKB = res.getInteger(R.integer.drawable_default_weight_kb);
        mMemoryCache = new LruCache<ImageKey, Drawable>(cacheSizeMB * MB) {
            @Override
            protected int sizeOf(ImageKey key, Drawable drawable) {
                if (drawable instanceof BitmapDrawable) {
                    return ((BitmapDrawable) drawable).getBitmap().getAllocationByteCount();
                } else {
                    // For now
                    // TODO(b/139386940): consider a more accurate sizing / caching strategy.
                    return drawableDefaultWeightKB * KB;
                }
            }
        };

        mFlagRemoteImages = CommonFlags.getInstance(context).shouldFlagImproperImageRefs();
    }

    /** Fetches an image. The resulting drawable may be null. */
    @UiThread
    public void getImage(Context context, ImageKey key, BiConsumer<ImageKey, Drawable> consumer) {
        Drawable cached = mMemoryCache.get(key);
        if (cached != null) {
            consumer.accept(key, cached);
            return;
        }

        ImageLoadingTask task = mTasks.get(key);

        HashSet<BiConsumer<ImageKey, Drawable>> consumers = mConsumers.get(key);
        if (consumers == null) {
            consumers = new HashSet<>(3);
            if (task != null && L_WARN) {
                Log.w(TAG, "Expected no task here for " + key);
            }
            mConsumers.put(key, consumers);
        }
        consumers.add(consumer);

        if (task == null) {
            task = new ImageLoadingTask(context, key, mFlagRemoteImages);
            mTasks.put(key, task);
            task.executeOnExecutor(mThreadPool);
        }
    }

    /** Cancels a request made via {@link #getImage}. */
    @UiThread
    public void cancelRequest(ImageKey key, BiConsumer<ImageKey, Drawable> consumer) {
        HashSet<BiConsumer<ImageKey, Drawable>> consumers = mConsumers.get(key);
        if (consumers != null) {
            boolean removed = consumers.remove(consumer);
            if (consumers.isEmpty()) {
                // Nobody else wants this image, remove the set and cancel the task.
                mConsumers.remove(key);
                ImageLoadingTask task = mTasks.remove(key);
                if (task != null) {
                    task.cancel(true);
                }
                if (L_WARN) {
                    Log.w(TAG, "cancelRequest missing task for: " + key);
                }
            }

            if (!removed && L_WARN) {
                Log.w(TAG, "cancelRequest missing consumer for: " + key);
            }
        } else if (L_WARN) {
            Log.w(TAG, "cancelRequest has no consumers for: " + key);
        }
    }


    @UiThread
    private void fulfilRequests(ImageLoadingTask task, Drawable drawable) {
        ImageKey key = task.mImageKey;
        ImageLoadingTask pendingTask = mTasks.get(key);
        if (pendingTask == task) {
            if (drawable != null) {
                mMemoryCache.put(key, drawable);
            }

            HashSet<BiConsumer<ImageKey, Drawable>> consumers = mConsumers.remove(key);
            mTasks.remove(key);
            if (consumers != null) {
                for (BiConsumer<ImageKey, Drawable> consumer : consumers) {
                    consumer.accept(key, drawable);
                }
            }
        } else if (L_WARN) {
            // This case would possible if a running task was canceled, a new one was restarted
            // right away for the same key, and the canceled task still managed to call
            // fulfilRequests (despite the !isCancelled check).
            Log.w(TAG, "A new task already started for: " + task.mImageKey);
        }
    }


    private static class ImageLoadingTask extends AsyncTask<Void, Void, Drawable> {

        private final WeakReference<Context> mWeakContext;
        private final ImageKey mImageKey;
        private final boolean mFlagRemoteImages;


        @UiThread
        ImageLoadingTask(Context context, ImageKey request, boolean flagRemoteImages) {
            mWeakContext = new WeakReference<>(context.getApplicationContext());
            mImageKey = request;
            mFlagRemoteImages = flagRemoteImages;
        }

        /** Runs in the background. */
        private final ImageDecoder.OnHeaderDecodedListener mOnHeaderDecodedListener =
                new ImageDecoder.OnHeaderDecodedListener() {
            @Override
            public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                    ImageDecoder.Source source) {
                if (isCancelled()) throw new CancellationException();
                decoder.setAllocator(mAllocatorMode);
                int maxW = mImageKey.mMaxImageSize.getWidth();
                int maxH = mImageKey.mMaxImageSize.getHeight();
                int imgW = info.getSize().getWidth();
                int imgH = info.getSize().getHeight();
                if (imgW > maxW || imgH > maxH) {
                    float scale = Math.min(maxW / (float) imgW, maxH / (float) imgH);
                    decoder.setTargetSize(Math.round(scale * imgW), Math.round(scale * imgH));
                }
            }
        };

        private @ImageDecoder.Allocator int mAllocatorMode = ImageDecoder.ALLOCATOR_HARDWARE;

        @Override
        protected Drawable doInBackground(Void... voids) {
            try {
                if (isCancelled()) return null;
                Uri imageUri = mImageKey.mImageUri;

                Context context = mWeakContext.get();
                if (context == null) return null;

                if (UriUtils.isAndroidResourceUri(imageUri)) {
                    // ImageDecoder doesn't support all resources via the content provider...
                    return UriUtils.getDrawable(context, UriUtils.getIconResource(imageUri));
                } else if (UriUtils.isContentUri(imageUri)) {

                    ContentResolver resolver = context.getContentResolver();
                    ImageDecoder.Source src = ImageDecoder.createSource(resolver, imageUri);
                    return ImageDecoder.decodeDrawable(src, mOnHeaderDecodedListener);

                } else if (mFlagRemoteImages) {
                    mAllocatorMode = ImageDecoder.ALLOCATOR_SOFTWARE; // Needed for canvas drawing.
                    URL url = new URL(imageUri.toString());

                    try (InputStream is = new BufferedInputStream(url.openStream());
                         ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {

                        CarAppsIOUtils.copy(is, bytes);
                        ImageDecoder.Source src = ImageDecoder.createSource(bytes.toByteArray());
                        Bitmap decoded = ImageDecoder.decodeBitmap(src, mOnHeaderDecodedListener);
                        Bitmap tinted = BitmapUtils.createTintedBitmap(decoded,
                                context.getColor(R.color.improper_image_refs_tint_color));
                        return new BitmapDrawable(context.getResources(), tinted);
                    }
                }
            } catch (IOException ioe) {
                Log.e(TAG, "ImageLoadingTask#doInBackground: " + ioe);
            } catch (CancellationException e) {
                return null;
            }
            return null;
        }

        @UiThread
        @Override
        protected void onPostExecute(Drawable drawable) {
            if (!isCancelled()) {
                if (sInstance != null) {
                    sInstance.fulfilRequests(this, drawable);
                } else {
                    Log.e(TAG, "ImageLoadingTask#onPostExecute: LocalImageFetcher was reset !");
                }
            }
        }
    }
}
