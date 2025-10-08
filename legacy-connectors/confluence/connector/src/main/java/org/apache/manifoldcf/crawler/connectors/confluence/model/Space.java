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

package org.apache.manifoldcf.crawler.connectors.confluence.model;

import org.json.simple.JSONObject;

public class Space {

  private static final String KEY_NAME = "name";
  private static final String KEY_KEY = "key";
  private static final String KEY_TYPE = "type";
  private static final String KEY_URL = "url";
  
  private String key;
  private String name;
  private String type;
  private String url;
  
  public Space() {
    
  }
  
  public String getKey() {
    return key;
  }
  public void setKey(String key) {
    this.key = key;
  }
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }
  public String getType() {
    return type;
  }
  public void setType(String type) {
    this.type = type;
  }
  public String getUrl() {
    return url;
  }
  public void setUrl(String url) {
    this.url = url;
  }
  
  public static Space fromJson(JSONObject spaceJson) {
    Space space = new Space();
    space.key = (spaceJson.get(KEY_KEY)==null)?"":spaceJson.get(KEY_KEY).toString();
    space.name = (spaceJson.get(KEY_NAME)==null)?"":spaceJson.get(KEY_NAME).toString();
    space.type = (spaceJson.get(KEY_TYPE)==null)?"":spaceJson.get(KEY_TYPE).toString();
    space.url = (spaceJson.get(KEY_URL)==null)?"":spaceJson.get(KEY_URL).toString();
    return space;
  }
  
}
