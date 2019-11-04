package com.android.car.messenger;


import android.os.Parcel;
import android.os.Parcelable;

/**
 * {@link CompositeKey} subclass used to identify Notification info for a sender;
 * it uses a combination of senderContactUri and senderContactName as the secondary key.
 */
public class SenderKey extends CompositeKey implements Parcelable {

    private SenderKey(String deviceAddress, String key) {
        super(deviceAddress, key);
    }

    SenderKey(MapMessage message) {
        // Use a combination of senderName and senderContactUri for key. Ideally we would use
        // only senderContactUri (which is encoded phone no.). However since some phones don't
        // provide these, we fall back to senderName. Since senderName may not be unique, we
        // include senderContactUri also to provide uniqueness in cases it is available.
        this(message.getDeviceAddress(),
                message.getSenderName() + "/" + message.getSenderContactUri());
    }

    @Override
    public String toString() {
        return String.format("SenderKey: %s -- %s", getDeviceAddress(), getSubKey());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(getDeviceAddress());
        dest.writeString(getSubKey());
    }

    /** Creates {@link SenderKey} instances from {@link Parcel} sources. */
    public static final Parcelable.Creator<SenderKey> CREATOR =
            new Parcelable.Creator<SenderKey>() {
                @Override
                public SenderKey createFromParcel(Parcel source) {
                    return new SenderKey(source.readString(), source.readString());
                }

                @Override
                public SenderKey[] newArray(int size) {
                    return new SenderKey[size];
                }
            };

}

