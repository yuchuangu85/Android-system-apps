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

package com.android.car.media.common.browse;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.media.MediaBrowserCompat;

import androidx.lifecycle.LiveData;

import com.android.car.media.common.MediaItemMetadata;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A LiveData that provides access the a MediaBrowser's children
 */

class BrowsedMediaItems extends LiveData<List<MediaItemMetadata>> {

    /**
     * Number of times we will retry obtaining the list of children of a certain node
     */
    private static final int CHILDREN_SUBSCRIPTION_RETRIES = 1;
    /**
     * Time between retries while trying to obtain the list of children of a certain node
     */
    private static final int CHILDREN_SUBSCRIPTION_RETRY_TIME_MS = 5000;

    /** Whether to send an error after the last timeout. The subscription stays active regardless.*/
    private static final boolean LAST_RETRY_TIMEOUT_SENDS_ERROR = false;

    private final MediaBrowserCompat mBrowser;
    private final String mParentId;
    private final Handler mHandler = new Handler();

    private ChildrenSubscription mSubscription;

    BrowsedMediaItems(@NonNull MediaBrowserCompat mediaBrowser, @Nullable String parentId) {
        mBrowser = mediaBrowser;
        mParentId = parentId;
    }

    @Override
    protected void onActive() {
        super.onActive();
        String rootNode = mBrowser.getRoot();
        String itemId = mParentId != null ? mParentId : rootNode;

        mSubscription = new ChildrenSubscription(itemId);
        mSubscription.start(CHILDREN_SUBSCRIPTION_RETRIES, CHILDREN_SUBSCRIPTION_RETRY_TIME_MS);
    }

    @Override
    protected void onInactive() {
        super.onInactive();
        mSubscription.stop();
        mSubscription = null;
        mHandler.removeCallbacksAndMessages(null);
    }

    /**
     * {@link MediaBrowserCompat.SubscriptionCallback} wrapper used to overcome the lack of a
     * reliable method to obtain the initial list of children of a given node.
     * <p>
     * When some 3rd party apps go through configuration changes (i.e., in the case of user-switch),
     * they leave subscriptions in an intermediate state where neither {@link
     * MediaBrowserCompat.SubscriptionCallback#onChildrenLoaded(String, List)} nor {@link
     * MediaBrowserCompat.SubscriptionCallback#onError(String)} are invoked.
     * <p>
     * This wrapper works around this problem by retrying the subscription a given number of times
     * if no data is received after a certain amount of time. This process is started by calling
     * {@link #start(int, int)}, passing the number of retries and delay between them as
     * parameters.
     * TODO: remove all this code if it's indeed not needed anymore (using retry=1 to be sure).
     */
    private class ChildrenSubscription extends MediaBrowserCompat.SubscriptionCallback {
        private final String mItemId;

        private boolean mIsDataLoaded;
        private int mRetries;
        private int mRetryDelay;

        ChildrenSubscription(String itemId) {
            mItemId = itemId;
        }

        private Runnable mRetryRunnable = new Runnable() {
            @Override
            public void run() {
                if (!mIsDataLoaded) {
                    if (mRetries > 0) {
                        mRetries--;
                        mBrowser.unsubscribe(mItemId);
                        mBrowser.subscribe(mItemId, ChildrenSubscription.this);
                        mHandler.postDelayed(this, mRetryDelay);
                    } else if (LAST_RETRY_TIMEOUT_SENDS_ERROR) {
                        mIsDataLoaded = true;
                        setValue(null);
                    }
                }
            }
        };

        /**
         * Starts trying to obtain the list of children
         *
         * @param retries    number of times to retry. If children are not obtained in this time
         *                   then the LiveData's value will be set to {@code null}
         * @param retryDelay time between retries in milliseconds
         */
        void start(int retries, int retryDelay) {
            if (mIsDataLoaded) {
                mBrowser.subscribe(mItemId, this);
            } else {
                mRetries = retries;
                mRetryDelay = retryDelay;
                mHandler.post(mRetryRunnable);
            }
        }

        /**
         * Stops retrying
         */
        void stop() {
            mHandler.removeCallbacks(mRetryRunnable);
            mBrowser.unsubscribe(mItemId);
        }

        @Override
        public void onChildrenLoaded(@NonNull String parentId,
                @NonNull List<MediaBrowserCompat.MediaItem> children) {
            mHandler.removeCallbacks(mRetryRunnable);
            mIsDataLoaded = true;
            setValue(children.stream()
                    .map(MediaItemMetadata::new)
                    .collect(Collectors.toList()));
        }

        @Override
        public void onChildrenLoaded(@NonNull String parentId,
                @NonNull List<MediaBrowserCompat.MediaItem> children,
                @NonNull Bundle options) {
            onChildrenLoaded(parentId, children);
        }

        @Override
        public void onError(@NonNull String parentId) {
            mHandler.removeCallbacks(mRetryRunnable);
            mIsDataLoaded = true;
            setValue(null);
        }

        @Override
        public void onError(@NonNull String parentId, @NonNull Bundle options) {
            onError(parentId);
        }
    }
}
