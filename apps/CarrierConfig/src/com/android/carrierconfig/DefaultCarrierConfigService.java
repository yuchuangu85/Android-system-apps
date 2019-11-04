package com.android.carrierconfig;

import android.annotation.Nullable;
import android.os.Build;
import android.os.PersistableBundle;
import android.service.carrier.CarrierIdentifier;
import android.service.carrier.CarrierService;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides network overrides for carrier configuration.
 *
 * The configuration available through CarrierConfigManager is a combination of default values,
 * default network overrides, and carrier overrides. The default network overrides are provided by
 * this service. For a given network, we look for a matching XML file in our assets folder, and
 * return the PersistableBundle from that file. Assets are preferred over Resources because resource
 * overlays only support using MCC+MNC and that doesn't work with MVNOs. The only resource file used
 * is vendor.xml, to provide vendor-specific overrides.
 */
public class DefaultCarrierConfigService extends CarrierService {

    private static final String SPN_EMPTY_MATCH = "null";

    private static final String CARRIER_ID_PREFIX = "carrier_config_carrierid_";

    private static final String MCCMNC_PREFIX = "carrier_config_mccmnc_";

    private static final String TAG = "DefaultCarrierConfigService";

    private XmlPullParserFactory mFactory;

    public DefaultCarrierConfigService() {
        Log.d(TAG, "Service created");
        mFactory = null;
    }

    /**
     * Returns per-network overrides for carrier configuration.
     *
     * This returns a carrier config bundle appropriate for the given carrier by reading data from
     * files in our assets folder. Config files in assets folder are carrier-id-indexed
     * {@link TelephonyManager#getSimCarrierId()}. NOTE: config files named after mccmnc
     * are for those without a matching carrier id and should be renamed to carrier id once the
     * missing IDs are added to
     * <a href="https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/master/assets/carrier_list.textpb">carrier id list</a>
     *
     * First, look for file named after
     * carrier_config_carrierid_<carrierid>_<carriername>.xml if carrier id is not
     * {@link TelephonyManager#UNKNOWN_CARRIER_ID}. Note <carriername> is to improve the
     * readability which should not be used to search asset files. If there is no configuration,
     * then we look for a file named after the MCC+MNC of {@code id} as a fallback. Last, we read
     * res/xml/vendor.xml.
     *
     * carrierid.xml doesn't support multiple bundles with filters as each carrier including MVNOs
     * has its own config file named after its carrier id.
     * Both vendor.xml and MCC+MNC.xml files may contain multiple bundles with filters on them.
     * All the matching bundles are flattened to return one carrier config bundle.
     */
    @Override
    public PersistableBundle onLoadConfig(CarrierIdentifier id) {
        Log.d(TAG, "Config being fetched");

        if (id == null) {
            return null;
        }

        PersistableBundle config = new PersistableBundle();
        try {
            synchronized (this) {
                if (mFactory == null) {
                    mFactory = XmlPullParserFactory.newInstance();
                }
            }

            XmlPullParser parser = mFactory.newPullParser();
            if (id.getCarrierId() != TelephonyManager.UNKNOWN_CARRIER_ID) {
                PersistableBundle configByCarrierId = new PersistableBundle();
                PersistableBundle configBySpecificCarrierId = new PersistableBundle();
                PersistableBundle configByMccMncFallBackCarrierId = new PersistableBundle();
                int mccmncCarrierId = TelephonyManager.from(getApplicationContext())
                        .getCarrierIdFromMccMnc(id.getMcc() + id.getMnc());
                for (String file : getApplicationContext().getAssets().list("")) {
                    if (file.startsWith(CARRIER_ID_PREFIX + id.getSpecificCarrierId() + "_")) {
                        parser.setInput(getApplicationContext().getAssets().open(file), "utf-8");
                        configBySpecificCarrierId = readConfigFromXml(parser, null);
                        break;
                    } else if (file.startsWith(CARRIER_ID_PREFIX + id.getCarrierId() + "_")) {
                        parser.setInput(getApplicationContext().getAssets().open(file), "utf-8");
                        configByCarrierId = readConfigFromXml(parser, null);
                    } else if (file.startsWith(CARRIER_ID_PREFIX + mccmncCarrierId + "_")) {
                        parser.setInput(getApplicationContext().getAssets().open(file), "utf-8");
                        configByMccMncFallBackCarrierId = readConfigFromXml(parser, null);
                    }
                }

                // priority: specific carrier id > carrier id > mccmnc fallback carrier id
                if (!configBySpecificCarrierId.isEmpty()) {
                    config = configBySpecificCarrierId;
                } else if (!configByCarrierId.isEmpty()) {
                    config = configByCarrierId;
                } else if (!configByMccMncFallBackCarrierId.isEmpty()) {
                    config = configByMccMncFallBackCarrierId;
                }
            }
            if (config.isEmpty()) {
                // fallback to use mccmnc.xml when there is no carrier id named configuration found.
                parser.setInput(getApplicationContext().getAssets().open(
                        MCCMNC_PREFIX + id.getMcc() + id.getMnc() + ".xml"), "utf-8");
                config = readConfigFromXml(parser, id);
            }

        }
        catch (IOException | XmlPullParserException e) {
            Log.d(TAG, e.toString());
            // We can return an empty config for unknown networks.
            config = new PersistableBundle();
        }

        // Treat vendor.xml as if it were appended to the carrier config file we read.
        XmlPullParser vendorInput = getApplicationContext().getResources().getXml(R.xml.vendor);
        try {
            PersistableBundle vendorConfig = readConfigFromXml(vendorInput, id);
            config.putAll(vendorConfig);
        }
        catch (IOException | XmlPullParserException e) {
            Log.e(TAG, e.toString());
        }

        return config;
    }

    /**
     * Parses an XML document and returns a PersistableBundle.
     *
     * <p>This function iterates over each {@code <carrier_config>} node in the XML document and
     * parses it into a bundle if its filters match {@code id}. XML documents named after carrier id
     * doesn't support filter match as each carrier including MVNOs will have its own config file.
     * The format of XML bundles is defined
     * by {@link PersistableBundle#restoreFromXml}. All the matching bundles will be flattened and
     * returned as a single bundle.</p>
     *
     * <p>Here is an example document in vendor.xml.
     * <pre>{@code
     * <carrier_config_list>
     *     <carrier_config cid="1938" name="verizon">
     *         <boolean name="voicemail_notification_persistent_bool" value="true" />
     *     </carrier_config>
     *     <carrier_config cid="1788" name="sprint">
     *         <boolean name="voicemail_notification_persistent_bool" value="false" />
     *     </carrier_config>
     * </carrier_config_list>
     * }</pre></p>
     *
     * <p>Here is an example document. The second bundle will be applied to the first only if the
     * GID1 is ABCD.
     * <pre>{@code
     * <carrier_config_list>
     *     <carrier_config>
     *         <boolean name="voicemail_notification_persistent_bool" value="true" />
     *     </carrier_config>
     *     <carrier_config gid1="ABCD">
     *         <boolean name="voicemail_notification_persistent_bool" value="false" />
     *     </carrier_config>
     * </carrier_config_list>
     * }</pre></p>
     *
     * @param parser an XmlPullParser pointing at the beginning of the document.
     * @param id the details of the SIM operator used to filter parts of the document. If read from
     *           files named after carrier id, this will be set to {@null code} as no filter match
     *           needed.
     * @return a possibly empty PersistableBundle containing the config values.
     */
    static PersistableBundle readConfigFromXml(XmlPullParser parser, @Nullable CarrierIdentifier id)
            throws IOException, XmlPullParserException {
        PersistableBundle config = new PersistableBundle();

        if (parser == null) {
          return config;
        }

        // Iterate over each <carrier_config> node in the document and add it to the returned
        // bundle if its filters match.
        int event;
        while (((event = parser.next()) != XmlPullParser.END_DOCUMENT)) {
            if (event == XmlPullParser.START_TAG && "carrier_config".equals(parser.getName())) {
                // Skip this fragment if it has filters that don't match.
                if (id != null && !checkFilters(parser, id)) {
                    continue;
                }
                PersistableBundle configFragment = PersistableBundle.restoreFromXml(parser);
                config.putAll(configFragment);
            }
        }

        return config;
    }

    /**
     * Checks to see if an XML node matches carrier filters.
     *
     * <p>This iterates over the attributes of the current tag pointed to by {@code parser} and
     * checks each one against {@code id} or {@link Build.DEVICE}. Attributes that are not specified
     * in the node will not be checked, so a node with no attributes will always return true. The
     * supported filter attributes are,
     * <ul>
     *   <li>mcc: {@link CarrierIdentifier#getMcc}</li>
     *   <li>mnc: {@link CarrierIdentifier#getMnc}</li>
     *   <li>gid1: {@link CarrierIdentifier#getGid1}</li>
     *   <li>gid2: {@link CarrierIdentifier#getGid2}</li>
     *   <li>spn: {@link CarrierIdentifier#getSpn}</li>
     *   <li>imsi: {@link CarrierIdentifier#getImsi}</li>
     *   <li>device: {@link Build.DEVICE}</li>
     *   <li>cid: {@link CarrierIdentifier#getCarrierId()}
     *   or {@link CarrierIdentifier#getSpecificCarrierId()}</li>
     * </ul>
     * </p>
     *
     * <p>
     * The attributes imsi and spn can be expressed as regexp to filter on patterns.
     * The spn attribute can be set to the string "null" to allow matching against a SIM
     * with no spn set.
     * </p>
     *
     * @param parser an XmlPullParser pointing at a START_TAG with the attributes to check.
     * @param id the carrier details to check against.
     * @return false if any XML attribute does not match the corresponding value.
     */
    static boolean checkFilters(XmlPullParser parser, CarrierIdentifier id) {
        boolean result = true;
        for (int i = 0; i < parser.getAttributeCount(); ++i) {
            String attribute = parser.getAttributeName(i);
            String value = parser.getAttributeValue(i);
            switch (attribute) {
                case "mcc":
                    result = result && value.equals(id.getMcc());
                    break;
                case "mnc":
                    result = result && value.equals(id.getMnc());
                    break;
                case "gid1":
                    result = result && value.equalsIgnoreCase(id.getGid1());
                    break;
                case "gid2":
                    result = result && value.equalsIgnoreCase(id.getGid2());
                    break;
                case "spn":
                    result = result && matchOnSP(value, id);
                    break;
                case "imsi":
                    result = result && matchOnImsi(value, id);
                    break;
                case "device":
                    result = result && value.equalsIgnoreCase(Build.DEVICE);
                    break;
                case "cid":
                    result = result && (value.equals(id.getCarrierId())
                            || value.equals(id.getSpecificCarrierId()));
                    break;
                case "name":
                    // name is used together with cid for readability. ignore for filter.
                    break;
                default:
                    Log.e(TAG, "Unknown attribute " + attribute + "=" + value);
                    result = false;
                    break;
            }
        }
        return result;
    }

    /**
     * Check to see if the IMSI expression from the XML matches the IMSI of the
     * Carrier.
     *
     * @param xmlImsi IMSI expression fetched from the resource XML
     * @param id Id of the evaluated CarrierIdentifier
     * @return true if the XML IMSI matches the IMSI of CarrierIdentifier, false
     *         otherwise.
     */
    static boolean matchOnImsi(String xmlImsi, CarrierIdentifier id) {
        boolean matchFound = false;

        String currentImsi = id.getImsi();
        // If we were able to retrieve current IMSI, see if it matches.
        if (currentImsi != null) {
            Pattern imsiPattern = Pattern.compile(xmlImsi, Pattern.CASE_INSENSITIVE);
            Matcher matcher = imsiPattern.matcher(currentImsi);
            matchFound = matcher.matches();
        }
        return matchFound;
    }

    /**
     * Check to see if the service provider name expression from the XML matches the
     * CarrierIdentifier.
     *
     * @param xmlSP SP expression fetched from the resource XML
     * @param id Id of the evaluated CarrierIdentifier
     * @return true if the XML SP matches the phone's SP, false otherwise.
     */
    static boolean matchOnSP(String xmlSP, CarrierIdentifier id) {
        boolean matchFound = false;

        String currentSP = id.getSpn();
        if (SPN_EMPTY_MATCH.equalsIgnoreCase(xmlSP)) {
            if (TextUtils.isEmpty(currentSP)) {
                matchFound = true;
            }
        } else if (currentSP != null) {
            Pattern spPattern = Pattern.compile(xmlSP, Pattern.CASE_INSENSITIVE);
            Matcher matcher = spPattern.matcher(currentSP);
            matchFound = matcher.matches();
        }
        return matchFound;
    }
}
