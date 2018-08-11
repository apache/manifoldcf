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

import com.mongodb.*;
import org.apache.manifoldcf.agents.output.mongodboutput.MongodbOutputConfig;
import org.apache.manifoldcf.core.interfaces.Configuration;
import org.apache.manifoldcf.core.interfaces.ConfigurationNode;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.Collections;

/**
 * @author Irindu Nugawela
 */
public class APISanityHSQLDBIT extends BaseITHSQLDB {

    private MongoClient client = null;
    private DB mongoDatabase = null;
    private DBCollection testCollection = null;

    private MongoClient getMongodbClientSession() throws Exception {

        MongoClient testClient = null;

        try {
            testClient = new MongoClient(BaseITSanityTestUtils.TARGET_HOST_VALUE, Integer.parseInt(BaseITSanityTestUtils.TARGET_PORT_VALUE));
            mongoDatabase = testClient.getDB(BaseITSanityTestUtils.TARGET_DATABASE__VALUE);
        } catch (UnknownHostException ex) {
            throw new ManifoldCFException("Mongodb: Default host is not found. Is mongod process running?" + ex.getMessage(), ex);
        }

        return testClient;
    }

    private long queryTestContents(DBCollection testCollection) {
        // deduct 1 for the document we created at the test area
        return testCollection.count() - 1;
    }

    @Before
    public void createTestArea()
            throws Exception {
        try {

            client = getMongodbClientSession();

            //creating a test database named testDatabase
            mongoDatabase = client.getDB(BaseITSanityTestUtils.TARGET_DATABASE__VALUE);

            //creating a test collection named testCollection
            testCollection = mongoDatabase.getCollection(BaseITSanityTestUtils.TARGET_COLLECTION_VALUE);

            //create credentials
            final BasicDBObject createUserCommand = new BasicDBObject("createUser", BaseITSanityTestUtils.TARGET_USERNAME_VALUE).append("pwd", BaseITSanityTestUtils.TARGET_PASSWORD_VALUE).append("roles",
                    Collections.singletonList(new BasicDBObject("role", "readWrite").append("db", BaseITSanityTestUtils.TARGET_DATABASE__VALUE)));


            CommandResult result = mongoDatabase.command(createUserCommand);

            //create a document to be inserted
            BasicDBObject newDocument = new BasicDBObject();
            newDocument.append("fileName", "fileName")
                    .append("creationDate", "creationDate")
                    .append("lastModificationDate", "lastModificationDate")
                    .append("binaryLength", "binaryLength")
                    .append("documentURI", "documentURI")
                    .append("mimeType", "mimeType")
                    .append("lastModificationDate", "lastModificationDate")
                    .append("content", "content")
                    .append("sourcePath", "sourcePath");

            //insert the test document so that the test collection and test Database are created
            testCollection.insert(newDocument);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }


    @After
    public void removeTestArea()
            throws Exception {

        // deleting the dummyuser
        final BasicDBObject deleteUserCommand = new BasicDBObject("dropUser", BaseITSanityTestUtils.TARGET_USERNAME_VALUE);
        CommandResult result = mongoDatabase.command(deleteUserCommand);

        // dropping the test Collection
        testCollection.drop();

        // dropping the test Database
        mongoDatabase.dropDatabase();
    }

    @Test
    public void sanityCheck()
            throws Exception {
        try {

            int i;

            // Create a basic file system connection, and save it.
            ConfigurationNode connectionObject;
            ConfigurationNode child;
            Configuration requestObject;
            Configuration result;

            connectionObject = new ConfigurationNode("repositoryconnection");

            child = new ConfigurationNode("name");
            child.setValue("Test Connection");
            connectionObject.addChild(connectionObject.getChildCount(), child);

            child = new ConfigurationNode("class_name");
            child.setValue("org.apache.manifoldcf.crawler.tests.TestingRepositoryConnector");
            connectionObject.addChild(connectionObject.getChildCount(), child);

            child = new ConfigurationNode("description");
            child.setValue("Test Connection");
            connectionObject.addChild(connectionObject.getChildCount(), child);

            child = new ConfigurationNode("max_connections");
            child.setValue("10");
            connectionObject.addChild(connectionObject.getChildCount(), child);

            child = new ConfigurationNode("configuration");

            //Testing Repository Connector parameters

            connectionObject.addChild(connectionObject.getChildCount(), child);

            requestObject = new Configuration();
            requestObject.addChild(0, connectionObject);

            result = performAPIPutOperationViaNodes("repositoryconnections/Test%20Connection", 201, requestObject);

            i = 0;
            while (i < result.getChildCount()) {
                ConfigurationNode resultNode = result.findChild(i++);
                if (resultNode.getType().equals("error"))
                    throw new Exception(resultNode.getValue());
            }

            // Create a Mongodb output connection, and save it.
            connectionObject = new ConfigurationNode("outputconnection");

            child = new ConfigurationNode("name");
            child.setValue("MongoDB Output Connection");
            connectionObject.addChild(connectionObject.getChildCount(), child);

            child = new ConfigurationNode("class_name");
            child.setValue("org.apache.manifoldcf.agents.output.mongodboutput.MongodbOutputConnector");
            connectionObject.addChild(connectionObject.getChildCount(), child);

            child = new ConfigurationNode("description");
            child.setValue("MongoDB Output Connection - Target repo of the content migration");
            connectionObject.addChild(connectionObject.getChildCount(), child);

            child = new ConfigurationNode("max_connections");
            child.setValue("100");
            connectionObject.addChild(connectionObject.getChildCount(), child);

            child = new ConfigurationNode("configuration");

            //Mongodb Output Connector parameters

            //host
            ConfigurationNode host = new ConfigurationNode("_PARAMETER_");
            host.setAttribute("name", MongodbOutputConfig.HOST_PARAM);
            host.setValue(BaseITSanityTestUtils.TARGET_HOST_VALUE);
            child.addChild(child.getChildCount(), host);

            //port
            ConfigurationNode port = new ConfigurationNode("_PARAMETER_");
            port.setAttribute("name", MongodbOutputConfig.PORT_PARAM);
            port.setValue(BaseITSanityTestUtils.TARGET_PORT_VALUE);
            child.addChild(child.getChildCount(), port);

            //database
            ConfigurationNode database = new ConfigurationNode("_PARAMETER_");
            database.setAttribute("name", MongodbOutputConfig.DATABASE_PARAM);
            database.setValue(BaseITSanityTestUtils.TARGET_DATABASE__VALUE);
            child.addChild(child.getChildCount(), database);

            //collection
            ConfigurationNode collection = new ConfigurationNode("_PARAMETER_");
            collection.setAttribute("name", MongodbOutputConfig.COLLECTION_PARAM);
            collection.setValue(BaseITSanityTestUtils.TARGET_COLLECTION_VALUE);
            child.addChild(child.getChildCount(), collection);

            //username
            ConfigurationNode username = new ConfigurationNode("_PARAMETER_");
            username.setAttribute("name", MongodbOutputConfig.USERNAME_PARAM);
            username.setValue(BaseITSanityTestUtils.TARGET_USERNAME_VALUE);
            child.addChild(child.getChildCount(), username);

            //password
            ConfigurationNode password = new ConfigurationNode("_PARAMETER_");
            password.setAttribute("name", MongodbOutputConfig.PASSWORD_PARAM);
            password.setValue(BaseITSanityTestUtils.TARGET_PASSWORD_VALUE);
            child.addChild(child.getChildCount(), password);


            connectionObject.addChild(connectionObject.getChildCount(), child);

            requestObject = new Configuration();
            requestObject.addChild(0, connectionObject);

            result = performAPIPutOperationViaNodes("outputconnections/MongoDB%20Output%20Connection", 201, requestObject);

            i = 0;
            while (i < result.getChildCount()) {
                ConfigurationNode resultNode = result.findChild(i++);
                if (resultNode.getType().equals("error"))
                    throw new Exception(resultNode.getValue());
            }

            // Create a job.
            ConfigurationNode jobObject = new ConfigurationNode("job");

            child = new ConfigurationNode("description");
            child.setValue("Test Job");
            jobObject.addChild(jobObject.getChildCount(), child);

            child = new ConfigurationNode("repository_connection");
            child.setValue("Test Connection");
            jobObject.addChild(jobObject.getChildCount(), child);

            // Revamped way of adding output connection
            child = new ConfigurationNode("pipelinestage");
            ConfigurationNode pipelineChild = new ConfigurationNode("stage_id");
            pipelineChild.setValue("0");
            child.addChild(child.getChildCount(), pipelineChild);
            pipelineChild = new ConfigurationNode("stage_isoutput");
            pipelineChild.setValue("true");
            child.addChild(child.getChildCount(), pipelineChild);
            pipelineChild = new ConfigurationNode("stage_connectionname");
            pipelineChild.setValue("MongoDB Output Connection");
            child.addChild(child.getChildCount(), pipelineChild);
            jobObject.addChild(jobObject.getChildCount(), child);

            child = new ConfigurationNode("run_mode");
            child.setValue("scan once");
            jobObject.addChild(jobObject.getChildCount(), child);

            child = new ConfigurationNode("start_mode");
            child.setValue("manual");
            jobObject.addChild(jobObject.getChildCount(), child);

            child = new ConfigurationNode("hopcount_mode");
            child.setValue("accurate");
            jobObject.addChild(jobObject.getChildCount(), child);

            child = new ConfigurationNode("document_specification");

            jobObject.addChild(jobObject.getChildCount(), child);

            requestObject = new Configuration();
            requestObject.addChild(0, jobObject);

            result = performAPIPostOperationViaNodes("jobs", 201, requestObject);

            String jobIDString = null;
            i = 0;
            while (i < result.getChildCount()) {
                ConfigurationNode resultNode = result.findChild(i++);
                if (resultNode.getType().equals("error"))
                    throw new Exception(resultNode.getValue());
                else if (resultNode.getType().equals("job_id"))
                    jobIDString = resultNode.getValue();
            }
            if (jobIDString == null)
                throw new Exception("Missing job_id from return!");

            // Now, start the job, and wait until it completes.
            startJob(jobIDString);
            waitJobInactive(jobIDString, 240000L);

            // Check to be sure we actually processed the right number of documents.
            // The test data area has 3 documents and one directory, and we have to count the root directory too.
            long count;
            count = getJobDocumentsProcessed(jobIDString);

            if (count != 3)
                throw new ManifoldCFException("Wrong number of documents processed - expected 3, saw " + new Long(count).toString());

            //Tests if these three documents are stored in the target repo
            long targetRepoNumberOfContents = queryTestContents(testCollection);
            if (targetRepoNumberOfContents != 3)
                throw new ManifoldCFException("Wrong number of documents stored in the MongoDB Target repo - expected 3, saw " + new Long(targetRepoNumberOfContents).toString());

            // Now, delete the job.
            deleteJob(jobIDString);

            waitJobDeleted(jobIDString, 240000L);

            // Cleanup is automatic by the base class, so we can feel free to leave jobs and connections lying around.
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    protected void startJob(String jobIDString)
            throws Exception {
        Configuration requestObject = new Configuration();

        Configuration result = performAPIPutOperationViaNodes("start/" + jobIDString, 201, requestObject);
        int i = 0;
        while (i < result.getChildCount()) {
            ConfigurationNode resultNode = result.findChild(i++);
            if (resultNode.getType().equals("error"))
                throw new Exception(resultNode.getValue());
        }
    }

    protected void deleteJob(String jobIDString)
            throws Exception {
        Configuration result = performAPIDeleteOperationViaNodes("jobs/" + jobIDString, 200);
        int i = 0;
        while (i < result.getChildCount()) {
            ConfigurationNode resultNode = result.findChild(i++);
            if (resultNode.getType().equals("error"))
                throw new Exception(resultNode.getValue());
        }

    }

    protected String getJobStatus(String jobIDString)
            throws Exception {
        Configuration result = performAPIGetOperationViaNodes("jobstatuses/" + jobIDString, 200);
        String status = null;
        int i = 0;
        while (i < result.getChildCount()) {
            ConfigurationNode resultNode = result.findChild(i++);
            if (resultNode.getType().equals("error"))
                throw new Exception(resultNode.getValue());
            else if (resultNode.getType().equals("jobstatus")) {
                int j = 0;
                while (j < resultNode.getChildCount()) {
                    ConfigurationNode childNode = resultNode.findChild(j++);
                    if (childNode.getType().equals("status"))
                        status = childNode.getValue();
                }
            }
        }
        return status;
    }

    protected long getJobDocumentsProcessed(String jobIDString)
            throws Exception {
        Configuration result = performAPIGetOperationViaNodes("jobstatuses/" + jobIDString, 200);
        String documentsProcessed = null;
        int i = 0;
        while (i < result.getChildCount()) {
            ConfigurationNode resultNode = result.findChild(i++);
            if (resultNode.getType().equals("error"))
                throw new Exception(resultNode.getValue());
            else if (resultNode.getType().equals("jobstatus")) {
                int j = 0;
                while (j < resultNode.getChildCount()) {
                    ConfigurationNode childNode = resultNode.findChild(j++);
                    if (childNode.getType().equals("documents_processed"))
                        documentsProcessed = childNode.getValue();
                }
            }
        }
        if (documentsProcessed == null)
            throw new Exception("Expected a documents_processed field, didn't find it");
        return new Long(documentsProcessed).longValue();
    }

    protected void waitJobInactive(String jobIDString, long maxTime)
            throws Exception {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() < startTime + maxTime) {
            String status = getJobStatus(jobIDString);
            if (status == null)
                throw new Exception("No such job: '" + jobIDString + "'");
            if (status.equals("not yet run"))
                throw new Exception("Job was never started.");
            if (status.equals("done"))
                return;
            if (status.equals("error"))
                throw new Exception("Job reports error.");
            ManifoldCF.sleep(1000L);
            continue;
        }
        throw new ManifoldCFException("ManifoldCF did not terminate in the allotted time of " + new Long(maxTime).toString() + " milliseconds");
    }


    protected void waitJobDeleted(String jobIDString, long maxTime)
            throws Exception {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() < startTime + maxTime) {
            String status = getJobStatus(jobIDString);
            if (status == null)
                return;
            ManifoldCF.sleep(1000L);
        }
        throw new ManifoldCFException("ManifoldCF did not delete in the allotted time of " + new Long(maxTime).toString() + " milliseconds");
    }


}
