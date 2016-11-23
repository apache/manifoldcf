/* $Id: DefaultAuthenticator.java 1688076 2015-06-28 23:04:30Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.nuxeo.model;

import java.util.ArrayList;
import java.util.List;

import org.apache.manifoldcf.crawler.connectors.nuxeo.model.builder.NuxeoResourceBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author David Arroyo Escobar <arroyoescobardavid@gmail.com>
 *
 */
public class Acl extends Document {

  public static final String KEY_ACL = "acl";
  public static final String KEY_ACE = "ace";

  public static final String KEY_NAME = "username";
  public static final String KEY_GRANTED = "granted";
  public static final String KEY_STATUS = "status";

  protected List<Ace> aces = new ArrayList<Ace>();;

  public List<Ace> getAces() {
    return aces;
  }

  public static NuxeoResourceBuilder<? extends Acl> builder() {
    return new AclBuilder();
  }

  public static class AclBuilder implements NuxeoResourceBuilder<Acl> {

    public Acl fromJson(JSONObject jsonDocument) {
      return fromJson(jsonDocument, new Acl());
    }

    public Acl fromJson(JSONObject jsonDocument, Acl acl) {

      try {

        JSONArray  aclArray = jsonDocument.getJSONArray(KEY_ACL);
        
        for (int i = 0; i < aclArray.length(); i++) {
          JSONObject aclObj = aclArray.getJSONObject(i);
          JSONArray aceArray = aclObj.getJSONArray(KEY_ACE);
          
          for (int j = 0; j < aceArray.length(); j++) {
            Ace ace = new Ace();
            JSONObject aceObj = aceArray.getJSONObject(j);
            
            ace.setName(aceObj.getString(KEY_NAME));
            ace.setGranted(aceObj.getBoolean(KEY_GRANTED));
            ace.setStatus(aceObj.getString(KEY_STATUS));
            
            acl.aces.add(ace);
          }

          
        }
        
        return acl;

      } catch (JSONException e) {
        e.printStackTrace();
      }

      return new Acl();

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.manifoldcf.crawler.connectors.nuxeo.model.builder.
     * NuxeoResourceBuilder#getType()
     */
    @Override
    public Class<Acl> getType() {
      return Acl.class;
    }

  }
}
