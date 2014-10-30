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

package org.apache.manifoldcf.agents.output.opensearchserver;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import org.apache.manifoldcf.agents.interfaces.IOutputHistoryActivity;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.util.URLEncoder;

public class OpenSearchServerDelete extends OpenSearchServerConnection {

  public OpenSearchServerDelete(HttpClient client, String documentURI,
      OpenSearchServerConfig config) throws ManifoldCFException {
    super(client, config);
    StringBuffer url = getApiUrl("delete");
    url.append("&uniq=");
    url.append(URLEncoder.encode(documentURI));
    HttpGet method = new HttpGet(url.toString());
    call(method);
    if ("OK".equals(checkXPath(xPathStatus)))
      return;
    setResult("XPATHEXCEPTION",Result.ERROR, checkXPath(xPathException));
  }
}
