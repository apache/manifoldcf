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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.HttpEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.Header;
import org.apache.http.util.EntityUtils;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;

import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.core.common.Base64;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.util.URLEncoder;

public class OpenSearchServerScheduler extends OpenSearchServerConnection {

  private final static String PATH = "services/rest/index/{index_name}/scheduler/{scheduler_name}/run";
  
  public OpenSearchServerScheduler(HttpClient client, OpenSearchServerConfig config, String schedulerName)
    throws ManifoldCFException {
    super(client, config);
    String path = StringUtils.replace(PATH, "{index_name}", URLEncoder.encode(config.getIndexName()));
    path = StringUtils.replace(path, "{scheduler_name}", URLEncoder.encode(schedulerName));
    StringBuffer url = getApiUrlV2(path);
    HttpPut put = new HttpPut(url.toString());
    put.setEntity(new StringEntity("{}", ContentType.APPLICATION_JSON));
    call(put);
  }

}
