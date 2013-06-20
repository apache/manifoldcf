/* $Id: JiraSession.java 1490586 2013-06-07 11:14:52Z kwright $ */
/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
* http://www.apache.org/licenses/LICENSE-2.0
 * 
* Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.manifoldcf.crawler.connectors.jira;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import org.apache.manifoldcf.core.common.*;

import java.util.Map;
import org.apache.commons.codec.binary.Base64;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;


import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.JSONArray;

/**
 *
 * @author andrew
 */
public class JiraSession {

  private static String APPNAME = "ManifoldCF Jira Connector";
  private String URLbase;
  private String authEnc;
  private static String apiPost = "/rest/api/2/";

  /**
   * Constructor. Create a session.
   */
  public JiraSession(String clientId, String clientSecret, String URLbase) {
    if ("".equals(clientId) && "".equals(clientSecret)) {
      authEnc = "";
    } else {
      String authString = clientId + ":" + clientSecret;
      authEnc = Base64.encodeBase64String(authString.getBytes());
    }
    this.URLbase = URLbase;
  }

  /**
   * Close session.
   */
  public void close() {
    // MHL - figure out what is needed
  }

  private JSONObject getRest(String rightside) throws IOException {
    URL url = new URL(URLbase + apiPost + rightside);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setRequestProperty("Accept", "application/json");

    if (authEnc.length() > 0) {
      conn.setRequestProperty("Authorization", "Basic " + authEnc);
    }

    if (conn.getResponseCode() != 200) {
      throw new RuntimeException("Failed : HTTP error code : "
          + conn.getResponseCode());
    }
    BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
    Object obj = JSONValue.parse(br);
    JSONObject jsonobj = (JSONObject) obj;
    return jsonobj;
  }

  /**
   * Obtain repository information.
   */
  public Map<String, String> getRepositoryInfo() throws IOException {
    HashMap<String, String> statistics = new HashMap<String, String>();
    JSONObject jsonobj = getRest("search?maxResults=1&jql=");
    statistics.put("Total Issues", jsonobj.get("total") + "");
    return statistics;
  }

  /**
   * Get the list of matching root documents, e.g. seeds.
   */
  public void getSeeds(XThreadStringBuffer idBuffer, String jiraDriveQuery)
      throws IOException, InterruptedException {
    JSONObject jsonobj;
    int startAt = 0;
    int setSize = 100;
    Long total = 0l;
    do {
      jsonobj = getRest("search?maxResults=" + setSize + "&startAt=" + startAt + "&jql=" + URLEncoder.encode(jiraDriveQuery, "ISO-8859-1"));
      total = (Long) (jsonobj.get("total"));
      JSONArray issues = (JSONArray) jsonobj.get("issues");
      for (Object issuei : issues) {
        JSONObject issue = (JSONObject) issuei;
        idBuffer.add(issue.get("id") + "");
      }
      startAt += setSize;
    } while (startAt < total); //results in a little overlap
  }

  /**
   * Get an individual document.
   */
  public JSONObject getObject(String id) throws IOException {
    return getRest("issue/" + id);
  }

  static public HashMap<String, String> addMetaDataToMap(String parent, JSONObject cval, HashMap<String, String> currentMap) {
    String append="";
    if (parent.length() > 0) {
      append=parent+"_";
    }
    for (Object key : cval.keySet()) {
      Object value = cval.get(key);
      if (value == null) {
        continue;
      }
      //System.out.println(key);
      if (JSONObject.class.isInstance(value)) {
        currentMap = addMetaDataToMap(append + key, (JSONObject) value, currentMap);
      } else if (JSONArray.class.isInstance(value)) {
        for (Object subpiece : (JSONArray) (value)) {
          if (String.class.isInstance(subpiece)) {
            currentMap.put(append + key, subpiece.toString());
          } else {
            currentMap = addMetaDataToMap(append + key, (JSONObject) subpiece, currentMap);
          }
        }
      } else {
          currentMap.put(append+key + "", value.toString());
      }
    }
    return currentMap;
  }

  public InputStream getJiraInputStream(JSONObject jiraFile) {
    return new ByteArrayInputStream(getJiraBody(jiraFile).getBytes());
  }
  
  static public String getJiraBody(JSONObject jiraFile){
    String body;
    Object possibleBody = ((JSONObject) jiraFile.get("fields")).get("description");
    if (possibleBody == null) {
      body = ((JSONObject) jiraFile.get("fields")).get("summary").toString();
    } else {
      body = possibleBody.toString();
    }
    return body;
  }
}
