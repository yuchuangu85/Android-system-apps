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

package com.android.car.media.common;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public class MediaItemMetadataTest {
    private static final String EXTRA_METADATA = "metadata";

    @Test
    public void testWriteReadParcel() {
        String[] albums = {"album", "", null};
        String[] artists = {"artist", "", null};
        for (int i = 0; i < 3; i++) {
            MediaItemMetadata metadata = new MediaItemMetadata(null, 0L, false, false, albums[i],
                    artists[i]);

            Intent intent = new Intent().putExtra(EXTRA_METADATA, metadata);
            MediaItemMetadata newMetadata = (MediaItemMetadata) intent.getExtras().getParcelable(
                    EXTRA_METADATA);

            assertThat(metadata).isEqualTo(newMetadata);
        }
    }
}
