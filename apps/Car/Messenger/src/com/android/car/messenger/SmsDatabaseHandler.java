package com.android.car.messenger;


import static com.android.car.messenger.MessengerDelegate.getContactId;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.Telephony;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.android.car.messenger.log.L;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Reads and writes SMS Messages into the Telephony.SMS Database.
 */
class SmsDatabaseHandler {
    private static final String TAG = "CM.SmsDatabaseHandler";
    private static final int MESSAGE_NOT_FOUND = -1;
    private static final int DUPLICATE_MESSAGES_FOUND = -2;
    private static final int DATABASE_ERROR = -3;
    private static final Uri SMS_URI = Telephony.Sms.CONTENT_URI;
    private static final String SMS_SELECTION = Telephony.Sms.ADDRESS + "=? AND "
            + Telephony.Sms.BODY + "=? AND (" + Telephony.Sms.DATE + ">=? OR " + Telephony.Sms.DATE
            + "<=?)";
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat(
            "MMM dd,yyyy HH:mm");

    private final ContentResolver mContentResolver;
    private final boolean mCanWriteToDatabase;

    protected SmsDatabaseHandler(Context context) {
        mCanWriteToDatabase = canWriteToDatabase(context);
        mContentResolver = context.getContentResolver();
        readDatabase(context);
    }

    protected void addOrUpdate(MapMessage message) {
        if (!mCanWriteToDatabase) {
            return;
        }

        int messageIndex = findMessageIndex(message);
        switch(messageIndex) {
            case DUPLICATE_MESSAGES_FOUND:
                removePreviousAndInsert(message);
                L.d(TAG, "Message has more than one duplicate in Telephony Database: %s",
                        message.toString());
                return;
            case MESSAGE_NOT_FOUND:
                mContentResolver.insert(SMS_URI, buildMessageContentValues(message));
                return;
            case DATABASE_ERROR:
                return;
            default:
                update(messageIndex, buildMessageContentValues(message));
        }
    }

    protected void removeMessagesForDevice(String address) {
        if (!mCanWriteToDatabase) {
            return;
        }

        String smsSelection = Telephony.Sms.ADDRESS + "=?";
        String[] smsSelectionArgs = {address};
        mContentResolver.delete(SMS_URI, smsSelection, smsSelectionArgs);
    }

    /**
     * Reads the Telephony SMS Database, and logs all of the SMS messages that have been received
     * in the last five minutes.
     * @param context
     */
    protected static void readDatabase(Context context) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) {
            return;
        }

        Long beginningTimeStamp = System.currentTimeMillis() - 300000;
        String timeStamp = DATE_FORMATTER.format(new Date(beginningTimeStamp));
        Log.d(TAG,
                " ------ printing SMSs received after " + timeStamp + "-------- ");

        String smsSelection = Telephony.Sms.DATE + ">=?";
        String[] smsSelectionArgs = {Long.toString(beginningTimeStamp)};
        Cursor cursor = context.getContentResolver().query(SMS_URI, null,
                smsSelection,
                smsSelectionArgs, null /* sortOrder */);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String body = cursor.getString(12);

                Date date = new Date(cursor.getLong(4));
                Log.d(TAG,
                        "_id " + cursor.getInt(0) + " person: " + cursor.getInt(3) + " body: "
                                + body.substring(0, Math.min(body.length(), 17)) + " address: "
                                + cursor.getString(2) + " date: " + DATE_FORMATTER.format(
                                date) + " longDate " + cursor.getLong(4) + " read: "
                                + cursor.getInt(7));
            }
        }
        Log.d(TAG, " ------ end read table --------");
    }

    /** Removes multiple previous copies, and inserts the new message. **/
    private void removePreviousAndInsert(MapMessage message) {
        String[] smsSelectionArgs = createSmsSelectionArgs(message);

        mContentResolver.delete(SMS_URI, SMS_SELECTION, smsSelectionArgs);
        mContentResolver.insert(SMS_URI, buildMessageContentValues(message));
    }

    private int findMessageIndex(MapMessage message) {
        String[] smsSelectionArgs = createSmsSelectionArgs(message);

        String[] projection = {BaseColumns._ID};
        Cursor cursor = mContentResolver.query(SMS_URI, projection, SMS_SELECTION,
                smsSelectionArgs, null /* sortOrder */);

        if (cursor != null && cursor.getCount() != 0) {
            if (cursor.moveToFirst() && cursor.isLast()) {
                return getIdOrThrow(cursor);
            } else {
                return DUPLICATE_MESSAGES_FOUND;
            }
        } else {
            return MESSAGE_NOT_FOUND;
        }
    }

    private int getIdOrThrow(Cursor cursor) {
        try {
            int columnIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID);
            return cursor.getInt(columnIndex);
        } catch (IllegalArgumentException e) {
            L.d(TAG, "Could not find _id column: " + e.getMessage());
            return DATABASE_ERROR;
        }
    }

    private void update(int messageIndex, ContentValues value) {
        final String smsSelection = BaseColumns._ID + "=?";
        String[] smsSelectionArgs = {Integer.toString(messageIndex)};

        mContentResolver.update(SMS_URI, value, smsSelection, smsSelectionArgs);
    }

    /** Create the ContentValues object using message info, following SMS columns **/
    private ContentValues buildMessageContentValues(MapMessage message) {
        ContentValues newMessage = new ContentValues();
        newMessage.put(Telephony.Sms.BODY, DatabaseUtils.sqlEscapeString(message.getMessageText()));
        newMessage.put(Telephony.Sms.DATE, message.getReceiveTime());
        newMessage.put(Telephony.Sms.ADDRESS, message.getDeviceAddress());
        // TODO: if contactId is null, add it.
        newMessage.put(Telephony.Sms.PERSON,
                getContactId(mContentResolver,
                        message.getSenderContactUri()));
        newMessage.put(Telephony.Sms.READ, (message.isReadOnPhone() || message.isReadOnCar()));
        return newMessage;
    }

    private String[] createSmsSelectionArgs(MapMessage message) {
        String sqlFriendlyMessageText = DatabaseUtils.sqlEscapeString(message.getMessageText());
        String[] smsSelectionArgs = {message.getDeviceAddress(), sqlFriendlyMessageText,
                Long.toString(message.getReceiveTime() - 5000), Long.toString(
                message.getReceiveTime() + 5000)};
        return smsSelectionArgs;
    }

    /** Checks if the application has the needed AppOps permission to write to the Telephony DB. **/
    private boolean canWriteToDatabase(Context context) {
        boolean granted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SMS)
                == PackageManager.PERMISSION_GRANTED;

        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OP_WRITE_SMS, android.os.Process.myUid(),
                context.getPackageName());
        if (mode != AppOpsManager.MODE_DEFAULT) {
            granted = (mode == AppOpsManager.MODE_ALLOWED);
        }

        return granted;
    }
}
