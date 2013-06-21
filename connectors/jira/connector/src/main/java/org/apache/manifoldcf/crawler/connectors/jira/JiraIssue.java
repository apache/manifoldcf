/* $Id$ */

/**
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.manifoldcf.crawler.connectors.jira;

import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import java.io.IOException;
import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.ParseException;

/** An instance of this class represents a Jira issue, and the parser hooks
* needed to extract the data from the JSON event stream we use to parse it.
*/
public class JiraIssue extends JiraJSONResponse {

  // Things we want to parse from a JSON issue response
  protected Date createdDate;
  protected Date updatedDate;
  // Can't use streaming for these; we need the entire object BEFORE we start
  // looking at content.
  protected String description;
  protected String summary;
  protected Map<String,String> metadataMap = new HashMap<String,String>();
  
  // Specific keys we care about
  private final static String KEY_FIELDS = "fields";
  private final static String KEY_CREATED = "created";
  private final static String KEY_UPDATED = "updated";
  private final static String KEY_DESCRIPTION = "description";
  private final static String KEY_SUMMARY = "summary";

  // Parsing
  private List<String> currentKeys = new ArrayList<String>();
    
  public JiraIssue() {
  }

  public Date getCreatedDate() {
    return createdDate;
  }
  
  public Date getUpdatedDate() {
    return updatedDate;
  }
  
  public String getDescription() {
    return description;
  }
  
  public String getSummary() {
    return summary;
  }
  
  public Map<String,String> getMetadata() {
    return metadataMap;
  }
  
  /*
  protected static HashMap<String, String> addMetaDataToMap(String parent, JSONObject cval, HashMap<String, String> currentMap) {
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
  */
  
  /**
  * Receive notification of the beginning of a JSON object.
  *
  * @return false if the handler wants to stop parsing after return.
  * @throws ParseException
  *          - JSONParser will stop and throw the same exception to the caller when receiving this exception.
  * @see #endJSON
  */
  @Override
  public boolean startObject() throws ParseException, IOException {
    return super.startObject();
  }
       
  /**
  * Receive notification of the end of a JSON object.
  *
  * @return false if the handler wants to stop parsing after return.
  * @throws ParseException
  *
  * @see #startObject
  */
  @Override
  public boolean endObject() throws ParseException, IOException {
    return super.endObject();
  }
       
  /**
  * Receive notification of the beginning of a JSON object entry.
  *
  * @param key - Key of a JSON object entry.
  *
  * @return false if the handler wants to stop parsing after return.
  * @throws ParseException
  *
  * @see #endObjectEntry
  */
  @Override
  public boolean startObjectEntry(String key) throws ParseException, IOException {
    currentKeys.add(key);
    return super.startObjectEntry(key);
  }
       
  /**
  * Receive notification of the end of the value of previous object entry.
  *
  * @return false if the handler wants to stop parsing after return.
  * @throws ParseException
  *
  * @see #startObjectEntry
  */
  @Override
  public boolean endObjectEntry() throws ParseException, IOException {
    currentKeys.remove(currentKeys.size()-1);
    return super.endObjectEntry();
  }
       
  /**
  * Receive notification of the beginning of a JSON array.
  *
  * @return false if the handler wants to stop parsing after return.
  * @throws ParseException
  *
  * @see #endArray
  */
  @Override
  public boolean startArray() throws ParseException, IOException {
    return super.startArray();
  }

  /**
  * Receive notification of the end of a JSON array.
  *
  * @return false if the handler wants to stop parsing after return.
  * @throws ParseException
  *
  * @see #startArray
  */
  @Override
  public boolean endArray() throws ParseException, IOException {
    return super.endArray();
  }

  /**
  * Receive notification of the JSON primitive values:
  *      java.lang.String,
  *      java.lang.Number,
  *      java.lang.Boolean
  *      null
  *
  * @param value - Instance of the following:
  *                      java.lang.String,
  *                      java.lang.Number,
  *                      java.lang.Boolean
  *                      null
  *
  * @return false if the handler wants to stop parsing after return.
  * @throws ParseException
  */
  @Override
  public boolean primitive(Object value) throws ParseException, IOException {
    return super.primitive(value);
  }

}
