package com.android.phone;

import static com.android.phone.TimeConsumingPreferenceActivity.RESPONSE_ERROR;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.preference.ListPreference;
import android.telephony.CarrierConfigManager;
import android.util.AttributeSet;
import android.util.Log;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;

/**
 * {@link ListPreference} for CLIR (Calling Line Identification Restriction).
 * Right now this is used for "Caller ID" setting.
 */
public class CLIRListPreference extends ListPreference {
    private static final String LOG_TAG = "CLIRListPreference";
    private final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    private final MyHandler mHandler = new MyHandler();
    private Phone mPhone;
    private TimeConsumingPreferenceListener mTcpListener;

    private final String[] mEntries = getContext().getResources()
            .getStringArray(R.array.clir_display_values);
    private final String[] mValues = getContext().getResources()
            .getStringArray(R.array.clir_values);
    private boolean mConfigSupportNetworkDefault;

    int clirArray[];

    public CLIRListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CLIRListPreference(Context context) {
        this(context, null);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        mPhone.setOutgoingCallerIdDisplay(convertValueToCLIRMode(getValue()),
                mHandler.obtainMessage(MyHandler.MESSAGE_SET_CLIR));
        if (mTcpListener != null) {
            mTcpListener.onStarted(this, false);
        }
    }

    /* package */ void init(
            TimeConsumingPreferenceListener listener, boolean skipReading, Phone phone) {
        mPhone = phone;
        mTcpListener = listener;
        mConfigSupportNetworkDefault = PhoneGlobals.getInstance()
                .getCarrierConfigForSubId(mPhone.getSubId())
                .getBoolean(CarrierConfigManager.KEY_SUPPORT_CLIR_NETWORK_DEFAULT_BOOL);
        // When "Network default" is not supported, create entries with remaining two values.
        if (!mConfigSupportNetworkDefault) {
            String[] noNetworkDefaultEntries = {mEntries[CommandsInterface.CLIR_INVOCATION],
                    mEntries[CommandsInterface.CLIR_SUPPRESSION]};
            String[] noNetworkDefaultValues = {mValues[CommandsInterface.CLIR_INVOCATION],
                    mValues[CommandsInterface.CLIR_SUPPRESSION]};
            setEntries(noNetworkDefaultEntries);
            setEntryValues(noNetworkDefaultValues);
        }

        if (!skipReading) {
            Log.i(LOG_TAG, "init: requesting CLIR");
            mPhone.getOutgoingCallerIdDisplay(mHandler.obtainMessage(MyHandler.MESSAGE_GET_CLIR,
                    MyHandler.MESSAGE_GET_CLIR, MyHandler.MESSAGE_GET_CLIR));
            if (mTcpListener != null) {
                mTcpListener.onStarted(this, true);
            }
        }
    }

    /* package */ void handleGetCLIRResult(int tmpClirArray[]) {
        clirArray = tmpClirArray;
        final boolean enabled =
                tmpClirArray[1] == 1 || tmpClirArray[1] == 3 || tmpClirArray[1] == 4;
        setEnabled(enabled);

        // set the value of the preference based upon the clirArgs.
        int value = CommandsInterface.CLIR_DEFAULT;
        switch (tmpClirArray[1]) {
            case 1: // Permanently provisioned
            case 3: // Temporary presentation disallowed
            case 4: // Temporary presentation allowed
                switch (tmpClirArray[0]) {
                    case 1: // CLIR invoked
                        value = CommandsInterface.CLIR_INVOCATION;
                        break;
                    case 2: // CLIR suppressed
                        value = CommandsInterface.CLIR_SUPPRESSION;
                        break;
                    case 0: // Network default
                    default:
                        value = CommandsInterface.CLIR_DEFAULT;
                        break;
                }
                break;
            case 0: // Not Provisioned
            case 2: // Unknown (network error, etc)
            default:
                value = CommandsInterface.CLIR_DEFAULT;
                break;
        }
        value = (!mConfigSupportNetworkDefault && value == CommandsInterface.CLIR_DEFAULT)
                ? CommandsInterface.CLIR_SUPPRESSION : value;

        setValue(mValues[value]);

        // set the string summary to reflect the value
        int summary = R.string.sum_default_caller_id;
        switch (value) {
            case CommandsInterface.CLIR_SUPPRESSION:
                summary = R.string.sum_show_caller_id;
                break;
            case CommandsInterface.CLIR_INVOCATION:
                summary = R.string.sum_hide_caller_id;
                break;
            case CommandsInterface.CLIR_DEFAULT:
                summary = R.string.sum_default_caller_id;
                break;
        }
        setSummary(summary);
    }

    /**
     * When "Network default" is hidden, UI list index(0-1) doesn't match CLIR Mode(0-2 for Modem).
     * In order to send request to Modem, it is necessary to convert value to CLIR Mode.
     * ("Hide" = CommandsInterface.CLIR_INVOCATION, "Show" = CommandsInterface.CLIR_SUPPRESSION)
     *
     * @param String of entry value.
     * @return "CommandInterface.CLIR_*" for Modem.
     */
    private int convertValueToCLIRMode(String value) {
        if (mValues[CommandsInterface.CLIR_INVOCATION].equals(value)) {
            return CommandsInterface.CLIR_INVOCATION;
        } else if (mValues[CommandsInterface.CLIR_SUPPRESSION].equals(value)) {
            return CommandsInterface.CLIR_SUPPRESSION;
        } else {
            return mConfigSupportNetworkDefault ? CommandsInterface.CLIR_DEFAULT :
                    CommandsInterface.CLIR_SUPPRESSION;
        }
    }

    private class MyHandler extends Handler {
        static final int MESSAGE_GET_CLIR = 0;
        static final int MESSAGE_SET_CLIR = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_CLIR:
                    handleGetCLIRResponse(msg);
                    break;
                case MESSAGE_SET_CLIR:
                    handleSetCLIRResponse(msg);
                    break;
            }
        }

        private void handleGetCLIRResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            if (msg.arg2 == MESSAGE_SET_CLIR) {
                mTcpListener.onFinished(CLIRListPreference.this, false);
            } else {
                mTcpListener.onFinished(CLIRListPreference.this, true);
            }
            clirArray = null;
            if (ar.exception != null) {
                Log.i(LOG_TAG, "handleGetCLIRResponse: ar.exception=" + ar.exception);
                mTcpListener.onException(CLIRListPreference.this, (CommandException) ar.exception);
            } else if (ar.userObj instanceof Throwable) {
                Log.i(LOG_TAG, "handleGetCLIRResponse: ar.throwable=" + ar.userObj);
                mTcpListener.onError(CLIRListPreference.this, RESPONSE_ERROR);
            } else {
                int clirArray[] = (int[]) ar.result;
                if (clirArray.length != 2) {
                    mTcpListener.onError(CLIRListPreference.this, RESPONSE_ERROR);
                } else {
                    Log.i(LOG_TAG, "handleGetCLIRResponse: CLIR successfully queried,"
                                + " clirArray[0]=" + clirArray[0]
                                + ", clirArray[1]=" + clirArray[1]);
                    handleGetCLIRResult(clirArray);
                }
            }
        }

        private void handleSetCLIRResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            if (ar.exception != null) {
                if (DBG) Log.d(LOG_TAG, "handleSetCallWaitingResponse: ar.exception="+ar.exception);
                //setEnabled(false);
            }
            if (DBG) Log.d(LOG_TAG, "handleSetCallWaitingResponse: re get");

            mPhone.getOutgoingCallerIdDisplay(obtainMessage(MESSAGE_GET_CLIR,
                    MESSAGE_SET_CLIR, MESSAGE_SET_CLIR, ar.exception));
        }
    }
}
