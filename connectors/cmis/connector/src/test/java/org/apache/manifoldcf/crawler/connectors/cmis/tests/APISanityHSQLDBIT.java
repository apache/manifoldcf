/* $Id: APISanityIT.java 1234472 2012-01-22 09:59:32Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.cmis.tests;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.QueryResult;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.api.SessionFactory;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ContentStreamImpl;
import org.apache.chemistry.opencmis.commons.impl.jaxb.EnumBaseObjectTypeIds;
import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.core.interfaces.Configuration;
import org.apache.manifoldcf.core.interfaces.ConfigurationNode;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.crawler.connectors.cmis.CmisConfig;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Piergiorgio Lucidi
 */
public class APISanityHSQLDBIT extends BaseITHSQLDB
{
  private static final String REPLACER = "?";
  private static final String CMIS_TEST_QUERY_CHANGE_DOC = "SELECT * FROM cmis:document WHERE cmis:name='"+REPLACER+"'";
  private static final String CMIS_TEST_QUERY = "SELECT * FROM cmis:folder WHERE cmis:name='testdata'";
    
  private Session cmisClientSession = null;
  
  private Session getCmisClientSession(){
    // default factory implementation
    SessionFactory factory = SessionFactoryImpl.newInstance();
    Map<String, String> parameters = new HashMap<String, String>();

    // user credentials
    parameters.put(SessionParameter.USER, CmisConfig.USERNAME_DEFAULT_VALUE);
    parameters.put(SessionParameter.PASSWORD, CmisConfig.PASSWORD_DEFAULT_VALUE);

    // connection settings
    String endpoint =
        CmisConfig.PROTOCOL_DEFAULT_VALUE + "://" + 
        CmisConfig.SERVER_DEFAULT_VALUE + ":" +
        CmisConfig.PORT_DEFAULT_VALUE + 
        CmisConfig.PATH_DEFAULT_VALUE;
    
    parameters.put(SessionParameter.ATOMPUB_URL, endpoint);
    parameters.put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());

    // create session
    return factory.getRepositories(parameters).get(0).createSession();
  }
  
  public Folder getTestFolder(Session session){
    Folder testFolder = null;
    ItemIterable<QueryResult> results = session.query(CMIS_TEST_QUERY, false);
    for (QueryResult result : results) {
      String folderId = result.getPropertyById(PropertyIds.OBJECT_ID).getFirstValue().toString();
      testFolder = (Folder)session.getObject(folderId);
    }
    return testFolder;
  }
  
  public void createNewDocument(Folder folder, String name) throws IOException{
    // properties 
    // (minimal set: name and object type id)
    Map<String, Object> contentProperties = new HashMap<String, Object>();
    contentProperties.put(PropertyIds.OBJECT_TYPE_ID, EnumBaseObjectTypeIds.CMIS_DOCUMENT.value());
    contentProperties.put(PropertyIds.NAME, name);
  
    // content
    String contentString = "CMIS Testdata "+name;
    byte[] content = contentString.getBytes(StandardCharsets.UTF_8);
    InputStream stream = new ByteArrayInputStream(content);
    ContentStream contentStream = new ContentStreamImpl(name, new BigInteger(content), "text/plain", stream);
  
    // create a major version
    folder.createDocument(contentProperties, contentStream, null);
    stream.close();
  }
  
  /**
   * change the document content with the new one provided as an argument
   * @param session
   * @param name
   * @param newContent
   */
  public void changeDocument(Session session, String name, String newContent){
    String cmisQuery = StringUtils.replace(CMIS_TEST_QUERY_CHANGE_DOC, REPLACER, name);
    ItemIterable<QueryResult> results = session.query(cmisQuery, false);
    String objectId = StringUtils.EMPTY;
    for (QueryResult result : results) {
      objectId = result.getPropertyById(PropertyIds.OBJECT_ID).getFirstValue().toString();
    }

    byte[] newContentByteArray = newContent.getBytes(StandardCharsets.UTF_8);
    InputStream stream = new ByteArrayInputStream(newContentByteArray);
    ContentStream contentStream = new ContentStreamImpl(name, new BigInteger(newContentByteArray), "text/plain", stream);
    Document documentToUpdate = (Document) session.getObject(objectId);
    documentToUpdate.setContentStream(contentStream, true);
  }
  
  public void removeDocument(Session session, String name){
    String cmisQuery = StringUtils.replace(CMIS_TEST_QUERY_CHANGE_DOC, REPLACER, name);
    ItemIterable<QueryResult> results = session.query(cmisQuery, false);
    String objectIdValue = StringUtils.EMPTY;
    for (QueryResult result : results) {
    	objectIdValue = result.getPropertyById(PropertyIds.OBJECT_ID).getFirstValue().toString();
    }
    session.getObject(objectIdValue).delete();
  }
  
  @Before
  public void createTestArea()
    throws Exception
  {
    try
    {
      cmisClientSession = getCmisClientSession();

      //creating a new folder
      Folder root = cmisClientSession.getRootFolder();
      
      ItemIterable<QueryResult> results = cmisClientSession.query(CMIS_TEST_QUERY, false);
      for (QueryResult result : results) {
         String repositoryId = cmisClientSession.getRepositoryInfo().getId();
        String folderId = result.getPropertyById(PropertyIds.OBJECT_ID).getFirstValue().toString();
        cmisClientSession.getBinding().getObjectService().deleteTree(repositoryId, folderId, true, null, false, null);
      }

      Map<String, Object> folderProperties = new HashMap<String, Object>();
      folderProperties.put(PropertyIds.OBJECT_TYPE_ID, EnumBaseObjectTypeIds.CMIS_FOLDER.value());
      folderProperties.put(PropertyIds.NAME, "testdata");
  
      Folder newFolder = root.createFolder(folderProperties);

      String name = "testdata1.txt";
      createNewDocument(newFolder, name);
      
      name = "testdata2.txt";
      createNewDocument(newFolder,name);
      
    }
    catch (Exception e)
    {
      e.printStackTrace();
      throw e;
    }
  }
  
  @After
  public void removeTestArea()
    throws Exception
  {
    // we don't need to remove anything
  }
  
  @Test
  public void sanityCheck()
    throws Exception
  {
    try
    {
      
      int i;

      // Create a basic file system connection, and save it.
      ConfigurationNode connectionObject;
      ConfigurationNode child;
      Configuration requestObject;
      Configuration result;
      
      connectionObject = new ConfigurationNode("repositoryconnection");
      
      child = new ConfigurationNode("name");
      child.setValue("CMIS Connection");
      connectionObject.addChild(connectionObject.getChildCount(),child);
      
      child = new ConfigurationNode("class_name");
      child.setValue("org.apache.manifoldcf.crawler.connectors.cmis.CmisRepositoryConnector");
      connectionObject.addChild(connectionObject.getChildCount(),child);
      
      child = new ConfigurationNode("description");
      child.setValue("CMIS Connection");
      connectionObject.addChild(connectionObject.getChildCount(),child);

      child = new ConfigurationNode("max_connections");
      child.setValue("10");
      connectionObject.addChild(connectionObject.getChildCount(),child);
      
      child = new ConfigurationNode("configuration");
      
      //CMIS Repository Connector parameters
      
      //binding
      ConfigurationNode cmisBindingNode = new ConfigurationNode("_PARAMETER_");
      cmisBindingNode.setAttribute("name", CmisConfig.BINDING_PARAM);
      cmisBindingNode.setValue(CmisConfig.BINDING_DEFAULT_VALUE);
      child.addChild(child.getChildCount(), cmisBindingNode);
      
      //username
      ConfigurationNode cmisUsernameNode = new ConfigurationNode("_PARAMETER_");
      cmisUsernameNode.setAttribute("name", CmisConfig.USERNAME_PARAM);
      cmisUsernameNode.setValue(CmisConfig.USERNAME_DEFAULT_VALUE);
      child.addChild(child.getChildCount(), cmisUsernameNode);
      
      //password
      ConfigurationNode cmisPasswordNode = new ConfigurationNode("_PARAMETER_");
      cmisPasswordNode.setAttribute("name", CmisConfig.PASSWORD_PARAM);
      cmisPasswordNode.setValue(CmisConfig.PASSWORD_DEFAULT_VALUE);
      child.addChild(child.getChildCount(), cmisPasswordNode);
      
      //protocol
      ConfigurationNode cmisProtocolNode = new ConfigurationNode("_PARAMETER_");
      cmisProtocolNode.setAttribute("name", CmisConfig.PROTOCOL_PARAM);
      cmisProtocolNode.setValue(CmisConfig.PROTOCOL_DEFAULT_VALUE);
      child.addChild(child.getChildCount(), cmisProtocolNode);
      
      //server
      ConfigurationNode cmisServerNode = new ConfigurationNode("_PARAMETER_");
      cmisServerNode.setAttribute("name", CmisConfig.SERVER_PARAM);
      cmisServerNode.setValue(CmisConfig.SERVER_DEFAULT_VALUE);
      child.addChild(child.getChildCount(), cmisServerNode);
      
      //port
      ConfigurationNode cmisPortNode = new ConfigurationNode("_PARAMETER_");
      cmisPortNode.setAttribute("name", CmisConfig.PORT_PARAM);
      cmisPortNode.setValue(CmisConfig.PORT_DEFAULT_VALUE);
      child.addChild(child.getChildCount(), cmisPortNode);
      
      //path
      ConfigurationNode cmisPathNode = new ConfigurationNode("_PARAMETER_");
      cmisPathNode.setAttribute("name", CmisConfig.PATH_PARAM);
      cmisPathNode.setValue(CmisConfig.PATH_DEFAULT_VALUE);
      child.addChild(child.getChildCount(), cmisPathNode);
      
      connectionObject.addChild(connectionObject.getChildCount(),child);

      requestObject = new Configuration();
      requestObject.addChild(0,connectionObject);
      
      result = performAPIPutOperationViaNodes("repositoryconnections/CMIS%20Connection",201,requestObject);
      
      i = 0;
      while (i < result.getChildCount())
      {
        ConfigurationNode resultNode = result.findChild(i++);
        if (resultNode.getType().equals("error"))
          throw new Exception(resultNode.getValue());
      }
      
      // Create a basic null output connection, and save it.
      connectionObject = new ConfigurationNode("outputconnection");
      
      child = new ConfigurationNode("name");
      child.setValue("Null Connection");
      connectionObject.addChild(connectionObject.getChildCount(),child);
      
      child = new ConfigurationNode("class_name");
      child.setValue("org.apache.manifoldcf.agents.tests.TestingOutputConnector");
      connectionObject.addChild(connectionObject.getChildCount(),child);
      
      child = new ConfigurationNode("description");
      child.setValue("Null Connection");
      connectionObject.addChild(connectionObject.getChildCount(),child);

      child = new ConfigurationNode("max_connections");
      child.setValue("100");
      connectionObject.addChild(connectionObject.getChildCount(),child);

      requestObject = new Configuration();
      requestObject.addChild(0,connectionObject);
      
      result = performAPIPutOperationViaNodes("outputconnections/Null%20Connection",201,requestObject);
      
      i = 0;
      while (i < result.getChildCount())
      {
        ConfigurationNode resultNode = result.findChild(i++);
        if (resultNode.getType().equals("error"))
          throw new Exception(resultNode.getValue());
      }

      // Create a job.
      ConfigurationNode jobObject = new ConfigurationNode("job");
      
      child = new ConfigurationNode("description");
      child.setValue("Test Job");
      jobObject.addChild(jobObject.getChildCount(),child);

      child = new ConfigurationNode("repository_connection");
      child.setValue("CMIS Connection");
      jobObject.addChild(jobObject.getChildCount(),child);

      // Revamped way of adding output connection
      child = new ConfigurationNode("pipelinestage");
      ConfigurationNode pipelineChild = new ConfigurationNode("stage_id");
      pipelineChild.setValue("0");
      child.addChild(child.getChildCount(),pipelineChild);
      pipelineChild = new ConfigurationNode("stage_isoutput");
      pipelineChild.setValue("true");
      child.addChild(child.getChildCount(),pipelineChild);
      pipelineChild = new ConfigurationNode("stage_connectionname");
      pipelineChild.setValue("Null Connection");
      child.addChild(child.getChildCount(),pipelineChild);
      jobObject.addChild(jobObject.getChildCount(),child);

      child = new ConfigurationNode("run_mode");
      child.setValue("scan once");
      jobObject.addChild(jobObject.getChildCount(),child);

      child = new ConfigurationNode("start_mode");
      child.setValue("manual");
      jobObject.addChild(jobObject.getChildCount(),child);

      child = new ConfigurationNode("hopcount_mode");
      child.setValue("accurate");
      jobObject.addChild(jobObject.getChildCount(),child);

      child = new ConfigurationNode("document_specification");
      
      
      //Job configuration
      ConfigurationNode sn = new ConfigurationNode("startpoint");
      sn.setAttribute("cmisQuery",CMIS_TEST_QUERY);
      
      child.addChild(child.getChildCount(),sn);
      jobObject.addChild(jobObject.getChildCount(),child);
      
      requestObject = new Configuration();
      requestObject.addChild(0,jobObject);
      
      result = performAPIPostOperationViaNodes("jobs",201,requestObject);
      
      String jobIDString = null;
      i = 0;
      while (i < result.getChildCount())
      {
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
      waitJobInactive(jobIDString, 920000L);

      // Check to be sure we actually processed the right number of documents.
      // The test data area has 3 documents and one directory, and we have to count the root directory too.
      long count;
      count = getJobDocumentsProcessed(jobIDString);
      if (count != 3)
        throw new ManifoldCFException("Wrong number of documents processed - expected 3, saw "+new Long(count).toString());
      
      // Add a file and recrawl
      Folder testFolder = getTestFolder(cmisClientSession);
      createNewDocument(testFolder, "testdata3.txt");
      createNewDocument(testFolder, "testdata4.txt");

      // Now, start the job, and wait until it completes.
      startJob(jobIDString);
      waitJobInactive(jobIDString, 920000L);

      // The test data area has 4 documents and one directory, and we have to count the root directory too.
      count = getJobDocumentsProcessed(jobIDString);
      if (count != 5)
        throw new ManifoldCFException("Wrong number of documents processed after add - expected 5, saw "+new Long(count).toString());

      // Change a document, and recrawl
      changeDocument(cmisClientSession,"testdata1.txt","MODIFIED - CMIS Testdata - MODIFIED");
      
      // Now, start the job, and wait until it completes.
      startJob(jobIDString);
      waitJobInactive(jobIDString, 920000L);

      // The test data area has 4 documents and one directory, and we have to count the root directory too.
      count = getJobDocumentsProcessed(jobIDString);
      if (count != 5)
        throw new ManifoldCFException("Wrong number of documents processed after change - expected 5, saw "+new Long(count).toString());
      
      // We also need to make sure the new document was indexed.  Have to think about how to do this though.
      // MHL
      //System.out.println("Starting delete...");
      // Delete a file, and recrawl
      removeDocument(cmisClientSession, "testdata3.txt");
      
      // Now, start the job, and wait until it completes.
      startJob(jobIDString);
      waitJobInactive(jobIDString, 920000L);

      // Check to be sure we actually processed the right number of documents.
      // The test data area has 3 documents and one directory, and we have to count the root directory too.
      count = getJobDocumentsProcessed(jobIDString);
      if (count != 4)
        throw new ManifoldCFException("Wrong number of documents processed after delete - expected 4, saw "+new Long(count).toString());

      // Now, delete the job.
      deleteJob(jobIDString);

      waitJobDeleted(jobIDString, 920000L);
      
      // Cleanup is automatic by the base class, so we can feel free to leave jobs and connections lying around.
    }
    catch (Exception e)
    {
      e.printStackTrace();
      throw e;
    }
  }
  
  protected void startJob(String jobIDString)
    throws Exception
  {
    Configuration requestObject = new Configuration();
    
    Configuration result = performAPIPutOperationViaNodes("start/"+jobIDString,201,requestObject);
    int i = 0;
    while (i < result.getChildCount())
    {
      ConfigurationNode resultNode = result.findChild(i++);
      if (resultNode.getType().equals("error"))
        throw new Exception(resultNode.getValue());
    }
  }
  
  protected void deleteJob(String jobIDString)
    throws Exception
  {
    Configuration result = performAPIDeleteOperationViaNodes("jobs/"+jobIDString,200);
    int i = 0;
    while (i < result.getChildCount())
    {
      ConfigurationNode resultNode = result.findChild(i++);
      if (resultNode.getType().equals("error"))
        throw new Exception(resultNode.getValue());
    }

  }
  
  protected String getJobStatus(String jobIDString)
    throws Exception
  {
    Configuration result = performAPIGetOperationViaNodes("jobstatuses/"+jobIDString,200);
    String status = null;
    int i = 0;
    while (i < result.getChildCount())
    {
      ConfigurationNode resultNode = result.findChild(i++);
      if (resultNode.getType().equals("error"))
        throw new Exception(resultNode.getValue());
      else if (resultNode.getType().equals("jobstatus"))
      {
        int j = 0;
        while (j < resultNode.getChildCount())
        {
          ConfigurationNode childNode = resultNode.findChild(j++);
          if (childNode.getType().equals("status"))
            status = childNode.getValue();
        }
      }
    }
    return status;
  }

  protected long getJobDocumentsProcessed(String jobIDString)
    throws Exception
  {
    Configuration result = performAPIGetOperationViaNodes("jobstatuses/"+jobIDString,200);
    String documentsProcessed = null;
    int i = 0;
    while (i < result.getChildCount())
    {
      ConfigurationNode resultNode = result.findChild(i++);
      if (resultNode.getType().equals("error"))
        throw new Exception(resultNode.getValue());
      else if (resultNode.getType().equals("jobstatus"))
      {
        int j = 0;
        while (j < resultNode.getChildCount())
        {
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
    throws Exception
  {
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() < startTime + maxTime)
    {
      String status = getJobStatus(jobIDString);
      if (status == null)
        throw new Exception("No such job: '"+jobIDString+"'");
      if (status.equals("not yet run"))
        throw new Exception("Job was never started.");
      if (status.equals("done"))
        return;
      if (status.equals("error"))
        throw new Exception("Job reports error.");
      ManifoldCF.sleep(1000L);
      continue;
    }
    throw new ManifoldCFException("ManifoldCF did not terminate in the allotted time of "+new Long(maxTime).toString()+" milliseconds");
  }
  
  protected void waitJobDeleted(String jobIDString, long maxTime)
    throws Exception
  {
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() < startTime + maxTime)
    {
      String status = getJobStatus(jobIDString);
      if (status == null)
        return;
      ManifoldCF.sleep(1000L);
    }
    throw new ManifoldCFException("ManifoldCF did not delete in the allotted time of "+new Long(maxTime).toString()+" milliseconds");
  }
    

}
