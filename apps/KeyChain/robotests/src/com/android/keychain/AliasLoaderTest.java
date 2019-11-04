/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.keychain;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.security.Credentials;
import android.security.KeyStore;
import android.util.Base64;
import com.android.keychain.internal.KeyInfoProvider;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.security.auth.x500.X500Principal;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowApplication;

@RunWith(RobolectricTestRunner.class)
public final class AliasLoaderTest {
    // Generated using:
    // openssl req -newkey rsa:2048 -nodes -keyout key.pem -x509 -days 3650 -out certificate.pem
    private static final String SELF_SIGNED_RSA_CERT_1_B64 =
            "MIIDlDCCAnygAwIBAgIJAJsWcaXZlx7GMA0GCSqGSIb3DQEBCwUAMF8xCzAJBgNV\n"
                    + "BAYTAlVLMQ8wDQYDVQQIDAZMb25kb24xGzAZBgNVBAoMEkFPU1AgVGVzdCBkYXRh\n"
                    + "IG9uZTEQMA4GA1UECwwHQW5kcm9pZDEQMA4GA1UEAwwHYW5kcm9pZDAeFw0xODA4\n"
                    + "MjMxNjAwNTFaFw0yODA4MjAxNjAwNTFaMF8xCzAJBgNVBAYTAlVLMQ8wDQYDVQQI\n"
                    + "DAZMb25kb24xGzAZBgNVBAoMEkFPU1AgVGVzdCBkYXRhIG9uZTEQMA4GA1UECwwH\n"
                    + "QW5kcm9pZDEQMA4GA1UEAwwHYW5kcm9pZDCCASIwDQYJKoZIhvcNAQEBBQADggEP\n"
                    + "ADCCAQoCggEBAMgyezTnRdmITmxXQNgG4UmCdvAaOQ7H+iB6wHfgT9iajoiGF9I9\n"
                    + "Efdx6QnnM6S3N4BD5MGb9IPvF79aXJlWgd9Q+l1vOG0bcpB9KVDrui1IjNW/R+X3\n"
                    + "0VKg2xa5+6kYTXnlI5GZF2pG8GCuoubsFkbfMTpmonAOdKDsfPLVSKbWoaNsFtli\n"
                    + "zXIpJDpK+QHY9yMvJ7lBme3f8OVOBC2OzetCUScTWl1Q9JqFHNgluk2in8mfwban\n"
                    + "DE7fdXGrnUNuJ31h5SBjLMAoaLTjxL9Vn0W3wiB/G8lVAgOJs+wJ5G7PVa/A7ZGB\n"
                    + "RGNySfpWs1yrpSUIxx4p9Gh3SVJ+WAceaH0CAwEAAaNTMFEwHQYDVR0OBBYEFJdE\n"
                    + "O9ssB/FgSEzvgrkLbthzJ4QpMB8GA1UdIwQYMBaAFJdEO9ssB/FgSEzvgrkLbthz\n"
                    + "J4QpMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEBAB//kA875v/7\n"
                    + "pOYFtacYYd6pU4JFhCnIpKTuc8ee3C1pJd9hScI1P5tiM8dnRscmTT5puE62OKE/\n"
                    + "XdlnCkBm9oAD2xXwK+W2fTFVidLHrjSdihTmyUbjfNOm5SS3Z6S9OmPPb4Ei4WXh\n"
                    + "qqCrk3OQ00A6agfTy0qzW1wQT9DE1uyLAZ1jdTMD2wNwzQP7IzoTx+ay985eRC1V\n"
                    + "pquK9kOHhnhGn/kSrZgpQB1rUmpm+IrdpwkIUIdnMyiuIrQa40D+bKRmOWpNKUH9\n"
                    + "4MCeitS4W9LfQyDj3hktD5hf4hxRIb185gN7v/Uf2Ft87rnFdtR1xum4JDagosOv\n"
                    + "vvF/HE7ofuw=\n";

    private static final String SELF_SIGNED_RSA_CERT_2_B64 =
            "MIIDlDCCAnygAwIBAgIJAL4ZhppdcG5IMA0GCSqGSIb3DQEBCwUAMF8xCzAJBgNV\n"
                    + "BAYTAlVLMQ8wDQYDVQQIDAZMb25kb24xGzAZBgNVBAoMEkFPU1AgdGVzdCBkYXRh\n"
                    + "IHR3bzEQMA4GA1UECwwHQW5kcm9pZDEQMA4GA1UEAwwHYW5kcm9pZDAeFw0xODA4\n"
                    + "MjMxNjA2NDhaFw0yODA4MjAxNjA2NDhaMF8xCzAJBgNVBAYTAlVLMQ8wDQYDVQQI\n"
                    + "DAZMb25kb24xGzAZBgNVBAoMEkFPU1AgdGVzdCBkYXRhIHR3bzEQMA4GA1UECwwH\n"
                    + "QW5kcm9pZDEQMA4GA1UEAwwHYW5kcm9pZDCCASIwDQYJKoZIhvcNAQEBBQADggEP\n"
                    + "ADCCAQoCggEBAMn7UvVsQquyotNNt9B/JCa84jcPfIV3RBDSYvcTrr1KyVIIJSmo\n"
                    + "JnUQYt6yRN9HjOOckuOSRAtzumqYuW1tpMDgDlORImwvX2pwQfJLT8dErgAaNGYu\n"
                    + "xjIUJ1Dwusuw891F/nFvACHOPWfgpcz4WJo1SQCUIObeuENebUurDnyYCMOD0+t3\n"
                    + "e4POaE6pF4VqjoDHX8slexonzkxZ3e7V2zrgpRBx+TUHq69GexpVj5frUSxjEllf\n"
                    + "58hNwwbPArpW73102wkqb7bCqeJ9f7fSY01hmhgIRn1uqy6jPfq9HqaXF+QhGVkT\n"
                    + "Oeoauu1o3/oTUmtbkifQvVGhsW/VX5v9hl0CAwEAAaNTMFEwHQYDVR0OBBYEFAO6\n"
                    + "1Tw6JddbJZI+iV0hIyRLOCGBMB8GA1UdIwQYMBaAFAO61Tw6JddbJZI+iV0hIyRL\n"
                    + "OCGBMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEBAAIQQEOvTqAn\n"
                    + "EpdZmrwQIBRvWT8pYyf4168CXEFDTXj5ODQY3IZ2hCOpIhJHPGc8RTVPNL0/LwIH\n"
                    + "fncgU+uLYL+CUH1DjqZK+99QvcxJkIi6+lQQynlCT4OXpo4AyLl4U4vpjKO9QUdi\n"
                    + "TiFXDMgM0iYyZU88ABgGYDsNUZuL+zj3rABszzeoxyQDjVrfUZQ1SnPYnN9wLlPj\n"
                    + "fFdyRBpJyZPOABXsJlB6AO3a5Erk6DBB3kj1EmN1b/oYqWgNBg2pBBW7fhxx8MVj\n"
                    + "q1bnYwvwp2Iq/oHoGbr80ZNl97i8YQiXEHG0N9Y+PzaTJ12bREA+0Q6ZBfe9V9ya\n"
                    + "IgPhDc8Rb0g=\n";

    // Generated with:
    // openssl req -newkey ec:<(openssl ecparam -name secp256k1) -nodes -keyout key.pem -x509 \
    //  -days 3650 -out certificate.pem
    private static final String SELF_SIGNED_EC_CERT_1_B64 =
            "MIICBDCCAaugAwIBAgIJAIOxj+lhuGDBMAoGCCqGSM49BAMCMF8xCzAJBgNVBAYT\n"
                    + "AlVLMQ8wDQYDVQQIDAZMb25kb24xGzAZBgNVBAoMEkFPU1AgdGVzdCBkYXRhIG9u\n"
                    + "ZTEQMA4GA1UECwwHQW5kcm9pZDEQMA4GA1UEAwwHYW5kcm9pZDAeFw0xODA4MjMx\n"
                    + "NjEzMzJaFw0yODA4MjAxNjEzMzJaMF8xCzAJBgNVBAYTAlVLMQ8wDQYDVQQIDAZM\n"
                    + "b25kb24xGzAZBgNVBAoMEkFPU1AgdGVzdCBkYXRhIG9uZTEQMA4GA1UECwwHQW5k\n"
                    + "cm9pZDEQMA4GA1UEAwwHYW5kcm9pZDBWMBAGByqGSM49AgEGBSuBBAAKA0IABJ/K\n"
                    + "6Z1d4T+LdDKdl+QkiLs/oJ0fBQmVezo4H0tY7EOugsydZGaem0CyEtZX/0Nki4To\n"
                    + "XvgUB2jFGRERYPDkM/WjUzBRMB0GA1UdDgQWBBTsJksMH345+fGhJYmyiR9xJEXt\n"
                    + "MDAfBgNVHSMEGDAWgBTsJksMH345+fGhJYmyiR9xJEXtMDAPBgNVHRMBAf8EBTAD\n"
                    + "AQH/MAoGCCqGSM49BAMCA0cAMEQCIFDrt1eB11O/lD4CHAaaZQ82WvoXgJC89rol\n"
                    + "EuDcG/j3AiBJ80KVSTmim6k6RWEMIHP78mCLnpwKnwVAk9On5xkJ0Q==";

    private static final String SELF_SIGNED_EC_CERT_2_B64 =
            "MIICBDCCAaugAwIBAgIJAMDRz9Ey2tIPMAoGCCqGSM49BAMCMF8xCzAJBgNVBAYT\n"
                    + "AlVLMQ8wDQYDVQQIDAZMb25kb24xGzAZBgNVBAoMEkFPU1AgdGVzdCBkYXRhIHR3\n"
                    + "bzEQMA4GA1UECwwHQW5kcm9pZDEQMA4GA1UEAwwHYW5kcm9pZDAeFw0xODA4MjMx\n"
                    + "NjE0MDdaFw0yODA4MjAxNjE0MDdaMF8xCzAJBgNVBAYTAlVLMQ8wDQYDVQQIDAZM\n"
                    + "b25kb24xGzAZBgNVBAoMEkFPU1AgdGVzdCBkYXRhIHR3bzEQMA4GA1UECwwHQW5k\n"
                    + "cm9pZDEQMA4GA1UEAwwHYW5kcm9pZDBWMBAGByqGSM49AgEGBSuBBAAKA0IABA7p\n"
                    + "osQCNiI+RBy29ydpFGBPypbIy8U3Ylgujo8k4B20evWj5CKYP9Cw0gCypwBB9uYM\n"
                    + "706diiK6rFbKXFhhcUGjUzBRMB0GA1UdDgQWBBSYWWkKZqiiNXC75cZHoIpRwMMd\n"
                    + "7zAfBgNVHSMEGDAWgBSYWWkKZqiiNXC75cZHoIpRwMMd7zAPBgNVHRMBAf8EBTAD\n"
                    + "AQH/MAoGCCqGSM49BAMCA0cAMEQCIFh7LgrBMMMSqAF8PdWy+bV8jUuQqwOQ34Mo\n"
                    + "MtghI6eYAiAOuAXmRZiwVjnB9rH3f2Vy3rbMgfD3/AYzREqVnuZD0Q==";

    private static final X500Principal ISSUER_ONE =
            new X500Principal("CN=android, OU=Android, O=AOSP test data one, ST=London, C=UK");

    private KeyInfoProvider mDummyInfoProvider;
    private KeyChainActivity.CertificateParametersFilter mDummyChecker;
    private byte[] mRSACertOne;
    private byte[] mRSACertTwo;
    private byte[] mECCertOne;
    private byte[] mECCertTwo;
    private ArrayList<byte[]> mIssuers;

    @Before
    public void setUp() {
        mRSACertOne = Base64.decode(SELF_SIGNED_RSA_CERT_1_B64, Base64.DEFAULT);
        mRSACertTwo = Base64.decode(SELF_SIGNED_RSA_CERT_2_B64, Base64.DEFAULT);
        mECCertOne = Base64.decode(SELF_SIGNED_EC_CERT_1_B64, Base64.DEFAULT);
        mECCertTwo = Base64.decode(SELF_SIGNED_EC_CERT_2_B64, Base64.DEFAULT);
        mIssuers = new ArrayList<byte[]>();
        mIssuers.add(ISSUER_ONE.getEncoded());
        mDummyInfoProvider =
                new KeyInfoProvider() {
                    public boolean isUserSelectable(String alias) {
                        return true;
                    }
                };

        mDummyChecker = mock(KeyChainActivity.CertificateParametersFilter.class);
        when(mDummyChecker.shouldPresentCertificate(Mockito.anyString())).thenReturn(true);
    }

    @Test
    public void testAliasLoader_loadsAllAliases()
            throws InterruptedException, ExecutionException, CancellationException,
                    TimeoutException {
        KeyStore keyStore = mock(KeyStore.class);
        when(keyStore.list(Credentials.USER_PRIVATE_KEY)).thenReturn(new String[] {"b", "c", "a"});

        KeyChainActivity.AliasLoader loader =
                new KeyChainActivity.AliasLoader(
                        keyStore,
                        RuntimeEnvironment.application,
                        mDummyInfoProvider,
                        mDummyChecker);
        loader.execute();

        ShadowApplication.runBackgroundTasks();
        KeyChainActivity.CertificateAdapter result = loader.get(5, TimeUnit.SECONDS);
        Assert.assertNotNull(result);
        Assert.assertEquals(3, result.getCount());
        Assert.assertEquals("a", result.getItem(0));
        Assert.assertEquals("b", result.getItem(1));
        Assert.assertEquals("c", result.getItem(2));
    }

    @Test
    public void testAliasLoader_copesWithNoAliases()
            throws InterruptedException, ExecutionException, CancellationException,
                    TimeoutException {
        KeyStore keyStore = mock(KeyStore.class);
        when(keyStore.list(Credentials.USER_PRIVATE_KEY)).thenReturn(null);

        KeyChainActivity.AliasLoader loader =
                new KeyChainActivity.AliasLoader(
                        keyStore,
                        RuntimeEnvironment.application,
                        mDummyInfoProvider,
                        mDummyChecker);
        loader.execute();

        ShadowApplication.runBackgroundTasks();
        KeyChainActivity.CertificateAdapter result = loader.get(5, TimeUnit.SECONDS);
        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.getCount());
    }

    @Test
    public void testAliasLoader_filtersNonUserSelectableAliases()
            throws InterruptedException, ExecutionException, CancellationException,
                    TimeoutException {
        KeyStore keyStore = mock(KeyStore.class);
        when(keyStore.list(Credentials.USER_PRIVATE_KEY)).thenReturn(new String[] {"a", "b", "c"});

        KeyInfoProvider infoProvider = mock(KeyInfoProvider.class);
        when(infoProvider.isUserSelectable("a")).thenReturn(false);
        when(infoProvider.isUserSelectable("b")).thenReturn(true);
        when(infoProvider.isUserSelectable("c")).thenReturn(false);

        KeyChainActivity.AliasLoader loader =
                new KeyChainActivity.AliasLoader(
                        keyStore, RuntimeEnvironment.application, infoProvider, mDummyChecker);
        loader.execute();

        ShadowApplication.runBackgroundTasks();
        KeyChainActivity.CertificateAdapter result = loader.get(5, TimeUnit.SECONDS);
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.getCount());
        Assert.assertEquals("b", result.getItem(0));
    }

    @Test
    public void testAliasLoader_filtersAliasesWithNonConformingParameters()
            throws InterruptedException, ExecutionException, CancellationException,
                    TimeoutException {
        KeyStore keyStore = mock(KeyStore.class);
        when(keyStore.list(Credentials.USER_PRIVATE_KEY))
                .thenReturn(new String[] {"a", "b", "c", "d"});

        KeyInfoProvider infoProvider = mock(KeyInfoProvider.class);
        when(infoProvider.isUserSelectable("a")).thenReturn(true);
        when(infoProvider.isUserSelectable("b")).thenReturn(true);
        when(infoProvider.isUserSelectable("c")).thenReturn(false);
        when(infoProvider.isUserSelectable("d")).thenReturn(false);

        KeyChainActivity.CertificateParametersFilter checker =
                mock(KeyChainActivity.CertificateParametersFilter.class);
        // The first alias is user-selectable and should be presented.
        when(checker.shouldPresentCertificate("a")).thenReturn(true);
        // The second alias is user-selectable but should not be presented.
        when(checker.shouldPresentCertificate("b")).thenReturn(false);
        // The third alias is not user-selectable but should be presented.
        when(checker.shouldPresentCertificate("c")).thenReturn(true);
        // The fourth alias is not user-selectable and should not be presented.
        when(checker.shouldPresentCertificate("d")).thenReturn(false);

        KeyChainActivity.AliasLoader loader =
                new KeyChainActivity.AliasLoader(
                        keyStore, RuntimeEnvironment.application, infoProvider, checker);
        loader.execute();

        ShadowApplication.runBackgroundTasks();
        KeyChainActivity.CertificateAdapter result = loader.get(5, TimeUnit.SECONDS);
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.getCount());
        Assert.assertEquals("a", result.getItem(0));
    }

    private KeyStore prepareKeyStoreWithCertificates() {
        KeyStore keyStore = mock(KeyStore.class);
        when(keyStore.get(Credentials.USER_CERTIFICATE + "rsa1")).thenReturn(mRSACertOne);
        when(keyStore.get(Credentials.USER_CERTIFICATE + "ec1")).thenReturn(mECCertOne);
        when(keyStore.get(Credentials.USER_CERTIFICATE + "rsa2")).thenReturn(mRSACertTwo);
        when(keyStore.get(Credentials.USER_CERTIFICATE + "ec2")).thenReturn(mECCertTwo);

        return keyStore;
    }

    @Test
    public void testCertificateParametersFilter_filtersByKey() throws CancellationException {
        KeyStore keyStore = prepareKeyStoreWithCertificates();

        KeyChainActivity.CertificateParametersFilter ec_checker =
                new KeyChainActivity.CertificateParametersFilter(
                        keyStore, new String[] {"EC"}, new ArrayList<byte[]>());
        Assert.assertFalse(ec_checker.shouldPresentCertificate("rsa1"));
        Assert.assertTrue(ec_checker.shouldPresentCertificate("ec1"));

        KeyChainActivity.CertificateParametersFilter rsa_and_ec_checker =
                new KeyChainActivity.CertificateParametersFilter(
                        keyStore, new String[] {"EC", "RSA"}, new ArrayList<byte[]>());
        Assert.assertTrue(rsa_and_ec_checker.shouldPresentCertificate("rsa1"));
        Assert.assertTrue(rsa_and_ec_checker.shouldPresentCertificate("ec1"));
    }

    @Test
    public void testCertificateParametersFilter_filtersByIssuer() throws CancellationException {
        KeyStore keyStore = prepareKeyStoreWithCertificates();

        KeyChainActivity.CertificateParametersFilter issuer_checker =
                new KeyChainActivity.CertificateParametersFilter(
                        keyStore, new String[] {}, mIssuers);
        Assert.assertTrue(issuer_checker.shouldPresentCertificate("rsa1"));
        Assert.assertTrue(issuer_checker.shouldPresentCertificate("ec1"));
        Assert.assertFalse(issuer_checker.shouldPresentCertificate("rsa2"));
        Assert.assertFalse(issuer_checker.shouldPresentCertificate("ec2"));
    }

    @Test
    public void testCertificateParametersFilter_filtersByIssuerAndKey()
            throws InterruptedException, ExecutionException, CancellationException,
                    TimeoutException {
        KeyStore keyStore = prepareKeyStoreWithCertificates();

        KeyChainActivity.CertificateParametersFilter issuer_checker =
                new KeyChainActivity.CertificateParametersFilter(
                        keyStore, new String[] {"EC"}, mIssuers);
        Assert.assertFalse(issuer_checker.shouldPresentCertificate("rsa1"));
        Assert.assertTrue(issuer_checker.shouldPresentCertificate("ec1"));
        Assert.assertFalse(issuer_checker.shouldPresentCertificate("rsa2"));
        Assert.assertFalse(issuer_checker.shouldPresentCertificate("ec2"));
    }
}
