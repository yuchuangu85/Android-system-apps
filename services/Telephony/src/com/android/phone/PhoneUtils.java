/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.phone;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.VideoProfile;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.CallerInfoAsyncQuery;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.phone.CallGatewayManager.RawGatewayInfo;
import com.android.phone.settings.SuppServicesUiUtil;

import java.util.Arrays;
import java.util.List;

/**
 * Misc utilities for the Phone app.
 */
public class PhoneUtils {
    public static final String EMERGENCY_ACCOUNT_HANDLE_ID = "E";
    private static final String LOG_TAG = "PhoneUtils";
    private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    // Do not check in with VDBG = true, since that may write PII to the system log.
    private static final boolean VDBG = false;

    // Return codes from placeCall()
    public static final int CALL_STATUS_DIALED = 0;  // The number was successfully dialed
    public static final int CALL_STATUS_DIALED_MMI = 1;  // The specified number was an MMI code
    public static final int CALL_STATUS_FAILED = 2;  // The call failed

    // USSD string length for MMI operations
    static final int MIN_USSD_LEN = 1;
    static final int MAX_USSD_LEN = 160;

    /** Define for not a special CNAP string */
    private static final int CNAP_SPECIAL_CASE_NO = -1;

    /**
     * Theme to use for dialogs displayed by utility methods in this class. This is needed
     * because these dialogs are displayed using the application context, which does not resolve
     * the dialog theme correctly.
     */
    private static final int THEME = com.android.internal.R.style.Theme_DeviceDefault_Dialog_Alert;

    /** USSD information used to aggregate all USSD messages */
    private static AlertDialog sUssdDialog = null;
    private static StringBuilder sUssdMsg = new StringBuilder();

    private static final ComponentName PSTN_CONNECTION_SERVICE_COMPONENT =
            new ComponentName("com.android.phone",
                    "com.android.services.telephony.TelephonyConnectionService");

    /** This class is never instantiated. */
    private PhoneUtils() {
    }

    /**
     * For a CDMA phone, advance the call state upon making a new
     * outgoing call.
     *
     * <pre>
     *   IDLE -> SINGLE_ACTIVE
     * or
     *   SINGLE_ACTIVE -> THRWAY_ACTIVE
     * </pre>
     * @param app The phone instance.
     */
    private static void updateCdmaCallStateOnNewOutgoingCall(PhoneGlobals app,
            Connection connection) {
        if (app.cdmaPhoneCallState.getCurrentCallState() ==
            CdmaPhoneCallState.PhoneCallState.IDLE) {
            // This is the first outgoing call. Set the Phone Call State to ACTIVE
            app.cdmaPhoneCallState.setCurrentCallState(
                CdmaPhoneCallState.PhoneCallState.SINGLE_ACTIVE);
        } else {
            // This is the second outgoing call. Set the Phone Call State to 3WAY
            app.cdmaPhoneCallState.setCurrentCallState(
                CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE);

            // TODO: Remove this code.
            //app.getCallModeler().setCdmaOutgoing3WayCall(connection);
        }
    }

    /**
     * @see placeCall below
     */
    public static int placeCall(Context context, Phone phone, String number, Uri contactRef,
            boolean isEmergencyCall) {
        return placeCall(context, phone, number, contactRef, isEmergencyCall,
                CallGatewayManager.EMPTY_INFO, null);
    }

    /**
     * Dial the number using the phone passed in.
     *
     * If the connection is establised, this method issues a sync call
     * that may block to query the caller info.
     * TODO: Change the logic to use the async query.
     *
     * @param context To perform the CallerInfo query.
     * @param phone the Phone object.
     * @param number to be dialed as requested by the user. This is
     * NOT the phone number to connect to. It is used only to build the
     * call card and to update the call log. See above for restrictions.
     * @param contactRef that triggered the call. Typically a 'tel:'
     * uri but can also be a 'content://contacts' one.
     * @param isEmergencyCall indicates that whether or not this is an
     * emergency call
     * @param gatewayUri Is the address used to setup the connection, null
     * if not using a gateway
     * @param callGateway Class for setting gateway data on a successful call.
     *
     * @return either CALL_STATUS_DIALED or CALL_STATUS_FAILED
     */
    public static int placeCall(Context context, Phone phone, String number, Uri contactRef,
            boolean isEmergencyCall, RawGatewayInfo gatewayInfo, CallGatewayManager callGateway) {
        final Uri gatewayUri = gatewayInfo.gatewayUri;

        if (VDBG) {
            log("placeCall()... number: '" + number + "'"
                    + ", GW:'" + gatewayUri + "'"
                    + ", contactRef:" + contactRef
                    + ", isEmergencyCall: " + isEmergencyCall);
        } else {
            log("placeCall()... number: " + toLogSafePhoneNumber(number)
                    + ", GW: " + (gatewayUri != null ? "non-null" : "null")
                    + ", emergency? " + isEmergencyCall);
        }
        final PhoneGlobals app = PhoneGlobals.getInstance();

        boolean useGateway = false;
        if (null != gatewayUri &&
            !isEmergencyCall &&
            PhoneUtils.isRoutableViaGateway(number)) {  // Filter out MMI, OTA and other codes.
            useGateway = true;
        }

        int status = CALL_STATUS_DIALED;
        Connection connection;
        String numberToDial;
        if (useGateway) {
            // TODO: 'tel' should be a constant defined in framework base
            // somewhere (it is in webkit.)
            if (null == gatewayUri || !PhoneAccount.SCHEME_TEL.equals(gatewayUri.getScheme())) {
                Log.e(LOG_TAG, "Unsupported URL:" + gatewayUri);
                return CALL_STATUS_FAILED;
            }

            // We can use getSchemeSpecificPart because we don't allow #
            // in the gateway numbers (treated a fragment delim.) However
            // if we allow more complex gateway numbers sequence (with
            // passwords or whatnot) that use #, this may break.
            // TODO: Need to support MMI codes.
            numberToDial = gatewayUri.getSchemeSpecificPart();
        } else {
            numberToDial = number;
        }

        try {
            connection = app.mCM.dial(phone, numberToDial, VideoProfile.STATE_AUDIO_ONLY);
        } catch (CallStateException ex) {
            // CallStateException means a new outgoing call is not currently
            // possible: either no more call slots exist, or there's another
            // call already in the process of dialing or ringing.
            Log.w(LOG_TAG, "Exception from app.mCM.dial()", ex);
            return CALL_STATUS_FAILED;

            // Note that it's possible for CallManager.dial() to return
            // null *without* throwing an exception; that indicates that
            // we dialed an MMI (see below).
        }

        int phoneType = phone.getPhoneType();

        // On GSM phones, null is returned for MMI codes
        if (null == connection) {
            status = CALL_STATUS_FAILED;
        } else {
            if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                updateCdmaCallStateOnNewOutgoingCall(app, connection);
            }

            if (gatewayUri == null) {
                // phone.dial() succeeded: we're now in a normal phone call.
                // attach the URI to the CallerInfo Object if it is there,
                // otherwise just attach the Uri Reference.
                // if the uri does not have a "content" scheme, then we treat
                // it as if it does NOT have a unique reference.
                String content = context.getContentResolver().SCHEME_CONTENT;
                if ((contactRef != null) && (contactRef.getScheme().equals(content))) {
                    Object userDataObject = connection.getUserData();
                    if (userDataObject == null) {
                        connection.setUserData(contactRef);
                    } else {
                        // TODO: This branch is dead code, we have
                        // just created the connection which has
                        // no user data (null) by default.
                        if (userDataObject instanceof CallerInfo) {
                        ((CallerInfo) userDataObject).contactRefUri = contactRef;
                        } else {
                        ((CallerInfoToken) userDataObject).currentInfo.contactRefUri =
                            contactRef;
                        }
                    }
                }
            }

            startGetCallerInfo(context, connection, null, null, gatewayInfo);
        }

        return status;
    }

    /* package */ static String toLogSafePhoneNumber(String number) {
        // For unknown number, log empty string.
        if (number == null) {
            return "";
        }

        if (VDBG) {
            // When VDBG is true we emit PII.
            return number;
        }

        // Do exactly same thing as Uri#toSafeString() does, which will enable us to compare
        // sanitized phone numbers.
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            if (c == '-' || c == '@' || c == '.') {
                builder.append(c);
            } else {
                builder.append('x');
            }
        }
        return builder.toString();
    }

    /**
     * Handle the MMIInitiate message and put up an alert that lets
     * the user cancel the operation, if applicable.
     *
     * @param context context to get strings.
     * @param mmiCode the MmiCode object being started.
     * @param buttonCallbackMessage message to post when button is clicked.
     * @param previousAlert a previous alert used in this activity.
     * @return the dialog handle
     */
    static Dialog displayMMIInitiate(Context context,
                                          MmiCode mmiCode,
                                          Message buttonCallbackMessage,
                                          Dialog previousAlert) {
        log("displayMMIInitiate: " + android.telecom.Log.pii(mmiCode.toString()));
        if (previousAlert != null) {
            previousAlert.dismiss();
        }

        // The UI paradigm we are using now requests that all dialogs have
        // user interaction, and that any other messages to the user should
        // be by way of Toasts.
        //
        // In adhering to this request, all MMI initiating "OK" dialogs
        // (non-cancelable MMIs) that end up being closed when the MMI
        // completes (thereby showing a completion dialog) are being
        // replaced with Toasts.
        //
        // As a side effect, moving to Toasts for the non-cancelable MMIs
        // also means that buttonCallbackMessage (which was tied into "OK")
        // is no longer invokable for these dialogs.  This is not a problem
        // since the only callback messages we supported were for cancelable
        // MMIs anyway.
        //
        // A cancelable MMI is really just a USSD request. The term
        // "cancelable" here means that we can cancel the request when the
        // system prompts us for a response, NOT while the network is
        // processing the MMI request.  Any request to cancel a USSD while
        // the network is NOT ready for a response may be ignored.
        //
        // With this in mind, we replace the cancelable alert dialog with
        // a progress dialog, displayed until we receive a request from
        // the the network.  For more information, please see the comments
        // in the displayMMIComplete() method below.
        //
        // Anything that is NOT a USSD request is a normal MMI request,
        // which will bring up a toast (desribed above).

        boolean isCancelable = (mmiCode != null) && mmiCode.isCancelable();

        if (!isCancelable) {
            log("displayMMIInitiate: not a USSD code, displaying status toast.");
            CharSequence text = context.getText(R.string.mmiStarted);
            Toast.makeText(context, text, Toast.LENGTH_SHORT)
                .show();
            return null;
        } else {
            log("displayMMIInitiate: running USSD code, displaying intermediate progress.");

            // create the indeterminate progress dialog and display it.
            ProgressDialog pd = new ProgressDialog(context, THEME);
            pd.setMessage(context.getText(R.string.ussdRunning));
            pd.setCancelable(false);
            pd.setIndeterminate(true);
            pd.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

            pd.show();

            return pd;
        }

    }

    /**
     * Handle the MMIComplete message and fire off an intent to display
     * the message.
     *
     * @param context context to get strings.
     * @param mmiCode MMI result.
     * @param previousAlert a previous alert used in this activity.
     */
    static void displayMMIComplete(final Phone phone, Context context, final MmiCode mmiCode,
            Message dismissCallbackMessage,
            AlertDialog previousAlert) {
        final PhoneGlobals app = PhoneGlobals.getInstance();
        CharSequence text;
        int title = 0;  // title for the progress dialog, if needed.
        MmiCode.State state = mmiCode.getState();

        log("displayMMIComplete: state=" + state);

        switch (state) {
            case PENDING:
                // USSD code asking for feedback from user.
                text = mmiCode.getMessage();
                log("displayMMIComplete: using text from PENDING MMI message: '" + text + "'");
                break;
            case CANCELLED:
                text = null;
                break;
            case COMPLETE:
                PersistableBundle b = null;
                if (SubscriptionManager.isValidSubscriptionId(phone.getSubId())) {
                    b = app.getCarrierConfigForSubId(
                            phone.getSubId());
                } else {
                    b = app.getCarrierConfig();
                }

                if (b.getBoolean(CarrierConfigManager.KEY_USE_CALLER_ID_USSD_BOOL)) {
                    text = SuppServicesUiUtil.handleCallerIdUssdResponse(app, context, phone,
                            mmiCode);
                    if (mmiCode.getMessage() != null && !text.equals(mmiCode.getMessage())) {
                        break;
                    }
                }

                if (app.getPUKEntryActivity() != null) {
                    // if an attempt to unPUK the device was made, we specify
                    // the title and the message here.
                    title = com.android.internal.R.string.PinMmi;
                    text = context.getText(R.string.puk_unlocked);
                    break;
                }
                // All other conditions for the COMPLETE mmi state will cause
                // the case to fall through to message logic in common with
                // the FAILED case.

            case FAILED:
                text = mmiCode.getMessage();
                log("displayMMIComplete (failed): using text from MMI message: '" + text + "'");
                break;
            default:
                throw new IllegalStateException("Unexpected MmiCode state: " + state);
        }

        if (previousAlert != null) {
            previousAlert.dismiss();
        }

        // Check to see if a UI exists for the PUK activation.  If it does
        // exist, then it indicates that we're trying to unblock the PUK.
        if ((app.getPUKEntryActivity() != null) && (state == MmiCode.State.COMPLETE)) {
            if (DBG) log("displaying PUK unblocking progress dialog.");

            // create the progress dialog, make sure the flags and type are
            // set correctly.
            ProgressDialog pd = new ProgressDialog(app, THEME);
            pd.setTitle(title);
            pd.setMessage(text);
            pd.setCancelable(false);
            pd.setIndeterminate(true);
            pd.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
            pd.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

            // display the dialog
            pd.show();

            // indicate to the Phone app that the progress dialog has
            // been assigned for the PUK unlock / SIM READY process.
            app.setPukEntryProgressDialog(pd);

        } else if ((app.getPUKEntryActivity() != null) && (state == MmiCode.State.FAILED)) {
            createUssdDialog(app, context, text,
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            // In case of failure to unlock, we'll need to reset the
            // PUK unlock activity, so that the user may try again.
            app.setPukEntryActivity(null);
        } else {
            // In case of failure to unlock, we'll need to reset the
            // PUK unlock activity, so that the user may try again.
            if (app.getPUKEntryActivity() != null) {
                app.setPukEntryActivity(null);
            }

            // A USSD in a pending state means that it is still
            // interacting with the user.
            if (state != MmiCode.State.PENDING) {
                createUssdDialog(app, context, text,
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            } else {
                log("displayMMIComplete: USSD code has requested user input. Constructing input "
                        + "dialog.");

                // USSD MMI code that is interacting with the user.  The
                // basic set of steps is this:
                //   1. User enters a USSD request
                //   2. We recognize the request and displayMMIInitiate
                //      (above) creates a progress dialog.
                //   3. Request returns and we get a PENDING or COMPLETE
                //      message.
                //   4. These MMI messages are caught in the PhoneApp
                //      (onMMIComplete) and the InCallScreen
                //      (mHandler.handleMessage) which bring up this dialog
                //      and closes the original progress dialog,
                //      respectively.
                //   5. If the message is anything other than PENDING,
                //      we are done, and the alert dialog (directly above)
                //      displays the outcome.
                //   6. If the network is requesting more information from
                //      the user, the MMI will be in a PENDING state, and
                //      we display this dialog with the message.
                //   7. User input, or cancel requests result in a return
                //      to step 1.  Keep in mind that this is the only
                //      time that a USSD should be canceled.

                // inflate the layout with the scrolling text area for the dialog.
                ContextThemeWrapper contextThemeWrapper =
                        new ContextThemeWrapper(context, R.style.DialerAlertDialogTheme);
                LayoutInflater inflater = (LayoutInflater) contextThemeWrapper.getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                View dialogView = inflater.inflate(R.layout.dialog_ussd_response, null);

                // get the input field.
                final EditText inputText = (EditText) dialogView.findViewById(R.id.input_field);

                // specify the dialog's click listener, with SEND and CANCEL logic.
                final DialogInterface.OnClickListener mUSSDDialogListener =
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            switch (whichButton) {
                                case DialogInterface.BUTTON_POSITIVE:
                                    // As per spec 24.080, valid length of ussd string
                                    // is 1 - 160. If length is out of the range then
                                    // display toast message & Cancel MMI operation.
                                    if (inputText.length() < MIN_USSD_LEN
                                            || inputText.length() > MAX_USSD_LEN) {
                                        Toast.makeText(app,
                                                app.getResources().getString(R.string.enter_input,
                                                MIN_USSD_LEN, MAX_USSD_LEN),
                                                Toast.LENGTH_LONG).show();
                                        if (mmiCode.isCancelable()) {
                                            mmiCode.cancel();
                                        }
                                    } else {
                                        phone.sendUssdResponse(inputText.getText().toString());
                                    }
                                    break;
                                case DialogInterface.BUTTON_NEGATIVE:
                                    if (mmiCode.isCancelable()) {
                                        mmiCode.cancel();
                                    }
                                    break;
                            }
                        }
                    };

                // build the dialog
                final AlertDialog newDialog = new AlertDialog.Builder(contextThemeWrapper)
                        .setMessage(text)
                        .setView(dialogView)
                        .setPositiveButton(R.string.send_button, mUSSDDialogListener)
                        .setNegativeButton(R.string.cancel, mUSSDDialogListener)
                        .setCancelable(false)
                        .create();

                // attach the key listener to the dialog's input field and make
                // sure focus is set.
                final View.OnKeyListener mUSSDDialogInputListener =
                    new View.OnKeyListener() {
                        public boolean onKey(View v, int keyCode, KeyEvent event) {
                            switch (keyCode) {
                                case KeyEvent.KEYCODE_CALL:
                                case KeyEvent.KEYCODE_ENTER:
                                    if(event.getAction() == KeyEvent.ACTION_DOWN) {
                                        phone.sendUssdResponse(inputText.getText().toString());
                                        newDialog.dismiss();
                                    }
                                    return true;
                            }
                            return false;
                        }
                    };
                inputText.setOnKeyListener(mUSSDDialogInputListener);
                inputText.requestFocus();

                // set the window properties of the dialog
                newDialog.getWindow().setType(
                        WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
                newDialog.getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_DIM_BEHIND);

                // now show the dialog!
                newDialog.show();

                newDialog.getButton(DialogInterface.BUTTON_POSITIVE)
                        .setTextColor(context.getResources().getColor(R.color.dialer_theme_color));
                newDialog.getButton(DialogInterface.BUTTON_NEGATIVE)
                        .setTextColor(context.getResources().getColor(R.color.dialer_theme_color));
            }
        }
    }

    /**
     * It displays the message dialog for user about the mmi code result message.
     *
     * @param app This is {@link PhoneGlobals}
     * @param context Context to get strings.
     * @param text This is message's result.
     * @param windowType The new window type. {@link WindowManager.LayoutParams}.
     */
    public static void createUssdDialog(PhoneGlobals app, Context context, CharSequence text,
            int windowType) {
        log("displayMMIComplete: MMI code has finished running.");

        log("displayMMIComplete: Extended NW displayMMIInitiate (" + text + ")");
        if (text == null || text.length() == 0) {
            return;
        }

        // displaying system alert dialog on the screen instead of
        // using another activity to display the message.  This
        // places the message at the forefront of the UI.

        if (sUssdDialog == null) {
            sUssdDialog = new AlertDialog.Builder(context, THEME)
                    .setPositiveButton(R.string.ok, null)
                    .setCancelable(true)
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            sUssdMsg.setLength(0);
                        }
                    })
                    .create();

            sUssdDialog.getWindow().setType(windowType);
            sUssdDialog.getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        if (sUssdMsg.length() != 0) {
            sUssdMsg
                    .insert(0, "\n")
                    .insert(0, app.getResources().getString(R.string.ussd_dialog_sep))
                    .insert(0, "\n");
        }
        sUssdMsg.insert(0, text);
        sUssdDialog.setMessage(sUssdMsg.toString());
        sUssdDialog.show();
    }

    /**
     * Cancels the current pending MMI operation, if applicable.
     * @return true if we canceled an MMI operation, or false
     *         if the current pending MMI wasn't cancelable
     *         or if there was no current pending MMI at all.
     *
     * @see displayMMIInitiate
     */
    static boolean cancelMmiCode(Phone phone) {
        List<? extends MmiCode> pendingMmis = phone.getPendingMmiCodes();
        int count = pendingMmis.size();
        if (DBG) log("cancelMmiCode: num pending MMIs = " + count);

        boolean canceled = false;
        if (count > 0) {
            // assume that we only have one pending MMI operation active at a time.
            // I don't think it's possible to enter multiple MMI codes concurrently
            // in the phone UI, because during the MMI operation, an Alert panel
            // is displayed, which prevents more MMI code from being entered.
            MmiCode mmiCode = pendingMmis.get(0);
            if (mmiCode.isCancelable()) {
                mmiCode.cancel();
                canceled = true;
            }
        }
        return canceled;
    }

    /**
     * Returns the caller-id info corresponding to the specified Connection.
     * (This is just a simple wrapper around CallerInfo.getCallerInfo(): we
     * extract a phone number from the specified Connection, and feed that
     * number into CallerInfo.getCallerInfo().)
     *
     * The returned CallerInfo may be null in certain error cases, like if the
     * specified Connection was null, or if we weren't able to get a valid
     * phone number from the Connection.
     *
     * Finally, if the getCallerInfo() call did succeed, we save the resulting
     * CallerInfo object in the "userData" field of the Connection.
     *
     * NOTE: This API should be avoided, with preference given to the
     * asynchronous startGetCallerInfo API.
     */
    static CallerInfo getCallerInfo(Context context, Connection c) {
        CallerInfo info = null;

        if (c != null) {
            //See if there is a URI attached.  If there is, this means
            //that there is no CallerInfo queried yet, so we'll need to
            //replace the URI with a full CallerInfo object.
            Object userDataObject = c.getUserData();
            if (userDataObject instanceof Uri) {
                info = CallerInfo.getCallerInfo(context, (Uri) userDataObject);
                if (info != null) {
                    c.setUserData(info);
                }
            } else {
                if (userDataObject instanceof CallerInfoToken) {
                    //temporary result, while query is running
                    info = ((CallerInfoToken) userDataObject).currentInfo;
                } else {
                    //final query result
                    info = (CallerInfo) userDataObject;
                }
                if (info == null) {
                    // No URI, or Existing CallerInfo, so we'll have to make do with
                    // querying a new CallerInfo using the connection's phone number.
                    String number = c.getAddress();

                    if (DBG) log("getCallerInfo: number = " + toLogSafePhoneNumber(number));

                    if (!TextUtils.isEmpty(number)) {
                        info = CallerInfo.getCallerInfo(context, number);
                        if (info != null) {
                            c.setUserData(info);
                        }
                    }
                }
            }
        }
        return info;
    }

    /**
     * Class returned by the startGetCallerInfo call to package a temporary
     * CallerInfo Object, to be superceded by the CallerInfo Object passed
     * into the listener when the query with token mAsyncQueryToken is complete.
     */
    public static class CallerInfoToken {
        /**indicates that there will no longer be updates to this request.*/
        public boolean isFinal;

        public CallerInfo currentInfo;
        public CallerInfoAsyncQuery asyncQuery;
    }

    /**
     * place a temporary callerinfo object in the hands of the caller and notify
     * caller when the actual query is done.
     */
    static CallerInfoToken startGetCallerInfo(Context context, Connection c,
            CallerInfoAsyncQuery.OnQueryCompleteListener listener, Object cookie,
            RawGatewayInfo info) {
        CallerInfoToken cit;

        if (c == null) {
            //TODO: perhaps throw an exception here.
            cit = new CallerInfoToken();
            cit.asyncQuery = null;
            return cit;
        }

        Object userDataObject = c.getUserData();

        // There are now 3 states for the Connection's userData object:
        //
        //   (1) Uri - query has not been executed yet
        //
        //   (2) CallerInfoToken - query is executing, but has not completed.
        //
        //   (3) CallerInfo - query has executed.
        //
        // In each case we have slightly different behaviour:
        //   1. If the query has not been executed yet (Uri or null), we start
        //      query execution asynchronously, and note it by attaching a
        //      CallerInfoToken as the userData.
        //   2. If the query is executing (CallerInfoToken), we've essentially
        //      reached a state where we've received multiple requests for the
        //      same callerInfo.  That means that once the query is complete,
        //      we'll need to execute the additional listener requested.
        //   3. If the query has already been executed (CallerInfo), we just
        //      return the CallerInfo object as expected.
        //   4. Regarding isFinal - there are cases where the CallerInfo object
        //      will not be attached, like when the number is empty (caller id
        //      blocking).  This flag is used to indicate that the
        //      CallerInfoToken object is going to be permanent since no
        //      query results will be returned.  In the case where a query
        //      has been completed, this flag is used to indicate to the caller
        //      that the data will not be updated since it is valid.
        //
        //      Note: For the case where a number is NOT retrievable, we leave
        //      the CallerInfo as null in the CallerInfoToken.  This is
        //      something of a departure from the original code, since the old
        //      code manufactured a CallerInfo object regardless of the query
        //      outcome.  From now on, we will append an empty CallerInfo
        //      object, to mirror previous behaviour, and to avoid Null Pointer
        //      Exceptions.

        if (userDataObject instanceof Uri) {
            // State (1): query has not been executed yet

            //create a dummy callerinfo, populate with what we know from URI.
            cit = new CallerInfoToken();
            cit.currentInfo = new CallerInfo();
            cit.asyncQuery = CallerInfoAsyncQuery.startQuery(QUERY_TOKEN, context,
                    (Uri) userDataObject, sCallerInfoQueryListener, c);
            cit.asyncQuery.addQueryListener(QUERY_TOKEN, listener, cookie);
            cit.isFinal = false;

            c.setUserData(cit);

            if (DBG) log("startGetCallerInfo: query based on Uri: " + userDataObject);

        } else if (userDataObject == null) {
            // No URI, or Existing CallerInfo, so we'll have to make do with
            // querying a new CallerInfo using the connection's phone number.
            String number = c.getAddress();

            if (info != null && info != CallGatewayManager.EMPTY_INFO) {
                // Gateway number, the connection number is actually the gateway number.
                // need to lookup via dialed number.
                number = info.trueNumber;
            }

            if (DBG) {
                log("PhoneUtils.startGetCallerInfo: new query for phone number...");
                log("- number (address): " + toLogSafePhoneNumber(number));
                log("- c: " + c);
                log("- phone: " + c.getCall().getPhone());
                int phoneType = c.getCall().getPhone().getPhoneType();
                log("- phoneType: " + phoneType);
                switch (phoneType) {
                    case PhoneConstants.PHONE_TYPE_NONE: log("  ==> PHONE_TYPE_NONE"); break;
                    case PhoneConstants.PHONE_TYPE_GSM: log("  ==> PHONE_TYPE_GSM"); break;
                    case PhoneConstants.PHONE_TYPE_IMS: log("  ==> PHONE_TYPE_IMS"); break;
                    case PhoneConstants.PHONE_TYPE_CDMA: log("  ==> PHONE_TYPE_CDMA"); break;
                    case PhoneConstants.PHONE_TYPE_SIP: log("  ==> PHONE_TYPE_SIP"); break;
                    case PhoneConstants.PHONE_TYPE_THIRD_PARTY:
                        log("  ==> PHONE_TYPE_THIRD_PARTY");
                        break;
                    default: log("  ==> Unknown phone type"); break;
                }
            }

            cit = new CallerInfoToken();
            cit.currentInfo = new CallerInfo();

            // Store CNAP information retrieved from the Connection (we want to do this
            // here regardless of whether the number is empty or not).
            cit.currentInfo.cnapName =  c.getCnapName();
            cit.currentInfo.name = cit.currentInfo.cnapName; // This can still get overwritten
                                                             // by ContactInfo later
            cit.currentInfo.numberPresentation = c.getNumberPresentation();
            cit.currentInfo.namePresentation = c.getCnapNamePresentation();

            if (VDBG) {
                log("startGetCallerInfo: number = " + number);
                log("startGetCallerInfo: CNAP Info from FW(1): name="
                    + cit.currentInfo.cnapName
                    + ", Name/Number Pres=" + cit.currentInfo.numberPresentation);
            }

            // handling case where number is null (caller id hidden) as well.
            if (!TextUtils.isEmpty(number)) {
                // Check for special CNAP cases and modify the CallerInfo accordingly
                // to be sure we keep the right information to display/log later
                number = modifyForSpecialCnapCases(context, cit.currentInfo, number,
                        cit.currentInfo.numberPresentation);

                cit.currentInfo.phoneNumber = number;
                // For scenarios where we may receive a valid number from the network but a
                // restricted/unavailable presentation, we do not want to perform a contact query
                // (see note on isFinal above). So we set isFinal to true here as well.
                if (cit.currentInfo.numberPresentation != PhoneConstants.PRESENTATION_ALLOWED) {
                    cit.isFinal = true;
                } else {
                    if (DBG) log("==> Actually starting CallerInfoAsyncQuery.startQuery()...");
                    cit.asyncQuery = CallerInfoAsyncQuery.startQuery(QUERY_TOKEN, context,
                            number, sCallerInfoQueryListener, c);
                    cit.asyncQuery.addQueryListener(QUERY_TOKEN, listener, cookie);
                    cit.isFinal = false;
                }
            } else {
                // This is the case where we are querying on a number that
                // is null or empty, like a caller whose caller id is
                // blocked or empty (CLIR).  The previous behaviour was to
                // throw a null CallerInfo object back to the user, but
                // this departure is somewhat cleaner.
                if (DBG) log("startGetCallerInfo: No query to start, send trivial reply.");
                cit.isFinal = true; // please see note on isFinal, above.
            }

            c.setUserData(cit);

            if (DBG) {
                log("startGetCallerInfo: query based on number: " + toLogSafePhoneNumber(number));
            }

        } else if (userDataObject instanceof CallerInfoToken) {
            // State (2): query is executing, but has not completed.

            // just tack on this listener to the queue.
            cit = (CallerInfoToken) userDataObject;

            // handling case where number is null (caller id hidden) as well.
            if (cit.asyncQuery != null) {
                cit.asyncQuery.addQueryListener(QUERY_TOKEN, listener, cookie);

                if (DBG) log("startGetCallerInfo: query already running, adding listener: " +
                        listener.getClass().toString());
            } else {
                // handling case where number/name gets updated later on by the network
                String updatedNumber = c.getAddress();

                if (info != null) {
                    // Gateway number, the connection number is actually the gateway number.
                    // need to lookup via dialed number.
                    updatedNumber = info.trueNumber;
                }

                if (DBG) {
                    log("startGetCallerInfo: updatedNumber initially = "
                            + toLogSafePhoneNumber(updatedNumber));
                }
                if (!TextUtils.isEmpty(updatedNumber)) {
                    // Store CNAP information retrieved from the Connection
                    cit.currentInfo.cnapName =  c.getCnapName();
                    // This can still get overwritten by ContactInfo
                    cit.currentInfo.name = cit.currentInfo.cnapName;
                    cit.currentInfo.numberPresentation = c.getNumberPresentation();
                    cit.currentInfo.namePresentation = c.getCnapNamePresentation();

                    updatedNumber = modifyForSpecialCnapCases(context, cit.currentInfo,
                            updatedNumber, cit.currentInfo.numberPresentation);

                    cit.currentInfo.phoneNumber = updatedNumber;
                    if (DBG) {
                        log("startGetCallerInfo: updatedNumber="
                                + toLogSafePhoneNumber(updatedNumber));
                    }
                    if (VDBG) {
                        log("startGetCallerInfo: CNAP Info from FW(2): name="
                                + cit.currentInfo.cnapName
                                + ", Name/Number Pres=" + cit.currentInfo.numberPresentation);
                    } else if (DBG) {
                        log("startGetCallerInfo: CNAP Info from FW(2)");
                    }
                    // For scenarios where we may receive a valid number from the network but a
                    // restricted/unavailable presentation, we do not want to perform a contact query
                    // (see note on isFinal above). So we set isFinal to true here as well.
                    if (cit.currentInfo.numberPresentation != PhoneConstants.PRESENTATION_ALLOWED) {
                        cit.isFinal = true;
                    } else {
                        cit.asyncQuery = CallerInfoAsyncQuery.startQuery(QUERY_TOKEN, context,
                                updatedNumber, sCallerInfoQueryListener, c);
                        cit.asyncQuery.addQueryListener(QUERY_TOKEN, listener, cookie);
                        cit.isFinal = false;
                    }
                } else {
                    if (DBG) log("startGetCallerInfo: No query to attach to, send trivial reply.");
                    if (cit.currentInfo == null) {
                        cit.currentInfo = new CallerInfo();
                    }
                    // Store CNAP information retrieved from the Connection
                    cit.currentInfo.cnapName = c.getCnapName();  // This can still get
                                                                 // overwritten by ContactInfo
                    cit.currentInfo.name = cit.currentInfo.cnapName;
                    cit.currentInfo.numberPresentation = c.getNumberPresentation();
                    cit.currentInfo.namePresentation = c.getCnapNamePresentation();

                    if (VDBG) {
                        log("startGetCallerInfo: CNAP Info from FW(3): name="
                                + cit.currentInfo.cnapName
                                + ", Name/Number Pres=" + cit.currentInfo.numberPresentation);
                    } else if (DBG) {
                        log("startGetCallerInfo: CNAP Info from FW(3)");
                    }
                    cit.isFinal = true; // please see note on isFinal, above.
                }
            }
        } else {
            // State (3): query is complete.

            // The connection's userDataObject is a full-fledged
            // CallerInfo instance.  Wrap it in a CallerInfoToken and
            // return it to the user.

            cit = new CallerInfoToken();
            cit.currentInfo = (CallerInfo) userDataObject;
            cit.asyncQuery = null;
            cit.isFinal = true;
            // since the query is already done, call the listener.
            if (DBG) log("startGetCallerInfo: query already done, returning CallerInfo");
            if (DBG) log("==> cit.currentInfo = " + cit.currentInfo);
        }
        return cit;
    }

    /**
     * Static CallerInfoAsyncQuery.OnQueryCompleteListener instance that
     * we use with all our CallerInfoAsyncQuery.startQuery() requests.
     */
    private static final int QUERY_TOKEN = -1;
    static CallerInfoAsyncQuery.OnQueryCompleteListener sCallerInfoQueryListener =
        new CallerInfoAsyncQuery.OnQueryCompleteListener () {
            /**
             * When the query completes, we stash the resulting CallerInfo
             * object away in the Connection's "userData" (where it will
             * later be retrieved by the in-call UI.)
             */
            public void onQueryComplete(int token, Object cookie, CallerInfo ci) {
                if (DBG) log("query complete, updating connection.userdata");
                Connection conn = (Connection) cookie;

                // Added a check if CallerInfo is coming from ContactInfo or from Connection.
                // If no ContactInfo, then we want to use CNAP information coming from network
                if (DBG) log("- onQueryComplete: CallerInfo:" + ci);
                if (ci.contactExists || ci.isEmergencyNumber() || ci.isVoiceMailNumber()) {
                    // If the number presentation has not been set by
                    // the ContactInfo, use the one from the
                    // connection.

                    // TODO: Need a new util method to merge the info
                    // from the Connection in a CallerInfo object.
                    // Here 'ci' is a new CallerInfo instance read
                    // from the DB. It has lost all the connection
                    // info preset before the query (see PhoneUtils
                    // line 1334). We should have a method to merge
                    // back into this new instance the info from the
                    // connection object not set by the DB. If the
                    // Connection already has a CallerInfo instance in
                    // userData, then we could use this instance to
                    // fill 'ci' in. The same routine could be used in
                    // PhoneUtils.
                    if (0 == ci.numberPresentation) {
                        ci.numberPresentation = conn.getNumberPresentation();
                    }
                } else {
                    // No matching contact was found for this number.
                    // Return a new CallerInfo based solely on the CNAP
                    // information from the network.

                    CallerInfo newCi = getCallerInfo(null, conn);

                    // ...but copy over the (few) things we care about
                    // from the original CallerInfo object:
                    if (newCi != null) {
                        newCi.phoneNumber = ci.phoneNumber; // To get formatted phone number
                        newCi.geoDescription = ci.geoDescription; // To get geo description string
                        ci = newCi;
                    }
                }

                if (DBG) log("==> Stashing CallerInfo " + ci + " into the connection...");
                conn.setUserData(ci);
            }
        };


    /**
     * Returns a single "name" for the specified given a CallerInfo object.
     * If the name is null, return defaultString as the default value, usually
     * context.getString(R.string.unknown).
     */
    static String getCompactNameFromCallerInfo(CallerInfo ci, Context context) {
        if (DBG) log("getCompactNameFromCallerInfo: info = " + ci);

        String compactName = null;
        if (ci != null) {
            if (TextUtils.isEmpty(ci.name)) {
                // Perform any modifications for special CNAP cases to
                // the phone number being displayed, if applicable.
                compactName = modifyForSpecialCnapCases(context, ci, ci.phoneNumber,
                                                        ci.numberPresentation);
            } else {
                // Don't call modifyForSpecialCnapCases on regular name. See b/2160795.
                compactName = ci.name;
            }
        }

        if ((compactName == null) || (TextUtils.isEmpty(compactName))) {
            // If we're still null/empty here, then check if we have a presentation
            // string that takes precedence that we could return, otherwise display
            // "unknown" string.
            if (ci != null && ci.numberPresentation == PhoneConstants.PRESENTATION_RESTRICTED) {
                compactName = context.getString(R.string.private_num);
            } else if (ci != null && ci.numberPresentation == PhoneConstants.PRESENTATION_PAYPHONE) {
                compactName = context.getString(R.string.payphone);
            } else {
                compactName = context.getString(R.string.unknown);
            }
        }
        if (VDBG) log("getCompactNameFromCallerInfo: compactName=" + compactName);
        return compactName;
    }

    static boolean isInEmergencyCall(CallManager cm) {
        Call fgCall = cm.getActiveFgCall();
        // isIdle includes checks for the DISCONNECTING/DISCONNECTED state.
        if(!fgCall.isIdle()) {
            for (Connection cn : fgCall.getConnections()) {
                if (PhoneNumberUtils.isLocalEmergencyNumber(PhoneGlobals.getInstance(),
                        cn.getAddress())) {
                    return true;
                }
            }
        }
        return false;
    }

    //
    // Misc UI policy helper functions
    //

    /**
     * Based on the input CNAP number string,
     * @return _RESTRICTED or _UNKNOWN for all the special CNAP strings.
     * Otherwise, return CNAP_SPECIAL_CASE_NO.
     */
    private static int checkCnapSpecialCases(String n) {
        if (n.equals("PRIVATE") ||
                n.equals("P") ||
                n.equals("RES")) {
            if (DBG) log("checkCnapSpecialCases, PRIVATE string: " + n);
            return PhoneConstants.PRESENTATION_RESTRICTED;
        } else if (n.equals("UNAVAILABLE") ||
                n.equals("UNKNOWN") ||
                n.equals("UNA") ||
                n.equals("U")) {
            if (DBG) log("checkCnapSpecialCases, UNKNOWN string: " + n);
            return PhoneConstants.PRESENTATION_UNKNOWN;
        } else {
            if (DBG) log("checkCnapSpecialCases, normal str. number: " + n);
            return CNAP_SPECIAL_CASE_NO;
        }
    }

    /**
     * Handles certain "corner cases" for CNAP. When we receive weird phone numbers
     * from the network to indicate different number presentations, convert them to
     * expected number and presentation values within the CallerInfo object.
     * @param number number we use to verify if we are in a corner case
     * @param presentation presentation value used to verify if we are in a corner case
     * @return the new String that should be used for the phone number
     */
    /* package */ static String modifyForSpecialCnapCases(Context context, CallerInfo ci,
            String number, int presentation) {
        // Obviously we return number if ci == null, but still return number if
        // number == null, because in these cases the correct string will still be
        // displayed/logged after this function returns based on the presentation value.
        if (ci == null || number == null) return number;

        if (DBG) {
            log("modifyForSpecialCnapCases: initially, number="
                    + toLogSafePhoneNumber(number)
                    + ", presentation=" + presentation + " ci " + ci);
        }

        // "ABSENT NUMBER" is a possible value we could get from the network as the
        // phone number, so if this happens, change it to "Unknown" in the CallerInfo
        // and fix the presentation to be the same.
        final String[] absentNumberValues =
                context.getResources().getStringArray(R.array.absent_num);
        if (Arrays.asList(absentNumberValues).contains(number)
                && presentation == PhoneConstants.PRESENTATION_ALLOWED) {
            number = context.getString(R.string.unknown);
            ci.numberPresentation = PhoneConstants.PRESENTATION_UNKNOWN;
        }

        // Check for other special "corner cases" for CNAP and fix them similarly. Corner
        // cases only apply if we received an allowed presentation from the network, so check
        // if we think we have an allowed presentation, or if the CallerInfo presentation doesn't
        // match the presentation passed in for verification (meaning we changed it previously
        // because it's a corner case and we're being called from a different entry point).
        if (ci.numberPresentation == PhoneConstants.PRESENTATION_ALLOWED
                || (ci.numberPresentation != presentation
                        && presentation == PhoneConstants.PRESENTATION_ALLOWED)) {
            int cnapSpecialCase = checkCnapSpecialCases(number);
            if (cnapSpecialCase != CNAP_SPECIAL_CASE_NO) {
                // For all special strings, change number & numberPresentation.
                if (cnapSpecialCase == PhoneConstants.PRESENTATION_RESTRICTED) {
                    number = context.getString(R.string.private_num);
                } else if (cnapSpecialCase == PhoneConstants.PRESENTATION_UNKNOWN) {
                    number = context.getString(R.string.unknown);
                }
                if (DBG) {
                    log("SpecialCnap: number=" + toLogSafePhoneNumber(number)
                            + "; presentation now=" + cnapSpecialCase);
                }
                ci.numberPresentation = cnapSpecialCase;
            }
        }
        if (DBG) {
            log("modifyForSpecialCnapCases: returning number string="
                    + toLogSafePhoneNumber(number));
        }
        return number;
    }

    //
    // Support for 3rd party phone service providers.
    //

    /**
     * Check if a phone number can be route through a 3rd party
     * gateway. The number must be a global phone number in numerical
     * form (1-800-666-SEXY won't work).
     *
     * MMI codes and the like cannot be used as a dial number for the
     * gateway either.
     *
     * @param number To be dialed via a 3rd party gateway.
     * @return true If the number can be routed through the 3rd party network.
     */
    private static boolean isRoutableViaGateway(String number) {
        if (TextUtils.isEmpty(number)) {
            return false;
        }
        number = PhoneNumberUtils.stripSeparators(number);
        if (!number.equals(PhoneNumberUtils.convertKeypadLettersToDigits(number))) {
            return false;
        }
        number = PhoneNumberUtils.extractNetworkPortion(number);
        return PhoneNumberUtils.isGlobalPhoneNumber(number);
    }

    /**
     * Returns whether the phone is in ECM ("Emergency Callback Mode") or not.
     */
    /* package */ static boolean isPhoneInEcm(Phone phone) {
        if ((phone != null) && TelephonyCapabilities.supportsEcm(phone)) {
            return phone.isInEcm();
        }
        return false;
    }

    /**
     * Returns true when the given call is in INCOMING state and there's no foreground phone call,
     * meaning the call is the first real incoming call the phone is having.
     */
    public static boolean isRealIncomingCall(Call.State state) {
        return (state == Call.State.INCOMING && !PhoneGlobals.getInstance().mCM.hasActiveFgCall());
    }

    //
    // General phone and call state debugging/testing code
    //

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    public static PhoneAccountHandle makePstnPhoneAccountHandle(String id) {
        return makePstnPhoneAccountHandleWithPrefix(id, "", false);
    }

    public static PhoneAccountHandle makePstnPhoneAccountHandle(int phoneId) {
        return makePstnPhoneAccountHandle(PhoneFactory.getPhone(phoneId));
    }

    public static PhoneAccountHandle makePstnPhoneAccountHandle(Phone phone) {
        return makePstnPhoneAccountHandleWithPrefix(phone, "", false);
    }

    public static PhoneAccountHandle makePstnPhoneAccountHandleWithPrefix(
            Phone phone, String prefix, boolean isEmergency) {
        // TODO: Should use some sort of special hidden flag to decorate this account as
        // an emergency-only account
        String id = isEmergency ? EMERGENCY_ACCOUNT_HANDLE_ID : prefix +
                String.valueOf(phone.getFullIccSerialNumber());
        return makePstnPhoneAccountHandleWithPrefix(id, prefix, isEmergency);
    }

    public static PhoneAccountHandle makePstnPhoneAccountHandleWithPrefix(
            String id, String prefix, boolean isEmergency) {
        ComponentName pstnConnectionServiceName = getPstnConnectionServiceName();
        return new PhoneAccountHandle(pstnConnectionServiceName, id);
    }

    public static int getSubIdForPhoneAccount(PhoneAccount phoneAccount) {
        if (phoneAccount != null
                && phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
            return getSubIdForPhoneAccountHandle(phoneAccount.getAccountHandle());
        }
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    public static int getSubIdForPhoneAccountHandle(PhoneAccountHandle handle) {
        Phone phone = getPhoneForPhoneAccountHandle(handle);
        if (phone != null) {
            return phone.getSubId();
        }
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    public static Phone getPhoneForPhoneAccountHandle(PhoneAccountHandle handle) {
        if (handle != null && handle.getComponentName().equals(getPstnConnectionServiceName())) {
            return getPhoneFromIccId(handle.getId());
        }
        return null;
    }

    /**
     * Determine if a given phone account corresponds to an active SIM
     *
     * @param sm An instance of the subscription manager so it is not recreated for each calling of
     * this method.
     * @param handle The handle for the phone account to check
     * @return {@code true} If there is an active SIM for this phone account,
     * {@code false} otherwise.
     */
    public static boolean isPhoneAccountActive(SubscriptionManager sm, PhoneAccountHandle handle) {
        return sm.getActiveSubscriptionInfoForIccIndex(handle.getId()) != null;
    }

    private static ComponentName getPstnConnectionServiceName() {
        return PSTN_CONNECTION_SERVICE_COMPONENT;
    }

    private static Phone getPhoneFromIccId(String iccId) {
        if (!TextUtils.isEmpty(iccId)) {
            for (Phone phone : PhoneFactory.getPhones()) {
                String phoneIccId = phone.getFullIccSerialNumber();
                if (iccId.equals(phoneIccId)) {
                    return phone;
                }
            }
        }
        return null;
    }

    /**
     * Register ICC status for all phones.
     */
    static final void registerIccStatus(Handler handler, int event) {
        for (Phone phone : PhoneFactory.getPhones()) {
            IccCard sim = phone.getIccCard();
            if (sim != null) {
                if (VDBG) Log.v(LOG_TAG, "register for ICC status, phone " + phone.getPhoneId());
                sim.registerForNetworkLocked(handler, event, phone);
            }
        }
    }

    /**
     * Register ICC status for all phones.
     */
    static final void registerIccStatus(Handler handler, int event, int phoneId) {
        Phone[] phones = PhoneFactory.getPhones();
        IccCard sim = phones[phoneId].getIccCard();
        if (sim != null) {
            if (VDBG) {
                Log.v(LOG_TAG, "register for ICC status, phone " + phones[phoneId].getPhoneId());
            }
            sim.registerForNetworkLocked(handler, event, phones[phoneId]);
        }
    }

    /**
     * Unregister ICC status for a specific phone.
     */
    static final void unregisterIccStatus(Handler handler, int phoneId) {
        Phone[] phones = PhoneFactory.getPhones();
        IccCard sim = phones[phoneId].getIccCard();
        if (sim != null) {
            if (VDBG) {
                Log.v(LOG_TAG, "unregister for ICC status, phone " + phones[phoneId].getPhoneId());
            }
            sim.unregisterForNetworkLocked(handler);
        }
    }

    /**
     * Set the radio power on/off state for all phones.
     *
     * @param enabled true means on, false means off.
     */
    static final void setRadioPower(boolean enabled) {
        for (Phone phone : PhoneFactory.getPhones()) {
            phone.setRadioPower(enabled);
        }
    }
}
