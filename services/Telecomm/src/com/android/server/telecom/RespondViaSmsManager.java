/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.server.telecom;

// TODO: Needed for move to system service: import com.android.internal.R;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.telecom.Connection;
import android.telecom.Log;
import android.telecom.Response;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to manage the "Respond via Message" feature for incoming calls.
 */
public class RespondViaSmsManager extends CallsManagerListenerBase {
    private static final String ACTION_MESSAGE_SENT = "com.android.server.telecom.MESSAGE_SENT";

    private static final class MessageSentReceiver extends BroadcastReceiver {
        private final String mContactName;
        private final int mNumMessageParts;
        private int mNumMessagesSent = 0;
        MessageSentReceiver(String contactName, int numMessageParts) {
            mContactName = contactName;
            mNumMessageParts = numMessageParts;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (getResultCode() == Activity.RESULT_OK) {
                mNumMessagesSent++;
                if (mNumMessagesSent == mNumMessageParts) {
                    showMessageResultToast(mContactName, context, true);
                    context.unregisterReceiver(this);
                }
            } else {
                context.unregisterReceiver(this);
                showMessageResultToast(mContactName, context, false);
                Log.w(RespondViaSmsManager.class.getSimpleName(),
                        "Message failed with error %s", getResultCode());
            }
        }
    }

    private final CallsManager mCallsManager;
    private final TelecomSystem.SyncRoot mLock;

    public RespondViaSmsManager(CallsManager callsManager, TelecomSystem.SyncRoot lock) {
        mCallsManager = callsManager;
        mLock = lock;
    }

    /**
     * Read the (customizable) canned responses from SharedPreferences,
     * or from defaults if the user has never actually brought up
     * the Settings UI.
     *
     * The interface of this method is asynchronous since it does disk I/O.
     *
     * @param response An object to receive an async reply, which will be called from
     *                 the main thread.
     * @param context The context.
     */
    public void loadCannedTextMessages(final Response<Void, List<String>> response,
            final Context context) {
        new Thread() {
            @Override
            public void run() {
                Log.d(RespondViaSmsManager.this, "loadCannedResponses() starting");

                // This function guarantees that QuickResponses will be in our
                // SharedPreferences with the proper values considering there may be
                // old QuickResponses in Telephony pre L.
                QuickResponseUtils.maybeMigrateLegacyQuickResponses(context);

                final SharedPreferences prefs = context.getSharedPreferences(
                        QuickResponseUtils.SHARED_PREFERENCES_NAME,
                        Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
                final Resources res = context.getResources();

                final ArrayList<String> textMessages = new ArrayList<>(
                        QuickResponseUtils.NUM_CANNED_RESPONSES);

                // Where the user has changed a quick response back to the same text as the
                // original text, clear the shared pref.  This ensures we always load the resource
                // in the current active language.
                QuickResponseUtils.maybeResetQuickResponses(context, prefs);

                // Note the default values here must agree with the corresponding
                // android:defaultValue attributes in respond_via_sms_settings.xml.
                textMessages.add(0, prefs.getString(QuickResponseUtils.KEY_CANNED_RESPONSE_PREF_1,
                        res.getString(R.string.respond_via_sms_canned_response_1)));
                textMessages.add(1, prefs.getString(QuickResponseUtils.KEY_CANNED_RESPONSE_PREF_2,
                        res.getString(R.string.respond_via_sms_canned_response_2)));
                textMessages.add(2, prefs.getString(QuickResponseUtils.KEY_CANNED_RESPONSE_PREF_3,
                        res.getString(R.string.respond_via_sms_canned_response_3)));
                textMessages.add(3, prefs.getString(QuickResponseUtils.KEY_CANNED_RESPONSE_PREF_4,
                        res.getString(R.string.respond_via_sms_canned_response_4)));

                Log.d(RespondViaSmsManager.this,
                        "loadCannedResponses() completed, found responses: %s",
                        textMessages.toString());

                synchronized (mLock) {
                    response.onResult(null, textMessages);
                }
            }
        }.start();
    }

    @Override
    public void onIncomingCallRejected(Call call, boolean rejectWithMessage, String textMessage) {
        if (rejectWithMessage
                && call.getHandle() != null
                && !call.can(Connection.CAPABILITY_CAN_SEND_RESPONSE_VIA_CONNECTION)) {
            int subId = mCallsManager.getPhoneAccountRegistrar().getSubscriptionIdForPhoneAccount(
                    call.getTargetPhoneAccount());
            rejectCallWithMessage(call.getContext(), call.getHandle().getSchemeSpecificPart(),
                    textMessage, subId, call.getName());
        }
    }

    private static void showMessageResultToast(final String phoneNumber,
            final Context context, boolean success) {
        // ...and show a brief confirmation to the user (since
        // otherwise it's hard to be sure that anything actually
        // happened.)
        final Resources res = context.getResources();
        final String formatString = res.getString(success
                ? R.string.respond_via_sms_confirmation_format
                : R.string.respond_via_sms_failure_format);
        final String confirmationMsg = String.format(formatString, phoneNumber);
        int startingPosition = confirmationMsg.indexOf(phoneNumber);
        int endingPosition = startingPosition + phoneNumber.length();

        Spannable styledConfirmationMsg = new SpannableString(confirmationMsg);
        PhoneNumberUtils.addTtsSpan(styledConfirmationMsg, startingPosition, endingPosition);
        Toast.makeText(context, styledConfirmationMsg,
                Toast.LENGTH_LONG).show();

        // TODO: If the device is locked, this toast won't actually ever
        // be visible!  (That's because we're about to dismiss the call
        // screen, which means that the device will return to the
        // keyguard.  But toasts aren't visible on top of the keyguard.)
        // Possible fixes:
        // (1) Is it possible to allow a specific Toast to be visible
        //     on top of the keyguard?
        // (2) Artificially delay the dismissCallScreen() call by 3
        //     seconds to allow the toast to be seen?
        // (3) Don't use a toast at all; instead use a transient state
        //     of the InCallScreen (perhaps via the InCallUiState
        //     progressIndication feature), and have that state be
        //     visible for 3 seconds before calling dismissCallScreen().
    }

    /**
     * Reject the call with the specified message. If message is null this call is ignored.
     */
    private void rejectCallWithMessage(Context context, String phoneNumber, String textMessage,
            int subId, String contactName) {
        if (TextUtils.isEmpty(textMessage)) {
            Log.w(RespondViaSmsManager.this, "Couldn't send SMS message: empty text message. ");
            return;
        }
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            Log.w(RespondViaSmsManager.this, "Couldn't send SMS message: Invalid SubId: " +
                    subId);
            return;
        }

        SmsManager smsManager = SmsManager.getSmsManagerForSubscriptionId(subId);
        try {
            ArrayList<String> messageParts = smsManager.divideMessage(textMessage);
            ArrayList<PendingIntent> sentIntents = new ArrayList<>(messageParts.size());
            for (int i = 0; i < messageParts.size(); i++) {
                Intent intent = new Intent(ACTION_MESSAGE_SENT);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, i, intent,
                        PendingIntent.FLAG_ONE_SHOT);
                sentIntents.add(pendingIntent);
            }
            MessageSentReceiver receiver = new MessageSentReceiver(
                    !TextUtils.isEmpty(contactName) ? contactName : phoneNumber,
                    messageParts.size());
            context.registerReceiver(receiver, new IntentFilter(ACTION_MESSAGE_SENT));
            smsManager.sendMultipartTextMessageExternal(phoneNumber, null, messageParts,
                    sentIntents/*sentIntent*/, null /*deliveryIntent*/, context.getOpPackageName());
        } catch (IllegalArgumentException e) {
            Log.w(RespondViaSmsManager.this, "Couldn't send SMS message: " +
                    e.getMessage());
        }
    }
}
