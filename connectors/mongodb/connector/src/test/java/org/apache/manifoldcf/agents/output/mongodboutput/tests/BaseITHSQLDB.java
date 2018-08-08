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

import org.junit.After;
import org.junit.Before;

/**
 * Base integration tests class for MongoDB tested against a CMIS repository
 *
 * @author Irindu Nugawela
 */
public class BaseITHSQLDB extends org.apache.manifoldcf.crawler.tests.BaseITHSQLDB {

    protected String[] getConnectorNames() {
        return new String[]{"CMIS"};
    }

    protected String[] getConnectorClasses() {
        return new String[]{"org.apache.manifoldcf.crawler.tests.TestingRepositoryConnector"};
    }

    protected String[] getOutputNames() {
        return new String[]{"MongoDB"};
    }

    protected String[] getOutputClasses() {
        return new String[]{"org.apache.manifoldcf.agents.output.mongodboutput.MongodbOutputConnector"};
    }

    // Setup/teardown

    @Before
    public void setUpMongoDB()
            throws Exception {

        //start mongod here
        //we use maven docker plugin for this so no need to implement
    }

    @After
    public void cleanUpMongoDB()
            throws Exception {
        //stop mongod here
        //we use maven docker plugin for this so no need to implement
    }

}
