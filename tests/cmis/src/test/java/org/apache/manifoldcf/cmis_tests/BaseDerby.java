/* $Id: BaseDerby.java 1225812 2011-30-12 13:08:38Z kwright $ */

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
package org.apache.manifoldcf.cmis_tests;

import org.apache.manifoldcf.crawler.system.ManifoldCF;
import org.junit.After;
import org.junit.Before;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.webapp.WebAppContext;

/** Tests that run the "agents daemon" should be derived from this 
 * 
 *  @author Piergiorgio Lucidi
 * 
 * */
public class BaseDerby extends org.apache.manifoldcf.crawler.tests.BaseITDerby
{
  protected Server cmisServer = null;

  
  protected String[] getConnectorNames()
  {
    return new String[]{"CMIS"};
  }
  
  protected String[] getConnectorClasses()
  {
    return new String[]{"org.apache.manifoldcf.crawler.connectors.cmis.CmisRepositoryConnector"};
  }
  
  protected String[] getOutputNames()
  {
    return new String[]{"Null Output"};
  }
  
  protected String[] getOutputClasses()
  {
    return new String[]{"org.apache.manifoldcf.agents.output.nullconnector.NullConnector"};
  }
  
  // Setup/teardown
  
  @Before
  public void setUpCMIS()
    throws Exception
  {
    cmisServer = new Server(9090);
    cmisServer.setStopAtShutdown(true);

    String openCmisServerWarPath = "../../lib/chemistry-opencmis-server-inmemory-war.war";

    if (System.getProperty("openCmisServerWarPath") != null)
      openCmisServerWarPath = System.getProperty("openCmisServerWarPath");
    
    //Initialize OpenCMIS Server bindings
    WebAppContext openCmisServerApi = new WebAppContext(openCmisServerWarPath,"/chemistry-opencmis-server-inmemory-war");
    openCmisServerApi.setParentLoaderPriority(false);
    cmisServer.addHandler(openCmisServerApi);
    
    cmisServer.start();
    boolean entered = false;
    
    while(cmisServer.isStarted() 
        && openCmisServerApi.isStarted()
        && !entered){
      entered = true;
      ManifoldCF.sleep(5000);
    }
  }
  
  @After
  public void cleanUpCMIS()
    throws Exception
  {
  }
  
}
