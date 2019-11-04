/**
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

package com.android.car.radio.bands;

import android.hardware.radio.ProgramSelector;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.android.car.broadcastradio.support.platform.ProgramSelectorExt;
import com.android.car.radio.platform.RadioTunerExt;
import com.android.car.radio.platform.RadioTunerExt.TuneCallback;
import com.android.car.radio.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Representation of program type (band); i.e. AM, FM, DAB.
 *
 * It's OK to use == operator between these objects, as a given program type
 * has only one instance per process.
 */
public abstract class ProgramType implements Parcelable {
    private static final String TAG = "BcRadioApp.ProgramType";

    /** {@see #TypeId} */
    public static final int ID_AM = 1;

    /** {@see #TypeId} */
    public static final int ID_FM = 2;

    /** {@see #TypeId} */
    public static final int ID_DAB = 3;

    /**
     * Numeric identifier of program type, for use with switch statements.
     */
    @IntDef(value = {
        ID_AM,
        ID_FM,
        ID_DAB,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface TypeId {}

    /** AM program type */
    public static final ProgramType AM = new AMProgramType(ID_AM);

    /** FM program type */
    public static final ProgramType FM = new FMProgramType(ID_FM);

    /** DAB program type */
    public static final ProgramType DAB = new DABProgramType(ID_DAB);

    /** Identifier of this program type.
     *
     * {@see #TypeId}
     */
    @TypeId
    public final int id;

    protected ProgramType(@TypeId int id) {
        this.id = id;
    }

    /**
     * Retrieves non-localized, english name of this program type.
     */
    @NonNull
    public abstract String getEnglishName();

    /**
     * Retrieves localized name of this program type.
     */
    @StringRes
    public abstract int getLocalizedName();

    /**
     * Tunes to a default channel from this band.
     *
     * @param tuner Tuner to take action on.
     * @param config Region config (i.e. frequency ranges).
     * @param result Callback for tune success/failure.
     */
    public abstract void tuneToDefault(@NonNull RadioTunerExt tuner, @NonNull RegionConfig config,
            @Nullable TuneCallback result);

    /**
     * Returns program type for a given selector.
     *
     * @param sel ProgramSelector to check.
     * @return program type of a given selector
     */
    public static @Nullable ProgramType fromSelector(@Nullable ProgramSelector sel) {
        if (sel == null) return null;

        int priType = sel.getPrimaryId().getType();
        if (priType == ProgramSelector.IDENTIFIER_TYPE_DAB_SID_EXT) {
            return DAB;
        }
        if (!ProgramSelectorExt.isAmFmProgram(sel)) return null;

        // this is an AM/FM program; let's check whether it's AM or FM
        if (!ProgramSelectorExt.hasId(sel, ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY)) {
            Log.e(TAG, "AM/FM program selector with missing frequency");
            return FM;
        }

        long freq = sel.getFirstId(ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY);
        if (ProgramSelectorExt.isAmFrequency(freq)) return AM;
        if (ProgramSelectorExt.isFmFrequency(freq)) return FM;

        Log.e(TAG, "AM/FM program selector with frequency out of range: " + freq);
        return FM;
    }

    /**
     * Checks, if the partial channel number is actually complete.
     *
     * This takes display format (see {@link #format}) into account, i.e. doesn't require
     * FM trailing zeros (95.5 MHz, not 95500 kHz).
     */
    public abstract boolean isComplete(@NonNull RegionConfig config, int leadingDigits);

    /**
     * Generates full channel selector from its leading digits.
     *
     * The argument must be validated with {@link #isComplete} prior.
     */
    @NonNull
    public abstract ProgramSelector parseDigits(int leadingDigits);

    /**
     * Generates an array stating whether certain digits are append-able to a given channel prefix
     * (so that it's still possible to type in a valid channel afterwards).
     *
     * @param config Regional config.
     * @param leadingDigits Channel prefix.
     * @return an array of length 10, where {@code arr[i] == true} states that it's possible to
     *         append {@code i} to {@code leadingDigits}
     */
    @NonNull
    public abstract boolean[] getValidAppendices(@NonNull RegionConfig config, int leadingDigits);

    /**
     * Format partial channel number.
     *
     * This is used by manual tuner dialpad to display channel number entered by the user.
     */
    public String format(int leadingDigits) {
        if (leadingDigits < 0) throw new IllegalArgumentException();
        if (leadingDigits == 0) return "";
        return Integer.toString(leadingDigits);
    }

    @Override
    public String toString() {
        return getEnglishName();
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ProgramType)) return false;
        ProgramType other = (ProgramType) obj;
        return other.id == id;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<ProgramType> CREATOR =
            new Parcelable.Creator<ProgramType>() {
        public ProgramType createFromParcel(Parcel in) {
            int id = in.readInt();
            switch (id) {
                case ID_AM:
                    return AM;
                case ID_FM:
                    return FM;
                case ID_DAB:
                    return DAB;
                default:
                    Log.w(TAG, "Unknown ProgramType ID: " + id);
                    return null;
            }
        }

        public ProgramType[] newArray(int size) {
            return new ProgramType[size];
        }
    };
}
