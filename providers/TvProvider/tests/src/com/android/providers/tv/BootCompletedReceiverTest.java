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

package com.android.providers.tv;

import static com.android.providers.tv.Utils.PreviewProgram;
import static com.android.providers.tv.Utils.Program;
import static com.android.providers.tv.Utils.RecordedProgram;
import static com.android.providers.tv.Utils.WatchNextProgram;
import static com.android.providers.tv.Utils.WatchedProgram;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.media.tv.TvContract;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;

import com.google.android.collect.Sets;

import java.util.concurrent.Executor;

public class BootCompletedReceiverTest extends AndroidTestCase {
    private static final String NON_EXISTING_PACKAGE_NAME = "package.boot.receiver.nonexisting";
    private static final String EXISTING_PACKAGE_NAME = "package.boot.receiver.existing";

    private MockContentResolver mResolver;
    private TvProviderForTesting mProvider;
    private BootCompletedReceiver mReceiver;

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

        MockTvProviderContext mockTvProviderContext =
                new MockTvProviderContext(mResolver, getContext());
        mockTvProviderContext.setExistingPackages(EXISTING_PACKAGE_NAME);
        setContext(mockTvProviderContext);

        final ProviderInfo info = new ProviderInfo();
        info.authority = TvContract.AUTHORITY;
        mProvider.attachInfoForTesting(getContext(), info);

        mReceiver = new BootCompletedReceiver(
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

    public void testPackageRemoved() throws PackageManager.NameNotFoundException {
        Program programInNonExistingPackage = new Program(NON_EXISTING_PACKAGE_NAME);
        Program programInExistingPackage = new Program(EXISTING_PACKAGE_NAME);
        PreviewProgram previewProgramInNonExistingPackage =
                new PreviewProgram(NON_EXISTING_PACKAGE_NAME);
        PreviewProgram previewProgramInExistingPackage =
                new PreviewProgram(EXISTING_PACKAGE_NAME);
        WatchedProgram watchedProgramInNonExistingPackage =
                new WatchedProgram(NON_EXISTING_PACKAGE_NAME);
        WatchedProgram watchedProgramInExistingPackage = new WatchedProgram(EXISTING_PACKAGE_NAME);
        WatchNextProgram watchNextProgramInNonExistingPackage =
                new WatchNextProgram(NON_EXISTING_PACKAGE_NAME);
        WatchNextProgram watchNextProgramInExistingPackage =
                new WatchNextProgram(EXISTING_PACKAGE_NAME);

        mProvider.callingPackage = NON_EXISTING_PACKAGE_NAME;
        long channelInNonExistingPackageId = Utils.insertChannel(mResolver);
        Utils.insertPrograms(mResolver, channelInNonExistingPackageId, programInNonExistingPackage);
        Utils.insertPreviewPrograms(
                mResolver, channelInNonExistingPackageId, previewProgramInNonExistingPackage);
        RecordedProgram recordedProgramInNonExistingPackage =
                new RecordedProgram(NON_EXISTING_PACKAGE_NAME, channelInNonExistingPackageId);
        Utils.insertRecordedPrograms(
                mResolver, channelInNonExistingPackageId, recordedProgramInNonExistingPackage);
        Utils.insertWatchedPrograms(
                mResolver, NON_EXISTING_PACKAGE_NAME, channelInNonExistingPackageId,
                watchedProgramInNonExistingPackage);
        Utils.insertWatchNextPrograms(mResolver, NON_EXISTING_PACKAGE_NAME,
                watchNextProgramInNonExistingPackage);

        mProvider.callingPackage = EXISTING_PACKAGE_NAME;
        long channelInExistingPackageId = Utils.insertChannel(mResolver);
        Utils.insertPrograms(mResolver, channelInExistingPackageId, programInExistingPackage);
        Utils.insertPreviewPrograms(
                mResolver, channelInExistingPackageId, previewProgramInExistingPackage);
        RecordedProgram recordedProgramInExistingPackage =
                new RecordedProgram(EXISTING_PACKAGE_NAME, channelInExistingPackageId);
        Utils.insertRecordedPrograms(
                mResolver, channelInExistingPackageId, recordedProgramInExistingPackage);
        Utils.insertWatchedPrograms(
                mResolver, EXISTING_PACKAGE_NAME, channelInExistingPackageId,
                watchedProgramInExistingPackage);
        Utils.insertWatchNextPrograms(
                mResolver, EXISTING_PACKAGE_NAME, watchNextProgramInExistingPackage);

        assertEquals(
                Sets.newHashSet(programInNonExistingPackage, programInExistingPackage),
                Utils.queryPrograms(mResolver));
        assertEquals(
                Sets.newHashSet(
                        previewProgramInNonExistingPackage, previewProgramInExistingPackage),
                Utils.queryPreviewPrograms(mResolver));
        assertEquals(
                Sets.newHashSet(
                        recordedProgramInNonExistingPackage, recordedProgramInExistingPackage),
                Utils.queryRecordedPrograms(mResolver));
        assertEquals(
                Sets.newHashSet(
                        watchedProgramInNonExistingPackage, watchedProgramInExistingPackage),
                Utils.queryWatchedPrograms(mResolver));
        assertEquals(
                Sets.newHashSet(
                        watchNextProgramInNonExistingPackage, watchNextProgramInExistingPackage),
                Utils.queryWatchNextPrograms(mResolver));
        assertEquals(2, Utils.getChannelCount(mResolver));

        mReceiver.setPendingResult(Utils.createFakePendingResultForTests());
        mReceiver.onReceive(getContext(), new Intent(Intent.ACTION_BOOT_COMPLETED));

        assertEquals("Program should be removed if its package doesn't exist.",
                Sets.newHashSet(programInExistingPackage), Utils.queryPrograms(mResolver));
        assertEquals("PreviewProgram should be removed if its package doesn't exist.",
                Sets.newHashSet(
                        previewProgramInExistingPackage), Utils.queryPreviewPrograms(mResolver));
        assertEquals("RecordedProgram should be removed if its package doesn't exist.",
                Sets.newHashSet(recordedProgramInExistingPackage),
                Utils.queryRecordedPrograms(mResolver));
        assertEquals("WatchedProgram should be removed if its package doesn't exist.",
                Sets.newHashSet(
                        watchedProgramInExistingPackage), Utils.queryWatchedPrograms(mResolver));
        assertEquals("WatchNextProgram should be removed if its package doesn't exist.",
                Sets.newHashSet(watchNextProgramInExistingPackage),
                Utils.queryWatchNextPrograms(mResolver));
        assertEquals("Channel should be removed if its package doesn't exist.",
                1, Utils.getChannelCount(mResolver));
    }
}
