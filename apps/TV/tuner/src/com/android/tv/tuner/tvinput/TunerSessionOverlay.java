/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.tuner.tvinput;

import android.content.Context;
import android.media.tv.TvInputService.Session;
import android.os.Handler;
import android.os.Message;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.android.tv.common.util.SystemPropertiesProxy;
import com.android.tv.tuner.R;
import com.android.tv.tuner.cc.CaptionLayout;
import com.android.tv.tuner.cc.CaptionTrackRenderer;
import com.android.tv.tuner.data.Cea708Data.CaptionEvent;
import com.android.tv.tuner.data.nano.Track.AtscCaptionTrack;
import com.android.tv.tuner.util.GlobalSettingsUtils;
import com.android.tv.tuner.util.StatusTextUtils;

/** Executes {@link Session} overlay changes on the main thread. */
/* package */ final class TunerSessionOverlay implements Handler.Callback {

    /** Displays the given {@link String} message object in the message view. */
    public static final int MSG_UI_SHOW_MESSAGE = 1;
    /** Hides the message view. Does not expect a message object. */
    public static final int MSG_UI_HIDE_MESSAGE = 2;
    /**
     * Displays a message in the audio status view to signal audio is not supported. Does not expect
     * a message object.
     */
    public static final int MSG_UI_SHOW_AUDIO_UNPLAYABLE = 3;
    /** Hides the audio status view. Does not expect a message object. */
    public static final int MSG_UI_HIDE_AUDIO_UNPLAYABLE = 4;
    /** Feeds the given {@link CaptionEvent} message object to the {@link CaptionTrackRenderer}. */
    public static final int MSG_UI_PROCESS_CAPTION_TRACK = 5;
    /**
     * Invokes {@link CaptionTrackRenderer#start(AtscCaptionTrack)} passing the given {@link
     * AtscCaptionTrack} message object as argument.
     */
    public static final int MSG_UI_START_CAPTION_TRACK = 6;
    /** Invokes {@link CaptionTrackRenderer#stop()}. Does not expect a message object. */
    public static final int MSG_UI_STOP_CAPTION_TRACK = 7;
    /** Invokes {@link CaptionTrackRenderer#reset()}. Does not expect a message object. */
    public static final int MSG_UI_RESET_CAPTION_TRACK = 8;
    /** Invokes {@link CaptionTrackRenderer#clear()}. Does not expect a message object. */
    public static final int MSG_UI_CLEAR_CAPTION_RENDERER = 9;
    /** Displays the given {@link CharSequence} message object in the status view. */
    public static final int MSG_UI_SET_STATUS_TEXT = 10;
    /** Displays a toast signalling that a re-scan is required. Does not expect a message object. */
    public static final int MSG_UI_TOAST_RESCAN_NEEDED = 11;

    private static final String USBTUNER_SHOW_DEBUG = "persist.tv.tuner.show_debug";

    private final Context mContext;
    private final Handler mHandler;
    private final View mOverlayView;
    private final TextView mMessageView;
    private final TextView mStatusView;
    private final TextView mAudioStatusView;
    private final ViewGroup mMessageLayout;
    private final CaptionTrackRenderer mCaptionTrackRenderer;

    /**
     * Creates and inflates a {@link Session} overlay from the given context.
     *
     * @param context The {@link Context} of the {@link Session}.
     */
    public TunerSessionOverlay(Context context) {
        mContext = context;
        mHandler = new Handler(this);
        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        boolean showDebug = SystemPropertiesProxy.getBoolean(USBTUNER_SHOW_DEBUG, false);
        mOverlayView = inflater.inflate(R.layout.ut_overlay_view, null);
        mMessageLayout = mOverlayView.findViewById(R.id.message_layout);
        mMessageLayout.setVisibility(View.INVISIBLE);
        mMessageView = mOverlayView.findViewById(R.id.message);
        mStatusView = mOverlayView.findViewById(R.id.tuner_status);
        mStatusView.setVisibility(showDebug ? View.VISIBLE : View.INVISIBLE);
        mAudioStatusView = mOverlayView.findViewById(R.id.audio_status);
        mAudioStatusView.setVisibility(View.INVISIBLE);
        CaptionLayout captionLayout = mOverlayView.findViewById(R.id.caption);
        mCaptionTrackRenderer = new CaptionTrackRenderer(captionLayout);
    }

    /** Clears any pending messages in the message queue. */
    public void release() {
        mHandler.removeCallbacksAndMessages(null);
    }

    /** Returns a {@link View} representation of the overlay. */
    public View getOverlayView() {
        return mOverlayView;
    }

    /**
     * Posts a message to be handled on the main thread. Only messages that do not expect a message
     * object may be posted through this method.
     *
     * @param message One of the {@code MSG_UI_*} constants.
     */
    public void sendUiMessage(int message) {
        mHandler.sendEmptyMessage(message);
    }

    /**
     * Posts a message to be handled on the main thread.
     *
     * @param message One of the {@code MSG_UI_*} constants.
     * @param object The object of the message. The required message object type depends on the
     *     message being posted.
     */
    public void sendUiMessage(int message, Object object) {
        mHandler.obtainMessage(message, object).sendToTarget();
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_UI_SHOW_MESSAGE:
                mMessageView.setText((String) msg.obj);
                mMessageLayout.setVisibility(View.VISIBLE);
                return true;
            case MSG_UI_HIDE_MESSAGE:
                mMessageLayout.setVisibility(View.INVISIBLE);
                return true;
            case MSG_UI_SHOW_AUDIO_UNPLAYABLE:
                // Showing message of enabling surround sound only when global surround sound
                // setting is "never".
                final int value = GlobalSettingsUtils.getEncodedSurroundOutputSettings(mContext);
                if (value == GlobalSettingsUtils.ENCODED_SURROUND_OUTPUT_NEVER) {
                    mAudioStatusView.setText(
                            Html.fromHtml(
                                    StatusTextUtils.getAudioWarningInHTML(
                                            mContext.getString(
                                                    R.string.ut_surround_sound_disabled))));
                } else {
                    mAudioStatusView.setText(
                            Html.fromHtml(
                                    StatusTextUtils.getAudioWarningInHTML(
                                            mContext.getString(
                                                    R.string.audio_passthrough_not_supported))));
                }
                mAudioStatusView.setVisibility(View.VISIBLE);
                return true;
            case MSG_UI_HIDE_AUDIO_UNPLAYABLE:
                mAudioStatusView.setVisibility(View.INVISIBLE);
                return true;
            case MSG_UI_PROCESS_CAPTION_TRACK:
                mCaptionTrackRenderer.processCaptionEvent((CaptionEvent) msg.obj);
                return true;
            case MSG_UI_START_CAPTION_TRACK:
                mCaptionTrackRenderer.start((AtscCaptionTrack) msg.obj);
                return true;
            case MSG_UI_STOP_CAPTION_TRACK:
                mCaptionTrackRenderer.stop();
                return true;
            case MSG_UI_RESET_CAPTION_TRACK:
                mCaptionTrackRenderer.reset();
                return true;
            case MSG_UI_CLEAR_CAPTION_RENDERER:
                mCaptionTrackRenderer.clear();
                return true;
            case MSG_UI_SET_STATUS_TEXT:
                mStatusView.setText((CharSequence) msg.obj);
                return true;
            case MSG_UI_TOAST_RESCAN_NEEDED:
                Toast.makeText(mContext, R.string.ut_rescan_needed, Toast.LENGTH_LONG).show();
                return true;
            default:
                return false;
        }
    }
}
