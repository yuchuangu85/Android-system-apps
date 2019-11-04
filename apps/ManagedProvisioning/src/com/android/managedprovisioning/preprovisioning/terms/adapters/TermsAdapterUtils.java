/*
 * Copyright 2018, The Android Open Source Project
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
package com.android.managedprovisioning.preprovisioning.terms.adapters;

import android.annotation.ColorInt;
import android.content.Context;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ClickableSpanFactory;
import com.android.managedprovisioning.common.HtmlToSpannedParser;
import com.android.managedprovisioning.preprovisioning.WebActivity;
import com.android.managedprovisioning.preprovisioning.terms.TermsDocument;

/**
 * Utils for adapters displaying terms
 */
final class TermsAdapterUtils {

    /**
     * Populate a given text view with the contents of the term
     *
     * @param context the calling activity's context
     * @param contentTextView text view to display the term contents
     * @param disclaimer the term document that contains the contents
     */
    public static void populateContentTextView(Context context, TextView contentTextView,
            TermsDocument disclaimer, @ColorInt int statusBarColor) {
        HtmlToSpannedParser htmlToSpannedParser = new HtmlToSpannedParser(
                new ClickableSpanFactory(context.getColor(R.color.blue_text)),
                url -> WebActivity.createIntent(context, url, statusBarColor));
        Spanned content = htmlToSpannedParser.parseHtml(disclaimer.getContent());
        contentTextView.setText(content);
        contentTextView.setContentDescription(
                context.getResources().getString(R.string.section_content, disclaimer.getHeading(),
                        content));
        // makes html links clickable
        contentTextView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private TermsAdapterUtils() {
    }
}
