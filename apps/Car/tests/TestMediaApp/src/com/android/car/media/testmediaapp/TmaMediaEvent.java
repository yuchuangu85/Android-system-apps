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

import static android.support.v4.media.session.PlaybackStateCompat.ERROR_CODE_ACTION_ABORTED;
import static android.support.v4.media.session.PlaybackStateCompat.ERROR_CODE_APP_ERROR;
import static android.support.v4.media.session.PlaybackStateCompat.ERROR_CODE_AUTHENTICATION_EXPIRED;
import static android.support.v4.media.session.PlaybackStateCompat.ERROR_CODE_CONCURRENT_STREAM_LIMIT;
import static android.support.v4.media.session.PlaybackStateCompat.ERROR_CODE_CONTENT_ALREADY_PLAYING;
import static android.support.v4.media.session.PlaybackStateCompat.ERROR_CODE_END_OF_QUEUE;
import static android.support.v4.media.session.PlaybackStateCompat.ERROR_CODE_NOT_AVAILABLE_IN_REGION;
import static android.support.v4.media.session.PlaybackStateCompat.ERROR_CODE_NOT_SUPPORTED;
import static android.support.v4.media.session.PlaybackStateCompat.ERROR_CODE_PARENTAL_CONTROL_RESTRICTED;
import static android.support.v4.media.session.PlaybackStateCompat.ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED;
import static android.support.v4.media.session.PlaybackStateCompat.ERROR_CODE_SKIP_LIMIT_REACHED;
import static android.support.v4.media.session.PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_BUFFERING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_CONNECTING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_ERROR;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_FAST_FORWARDING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_NONE;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_REWINDING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_SKIPPING_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED;

import android.support.v4.media.session.PlaybackStateCompat.State;
import android.util.Log;

/**
 * Contains the info needed to generate a new playback state.
 */
public class TmaMediaEvent {

    private static final String TAG = "TmaMediaEvent";

    public static final TmaMediaEvent INSTANT_PLAYBACK =
            new TmaMediaEvent(EventState.PLAYING, StateErrorCode.UNKNOWN_ERROR, null, null,
                    ResolutionIntent.NONE, 0, null);

    /** The name of each entry is the value used in the json file. */
    public enum EventState {
        NONE                    (STATE_NONE),
        STOPPED                 (STATE_STOPPED),
        PAUSED                  (STATE_PAUSED),
        PLAYING                 (STATE_PLAYING),
        FAST_FORWARDING         (STATE_FAST_FORWARDING),
        REWINDING               (STATE_REWINDING),
        BUFFERING               (STATE_BUFFERING),
        ERROR                   (STATE_ERROR),
        CONNECTING              (STATE_CONNECTING),
        SKIPPING_TO_PREVIOUS    (STATE_SKIPPING_TO_PREVIOUS),
        SKIPPING_TO_NEXT        (STATE_SKIPPING_TO_NEXT),
        SKIPPING_TO_QUEUE_ITEM  (STATE_SKIPPING_TO_QUEUE_ITEM);

        @State final int mValue;

        EventState(@State int value) {
            mValue = value;
        }
    }

    /** The name of each entry is the value used in the json file. */
    public enum StateErrorCode {
        UNKNOWN_ERROR                   (ERROR_CODE_UNKNOWN_ERROR),
        APP_ERROR                       (ERROR_CODE_APP_ERROR),
        NOT_SUPPORTED                   (ERROR_CODE_NOT_SUPPORTED),
        AUTHENTICATION_EXPIRED          (ERROR_CODE_AUTHENTICATION_EXPIRED),
        PREMIUM_ACCOUNT_REQUIRED        (ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED),
        CONCURRENT_STREAM_LIMIT         (ERROR_CODE_CONCURRENT_STREAM_LIMIT),
        PARENTAL_CONTROL_RESTRICTED     (ERROR_CODE_PARENTAL_CONTROL_RESTRICTED),
        NOT_AVAILABLE_IN_REGION         (ERROR_CODE_NOT_AVAILABLE_IN_REGION),
        CONTENT_ALREADY_PLAYING         (ERROR_CODE_CONTENT_ALREADY_PLAYING),
        SKIP_LIMIT_REACHED              (ERROR_CODE_SKIP_LIMIT_REACHED),
        ACTION_ABORTED                  (ERROR_CODE_ACTION_ABORTED),
        END_OF_QUEUE                    (ERROR_CODE_END_OF_QUEUE);

        @State final int mValue;

        StateErrorCode(@State int value) {
            mValue = value;
        }
    }

    /** The name of each entry is the value used in the json file. */
    public enum ResolutionIntent {
        NONE,
        PREFS
    }

    final EventState mState;
    final StateErrorCode mErrorCode;
    final String mErrorMessage;
    final String mActionLabel;
    final ResolutionIntent mResolutionIntent;
    /** How long to wait before sending the event to the app. */
    final int mPostDelayMs;
    private final String mExceptionClass;

    public TmaMediaEvent(EventState state, StateErrorCode errorCode, String errorMessage,
            String actionLabel, ResolutionIntent resolutionIntent, int postDelayMs,
            String exceptionClass) {
        mState = state;
        mErrorCode = errorCode;
        mErrorMessage = errorMessage;
        mActionLabel = actionLabel;
        mResolutionIntent = resolutionIntent;
        mPostDelayMs = postDelayMs;
        mExceptionClass = exceptionClass;
    }

    boolean premiumAccountRequired() {
        return mState == EventState.ERROR && mErrorCode == StateErrorCode.PREMIUM_ACCOUNT_REQUIRED;
    }

    void maybeThrow() {
        if (mExceptionClass != null) {
            RuntimeException exception = null;
            try {
                Class aClass = Class.forName(mExceptionClass);
                exception = (RuntimeException) aClass.newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                Log.e(TAG, "Class error for " + mExceptionClass + " : " + e);
            }

            if (exception != null) throw exception;
        }
    }

    @Override
    public String toString() {
        return "TmaMediaEvent{" +
                "mState=" + mState +
                ", mErrorCode=" + mErrorCode +
                ", mErrorMessage='" + mErrorMessage + '\'' +
                ", mActionLabel='" + mActionLabel + '\'' +
                ", mResolutionIntent=" + mResolutionIntent +
                ", mPostDelayMs=" + mPostDelayMs +
                ", mExceptionClass=" + mExceptionClass +
                '}';
    }
}
