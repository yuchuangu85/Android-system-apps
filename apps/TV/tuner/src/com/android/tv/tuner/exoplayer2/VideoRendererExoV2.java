/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tv.tuner.exoplayer2;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import com.android.tv.tuner.features.TunerFeatures;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

/**
 * Subclasses {@link MediaCodecVideoRenderer} to customize minor behaviors.
 *
 * <p>This class changes two behaviors from {@link MediaCodecVideoRenderer}:
 *
 * <ul>
 *   <li>Prefer software decoders for sub-HD streams.
 *   <li>Prevents the rendering of the first frame when audio can start playing before the first
 *       video key frame's presentation timestamp.
 * </ul>
 */
public class VideoRendererExoV2 extends MediaCodecVideoRenderer {
    private static final String TAG = "MpegTsVideoTrackRender";

    private static final String SOFTWARE_DECODER_NAME_PREFIX = "OMX.google.";
    private static final long ALLOWED_JOINING_TIME_MS = 5000;
    private static final int DROPPED_FRAMES_NOTIFICATION_THRESHOLD = 10;
    private static final int MIN_HD_HEIGHT = 720;
    private static Field sRenderedFirstFrameField;

    private final boolean mIsSwCodecEnabled;
    private boolean mCodecIsSwPreferred;
    private boolean mSetRenderedFirstFrame;

    static {
        // Remove the reflection below once b/31223646 is resolved.
        try {
            // TODO: Remove this workaround by using public notification mechanisms.
            sRenderedFirstFrameField =
                    MediaCodecVideoRenderer.class.getDeclaredField("renderedFirstFrame");
            sRenderedFirstFrameField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            // Null-checking for {@code sRenderedFirstFrameField} will do the error handling.
        }
    }

    /**
     * Creates an instance.
     *
     * @param context A context.
     * @param handler The handler to use when delivering events to {@code eventListener}. May be
     *     null if delivery of events is not required.
     * @param listener The listener of events. May be null if delivery of events is not required.
     */
    public VideoRendererExoV2(
            Context context, Handler handler, VideoRendererEventListener listener) {
        super(
                context,
                MediaCodecSelector.DEFAULT,
                ALLOWED_JOINING_TIME_MS,
                handler,
                listener,
                DROPPED_FRAMES_NOTIFICATION_THRESHOLD);
        mIsSwCodecEnabled = TunerFeatures.USE_SW_CODEC_FOR_SD.isEnabled(context);
    }

    @Override
    protected List<MediaCodecInfo> getDecoderInfos(
            MediaCodecSelector codecSelector, Format format, boolean requiresSecureDecoder)
            throws DecoderQueryException {
        List<MediaCodecInfo> decoderInfos =
                super.getDecoderInfos(codecSelector, format, requiresSecureDecoder);
        if (mIsSwCodecEnabled && mCodecIsSwPreferred) {
            // If software decoders are preferred, we sort the returned list so that software
            // decoders appear first.
            Collections.sort(
                    decoderInfos,
                    (o1, o2) ->
                            // Negate the result to consider software decoders as lower in
                            // comparisons.
                            -Boolean.compare(
                                    o1.name.startsWith(SOFTWARE_DECODER_NAME_PREFIX),
                                    o2.name.startsWith(SOFTWARE_DECODER_NAME_PREFIX)));
        }
        return decoderInfos;
    }

    @Override
    protected void onInputFormatChanged(Format format) throws ExoPlaybackException {
        mCodecIsSwPreferred =
                MimeTypes.VIDEO_MPEG2.equals(format.sampleMimeType)
                        && format.height < MIN_HD_HEIGHT;
        super.onInputFormatChanged(format);
    }

    @Override
    protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
        super.onPositionReset(positionUs, joining);
        // Disabling pre-rendering of the first frame in order to avoid a frozen picture when
        // starting the playback. We do this only once, when the renderer is enabled at first, since
        // we need to pre-render the frame in advance when we do trickplay backed by seeking.
        if (!mSetRenderedFirstFrame) {
            setRenderedFirstFrame(true);
            mSetRenderedFirstFrame = true;
        }
    }

    private void setRenderedFirstFrame(boolean renderedFirstFrame) {
        if (sRenderedFirstFrameField != null) {
            try {
                sRenderedFirstFrameField.setBoolean(this, renderedFirstFrame);
            } catch (IllegalAccessException e) {
                Log.w(
                        TAG,
                        "renderedFirstFrame is not accessible. Playback may start with a frozen"
                                + " picture.");
            }
        }
    }
}
