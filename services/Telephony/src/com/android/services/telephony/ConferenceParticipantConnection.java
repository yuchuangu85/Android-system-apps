/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.services.telephony;

import android.net.Uri;
import android.telecom.ConferenceParticipant;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionInfo;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;

/**
 * Represents a participant in a conference call.
 */
public class ConferenceParticipantConnection extends Connection {

    /**
     * The user entity URI For the conference participant.
     */
    private final Uri mUserEntity;

    /**
     * The endpoint URI For the conference participant.
     */
    private final Uri mEndpoint;

    /**
     * The connection which owns this participant.
     */
    private final com.android.internal.telephony.Connection mParentConnection;

    /**
     * Creates a new instance.
     *
     * @param participant The conference participant to create the instance for.
     * @param isRemotelyHosted {@code true} if this participant is part of a conference remotely
     *                         hosted on another device, {@code false} otherwise.
     */
    public ConferenceParticipantConnection(
            com.android.internal.telephony.Connection parentConnection,
            ConferenceParticipant participant,
            boolean isRemotelyHosted) {

        mParentConnection = parentConnection;

        int presentation = participant.getParticipantPresentation();
        Uri address;
        if (presentation != PhoneConstants.PRESENTATION_ALLOWED) {
            address = null;
        } else {
            String countryIso = getCountryIso(parentConnection.getCall().getPhone());
            address = ConferenceParticipant.getParticipantAddress(participant.getHandle(),
                    countryIso);
        }
        setAddress(address, presentation);
        setVideoState(parentConnection.getVideoState());
        setCallerDisplayName(participant.getDisplayName(), presentation);

        mUserEntity = participant.getHandle();
        mEndpoint = participant.getEndpoint();

        setCapabilitiesAndProperties(isRemotelyHosted);
    }

    /**
     * Changes the state of the conference participant.
     *
     * @param newState The new state.
     */
    public void updateState(int newState) {
        Log.v(this, "updateState endPoint: %s state: %s", Log.pii(mEndpoint),
                Connection.stateToString(newState));
        if (newState == getState()) {
            return;
        }

        switch (newState) {
            case STATE_INITIALIZING:
                setInitializing();
                break;
            case STATE_RINGING:
                setRinging();
                break;
            case STATE_DIALING:
                setDialing();
                break;
            case STATE_HOLDING:
                setOnHold();
                break;
            case STATE_ACTIVE:
                setActive();
                break;
            case STATE_DISCONNECTED:
                setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
                destroy();
                break;
            default:
                setActive();
        }
    }

    /**
     * Disconnects the current {@code ConferenceParticipantConnection} from the conference.
     * <p>
     * Sends a participant disconnect signal to the associated parent connection.  The participant
     * connection is not disconnected and cleaned up here.  On successful disconnection of the
     * participant, the conference server will send an update to the conference controller
     * indicating the disconnection was successful.
     */
    @Override
    public void onDisconnect() {
        mParentConnection.onDisconnectConferenceParticipant(mUserEntity);
    }

    /**
     * Retrieves the user handle for this connection.
     *
     * @return The userEntity.
     */
    public Uri getUserEntity() {
        return mUserEntity;
    }

    /**
     * Retrieves the endpoint for this connection.
     *
     * @return The endpoint.
     */
    public Uri getEndpoint() {
        return mEndpoint;
    }

    /**
     * Configures the capabilities and properties applicable to this connection.  A
     * conference participant can only be disconnected from a conference since there is not
     * actual connection to the participant which could be split from the conference.
     * @param isRemotelyHosted {@code true} if this participant is part of a conference hosted
     *                         hosted on a remote device, {@code false} otherwise.
     */
    private void setCapabilitiesAndProperties(boolean isRemotelyHosted) {
        int capabilities = CAPABILITY_DISCONNECT_FROM_CONFERENCE;
        setConnectionCapabilities(capabilities);

        if (isRemotelyHosted) {
            setConnectionProperties(PROPERTY_REMOTELY_HOSTED);
        }
    }

    /**
     * Given a {@link Phone} instance, determines the country ISO associated with the phone's
     * subscription.
     *
     * @param phone The phone instance.
     * @return The country ISO.
     */
    private String getCountryIso(Phone phone) {
        if (phone == null) {
            return null;
        }

        int subId = phone.getSubId();

        SubscriptionInfo subInfo = TelecomAccountRegistry.getInstance(null).
                getSubscriptionManager().getActiveSubscriptionInfo(subId);

        if (subInfo == null) {
            return null;
        }
        // The SubscriptionInfo reports ISO country codes in lower case.  Convert to upper case,
        // since ultimately we use this ISO when formatting the CEP phone number, and the phone
        // number formatting library expects uppercase ISO country codes.
        return subInfo.getCountryIso().toUpperCase();
    }

    /**
     * Builds a string representation of this conference participant connection.
     *
     * @return String representation of connection.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ConferenceParticipantConnection objId:");
        sb.append(System.identityHashCode(this));
        sb.append(" endPoint:");
        sb.append(Log.pii(mEndpoint));
        sb.append(" address:");
        sb.append(Log.pii(getAddress()));
        sb.append(" addressPresentation:");
        sb.append(getAddressPresentation());
        sb.append(" parentConnection:");
        sb.append(Log.pii(mParentConnection.getAddress()));
        sb.append(" state:");
        sb.append(Connection.stateToString(getState()));
        sb.append(" connectTime:");
        sb.append(getConnectTimeMillis());
        sb.append(" connectElapsedTime:");
        sb.append(getConnectElapsedTimeMillis());
        sb.append("]");

        return sb.toString();
    }
}
