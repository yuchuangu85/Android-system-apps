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

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.IntentFilterVerificationInfo;
import android.content.pm.PackageManager;
import android.util.ArraySet;

import com.android.car.settings.R;

import java.util.List;

/** Utility functions related to handling application domain urls. */
public final class DomainUrlsUtils {
    private DomainUrlsUtils() {
    }

    /** Get a summary text based on the number of handled domains. */
    public static CharSequence getDomainsSummary(Context context, String packageName, int userId,
            ArraySet<String> domains) {
        PackageManager pm = context.getPackageManager();

        // If the user has explicitly said "no" for this package, that's the string we should show.
        int domainStatus = pm.getIntentVerificationStatusAsUser(packageName, userId);
        if (domainStatus == PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER) {
            return context.getText(R.string.domain_urls_summary_none);
        }
        // Otherwise, ask package manager for the domains for this package, and show the first
        // one (or none if there aren't any).
        if (domains.isEmpty()) {
            return context.getText(R.string.domain_urls_summary_none);
        } else if (domains.size() == 1) {
            return context.getString(R.string.domain_urls_summary_one, domains.valueAt(0));
        } else {
            return context.getString(R.string.domain_urls_summary_some, domains.valueAt(0));
        }
    }

    /** Get the list of domains handled by the given package. */
    public static ArraySet<String> getHandledDomains(PackageManager pm, String packageName) {
        List<IntentFilterVerificationInfo> iviList = pm.getIntentFilterVerifications(packageName);
        List<IntentFilter> filters = pm.getAllIntentFilters(packageName);

        ArraySet<String> result = new ArraySet<>();
        if (iviList != null && iviList.size() > 0) {
            for (IntentFilterVerificationInfo ivi : iviList) {
                for (String host : ivi.getDomains()) {
                    result.add(host);
                }
            }
        }
        if (filters != null && filters.size() > 0) {
            for (IntentFilter filter : filters) {
                if (filter.hasCategory(Intent.CATEGORY_BROWSABLE)
                        && (filter.hasDataScheme(IntentFilter.SCHEME_HTTP)
                        || filter.hasDataScheme(IntentFilter.SCHEME_HTTPS))) {
                    result.addAll(filter.getHostsList());
                }
            }
        }
        return result;
    }
}
