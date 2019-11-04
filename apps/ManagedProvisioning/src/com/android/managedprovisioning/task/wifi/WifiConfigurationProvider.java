/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.task.wifi;

import android.annotation.Nullable;
import android.net.IpConfiguration.ProxySettings;
import android.net.ProxyInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.model.WifiInfo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for configuring a new {@link WifiConfiguration} object from the provisioning
 * parameters represented via {@link WifiInfo}.
 */
public class WifiConfigurationProvider {

    @VisibleForTesting
    static final String WPA = "WPA";
    @VisibleForTesting
    static final String WEP = "WEP";
    @VisibleForTesting
    static final String EAP = "EAP";
    @VisibleForTesting
    static final String NONE = "NONE";
    @VisibleForTesting
    static final char[] PASSWORD = {};
    public static final String KEYSTORE_TYPE_PKCS12 = "PKCS12";
    private static Map<String, Integer> EAP_METHODS = buildEapMethodsMap();
    private static Map<String, Integer> PHASE2_AUTH = buildPhase2AuthMap();

    private static Map<String, Integer> buildEapMethodsMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("PEAP", WifiEnterpriseConfig.Eap.PEAP);
        map.put("TLS", WifiEnterpriseConfig.Eap.TLS);
        map.put("TTLS", WifiEnterpriseConfig.Eap.TTLS);
        map.put("PWD", WifiEnterpriseConfig.Eap.PWD);
        map.put("SIM", WifiEnterpriseConfig.Eap.SIM);
        map.put("AKA", WifiEnterpriseConfig.Eap.AKA);
        map.put("AKA_PRIME", WifiEnterpriseConfig.Eap.AKA_PRIME);
        return map;
    }

    private static Map<String, Integer> buildPhase2AuthMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put(null, WifiEnterpriseConfig.Phase2.NONE);
        map.put("", WifiEnterpriseConfig.Phase2.NONE);
        map.put("NONE", WifiEnterpriseConfig.Phase2.NONE);
        map.put("PAP", WifiEnterpriseConfig.Phase2.PAP);
        map.put("MSCHAP", WifiEnterpriseConfig.Phase2.MSCHAP);
        map.put("MSCHAPV2", WifiEnterpriseConfig.Phase2.MSCHAPV2);
        map.put("GTC", WifiEnterpriseConfig.Phase2.GTC);
        map.put("SIM", WifiEnterpriseConfig.Phase2.SIM);
        map.put("AKA", WifiEnterpriseConfig.Phase2.AKA);
        map.put("AKA_PRIME", WifiEnterpriseConfig.Phase2.AKA_PRIME);
        return map;
    }

    /**
     * Create a {@link WifiConfiguration} object from the internal representation given via
     * {@link WifiInfo}.
     */
    public WifiConfiguration generateWifiConfiguration(WifiInfo wifiInfo) {
        WifiConfiguration wifiConf = new WifiConfiguration();
        wifiConf.SSID = wifiInfo.ssid;
        wifiConf.status = WifiConfiguration.Status.ENABLED;
        wifiConf.hiddenSSID = wifiInfo.hidden;
        wifiConf.userApproved = WifiConfiguration.USER_APPROVED;
        String securityType = wifiInfo.securityType != null ? wifiInfo.securityType : NONE;
        switch (securityType) {
            case WPA:
                updateForWPAConfiguration(wifiConf, wifiInfo.password);
                break;
            case WEP:
                updateForWEPConfiguration(wifiConf, wifiInfo.password);
                break;
            case EAP:
                maybeUpdateForEAPConfiguration(wifiConf, wifiInfo);
                break;
            default: // NONE
                wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                wifiConf.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                break;
        }

        updateForProxy(
                wifiConf,
                wifiInfo.proxyHost,
                wifiInfo.proxyPort,
                wifiInfo.proxyBypassHosts,
                wifiInfo.pacUrl);
        return wifiConf;
    }

    private void maybeUpdateForEAPConfiguration(WifiConfiguration wifiConf, WifiInfo wifiInfo) {
        try {
            maybeUpdateForEAPConfigurationOrThrow(wifiConf, wifiInfo);
        } catch (IOException | CertificateException | NoSuchAlgorithmException
                | UnrecoverableKeyException | KeyStoreException e) {
            ProvisionLogger.loge("Error while reading certificate", e);
        }
    }

    private void maybeUpdateForEAPConfigurationOrThrow(
            WifiConfiguration wifiConf, WifiInfo wifiInfo)
            throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException,
            KeyStoreException, IOException {
        if (!isEAPWifiInfoValid(wifiInfo.eapMethod)) {
            ProvisionLogger.loge("Unknown EAP method: " + wifiInfo.eapMethod);
            return;
        }
        if (!isPhase2AuthWifiInfoValid(wifiInfo.phase2Auth)) {
            ProvisionLogger.loge(
                    "Unknown phase 2 authentication method: " + wifiInfo.phase2Auth);
            return;
        }
        wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X);
        wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);
        WifiEnterpriseConfig wifiEnterpriseConfig = new WifiEnterpriseConfig();
        updateWifiEnterpriseConfigFromWifiInfo(wifiEnterpriseConfig, wifiInfo);
        maybeUpdateClientKeyForEAPConfiguration(wifiEnterpriseConfig, wifiInfo.userCertificate);
        wifiConf.enterpriseConfig = wifiEnterpriseConfig;
    }

    private void updateWifiEnterpriseConfigFromWifiInfo(
            WifiEnterpriseConfig wifiEnterpriseConfig, WifiInfo wifiInfo)
            throws CertificateException, IOException {
        wifiEnterpriseConfig.setEapMethod(getEAPMethodFromString(wifiInfo.eapMethod));
        wifiEnterpriseConfig.setPhase2Method(getPhase2AuthFromString(wifiInfo.phase2Auth));
        wifiEnterpriseConfig.setPassword(wifiInfo.password);
        wifiEnterpriseConfig.setIdentity(wifiInfo.identity);
        wifiEnterpriseConfig.setAnonymousIdentity(wifiInfo.anonymousIdentity);
        wifiEnterpriseConfig.setDomainSuffixMatch(wifiInfo.domain);
        if (!TextUtils.isEmpty(wifiInfo.caCertificate)) {
            wifiEnterpriseConfig.setCaCertificate(buildCACertificate(wifiInfo.caCertificate));
        }
    }

    /**
     * Updates client key information in EAP configuration if the key and certificate from {@code
     * userCertificate} passes {@link #isKeyValidType(Key)} and {@link
     * #isCertificateChainValidType(Certificate[])}.
     */
    private void maybeUpdateClientKeyForEAPConfiguration(WifiEnterpriseConfig wifiEnterpriseConfig,
            String userCertificate)
            throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException,
            UnrecoverableKeyException {
        if (TextUtils.isEmpty(userCertificate)) {
            return;
        }
        KeyStore keyStore = loadKeystoreFromCertificate(userCertificate);
        String alias = findAliasFromKeystore(keyStore);
        if (TextUtils.isEmpty(alias) || !keyStore.isKeyEntry(alias)) {
            return;
        }
        Key key = keyStore.getKey(alias, PASSWORD);
        if (key == null) {
            return;
        }
        if (!isKeyValidType(key)) {
            ProvisionLogger.loge(
                    "Key in user certificate must be non-null and PrivateKey type");
            return;
        }
        Certificate[] certificates = keyStore.getCertificateChain(alias);
        if (certificates == null) {
            return;
        }
        if (!isCertificateChainValidType(certificates)) {
            ProvisionLogger.loge(
                    "All certificates in chain in user certificate must be non-null "
                            + "X509Certificate type");
            return;
        }
        wifiEnterpriseConfig.setClientKeyEntryWithCertificateChain(
                (PrivateKey) key, castX509Certificates(certificates));
    }

    private boolean isCertificateChainValidType(Certificate[] certificates) {
        return !Arrays.stream(certificates).anyMatch(c -> !(c instanceof X509Certificate));
    }

    private boolean isKeyValidType(Key key) {
        return key instanceof PrivateKey;
    }

    private boolean isPhase2AuthWifiInfoValid(String phase2Auth) {
        return PHASE2_AUTH.containsKey(phase2Auth);
    }

    private boolean isEAPWifiInfoValid(String eapMethod) {
        return EAP_METHODS.containsKey(eapMethod);
    }

    private void updateForWPAConfiguration(WifiConfiguration wifiConf, String wifiPassword) {
        wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        wifiConf.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        wifiConf.allowedProtocols.set(WifiConfiguration.Protocol.WPA); // For WPA
        wifiConf.allowedProtocols.set(WifiConfiguration.Protocol.RSN); // For WPA2
        wifiConf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        wifiConf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        wifiConf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        wifiConf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        if (!TextUtils.isEmpty(wifiPassword)) {
            wifiConf.preSharedKey = "\"" + wifiPassword + "\"";
        }
    }

    private void updateForWEPConfiguration(WifiConfiguration wifiConf, String password) {
        wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        wifiConf.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        wifiConf.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
        wifiConf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        wifiConf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
        wifiConf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        wifiConf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        int length = password.length();
        if ((length == 10 || length == 26 || length == 58) && password.matches("[0-9A-Fa-f]*")) {
            wifiConf.wepKeys[0] = password;
        } else {
            wifiConf.wepKeys[0] = '"' + password + '"';
        }
        wifiConf.wepTxKeyIndex = 0;
    }

    /**
     * Keystore must not contain more then one alias.
     */
    @Nullable
    private static String findAliasFromKeystore(KeyStore keyStore)
            throws KeyStoreException, CertificateException {
        List<String> aliases = Collections.list(keyStore.aliases());
        if (aliases.isEmpty()) {
            return null;
        }
        if (aliases.size() != 1) {
            throw new CertificateException(
                    "Configuration must contain only one certificate");
        }
        return aliases.get(0);
    }

    private static KeyStore loadKeystoreFromCertificate(String userCertificate)
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE_PKCS12);
        try (InputStream inputStream = new ByteArrayInputStream(
                Base64.getDecoder().decode(userCertificate
                        .getBytes(StandardCharsets.UTF_8)))) {
            keyStore.load(inputStream, PASSWORD);
        }
        return keyStore;
    }

    /**
     * Casts the given certificate chain to a chain of {@link X509Certificate} objects. Assumes the
     * given certificate chain passes {@link #isCertificateChainValidType(Certificate[])}.
     */
    private static X509Certificate[] castX509Certificates(Certificate[] certificateChain) {
        return Arrays.stream(certificateChain)
                .map(certificate -> (X509Certificate) certificate)
                .toArray(X509Certificate[]::new);
    }

    /**
     * @param caCertificate String representation of CA certificate in the format described at
     * {@link android.app.admin.DevicePolicyManager#EXTRA_PROVISIONING_WIFI_CA_CERTIFICATE}.
     */
    private X509Certificate buildCACertificate(String caCertificate)
            throws CertificateException, IOException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        try (InputStream inputStream = new ByteArrayInputStream(Base64.getDecoder()
                .decode(caCertificate.getBytes(StandardCharsets.UTF_8)))) {
        X509Certificate caCertificateX509 = (X509Certificate) certificateFactory
                .generateCertificate(inputStream);
            return caCertificateX509;
        }
    }

    private void updateForProxy(WifiConfiguration wifiConf, String proxyHost, int proxyPort,
            String proxyBypassHosts, String pacUrl) {
        if (TextUtils.isEmpty(proxyHost) && TextUtils.isEmpty(pacUrl)) {
            return;
        }
        if (!TextUtils.isEmpty(proxyHost)) {
            ProxyInfo proxy = new ProxyInfo(proxyHost, proxyPort, proxyBypassHosts);
            wifiConf.setProxy(ProxySettings.STATIC, proxy);
        } else {
            ProxyInfo proxy = new ProxyInfo(pacUrl);
            wifiConf.setProxy(ProxySettings.PAC, proxy);
        }
    }

    private int getEAPMethodFromString(String eapMethod) {
        if (EAP_METHODS.containsKey(eapMethod)) {
            return EAP_METHODS.get(eapMethod);
        }
        throw new IllegalArgumentException("Unknown EAP method: " + eapMethod);
    }

    private int getPhase2AuthFromString(String phase2Auth) {
        if (PHASE2_AUTH.containsKey(phase2Auth)) {
            return PHASE2_AUTH.get(phase2Auth);
        }
        throw new IllegalArgumentException("Unknown Phase 2 authentication method: " + phase2Auth);
    }
}