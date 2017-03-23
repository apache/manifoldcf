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
    testerInstance.clickButton("Add", true);
    
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
    testerInstance.clickButton("Add", true);
    // Security tab
    testerInstance.clickTab("Security");
    // URL Mapping tab
    testerInstance.clickTab("URL Mappings");
    testerInstance.setValue("s0_rssmatch", "foo");
    testerInstance.setValue("s0_rssmap", "bar");
    testerInstance.clickButton("Add", true);
    testerInstance.clickButton("Remove", true);
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

/*
    testerInstance.newTest(Locale.US);
    
    HTMLTester.Window window;
    HTMLTester.Link link;
    HTMLTester.Form form;
    HTMLTester.Textarea textarea;
    HTMLTester.Selectbox selectbox;
    HTMLTester.Button button;
    HTMLTester.Radiobutton radiobutton;
    HTMLTester.Checkbox checkbox;
    HTMLTester.Loop loop;
    
    window = testerInstance.openMainWindow("http://localhost:8346/mcf-crawler-ui/index.jsp");

    // Login
    form = window.findForm(testerInstance.createStringDescription("loginform"));
    textarea = form.findTextarea(testerInstance.createStringDescription("userID"));
    textarea.setValue(testerInstance.createStringDescription("admin"));
    textarea = form.findTextarea(testerInstance.createStringDescription("password"));
    textarea.setValue(testerInstance.createStringDescription("admin"));
    button = window.findButton(testerInstance.createStringDescription("Login"));
    button.click();
    window = testerInstance.findWindow(null);

    // Define an output connection via the UI
    link = window.findLink(testerInstance.createStringDescription("List output connections"));
    link.click();
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("Add an output connection"));
    link.click();
    // Fill in a name
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editconnection"));
    textarea = form.findTextarea(testerInstance.createStringDescription("connname"));
    textarea.setValue(testerInstance.createStringDescription("MyOutputConnection"));
    link = window.findLink(testerInstance.createStringDescription("Type tab"));
    link.click();
    // Select a type
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editconnection"));
    selectbox = form.findSelectbox(testerInstance.createStringDescription("classname"));
    selectbox.selectValue(testerInstance.createStringDescription("org.apache.manifoldcf.agents.tests.TestingOutputConnector"));
    button = window.findButton(testerInstance.createStringDescription("Continue to next page"));
    button.click();
    // Visit the Throttling tab
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("Throttling tab"));
    link.click();
    // Go back to the Name tab
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("Name tab"));
    link.click();
    // Now save the connection.
    window = testerInstance.findWindow(null);
    button = window.findButton(testerInstance.createStringDescription("Save this output connection"));
    button.click();
    
    // Define a repository connection via the UI
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("List repository connections"));
    link.click();
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("Add a connection"));
    link.click();
    // Fill in a name
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editconnection"));
    textarea = form.findTextarea(testerInstance.createStringDescription("connname"));
    textarea.setValue(testerInstance.createStringDescription("MyRepositoryConnection"));
    link = window.findLink(testerInstance.createStringDescription("Type tab"));
    link.click();
    // Select a type
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editconnection"));
    selectbox = form.findSelectbox(testerInstance.createStringDescription("classname"));
    selectbox.selectValue(testerInstance.createStringDescription("org.apache.manifoldcf.crawler.connectors.webcrawler.WebcrawlerConnector"));
    button = window.findButton(testerInstance.createStringDescription("Continue to next page"));
    button.click();
    // Visit the Throttling tab
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("Throttling tab"));
    link.click();
    // Visit the rest of the tabs - Email first
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("Email tab"));
    link.click();
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editconnection"));
    textarea = form.findTextarea(testerInstance.createStringDescription("email"));
    textarea.setValue(testerInstance.createStringDescription("foo@bar.com"));
    // Robots
    link = window.findLink(testerInstance.createStringDescription("Robots tab"));
    link.click();
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editconnection"));
    selectbox = form.findSelectbox(testerInstance.createStringDescription("robotsusage"));
    selectbox.selectValue(testerInstance.createStringDescription("none"));
    selectbox = form.findSelectbox(testerInstance.createStringDescription("metarobotstagsusage"));
    selectbox.selectValue(testerInstance.createStringDescription("none"));
    // Bandwidth
    link = window.findLink(testerInstance.createStringDescription("Bandwidth tab"));
    link.click();
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editconnection"));
    // Access Credentials
    link = window.findLink(testerInstance.createStringDescription("Access Credentials tab"));
    link.click();
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editconnection"));
    // Certificates
    link = window.findLink(testerInstance.createStringDescription("Certificates tab"));
    link.click();
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editconnection"));
    checkbox = form.findCheckbox(testerInstance.createStringDescription("all_trust"),testerInstance.createStringDescription("true"));
    checkbox.select();
    button = window.findButton(testerInstance.createStringDescription("Add url regular expression for truststore"));
    button.click();
    window = testerInstance.findWindow(null);
    // Go back to the Name tab
    link = window.findLink(testerInstance.createStringDescription("Name tab"));
    link.click();
    // Now save the connection.
    window = testerInstance.findWindow(null);
    button = window.findButton(testerInstance.createStringDescription("Save this connection"));
    button.click();
    
    // Create a job
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("List jobs"));
    link.click();
    // Add a job
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("Add a job"));
    link.click();
    // Fill in a name
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editjob"));
    textarea = form.findTextarea(testerInstance.createStringDescription("description"));
    textarea.setValue(testerInstance.createStringDescription("MyJob"));
    link = window.findLink(testerInstance.createStringDescription("Connection tab"));
    link.click();
    // Select the connections
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editjob"));
    selectbox = form.findSelectbox(testerInstance.createStringDescription("output_connectionname"));
    selectbox.selectValue(testerInstance.createStringDescription("MyOutputConnection"));
    selectbox = form.findSelectbox(testerInstance.createStringDescription("output_precedent"));
    selectbox.selectValue(testerInstance.createStringDescription("-1"));
    button = window.findButton(testerInstance.createStringDescription("Add an output"));
    button.click();
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editjob"));
    selectbox = form.findSelectbox(testerInstance.createStringDescription("connectionname"));
    selectbox.selectValue(testerInstance.createStringDescription("MyRepositoryConnection"));
    button = window.findButton(testerInstance.createStringDescription("Continue to next screen"));
    button.click();
    // Visit all the tabs.  Scheduling tab first
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("Scheduling tab"));
    link.click();
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editjob"));
    selectbox = form.findSelectbox(testerInstance.createStringDescription("dayofweek"));
    selectbox.selectValue(testerInstance.createStringDescription("0"));
    selectbox = form.findSelectbox(testerInstance.createStringDescription("hourofday"));
    selectbox.selectValue(testerInstance.createStringDescription("1"));
    selectbox = form.findSelectbox(testerInstance.createStringDescription("minutesofhour"));
    selectbox.selectValue(testerInstance.createStringDescription("30"));
    selectbox = form.findSelectbox(testerInstance.createStringDescription("monthofyear"));
    selectbox.selectValue(testerInstance.createStringDescription("11"));
    selectbox = form.findSelectbox(testerInstance.createStringDescription("dayofmonth"));
    selectbox.selectValue(testerInstance.createStringDescription("none"));
    textarea = form.findTextarea(testerInstance.createStringDescription("duration"));
    textarea.setValue(testerInstance.createStringDescription("120"));
    button = window.findButton(testerInstance.createStringDescription("Add new schedule record"));
    button.click();
    window = testerInstance.findWindow(null);
    // HopFilters tab
    link = window.findLink(testerInstance.createStringDescription("Hop Filters tab"));
    link.click();
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editjob"));
    // Seeds tab
    link = window.findLink(testerInstance.createStringDescription("Seeds tab"));
    link.click();
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editjob"));
    textarea = form.findTextarea(testerInstance.createStringDescription("s0_seeds"));
    textarea.setValue(testerInstance.createStringDescription("http://www.cnn.com"));
    // Canonicalization tab
    link = window.findLink(testerInstance.createStringDescription("Canonicalization tab"));
    link.click();
    window = testerInstance.findWindow(null);
    button = window.findButton(testerInstance.createStringDescription("Add url regexp"));
    button.click();
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editjob"));
    // Security tab
    link = window.findLink(testerInstance.createStringDescription("Security tab"));
    link.click();
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editjob"));
    // URL Mapping tab
    link = window.findLink(testerInstance.createStringDescription("URL Mappings tab"));
    link.click();
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editjob"));
    form.findTextarea(testerInstance.createStringDescription("s0_rssmatch")).setValue(testerInstance.createStringDescription("foo"));
    form.findTextarea(testerInstance.createStringDescription("s0_rssmap")).setValue(testerInstance.createStringDescription("bar"));
    button = window.findButton(testerInstance.createStringDescription("Add regular expression"));
    button.click();
    window = testerInstance.findWindow(null);
    button = window.findButton(testerInstance.createStringDescription("Remove regular expression #0"));
    button.click();
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editjob"));
    // Metadata tab
    link = window.findLink(testerInstance.createStringDescription("Metadata tab"));
    link.click();
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editjob"));
    // Inclusions tab
    link = window.findLink(testerInstance.createStringDescription("Inclusions tab"));
    link.click();
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editjob"));
    checkbox = form.findCheckbox(testerInstance.createStringDescription("s0_matchinghosts"),
      testerInstance.createStringDescription("true"));
    checkbox.select();
    // Exclusions tab
    link = window.findLink(testerInstance.createStringDescription("Exclusions tab"));
    link.click();
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editjob"));

    // Save the job
    button = window.findButton(testerInstance.createStringDescription("Save this job"));
    button.click();

    // Delete the job
    window = testerInstance.findWindow(null);
    HTMLTester.StringDescription jobID = window.findMatch(testerInstance.createStringDescription("<!--jobid=(.*?)-->"),0);
    testerInstance.printValue(jobID);
    link = window.findLink(testerInstance.createStringDescription("Delete this job"));
    link.click();
    
    // Wait for the job to go away
    loop = testerInstance.beginLoop(120);
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("Manage jobs"));
    link.click();
    window = testerInstance.findWindow(null);
    HTMLTester.StringDescription isJobNotPresent = window.isNotPresent(jobID);
    testerInstance.printValue(isJobNotPresent);
    loop.breakWhenTrue(isJobNotPresent);
    loop.endLoop();
    
    // Delete the repository connection
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("List repository connections"));
    link.click();
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("Delete MyRepositoryConnection"));
    link.click();
    
    // Delete the output connection
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("List output connections"));
    link.click();
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("Delete MyOutputConnection"));
    link.click();
    
    testerInstance.executeTest();
*/
  }
  
}
