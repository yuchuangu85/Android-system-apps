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
package com.android.documentsui.inspector;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.test.suitebuilder.annotation.MediumTest;

import androidx.test.rule.provider.ProviderTestRule;

import com.android.documentsui.InspectorProvider;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.inspector.InspectorController.DataSupplier;
import com.android.documentsui.testing.LatchedConsumer;
import com.android.documentsui.testing.TestSupportLoaderManager;

import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * This test relies the inspector providers test.txt file in inspector root.
 */
@MediumTest
public class DocumentLoaderTest extends TestCase {

    private static final String TEST_DOC_NAME = "test.txt";
    private static final String DIR_TOP = "Top";
    private static final String NOT_DIRECTORY = "OpenInProviderTest";

    private Context mContext;
    private TestSupportLoaderManager mLoaderManager;
    private DataSupplier mLoader;
    private ContentResolver mResolver;

    @Rule
    private ProviderTestRule mProviderTestRule = new ProviderTestRule.Builder(
            InspectorProvider.class, InspectorProvider.AUTHORITY).build();

    @Before
    public void setUp() throws Exception {
        super.setUp();

        mContext = prepareContentResolverSource();
        mResolver = mContext.getContentResolver();
        mLoaderManager = new TestSupportLoaderManager();
        mLoader = new RuntimeDataSupplier(mContext, mLoaderManager);

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
    }

    protected Context prepareContentResolverSource() {
        ContentResolver contentResolver = mProviderTestRule.getResolver();
        Context context = mock(Context.class);
        // inject ContentResolver
        when(context.getContentResolver()).thenReturn(contentResolver);
        // inject ContentResolver and prevent CursorLoader.loadInBackground from
        // NullPointerException
        when(context.getApplicationContext()).thenReturn(context);
        return context;

    }

    /**
     * Tests the loader using the Inspector Content provider. This test that we got valid info back
     * from the loader.
     *
     * @throws Exception
     */
    @Test
    public void testLoadsDocument() throws Exception {
        Uri validUri = DocumentsContract.buildDocumentUri(
                InspectorProvider.AUTHORITY, TEST_DOC_NAME);
        LatchedConsumer<DocumentInfo> consumer = new LatchedConsumer<>(1);
        mLoader.loadDocInfo(validUri, consumer);

        // this is a test double that requires explicitly loading. @see TestLoaderManager
        mLoaderManager.getLoader(0).startLoading();

        consumer.assertCalled(1000, TimeUnit.MILLISECONDS);

        assertNotNull(consumer.getValue());
        assertEquals(consumer.getValue().displayName, TEST_DOC_NAME);
        assertEquals(consumer.getValue().size, 0);
    }

    /**
     * Test invalid uri, DocumentInfo returned should be null.
     *
     * @throws Exception
     */
    @Test
    public void testInvalidInput() throws Exception {
        Uri invalidUri = Uri.parse("content://poodles/chuckleberry/ham");
        LatchedConsumer<DocumentInfo> consumer = new LatchedConsumer<>(1);
        mLoader.loadDocInfo(invalidUri, consumer);

        // this is a test double that requires explicitly loading. @see TestLoaderManager
        mLoaderManager.getLoader(0).startLoading();

        consumer.assertCalled(1000, TimeUnit.MILLISECONDS);
        assertNull(consumer.getValue());
    }

    @Test
    public void testNonContentUri() {

        Uri invalidUri = Uri.parse("http://poodles/chuckleberry/ham");
        LatchedConsumer<DocumentInfo> consumer = new LatchedConsumer<>(1);

        try {
            mLoader.loadDocInfo(invalidUri, consumer);

            // this is a test double that requires explicitly loading. @see TestLoaderManager
            mLoaderManager.getLoader(0).startLoading();
            fail("Should have thrown exception.");
        } catch (Exception expected) {}
    }

    @Test
    public void testDir_loadNumberOfChildren() throws Exception {
        Uri dirUri = DocumentsContract.buildDocumentUri(
            InspectorProvider.AUTHORITY, DIR_TOP);

        DocumentInfo info = DocumentInfo.fromUri(mResolver, dirUri);

        LatchedConsumer<Integer> consumer = new LatchedConsumer<>(1);
        mLoader.loadDirCount(info, consumer);
        mLoaderManager.getLoader(0).startLoading();

        consumer.assertCalled(1000, TimeUnit.MILLISECONDS);
        assertEquals(consumer.getValue().intValue(), 4);
    }

    @Test
    public void testDir_notADirectory() throws Exception {
        Uri uri = DocumentsContract.buildDocumentUri(
            InspectorProvider.AUTHORITY, NOT_DIRECTORY);

        DocumentInfo info = DocumentInfo.fromUri(mResolver, uri);
        LatchedConsumer<Integer> consumer = new LatchedConsumer<>(1);

        try {
            mLoader.loadDirCount(info, consumer);
            mLoaderManager.getLoader(0).startLoading();
            fail("should have thrown exception");
        } catch (Exception expected) {}
    }

    @Test
    public void testLoadMetadata() throws Exception  {
        Uri uri = DocumentsContract.buildDocumentUri(
                InspectorProvider.AUTHORITY, InspectorProvider.TEST_JPEG);
        LatchedConsumer<Bundle> consumer = new LatchedConsumer<>(1);

        mLoader.getDocumentMetadata(uri, consumer);
        mLoaderManager.getLoader(0).startLoading();

        consumer.assertCalled(100, TimeUnit.MILLISECONDS);
        assertNotNull(consumer.getValue());
        assertEquals(consumer.getValue().getInt(ExifInterface.TAG_IMAGE_WIDTH),
                InspectorProvider.TEST_JPEG_WIDTH);
        assertEquals(consumer.getValue().getInt(ExifInterface.TAG_IMAGE_LENGTH),
                InspectorProvider.TEST_JPEG_HEIGHT);
    }

    @Test
    public void testLoadMetadata_Unsupported() throws Exception  {
        Uri uri = DocumentsContract.buildDocumentUri(
                InspectorProvider.AUTHORITY, InspectorProvider.INVALID_JPEG);
        LatchedConsumer<Bundle> consumer = new LatchedConsumer<>(1);

        mLoader.getDocumentMetadata(uri, consumer);
        mLoaderManager.getLoader(0).startLoading();

        consumer.assertCalled(100, TimeUnit.MILLISECONDS);
        assertNull(consumer.getValue());
    }
}
