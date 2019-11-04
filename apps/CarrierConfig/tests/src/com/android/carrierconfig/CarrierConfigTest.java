package com.android.carrierconfig;

import android.Manifest;
import android.annotation.NonNull;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.PersistableBundle;
import android.provider.Telephony;
import android.service.carrier.CarrierIdentifier;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.test.InstrumentationTestCase;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.AssertionFailedError;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

public class CarrierConfigTest extends InstrumentationTestCase {
    private static final String TAG = "CarrierConfigTest";

    /**
     * Iterate over all XML files in assets/ and ensure they parse without error.
     */
    public void testAllFilesParse() {
        forEachConfigXml(new ParserChecker() {
            public void check(XmlPullParser parser, String mccmnc) throws XmlPullParserException,
                    IOException {
                PersistableBundle b = DefaultCarrierConfigService.readConfigFromXml(parser,
                        new CarrierIdentifier("001", "001", "Test", "001001123456789", "", ""));
                assertNotNull("got null bundle", b);
            }
        });
    }

    /**
     * Check that the config bundles in XML files have valid filter attributes.
     * This checks the attribute names only.
     */
    public void testFilterValidAttributes() {
        forEachConfigXml(new ParserChecker() {
            public void check(XmlPullParser parser, String mccmnc) throws XmlPullParserException,
                    IOException {
                int event;
                while (((event = parser.next()) != XmlPullParser.END_DOCUMENT)) {
                    if (event == XmlPullParser.START_TAG
                            && "carrier_config".equals(parser.getName())) {
                        for (int i = 0; i < parser.getAttributeCount(); ++i) {
                            String attribute = parser.getAttributeName(i);
                            switch (attribute) {
                                case "mcc":
                                case "mnc":
                                case "gid1":
                                case "gid2":
                                case "spn":
                                case "imsi":
                                case "device":
                                case "cid":
                                case "name":
                                    break;
                                default:
                                    fail("Unknown attribute '" + attribute
                                            + "' at " + parser.getPositionDescription());
                                    break;
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Check that XML files named after mccmnc are those without matching carrier id.
     * If there is a matching carrier id, all configurations should move to carrierid.xml which
     * has a higher matching priority than mccmnc.xml
     */
    public void testCarrierConfigFileNaming() {
        forEachConfigXml(new ParserChecker() {
            public void check(XmlPullParser parser, String mccmnc) throws XmlPullParserException,
                    IOException {
                if (mccmnc == null) {
                    // only check file named after mccmnc
                    return;
                }
                int event;
                while (((event = parser.next()) != XmlPullParser.END_DOCUMENT)) {
                    if (event == XmlPullParser.START_TAG
                            && "carrier_config".equals(parser.getName())) {
                        String mcc = null;
                        String mnc = null;
                        String spn = null;
                        String gid1 = null;
                        String gid2 = null;
                        String imsi = null;
                        for (int i = 0; i < parser.getAttributeCount(); ++i) {
                            String attribute = parser.getAttributeName(i);
                            switch (attribute) {
                                case "mcc":
                                    mcc = parser.getAttributeValue(i);
                                    break;
                                case "mnc":
                                    mnc = parser.getAttributeValue(i);
                                    break;
                                case "gid1":
                                    gid1 = parser.getAttributeValue(i);
                                    break;
                                case "gid2":
                                    gid2 = parser.getAttributeValue(i);
                                    break;
                                case "spn":
                                    spn = parser.getAttributeValue(i);
                                    break;
                                case "imsi":
                                    imsi = parser.getAttributeValue(i);
                                    break;
                                default:
                                    fail("Unknown attribute '" + attribute
                                            + "' at " + parser.getPositionDescription());
                                    break;
                            }
                        }
                        mcc = (mcc != null) ? mcc : mccmnc.substring(0, 3);
                        mnc = (mnc != null) ? mnc : mccmnc.substring(3);
                        // check if there is a valid carrier id
                        int carrierId = getCarrierId(getInstrumentation().getTargetContext(),
                                new CarrierIdentifier(mcc, mnc, spn, imsi, gid1, gid2));
                        if (carrierId != TelephonyManager.UNKNOWN_CARRIER_ID) {
                            fail("unexpected carrier_config_mccmnc.xml with matching carrier id: "
                                    + carrierId + ", please move to carrier_config_carrierid.xml");
                        }
                    }
                }
            }
        });
    }

    /**
     * Tests that the variable names in each XML file match actual keys in CarrierConfigManager.
     */
    public void testVariableNames() {
        final Set<String> varXmlNames = getCarrierConfigXmlNames();
        // organize them into sets by type or unknown
        forEachConfigXml(new ParserChecker() {
            public void check(XmlPullParser parser, String mccmnc) throws XmlPullParserException,
                    IOException {
                int event;
                while (((event = parser.next()) != XmlPullParser.END_DOCUMENT)) {
                    if (event == XmlPullParser.START_TAG) {
                        switch (parser.getName()) {
                            case "int-array":
                            case "string-array":
                                // string-array and int-array require the 'num' attribute
                                final String varNum = parser.getAttributeValue(null, "num");
                                assertNotNull("No 'num' attribute in array: "
                                        + parser.getPositionDescription(), varNum);
                            case "int":
                            case "long":
                            case "boolean":
                            case "string":
                                // NOTE: This doesn't check for other valid Bundle values, but it
                                // is limited to the key types in CarrierConfigManager.
                                final String varName = parser.getAttributeValue(null, "name");
                                assertNotNull("No 'name' attribute: "
                                        + parser.getPositionDescription(), varName);
                                assertTrue("Unknown variable: '" + varName
                                        + "' at " + parser.getPositionDescription(),
                                        varXmlNames.contains(varName));
                                // TODO: Check that the type is correct.
                                break;
                            case "carrier_config_list":
                            case "item":
                            case "carrier_config":
                                // do nothing
                                break;
                            default:
                                fail("unexpected tag: '" + parser.getName()
                                        + "' at " + parser.getPositionDescription());
                                break;
                        }
                    }
                }
            }
        });
    }

    /**
     * Utility for iterating over each XML document in the assets folder.
     *
     * This can be used with {@link #forEachConfigXml} to run checks on each XML document.
     * {@link #check} should {@link #fail} if the test does not pass.
     */
    private interface ParserChecker {
        void check(XmlPullParser parser, String mccmnc) throws XmlPullParserException, IOException;
    }

    /**
     * Utility for iterating over each XML document in the assets folder.
     */
    private void forEachConfigXml(ParserChecker checker) {
        AssetManager assetMgr = getInstrumentation().getTargetContext().getAssets();
        String mccmnc = null;
        try {
            String[] files = assetMgr.list("");
            assertNotNull("failed to list files", files);
            assertTrue("no files", files.length > 0);
            for (String fileName : files) {
                try {
                    if (!fileName.startsWith("carrier_config_")) continue;
                    if (fileName.startsWith("carrier_config_mccmnc_")) {
                        mccmnc = fileName.substring("carrier_config_mccmnc_".length(),
                                fileName.indexOf(".xml"));

                    }
                    XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                    XmlPullParser parser = factory.newPullParser();
                    parser.setInput(assetMgr.open(fileName), "utf-8");

                    checker.check(parser, mccmnc);

                } catch (Throwable e) {
                    throw new AssertionError("Problem in " + fileName + ": " + e.getMessage(), e);
                }
            }
            // Check vendor.xml too
            try {
                Resources res = getInstrumentation().getTargetContext().getResources();
                checker.check(res.getXml(R.xml.vendor), mccmnc);
            } catch (Throwable e) {
                throw new AssertionError("Problem in vendor.xml: " + e.getMessage(), e);
            }
        } catch (IOException e) {
            fail(e.toString());
        }
    }

    /**
     * Get the set of config variable names, as used in XML files.
     */
    private Set<String> getCarrierConfigXmlNames() {
        Set<String> names = new HashSet<>();
        // get values of all KEY_ members of CarrierConfigManager as well as its nested classes.
        names.addAll(getCarrierConfigXmlNames(CarrierConfigManager.class));
        for (Class nested : CarrierConfigManager.class.getDeclaredClasses()) {
            Log.i("CarrierConfigTest", nested.toString());
            if (Modifier.isStatic(nested.getModifiers())) {
                names.addAll(getCarrierConfigXmlNames(nested));
            }
        }
        return names;
    }

    private Set<String> getCarrierConfigXmlNames(Class clazz) {
        // get values of all KEY_ members of clazz
        Field[] fields = clazz.getDeclaredFields();
        HashSet<String> varXmlNames = new HashSet<>();
        for (Field f : fields) {
            if (!f.getName().startsWith("KEY_")) continue;
            if (!Modifier.isStatic(f.getModifiers())) {
                fail("non-static key in " + clazz.getName() + ":" + f.toString());
            }
            try {
                String value = (String) f.get(null);
                varXmlNames.add(value);
            }
            catch (IllegalAccessException e) {
                throw new AssertionError("Failed to get config key: " + e.getMessage(), e);
            }
        }
        assertTrue("Found zero keys", varXmlNames.size() > 0);
        return varXmlNames;
    }

    // helper function to get carrier id from carrierIdentifier
    private int getCarrierId(@NonNull Context context,
                             @NonNull CarrierIdentifier carrierIdentifier) {
        try {
            getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                    Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
            List<String> args = new ArrayList<>();
            args.add(carrierIdentifier.getMcc() + carrierIdentifier.getMnc());
            if (carrierIdentifier.getGid1() != null) {
                args.add(carrierIdentifier.getGid1());
            }
            if (carrierIdentifier.getGid2() != null) {
                args.add(carrierIdentifier.getGid2());
            }
            if (carrierIdentifier.getImsi() != null) {
                args.add(carrierIdentifier.getImsi());
            }
            if (carrierIdentifier.getSpn() != null) {
                args.add(carrierIdentifier.getSpn());
            }
            try (Cursor cursor = context.getContentResolver().query(
                    Telephony.CarrierId.All.CONTENT_URI,
                    /* projection */ null,
                    /* selection */ Telephony.CarrierId.All.MCCMNC + "=? AND "
                            + Telephony.CarrierId.All.GID1
                            + ((carrierIdentifier.getGid1() == null) ? " is NULL" : "=?") + " AND "
                            + Telephony.CarrierId.All.GID2
                            + ((carrierIdentifier.getGid2() == null) ? " is NULL" : "=?") + " AND "
                            + Telephony.CarrierId.All.IMSI_PREFIX_XPATTERN
                            + ((carrierIdentifier.getImsi() == null) ? " is NULL" : "=?") + " AND "
                            + Telephony.CarrierId.All.SPN
                            + ((carrierIdentifier.getSpn() == null) ? " is NULL" : "=?"),
                /* selectionArgs */ args.toArray(new String[args.size()]), null)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        return cursor.getInt(cursor.getColumnIndex(Telephony.CarrierId.CARRIER_ID));
                    }
                }
            }
        } catch (SecurityException e) {
            fail("Should be able to access APIs protected by a permission apps cannot get");
        } finally {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
        return TelephonyManager.UNKNOWN_CARRIER_ID;
    }
}
