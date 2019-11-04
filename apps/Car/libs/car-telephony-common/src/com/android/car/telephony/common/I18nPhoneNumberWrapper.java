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

import static com.google.i18n.phonenumbers.PhoneNumberUtil.MatchType.EXACT_MATCH;
import static com.google.i18n.phonenumbers.PhoneNumberUtil.MatchType.NSN_MATCH;
import static com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Wraps the i18n {@link Phonenumber.PhoneNumber} with a raw phone number that creates it. It
 * facilitates the invalid phone number comparison where a raw phone number can't be converted into
 * an i18n phone number.
 */
public class I18nPhoneNumberWrapper implements Parcelable {
    private final Phonenumber.PhoneNumber mI18nPhoneNumber;
    private final String mRawNumber;
    private final String mNumber;

    private I18nPhoneNumberWrapper(String rawNumber,
            @Nullable Phonenumber.PhoneNumber i18nPhoneNumber) {
        mI18nPhoneNumber = i18nPhoneNumber;
        mRawNumber = rawNumber;
        mNumber = (i18nPhoneNumber == null)
                ? rawNumber
                : PhoneNumberUtil.getInstance().format(i18nPhoneNumber, INTERNATIONAL);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof I18nPhoneNumberWrapper) {
            I18nPhoneNumberWrapper other = (I18nPhoneNumberWrapper) obj;
            if (mI18nPhoneNumber != null && other.mI18nPhoneNumber != null) {
                PhoneNumberUtil.MatchType matchType = PhoneNumberUtil.getInstance().isNumberMatch(
                        mI18nPhoneNumber, other.mI18nPhoneNumber);
                return matchType == EXACT_MATCH || matchType == NSN_MATCH;
            } else if (mI18nPhoneNumber == null && other.mI18nPhoneNumber == null) {
                // compare the raw number directly.
                return mRawNumber.equals(other.mRawNumber);
            } else {
                return false;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        if (mI18nPhoneNumber == null) {
            return Objects.hashCode(mRawNumber);
        }
        return Objects.hash(mI18nPhoneNumber);
    }

    /**
     * Returns the unformatted phone number used to create this class.
     */
    public String getRawNumber() {
        return mRawNumber;
    }

    /**
     * Returns the formatted number if the raw number passed to {@link Factory#create(Context,
     * String)} is a valid phone number. Otherwise, returns the raw number.
     *
     * <P>The number is formatted with {@link PhoneNumberUtil.PhoneNumberFormat#INTERNATIONAL
     * international} format.
     */
    public String getNumber() {
        return mNumber;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mRawNumber);
        dest.writeSerializable(mI18nPhoneNumber);
    }

    public static Creator<I18nPhoneNumberWrapper> CREATOR = new Creator<I18nPhoneNumberWrapper>() {
        @Override
        public I18nPhoneNumberWrapper createFromParcel(Parcel source) {
            String rawNumber = source.readString();
            Phonenumber.PhoneNumber i18nPhoneNumber =
                    (Phonenumber.PhoneNumber) source.readSerializable();
            return new I18nPhoneNumberWrapper(rawNumber, i18nPhoneNumber);
        }

        @Override
        public I18nPhoneNumberWrapper[] newArray(int size) {
            return new I18nPhoneNumberWrapper[size];
        }
    };

    /**
     * Caches {@link WeakReference} of {@link I18nPhoneNumberWrapper}s to avoid creating same object
     * over and over again. It will avoid too many instances getting created during contact sync.
     */
    public enum Factory {
        INSTANCE;

        private final Map<String, WeakReference<I18nPhoneNumberWrapper>> mRecycledPool =
                new ConcurrentHashMap<>();
        /**
         * Returns cached {@link I18nPhoneNumberWrapper} for the given {@code rawNumber}. It will
         * create a new instance if not present.
         *
         * @param rawNumber A potential phone number. If it can be parsed as a valid phone number,
         *                  {@link #getNumber()} will return a formatted number.
         */
        public I18nPhoneNumberWrapper get(@NonNull Context context, @NonNull String rawNumber) {
            for (String key : mRecycledPool.keySet()) {
                if (mRecycledPool.get(key).get() == null) {
                    mRecycledPool.remove(key);
                }
            }
            WeakReference<I18nPhoneNumberWrapper> existingReference = mRecycledPool.get(rawNumber);
            I18nPhoneNumberWrapper i18nPhoneNumberWrapper =
                    existingReference == null ? null : existingReference.get();
            if (i18nPhoneNumberWrapper == null) {
                i18nPhoneNumberWrapper = create(context, rawNumber);
                mRecycledPool.put(rawNumber, new WeakReference<>(i18nPhoneNumberWrapper));
                return i18nPhoneNumberWrapper;
            }
            return i18nPhoneNumberWrapper;
        }

        /** Create a new instance. */
        private I18nPhoneNumberWrapper create(@NonNull Context context, @NonNull String rawNumber) {
            Phonenumber.PhoneNumber i18nPhoneNumber = TelecomUtils.createI18nPhoneNumber(context,
                    rawNumber);
            return new I18nPhoneNumberWrapper(rawNumber, i18nPhoneNumber);
        }
    }
}
