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

package com.android.car.settings.system;

import android.content.Context;

import com.android.car.settings.common.Logger;
import com.android.car.settingslib.loader.AsyncLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * LicenseHtmlLoader is a loader which loads a license html file from default license xml files.
 */
public class LicenseHtmlLoader extends AsyncLoader<File> {
    private static final Logger LOG = new Logger(LicenseHtmlLoader.class);

    private static final String[] DEFAULT_LICENSE_XML_PATHS = {
            "/system/etc/NOTICE.xml.gz",
            "/vendor/etc/NOTICE.xml.gz",
            "/odm/etc/NOTICE.xml.gz",
            "/oem/etc/NOTICE.xml.gz"};
    private static final String NOTICE_HTML_FILE_NAME = "NOTICE.html";

    private final Context mContext;

    public LicenseHtmlLoader(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public File loadInBackground() {
        return generateHtmlFromDefaultXmlFiles();
    }

    private File generateHtmlFromDefaultXmlFiles() {
        final List<File> xmlFiles = getVaildXmlFiles();
        if (xmlFiles.isEmpty()) {
            LOG.e("No notice file exists.");
            return null;
        }

        File cachedHtmlFile = getCachedHtmlFile();
        if (!isCachedHtmlFileOutdated(xmlFiles, cachedHtmlFile)
                || generateHtmlFile(xmlFiles, cachedHtmlFile)) {
            return cachedHtmlFile;
        }

        return null;
    }

    private List<File> getVaildXmlFiles() {
        final List<File> xmlFiles = new ArrayList();
        for (final String xmlPath : DEFAULT_LICENSE_XML_PATHS) {
            File file = new File(xmlPath);
            if (file.exists() && file.length() != 0) {
                xmlFiles.add(file);
            }
        }
        return xmlFiles;
    }

    private File getCachedHtmlFile() {
        return new File(mContext.getCacheDir(), NOTICE_HTML_FILE_NAME);
    }

    private boolean isCachedHtmlFileOutdated(List<File> xmlFiles, File cachedHtmlFile) {
        boolean outdated = true;
        if (cachedHtmlFile.exists() && cachedHtmlFile.length() != 0) {
            outdated = false;
            for (File file : xmlFiles) {
                if (cachedHtmlFile.lastModified() < file.lastModified()) {
                    outdated = true;
                    break;
                }
            }
        }
        return outdated;
    }

    private boolean generateHtmlFile(List<File> xmlFiles, File htmlFile) {
        return LicenseHtmlGeneratorFromXml.generateHtml(xmlFiles, htmlFile);
    }
}


