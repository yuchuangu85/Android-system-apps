package com.android.documentsui.picker;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class PickResultTest {
    private PickResult mPickResult;

    @Before
    public void setUp() {
        mPickResult = new PickResult();
    }

    @Test
    public void testActionCount() {
        mPickResult.increaseActionCount();
        assertThat(mPickResult.getActionCount()).isEqualTo(1);
    }

    @Test
    public void testDuration() {
        mPickResult.setPickStartTime(487);
        mPickResult.increaseDuration(9487);
        assertThat(mPickResult.getDuration()).isEqualTo(9000);
    }

    @Test
    public void testFileCount() {
        mPickResult.setFileCount(10);
        assertThat(mPickResult.getFileCount()).isEqualTo(10);
    }

    @Test
    public void testIsSearching() {
        mPickResult.setIsSearching(true);
        assertThat(mPickResult.isSearching()).isTrue();
    }

    @Test
    public void testRoot() {
        mPickResult.setRoot(2);
        assertThat(mPickResult.getRoot()).isEqualTo(2);
    }

    @Test
    public void testMimeType() {
        mPickResult.setMimeType(3);
        assertThat(mPickResult.getMimeType()).isEqualTo(3);
    }

    @Test
    public void testRepeatedlyPickTimes() {
        mPickResult.setRepeatedPickTimes(4);
        assertThat(mPickResult.getRepeatedPickTimes()).isEqualTo(4);
    }

    @Test
    public void testFileUri() {
        Uri fakeUri = new Uri.Builder().authority("test").appendPath("path").build();
        mPickResult.setFileUri(fakeUri);
        assertThat(mPickResult.getFileUri()).isEqualTo(fakeUri);
    }
}
