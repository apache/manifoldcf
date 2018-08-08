/* $Id$ */

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.manifoldcf.agents.output.mongodboutput.tests;

import org.apache.manifoldcf.core.tests.SeleniumTester;
import org.junit.Test;

/**
 * Basic UI navigation tests
 */
public class NavigationHSQLDBUI extends BaseUIHSQLDB {

    @Test
    public void createConnectionsAndJob()
            throws Exception {
        testerInstance.start(SeleniumTester.BrowserType.CHROME, "en-US", "http://localhost:8346/mcf-crawler-ui/index.jsp");

        //Login
        testerInstance.waitForElementWithName("loginform");
        testerInstance.setValue("userID", "admin");
        testerInstance.setValue("password", "admin");
        testerInstance.clickButton("Login");
        testerInstance.verifyHeader("Welcome to Apache ManifoldCF™");
        testerInstance.navigateTo("List output connections");
        testerInstance.clickButton("Add a new output connection");

        // Fill in a name
        testerInstance.waitForElementWithName("connname");
        testerInstance.setValue("connname", "MyOutputConnection");

        //Goto to Type tab
        testerInstance.clickTab("Type");

        // Select a type
        testerInstance.waitForElementWithName("classname");
        testerInstance.selectValue("classname", "org.apache.manifoldcf.agents.output.mongodboutput.MongodbOutputConnector");
        testerInstance.clickButton("Continue");

        // Visit the Throttling tab
        testerInstance.clickTab("Throttling");

        // Parameters tab
        testerInstance.clickTab("Parameters");
        testerInstance.setValue("host", "localhost");
        testerInstance.setValue("port", "27017");
        testerInstance.setValue("username", "mongoadmin");
        testerInstance.setValue("password", "secret");
        testerInstance.setValue("database", "testDatabase");
        testerInstance.setValue("collection", "testCollection");

        // Go back to the Name tab
        testerInstance.clickTab("Name");

        // Now save the connection.
        testerInstance.clickButton("Save");
        testerInstance.verifyThereIsNoError();

        // Define a repository connection via the UI
        testerInstance.navigateTo("List repository connections");
        testerInstance.clickButton("Add new connection");

        testerInstance.waitForElementWithName("connname");
        testerInstance.setValue("connname", "MyRepositoryConnection");

        // Select a type
        testerInstance.clickTab("Type");
        testerInstance.selectValue("classname", "org.apache.manifoldcf.crawler.tests.TestingRepositoryConnector");
        testerInstance.clickButton("Continue");

        // Visit the Throttling tab
        testerInstance.clickTab("Throttling");

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
        testerInstance.setValue("description", "MyJob");
        testerInstance.clickTab("Connection");

        // Select the connections
        testerInstance.selectValue("output_connectionname", "MyOutputConnection");
        testerInstance.selectValue("output_precedent", "-1");
        testerInstance.clickButton("Add output", true);
        testerInstance.waitForElementWithName("connectionname");
        testerInstance.selectValue("connectionname", "MyRepositoryConnection");

        testerInstance.clickButton("Continue");

        // Visit all the tabs.  Scheduling tab first
        testerInstance.clickTab("Scheduling");
        testerInstance.selectValue("dayofweek", "0");
        testerInstance.selectValue("hourofday", "1");
        testerInstance.selectValue("minutesofhour", "30");
        testerInstance.selectValue("monthofyear", "11");
        testerInstance.selectValue("dayofmonth", "none");
        testerInstance.setValue("duration", "120");
        testerInstance.clickButton("Add Scheduled Time", true);
        testerInstance.waitForElementWithName("editjob");

        // Save the job
        testerInstance.clickButton("Save");
        testerInstance.verifyThereIsNoError();

        testerInstance.waitForPresenceById("job");
        String jobID = testerInstance.getAttributeValueById("job", "jobid");

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
