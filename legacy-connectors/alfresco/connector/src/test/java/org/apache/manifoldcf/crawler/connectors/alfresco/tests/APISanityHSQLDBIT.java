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

package org.apache.manifoldcf.crawler.connectors.alfresco.tests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;

import org.alfresco.webservice.content.ContentServiceSoapBindingStub;
import org.alfresco.webservice.repository.QueryResult;
import org.alfresco.webservice.repository.RepositoryFault;
import org.alfresco.webservice.repository.RepositoryServiceSoapBindingStub;
import org.alfresco.webservice.repository.UpdateResult;
import org.alfresco.webservice.types.CML;
import org.alfresco.webservice.types.CMLCreate;
import org.alfresco.webservice.types.CMLDelete;
import org.alfresco.webservice.types.ContentFormat;
import org.alfresco.webservice.types.NamedValue;
import org.alfresco.webservice.types.ParentReference;
import org.alfresco.webservice.types.Predicate;
import org.alfresco.webservice.types.Query;
import org.alfresco.webservice.types.Reference;
import org.alfresco.webservice.types.ResultSetRow;
import org.alfresco.webservice.types.Store;
import org.alfresco.webservice.util.AuthenticationUtils;
import org.alfresco.webservice.util.Constants;
import org.alfresco.webservice.util.Utils;
import org.alfresco.webservice.util.WebServiceFactory;
import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.core.interfaces.Configuration;
import org.apache.manifoldcf.core.interfaces.ConfigurationNode;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.crawler.connectors.alfresco.AlfrescoConfig;
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
  private static final String ALFRESCO_TEST_QUERY_CHANGE_DOC = "PATH:\"/app:company_home/cm:testdata/*\" AND TYPE:\"cm:content\" AND @cm\\:name:\""+REPLACER+"\"";
  private static final String ALFRESCO_TEST_QUERY = "PATH:\"/app:company_home/cm:testdata\"";
  
  private static final String ALFRESCO_USERNAME = "admin"; 
  private static final String ALFRESCO_PASSWORD = "admin";
  private static final String ALFRESCO_PROTOCOL = "http";
  private static final String ALFRESCO_SERVER = "localhost";
  private static final String ALFRESCO_PORT = "9090";
  private static final String ALFRESCO_PATH = "/alfresco/api";
  private static final int SOCKET_TIMEOUT = 120000;
  private static final String ALFRESCO_ENDPOINT_TEST_SERVER = 
      ALFRESCO_PROTOCOL+"://"+ALFRESCO_SERVER+":"+ALFRESCO_PORT+ALFRESCO_PATH;
  
  private static final Store STORE = new Store(Constants.WORKSPACE_STORE, "SpacesStore");
  
  public Reference getTestFolder() throws RepositoryFault, RemoteException{
    WebServiceFactory.setTimeoutMilliseconds(SOCKET_TIMEOUT);
    WebServiceFactory.setEndpointAddress(ALFRESCO_ENDPOINT_TEST_SERVER);
    AuthenticationUtils.startSession(ALFRESCO_USERNAME, ALFRESCO_PASSWORD);
    Reference reference = new Reference();
    try{
      RepositoryServiceSoapBindingStub repositoryService = WebServiceFactory.getRepositoryService();
      Query query = new Query(Constants.QUERY_LANG_LUCENE, ALFRESCO_TEST_QUERY);
      QueryResult queryResult = repositoryService.query(STORE, query, false);
      ResultSetRow row = queryResult.getResultSet().getRows(0);
      reference.setStore(STORE);
      reference.setUuid(row.getNode().getId());
      return reference;
    } finally {
      AuthenticationUtils.endSession();
    }
  }
  
  public void createNewDocument(Reference folder, String name) throws IOException{
    ParentReference folderReference = 
        new ParentReference(STORE, folder.getUuid(), null, Constants.ASSOC_CONTAINS, null);

    folderReference.setChildName("{"+Constants.NAMESPACE_CONTENT_MODEL +"}"+ name);
    
    NamedValue[] contentProps = new NamedValue[1]; 
    contentProps[0] = Utils.createNamedValue(Constants.PROP_NAME, name); 
    CMLCreate create = new CMLCreate("1", folderReference, null, null, null, Constants.TYPE_CONTENT, contentProps);
 
    CML cml = new CML();
    cml.setCreate(new CMLCreate[] {create});

    WebServiceFactory.setEndpointAddress(ALFRESCO_ENDPOINT_TEST_SERVER);
    AuthenticationUtils.startSession(ALFRESCO_USERNAME, ALFRESCO_PASSWORD);
    
    try{
      
      UpdateResult[] result = WebServiceFactory.getRepositoryService().update(cml);
      
      //format
      ContentFormat contentFormat = new ContentFormat();
      contentFormat.setEncoding(StandardCharsets.UTF_8.name());
      contentFormat.setMimetype("text/plain");
      
      //the content
      String content = "Alfresco Testdata "+name;
      
      //the new node
      Reference reference = result[0].getDestination();
  
      //write the content in the new node
      ContentServiceSoapBindingStub contentService = WebServiceFactory.getContentService();
      contentService.write(reference, Constants.PROP_CONTENT, content.getBytes(StandardCharsets.UTF_8), contentFormat);
      
    } finally{
      AuthenticationUtils.endSession();
    }
    
  }
  
  /**
   * change the document content with the new one provided as an argument
   * @param session
   * @param name
   * @param newContent
   * @throws RemoteException 
   * @throws RepositoryFault 
   */
  public void changeDocument(String name, String newContent) throws RepositoryFault, RemoteException{
    String luceneQuery = StringUtils.replace(ALFRESCO_TEST_QUERY_CHANGE_DOC, REPLACER, name);
    WebServiceFactory.setTimeoutMilliseconds(SOCKET_TIMEOUT);
    WebServiceFactory.setEndpointAddress(ALFRESCO_ENDPOINT_TEST_SERVER);
    AuthenticationUtils.startSession(ALFRESCO_USERNAME, ALFRESCO_PASSWORD);
    
    try{
    
      RepositoryServiceSoapBindingStub repositoryService = WebServiceFactory.getRepositoryService();         
      Query query = new Query(Constants.QUERY_LANG_LUCENE, luceneQuery);
      QueryResult queryResult = repositoryService.query(STORE, query, false);
      
      ResultSetRow row = queryResult.getResultSet().getRows(0);
      
      Reference reference = new Reference();
      reference.setStore(STORE);
      reference.setUuid(row.getNode().getId());
      
      ContentFormat contentFormat = new ContentFormat();
      contentFormat.setEncoding(StandardCharsets.UTF_8.name());
      contentFormat.setMimetype("text/plain");
      
      ContentServiceSoapBindingStub contentService = WebServiceFactory.getContentService();
      contentService.write(reference, Constants.PROP_CONTENT, newContent.getBytes(StandardCharsets.UTF_8), contentFormat);
      
    } finally {
      AuthenticationUtils.endSession();
    }
    
  }
  
  public void removeDocument(String name) throws RepositoryFault, RemoteException{
    String luceneQuery = StringUtils.replace(ALFRESCO_TEST_QUERY_CHANGE_DOC, REPLACER, name);
    WebServiceFactory.setTimeoutMilliseconds(SOCKET_TIMEOUT);
    WebServiceFactory.setEndpointAddress(ALFRESCO_ENDPOINT_TEST_SERVER);
    AuthenticationUtils.startSession(ALFRESCO_USERNAME, ALFRESCO_PASSWORD);
    
    try{
    
      RepositoryServiceSoapBindingStub repositoryService = WebServiceFactory.getRepositoryService();         
      Query query = new Query(Constants.QUERY_LANG_LUCENE, luceneQuery);
      QueryResult queryResult = repositoryService.query(STORE, query, false);
      
      ResultSetRow row = queryResult.getResultSet().getRows(0);
      
      Reference reference = new Reference();
      reference.setStore(STORE);
      reference.setUuid(row.getNode().getId());
      
      Predicate predicate = new Predicate();
      predicate.setStore(STORE);
      predicate.setNodes(new Reference[]{reference});
      
      CMLDelete cmlDelete = new CMLDelete();
      cmlDelete.setWhere(predicate);
      
      CML cml = new CML();
      cml.setDelete(new CMLDelete[]{cmlDelete});
      
      repositoryService.update(cml);
      
    } finally {
      AuthenticationUtils.endSession();
    }
  }
  
  @Before
  public void createTestArea()
    throws Exception
  {
    removeTestArea();
    
    try
    {
      
      AuthenticationUtils.startSession(ALFRESCO_USERNAME, ALFRESCO_PASSWORD);
      
      UpdateResult[] result = null;
      
      ParentReference companyHomeParent = 
          new ParentReference(STORE, null, "/app:company_home", Constants.ASSOC_CONTAINS, null);

      String name = "testdata";
      companyHomeParent.setChildName("{"+Constants.NAMESPACE_CONTENT_MODEL +"}" + name);
      
      NamedValue[] contentProps = new NamedValue[1]; 
      contentProps[0] = Utils.createNamedValue(Constants.PROP_NAME, name); 
      CMLCreate create = new CMLCreate("1", companyHomeParent, null, null, null, Constants.TYPE_FOLDER, contentProps);
      
      CML cml = new CML();
      cml.setCreate(new CMLCreate[] {create});
      
      try{
        
        result = WebServiceFactory.getRepositoryService().update(cml);
      
      } finally {
        AuthenticationUtils.endSession();
      }
      
      Reference testData = result[0].getDestination();
      
      createNewDocument(testData, "testdata1.txt");
      createNewDocument(testData, "testdata2.txt");
      
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
    WebServiceFactory.setEndpointAddress(ALFRESCO_ENDPOINT_TEST_SERVER);
    AuthenticationUtils.startSession(ALFRESCO_USERNAME, ALFRESCO_PASSWORD);
    
    QueryResult queryResultTestArea = null;
    try {
      
      RepositoryServiceSoapBindingStub repositoryService = WebServiceFactory.getRepositoryService();
      Query query = new Query(Constants.QUERY_LANG_LUCENE, ALFRESCO_TEST_QUERY);
      queryResultTestArea = repositoryService.query(STORE, query, false);

      if(queryResultTestArea.getResultSet().getTotalRowCount()>0){
      
        ResultSetRow row = queryResultTestArea.getResultSet().getRows(0);
        
        Reference reference = new Reference();
        reference.setStore(STORE);
        reference.setUuid(row.getNode().getId());
        
        Predicate predicate = new Predicate();
        predicate.setStore(STORE);
        predicate.setNodes(new Reference[]{reference});
        
        CMLDelete cmlDelete = new CMLDelete();
        cmlDelete.setWhere(predicate);
        
        CML cml = new CML();
        cml.setDelete(new CMLDelete[]{cmlDelete});
        
        repositoryService.update(cml);
      }
        
      } finally {
        AuthenticationUtils.endSession();
      }
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
      child.setValue("Alfresco Connection");
      connectionObject.addChild(connectionObject.getChildCount(),child);
      
      child = new ConfigurationNode("class_name");
      child.setValue("org.apache.manifoldcf.crawler.connectors.alfresco.AlfrescoRepositoryConnector");
      connectionObject.addChild(connectionObject.getChildCount(),child);
      
      child = new ConfigurationNode("description");
      child.setValue("An Alfresco Repository Connection");
      connectionObject.addChild(connectionObject.getChildCount(),child);

      child = new ConfigurationNode("max_connections");
      child.setValue("10");
      connectionObject.addChild(connectionObject.getChildCount(),child);
      
      child = new ConfigurationNode("configuration");
      
      //Alfresco Repository Connector parameters
      
      //username
      ConfigurationNode alfrescoUsernameNode = new ConfigurationNode("_PARAMETER_");
      alfrescoUsernameNode.setAttribute("name", AlfrescoConfig.USERNAME_PARAM);
      alfrescoUsernameNode.setValue(ALFRESCO_USERNAME);
      child.addChild(child.getChildCount(), alfrescoUsernameNode);
      
      //password
      ConfigurationNode alfrescoPasswordNode = new ConfigurationNode("_PARAMETER_");
      alfrescoPasswordNode.setAttribute("name", AlfrescoConfig.PASSWORD_PARAM);
      alfrescoPasswordNode.setValue(ALFRESCO_PASSWORD);
      child.addChild(child.getChildCount(), alfrescoPasswordNode);
      
      //protocol
      ConfigurationNode alfrescoProtocolNode = new ConfigurationNode("_PARAMETER_");
      alfrescoProtocolNode.setAttribute("name", AlfrescoConfig.PROTOCOL_PARAM);
      alfrescoProtocolNode.setValue(ALFRESCO_PROTOCOL);
      child.addChild(child.getChildCount(), alfrescoProtocolNode);
      
      //server
      ConfigurationNode alfrescoServerNode = new ConfigurationNode("_PARAMETER_");
      alfrescoServerNode.setAttribute("name", AlfrescoConfig.SERVER_PARAM);
      alfrescoServerNode.setValue(ALFRESCO_SERVER);
      child.addChild(child.getChildCount(), alfrescoServerNode);
      
      //port
      ConfigurationNode alfrescoPortNode = new ConfigurationNode("_PARAMETER_");
      alfrescoPortNode.setAttribute("name", AlfrescoConfig.PORT_PARAM);
      alfrescoPortNode.setValue(ALFRESCO_PORT);
      child.addChild(child.getChildCount(), alfrescoPortNode);
      
      //path
      ConfigurationNode alfrescoPathNode = new ConfigurationNode("_PARAMETER_");
      alfrescoPathNode.setAttribute("name", AlfrescoConfig.PATH_PARAM);
      alfrescoPathNode.setValue(ALFRESCO_PATH);
      child.addChild(child.getChildCount(), alfrescoPathNode);
      
      //socketTimeout
      ConfigurationNode socketTimeoutNode = new ConfigurationNode("_PARAMETER_");
      socketTimeoutNode.setAttribute("name", AlfrescoConfig.SOCKET_TIMEOUT_PARAM);
      socketTimeoutNode.setValue(String.valueOf(SOCKET_TIMEOUT));
      child.addChild(child.getChildCount(), socketTimeoutNode);
      
      connectionObject.addChild(connectionObject.getChildCount(),child);

      requestObject = new Configuration();
      requestObject.addChild(0,connectionObject);
      
      result = performAPIPutOperationViaNodes("repositoryconnections/Alfresco%20Connection",201,requestObject);
      
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
      child.setValue("Alfresco Connection");
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
      sn.setAttribute("luceneQuery",ALFRESCO_TEST_QUERY);
      
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
      waitJobInactive(jobIDString, 360000L);

      // Check to be sure we actually processed the right number of documents.
      // The test data area has 3 documents and one directory, and we have to count the root directory too.
      long count;
      count = getJobDocumentsProcessed(jobIDString);
      if (count != 3)
        throw new ManifoldCFException("Wrong number of documents processed - expected 3, saw "+new Long(count).toString());
      
      // Add a file and recrawl
      Reference testFolder = getTestFolder();
      createNewDocument(testFolder, "testdata3.txt");
      createNewDocument(testFolder, "testdata4.txt");

      // Now, start the job, and wait until it completes.
      startJob(jobIDString);
      waitJobInactive(jobIDString, 360000L);

      // The test data area has 4 documents and one directory, and we have to count the root directory too.
      count = getJobDocumentsProcessed(jobIDString);
      if (count != 5)
        throw new ManifoldCFException("Wrong number of documents processed after add - expected 5, saw "+new Long(count).toString());

      // Change a document, and recrawl
      changeDocument("testdata1*","MODIFIED - Alfresco Testdata - MODIFIED");
      
      // Now, start the job, and wait until it completes.
      startJob(jobIDString);
      waitJobInactive(jobIDString, 360000L);

      // The test data area has 4 documents and one directory, and we have to count the root directory too.
      count = getJobDocumentsProcessed(jobIDString);
      if (count != 5)
        throw new ManifoldCFException("Wrong number of documents processed after change - expected 5, saw "+new Long(count).toString());
      
      // We also need to make sure the new document was indexed.  Have to think about how to do this though.
      // MHL
      
      // Delete a file, and recrawl
      removeDocument("testdata2*");
      
      // Now, start the job, and wait until it completes.
      startJob(jobIDString);
      waitJobInactive(jobIDString, 360000L);

      // Check to be sure we actually processed the right number of documents.
      // The test data area has 3 documents and one directory, and we have to count the root directory too.
      count = getJobDocumentsProcessed(jobIDString);
      if (count != 4)
        throw new ManifoldCFException("Wrong number of documents processed after delete - expected 4, saw "+new Long(count).toString());

      // Now, delete the job.
      deleteJob(jobIDString);

      waitJobDeleted(jobIDString, 360000L);
      
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
