/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.settings.applications.managedomainurls;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.IntentFilterVerificationInfo;
import android.content.pm.PackageManager;
import android.util.ArraySet;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.ShadowApplicationPackageManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowApplicationPackageManager.class})
public class DomainUrlsUtilsTest {

    private static final String TEST_PACKAGE = "com.test.android.Package";
    private static final int USER_ID = 10;

    private Context mContext;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
    }

    @Test
    public void getDomainsSummary_domainStatusSetToNever_showNoneText() {
        mContext.getPackageManager().updateIntentVerificationStatusAsUser(TEST_PACKAGE,
                PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER, USER_ID);

        assertThat(DomainUrlsUtils.getDomainsSummary(mContext, TEST_PACKAGE, USER_ID,
                new ArraySet<>())).isEqualTo(mContext.getString(R.string.domain_urls_summary_none));
    }

    @Test
    public void getDomainsSummary_domainStatusSet_noDomains_showNoneText() {
        mContext.getPackageManager().updateIntentVerificationStatusAsUser(TEST_PACKAGE,
                PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS_ASK, USER_ID);

        assertThat(DomainUrlsUtils.getDomainsSummary(mContext, TEST_PACKAGE, USER_ID,
                new ArraySet<>())).isEqualTo(mContext.getString(R.string.domain_urls_summary_none));
    }

    @Test
    public void getDomainsSummary_domainStatusSet_oneDomain_showSingleDomain() {
        mContext.getPackageManager().updateIntentVerificationStatusAsUser(TEST_PACKAGE,
                PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS_ASK, USER_ID);
        ArraySet<String> domains = new ArraySet<>();
        domains.add("test.domain.com");

        assertThat(DomainUrlsUtils.getDomainsSummary(mContext, TEST_PACKAGE, USER_ID,
                domains)).isEqualTo(
                mContext.getString(R.string.domain_urls_summary_one, domains.valueAt(0)));
    }

    @Test
    public void getDomainsSummary_domainStatusSet_multipleDomain_showMultipleDomains() {
        mContext.getPackageManager().updateIntentVerificationStatusAsUser(TEST_PACKAGE,
                PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS_ASK, USER_ID);
        ArraySet<String> domains = new ArraySet<>();
        domains.add("test.domain.com");
        domains.add("test.domain2.com");

        assertThat(DomainUrlsUtils.getDomainsSummary(mContext, TEST_PACKAGE, USER_ID,
                domains)).isEqualTo(
                mContext.getString(R.string.domain_urls_summary_some, domains.valueAt(0)));
    }

    @Test
    public void getHandledDomains_includeIntentFilterVerificationInfoDomains() {
        PackageManager pm = mock(PackageManager.class);
        String domain = "test.domain.com";
        ArraySet<String> domains = new ArraySet<>();
        domains.add(domain);
        IntentFilterVerificationInfo info = new IntentFilterVerificationInfo(TEST_PACKAGE, domains);
        when(pm.getIntentFilterVerifications(TEST_PACKAGE)).thenReturn(Arrays.asList(info));

        assertThat(DomainUrlsUtils.getHandledDomains(pm, TEST_PACKAGE)).hasSize(1);
        assertThat(DomainUrlsUtils.getHandledDomains(pm, TEST_PACKAGE)).contains(domain);
    }

    @Test
    public void getHandledDomains_includeBrowsableIntentFiltersWithHttpDataScheme() {
        PackageManager pm = mock(PackageManager.class);
        String domain = "test.domain.com";
        String port = "80";
        IntentFilter filter = new IntentFilter();
        filter.addCategory(Intent.CATEGORY_BROWSABLE);
        filter.addDataScheme(IntentFilter.SCHEME_HTTP);
        filter.addDataAuthority(new IntentFilter.AuthorityEntry(domain, port));
        when(pm.getAllIntentFilters(TEST_PACKAGE)).thenReturn(Arrays.asList(filter));

        assertThat(DomainUrlsUtils.getHandledDomains(pm, TEST_PACKAGE)).hasSize(1);
        assertThat(DomainUrlsUtils.getHandledDomains(pm, TEST_PACKAGE)).contains(domain);
    }

    @Test
    public void getHandledDomains_includeBrowsableIntentFiltersWithHttpsDataScheme() {
        PackageManager pm = mock(PackageManager.class);
        String domain = "test.domain.com";
        String port = "80";
        IntentFilter filter = new IntentFilter();
        filter.addCategory(Intent.CATEGORY_BROWSABLE);
        filter.addDataScheme(IntentFilter.SCHEME_HTTPS);
        filter.addDataAuthority(new IntentFilter.AuthorityEntry(domain, port));
        when(pm.getAllIntentFilters(TEST_PACKAGE)).thenReturn(Arrays.asList(filter));

        assertThat(DomainUrlsUtils.getHandledDomains(pm, TEST_PACKAGE)).hasSize(1);
        assertThat(DomainUrlsUtils.getHandledDomains(pm, TEST_PACKAGE)).contains(domain);
    }

    @Test
    public void getHandledDomains_excludeBrowsableIntentFiltersWithOtherDataScheme() {
        PackageManager pm = mock(PackageManager.class);
        String domain = "test.domain.com";
        String port = "80";
        IntentFilter filter = new IntentFilter();
        filter.addCategory(Intent.CATEGORY_BROWSABLE);
        filter.addDataAuthority(new IntentFilter.AuthorityEntry(domain, port));
        when(pm.getAllIntentFilters(TEST_PACKAGE)).thenReturn(Arrays.asList(filter));

        assertThat(DomainUrlsUtils.getHandledDomains(pm, TEST_PACKAGE)).isEmpty();
    }
}
