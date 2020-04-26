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

import org.junit.After;
import org.junit.Before;

import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

/**  
 *  Base integration tests class for Elastic Search tested against a CMIS repository
 *  @author Piergiorgio Lucidi
 * 
 * */
public class BaseITHSQLDB extends org.apache.manifoldcf.crawler.tests.BaseITHSQLDB
{

  final static boolean isUnix;
  static {
    final String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("win")) {
      isUnix = false;
    } else {
      isUnix = true;
    }
  }

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

  Process esTestProcess = null;
  
  @Before
  public void setupElasticSearch()
    throws Exception
  {
    System.out.println("ElasticSearch is starting...");
    //the default port is 9200

    // Call the test-materials script in the appropriate way
    if (isUnix) {
      esTestProcess = Runtime.exec(new String[]{
        "bash", 
        "test-materials/elasticsearch-7.6.2/bin/elasticsearch",
        "-q"},
        null);
    } else {
      esTestProcess = Runtime.exec(new String[]{
        "cmd", 
        "test-materials/elasticsearch-7.6.2/bin/elasticsearch.bat",
        "-q"},
        null);
    }
    
    System.out.println("ElasticSearch is started on port 9200");
  }
  
  
  @After
  public void cleanUpElasticSearch(){
    if (esTestProcess != null) {
      esTestProcess.destroy();
    }
  }
  
}
