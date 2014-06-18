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

import org.apache.solr.client.solrj.impl.LBHttpSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
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
public class ModifiedLBHttpSolrServer extends LBHttpSolrServer
{
  private final HttpClient httpClient;
  private final ResponseParser parser;
  
  public ModifiedLBHttpSolrServer(String... solrServerUrls) throws MalformedURLException {
    this(null, solrServerUrls);
  }
  
  /** The provided httpClient should use a multi-threaded connection manager */ 
  public ModifiedLBHttpSolrServer(HttpClient httpClient, String... solrServerUrl)
          throws MalformedURLException {
    this(httpClient, new BinaryResponseParser(), solrServerUrl);
  }

  /** The provided httpClient should use a multi-threaded connection manager */  
  public ModifiedLBHttpSolrServer(HttpClient httpClient, ResponseParser parser, String... solrServerUrl)
          throws MalformedURLException {
    super(httpClient, parser, solrServerUrl);
    this.httpClient = httpClient;
    this.parser = parser;
  }
  
  @Override
  protected HttpSolrServer makeServer(String server) {
    HttpSolrServer s = new ModifiedHttpSolrServer(server, httpClient, parser);
    RequestWriter r = getRequestWriter();
    Set<String> qp = getQueryParams();
    if (r != null) {
      s.setRequestWriter(r);
    }
    if (qp != null) {
      s.setQueryParams(qp);
    }
    return s;
  }

}
