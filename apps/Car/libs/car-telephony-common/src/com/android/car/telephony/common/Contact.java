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

import android.content.Context;
import android.database.Cursor;
import android.icu.text.Collator;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates data about a phone Contact entry. Typically loaded from the local Contact store.
 */
public class Contact implements Parcelable, Comparable<Contact> {
    private static final String TAG = "CD.Contact";
    private static final String PHONEBOOK_LABEL = "phonebook_label";
    private static final String PHONEBOOK_LABEL_ALT = "phonebook_label_alt";

    /**
     * Contact belongs to TYPE_LETTER if its display name starts with a letter
     */
    private static final int TYPE_LETTER = 1;

    /**
     * Contact belongs to TYPE_DIGIT if its display name starts with a digit
     */
    private static final int TYPE_DIGIT = 2;

    /**
     * Contact belongs to TYPE_OTHER if it does not belong to TYPE_LETTER or TYPE_DIGIT
     * Such as empty display name or the display name starts with "_"
     */
    private static final int TYPE_OTHER = 3;


    /**
     * A reference to the {@link ContactsContract.Contacts#_ID} that this data belongs to. See
     * {@link ContactsContract.Contacts.Entity#CONTACT_ID}
     */
    private long mId;

    /**
     * Whether this contact entry is starred by user.
     */
    private boolean mIsStarred;

    /**
     * Contact-specific information about whether or not a contact has been pinned by the user at
     * a particular position within the system contact application's user interface.
     */
    private int mPinnedPosition;

    /**
     * All phone numbers of this contact mapping to the unique primary key for the raw data entry.
     */
    private List<PhoneNumber> mPhoneNumbers = new ArrayList<>();

    /**
     * The display name.
     * <p>
     * The standard text shown as the contact's display name, based on the best
     * available information for the contact.
     * </p>
     *
     * @see ContactsContract.CommonDataKinds.Phone#DISPLAY_NAME
     */
    private String mDisplayName;

    /**
     * The alternative display name.
     * <p>
     * An alternative representation of the display name, such as "family name first"
     * instead of "given name first" for Western names.  If an alternative is not
     * available, the values should be the same as {@link #mDisplayName}.
     * </p>
     *
     * @see ContactsContract.CommonDataKinds.Phone#DISPLAY_NAME_ALTERNATIVE
     */
    private String mAltDisplayName;

    /**
     * The phonebook label.
     * <p>
     * For {@link #mDisplayName}s starting with letters, label will be the first character of
     * {@link #mDisplayName}. For {@link #mDisplayName}s starting with numbers, the label will
     * be "#". For {@link #mDisplayName}s starting with other characters, the label will be "...".
     * </p>
     */
    private String mPhoneBookLabel;

    /**
     * The alternative phonebook label.
     * <p>
     * It is similar with {@link #mPhoneBookLabel}. But instead of generating from
     * {@link #mDisplayName}, it will use {@link #mAltDisplayName}.
     * </p>
     */
    private String mPhoneBookLabelAlt;

    /**
     * A URI that can be used to retrieve a thumbnail of the contact's photo.
     */
    private Uri mAvatarThumbnailUri;

    /**
     * A URI that can be used to retrieve the contact's full-size photo.
     */
    private Uri mAvatarUri;

    /**
     * An opaque value that contains hints on how to find the contact if its row id changed
     * as a result of a sync or aggregation. If a contact has multiple phone numbers, all phone
     * numbers are recorded in a single entry and they all have the same look up key in a single
     * load.
     */
    private String mLookupKey;

    /**
     * Whether this contact represents a voice mail.
     */
    private boolean mIsVoiceMail;

    private PhoneNumber mPrimaryPhoneNumber;

    /**
     * Parses a Contact entry for a Cursor loaded from the Contact Database.
     */
    public static Contact fromCursor(Context context, Cursor cursor) {
        int contactIdColumn = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID);
        int starredColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED);
        int pinnedColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PINNED);
        int displayNameColumn = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
        int altDisplayNameColumn = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_ALTERNATIVE);
        int phoneBookLabelColumn = cursor.getColumnIndex(PHONEBOOK_LABEL);
        int phoneBookLabelAltColumn = cursor.getColumnIndex(PHONEBOOK_LABEL_ALT);
        int avatarUriColumn = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI);
        int avatarThumbnailColumn = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI);
        int lookupKeyColumn = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY);

        Contact contact = new Contact();
        contact.mId = cursor.getLong(contactIdColumn);
        contact.mDisplayName = cursor.getString(displayNameColumn);
        contact.mAltDisplayName = cursor.getString(altDisplayNameColumn);
        contact.mPhoneBookLabel = cursor.getString(phoneBookLabelColumn);
        contact.mPhoneBookLabelAlt = cursor.getString(phoneBookLabelAltColumn);

        PhoneNumber number = PhoneNumber.fromCursor(context, cursor);
        contact.mPhoneNumbers.add(number);
        if (number.isPrimary()) {
            contact.mPrimaryPhoneNumber = number;
        }

        contact.mIsStarred = cursor.getInt(starredColumn) > 0;
        contact.mPinnedPosition = cursor.getInt(pinnedColumn);
        contact.mIsVoiceMail = TelecomUtils.isVoicemailNumber(context, number.getNumber());

        String avatarUriStr = cursor.getString(avatarUriColumn);
        contact.mAvatarUri = avatarUriStr == null ? null : Uri.parse(avatarUriStr);

        String avatarThumbnailStringUri = cursor.getString(avatarThumbnailColumn);
        contact.mAvatarThumbnailUri = avatarThumbnailStringUri == null ? null : Uri.parse(
                avatarThumbnailStringUri);

        String lookUpKey = cursor.getString(lookupKeyColumn);
        if (lookUpKey != null) {
            contact.mLookupKey = lookUpKey;
        } else {
            Log.w(TAG, "Look up key is null. Fallback to use display name");
            contact.mLookupKey = contact.mDisplayName;
        }
        return contact;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Contact && mLookupKey.equals(((Contact) obj).mLookupKey);
    }

    @Override
    public int hashCode() {
        return mLookupKey.hashCode();
    }

    @Override
    public String toString() {
        return mDisplayName + mPhoneNumbers;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    /**
     * Returns alternative display name.
     */
    public String getAltDisplayName() {
        return mAltDisplayName;
    }

    /**
     * Returns {@link #mPhoneBookLabel}
     */
    public String getPhonebookLabel() {
        return mPhoneBookLabel;
    }

    /**
     * Returns {@link #mPhoneBookLabelAlt}
     */
    public String getPhonebookLabelAlt() {
        return mPhoneBookLabelAlt;
    }

    public boolean isVoicemail() {
        return mIsVoiceMail;
    }

    @Nullable
    public Uri getAvatarUri() {
        return mAvatarThumbnailUri != null ? mAvatarThumbnailUri : mAvatarUri;
    }

    public String getLookupKey() {
        return mLookupKey;
    }

    public Uri getLookupUri() {
        return ContactsContract.Contacts.getLookupUri(mId, mLookupKey);
    }

    /** Return all phone numbers associated with this contact. */
    public List<PhoneNumber> getNumbers() {
        return mPhoneNumbers;
    }

    /** Return the aggregated contact id. */
    public long getId() {
        return mId;
    }

    public boolean isStarred() {
        return mIsStarred;
    }

    public int getPinnedPosition() {
        return mPinnedPosition;
    }

    /**
     * Merges a Contact entry with another if they represent different numbers of the same contact.
     *
     * @return A merged contact.
     */
    public Contact merge(Contact contact) {
        if (equals(contact)) {
            for (PhoneNumber phoneNumber : contact.mPhoneNumbers) {
                int indexOfPhoneNumber = mPhoneNumbers.indexOf(phoneNumber);
                if (indexOfPhoneNumber < 0) {
                    mPhoneNumbers.add(phoneNumber);
                } else {
                    PhoneNumber existingPhoneNumber = mPhoneNumbers.get(indexOfPhoneNumber);
                    existingPhoneNumber.merge(phoneNumber);
                }
            }
            if (contact.mPrimaryPhoneNumber != null) {
                mPrimaryPhoneNumber = contact.mPrimaryPhoneNumber.merge(mPrimaryPhoneNumber);
            }
        }
        return this;
    }

    /**
     * Looks up a {@link PhoneNumber} of this contact for the given phone number. Returns {@code
     * null} if this contact doesn't contain the given phone number.
     */
    @Nullable
    public PhoneNumber getPhoneNumber(String number) {
        for (PhoneNumber phoneNumber : mPhoneNumbers) {
            if (PhoneNumberUtils.compare(phoneNumber.getNumber(), number)) {
                return phoneNumber;
            }
        }
        return null;
    }

    public PhoneNumber getPrimaryPhoneNumber() {
        return mPrimaryPhoneNumber;
    }

    /** Return if this contact has a primary phone number. */
    public boolean hasPrimaryPhoneNumber() {
        return mPrimaryPhoneNumber != null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mId);
        dest.writeBoolean(mIsStarred);
        dest.writeInt(mPinnedPosition);
        dest.writeInt(mPhoneNumbers.size());
        for (PhoneNumber phoneNumber : mPhoneNumbers) {
            dest.writeParcelable(phoneNumber, flags);
        }
        dest.writeString(mDisplayName);
        dest.writeString(mAltDisplayName);
        dest.writeString(mPhoneBookLabel);
        dest.writeString(mPhoneBookLabelAlt);
        dest.writeParcelable(mAvatarThumbnailUri, 0);
        dest.writeParcelable(mAvatarUri, 0);
        dest.writeString(mLookupKey);
        dest.writeBoolean(mIsVoiceMail);
    }

    public static final Creator<Contact> CREATOR = new Creator<Contact>() {
        @Override
        public Contact createFromParcel(Parcel source) {
            return Contact.fromParcel(source);
        }

        @Override
        public Contact[] newArray(int size) {
            return new Contact[size];
        }
    };

    /** Create {@link Contact} object from saved parcelable. */
    private static Contact fromParcel(Parcel source) {
        Contact contact = new Contact();
        contact.mId = source.readLong();
        contact.mIsStarred = source.readBoolean();
        contact.mPinnedPosition = source.readInt();
        int phoneNumberListLength = source.readInt();
        contact.mPhoneNumbers = new ArrayList<>();
        for (int i = 0; i < phoneNumberListLength; i++) {
            PhoneNumber phoneNumber = source.readParcelable(PhoneNumber.class.getClassLoader());
            contact.mPhoneNumbers.add(phoneNumber);
            if (phoneNumber.isPrimary()) {
                contact.mPrimaryPhoneNumber = phoneNumber;
            }
        }
        contact.mDisplayName = source.readString();
        contact.mAltDisplayName = source.readString();
        contact.mPhoneBookLabel = source.readString();
        contact.mPhoneBookLabelAlt = source.readString();
        contact.mAvatarThumbnailUri = source.readParcelable(Uri.class.getClassLoader());
        contact.mAvatarUri = source.readParcelable(Uri.class.getClassLoader());
        contact.mLookupKey = source.readString();
        contact.mIsVoiceMail = source.readBoolean();
        return contact;
    }

    @Override
    public int compareTo(Contact otherContact) {
        // Use a helper function to classify Contacts
        // and by default, it should be compared by first name order.
        return compareByDisplayName(otherContact);
    }

    /**
     * Compares contacts by their {@link #mDisplayName} in an order of
     * letters, numbers, then special characters.
     */
    public int compareByDisplayName(@NonNull Contact otherContact) {
        return compareNames(mDisplayName, otherContact.getDisplayName(),
                mPhoneBookLabel, otherContact.getPhonebookLabel());
    }

    /**
     * Compares contacts by their {@link #mAltDisplayName} in an order of
     * letters, numbers, then special characters.
     */
    public int compareByAltDisplayName(@NonNull Contact otherContact) {
        return compareNames(mAltDisplayName, otherContact.getAltDisplayName(),
                mPhoneBookLabelAlt, otherContact.getPhonebookLabelAlt());
    }

    /**
     * Compares two strings in an order of letters, numbers, then special characters.
     */
    private int compareNames(String name, String otherName, String label, String otherLabel) {
        int type = getNameType(label);
        int otherType = getNameType(otherLabel);
        if (type != otherType) {
            return Integer.compare(type, otherType);
        }
        Collator collator = Collator.getInstance();
        return collator.compare(name == null ? "" : name, otherName == null ? "" : otherName);
    }

    /**
     * Returns the type of the name string.
     * Types can be {@link #TYPE_LETTER}, {@link #TYPE_DIGIT} and {@link #TYPE_OTHER}.
     */
    private static int getNameType(String label) {
        // A helper function to classify Contacts
        if (!TextUtils.isEmpty(label)) {
            if (Character.isLetter(label.charAt(0))) {
                return TYPE_LETTER;
            }
            if (label.contains("#")) {
                return TYPE_DIGIT;
            }
        }
        return TYPE_OTHER;
    }
}
