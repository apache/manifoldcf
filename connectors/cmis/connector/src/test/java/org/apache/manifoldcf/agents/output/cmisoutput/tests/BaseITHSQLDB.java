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
package org.apache.manifoldcf.agents.output.cmisoutput.tests;

import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.crawler.connectors.cmis.tests.CMISServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Tests that run the "agents daemon" should be derived from this 
 * 
 *  @author Piergiorgio Lucidi
 * 
 * */
public class BaseITHSQLDB extends org.apache.manifoldcf.crawler.tests.BaseITHSQLDB
{
  protected CMISServer sourceCmisServer = null;
  protected CMISServer targetCmisServer = null;

  
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
    return new String[]{"CMIS"};
  }
  
  protected String[] getOutputClasses()
  {
    return new String[]{"org.apache.manifoldcf.agents.output.cmisoutput.CmisOutputConnector"};
  }
  
  // Setup/teardown
  
  @Before
  public void setUpCMIS()
    throws Exception {
  	
  	String openCmisServerWarPath = "../../../lib/chemistry-opencmis-server-inmemory.war";
    if (StringUtils.isNotEmpty(System.getProperty("openCmisServerWarPath"))) {
      openCmisServerWarPath = System.getProperty("openCmisServerWarPath");
    }
  	
  	//CMIS Source repo server on port 9091
    sourceCmisServer = new CMISServer(9091, openCmisServerWarPath);
    sourceCmisServer.start();
    
    //CMIS target repo server on port 9092
    targetCmisServer = new CMISServer(9092, openCmisServerWarPath);
    targetCmisServer.start();
    
  }
  
  @After
  public void cleanUpCMIS()
    throws Exception {
  	sourceCmisServer.stop();
  	targetCmisServer.stop();
  }
  
}
