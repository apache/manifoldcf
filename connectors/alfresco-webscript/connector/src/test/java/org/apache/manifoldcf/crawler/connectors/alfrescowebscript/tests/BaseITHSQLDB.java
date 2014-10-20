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
package org.apache.manifoldcf.crawler.connectors.alfrescowebscript.tests;

import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

import org.junit.Before;

public class BaseITHSQLDB extends org.apache.manifoldcf.crawler.tests.BaseITHSQLDB
{
  protected Server alfrescoServer = null;

  
  protected String[] getConnectorNames()
  {
    return new String[]{"Alfresco"};
  }
  
  protected String[] getConnectorClasses()
  {
    return new String[]{"org.apache.manifoldcf.crawler.connectors.alfrescowebscript.AlfrescoConnector"};
  }
  
  protected String[] getOutputNames()
  {
    return new String[]{"Null Output"};
  }
  
  protected String[] getOutputClasses()
  {
    return new String[]{"org.apache.manifoldcf.agents.tests.TestingOutputConnector"};
  }

  @Before
  public void setUpAlfresco()
    throws Exception
  {
    alfrescoServer = new Server(9090);
    alfrescoServer.setStopAtShutdown(true);

    String alfrescoServerWarPath = "../../connectors/alfresco-webscript/test-materials-proprietary/alfresco.war";

    if (System.getProperty("alfrescoServerWarPath") != null)
      alfrescoServerWarPath = System.getProperty("alfrescoServerWarPath");

    ContextHandlerCollection contexts = new ContextHandlerCollection();
    alfrescoServer.setHandler(contexts);

    WebAppContext alfrescoServerApi = new WebAppContext(alfrescoServerWarPath,"/alfresco");
    alfrescoServerApi.setParentLoaderPriority(false);
    HashLoginService dummyLoginService = new HashLoginService("TEST-SECURITY-REALM");
    alfrescoServerApi.getSecurityHandler().setLoginService(dummyLoginService);
    contexts.addHandler(alfrescoServerApi);

    alfrescoServer.start();
    boolean entered = false;
    
    while(alfrescoServer.isStarted() 
        && alfrescoServerApi.isStarted()
        && !entered){
      entered = true;
      Thread.sleep(5000);
    }
  }
}
