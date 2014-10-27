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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.manifoldcf.crawler.connectors.dropbox;

import org.apache.manifoldcf.core.common.*;
import org.apache.manifoldcf.connectorcommon.common.*;

import com.dropbox.client2.session.AppKeyPair;
import java.util.Map;
import com.dropbox.client2.session.WebAuthSession;
import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.DeltaEntry;
import com.dropbox.client2.DropboxAPI.DropboxInputStream;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.jsonextract.JsonExtractionException;
import com.dropbox.client2.jsonextract.JsonList;
import com.dropbox.client2.jsonextract.JsonMap;
import com.dropbox.client2.jsonextract.JsonThing;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.Session;
import com.dropbox.client2.session.WebAuthSession;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.json.simple.parser.ParseException;

/**
 *
 * @author andrew
 */
public class DropboxSession {

  private DropboxAPI<?> client;
  
  public DropboxSession(String app_key, String app_secret, String key, String secret) {
    AppKeyPair appKeyPair = new AppKeyPair(app_key, app_secret);
    WebAuthSession session = new WebAuthSession(appKeyPair, WebAuthSession.AccessType.DROPBOX);
    AccessTokenPair ac = new AccessTokenPair(key, secret);
    session.setAccessTokenPair(ac);
    client = new DropboxAPI<WebAuthSession>(session);
  }

  public Map<String, String> getRepositoryInfo() throws DropboxException {
    Map<String, String> info = new HashMap<String, String>();

    info.put("Country", client.accountInfo().country);
    info.put("Display Name", client.accountInfo().displayName);
    info.put("Referral Link", client.accountInfo().referralLink);
    info.put("Quota", String.valueOf(client.accountInfo().quota));
    info.put("Quota Normal", String.valueOf(client.accountInfo().quotaNormal));
    info.put("Quota Shared", String.valueOf(client.accountInfo().quotaShared));
    info.put("Uid", String.valueOf(client.accountInfo().uid));
    return info;
  }

  public void getSeeds(XThreadStringBuffer idBuffer, String path, int max_dirs)
    throws DropboxException, InterruptedException {

    idBuffer.add(path); //need to add root dir so that single files such as /file1 will still get read
        
        
    DropboxAPI.Entry root_entry = client.metadata(path, max_dirs, null, true, null);
    List<DropboxAPI.Entry> entries = root_entry.contents; //gets a list of the contents of the entire folder: subfolders + files

    // Apply the entries one by one.
    for (DropboxAPI.Entry e : entries) {
      if (e.isDir) { //only add the directories as seeds, we'll add the files later
        idBuffer.add(e.path);
      }
    }
  }
  
  public DropboxAPI.Entry getObject(String id) throws DropboxException {
    return client.metadata(id, 25000, null, true, null);
  }

  public DropboxInputStream getDropboxInputStream(String id) throws DropboxException {
    return client.getFileStream(id, null);
  }
  
  public void close() {
    // MHL
  }
}
