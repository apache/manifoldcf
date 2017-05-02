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
package org.apache.manifoldcf.crawler.connectors.webcrawler.tests;

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
    testerInstance.navigateTo("List output connections");
    testerInstance.clickButton("Add a new output connection");

    // Fill in a name
    testerInstance.waitForElementWithName("connname");
    testerInstance.setValue("connname","MyOutputConnection");

    //Goto to Type tab
    testerInstance.clickTab("Type");

    // Select a type
    testerInstance.waitForElementWithName("classname");
    testerInstance.selectValue("classname","org.apache.manifoldcf.agents.tests.TestingOutputConnector");
    testerInstance.clickButton("Continue");

    // Go back to the Name tab
    testerInstance.clickTab("Name");

    // Now save the connection.
    testerInstance.clickButton("Save");
    testerInstance.verifyThereIsNoError();

    // Define a repository connection via the UI
    testerInstance.navigateTo("List repository connections");
    testerInstance.clickButton("Add new connection");

    testerInstance.waitForElementWithName("connname");
    testerInstance.setValue("connname","MyRepositoryConnection");

    // Select a type
    testerInstance.clickTab("Type");
    testerInstance.selectValue("classname","org.apache.manifoldcf.crawler.connectors.webcrawler.WebcrawlerConnector");
    testerInstance.clickButton("Continue");

    // Visit the rest of the tabs - Email first
    testerInstance.clickTab("Email");
    testerInstance.setValue("email", "foo@bar.com");
    // Robots
    testerInstance.clickTab("Robots");
    testerInstance.selectValue("robotsusage", "none");
    testerInstance.selectValue("metarobotstagsusage", "none");
    // Bandwidth
    testerInstance.clickTab("Bandwidth");
    // Access Credentials
    testerInstance.clickTab("Access Credentials");
    // Certificates
    testerInstance.clickTab("Certificates");
    testerInstance.clickCheckbox("all_trust");
    testerInstance.clickButton("Add");

    // Go back to the Name tab
    testerInstance.clickTab("Name");

    // Save
    testerInstance.clickButton("Save");
    testerInstance.verifyThereIsNoError();

    // Create a job
    testerInstance.navigateTo("List jobs");
    //Add a job
    testerInstance.clickButton("Add a new job");
    testerInstance.waitForElementWithName("description");
    //Fill in a name
    testerInstance.setValue("description","MyJob");
    testerInstance.clickTab("Connection");

    // Select the connections
    testerInstance.selectValue("output_connectionname","MyOutputConnection");
    testerInstance.selectValue("output_precedent","-1");
    testerInstance.clickButton("Add output",true);
    testerInstance.waitForElementWithName("connectionname");
    testerInstance.selectValue("connectionname","MyRepositoryConnection");

    testerInstance.clickButton("Continue");

    // HopFilters tab
    testerInstance.clickTab("Hop Filters");
    // Seeds tab
    testerInstance.clickTab("Seeds");
    testerInstance.setValue("s0_seeds", "http://www.cnn.com");
    // Canonicalization tab
    testerInstance.clickTab("Canonicalization");
    testerInstance.clickButton("Add");
    // Security tab
    testerInstance.clickTab("Security");
    // URL Mapping tab
    testerInstance.clickTab("URL Mappings");
    testerInstance.setValue("s0_rssmatch", "foo");
    testerInstance.setValue("s0_rssmap", "bar");
    testerInstance.clickButton("Add");
    testerInstance.clickButton("Remove");
    // Metadata tab
    testerInstance.clickTab("Metadata");
    // Inclusions tab
    testerInstance.clickTab("Inclusions");
    testerInstance.clickCheckbox("s0_matchinghosts");
    // Exclusions tab
    testerInstance.clickTab("Exclusions");

    // Save the job
    testerInstance.clickButton("Save");
    testerInstance.verifyThereIsNoError();
    
    testerInstance.waitForPresenceById("job");
    String jobID = testerInstance.getAttributeValueById("job","jobid");

    //Navigate to List Jobs
    testerInstance.navigateTo("List jobs");
    testerInstance.waitForElementWithName("listjobs");

    //Delete the job
    testerInstance.clickButtonByTitle("Delete job " + jobID);
    testerInstance.acceptAlert();
    testerInstance.verifyThereIsNoError();

    //Wait for the job to go away
    testerInstance.waitForJobDeleteEN(jobID, 120);

    // Delete the repository connection
    testerInstance.navigateTo("List repository connections");
    testerInstance.clickButtonByTitle("Delete MyRepositoryConnection");
    testerInstance.acceptAlert();

    // Delete the output connection
    testerInstance.navigateTo("List output connections");
    testerInstance.clickButtonByTitle("Delete MyOutputConnection");
    testerInstance.acceptAlert();

  }

}
