/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.telephony.common;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.Settings;
import android.telecom.Call;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.android.car.apps.common.LetterTileDrawable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Helper methods. */
public class TelecomUtils {
    private static final String TAG = "CD.TelecomUtils";

    private static String sVoicemailNumber;
    private static TelephonyManager sTelephonyManager;

    /**
     * Get the voicemail number.
     */
    public static String getVoicemailNumber(Context context) {
        if (sVoicemailNumber == null) {
            sVoicemailNumber = getTelephonyManager(context).getVoiceMailNumber();
        }
        return sVoicemailNumber;
    }

    /**
     * Returns {@code true} if the given number is a voice mail number.
     *
     * @see TelephonyManager#getVoiceMailNumber()
     */
    public static boolean isVoicemailNumber(Context context, String number) {
        return !TextUtils.isEmpty(number) && number.equals(getVoicemailNumber(context));
    }

    /**
     * Get the {@link TelephonyManager} instance.
     */
    // TODO(deanh): remove this, getSystemService is not slow.
    public static TelephonyManager getTelephonyManager(Context context) {
        if (sTelephonyManager == null) {
            sTelephonyManager =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        }
        return sTelephonyManager;
    }

    /**
     * Format a number as a phone number.
     */
    public static String getFormattedNumber(Context context, String number) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getFormattedNumber: " + number);
        }
        if (number == null) {
            return "";
        }

        String countryIso = getIsoDefaultCountryNumber(context);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "PhoneNumberUtils.formatNumberToE16, number: "
                    + number + ", country: " + countryIso);
        }
        String e164 = PhoneNumberUtils.formatNumberToE164(number, countryIso);
        String formattedNumber = PhoneNumberUtils.formatNumber(number, e164, countryIso);
        formattedNumber = TextUtils.isEmpty(formattedNumber) ? number : formattedNumber;
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getFormattedNumber, result: " + formattedNumber);
        }
        return formattedNumber;
    }

    private static String getIsoDefaultCountryNumber(Context context) {
        String countryIso = getTelephonyManager(context).getSimCountryIso().toUpperCase(Locale.US);
        if (countryIso.length() != 2) {
            countryIso = Locale.getDefault().getCountry();
            if (countryIso == null || countryIso.length() != 2) {
                countryIso = "US";
            }
        }

        return countryIso;
    }

    /**
     * Creates a new instance of {@link Phonenumber#Phonenumber} base on the given number and sim
     * card country code. Returns {@code null} if the number in an invalid number.
     */
    @Nullable
    public static Phonenumber.PhoneNumber createI18nPhoneNumber(Context context, String number) {
        try {
            return PhoneNumberUtil.getInstance().parse(number, getIsoDefaultCountryNumber(context));
        } catch (NumberParseException e) {
            return null;
        }
    }

    /**
     * Contains all the info used to display a phone number on the screen.
     * Returned by {@link #getPhoneNumberInfo(Context, String)}
     */
    public static final class PhoneNumberInfo {
        private final String mPhoneNumber;
        private final String mDisplayName;
        private final Uri mAvatarUri;
        private final String mTypeLabel;

        public PhoneNumberInfo(String phoneNumber, String displayName,
                Uri avatarUri, String typeLabel) {
            mPhoneNumber = phoneNumber;
            mDisplayName = displayName;
            mAvatarUri = avatarUri;
            mTypeLabel = typeLabel;
        }

        public String getPhoneNumber() {
            return mPhoneNumber;
        }

        public String getDisplayName() {
            return mDisplayName;
        }

        public Uri getAvatarUri() {
            return mAvatarUri;
        }

        public String getTypeLabel() {
            return mTypeLabel;
        }

    }

    /**
     * Gets all the info needed to properly display a phone number to the UI. (e.g. if it's the
     * voicemail number, return a string and a uri that represents voicemail, if it's a contact, get
     * the contact's name, its avatar uri, the phone number's label, etc).
     */
    public static CompletableFuture<PhoneNumberInfo> getPhoneNumberInfo(
            Context context, String number) {

        if (TextUtils.isEmpty(number)) {
            return CompletableFuture.completedFuture(new PhoneNumberInfo(
                    number,
                    context.getString(R.string.unknown),
                    null,
                    ""));
        }

        if (isVoicemailNumber(context, number)) {
            return CompletableFuture.completedFuture(new PhoneNumberInfo(
                    number,
                    context.getString(R.string.voicemail),
                    makeResourceUri(context, R.drawable.ic_voicemail),
                    ""));
        }

        if (InMemoryPhoneBook.isInitialized()) {
            Contact contact = InMemoryPhoneBook.get().lookupContactEntry(number);
            if (contact != null) {
                String name = contact.getDisplayName();
                if (name == null) {
                    name = getFormattedNumber(context, number);
                }

                if (name == null) {
                    name = context.getString(R.string.unknown);
                }

                PhoneNumber phoneNumber = contact.getPhoneNumber(number);
                CharSequence typeLabel = "";
                if (phoneNumber != null) {
                    typeLabel = Phone.getTypeLabel(context.getResources(),
                            phoneNumber.getType(),
                            phoneNumber.getLabel());
                }

                return CompletableFuture.completedFuture(new PhoneNumberInfo(
                        number,
                        name,
                        contact.getAvatarUri(),
                        typeLabel.toString()));
            }
        }

        return CompletableFuture.supplyAsync(() -> {
            String name = null;
            String photoUriString = null;
            CharSequence typeLabel = "";
            ContentResolver cr = context.getContentResolver();
            try (Cursor cursor = cr.query(
                    Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                    new String[] {
                            PhoneLookup.DISPLAY_NAME,
                            PhoneLookup.PHOTO_URI,
                            PhoneLookup.TYPE,
                            PhoneLookup.LABEL,
                    },
                    null, null, null)) {

                if (cursor != null && cursor.moveToFirst()) {
                    name = cursor.getString(0);
                    photoUriString = cursor.getString(1);
                    int type = cursor.getInt(2);
                    String label = cursor.getString(3);
                    typeLabel = Phone.getTypeLabel(context.getResources(), type, label);
                }
            }

            if (name == null) {
                name = getFormattedNumber(context, number);
            }

            if (name == null) {
                name = context.getString(R.string.unknown);
            }

            return new PhoneNumberInfo(number, name,
                    TextUtils.isEmpty(photoUriString) ? null : Uri.parse(photoUriString),
                    typeLabel.toString());
        });
    }

    /**
     * @return A string representation of the call state that can be presented to a user.
     */
    public static String callStateToUiString(Context context, int state) {
        Resources res = context.getResources();
        switch (state) {
            case Call.STATE_ACTIVE:
                return res.getString(R.string.call_state_call_active);
            case Call.STATE_HOLDING:
                return res.getString(R.string.call_state_hold);
            case Call.STATE_NEW:
            case Call.STATE_CONNECTING:
                return res.getString(R.string.call_state_connecting);
            case Call.STATE_SELECT_PHONE_ACCOUNT:
            case Call.STATE_DIALING:
                return res.getString(R.string.call_state_dialing);
            case Call.STATE_DISCONNECTED:
                return res.getString(R.string.call_state_call_ended);
            case Call.STATE_RINGING:
                return res.getString(R.string.call_state_call_ringing);
            case Call.STATE_DISCONNECTING:
                return res.getString(R.string.call_state_call_ending);
            default:
                throw new IllegalStateException("Unknown Call State: " + state);
        }
    }

    /**
     * Returns true if the telephony network is available.
     */
    public static boolean isNetworkAvailable(Context context) {
        TelephonyManager tm =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return tm.getNetworkType() != TelephonyManager.NETWORK_TYPE_UNKNOWN
                && tm.getSimState() == TelephonyManager.SIM_STATE_READY;
    }

    /**
     * Returns true if airplane mode is on.
     */
    public static boolean isAirplaneModeOn(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    /**
     * Sets a Contact avatar onto the provided {@code icon}. The first letter of the contact's
     * display name or {@code fallbackDisplayName} will be used as a fallback resource if avatar
     * loading fails.
     */
    public static void setContactBitmapAsync(
            Context context,
            final ImageView icon,
            @Nullable final Contact contact,
            @Nullable final String fallbackDisplayName) {
        Uri avatarUri = contact != null ? contact.getAvatarUri() : null;
        String displayName = contact != null ? contact.getDisplayName() : fallbackDisplayName;

        setContactBitmapAsync(context, icon, avatarUri, displayName);
    }

    /**
     * Sets a Contact avatar onto the provided {@code icon}. The first letter of the contact's
     * display name will be used as a fallback resource if avatar loading fails.
     */
    public static void setContactBitmapAsync(
            Context context,
            final ImageView icon,
            final Uri avatarUri,
            final String displayName) {
        LetterTileDrawable letterTileDrawable = createLetterTile(context, displayName);

        Glide.with(context)
                .load(avatarUri)
                .apply(new RequestOptions().centerCrop().error(letterTileDrawable))
                .into(icon);
    }

    /** Create a {@link LetterTileDrawable} for the given display name. */
    public static LetterTileDrawable createLetterTile(Context context, String displayName) {
        LetterTileDrawable letterTileDrawable = new LetterTileDrawable(context.getResources());
        letterTileDrawable.setContactDetails(displayName, displayName);
        return  letterTileDrawable;
    }

    /** Set the given phone number as the primary phone number for its associated contact. */
    public static void setAsPrimaryPhoneNumber(Context context, PhoneNumber phoneNumber) {
        // Update the primary values in the data record.
        ContentValues values = new ContentValues(1);
        values.put(ContactsContract.Data.IS_SUPER_PRIMARY, 1);
        values.put(ContactsContract.Data.IS_PRIMARY, 1);

        context.getContentResolver().update(
                ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, phoneNumber.getId()),
                values, null, null);
    }

    /** Add a contact to favorite or remove it from favorite. */
    public static int setAsFavoriteContact(Context context, Contact contact, boolean isFavorite) {
        if (contact.isStarred() == isFavorite) {
            return 0;
        }

        ContentValues values = new ContentValues(1);
        values.put(ContactsContract.Contacts.STARRED, isFavorite ? 1 : 0);

        String where = ContactsContract.Contacts._ID + " = ?";
        String[] selectionArgs = new String[]{Long.toString(contact.getId())};
        return context.getContentResolver().update(ContactsContract.Contacts.CONTENT_URI, values,
                where, selectionArgs);
    }

    /**
     * Mark missed call log matching given phone number as read. If phone number string is not
     * valid, it will mark all new missed call log as read.
     */
    public static void markCallLogAsRead(Context context, String phoneNumberString) {
        if (context.checkSelfPermission(Manifest.permission.WRITE_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Missing WRITE_CALL_LOG permission; not marking missed calls as read.");
            return;
        }
        ContentValues contentValues = new ContentValues();
        contentValues.put(CallLog.Calls.NEW, 0);
        contentValues.put(CallLog.Calls.IS_READ, 1);

        List<String> selectionArgs = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        where.append(CallLog.Calls.NEW);
        where.append(" = 1 AND ");
        where.append(CallLog.Calls.TYPE);
        where.append(" = ?");
        selectionArgs.add(Integer.toString(CallLog.Calls.MISSED_TYPE));
        if (!TextUtils.isEmpty(phoneNumberString)) {
            where.append(" AND ");
            where.append(CallLog.Calls.NUMBER);
            where.append(" = ?");
            selectionArgs.add(phoneNumberString);
        }
        String[] selectionArgsArray = new String[0];
        try {
            context
                    .getContentResolver()
                    .update(
                            CallLog.Calls.CONTENT_URI,
                            contentValues,
                            where.toString(),
                            selectionArgs.toArray(selectionArgsArray));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "markCallLogAsRead failed", e);
        }
    }

    private static Uri makeResourceUri(Context context, int resourceId) {
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .encodedAuthority(context.getBasePackageName())
                .appendEncodedPath(String.valueOf(resourceId))
                .build();
    }

}
