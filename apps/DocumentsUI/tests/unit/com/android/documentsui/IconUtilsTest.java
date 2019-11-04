package com.android.documentsui;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;

@SmallTest
public class IconUtilsTest {
    private static final String AUDIO_MIME_TYPE = "audio";
    private static final String IMAGE_MIME_TYPE = "image";
    private static final String TEXT_MIME_TYPE = "text";
    private static final String VIDEO_MIME_TYPE = "video";
    private static final String GENERIC_MIME_TYPE = "generic";

    private Context mTargetContext;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testLoadMimeIcon_isAudioMimeType() {
        assertThat(IconUtils.loadMimeIcon(mTargetContext, AUDIO_MIME_TYPE)).isNotNull();
    }

    @Test
    public void testLoadMimeIcon_isImageMimeType() {
        assertThat(IconUtils.loadMimeIcon(mTargetContext, IMAGE_MIME_TYPE)).isNotNull();
    }

    @Test
    public void testLoadMimeIcon_isGenericMimeType() {
        assertThat(IconUtils.loadMimeIcon(mTargetContext, GENERIC_MIME_TYPE)).isNotNull();
    }

    @Test
    public void testLoadMimeIcon_isVideoMimeType() {
        assertThat(IconUtils.loadMimeIcon(mTargetContext, VIDEO_MIME_TYPE)).isNotNull();
    }

    @Test
    public void testLoadMimeIcon_isTextMimeType() {
        assertThat(IconUtils.loadMimeIcon(mTargetContext, TEXT_MIME_TYPE)).isNotNull();
    }

    @Test
    public void testLoadMimeIcon_isMimeTypeNull_shouldReturnNull() {
        assertThat(IconUtils.loadMimeIcon(mTargetContext, null)).isNull();
    }
}
