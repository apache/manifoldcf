/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.manifoldcf.agents.output.solr;

import org.apache.solr.client.solrj.impl.LBHttpSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.BinaryResponseParser;
import org.apache.solr.client.solrj.request.RequestWriter;
import org.apache.solr.client.solrj.*;
import java.net.MalformedURLException;
import org.apache.http.client.HttpClient;
import java.util.Set;

/** This class overrides and somewhat changes the behavior of the
* SolrJ LBHttpSolrServer class.  This is so it instantiates our modified
* HttpSolrServer class, so that multipart forms work.
*/
public class ModifiedLBHttpSolrClient extends LBHttpSolrClient
{
  private final HttpClient httpClient;
  private final ResponseParser parser;
  private final boolean allowCompression;
  
  public ModifiedLBHttpSolrClient(boolean allowCompression, String... solrServerUrls) throws MalformedURLException {
    this(null, allowCompression, solrServerUrls);
  }
  
  /** The provided httpClient should use a multi-threaded connection manager */ 
  public ModifiedLBHttpSolrClient(HttpClient httpClient, boolean allowCompression, String... solrServerUrl)
          throws MalformedURLException {
    this(httpClient, new BinaryResponseParser(), allowCompression, solrServerUrl);
  }

  /** The provided httpClient should use a multi-threaded connection manager */  
  public ModifiedLBHttpSolrClient(HttpClient httpClient, ResponseParser parser, boolean allowCompression, String... solrServerUrl)
          throws MalformedURLException {
    super(httpClient, parser, solrServerUrl);
    this.httpClient = httpClient;
    this.parser = parser;
    this.allowCompression = allowCompression;
  }
  
  @Override
  protected HttpSolrClient makeSolrClient(String server) {
    HttpSolrClient client = new ModifiedHttpSolrClient(server, httpClient, parser, allowCompression);
    if (getRequestWriter() != null) {
      client.setRequestWriter(getRequestWriter());
    }
    if (getQueryParams() != null) {
      client.setQueryParams(getQueryParams());
    }
    return client;
  }

}
