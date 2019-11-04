/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.pump.provider;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.android.pump.util.Clog;
import com.android.pump.util.Http;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@WorkerThread
public final class Wikidata {
    private static final String TAG = Clog.tag(Wikidata.class);

    private Wikidata() { }

    public static void search(@NonNull Query query) throws IOException {
        search(query, 1);
    }

    public static void search(@NonNull Query query, int maxResults) throws IOException {
        Clog.i(TAG, "search(" + query + ", " + maxResults + ")");
        getContentForResults(getSearchResults(getQueryString(query), maxResults));
    }

    private static String getQueryString(Query query) {
        StringBuilder builder = new StringBuilder();
        builder.append(query.getName());
        if (query.hasYear()) {
            builder.append(' ');
            builder.append(query.getYear());
        }
        if (query.isEpisode()) {
            builder.append(' ');
            builder.append('s');
            builder.append(query.getSeason());

            builder.append(' ');
            builder.append('e');
            builder.append(query.getEpisode());
        }
        return  builder.toString();
    }

    private static List<String> getSearchResults(String search, int maxResults) throws IOException {
        String uri = getSearchUri(search, maxResults);
        Clog.i(TAG, uri);
        String result = new String(Http.get(uri), StandardCharsets.UTF_8);
        Clog.i(TAG, result);
        try {
            Object root = new JSONTokener(result).nextValue();
            dumpJson(root);
            JSONObject resultRoot = (JSONObject) root;
            JSONArray resultArray = resultRoot.getJSONObject("query").getJSONArray("search");
            List<String> ids = new ArrayList<>(resultArray.length());
            for (int i = 0; i < resultArray.length(); ++i) {
                ids.add(resultArray.getJSONObject(i).getString("title"));
            }
            return ids;
        } catch (JSONException e) {
            Clog.w(TAG, "Failed to parse search result", e);
            throw new IOException(e);
        }
    }

    private static void getContentForResults(List<String> ids) throws IOException {
        /*
        String uri = getContentUri(ids);
        Clog.i(TAG, uri);
        String result = new String(Http.get(uri), StandardCharsets.UTF_8);
        //Clog.i(TAG, result);
        try {
            Object root = new JSONTokener(result).nextValue();
            //dumpJson(root);
            JSONObject resultRoot = (JSONObject) root;
        } catch (JSONException e) {
            Clog.w(TAG, "Failed to parse data", e);
            throw new IOException(e);
        }
        */
        getSparqlForResults(ids);
    }

    private static void getSparqlForResults(List<String> ids) throws IOException {
        String uri = getSparqlUri(ids);
        Clog.i(TAG, uri);
        String result = new String(Http.get(uri), StandardCharsets.UTF_8);
        Clog.i(TAG, result);
        try {
            Object root = new JSONTokener(result).nextValue();
            dumpJson(root);
            JSONObject resultRoot = (JSONObject) root;
        } catch (JSONException e) {
            Clog.w(TAG, "Failed to parse sparql", e);
            throw new IOException(e);
        }
    }

    private static String getSearchUri(String search, int maxResults) {
        Uri.Builder ub = new Uri.Builder();
        ub.scheme("https");
        ub.authority("www.wikidata.org");
        ub.appendPath("w");
        ub.appendPath("api.php");
        ub.appendQueryParameter("action", "query");
        ub.appendQueryParameter("list", "search");
        ub.appendQueryParameter("format", "json");
        ub.appendQueryParameter("formatversion", "2");
        ub.appendQueryParameter("srsearch", search);
        ub.appendQueryParameter("srlimit", Integer.toString(maxResults));
        ub.appendQueryParameter("srprop", "");
        ub.appendQueryParameter("srinfo", "");
        return ub.build().toString();
    }

    /*
     */

    // https://www.wikidata.org/wiki/Wikidata:SPARQL_query_service

    /*
const endpointUrl = 'https://query.wikidata.org/sparql',
      sparqlQuery = `SELECT DISTINCT ?item ?itemLabel ?itemDescription
WHERE
{
  ?item wdt:P31/wdt:P279* wd:Q11424.
  SERVICE wikibase:label { bd:serviceParam wikibase:language "[AUTO_LANGUAGE],en" }
  ?item wdt:P3383 ?itemPoster.
}`,
      fullUrl = endpointUrl + '?query=' + encodeURIComponent( sparqlQuery ),
      headers = { 'Accept': 'application/sparql-results+json' };

fetch( fullUrl, { headers } ).then( body => body.json() ).then( json => {
    const { head: { vars }, results } = json;
    for ( const result of results.bindings ) {
        for ( const variable of vars ) {
            console.log( '%s: %o', variable, result[variable] );
        }
        console.log( '---' );
    }
} );
     */

    // ?action=wbgetentities&ids=Q775450|Q3041294|Q646968|Q434841|Q11920&format=jsonfm&props=labels&languages=en|de|fr
    private static String getContentUri(List<String> ids) {
        Uri.Builder ub = new Uri.Builder();
        ub.scheme("https");
        ub.authority("www.wikidata.org");
        ub.appendPath("w");
        ub.appendPath("api.php");
        ub.appendQueryParameter("action", "wbgetentities");
        ub.appendQueryParameter("props", "labels|descriptions");
        ub.appendQueryParameter("format", "json");
        ub.appendQueryParameter("formatversion", "2");
        ub.appendQueryParameter("languages", "en");
        ub.appendQueryParameter("languagefallback", "");
        ub.appendQueryParameter("ids", TextUtils.join("|", ids));
        return ub.build().toString();
    }

    private static String getSparqlUri(List<String> ids) {
        List<String> dbIds = new ArrayList<>(ids.size());
        for (String id : ids) {
            dbIds.add("wd:" + id);
        }
        String sparqlQuery = ""
                + "SELECT * WHERE {"
                +   "VALUES ?item {"
                +     TextUtils.join(" ", dbIds)
                +   "}"
                +   "FILTER EXISTS {?item wdt:P31/wdt:P279* wd:Q11424.}"
                +   "OPTIONAL {?item wdt:P3383 ?poster.}"
                +   "?item rdfs:label ?title FILTER (lang(?title) = 'en')."
                + "}";
        Uri.Builder ub = new Uri.Builder();
        ub.scheme("https");
        ub.authority("query.wikidata.org");
        ub.appendPath("sparql");
        ub.appendQueryParameter("format", "json");
        ub.appendQueryParameter("query", sparqlQuery);
        return ub.build().toString();
    }

    private static void dumpJson(Object root) throws JSONException {
        dumpJson(null, "", root);
    }

    private static void dumpJson(String name, String indent, Object object) throws JSONException {
        name = name != null ? name + ": " : "";
        if (object == JSONObject.NULL) {
            Clog.d(TAG, indent + name + "null");
        } else if (object instanceof JSONObject) {
            Clog.d(TAG, indent + name + "{");
            JSONObject jsonObject = (JSONObject) object;
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                dumpJson(key, indent + "  ", jsonObject.get(key));
            }
            Clog.d(TAG, indent + "}");
        } else if (object instanceof JSONArray) {
            Clog.d(TAG, indent + name + "[");
            JSONArray jsonArray = (JSONArray) object;
            for (int i = 0; i < jsonArray.length(); ++i) {
                dumpJson(null, indent + "  ", jsonArray.get(i));
            }
            Clog.d(TAG, indent + "]");
        } else if (object instanceof String) {
            String jsonString = (String) object;
            Clog.d(TAG, indent + name + jsonString + " (string)");
        } else if (object instanceof Boolean) {
            Boolean jsonBoolean = (Boolean) object;
            Clog.d(TAG, indent + name + jsonBoolean + " (boolean)");
        } else if (object instanceof Integer) {
            Integer jsonInteger = (Integer) object;
            Clog.d(TAG, indent + name + jsonInteger + " (int)");
        } else if (object instanceof Long) {
            Long jsonLong = (Long) object;
            Clog.d(TAG, indent + name + jsonLong + " (long)");
        } else if (object instanceof Double) {
            Double jsonDouble = (Double) object;
            Clog.d(TAG, indent + name + jsonDouble + " (double)");
        }
    }
}
