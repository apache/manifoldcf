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

import java.util.ArrayList;
import java.util.List;

import org.apache.manifoldcf.crawler.connectors.confluence.model.builder.ConfluenceResourceBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ConfluenceResponse<T extends ConfluenceResource> {

  private List<T> results;
  private int start;
  private int limit;
  private Boolean isLast;
  
  public ConfluenceResponse(List<T> results, int start, int limit, Boolean isLast) {
    this.results = results;
    this.start = start;
    this.limit = limit;
    this.isLast = isLast;
  }
  
  public List<T> getResults() {
    return this.results;
  }
  
  public int getStart() {
    return this.start;
  }
  
  public int getLimit() {
    return this.limit;
  }
  
  public Boolean isLast() {
    return isLast;
  }
  
  public static <T extends ConfluenceResource> ConfluenceResponse<T> fromJson(JSONObject response, ConfluenceResourceBuilder<T> builder) {
    List<T> resources = new ArrayList<T>();
    try {
      JSONArray jsonArray = response.getJSONArray("results");
      for(int i=0,size=jsonArray.length(); i<size;i++) {
        JSONObject jsonPage = jsonArray.getJSONObject(i);
        T resource = (T) builder.fromJson(jsonPage);
        resources.add(resource);
      }
      
      int limit = response.getInt("limit");
      int start = response.getInt("start");
      Boolean isLast = false;
      JSONObject links = response.getJSONObject("_links");
      if(links != null) {
        isLast = links.optString("next", "undefined").equalsIgnoreCase("undefined");
      }
      
      return new ConfluenceResponse<T>(resources, start, limit, isLast);
      
    } catch (JSONException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    return new ConfluenceResponse<T>(new ArrayList<T>(), 0,0,false);
  }
}
