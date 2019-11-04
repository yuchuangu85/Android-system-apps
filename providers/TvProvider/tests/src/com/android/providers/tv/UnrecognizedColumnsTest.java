package com.android.providers.tv;

import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Programs;
import android.os.Bundle;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import com.android.providers.tv.Utils.Program;
import java.util.Arrays;

import com.google.android.collect.Sets;

public class UnrecognizedColumnsTest extends AndroidTestCase {
    private static final String PERMISSION_ACCESS_ALL_EPG_DATA =
            "com.android.providers.tv.permission.ACCESS_ALL_EPG_DATA";
    private static final String PERMISSION_READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS";

    private static final String MY_PACKAGE = "example.my";
    private static final String ANOTHER_PACKAGE = "example.another";

    private MockContentResolver mResolver;
    private TvProviderForTesting mProvider;
    private MockTvProviderContext mContext;
    private Program mProgram;

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

        mContext = new MockTvProviderContext(mResolver, getContext());
        // get data of the calling package only
        mContext.grantOrRejectPermission(PERMISSION_ACCESS_ALL_EPG_DATA, false);
        mContext.grantOrRejectPermission(PERMISSION_READ_TV_LISTINGS, false);

        setContext(mContext);

        final ProviderInfo info = new ProviderInfo();
        info.authority = TvContract.AUTHORITY;
        mProvider.attachInfoForTesting(getContext(), info);
    }


    @Override
    protected void tearDown() throws Exception {
        Utils.clearTvProvider(mResolver);
        mProvider.setOpenHelper(null, true);
        mProvider.shutdown();
        super.tearDown();
    }

    public void testUnrecognizedColumns() {
        insertPrograms();

        String[] projection = new String[] {
            TvContract.Programs._ID,
            "_random_name",
            " with spaces ",
            "\' in single quotes \'",
            "\" in double quotes \"",
            "quotes \' inside \' this \" name \"",
        };

        Cursor cursor =
            mResolver.query(TvContract.Programs.CONTENT_URI, projection, null, null, null);
        assertNotNull(cursor);
        cursor.moveToNext();
        assertEquals(1, cursor.getCount());

        assertEquals(
            "Column names don't match.",
            Arrays.asList(
                    Programs._ID,
                    "_random_name",
                    " with spaces ",
                    "\' in single quotes \'",
                    "\" in double quotes \"",
                    "quotes \' inside \' this \" name \""),
            Arrays.asList(cursor.getColumnNames()));

        assertEquals(mProgram.id, cursor.getLong(0));
        assertNull(cursor.getString(1));
        assertNull(cursor.getString(2));
        assertNull(cursor.getString(3));
        assertNull(cursor.getString(4));
        assertNull(cursor.getString(5));
    }

    private void insertPrograms() {
        mProvider.callingPackage = MY_PACKAGE;
        long myChannelId = Utils.insertChannel(mResolver);
        mProgram = new Program(1, MY_PACKAGE);
        Utils.insertPrograms(mResolver, myChannelId, mProgram);

        mProvider.callingPackage = ANOTHER_PACKAGE;
        long anotherChannelId = Utils.insertChannel(mResolver);
        Program anotherProgram = new Program(2, ANOTHER_PACKAGE);
        Utils.insertPrograms(mResolver, anotherChannelId, anotherProgram);

        mProvider.callingPackage = MY_PACKAGE;
    }
}
