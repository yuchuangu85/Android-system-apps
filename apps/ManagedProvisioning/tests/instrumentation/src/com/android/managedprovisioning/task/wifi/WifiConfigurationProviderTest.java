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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.net.IpConfiguration;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;

import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.model.WifiInfo;

import org.junit.Test;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Unit test for {@link WifiConfigurationProvider}.
 */
@SmallTest
public class WifiConfigurationProviderTest {
    private static final String TEST_SSID = "test_ssid";
    private static final boolean TEST_HIDDEN = true;
    private static final String TEST_PAC_URL = "test.pac.url";
    private static final String TEST_PROXY_BYPASS_HOST = "testProxyBypassHost";
    private static final String TEST_PROXY_HOST = "TestProxyHost";
    private static final int TEST_PROXY_PORT = 1234;
    private static final String TEST_PASSWORD = "testPassword";
    private static final String TEST_PASSWORD_WEP = "0123456789"; // length needs to be 10

    /*
     * Taken from:
     * https://g3doc.corp.google.com/company/teams/clouddpc/documents/policy/sample_policy_jsons.md?cl=head#with-eap
     */
    private static final String TEST_CA_CERT = "MIIDKDCCAhCgAwIBAgIJAOM5SzKO2pzCMA0GCSqGSIb3DQEBCwUAMBIxEDAOBgNVBAMTB0VBUCBDQTAwHhcNMTYwMTEyMDAxMDQ3WhcNMjYwMTA5MDAxMDQ3WjASMRAwDgYDVQQDEwdFQVAgQ0EwMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA89ug+IEKVQXnJGKg5g4uVHg6J/8iRUxR5k2eH5o03hrJNMfN2D+cBe/wCiZcnWbIGbGZACWm2nQth2wy9Zgm2LOd3b4ocrHYls3XLq6Qb5Dd7a0JKU7pdGufiNVEkrmFEB+N64wgwH4COTvCiN4erp5kyJwkfqAl2xLkZo0C464c9XoyQOXbmYD9A8v10wZujyNsEo7Nr2USyw+qhjWSbFbEirP77Tvx+7pJQJwdtk1V9Tn73T2dGF2WHYejei9SmcWpdIUqsu9etYH+zDmtu7I1xlkwiaVsNr2+D+qaCJyOYqrDTKVNK5nmbBPXDWZcNoDbTOoqquX7xONpq9M6jQIDAQABo4GAMH4wHQYDVR0OBBYEFAZ3A2S4qJZZwuNYwkJ6mAdc0gVdMEIGA1UdIwQ7MDmAFAZ3A2S4qJZZwuNYwkJ6mAdc0gVdoRakFDASMRAwDgYDVQQDEwdFQVAgQ0EwggkA4zlLMo7anMIwDAYDVR0TBAUwAwEB/zALBgNVHQ8EBAMCAQYwDQYJKoZIhvcNAQELBQADggEBAHmdMwEhtys4d0E+t7owBmoVR+lUhMCcRtWs8YKX5WIM2kTweT0h/O1xwE1mWmRv/IbDAEb8od4BjAQLhIcolStr2JaO9ZzyxjOnNzqeErh/1DHDbb/moPpqfeJ8YiEz7nH/YU56Q8iCPO7TsgS0sNNE7PfNIUsBW0yHRgpQ4OxWmiZG2YZWiECRzAC0ecPzo59N5iH4vLQIMTMYquiDeMPQnn1eNDGxG8gCtDKIaS6tMg3a28MvWB094pr2ETou8O1C8Ji0Y4hE8QJmSdT7I4+GZjgWg94DZ5RiL7sdp3vC48CXOmeT61YBIvhGUsE1rPhXqkpqQ3Z3C4TFF0jXZZc=";
    private static final String TEST_USER_CERT_STRING_INPUT = "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";
    private static final String TEST_USER_CERT_PRIVATE_KEY_OUTPUT = "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQCcU7DyIza/CXITb7twAM8IXoNGb8gkR0mBB71riLh6Kp9DWACndgoJEMrwwN2QecbZLBsytHfaSGeG+cROWFFHGVcAtwvyT+EBPdJl7cSWB8Sx5xK2JnsIPhYLdvkvjecJvwih3Ajciyr1PYdUG4iZIwk7Yhiq7S7E/dEZ0Rmjf5CAg3kcQCtfHkIzG75Jml1xVsgCNW9+K86/jC2TIwq7zboOZYjVUKGpBtsXL4BGMo1IdU79SXB1PuEEPy88oeARmcPXPbfcsQjhnB0jlM5TegMvVVF9ro+C7yu835CNOG1KoAhJt+7NGhmZC+3OzxcuVqbqqK+PimYEmuBgGCbvAgMBAAECggEAMltHOYicXwd05svsLhUkm8aONQdXClCoXdcXbmdZPYkzSmcztr3wV6FALjOCa8K+ikCJ9lhp7ze5maIlWTNb4zJHc2SDhaezjEnU156y371a4A/WWDSnFy2gvkqx1F66mMofxrvxYtG1odATIsXHx8Sgea+M0KqckTpNvCo+PwscoOjuNsasH+2x5mhUQmwXpoWZR+XrSWINqcpEl+ILGcUqeRNu4qh4oLQOegtxuhQxZI8XCbSFLr59vSx9OXapqX4+sUlGnus+58pXPxEw16MddaggSTH85xeNZlHC43RuJMq94sleaOPoCnFrKuvBL4SRUzYz9XI4bHDoWmR4cQKBgQDMSh4he7k41Yi+e2lQR6SYduRPgREHcC0rAsSH+NhU/rtcC0SUrMLCrskMXsqPFSXpbLlLw+nYj+vFZ7e1s2bq6MyTeO/bDJM+3m9L0/MNmd2g5xbW6JZXUgJOa49XkgkXD8fwoWFoDg3jL5RlkQ83xPf9cnxEs5/N+PA2vIjj2QKBgQDD5Z2Cm0QC0N12YJcmPytzuV+85CbUWgj7m2iuOQS3dnf+12oNUc3GCAuKnghS2eLMYCYM8JijHyKSfYsk6zNgjHgoEUcb+hZesYmML41hsVQ+L0sBjqb1OnBegGLSaeUXhYtDedSKkzgZR7nLorC/89TutrcIMOI9jO4KEHHMBwKBgQCpOLASzljUklUubC4FeQMH5Fwk22XOwoY3vZgshd41MbjjetX5Tc4a1Avn+lFSCpOX8x7ees+nOzhzEgIkOhKDfgmQEzqkOZtzFXAd4NjRqGXk1eeeZ5W5iU4txX08bdSnzMOzOQrl1dZ9HTmQlIOFj9xYjlAP3LcAODhLLws5qQKBgQCcq8FDOWY1UlIsYKfCAPeBgBpfeaDMaI2SnQIlhJiPGgJyIFpC+M+3t6tzW1yQ1o2aorML2khY/Yeq3Rkxl6Hpb66RbPAQIf1OEnNNWKKcJTSY3z3/qtVAf1JrYgam/eYo37c3afJgOcm9/i1L/XuaqSn9GMhdlqr6SwH9rpU0dQKBgQCVaTwnzQHIk6GtzY5M9WayUhR79mZ7f2PutOf2gVviptJA/RenqR+S3JvPny4zcOYS3YxF7jL8sbHyraxlcxUom3/njHSb+Z/BYqY3i7S/T8IwXzkHHxHlNT8HcvG4iNO89o38+Oh1efG40iREDTfKmuv/FvAA4Cfp2/sMAUxMFA==";
    private static final String TEST_USER_CERT_CHAIN_OUTPUT = "MIIDMTCCAhmgAwIBAgIBAjANBgkqhkiG9w0BAQsFADASMRAwDgYDVQQDEwdFQVAgQ0EwMB4XDTE2MDIxNTE2NDUzNVoXDTI2MDIxMjE2NDUzNVowEDEOMAwGA1UEAxMFdXNlcjAwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCcU7DyIza/CXITb7twAM8IXoNGb8gkR0mBB71riLh6Kp9DWACndgoJEMrwwN2QecbZLBsytHfaSGeG+cROWFFHGVcAtwvyT+EBPdJl7cSWB8Sx5xK2JnsIPhYLdvkvjecJvwih3Ajciyr1PYdUG4iZIwk7Yhiq7S7E/dEZ0Rmjf5CAg3kcQCtfHkIzG75Jml1xVsgCNW9+K86/jC2TIwq7zboOZYjVUKGpBtsXL4BGMo1IdU79SXB1PuEEPy88oeARmcPXPbfcsQjhnB0jlM5TegMvVVF9ro+C7yu835CNOG1KoAhJt+7NGhmZC+3OzxcuVqbqqK+PimYEmuBgGCbvAgMBAAGjgZMwgZAwCQYDVR0TBAIwADAdBgNVHQ4EFgQUq+JLbJFNWLo/CTOjSdzzj8NfeIcwQgYDVR0jBDswOYAUBncDZLiollnC41jCQnqYB1zSBV2hFqQUMBIxEDAOBgNVBAMTB0VBUCBDQTCCCQDjOUsyjtqcwjATBgNVHSUEDDAKBggrBgEFBQcDAjALBgNVHQ8EBAMCB4AwDQYJKoZIhvcNAQELBQADggEBAEjDSRnJptUoNZooQJfjaxEH5uv16SChU/0sgk4HfM0YAR/LTTO0XhHGvueMk72gVc3KARIv6m2cLYDhEJ7xvyb/b6PkMchHFzXqk2cxJw01OpwYmouR58MZqRAJm5xicriBtLy11Mx7YEeSSZGKLlDEAT7B1UXw7dodHS0AFZbLT2N8w31PpYMopAPkdc/4DGwDuDvK/QGQ9Xjebn+m0muOPRTgKEyS0/jfkoVEML1K+a2wQA4udk+Si1DEvZETXkEEYxX7l9auUUXmRec7WWO9BY2VDvHyproMDbQndXxy3EC/RH/noS39IItdBeame9CQ5yvlaDBaoqN6tScetUc=";

    private static final String TEST_USER_CERT_NON_X509 = "/u3+7QAAAAIAAAABAAAAAQADYWJjAAABZjmySqMAAAK8MIICuDAOBgorBgEEASoCEQEBBQAEggKk9B93ViQJqk1DOwD/AFiljxR//AoKOEl0XPC6SFadj7x8j3M6FwbALo2lGWHMYnxHU7uVPJ8iSynGWiD+5WcaXA9+xlUmvpI7WgrQ7Dek+r8mLddkvGorqzI1CjJcDIHEmio3WhnM1X8MnBlrKggFP/FKsdE27oirq2YbcH7SEtvsAJNaqHZp7ZkG38AYcZH+V+4hhxDk/VJo3EZeJPcnLVASUyM69t6cTOo0wqOTZjeOytSAwVgKjM2FG/ZkqGgfmP2/BzPbUk0e7amTxCQDYRLsn/KkVdKOq8AoIESGGeaS7bt9p6/wRayp1t3ykAyQipIGlY7aiaMAsXxbdc//MTO4yrt45VEsLGzVaayRRzMD3pITvkz5ZWvuJGQLneKqCFdIzGJPC5tkWmiTWa9WA7+RKVmGgV1loqS86Uzur19N/wrLH56nVWlAVjKknBW8ly5xJF7AAgK3/u3bKSVA50XkO6i1lEsz9FCU4KU3Mx2MVv10NIysXcpecx8LpkZ2gLLm+2Y7exaIqUn239oLJEHhVGgmLAL2avQ3Vd+glKBfgyoScpbWqpFkWD1Qm4UeseZi+XG/77g0+dvniX5SXDbTdZH+uH/AcZnOVMxLGSY9+53jWsMXYvPsnlxEvMVRhsLgwsEdzrqI7uI2XFRj0wSDFthRO9zq0EU2K1BZtwvcILdmmwP2jCBoXDd8vm37K99oPSXjMwJC+XhkxY3K3BGyQp1vUNdtixma1m+ONApFwpgFkSr3vwJOfDlmN2heP4z9OeJWNp0KIBg/SbXeemiIYbzlWmVTmNIQBhzbWL7GQSVjQLmkcZ6oOL4eeBZIPvhfNqEHwEJYYzMf1GVMecJ8aQI5JQx2/ObZ7pUiJukW3PCBVfOzQ7CrpD6WXaLdwBlLmQAAAAEABHRlc3QAAAACAQILu3qeGs7aEjgBWSGbNW1kiGBc3g==";

    private static final String TEST_IDENTITY = "TestUser";
    private static final String TEST_ANONYMOUS_IDENTITY = "TestAUser";
    private static final String TEST_DOMAIN = "google.com";
    private static final String TEST_SECURITY_TYPE = "EAP";

    private static final WifiInfo.Builder BASE_BUILDER = new WifiInfo.Builder()
            .setSsid(TEST_SSID)
            .setHidden(TEST_HIDDEN);

    private static final WifiInfo WIFI_INFO_WPA = BASE_BUILDER
            .setSecurityType(WifiConfigurationProvider.WPA)
            .setPassword(TEST_PASSWORD)
            .build();

    private static final WifiInfo WIFI_INFO_WEP = BASE_BUILDER
            .setSecurityType(WifiConfigurationProvider.WEP)
            .setPassword(TEST_PASSWORD)
            .build();

    private static final WifiInfo WIFI_INFO_WEP_2 = BASE_BUILDER
            .setSecurityType(WifiConfigurationProvider.WEP)
            .setPassword(TEST_PASSWORD_WEP)
            .build();

    private static final WifiInfo WIFI_INFO_NONE = BASE_BUILDER
            .setSecurityType(WifiConfigurationProvider.NONE)
            .build();

    private static final WifiInfo WIFI_INFO_NULL = BASE_BUILDER
            .build();

    private static final WifiInfo WIFI_INFO_PAC = BASE_BUILDER
            .setPacUrl(TEST_PAC_URL)
            .build();

    private static final WifiInfo WIFI_INFO_PROXY = BASE_BUILDER
            .setProxyBypassHosts(TEST_PROXY_BYPASS_HOST)
            .setProxyHost(TEST_PROXY_HOST)
            .setProxyPort(TEST_PROXY_PORT)
            .build();

    private static final WifiInfo.Builder BASE_EAP_BUILDER = new WifiInfo.Builder()
            .setSsid(TEST_SSID)
            .setHidden(TEST_HIDDEN)
            .setSecurityType(TEST_SECURITY_TYPE);

    private final WifiConfigurationProvider mProvider = new WifiConfigurationProvider();

    @Test
    public void testWpa() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(WIFI_INFO_WPA);

        assertBase(wifiConf);
        assertTrue(wifiConf.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK));
        assertTrue(wifiConf.allowedProtocols.get(WifiConfiguration.Protocol.WPA));
        assertEquals("\"" + TEST_PASSWORD + "\"", wifiConf.preSharedKey);
        assertEquals(IpConfiguration.ProxySettings.UNASSIGNED, wifiConf.getProxySettings());
    }

    @Test
    public void testWep() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(WIFI_INFO_WEP);

        assertBase(wifiConf);
        assertTrue(wifiConf.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE));
        assertEquals("\"" + TEST_PASSWORD + "\"", wifiConf.wepKeys[0]);
        assertEquals(IpConfiguration.ProxySettings.UNASSIGNED, wifiConf.getProxySettings());
    }

    @Test
    public void testWep2() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(WIFI_INFO_WEP_2);

        assertBase(wifiConf);
        assertTrue(wifiConf.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE));
        assertEquals(TEST_PASSWORD_WEP, wifiConf.wepKeys[0]);
        assertEquals(IpConfiguration.ProxySettings.UNASSIGNED, wifiConf.getProxySettings());
    }

    @Test
    public void testNone() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(WIFI_INFO_NONE);

        assertBase(wifiConf);
        assertTrue(wifiConf.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE));
        assertTrue(wifiConf.allowedAuthAlgorithms.get(WifiConfiguration.AuthAlgorithm.OPEN));
        assertEquals(IpConfiguration.ProxySettings.UNASSIGNED, wifiConf.getProxySettings());
    }

    @Test
    public void testNull() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(WIFI_INFO_NULL);

        assertBase(wifiConf);
        assertTrue(wifiConf.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE));
        assertTrue(wifiConf.allowedAuthAlgorithms.get(WifiConfiguration.AuthAlgorithm.OPEN));
        assertEquals(IpConfiguration.ProxySettings.UNASSIGNED, wifiConf.getProxySettings());
    }

    @Test
    public void testPac() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(WIFI_INFO_PAC);

        assertBase(wifiConf);
        assertEquals(IpConfiguration.ProxySettings.PAC, wifiConf.getProxySettings());
    }

    @Test
    public void testStaticProxy() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(WIFI_INFO_PROXY);

        assertBase(wifiConf);
        assertEquals(IpConfiguration.ProxySettings.STATIC, wifiConf.getProxySettings());
    }

    @Test
    public void testEAP_returnsCorrectKeyManagement() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(
                buildBaseTestWifiInfoForEAP());

        assertEAPAllowedKeyManagement(wifiConf);
    }

    @Test
    public void testEAP_returnsCorrectEnterpriseConfig() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(
                buildTestWifiInfoForEAPWithCertificates(
                        TEST_PASSWORD, TEST_IDENTITY, TEST_ANONYMOUS_IDENTITY, TEST_DOMAIN,
                        TEST_CA_CERT, TEST_USER_CERT_STRING_INPUT));

        assertEnterpriseConfig(
                wifiConf, TEST_PASSWORD, TEST_IDENTITY, TEST_ANONYMOUS_IDENTITY, TEST_DOMAIN,
                TEST_CA_CERT, TEST_USER_CERT_CHAIN_OUTPUT, TEST_USER_CERT_PRIVATE_KEY_OUTPUT);
    }

    @Test
    public void testEAP_noCertificates_returnsCorrectEnterpriseConfig() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(
                buildTestWifiInfoForEAPWithCertificates(
                        TEST_PASSWORD, TEST_IDENTITY, TEST_ANONYMOUS_IDENTITY, TEST_DOMAIN,
                        /* caCertificate= */null, /* userCertificate= */null));

        assertEnterpriseConfigWithoutCertificates(
                wifiConf, TEST_PASSWORD, TEST_IDENTITY, TEST_ANONYMOUS_IDENTITY, TEST_DOMAIN);
    }

    @Test
    public void testEAP_invalidCACertificate() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(
                buildTestWifiInfoForEAPWithCertificates(
                        TEST_PASSWORD, TEST_IDENTITY, TEST_ANONYMOUS_IDENTITY, TEST_DOMAIN,
                        /* caCertificate= */"random", /* userCertificate= */null));

        assertEmptyEnterpriseConfig(wifiConf);
    }

    @Test
    public void testEAP_invalidUserCertificate() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(
                buildTestWifiInfoForEAPWithCertificates(
                        TEST_PASSWORD, TEST_IDENTITY, TEST_ANONYMOUS_IDENTITY, TEST_DOMAIN,
                        /* caCertificate= */null, /* userCertificate= */"random"));

        assertEmptyEnterpriseConfig(wifiConf);
    }

    @Test
    public void testEAP_nonX509Cert() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(
                buildTestWifiInfoForEAPWithCertificates(
                        TEST_PASSWORD, TEST_IDENTITY, TEST_ANONYMOUS_IDENTITY, TEST_DOMAIN,
                        /* caCertificate= */null, TEST_USER_CERT_NON_X509));

        assertEmptyEnterpriseConfig(wifiConf);
    }

    @Test
    public void testEAP_PEAP_PAP() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(
                buildTestWifiInfoForEAPJustAuthMethods("PEAP", "PAP"));

        assertEAP_MethodAndPhase2Auth(
                wifiConf, WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.PAP);
    }

    @Test
    public void testEAP_PEAP_NONE() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(
                buildTestWifiInfoForEAPJustAuthMethods("PEAP", "NONE"));

        assertEAP_MethodAndPhase2Auth(
                wifiConf, WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.NONE);
    }

    @Test
    public void testEAP_Phase2_Empty() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(
                buildTestWifiInfoForEAPJustAuthMethods("PEAP", ""));

        assertEAP_MethodAndPhase2Auth(
                wifiConf, WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.NONE);
    }

    @Test
    public void testEAP_Phase2_Null() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(
                buildTestWifiInfoForEAPJustAuthMethods("PEAP", null));

        assertEAP_MethodAndPhase2Auth(
                wifiConf, WifiEnterpriseConfig.Eap.PEAP, WifiEnterpriseConfig.Phase2.NONE);
    }

    @Test
    public void testEAP_TLS_MSCHAP() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(
                buildTestWifiInfoForEAPJustAuthMethods("TLS", "MSCHAP"));

        assertEAP_MethodAndPhase2Auth(
                wifiConf, WifiEnterpriseConfig.Eap.TLS, WifiEnterpriseConfig.Phase2.MSCHAP);
    }

    @Test
    public void testEAP_TTLS_MSCHAPV2() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(
                buildTestWifiInfoForEAPJustAuthMethods("TTLS", "MSCHAPV2"));

        assertEAP_MethodAndPhase2Auth(
                wifiConf, WifiEnterpriseConfig.Eap.TTLS, WifiEnterpriseConfig.Phase2.MSCHAPV2);
    }

    @Test
    public void testEAP_PWD_GTC() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(
                buildTestWifiInfoForEAPJustAuthMethods("PWD", "GTC"));

        assertEAP_MethodAndPhase2Auth(
                wifiConf, WifiEnterpriseConfig.Eap.PWD, WifiEnterpriseConfig.Phase2.GTC);
    }

    @Test
    public void testEAP_SIM_SIM() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(
                buildTestWifiInfoForEAPJustAuthMethods("SIM", "SIM"));

        assertEAP_MethodAndPhase2Auth(
                wifiConf, WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.SIM);
    }

    @Test
    public void testEAP_AKA_AKA() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(
                buildTestWifiInfoForEAPJustAuthMethods("AKA", "AKA"));

        assertEAP_MethodAndPhase2Auth(
                wifiConf, WifiEnterpriseConfig.Eap.AKA, WifiEnterpriseConfig.Phase2.AKA);
    }

    @Test
    public void testEAP_AKA_PRIME_AKA_PRIME() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(
                buildTestWifiInfoForEAPJustAuthMethods("AKA_PRIME", "AKA_PRIME"));

        assertEAP_MethodAndPhase2Auth(
                wifiConf, WifiEnterpriseConfig.Eap.AKA_PRIME,
                WifiEnterpriseConfig.Phase2.AKA_PRIME);
    }

    @Test
    public void testEAPWithInvalidEAPMethod() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(
                buildTestWifiInfoForEAPJustAuthMethods("ABC", "PAP"));

        assertEAP_MethodAndPhase2Auth(wifiConf, WifiEnterpriseConfig.Eap.NONE,
                WifiEnterpriseConfig.Phase2.NONE);
    }

    @Test
    public void testEAPWithInvalidPhase2Auth() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(
                buildTestWifiInfoForEAPJustAuthMethods("PEAP", "ABC"));

        assertEAP_MethodAndPhase2Auth(wifiConf, WifiEnterpriseConfig.Eap.NONE,
                WifiEnterpriseConfig.Phase2.NONE);
    }

    private WifiInfo buildBaseTestWifiInfoForEAP() {
        return buildTestWifiInfoForEAPWithCertificates(
                TEST_PASSWORD, TEST_IDENTITY, TEST_ANONYMOUS_IDENTITY, TEST_DOMAIN,
                /* caCertificate= */null, /* userCertificate= */null);
    }

    private WifiInfo buildTestWifiInfoForEAPJustAuthMethods(String eapMethod, String phase2Auth) {
        return BASE_EAP_BUILDER
                .setEapMethod(eapMethod)
                .setPhase2Auth(phase2Auth)
                .setCaCertificate(null)
                .setUserCertificate(null)
                .build();
    }

    private WifiInfo buildTestWifiInfoForEAPWithCertificates(
            String password, String identity, String anonymousIdentity, String domain,
            String caCertificate, String userCertificate) {
        return BASE_EAP_BUILDER
                .setEapMethod("PEAP")
                .setPhase2Auth("")
                .setPassword(password)
                .setIdentity(identity)
                .setAnonymousIdentity(anonymousIdentity)
                .setDomain(domain)
                .setCaCertificate(caCertificate)
                .setUserCertificate(userCertificate)
                .build();
    }

    private void assertBase(WifiConfiguration wifiConf) {
        assertEquals(TEST_SSID, wifiConf.SSID);
        assertEquals(TEST_HIDDEN, wifiConf.hiddenSSID);
        assertEquals(WifiConfiguration.Status.ENABLED, wifiConf.status);
        assertEquals(WifiConfiguration.USER_APPROVED, wifiConf.userApproved);
    }

    private void assertEnterpriseConfig(WifiConfiguration wifiConf,
            String password, String identity, String anonymousIdentity, String domain,
            String caCertificate, String userCertificate, String privateKey) {
        assertEnterpriseConfigBase(wifiConf, password, identity, anonymousIdentity, domain);
        assertCertificateInformation(wifiConf, caCertificate, userCertificate, privateKey);
    }

    private void assertEnterpriseConfigWithoutCertificates(WifiConfiguration wifiConf,
            String password, String identity, String anonymousIdentity, String domain) {
        assertEnterpriseConfigBase(wifiConf, password, identity, anonymousIdentity, domain);
        assertEmptyCertificates(wifiConf);
    }

    private void assertEnterpriseConfigBase(WifiConfiguration wifiConf, String password,
            String identity, String anonymousIdentity, String domain) {
        assertNotEquals(null, wifiConf.enterpriseConfig);
        assertEquals(password, wifiConf.enterpriseConfig.getPassword());
        assertEquals(identity, wifiConf.enterpriseConfig.getIdentity());
        assertEquals(anonymousIdentity, wifiConf.enterpriseConfig.getAnonymousIdentity());
        assertEquals(domain, wifiConf.enterpriseConfig.getDomainSuffixMatch());
    }

    private void assertCertificateInformation(
            WifiConfiguration wifiConf, String caCertificate, String userCertificate,
            String privateKey) {
        try {
            assertEquals(caCertificate, Base64.getEncoder()
                    .encodeToString(wifiConf.enterpriseConfig.getCaCertificate().getEncoded()));
            assertEquals(userCertificate, buildEncodedCertificateChain(
                    wifiConf.enterpriseConfig.getClientCertificateChain()));
            assertEquals(privateKey, Base64.getEncoder()
                    .encodeToString(wifiConf.enterpriseConfig.getClientPrivateKey().getEncoded()));
        } catch (CertificateEncodingException e) {
            throw new IllegalArgumentException("Certificate cannot be encoded!");
        }
    }

    private void assertEmptyEnterpriseConfig(WifiConfiguration wifiConf) {
        assertEnterpriseConfigBase(wifiConf, "", "", "",  "");
        assertEmptyCertificates(wifiConf);
    }

    private void assertEmptyCertificates(WifiConfiguration wifiConf) {
        assertEquals(null, wifiConf.enterpriseConfig.getCaCertificate());
        assertEquals(null, wifiConf.enterpriseConfig.getClientPrivateKey());
        assertEquals(null, wifiConf.enterpriseConfig.getClientCertificateChain());
    }

    private String buildEncodedCertificateChain(X509Certificate[] clientCertificateChain)
            throws CertificateEncodingException {
        StringBuilder encodedCertChain = new StringBuilder();
        for (X509Certificate certificate: clientCertificateChain) {
            encodedCertChain.append(Base64.getEncoder().encodeToString(certificate.getEncoded()));
        }
        return encodedCertChain.toString();
    }

    private void assertEAP_MethodAndPhase2Auth(
            WifiConfiguration wifiConf, int eapMethod, int phase2Auth) {
        assertEquals(eapMethod, wifiConf.enterpriseConfig.getEapMethod());
        assertEquals(phase2Auth, wifiConf.enterpriseConfig.getPhase2Method());
    }

    private void assertEAPAllowedKeyManagement(WifiConfiguration wifiConf) {
        assertTrue(wifiConf.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.IEEE8021X));
        assertTrue(wifiConf.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP));
    }
}
