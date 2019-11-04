/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.providers.tv;

import static com.android.providers.tv.Utils.PreviewProgram;
import static com.android.providers.tv.Utils.Program;
import static com.android.providers.tv.Utils.RecordedProgram;
import static com.android.providers.tv.Utils.WatchNextProgram;
import static com.android.providers.tv.Utils.WatchedProgram;

import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;

import com.google.android.collect.Sets;

import java.util.concurrent.Executor;

public class PackageRemovedReceiverTest extends AndroidTestCase {
    private static final String FAKE_PACKAGE_NAME_1 = "package.removed.receiver.Test1";
    private static final String FAKE_PACKAGE_NAME_2 = "package.removed.receiver.Test2";

    private MockContentResolver mResolver;
    private TvProviderForTesting mProvider;
    private PackageRemovedReceiver mReceiver;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mResolver = new MockContentResolver();
        mResolver.addProvider(Settings.AUTHORITY, new MockContentProvider() {
            @Override
            public Bundle call(String method, String request, Bundle args) {
                return new Bundle();
            }
        });

        mProvider = new TvProviderForTesting();
        mResolver.addProvider(TvContract.AUTHORITY, mProvider);

        setContext(new MockTvProviderContext(mResolver, getContext()));

        final ProviderInfo info = new ProviderInfo();
        info.authority = TvContract.AUTHORITY;
        mProvider.attachInfoForTesting(getContext(), info);

        mReceiver = new PackageRemovedReceiver(
                new Executor() {
                    public void execute(Runnable command) {
                        command.run();
                    }
                });
        Utils.clearTvProvider(mResolver);
    }

    @Override
    protected void tearDown() throws Exception {
        Utils.clearTvProvider(mResolver);
        mProvider.shutdown();
        super.tearDown();
    }

    public void testPackageRemoved() {
        Program programInPackage1 = new Program(FAKE_PACKAGE_NAME_1);
        Program programInPackage2 = new Program(FAKE_PACKAGE_NAME_2);
        PreviewProgram previewProgramInPackage1 = new PreviewProgram(FAKE_PACKAGE_NAME_1);
        PreviewProgram previewProgramInPackage2 = new PreviewProgram(FAKE_PACKAGE_NAME_2);
        WatchedProgram watchedProgramInPackage1 = new WatchedProgram(FAKE_PACKAGE_NAME_1);
        WatchedProgram watchedProgramInPackage2 = new WatchedProgram(FAKE_PACKAGE_NAME_2);
        WatchNextProgram watchNextProgramInPackage1 = new WatchNextProgram(FAKE_PACKAGE_NAME_1);
        WatchNextProgram watchNextProgramInPackage2 = new WatchNextProgram(FAKE_PACKAGE_NAME_2);

        mProvider.callingPackage = FAKE_PACKAGE_NAME_1;
        long channelInPackage1Id = Utils.insertChannel(mResolver);
        Utils.insertPrograms(mResolver, channelInPackage1Id, programInPackage1);
        Utils.insertPreviewPrograms(mResolver, channelInPackage1Id, previewProgramInPackage1);
        RecordedProgram recordedProgramInPackage1 =
                new RecordedProgram(FAKE_PACKAGE_NAME_1, channelInPackage1Id);
        Utils.insertRecordedPrograms(mResolver, channelInPackage1Id, recordedProgramInPackage1);
        Utils.insertWatchedPrograms(mResolver, FAKE_PACKAGE_NAME_1, channelInPackage1Id,
                watchedProgramInPackage1);
        Utils.insertWatchNextPrograms(mResolver, FAKE_PACKAGE_NAME_1, watchNextProgramInPackage1);

        mProvider.callingPackage = FAKE_PACKAGE_NAME_2;
        long channelInPackage2Id = Utils.insertChannel(mResolver);
        Utils.insertPrograms(mResolver, channelInPackage2Id, programInPackage2);
        Utils.insertPreviewPrograms(mResolver, channelInPackage2Id, previewProgramInPackage2);
        RecordedProgram recordedProgramInPackage2 =
                new RecordedProgram(FAKE_PACKAGE_NAME_2, channelInPackage2Id);
        Utils.insertRecordedPrograms(mResolver, channelInPackage2Id, recordedProgramInPackage2);
        Utils.insertWatchedPrograms(mResolver, FAKE_PACKAGE_NAME_2, channelInPackage2Id,
                watchedProgramInPackage2);
        Utils.insertWatchNextPrograms(mResolver, FAKE_PACKAGE_NAME_2, watchNextProgramInPackage2);

        assertEquals(
                Sets.newHashSet(programInPackage1, programInPackage2),
                Utils.queryPrograms(mResolver));
        assertEquals(
                Sets.newHashSet(previewProgramInPackage1, previewProgramInPackage2),
                Utils.queryPreviewPrograms(mResolver));
        assertEquals(
                Sets.newHashSet(recordedProgramInPackage1, recordedProgramInPackage2),
                Utils.queryRecordedPrograms(mResolver));
        assertEquals(
                Sets.newHashSet(watchedProgramInPackage1, watchedProgramInPackage2),
                Utils.queryWatchedPrograms(mResolver));
        assertEquals(
                Sets.newHashSet(watchNextProgramInPackage1, watchNextProgramInPackage2),
                Utils.queryWatchNextPrograms(mResolver));
        assertEquals(2, Utils.getChannelCount(mResolver));

        mReceiver.setPendingResult(Utils.createFakePendingResultForTests());
        mReceiver.onReceive(getContext(), new Intent(Intent.ACTION_PACKAGE_FULLY_REMOVED,
                Uri.parse("package:" + FAKE_PACKAGE_NAME_1)));

        assertEquals("Program should be removed if its package is removed.",
                Sets.newHashSet(programInPackage2), Utils.queryPrograms(mResolver));
        assertEquals("PreviewProgram should be removed if its package is removed.",
                Sets.newHashSet(previewProgramInPackage2), Utils.queryPreviewPrograms(mResolver));
        assertEquals("RecordedProgram should be removed if its package is removed.",
                Sets.newHashSet(recordedProgramInPackage2),
                Utils.queryRecordedPrograms(mResolver));
        assertEquals("WatchedProgram should be removed if its package is removed.",
                Sets.newHashSet(watchedProgramInPackage2), Utils.queryWatchedPrograms(mResolver));
        assertEquals("WatchNextProgram should be removed if its package is removed.",
                Sets.newHashSet(watchNextProgramInPackage2),
                Utils.queryWatchNextPrograms(mResolver));
        assertEquals("Channel should be removed if its package is removed.",
                1, Utils.getChannelCount(mResolver));

        mReceiver.setPendingResult(Utils.createFakePendingResultForTests());
        mReceiver.onReceive(getContext(), new Intent(Intent.ACTION_PACKAGE_FULLY_REMOVED,
                Uri.parse("package:" + FAKE_PACKAGE_NAME_2)));

        assertTrue("Program should be removed if its package is removed.",
                Utils.queryPrograms(mResolver).isEmpty());
        assertTrue("PreviewProgram should be removed if its package is removed.",
                Utils.queryPreviewPrograms(mResolver).isEmpty());
        assertTrue("RecordedProgram should be removed if its package is removed.",
                Utils.queryRecordedPrograms(mResolver).isEmpty());
        assertTrue("WatchedProgram should be removed if its package is removed.",
                Utils.queryWatchedPrograms(mResolver).isEmpty());
        assertTrue("WatchedProgram should be removed if its package is removed.",
                Utils.queryWatchNextPrograms(mResolver).isEmpty());
        assertEquals("Channel should be removed if its package is removed.",
                0, Utils.getChannelCount(mResolver));
    }
}
