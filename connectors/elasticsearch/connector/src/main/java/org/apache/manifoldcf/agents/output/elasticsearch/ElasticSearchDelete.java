/* $Id: ElasticSearchDelete.java 1299512 2012-03-12 00:58:38Z piergiorgio $ */

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

package org.apache.manifoldcf.agents.output.elasticsearch;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;

import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.crawler.system.Logging;

public class ElasticSearchDelete extends ElasticSearchConnection
{

  public ElasticSearchDelete(HttpClient client, ElasticSearchConfig config)
  {
    super(config, client);
  }
  
  public void execute(String documentURI)
      throws ManifoldCFException, ServiceInterruption
  {
    try
    {
      String idField = java.net.URLEncoder.encode(documentURI,"utf-8");
      HttpDelete method = new HttpDelete(config.getServerLocation() +
          "/" + config.getIndexName() + "/" + config.getIndexType()
          + "/" + idField);
      call(method);
      if ("true".equals(checkJson(jsonStatus)))
        return;
      // We thought we needed to delete, but ElasticSearch disagreed.
      // Log the result as an error, but proceed anyway.
      setResult(Result.ERROR, checkJson(jsonException));
      Logging.connectors.warn("ES: Delete failed: "+getResponse());
    }
    catch (java.io.UnsupportedEncodingException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }
}
