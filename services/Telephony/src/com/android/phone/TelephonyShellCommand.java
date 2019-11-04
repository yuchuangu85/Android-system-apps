/*
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

package com.android.phone;

import android.os.Binder;
import android.os.Process;
import android.os.RemoteException;
import android.os.ShellCommand;
import android.os.UserHandle;
import android.telephony.SubscriptionManager;
import android.telephony.emergency.EmergencyNumber;
import android.util.Log;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.emergency.EmergencyNumberTracker;

import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * Takes actions based on the adb commands given by "adb shell cmd phone ...". Be careful, no
 * permission checks have been done before onCommand was called. Make sure any commands processed
 * here also contain the appropriate permissions checks.
 */

public class TelephonyShellCommand extends ShellCommand {

    private static final String LOG_TAG = "TelephonyShellCommand";
    // Don't commit with this true.
    private static final boolean VDBG = true;
    private static final int DEFAULT_PHONE_ID = 0;

    private static final String IMS_SUBCOMMAND = "ims";
    private static final String SMS_SUBCOMMAND = "sms";
    private static final String NUMBER_VERIFICATION_SUBCOMMAND = "numverify";
    private static final String EMERGENCY_NUMBER_TEST_MODE = "emergency-number-test-mode";

    private static final String IMS_SET_CARRIER_SERVICE = "set-ims-service";
    private static final String IMS_GET_CARRIER_SERVICE = "get-ims-service";
    private static final String IMS_ENABLE = "enable";
    private static final String IMS_DISABLE = "disable";

    private static final String SMS_GET_APPS = "get-apps";
    private static final String SMS_GET_DEFAULT_APP = "get-default-app";
    private static final String SMS_SET_DEFAULT_APP = "set-default-app";

    private static final String NUMBER_VERIFICATION_OVERRIDE_PACKAGE = "override-package";
    private static final String NUMBER_VERIFICATION_FAKE_CALL = "fake-call";

    // Take advantage of existing methods that already contain permissions checks when possible.
    private final ITelephony mInterface;

    public TelephonyShellCommand(ITelephony binder) {
        mInterface = binder;
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) {
            return handleDefaultCommands(null);
        }

        switch (cmd) {
            case IMS_SUBCOMMAND: {
                return handleImsCommand();
            }
            case SMS_SUBCOMMAND: {
                return handleSmsCommand();
            }
            case NUMBER_VERIFICATION_SUBCOMMAND:
                return handleNumberVerificationCommand();
            case EMERGENCY_NUMBER_TEST_MODE:
                return handleEmergencyNumberTestModeCommand();
            default: {
                return handleDefaultCommands(cmd);
            }
        }
    }

    @Override
    public void onHelp() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Telephony Commands:");
        pw.println("  help");
        pw.println("    Print this help text.");
        pw.println("  ims");
        pw.println("    IMS Commands.");
        pw.println("  sms");
        pw.println("    SMS Commands.");
        pw.println("  emergency-number-test-mode");
        pw.println("    Emergency Number Test Mode Commands.");
        onHelpIms();
        onHelpSms();
        onHelpEmergencyNumber();
    }

    private void onHelpIms() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("IMS Commands:");
        pw.println("  ims set-ims-service [-s SLOT_ID] (-c | -d) PACKAGE_NAME");
        pw.println("    Sets the ImsService defined in PACKAGE_NAME to to be the bound");
        pw.println("    ImsService. Options are:");
        pw.println("      -s: the slot ID that the ImsService should be bound for. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
        pw.println("      -c: Override the ImsService defined in the carrier configuration.");
        pw.println("      -d: Override the ImsService defined in the device overlay.");
        pw.println("  ims get-ims-service [-s SLOT_ID] [-c | -d]");
        pw.println("    Gets the package name of the currently defined ImsService.");
        pw.println("    Options are:");
        pw.println("      -s: The SIM slot ID for the registered ImsService. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
        pw.println("      -c: The ImsService defined as the carrier configured ImsService.");
        pw.println("      -c: The ImsService defined as the device default ImsService.");
        pw.println("  ims enable [-s SLOT_ID]");
        pw.println("    enables IMS for the SIM slot specified, or for the default voice SIM slot");
        pw.println("    if none is specified.");
        pw.println("  ims disable [-s SLOT_ID]");
        pw.println("    disables IMS for the SIM slot specified, or for the default voice SIM");
        pw.println("    slot if none is specified.");
    }

    private void onHelpSms() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("SMS Commands:");
        pw.println("  sms get-apps [--user USER_ID]");
        pw.println("    Print all SMS apps on a user.");
        pw.println("  sms get-default-app [--user USER_ID]");
        pw.println("    Get the default SMS app.");
        pw.println("  sms set-default-app [--user USER_ID] PACKAGE_NAME");
        pw.println("    Set PACKAGE_NAME as the default SMS app.");
    }


    private void onHelpNumberVerification() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Number verification commands");
        pw.println("  numverify override-package PACKAGE_NAME;");
        pw.println("    Set the authorized package for number verification.");
        pw.println("    Leave the package name blank to reset.");
        pw.println("  numverify fake-call NUMBER;");
        pw.println("    Fake an incoming call from NUMBER. This is for testing. Output will be");
        pw.println("    1 if the call would have been intercepted, 0 otherwise.");
    }

    private void onHelpEmergencyNumber() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Emergency Number Test Mode Commands:");
        pw.println("  emergency-number-test-mode ");
        pw.println("    Add(-a), Clear(-c), Print (-p) or Remove(-r) the emergency number list in"
                + " the test mode");
        pw.println("      -a <emergency number address>: add an emergency number address for the"
                + " test mode, only allows '0'-'9', '*', '#' or '+'.");
        pw.println("      -c: clear the emergency number list in the test mode.");
        pw.println("      -r <emergency number address>: remove an existing emergency number"
                + " address added by the test mode, only allows '0'-'9', '*', '#' or '+'.");
        pw.println("      -p: get the full emergency number list in the test mode.");
    }

    private int handleImsCommand() {
        String arg = getNextArg();
        if (arg == null) {
            onHelpIms();
            return 0;
        }

        switch (arg) {
            case IMS_SET_CARRIER_SERVICE: {
                return handleImsSetServiceCommand();
            }
            case IMS_GET_CARRIER_SERVICE: {
                return handleImsGetServiceCommand();
            }
            case IMS_ENABLE: {
                return handleEnableIms();
            }
            case IMS_DISABLE: {
                return handleDisableIms();
            }
        }

        return -1;
    }

    private int handleEmergencyNumberTestModeCommand() {
        PrintWriter errPw = getErrPrintWriter();
        String opt = getNextOption();
        if (opt == null) {
            onHelpEmergencyNumber();
            return 0;
        }

        switch (opt) {
            case "-a": {
                String emergencyNumberCmd = getNextArgRequired();
                if (emergencyNumberCmd == null
                        || !EmergencyNumber.validateEmergencyNumberAddress(emergencyNumberCmd)) {
                    errPw.println("An emergency number (only allow '0'-'9', '*', '#' or '+') needs"
                            + " to be specified after -a in the command ");
                    return -1;
                }
                try {
                    mInterface.updateEmergencyNumberListTestMode(
                            EmergencyNumberTracker.ADD_EMERGENCY_NUMBER_TEST_MODE,
                            new EmergencyNumber(emergencyNumberCmd, "", "",
                                    EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                                    new ArrayList<String>(),
                                    EmergencyNumber.EMERGENCY_NUMBER_SOURCE_TEST,
                                    EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN));
                } catch (RemoteException ex) {
                    Log.w(LOG_TAG, "emergency-number-test-mode -a " + emergencyNumberCmd
                            + ", error " + ex.getMessage());
                    errPw.println("Exception: " + ex.getMessage());
                    return -1;
                }
                break;
            }
            case "-c": {
                try {
                    mInterface.updateEmergencyNumberListTestMode(
                            EmergencyNumberTracker.RESET_EMERGENCY_NUMBER_TEST_MODE, null);
                } catch (RemoteException ex) {
                    Log.w(LOG_TAG, "emergency-number-test-mode -c " + "error " + ex.getMessage());
                    errPw.println("Exception: " + ex.getMessage());
                    return -1;
                }
                break;
            }
            case "-r": {
                String emergencyNumberCmd = getNextArgRequired();
                if (emergencyNumberCmd == null
                        || !EmergencyNumber.validateEmergencyNumberAddress(emergencyNumberCmd)) {
                    errPw.println("An emergency number (only allow '0'-'9', '*', '#' or '+') needs"
                            + " to be specified after -r in the command ");
                    return -1;
                }
                try {
                    mInterface.updateEmergencyNumberListTestMode(
                            EmergencyNumberTracker.REMOVE_EMERGENCY_NUMBER_TEST_MODE,
                            new EmergencyNumber(emergencyNumberCmd, "", "",
                                    EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                                    new ArrayList<String>(),
                                    EmergencyNumber.EMERGENCY_NUMBER_SOURCE_TEST,
                                    EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN));
                } catch (RemoteException ex) {
                    Log.w(LOG_TAG, "emergency-number-test-mode -r " + emergencyNumberCmd
                            + ", error " + ex.getMessage());
                    errPw.println("Exception: " + ex.getMessage());
                    return -1;
                }
                break;
            }
            case "-p": {
                try {
                    getOutPrintWriter().println(mInterface.getEmergencyNumberListTestMode());
                } catch (RemoteException ex) {
                    Log.w(LOG_TAG, "emergency-number-test-mode -p " + "error " + ex.getMessage());
                    errPw.println("Exception: " + ex.getMessage());
                    return -1;
                }
                break;
            }
            default:
                onHelpEmergencyNumber();
                break;
        }
        return 0;
    }

    private int handleNumberVerificationCommand() {
        String arg = getNextArg();
        if (arg == null) {
            onHelpNumberVerification();
            return 0;
        }

        if (!checkShellUid()) {
            return -1;
        }

        switch (arg) {
            case NUMBER_VERIFICATION_OVERRIDE_PACKAGE: {
                NumberVerificationManager.overrideAuthorizedPackage(getNextArg());
                return 0;
            }
            case NUMBER_VERIFICATION_FAKE_CALL: {
                boolean val = NumberVerificationManager.getInstance()
                        .checkIncomingCall(getNextArg());
                getOutPrintWriter().println(val ? "1" : "0");
                return 0;
            }
        }

        return -1;
    }

    // ims set-ims-service
    private int handleImsSetServiceCommand() {
        PrintWriter errPw = getErrPrintWriter();
        int slotId = getDefaultSlot();
        Boolean isCarrierService = null;

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-s": {
                    try {
                        slotId = Integer.parseInt(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        errPw.println("ims set-ims-service requires an integer as a SLOT_ID.");
                        return -1;
                    }
                    break;
                }
                case "-c": {
                    isCarrierService = true;
                    break;
                }
                case "-d": {
                    isCarrierService = false;
                    break;
                }
            }
        }
        // Mandatory param, either -c or -d
        if (isCarrierService == null) {
            errPw.println("ims set-ims-service requires either \"-c\" or \"-d\" to be set.");
            return -1;
        }

        String packageName = getNextArg();

        try {
            if (packageName == null) {
                packageName = "";
            }
            boolean result = mInterface.setImsService(slotId, isCarrierService, packageName);
            if (VDBG) {
                Log.v(LOG_TAG, "ims set-ims-service -s " + slotId + " "
                        + (isCarrierService ? "-c " : "-d ") + packageName + ", result=" + result);
            }
            getOutPrintWriter().println(result);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "ims set-ims-service -s " + slotId + " "
                    + (isCarrierService ? "-c " : "-d ") + packageName + ", error"
                    + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    // ims get-ims-service
    private int handleImsGetServiceCommand() {
        PrintWriter errPw = getErrPrintWriter();
        int slotId = getDefaultSlot();
        Boolean isCarrierService = null;

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-s": {
                    try {
                        slotId = Integer.parseInt(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        errPw.println("ims set-ims-service requires an integer as a SLOT_ID.");
                        return -1;
                    }
                    break;
                }
                case "-c": {
                    isCarrierService = true;
                    break;
                }
                case "-d": {
                    isCarrierService = false;
                    break;
                }
            }
        }
        // Mandatory param, either -c or -d
        if (isCarrierService == null) {
            errPw.println("ims set-ims-service requires either \"-c\" or \"-d\" to be set.");
            return -1;
        }

        String result;
        try {
            result = mInterface.getImsService(slotId, isCarrierService);
        } catch (RemoteException e) {
            return -1;
        }
        if (VDBG) {
            Log.v(LOG_TAG, "ims get-ims-service -s " + slotId + " "
                    + (isCarrierService ? "-c " : "-d ") + ", returned: " + result);
        }
        getOutPrintWriter().println(result);
        return 0;
    }

    private int handleEnableIms() {
        int slotId = getDefaultSlot();
        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-s": {
                    try {
                        slotId = Integer.parseInt(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        getErrPrintWriter().println("ims enable requires an integer as a SLOT_ID.");
                        return -1;
                    }
                    break;
                }
            }
        }
        try {
            mInterface.enableIms(slotId);
        } catch (RemoteException e) {
            return -1;
        }
        if (VDBG) {
            Log.v(LOG_TAG, "ims enable -s " + slotId);
        }
        return 0;
    }

    private int handleDisableIms() {
        int slotId = getDefaultSlot();
        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-s": {
                    try {
                        slotId = Integer.parseInt(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        getErrPrintWriter().println(
                                "ims disable requires an integer as a SLOT_ID.");
                        return -1;
                    }
                    break;
                }
            }
        }
        try {
            mInterface.disableIms(slotId);
        } catch (RemoteException e) {
            return -1;
        }
        if (VDBG) {
            Log.v(LOG_TAG, "ims disable -s " + slotId);
        }
        return 0;
    }

    private int getDefaultSlot() {
        int slotId = SubscriptionManager.getDefaultVoicePhoneId();
        if (slotId <= SubscriptionManager.INVALID_SIM_SLOT_INDEX
                || slotId == SubscriptionManager.DEFAULT_PHONE_INDEX) {
            // If there is no default, default to slot 0.
            slotId = DEFAULT_PHONE_ID;
        }
        return slotId;
    }

    private int handleSmsCommand() {
        String arg = getNextArg();
        if (arg == null) {
            onHelpSms();
            return 0;
        }

        try {
            switch (arg) {
                case SMS_GET_APPS: {
                    return handleSmsGetApps();
                }
                case SMS_GET_DEFAULT_APP: {
                    return handleSmsGetDefaultApp();
                }
                case SMS_SET_DEFAULT_APP: {
                    return handleSmsSetDefaultApp();
                }
                default:
                    getErrPrintWriter().println("Unknown command " + arg);
            }
        } catch (RemoteException e) {
            getErrPrintWriter().println("RemoteException: " + e.getMessage());
        }

        return -1;
    }

    private int maybeParseUserIdArg() {
        int userId = UserHandle.USER_SYSTEM;
        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "--user": {
                    try {
                        userId = Integer.parseInt(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        getErrPrintWriter().println("Invalid user ID for --user");
                        return -1;
                    }
                    break;
                }
            }
        }
        return userId;
    }

    private int handleSmsGetApps() throws RemoteException {
        final int userId = maybeParseUserIdArg();
        if (userId < 0) {
            return -1;
        }

        for (String packageName : mInterface.getSmsApps(userId)) {
            getOutPrintWriter().println(packageName);
        }
        return 0;
    }

    private int handleSmsGetDefaultApp() throws RemoteException {
        final int userId = maybeParseUserIdArg();
        if (userId < 0) {
            return -1;
        }

        getOutPrintWriter().println(mInterface.getDefaultSmsApp(userId));
        return 0;
    }

    private int handleSmsSetDefaultApp() throws RemoteException {
        final int userId = maybeParseUserIdArg();
        if (userId < 0) {
            return -1;
        }

        String packageName = getNextArgRequired();
        mInterface.setDefaultSmsApp(userId, packageName);
        getOutPrintWriter().println("SMS app set to " + mInterface.getDefaultSmsApp(userId));
        return 0;
    }

    private boolean checkShellUid() {
        // adb can run as root or as shell, depending on whether the device is rooted.
        return Binder.getCallingUid() == Process.SHELL_UID
                || Binder.getCallingUid() == Process.ROOT_UID;
    }
}
