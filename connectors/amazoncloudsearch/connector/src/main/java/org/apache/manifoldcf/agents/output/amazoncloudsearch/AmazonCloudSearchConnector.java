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
package org.apache.manifoldcf.agents.output.amazoncloudsearch;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.StringReader;
import java.io.BufferedReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.Date;

import org.apache.commons.io.input.ReaderInputStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputNotifyActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputRemoveActivity;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.output.BaseOutputConnector;
import org.apache.manifoldcf.agents.output.amazoncloudsearch.SDFModel.Document;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.ConfigurationNode;
import org.apache.manifoldcf.core.interfaces.DBInterfaceFactory;
import org.apache.manifoldcf.core.interfaces.IDBInterface;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IPasswordMapperActivity;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.core.system.ManifoldCF;
import org.apache.manifoldcf.crawler.system.Logging;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.manifoldcf.core.jsongen.*;

public class AmazonCloudSearchConnector extends BaseOutputConnector {

  /** Ingestion activity */
  public final static String INGEST_ACTIVITY = "document ingest";
  /** Document removal activity */
  public final static String REMOVE_ACTIVITY = "document deletion";

  /** Forward to the javascript to check the configuration parameters */
  private static final String EDIT_CONFIGURATION_JS = "editConfiguration.js";

  /** Forward to the HTML template to edit the configuration parameters */
  private static final String EDIT_CONFIGURATION_HTML = "editConfiguration.html";
  
  /** Forward to the HTML template to view the configuration parameters */
  private static final String VIEW_CONFIGURATION_HTML = "viewConfiguration.html";

  /** Forward to the javascript to check the specification parameters for the job */
  private static final String EDIT_SPECIFICATION_JS = "editSpecification.js";
  
  private static final String EDIT_SPECIFICATION_CONTENTS_HTML = "editSpecification_Contents.html";
  
  private static final String VIEW_SPECIFICATION_HTML = "viewSpecification.html";
  
  /** Local connection */
  protected HttpPost poster = null;
  
  // What we need for database keys
  protected String serverHost = null;
  protected String serverPath = null;
  
  /** Document Chunk Manager */
  private DocumentChunkManager documentChunkManager = null;
  
  /** cloudsearch field name for file body text. */
  private static final String FILE_BODY_TEXT_FIELDNAME = "f_bodytext";
  
  /** Constructor.
   */
  public AmazonCloudSearchConnector(){
  }
  
  /** Clear out any state information specific to a given thread.
  * This method is called when this object is returned to the connection pool.
  */
  @Override
  public void clearThreadContext()
  {
    super.clearThreadContext();
    documentChunkManager = null;
  }

  @Override
  public void install(IThreadContext threadContext) 
      throws ManifoldCFException
  {
    IDBInterface mainDatabase = DBInterfaceFactory.make(threadContext,
      ManifoldCF.getMasterDatabaseName(),
      ManifoldCF.getMasterDatabaseUsername(),
      ManifoldCF.getMasterDatabasePassword());
    
    DocumentChunkManager dcmanager = new DocumentChunkManager(mainDatabase);
    dcmanager.install();
  }

  @Override
  public void deinstall(IThreadContext threadContext)
      throws ManifoldCFException
  {
    IDBInterface mainDatabase = DBInterfaceFactory.make(threadContext,
        ManifoldCF.getMasterDatabaseName(),
        ManifoldCF.getMasterDatabaseUsername(),
        ManifoldCF.getMasterDatabasePassword());
      
    DocumentChunkManager dcmanager = new DocumentChunkManager(mainDatabase);
    dcmanager.deinstall();
  }

  /** Return the list of activities that this connector supports (i.e. writes into the log).
  *@return the list.
  */
  @Override
  public String[] getActivitiesList()
  {
    return new String[]{INGEST_ACTIVITY,REMOVE_ACTIVITY};
  }

  /** Connect.
  *@param configParameters is the set of configuration parameters, which
  * in this case describe the target appliance, basic auth configuration, etc.  (This formerly came
  * out of the ini file.)
  */
  @Override
  public void connect(ConfigParams configParameters)
  {
    super.connect(configParameters);
  }

  /** This method is called to assess whether to count this connector instance should
  * actually be counted as being connected.
  *@return true if the connector instance is actually connected.
  */
  @Override
  public boolean isConnected()
  {
    return poster != null;
  }
  
  /** Close the connection.  Call this before discarding the connection.
  */
  @Override
  public void disconnect()
    throws ManifoldCFException
  {
    serverHost = null;
    serverPath = null;
    poster = null;
    super.disconnect();
  }

  /** Set up a session */
  protected void getSession()
    throws ManifoldCFException
  {
    if (documentChunkManager == null)
    {
      IDBInterface databaseHandle = DBInterfaceFactory.make(currentContext,
        ManifoldCF.getMasterDatabaseName(),
        ManifoldCF.getMasterDatabaseUsername(),
        ManifoldCF.getMasterDatabasePassword());
      documentChunkManager = new DocumentChunkManager(databaseHandle);
    }

    serverHost = params.getParameter(AmazonCloudSearchConfig.SERVER_HOST);
    if (serverHost == null)
      throw new ManifoldCFException("Server host parameter required");
    serverPath = params.getParameter(AmazonCloudSearchConfig.SERVER_PATH);
    if (serverPath == null)
      throw new ManifoldCFException("Server path parameter required");
    String proxyProtocol = params.getParameter(AmazonCloudSearchConfig.PROXY_PROTOCOL);
    String proxyHost = params.getParameter(AmazonCloudSearchConfig.PROXY_HOST);
    String proxyPort = params.getParameter(AmazonCloudSearchConfig.PROXY_PORT);
    
    // Https is OK here without a custom trust store because we know we are talking to an Amazon instance, which has certs that
    // are presumably non-custom.
    String urlStr = "https://" + serverHost + serverPath;
    poster = new HttpPost(urlStr);
    
    //set proxy
    if(proxyHost != null && proxyHost.length() > 0)
    {
      try
      {
        HttpHost proxy = new HttpHost(proxyHost, Integer.parseInt(proxyPort), proxyProtocol);
        RequestConfig config = RequestConfig.custom().setProxy(proxy).build();
        poster.setConfig(config);
      }
      catch (NumberFormatException e)
      {
        throw new ManifoldCFException("Number format exception: "+e.getMessage(),e);
      }
    }
    
    poster.addHeader("Content-Type", "application/json");
  }
  
  /** Test the connection.  Returns a string describing the connection integrity.
  *@return the connection's status as a displayable string.
  */
  @Override
  public String check() throws ManifoldCFException {
    try {
      getSession();
      String responsbody = postData(new ReaderInputStream(new StringReader("[]"),Consts.UTF_8));
      String status = "";
      try
      {
        status = getStatusFromJsonResponse(responsbody);
      } catch (ManifoldCFException e)
      {
        Logging.connectors.debug(e);
        return "Could not get status from response body. Check Access Policy setting of your domain of Amazon CloudSearch.: " + e.getMessage();
      }
          
      // check status message
      String message = "";
      if ("error".equals(status)) {
        JsonParser parser = new JsonFactory().createJsonParser(responsbody);
        while (parser.nextToken() != JsonToken.END_OBJECT) {
          String name = parser.getCurrentName();
          if ("errors".equalsIgnoreCase(name)) {
            message = parseMessage(parser);
            break;
          }
        }
      }
      if ("error".equalsIgnoreCase(status)
          && "batch must contain at least one operation".equals(message)) {
        return "Connection working.";
      }
      return "Connection NOT working.";
      
    } catch (ClientProtocolException e) {
      Logging.connectors.debug(e);
      return "Protocol exception: "+e.getMessage();
    } catch (IOException e) {
      Logging.connectors.debug(e);
      return "IO exception: "+e.getMessage();
    } catch (ServiceInterruption e) {
      Logging.connectors.debug(e);
      return "Transient exception: "+e.getMessage();
    }
  }
  
  private String getStatusFromJsonResponse(String responsbody) throws ManifoldCFException {
    try {
      JsonParser parser = new JsonFactory().createJsonParser(responsbody);
      while (parser.nextToken() != JsonToken.END_OBJECT)
      {
        String name = parser.getCurrentName();
        if("status".equalsIgnoreCase(name)){
          parser.nextToken();
          return parser.getText();
        }
      }
    } catch (JsonParseException e) {
      throw new ManifoldCFException(e);
    } catch (IOException e) {
      throw new ManifoldCFException(e);
    }
    return null;
  }
  
  private String parseMessage(JsonParser parser) throws JsonParseException, IOException {
    while(parser.nextToken() != JsonToken.END_ARRAY){
      String name = parser.getCurrentName();
      if("message".equalsIgnoreCase(name)){
        parser.nextToken();
        return parser.getText();
      }
    }
    return null;
  }

  /** Get an output version string, given an output specification.  The output version string is used to uniquely describe the pertinent details of
  * the output specification and the configuration, to allow the Connector Framework to determine whether a document will need to be output again.
  * Note that the contents of the document cannot be considered by this method, and that a different version string (defined in IRepositoryConnector)
  * is used to describe the version of the actual document.
  *
  * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be
  * necessary.
  *@param os is the current output specification for the job that is doing the crawling.
  *@return a string, of unlimited length, which uniquely describes output configuration and specification in such a way that if two such strings are equal,
  * the document will not need to be sent again to the output data store.
  */
  @Override
  public String getPipelineDescription(Specification os)
    throws ManifoldCFException, ServiceInterruption
  {
    SpecPacker sp = new SpecPacker(os);
    return sp.toPackedString();
  }

  /** Detect if a mime type is indexable or not.  This method is used by participating repository connectors to pre-filter the number of
  * unusable documents that will be passed to this output connector.
  *@param outputDescription is the document's output version.
  *@param mimeType is the mime type of the document.
  *@return true if the mime type is indexable by this connector.
  */
  public boolean checkMimeTypeIndexable(String outputDescription, String mimeType)
    throws ManifoldCFException, ServiceInterruption
  {
    SpecPacker sp = new SpecPacker(outputDescription);
    if (sp.checkMimeType(mimeType))
      return super.checkMimeTypeIndexable(outputDescription, mimeType);
    else
      return false;
  }

  @Override
  public boolean checkLengthIndexable(String outputDescription, long length)
    throws ManifoldCFException, ServiceInterruption {
    SpecPacker sp = new SpecPacker(outputDescription);
    if (sp.checkLengthIndexable(length))
      return super.checkLengthIndexable(outputDescription, length);
    else
      return false;
  }

  @Override
  public boolean checkURLIndexable(String outputDescription, String url)
    throws ManifoldCFException, ServiceInterruption {
    SpecPacker sp = new SpecPacker(outputDescription);
    if (sp.checkURLIndexable(url))
      return super.checkURLIndexable(outputDescription, url);
    else
      return false;
  }
  
  /** Add (or replace) a document in the output data store using the connector.
  * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be
  * necessary.
  * The OutputSpecification is *not* provided to this method, because the goal is consistency, and if output is done it must be consistent with the
  * output description, since that was what was partly used to determine if output should be taking place.  So it may be necessary for this method to decode
  * an output description string in order to determine what should be done.
  *@param documentURI is the URI of the document.  The URI is presumed to be the unique identifier which the output data store will use to process
  * and serve the document.  This URI is constructed by the repository connector which fetches the document, and is thus universal across all output connectors.
  *@param outputDescription is the description string that was constructed for this document by the getOutputDescription() method.
  *@param document is the document data to be processed (handed to the output data store).
  *@param authorityNameString is the name of the authority responsible for authorizing any access tokens passed in with the repository document.  May be null.
  *@param activities is the handle to an object that the implementer of an output connector may use to perform operations, such as logging processing activity.
  *@return the document status (accepted or permanently rejected).
  */
  @Override
  public int addOrReplaceDocumentWithException(String documentURI, String outputDescription, RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
    throws ManifoldCFException, ServiceInterruption, IOException
  {
    // Establish a session
    getSession();
    
    SpecPacker sp = new SpecPacker(outputDescription);
    
    String uid = ManifoldCF.hash(documentURI);

    // Build a JSON generator
    JSONObjectReader objectReader = new JSONObjectReader();
    // Build the metadata field part
    JSONObjectReader fieldReader = new JSONObjectReader();
    // Add the type and ID
    objectReader.addNameValuePair(new JSONNameValueReader(new JSONStringReader("id"),new JSONStringReader(uid)))
      .addNameValuePair(new JSONNameValueReader(new JSONStringReader("type"),new JSONStringReader("add")))
      .addNameValuePair(new JSONNameValueReader(new JSONStringReader("fields"),fieldReader));
    
    // Populate the fields...
    Iterator<String> itr = document.getFields();
    while (itr.hasNext())
    {
      String fieldName = itr.next();
      Object[] fieldValues = document.getField(fieldName);
      JSONReader[] elements = new JSONReader[fieldValues.length];
      if (fieldValues instanceof Reader[])
      {
        for (int i = 0; i < elements.length; i++)
        {
          elements[i] = new JSONStringReader((Reader)fieldValues[i]);
        }
      }
      else if (fieldValues instanceof Date[])
      {
        for (int i = 0; i < elements.length; i++)
        {
          elements[i] = new JSONStringReader(((Date)fieldValues[i]).toString());
        }
      }
      else if (fieldValues instanceof String[])
      {
        for (int i = 0; i < elements.length; i++)
        {
          elements[i] = new JSONStringReader((String)fieldValues[i]);
        }
      }
      else
        throw new IllegalStateException("Unexpected metadata type: "+fieldValues.getClass().getName());
      
      fieldReader.addNameValuePair(new JSONNameValueReader(new JSONStringReader(fieldName),new JSONArrayReader(elements)));
    }
    
    // Add the primary content data in.
    fieldReader.addNameValuePair(new JSONNameValueReader(new JSONStringReader(FILE_BODY_TEXT_FIELDNAME),
      new JSONStringReader(new InputStreamReader(document.getBinaryStream(),Consts.UTF_8))));
    
    documentChunkManager.recordDocument(uid, serverHost, serverPath, new ReaderInputStream(objectReader, Consts.UTF_8));
    
    return DOCUMENTSTATUS_ACCEPTED;
  }
  
  /** Remove a document using the connector.
  * Note that the last outputDescription is included, since it may be necessary for the connector to use such information to know how to properly remove the document.
  *@param documentURI is the URI of the document.  The URI is presumed to be the unique identifier which the output data store will use to process
  * and serve the document.  This URI is constructed by the repository connector which fetches the document, and is thus universal across all output connectors.
  *@param outputDescription is the last description string that was constructed for this document by the getOutputDescription() method above.
  *@param activities is the handle to an object that the implementer of an output connector may use to perform operations, such as logging processing activity.
  */
  @Override
  public void removeDocument(String documentURI, String outputDescription, IOutputRemoveActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    // Establish a session
    getSession();
    
    String uid = ManifoldCF.hash(documentURI);

    // Build a JSON generator
    JSONObjectReader objectReader = new JSONObjectReader();
    // Add the type and ID
    objectReader.addNameValuePair(new JSONNameValueReader(new JSONStringReader("id"),new JSONStringReader(uid)))
      .addNameValuePair(new JSONNameValueReader(new JSONStringReader("type"),new JSONStringReader("delete")));

    try
    {
      documentChunkManager.recordDocument(uid, serverHost, serverPath, new ReaderInputStream(objectReader, Consts.UTF_8));
    }
    catch (IOException e)
    {
      handleIOException(e);
    }
  }
  
  @Override
  public void noteJobComplete(IOutputNotifyActivity activities)
      throws ManifoldCFException, ServiceInterruption {
    getSession();
    
    // Repeat until we are empty of cached stuff
    int chunkNumber = 0;
    while (true)
    {
      DocumentRecord[] records = documentChunkManager.readChunk(serverHost, serverPath, 1000);
      try
      {
        if (records.length == 0)
          break;
        // The records consist of up to 1000 individual input streams, which must be all concatenated together into the post
        // To do that, we go into and out of Reader space once again...
        JSONArrayReader arrayReader = new JSONArrayReader();
        for (DocumentRecord dr : records)
        {
          arrayReader.addArrayElement(new JSONValueReader(new InputStreamReader(dr.getDataStream(),Consts.UTF_8)));
        }
        
        //post data..
        String responsbody = postData(new ReaderInputStream(arrayReader,Consts.UTF_8));
        // check status
        String status = getStatusFromJsonResponse(responsbody);
        if("success".equals(status))
        {
          Logging.connectors.info("AmazonCloudSearch: Successfully sent document chunk " + chunkNumber);
          //remove documents from table..
          documentChunkManager.deleteChunk(records);
        }
        else
        {
          Logging.connectors.error("AmazonCloudSearch: Error sending document chunk "+ chunkNumber+": "+ responsbody);
          throw new ManifoldCFException("recieved error status from service after feeding document. response body : " + responsbody);
        }
      }
      finally
      {
        Throwable exception = null;
        for (DocumentRecord dr : records)
        {
          try
          {
            dr.close();
          }
          catch (Throwable e)
          {
            exception = e;
          }
        }
        if (exception != null)
        {
          if (exception instanceof ManifoldCFException)
            throw (ManifoldCFException)exception;
          else if (exception instanceof Error)
            throw (Error)exception;
          else if (exception instanceof RuntimeException)
            throw (RuntimeException)exception;
          else
            throw new RuntimeException("Unknown exception class thrown: "+exception.getClass().getName()+": "+exception.getMessage(),exception);
        }
      }
    }
  }

  /**
   * Fill in a Server tab configuration parameter map for calling a Velocity
   * template.
   *
   * @param newMap is the map to fill in
   * @param parameters is the current set of configuration parameters
   */
  private static void fillInServerConfigurationMap(Map<String, Object> newMap, IPasswordMapperActivity mapper, ConfigParams parameters) {
    String serverhost = parameters.getParameter(AmazonCloudSearchConfig.SERVER_HOST);
    String serverpath = parameters.getParameter(AmazonCloudSearchConfig.SERVER_PATH);
    String proxyprotocol = parameters.getParameter(AmazonCloudSearchConfig.PROXY_PROTOCOL);
    String proxyhost = parameters.getParameter(AmazonCloudSearchConfig.PROXY_HOST);
    String proxyport = parameters.getParameter(AmazonCloudSearchConfig.PROXY_PORT);

    if (serverhost == null)
      serverhost = AmazonCloudSearchConfig.SERVER_HOST_DEFAULT;
    if (serverpath == null)
      serverpath = AmazonCloudSearchConfig.SERVER_PATH_DEFAULT;
    if (proxyprotocol == null)
      proxyprotocol = AmazonCloudSearchConfig.PROXY_PROTOCOL_DEFAULT;
    if (proxyhost == null)
      proxyhost = AmazonCloudSearchConfig.PROXY_HOST_DEFAULT;
    if (proxyport == null)
      proxyport = AmazonCloudSearchConfig.PROXY_PORT_DEFAULT;

    newMap.put("SERVERHOST", serverhost);
    newMap.put("SERVERPATH", serverpath);
    newMap.put("PROXYPROTOCOL", proxyprotocol);
    newMap.put("PROXYHOST", proxyhost);
    newMap.put("PROXYPORT", proxyport);
  }

  /**
   * View configuration. This method is called in the body section of the
   * connector's view configuration page. Its purpose is to present the
   * connection information to the user. The coder can presume that the HTML
   * that is output from this configuration will be within appropriate <html>
   * and <body> tags.
   *
   * @param threadContext is the local thread context.
   * @param out is the output to which any HTML should be sent.
   * @param parameters are the configuration parameters, as they currently
   * exist, for this connection being configured.
   */
  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
      Locale locale, ConfigParams parameters) throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();

    // Fill in map from each tab
    fillInServerConfigurationMap(paramMap, out, parameters);

    Messages.outputResourceWithVelocity(out,locale,VIEW_CONFIGURATION_HTML,paramMap);
  }

  /**
   *
   * Output the configuration header section. This method is called in the
   * head section of the connector's configuration page. Its purpose is to add
   * the required tabs to the list, and to output any javascript methods that
   * might be needed by the configuration editing HTML.
   *
   * @param threadContext is the local thread context.
   * @param out is the output to which any HTML should be sent.
   * @param parameters are the configuration parameters, as they currently
   * exist, for this connection being configured.
   * @param tabsArray is an array of tab names. Add to this array any tab
   * names that are specific to the connector.
   */
  @Override
  public void outputConfigurationHeader(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters, List<String> tabsArray)
      throws ManifoldCFException, IOException {
    // Add the Server tab
    tabsArray.add(Messages.getString(locale, "AmazonCloudSearchOutputConnector.ServerTabName"));
    // Map the parameters
    Map<String, Object> paramMap = new HashMap<String, Object>();

    // Fill in the parameters from each tab
    fillInServerConfigurationMap(paramMap, out, parameters);
        
    // Output the Javascript - only one Velocity template for all tabs
    Messages.outputResourceWithVelocity(out,locale,EDIT_CONFIGURATION_JS,paramMap);
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
      throws ManifoldCFException, IOException {
    
    // Call the Velocity templates for each tab
    Map<String, Object> paramMap = new HashMap<String, Object>();
    
    // Set the tab name
    paramMap.put("TABNAME", tabName);
    
    // Fill in the parameters
    fillInServerConfigurationMap(paramMap, out, parameters);
    
    // Server tab
    Messages.outputResourceWithVelocity(out,locale,EDIT_CONFIGURATION_HTML,paramMap);
  }

  /**
   * Process a configuration post. This method is called at the start of the
   * connector's configuration page, whenever there is a possibility that form
   * data for a connection has been posted. Its purpose is to gather form
   * information and modify the configuration parameters accordingly. The name
   * of the posted form is "editconnection".
   *
   * @param threadContext is the local thread context.
   * @param variableContext is the set of variables available from the post,
   * including binary file post information.
   * @param parameters are the configuration parameters, as they currently
   * exist, for this connection being configured.
   * @return null if all is well, or a string error message if there is an
   * error that should prevent saving of the connection (and cause a
   * redirection to an error page).
   *
   */
  @Override
  public String processConfigurationPost(IThreadContext threadContext,
    IPostParameters variableContext, ConfigParams parameters)
    throws ManifoldCFException {

    // Server tab parameters
    String serverhost = variableContext.getParameter("serverhost");
    if (serverhost != null)
      parameters.setParameter(AmazonCloudSearchConfig.SERVER_HOST, serverhost);
    String serverpath = variableContext.getParameter("serverpath");
    if (serverpath != null)
      parameters.setParameter(AmazonCloudSearchConfig.SERVER_PATH, serverpath);
    String proxyprotocol = variableContext.getParameter("proxyprotocol");
    if (proxyprotocol != null)
      parameters.setParameter(AmazonCloudSearchConfig.PROXY_PROTOCOL, proxyprotocol);
    String proxyhost = variableContext.getParameter("proxyhost");
    if (proxyhost != null)
      parameters.setParameter(AmazonCloudSearchConfig.PROXY_HOST, proxyhost);
    String proxyport = variableContext.getParameter("proxyport");
    if (proxyport != null)
      parameters.setParameter(AmazonCloudSearchConfig.PROXY_PORT, proxyport);

    return null;
  }

  private String postData(InputStream jsonData) throws ServiceInterruption, ManifoldCFException {
    CloseableHttpClient httpclient = HttpClients.createDefault();
    try {
      poster.setEntity(new InputStreamEntity(jsonData));
      HttpResponse res = httpclient.execute(poster);
      
      HttpEntity resEntity = res.getEntity();
      return EntityUtils.toString(resEntity);
    } catch (ClientProtocolException e) {
      throw new ManifoldCFException(e);
    } catch (IOException e) {
      handleIOException(e);
    } finally {
      try {
        httpclient.close();
      } catch (IOException e) {
        //do nothing
      }
    }
    return null;
  }
  
  private static void handleIOException(IOException e)
      throws ManifoldCFException, ServiceInterruption {
    if (!(e instanceof java.net.SocketTimeoutException)
        && (e instanceof InterruptedIOException)) {
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
          ManifoldCFException.INTERRUPTED);
    }
    Logging.connectors.warn(
        "Amazon CloudSearch: IO exception: " + e.getMessage(), e);
    long currentTime = System.currentTimeMillis();
    throw new ServiceInterruption("IO exception: " + e.getMessage(), e,
        currentTime + 300000L, currentTime + 3 * 60 * 60000L, -1, false);
  }
  
  protected static void fillInContentsSpecificationMap(Map<String,Object> paramMap, Specification os)
  {
    String maxFileSize = AmazonCloudSearchConfig.MAXLENGTH_DEFAULT;
    String allowedMimeTypes = AmazonCloudSearchConfig.MIMETYPES_DEFAULT;
    String allowedFileExtensions = AmazonCloudSearchConfig.EXTENSIONS_DEFAULT;
    for (int i = 0; i < os.getChildCount(); i++)
    {
      SpecificationNode sn = os.getChild(i);
      if (sn.getType().equals(AmazonCloudSearchConfig.NODE_MAXLENGTH))
        maxFileSize = sn.getAttributeValue(AmazonCloudSearchConfig.ATTRIBUTE_VALUE);
      else if (sn.getType().equals(AmazonCloudSearchConfig.NODE_MIMETYPES))
        allowedMimeTypes = sn.getValue();
      else if (sn.getType().equals(AmazonCloudSearchConfig.NODE_EXTENSIONS))
        allowedFileExtensions = sn.getValue();
    }
    paramMap.put("MAXFILESIZE",maxFileSize);
    paramMap.put("MIMETYPES",allowedMimeTypes);
    paramMap.put("EXTENSIONS",allowedFileExtensions);
  }
  
  /** Obtain the name of the form check javascript method to call.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return the name of the form check javascript method.
  */
  @Override
  public String getFormCheckJavascriptMethodName(int connectionSequenceNumber)
  {
    return "s"+connectionSequenceNumber+"_checkSpecification";
  }

  /** Obtain the name of the form presave check javascript method to call.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return the name of the form presave check javascript method.
  */
  @Override
  public String getFormPresaveCheckJavascriptMethodName(int connectionSequenceNumber)
  {
    return "s"+connectionSequenceNumber+"_checkSpecificationForSave";
  }

  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected a pipeline connection of the current type.  Its purpose is to add the required tabs
  * to the list, and to output any javascript methods that might be needed by the job editing HTML.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param os is the current pipeline specification for this connection.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("SEQNUM",Integer.toString(connectionSequenceNumber));

    tabsArray.add(Messages.getString(locale, "AmazonCloudSearchOutputConnector.ContentsTabName"));

    // Fill in the specification header map, using data from all tabs.
    fillInContentsSpecificationMap(paramMap, os);

    Messages.outputResourceWithVelocity(out,locale,EDIT_SPECIFICATION_JS,paramMap);
  }
  
  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected a pipeline connection of the current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editjob".
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param os is the current pipeline specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param actualSequenceNumber is the connection within the job that has currently been selected.
  *@param tabName is the current tab name.
  */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber, int actualSequenceNumber, String tabName)
    throws ManifoldCFException, IOException
  {
    Map<String, Object> paramMap = new HashMap<String, Object>();

    // Set the tab name
    paramMap.put("TABNAME", tabName);
    paramMap.put("SEQNUM",Integer.toString(connectionSequenceNumber));
    paramMap.put("SELECTEDNUM",Integer.toString(actualSequenceNumber));

    // Fill in the field mapping tab data
    fillInContentsSpecificationMap(paramMap, os);
    Messages.outputResourceWithVelocity(out,locale,EDIT_SPECIFICATION_CONTENTS_HTML,paramMap);
  }

  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the transformation specification accordingly.
  * The name of the posted form is "editjob".
  *@param variableContext contains the post data, including binary file-upload information.
  *@param locale is the preferred local of the output.
  *@param os is the current pipeline specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
  */
  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, Specification os,
    int connectionSequenceNumber)
    throws ManifoldCFException {
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    String x;
        
    x = variableContext.getParameter(seqPrefix+"maxfilesize");
    if (x != null)
    {
      int i = 0;
      while (i < os.getChildCount())
      {
        SpecificationNode node = os.getChild(i);
        if (node.getType().equals(AmazonCloudSearchConfig.NODE_MAXLENGTH))
          os.removeChild(i);
        else
          i++;
      }
      SpecificationNode sn = new SpecificationNode(AmazonCloudSearchConfig.NODE_MAXLENGTH);
      sn.setAttribute(AmazonCloudSearchConfig.ATTRIBUTE_VALUE,x);
      os.addChild(os.getChildCount(),sn);
    }

    x = variableContext.getParameter(seqPrefix+"mimetypes");
    if (x != null)
    {
      int i = 0;
      while (i < os.getChildCount())
      {
        SpecificationNode node = os.getChild(i);
        if (node.getType().equals(AmazonCloudSearchConfig.NODE_MIMETYPES))
          os.removeChild(i);
        else
          i++;
      }
      SpecificationNode sn = new SpecificationNode(AmazonCloudSearchConfig.NODE_MIMETYPES);
      sn.setValue(x);
      os.addChild(os.getChildCount(),sn);
    }

    x = variableContext.getParameter(seqPrefix+"extensions");
    if (x != null)
    {
      int i = 0;
      while (i < os.getChildCount())
      {
        SpecificationNode node = os.getChild(i);
        if (node.getType().equals(AmazonCloudSearchConfig.NODE_EXTENSIONS))
          os.removeChild(i);
        else
          i++;
      }
      SpecificationNode sn = new SpecificationNode(AmazonCloudSearchConfig.NODE_EXTENSIONS);
      sn.setValue(x);
      os.addChild(os.getChildCount(),sn);
    }
    
    return null;
  }
  

  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the pipeline specification information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param os is the current pipeline specification for this job.
  */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber)
    throws ManifoldCFException, IOException
  {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("SEQNUM",Integer.toString(connectionSequenceNumber));

    // Fill in the map with data from all tabs
    fillInContentsSpecificationMap(paramMap, os);

    Messages.outputResourceWithVelocity(out,locale,VIEW_SPECIFICATION_HTML,paramMap);
    
  }
  
  protected static void fillSet(Set<String> set, String input) {
    try
    {
      StringReader sr = new StringReader(input);
      BufferedReader br = new BufferedReader(sr);
      String line = null;
      while ((line = br.readLine()) != null)
      {
        line = line.trim();
        if (line.length() > 0)
          set.add(line);
      }
    }
    catch (IOException e)
    {
      // Should never happen
      throw new RuntimeException("IO exception reading strings: "+e.getMessage(),e);
    }
  }
  
  protected static class SpecPacker {
    
    private final Set<String> extensions = new HashSet<String>();
    private final Set<String> mimeTypes = new HashSet<String>();
    private final Long lengthCutoff;
    
    public SpecPacker(Specification os) {
      Long lengthCutoff = null;
      String extensions = null;
      String mimeTypes = null;
      for (int i = 0; i < os.getChildCount(); i++) {
        SpecificationNode sn = os.getChild(i);
        
        if (sn.getType().equals(AmazonCloudSearchConfig.NODE_MIMETYPES)) {
          mimeTypes = sn.getValue();
        } else if (sn.getType().equals(AmazonCloudSearchConfig.NODE_EXTENSIONS)) {
          extensions = sn.getValue();
        } else if (sn.getType().equals(AmazonCloudSearchConfig.NODE_MAXLENGTH)) {
          String value = sn.getAttributeValue(AmazonCloudSearchConfig.ATTRIBUTE_VALUE);
          lengthCutoff = new Long(value);
        }
      }
      this.lengthCutoff = lengthCutoff;
      fillSet(this.extensions, extensions);
      fillSet(this.mimeTypes, mimeTypes);
    }
    
    public SpecPacker(String packedString) {
      
      int index = 0;
      
      // Max length
      final StringBuilder sb = new StringBuilder();
      if (packedString.length() > index) {
        if (packedString.charAt(index++) == '+') {
          index = unpack(sb,packedString,index,'+');
          this.lengthCutoff = new Long(sb.toString());
        } else
          this.lengthCutoff = null;
      } else
        this.lengthCutoff = null;
      
      // Mime types
      final List<String> mimeBuffer = new ArrayList<String>();
      index = unpackList(mimeBuffer,packedString,index,'+');
      for (String mimeType : mimeBuffer) {
        this.mimeTypes.add(mimeType);
      }
      
      // Extensions
      final List<String> extensionsBuffer = new ArrayList<String>();
      index = unpackList(extensionsBuffer,packedString,index,'+');
      for (String extension : extensionsBuffer) {
        this.extensions.add(extension);
      }
    }
    
    public String toPackedString() {
      StringBuilder sb = new StringBuilder();
      int i;
      
      // Max length
      if (lengthCutoff == null)
        sb.append('-');
      else {
        sb.append('+');
        pack(sb,lengthCutoff.toString(),'+');
      }
      
      // Mime types
      String[] mimeTypes = new String[this.mimeTypes.size()];
      i = 0;
      for (String mimeType : this.mimeTypes) {
        mimeTypes[i++] = mimeType;
      }
      java.util.Arrays.sort(mimeTypes);
      packList(sb,mimeTypes,'+');
      
      // Extensions
      String[] extensions = new String[this.extensions.size()];
      i = 0;
      for (String extension : this.extensions) {
        extensions[i++] = extension;
      }
      java.util.Arrays.sort(extensions);
      packList(sb,extensions,'+');
      
      return sb.toString();
    }
    
    public boolean checkLengthIndexable(long length) {
      if (lengthCutoff == null)
        return true;
      return (length <= lengthCutoff.longValue());
    }
    
    public boolean checkMimeType(String mimeType) {
      if (mimeType == null)
        mimeType = "application/unknown";
      return mimeTypes.contains(mimeType);
    }
    
    public boolean checkURLIndexable(String url) {
      String extension = FilenameUtils.getExtension(url);
      if (extension == null || extension.length() == 0)
        extension = ".";
      return extensions.contains(extension);
    }
    
  }
  
}
