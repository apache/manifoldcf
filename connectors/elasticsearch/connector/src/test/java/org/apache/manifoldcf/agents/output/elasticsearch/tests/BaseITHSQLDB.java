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
package org.apache.manifoldcf.agents.output.elasticsearch.tests;

import org.elasticsearch.node.Node;
import org.junit.After;
import org.junit.Before;

import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

import static org.elasticsearch.node.NodeBuilder.*;

/**  
 *  Base integration tests class for Elastic Search tested against a CMIS repository
 *  @author Piergiorgio Lucidi
 * 
 * */
public class BaseITHSQLDB extends org.apache.manifoldcf.crawler.tests.BaseITHSQLDB
{
  protected Node node = null;

  protected String[] getConnectorNames()
  {
    return new String[]{"CMIS"};
  }
  
  protected String[] getConnectorClasses()
  {
    return new String[]{"org.apache.manifoldcf.crawler.tests.TestingRepositoryConnector"};
  }

  protected String[] getOutputNames()
  {
    return new String[]{"ElasticSearch"};
  }
  
  protected String[] getOutputClasses()
  {
    return new String[]{"org.apache.manifoldcf.agents.output.elasticsearch.ElasticSearchConnector"};
  }

  @Before
  public void setupElasticSearch()
    throws Exception
  {
    //Initialize ElasticSearch server
    //the default port is 9200
    System.out.println("ElasticSearch is starting...");
    node = nodeBuilder().local(true).node();
    System.out.println("ElasticSearch is started on port 9200");
  }
  
  
  @After
  public void cleanUpElasticSearch(){
    if(node!=null)
      node.close();
  }
  
}
