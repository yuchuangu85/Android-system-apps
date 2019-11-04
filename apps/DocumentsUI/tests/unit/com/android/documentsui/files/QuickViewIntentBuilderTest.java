package com.android.documentsui.files;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import android.content.Intent;
import android.content.QuickViewConstants;
import android.content.pm.PackageManager;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestPackageManager;
import com.android.documentsui.testing.TestResources;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class QuickViewIntentBuilderTest {

    private static String mTargetPackageName;
    private PackageManager mPm;
    private TestEnv mEnv;
    private TestResources mRes;

    @Before
    public void setUp() {
        mTargetPackageName =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
        mPm = TestPackageManager.create();
        mEnv = TestEnv.create();
        mRes = TestResources.create();

        mRes.setQuickViewerPackage(mTargetPackageName);
    }

    @Test
    public void testSetsNoFeatures_InArchiveDocument() {
        QuickViewIntentBuilder builder =
                new QuickViewIntentBuilder(
                        mPm, mRes, TestEnv.FILE_IN_ARCHIVE, mEnv.archiveModel, false);

        Intent intent = builder.build();

        String[] features = intent.getStringArrayExtra(Intent.EXTRA_QUICK_VIEW_FEATURES);
        assertEquals(0, features.length);
    }

    @Test
    public void testSetsFullFeatures_RegularDocument() {
        QuickViewIntentBuilder builder =
                new QuickViewIntentBuilder(mPm, mRes, TestEnv.FILE_JPG, mEnv.model, false);

        Intent intent = builder.build();

        Set<String> features = new HashSet<>(
                Arrays.asList(intent.getStringArrayExtra(Intent.EXTRA_QUICK_VIEW_FEATURES)));

        assertEquals("Unexpected features set: " + features, 6, features.size());
        assertTrue(features.contains(QuickViewConstants.FEATURE_VIEW));
        assertTrue(features.contains(QuickViewConstants.FEATURE_EDIT));
        assertTrue(features.contains(QuickViewConstants.FEATURE_DELETE));
        assertTrue(features.contains(QuickViewConstants.FEATURE_SEND));
        assertTrue(features.contains(QuickViewConstants.FEATURE_DOWNLOAD));
        assertTrue(features.contains(QuickViewConstants.FEATURE_PRINT));
    }

    @Test
    public void testPickerFeatures_RegularDocument() {

        QuickViewIntentBuilder builder =
                new QuickViewIntentBuilder(mPm, mRes, TestEnv.FILE_JPG, mEnv.model, true);

        Intent intent = builder.build();

        Set<String> features = new HashSet<>(
                Arrays.asList(intent.getStringArrayExtra(Intent.EXTRA_QUICK_VIEW_FEATURES)));

        assertEquals("Unexpected features set: " + features, 1, features.size());
        assertTrue(features.contains(QuickViewConstants.FEATURE_VIEW));
    }
}
