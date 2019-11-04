/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
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

package com.android.bips.discovery;

import android.net.Uri;
import android.print.PrinterId;
import android.printservice.PrintService;
import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Represents a network-visible printer */
public class DiscoveredPrinter {
    /** UUID (RFC4122) uniquely identifying the print service, or null if not reported */
    public final Uri uuid;

    /** User-visible name for the print service */
    public final String name;

    /** Location of the device or null of not reported */
    public final String location;

    /** Resource path at which the print service can be reached */
    public final Uri path;

    /** All paths at which this this printer can be reached. Includes "path". */
    public final List<Uri> paths;

    /** Lazily-created printer id. */
    private PrinterId mPrinterId;

    /**
     * Construct minimal information about a network printer
     *
     * @param uuid     Unique identification of the network printer, if known
     * @param name     Self-identified printer or service name
     * @param paths    One or more network paths at which the printer is currently available
     * @param location Self-advertised location of the printer, if known
     */
    public DiscoveredPrinter(Uri uuid, String name, List<Uri> paths, String location) {
        this.uuid = uuid;
        this.name = name;
        this.path = paths.get(0);
        this.paths = Collections.unmodifiableList(paths);
        this.location = location;
    }

    /**
     * Construct minimal information about a network printer
     *
     * @param uuid     Unique identification of the network printer, if known
     * @param name     Self-identified printer or service name
     * @param path     Network path at which the printer is currently available
     * @param location Self-advertised location of the printer, if known
     */
    public DiscoveredPrinter(Uri uuid, String name, Uri path, String location) {
        this(uuid, name, Collections.singletonList(path), location);
    }

    /** Construct an object based on field values of an JSON object found next in the JsonReader */
    public DiscoveredPrinter(JsonReader reader) throws IOException {
        String printerName = null, location = null;
        Uri uuid = null, path = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String itemName = reader.nextName();
            switch (itemName) {
                case "uuid":
                    uuid = Uri.parse(reader.nextString());
                    break;
                case "name":
                    printerName = reader.nextString();
                    break;
                case "path":
                    path = Uri.parse(reader.nextString());
                    break;
                case "location":
                    location = reader.nextString();
                    break;
            }
        }
        reader.endObject();

        if (printerName == null || path == null) {
            throw new IOException("Missing name or path");
        }
        this.uuid = uuid;
        this.name = printerName;
        this.path = path;
        this.paths = Collections.singletonList(path);
        this.location = location;
    }

    /**
     * Return the best URI describing this printer: from the UUID (if present) or
     * from the path (if UUID is missing)
     */
    public Uri getUri() {
        return uuid != null ? uuid : path;
    }

    /**
     * Return true if this printer has a secure (encrypted) path.
     */
    public boolean isSecure() {
        for (Uri path : paths) {
            if (path.getScheme().equals("ipps")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return a host string for the user to see (an IP address or hostname without port number)
     */
    public String getHost() {
        return path.getHost().replaceAll(":[0-9]+", "");
    }

    /** Return a generated printer ID based on uuid or (if uuid is missing) its path */
    public PrinterId getId(PrintService printService) {
        if (mPrinterId == null) {
            mPrinterId = printService.generatePrinterId(getUri().toString());
        }
        return mPrinterId;
    }

    /** Writes all serializable fields into JSON form */
    void write(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name("name").value(name);
        writer.name("path").value(path.toString());
        if (uuid != null) {
            writer.name("uuid").value(uuid.toString());
        }
        if (!TextUtils.isEmpty(location)) {
            writer.name("location").value(location);
        }
        writer.endObject();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DiscoveredPrinter)) {
            return false;
        }
        DiscoveredPrinter other = (DiscoveredPrinter) obj;
        return Objects.equals(uuid, other.uuid)
                && Objects.equals(name, other.name)
                && Objects.equals(paths, other.paths)
                && Objects.equals(location, other.location);
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + name.hashCode();
        result = 31 * result + (uuid != null ? uuid.hashCode() : 0);
        result = 31 * result + paths.hashCode();
        result = 31 * result + (location != null ? location.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        try {
            write(new JsonWriter(sw));
        } catch (IOException ignored) {
        }
        return "DiscoveredPrinter" + sw.toString();
    }
}
