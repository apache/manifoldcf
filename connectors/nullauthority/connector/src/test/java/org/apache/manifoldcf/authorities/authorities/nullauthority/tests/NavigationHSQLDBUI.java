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
package org.apache.manifoldcf.authorities.authorities.nullauthority.tests;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.ManifoldCF;

import java.io.*;
import java.util.*;
import org.junit.*;

import org.apache.manifoldcf.core.tests.SeleniumTester;

/** Basic UI navigation tests */
public class NavigationHSQLDBUI extends BaseUIHSQLDB
{

  @Test
  public void createConnectionsAndJob()
    throws Exception
  {
    testerInstance.start(SeleniumTester.BrowserType.CHROME, "en-US", "http://localhost:8346/mcf-crawler-ui/index.jsp");

    //Login
    testerInstance.waitForElementWithName("loginform");
    testerInstance.setValue("userID","admin");
    testerInstance.setValue("password","admin");
    testerInstance.clickButton("Login");
    testerInstance.verifyHeader("Welcome to Apache ManifoldCF™");

    // Add an authority group
    testerInstance.navigateTo("List authority groups");
    testerInstance.clickButton("Add a new authority group");

    // Fill in a name
    testerInstance.waitForElementWithName("groupname");
    testerInstance.setValue("groupname","MyAuthorityGroup");

    // Save the authority group
    testerInstance.clickButton("Save");
    testerInstance.verifyThereIsNoError();

    // Add an authority
    testerInstance.navigateTo("List authorities");
    testerInstance.clickButton("Add a new connection");

    // Fill in a name
    testerInstance.waitForElementWithName("connname");
    testerInstance.setValue("connname","MyAuthorityConnection");

    // Select a type
    testerInstance.clickTab("Type");
    testerInstance.selectValue("classname","org.apache.manifoldcf.authorities.authorities.nullauthority.NullAuthority");
    testerInstance.selectValue("authoritygroup", "MyAuthorityGroup");
    testerInstance.clickButton("Continue");
    
    // Back to the name tab
    testerInstance.clickTab("Name");
    
    // Now, save
    testerInstance.clickButton("Save");
    testerInstance.verifyThereIsNoError();

    // Delete the authority connection
    testerInstance.navigateTo("List authorities");
    testerInstance.clickButtonByTitle("Delete MyAuthorityConnection");
    testerInstance.acceptAlert();

    // Delete the authority group
    testerInstance.navigateTo("List authority groups");
    testerInstance.clickButtonByTitle("Delete MyAuthorityGroup");
    testerInstance.acceptAlert();
    
  }
  
}
