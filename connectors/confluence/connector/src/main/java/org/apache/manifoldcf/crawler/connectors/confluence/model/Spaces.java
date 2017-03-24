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

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Spaces extends ArrayList<Space> {

  private static Logger logger = LoggerFactory.getLogger(Spaces.class);
  /**
   * 
   */
  private static final long serialVersionUID = -5334215263162816914L;

  
  public static Spaces fromJson(JSONArray jsonSpaces) {
    Spaces spaces = new Spaces();
    for(int i=0,len=jsonSpaces.size();i<len;i++) {
      JSONObject spaceJson = (JSONObject)jsonSpaces.get(i);
      Space space = Space.fromJson(spaceJson);
      spaces.add(space);
    }
    
    return spaces;
    
  }
}
