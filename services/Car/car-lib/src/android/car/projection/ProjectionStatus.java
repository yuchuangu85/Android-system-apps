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

package android.car.projection;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.IntArray;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class encapsulates information about projection status and connected mobile devices.
 *
 * <p>Since the number of connected devices expected to be small we include information about
 * connected devices in every status update.
 *
 * @hide
 */
@SystemApi
public final class ProjectionStatus implements Parcelable {
    /** This state indicates that projection is not actively running and no compatible mobile
     * devices available. */
    public static final int PROJECTION_STATE_INACTIVE = 0;

    /** At least one phone connected and ready to project. */
    public static final int PROJECTION_STATE_READY_TO_PROJECT = 1;

    /** Projecting in the foreground */
    public static final int PROJECTION_STATE_ACTIVE_FOREGROUND = 2;

    /** Projection is running in the background */
    public static final int PROJECTION_STATE_ACTIVE_BACKGROUND = 3;

    private static final int PROJECTION_STATE_MAX = PROJECTION_STATE_ACTIVE_BACKGROUND;

    /** This status is used when projection is not actively running */
    public static final int PROJECTION_TRANSPORT_NONE = 0;

    /** This status is used when projection is not actively running */
    public static final int PROJECTION_TRANSPORT_USB = 1;

    /** This status is used when projection is not actively running */
    public static final int PROJECTION_TRANSPORT_WIFI = 2;

    private static final int PROJECTION_TRANSPORT_MAX = PROJECTION_TRANSPORT_WIFI;

    /** @hide */
    @IntDef(value = {
            PROJECTION_TRANSPORT_NONE,
            PROJECTION_TRANSPORT_USB,
            PROJECTION_TRANSPORT_WIFI,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ProjectionTransport {}

    /** @hide */
    @IntDef(value = {
            PROJECTION_STATE_INACTIVE,
            PROJECTION_STATE_READY_TO_PROJECT,
            PROJECTION_STATE_ACTIVE_FOREGROUND,
            PROJECTION_STATE_ACTIVE_BACKGROUND,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ProjectionState {}

    private final String mPackageName;
    private final int mState;
    private final int mTransport;
    private final List<MobileDevice> mConnectedMobileDevices;
    private final Bundle mExtras;

    /** Creator for this class. Required to have in parcelable implementations. */
    public static final Creator<ProjectionStatus> CREATOR = new Creator<ProjectionStatus>() {
        @Override
        public ProjectionStatus createFromParcel(Parcel source) {
            return new ProjectionStatus(source);
        }

        @Override
        public ProjectionStatus[] newArray(int size) {
            return new ProjectionStatus[size];
        }
    };

    private ProjectionStatus(Builder builder) {
        mPackageName = builder.mPackageName;
        mState = builder.mState;
        mTransport = builder.mTransport;
        mConnectedMobileDevices = new ArrayList<>(builder.mMobileDevices);
        mExtras = builder.mExtras == null ? null : new Bundle(builder.mExtras);
    }

    private ProjectionStatus(Parcel source) {
        mPackageName = source.readString();
        mState = source.readInt();
        mTransport = source.readInt();
        mExtras = source.readBundle(getClass().getClassLoader());
        mConnectedMobileDevices = source.createTypedArrayList(MobileDevice.CREATOR);
    }

    /** Parcelable implementation */
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mPackageName);
        dest.writeInt(mState);
        dest.writeInt(mTransport);
        dest.writeBundle(mExtras);
        dest.writeTypedList(mConnectedMobileDevices);
    }

    /** Returns projection state which could be one of the constants starting with
     * {@code #PROJECTION_STATE_}.
     */
    public @ProjectionState int getState() {
        return mState;
    }

    /** Returns package name of the projection receiver app. */
    public @NonNull String getPackageName() {
        return mPackageName;
    }

    /** Returns extra information provided by projection receiver app */
    public @NonNull Bundle getExtras() {
        return mExtras == null ? new Bundle() : new Bundle(mExtras);
    }

    /** Returns true if currently projecting either in the foreground or in the background. */
    public boolean isActive() {
        return mState == PROJECTION_STATE_ACTIVE_BACKGROUND
                || mState == PROJECTION_STATE_ACTIVE_FOREGROUND;
    }

    /** Returns transport which is used for active projection or
     * {@link #PROJECTION_TRANSPORT_NONE} if projection is not running.
     */
    public @ProjectionTransport int getTransport() {
        return mTransport;
    }

    /** Returns a list of currently connected mobile devices. */
    public @NonNull List<MobileDevice> getConnectedMobileDevices() {
        return new ArrayList<>(mConnectedMobileDevices);
    }

    /**
     * Returns new {@link Builder} instance.
     *
     * @param packageName package name that will be associated with this status
     * @param state current projection state, must be one of the {@code PROJECTION_STATE_*}
     */
    @NonNull
    public static Builder builder(String packageName, @ProjectionState int state) {
        return new Builder(packageName, state);
    }

    /** Builder class for {@link ProjectionStatus} */
    public static final class Builder {
        private final int mState;
        private final String mPackageName;
        private int mTransport = PROJECTION_TRANSPORT_NONE;
        private List<MobileDevice> mMobileDevices = new ArrayList<>();
        private Bundle mExtras;

        private Builder(String packageName, @ProjectionState int state) {
            if (packageName == null) {
                throw new IllegalArgumentException("Package name can't be null");
            }
            if (state < 0 || state > PROJECTION_STATE_MAX) {
                throw new IllegalArgumentException("Invalid projection state: " + state);
            }
            mPackageName = packageName;
            mState = state;
        }

        /**
         * Sets the transport which is used for currently projecting phone if any.
         *
         * @param transport transport of current projection, must be one of the
         * {@code PROJECTION_TRANSPORT_*}
         */
        public @NonNull Builder setProjectionTransport(@ProjectionTransport int transport) {
            checkProjectionTransport(transport);
            mTransport = transport;
            return this;
        }

        /**
         * Add connected mobile device
         *
         * @param mobileDevice connected mobile device
         * @return this builder
         */
        public @NonNull Builder addMobileDevice(MobileDevice mobileDevice) {
            mMobileDevices.add(mobileDevice);
            return this;
        }

        /**
         * Add extra information.
         *
         * @param extras may contain an extra information that can be passed from the projection
         * app to the projection status listeners
         * @return this builder
         */
        public @NonNull Builder setExtras(Bundle extras) {
            mExtras = extras;
            return this;
        }

        /** Creates {@link ProjectionStatus} object. */
        public ProjectionStatus build() {
            return new ProjectionStatus(this);
        }
    }

    private static void checkProjectionTransport(@ProjectionTransport int transport) {
        if (transport < 0 || transport > PROJECTION_TRANSPORT_MAX) {
            throw new IllegalArgumentException("Invalid projection transport: " + transport);
        }
    }

    @Override
    public String toString() {
        return "ProjectionStatus{"
                + "mPackageName='" + mPackageName + '\''
                + ", mState=" + mState
                + ", mTransport=" + mTransport
                + ", mConnectedMobileDevices=" + mConnectedMobileDevices
                + (mExtras != null ? " (has extras)" : "")
                + '}';
    }

    /** Class that represents information about connected mobile device. */
    public static final class MobileDevice implements Parcelable {
        private final int mId;
        private final String mName;
        private final int[] mAvailableTransports;
        private final boolean mProjecting;
        private final Bundle mExtras;

        /** Creator for this class. Required to have in parcelable implementations. */
        public static final Creator<MobileDevice> CREATOR = new Creator<MobileDevice>() {
            @Override
            public MobileDevice createFromParcel(Parcel source) {
                return new MobileDevice(source);
            }

            @Override
            public MobileDevice[] newArray(int size) {
                return new MobileDevice[size];
            }
        };

        private MobileDevice(Builder builder) {
            mId = builder.mId;
            mName = builder.mName;
            mAvailableTransports = builder.mAvailableTransports.toArray();
            mProjecting = builder.mProjecting;
            mExtras = builder.mExtras == null ? null : new Bundle(builder.mExtras);
        }

        private MobileDevice(Parcel source) {
            mId = source.readInt();
            mName = source.readString();
            mAvailableTransports = source.createIntArray();
            mProjecting = source.readBoolean();
            mExtras = source.readBundle(getClass().getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mId);
            dest.writeString(mName);
            dest.writeIntArray(mAvailableTransports);
            dest.writeBoolean(mProjecting);
            dest.writeBundle(mExtras);
        }

        /** Returns the device id which uniquely identifies the mobile device within projection  */
        public int getId() {
            return mId;
        }

        /** Returns the name of the device */
        public @NonNull String getName() {
            return mName;
        }

        /** Returns a list of available projection transports. See {@code PROJECTION_TRANSPORT_*}
         * for possible values. */
        public @NonNull List<Integer> getAvailableTransports() {
            List<Integer> transports = new ArrayList<>(mAvailableTransports.length);
            for (int transport : mAvailableTransports) {
                transports.add(transport);
            }
            return transports;
        }

        /** Indicates whether this mobile device is currently projecting */
        public boolean isProjecting() {
            return mProjecting;
        }

        /** Returns extra information for mobile device */
        public @NonNull Bundle getExtras() {
            return mExtras == null ? new Bundle() : new Bundle(mExtras);
        }

        /** Parcelable implementation */
        @Override
        public int describeContents() {
            return 0;
        }

        /**
         * Creates new instance of {@link Builder}
         *
         * @param id uniquely identifies the device
         * @param name name of the connected device
         * @return the instance of {@link Builder}
         */
        public static @NonNull Builder builder(int id, String name) {
            return new Builder(id, name);
        }

        @Override
        public String toString() {
            return "MobileDevice{"
                    + "mId=" + mId
                    + ", mName='" + mName + '\''
                    + ", mAvailableTransports=" + Arrays.toString(mAvailableTransports)
                    + ", mProjecting=" + mProjecting
                    + (mExtras != null ? ", (has extras)" : "")
                    + '}';
        }

        /**
         * Builder class for {@link MobileDevice}
         */
        public static final class Builder {
            private int mId;
            private String mName;
            private IntArray mAvailableTransports = new IntArray();
            private boolean mProjecting;
            private Bundle mExtras;

            private Builder(int id, String name) {
                mId = id;
                if (name == null) {
                    throw new IllegalArgumentException("Name of the device can't be null");
                }
                mName = name;
            }

            /**
             * Add supported transport
             *
             * @param transport supported transport by given device, must be one of the
             * {@code PROJECTION_TRANSPORT_*}
             * @return this builder
             */
            public @NonNull Builder addTransport(@ProjectionTransport int transport) {
                checkProjectionTransport(transport);
                mAvailableTransports.add(transport);
                return this;
            }

            /**
             * Indicate whether the mobile device currently projecting or not.
             *
             * @param projecting {@code True} if this mobile device currently projecting
             * @return this builder
             */
            public @NonNull Builder setProjecting(boolean projecting) {
                mProjecting = projecting;
                return this;
            }

            /**
             * Add extra information for mobile device
             *
             * @param extras provides an arbitrary extra information about this mobile device
             * @return this builder
             */
            public @NonNull Builder setExtras(Bundle extras) {
                mExtras = extras;
                return this;
            }

            /** Creates new instance of {@link MobileDevice} */
            public @NonNull MobileDevice build() {
                return new MobileDevice(this);
            }
        }
    }
}
