package com.android.documentsui.picker;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.rule.provider.ProviderTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PickCountRecordProviderTest {

    private final static int FAKE_FILE_ID = 1234567;

    private Uri mPickRecordUri;

    @Rule
    public ProviderTestRule mProviderTestRule = new ProviderTestRule.Builder(
            PickCountRecordProvider.class, PickCountRecordProvider.AUTHORITY)
            .build();

    @Before
    public void setUp() {
        mPickRecordUri = PickCountRecordProvider.buildPickRecordUri(FAKE_FILE_ID);
        final ContentValues values = new ContentValues();
        values.clear();
        values.put(PickCountRecordProvider.Columns.PICK_COUNT, 1);
        mProviderTestRule.getResolver().insert(mPickRecordUri, values);
    }

    @After
    public void tearDown() {
        mProviderTestRule.getResolver().delete(mPickRecordUri, null, null);
    }

    @Test
    public void testInsert() {
        final ContentValues values = new ContentValues();
        values.clear();
        values.put(PickCountRecordProvider.Columns.PICK_COUNT, 3);
        mProviderTestRule.getResolver().insert(mPickRecordUri, values);
        Cursor cursor = mProviderTestRule.getResolver().query(
            mPickRecordUri, null, null, null, null);
        cursor.moveToNext();
        int index = cursor.getColumnIndex(PickCountRecordProvider.Columns.PICK_COUNT);
        assertThat(cursor.getInt(index)).isEqualTo(3);
    }

    @Test
    public void testQuery() {
        Cursor cursor = mProviderTestRule.getResolver().query(
            mPickRecordUri, null, null, null, null);
        cursor.moveToNext();
        int index = cursor.getColumnIndex(PickCountRecordProvider.Columns.PICK_COUNT);
        assertThat(cursor.getInt(index)).isEqualTo(1);
    }

    @Test
    public void testDelete() {
        Cursor cursor = mProviderTestRule.getResolver().query(
            mPickRecordUri, null, null, null, null);
        assertThat(cursor.getCount()).isEqualTo(1);

        mProviderTestRule.getResolver().delete(mPickRecordUri, null, null);

        cursor = mProviderTestRule.getResolver().query(
            mPickRecordUri, null, null, null, null);
        assertThat(cursor.getCount()).isEqualTo(0);
    }

    @Test
    public void testUpdate() {
        boolean unsupportExcetionCaught = false;
        try {
            mProviderTestRule.getResolver().update(mPickRecordUri, null, null, null);
        } catch (UnsupportedOperationException e) {
            unsupportExcetionCaught = true;
        }
        assertThat(unsupportExcetionCaught).isTrue();
    }
}