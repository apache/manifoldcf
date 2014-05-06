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
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import org.apache.commons.io.FilenameUtils;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputRemoveActivity;
import org.apache.manifoldcf.agents.interfaces.OutputSpecification;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.output.BaseOutputConnector;
import org.apache.manifoldcf.agents.output.amazoncloudsearch.SDFModel.Document;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.ConfigurationNode;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IPasswordMapperActivity;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AmazonCloudSearchConnector  extends BaseOutputConnector {

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
  
  private static final String EDIT_SPECIFICATION_CONTENT_HTML = "editSpecification.html";
  
  private static final String VIEW_SPEC_FORWARD = "viewSpecification.html";
  
  /** Local connection */
  protected HttpPost poster = null;
  
  /** cloudsearch field name for file body text. */
  private static final String FILE_BODY_TEXT_FIELDNAME = "f_bodytext";
  
  /** Constructor.
   */
  public AmazonCloudSearchConnector(){
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
    if (poster != null)
    {
      poster = null;
    }
    super.disconnect();
  }

  /** Set up a session */
  protected void getSession()
    throws ManifoldCFException
  {
    String serverHost = params.getParameter(AmazonCloudSearchConfig.SERVER_HOST);
    if (serverHost == null)
      throw new ManifoldCFException("Server host parameter required");
    String serverPath = params.getParameter(AmazonCloudSearchConfig.SERVER_PATH);
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
      String responsbody = postData("[]");
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
  public String getOutputDescription(OutputSpecification os)
    throws ManifoldCFException, ServiceInterruption
  {
    // Do the source/target pairs
    int i = 0;
    Map<String, List<String>> sourceTargets = new HashMap<String, List<String>>();
    boolean keepAllMetadata = true;
    while (i < os.getChildCount()) {
      SpecificationNode sn = os.getChild(i++);
      
      if(sn.getType().equals(AmazonCloudSearchConfig.NODE_KEEPMETADATA)) {
        String value = sn.getAttributeValue(AmazonCloudSearchConfig.ATTRIBUTE_VALUE);
        keepAllMetadata = Boolean.parseBoolean(value);
      } else if (sn.getType().equals(AmazonCloudSearchConfig.NODE_FIELDMAP)) {
        String source = sn.getAttributeValue(AmazonCloudSearchConfig.ATTRIBUTE_SOURCE);
        String target = sn.getAttributeValue(AmazonCloudSearchConfig.ATTRIBUTE_TARGET);
        
        if (target == null) {
          target = "";
        }
        List<String> list = (List<String>)sourceTargets.get(source);
        if (list == null) {
          list = new ArrayList<String>();
          sourceTargets.put(source, list);
        }
        list.add(target);
      }
    }
    
    String[] sortArray = new String[sourceTargets.size()];
    Iterator iter = sourceTargets.keySet().iterator();
    i = 0;
    while (iter.hasNext()) {
      sortArray[i++] = (String)iter.next();
    }
    java.util.Arrays.sort(sortArray);
    
    ArrayList<String[]> sourceTargetsList = new ArrayList<String[]>();
    i = 0;
    while (i < sortArray.length) {
      String source = sortArray[i++];
      List<String> values = (List<String>)sourceTargets.get(source);
      java.util.Collections.sort(values);
      int j = 0;
      while (j < values.size()) {
        String target = (String)values.get(j++);
        String[] fixedList = new String[2];
        fixedList[0] = source;
        fixedList[1] = target;
        sourceTargetsList.add(fixedList);
      }
    }
    
    //for Content tab
    AmazonCloudSearchSpecs spec = new AmazonCloudSearchSpecs(getSpecNode(os));
    
    //build json 
    // {"fieldMappings":{"Content-Style-Type":"content_type","dc:title":"title","description":"description"},"keepAllMetadata":true}
    String resultBody = "";
    try {
      ObjectMapper mapper = new ObjectMapper();
      
      ObjectNode rootNode = mapper.createObjectNode();
      ObjectNode fm = rootNode.putObject("fieldMappings");
      for(String[] f : sourceTargetsList){
        fm.put(f[0], f[1]);
      }
      
      // Keep all metadata flag
      if (keepAllMetadata)
      {
        rootNode.put("keepAllMetadata", true);
      }
      else
      {
        rootNode.put("keepAllMetadata", false);
      }
      
      // Content tab..
      rootNode.put("content", mapper.valueToTree(spec));
      
      resultBody = mapper.writeValueAsString(rootNode);
    } catch (JsonProcessingException e) {
      throw new ManifoldCFException(e);
    }
    return resultBody;
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
    try {
      ObjectMapper mapper = new ObjectMapper();
      AmazonCloudSearchSpecs spec = new AmazonCloudSearchSpecs(mapper.readTree(outputDescription).get("content"));
      if(spec.checkMimeType(mimeType))
      {
        return super.checkMimeTypeIndexable(mimeType);
      }else
      {
        return false;
      }
    } catch (JsonProcessingException e) {
      throw new ManifoldCFException(e);
    } catch (IOException e) {
      throw new ManifoldCFException(e);
    }
  }

  @Override
  public boolean checkLengthIndexable(String outputDescription, long length)
      throws ManifoldCFException, ServiceInterruption {
    try {
      ObjectMapper mapper = new ObjectMapper();
      AmazonCloudSearchSpecs spec = new AmazonCloudSearchSpecs(mapper.readTree(outputDescription).get("content"));
      long maxFileSize = spec.getMaxFileSize();
      if (length > maxFileSize)
      {
        return false;
      }
      return super.checkLengthIndexable(outputDescription, length);
    } catch (JsonProcessingException e) {
      throw new ManifoldCFException(e);
    } catch (IOException e){
      throw new ManifoldCFException(e);
    }
  }

  @Override
  public boolean checkURLIndexable(String outputDescription, String url)
    throws ManifoldCFException, ServiceInterruption {
    try {
      ObjectMapper mapper = new ObjectMapper();
      AmazonCloudSearchSpecs spec = new AmazonCloudSearchSpecs(mapper.readTree(outputDescription).get("content"));
      if (spec.checkExtension(FilenameUtils.getExtension(url)))
      {
        return super.checkURLIndexable(outputDescription, url);
      }
      return false;
    } catch (JsonProcessingException e) {
      throw new ManifoldCFException(e);
    } catch (IOException e){
      throw new ManifoldCFException(e);
    }
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
  public int addOrReplaceDocument(String documentURI, String outputDescription, RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    // Establish a session
    getSession();
    
    Map<String, List<String>> sourceTargets = new HashMap<String, List<String>>();
    boolean keepAllMetadata = true;
    keepAllMetadata = readConfigurationDescription(outputDescription, sourceTargets);
    
    String jsondata = "";
    try {
      //build json..
      SDFModel model = new SDFModel();
      Document doc = model.new Document();
      doc.setType("add");
      doc.setId(documentURI);
      
      HashMap fields = new HashMap();
      Metadata metadata = extractBinaryFile(document, fields);
      
      Iterator<String> itr = document.getFields();
      while(itr.hasNext())
      {
        String fName = itr.next();
        Object[] value = document.getField(fName);
        if(sourceTargets.get(fName)!=null)
        {
          List<String> fnameList = sourceTargets.get(fName);
          for(String newName : fnameList)
          {
            fields.put(newName, value);
          }
        }
        else
        {
          if(keepAllMetadata)
          {
            fields.put(fName, value);
          }
        }
      }
      
      //metadata of binary files.
      String[] metaNames = metadata.names();
      for(String mName : metaNames){
        String value = metadata.get(mName);
        if(sourceTargets.get(mName)!=null)
        {
          List<String> nameList = sourceTargets.get(mName);
          for(String newName : nameList)
          {
            fields.put(newName, value);
          }
        }
        else
        {
          if(keepAllMetadata)
          {
            fields.put(mName, value);
          }
        }
      }
      doc.setFields(fields);
      model.addDocument(doc);
      
      //generate json data.
      jsondata = model.toJSON();
    } 
    catch (SAXException e) {
      // if document data could not be converted to JSON by jackson.
      Logging.connectors.debug(e);
      throw new ManifoldCFException(e);
    } catch (JsonProcessingException e) {
      // if document data could not be converted to JSON by jackson.
      Logging.connectors.debug(e);
      throw new ManifoldCFException(e);
    } catch (TikaException e) {
      // if document could not be parsed by tika.
      Logging.connectors.debug(e);
      return DOCUMENTSTATUS_REJECTED;
    } catch (IOException e) {
      // if document data could not be read when the document parsing by tika.
      Logging.connectors.debug(e);
      throw new ManifoldCFException(e);
    }
    
    //post data..
    String responsbody = postData(jsondata);
    
    // check status
    String status = getStatusFromJsonResponse(responsbody);
    if("success".equals(status))
    {
      activities.recordActivity(null,INGEST_ACTIVITY,new Long(document.getBinaryLength()),documentURI,"OK",null);
      return DOCUMENTSTATUS_ACCEPTED;
    }
    else {
      throw new ManifoldCFException("recieved error status from service after feeding document. response body : " + responsbody);
    }
  }

  private boolean readConfigurationDescription(String outputDescription,
      Map<String, List<String>> sourceTargets)
      throws ManifoldCFException {
    ObjectMapper mapper = new ObjectMapper();
    
    boolean keepAllMetadata = true;
    try
    {
      JsonNode node = mapper.readTree(outputDescription);
      Iterator<String> ir = node.fieldNames();
      while(ir.hasNext()){
        String fieldName = ir.next();
        if("fieldMappings".equals(fieldName))
        {
          JsonNode fm = node.path(fieldName);
          Iterator<String> itr = fm.fieldNames();
          while(itr.hasNext())
          {
            String from = itr.next();
            String to = fm.path(from).asText();
            
            List<String> list = sourceTargets.get(from);
            if (list == null) {
              list = new ArrayList<String>();
              sourceTargets.put(from, list);
            }
            list.add(to);
          }
        }
        else if("keepAllMetadata".equals(fieldName)){
          String meta = node.path(fieldName).toString();
          keepAllMetadata = Boolean.getBoolean(meta);
        }
      }
      return keepAllMetadata;
      
    } catch (JsonProcessingException e) {
      throw new ManifoldCFException(e);
    } catch (IOException e) {
      throw new ManifoldCFException(e);
    }
  }

  private Metadata extractBinaryFile(RepositoryDocument document, HashMap fields)
      throws IOException, SAXException, TikaException {
    
    //extract body text and metadata fields from binary file.
    InputStream is = document.getBinaryStream();
    Parser parser = new HtmlParser(); //TODO
    ContentHandler handler = new BodyContentHandler();
    Metadata metadata = new Metadata();
    parser.parse(is, handler, metadata, new ParseContext());
    String bodyStr = handler.toString();
    if(bodyStr != null){
      bodyStr = handler.toString().replaceAll("\\n", "").replaceAll("\\t", "");
      fields.put(FILE_BODY_TEXT_FIELDNAME, bodyStr);
    }
    return metadata;
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
    
    String jsonData = "";
    try {
      SDFModel model = new SDFModel();
      SDFModel.Document doc = model.new Document();
      doc.setType("delete");
      doc.setId(documentURI);
      model.addDocument(doc);
      jsonData = model.toJSON();
    } catch (JsonProcessingException e) {
      throw new ManifoldCFException(e);
    }
    String responsbody = postData(jsonData);
    
    // check status
    String status = getStatusFromJsonResponse(responsbody);
    if("success".equals(status))
    {
      activities.recordActivity(null,REMOVE_ACTIVITY,null,documentURI,"OK",null);
    }
    else {
      throw new ManifoldCFException("recieved error status from service after feeding document.");
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

  private String postData(String jsonData) throws ServiceInterruption, ManifoldCFException {
    CloseableHttpClient httpclient = HttpClients.createDefault();
    try {
      poster.setEntity(new StringEntity(jsonData, Consts.UTF_8));
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
  
  /**
   * Output the specification header section. This method is called in the head
   * section of a job page which has selected an output connection of the
   * current type. Its purpose is to add the required tabs to the list, and to
   * output any javascript methods that might be needed by the job editing HTML.
   * 
   * @param out is the output to which any HTML should be sent.
   * @param os is the current output specification for this job.
   * @param tabsArray is an array of tab names. Add to this array any tab names
   *        that are specific to the connector.
   */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale,
      OutputSpecification os, List<String> tabsArray)
      throws ManifoldCFException, IOException
  {
    super.outputSpecificationHeader(out, locale, os, tabsArray);
    tabsArray.add(Messages.getString(locale, "AmazonCloudSearchOutputConnector.FieldMappingTabName"));
    tabsArray.add(Messages.getString(locale, "AmazonCloudSearchOutputConnector.ContentsTabName"));
    outputResource(EDIT_SPECIFICATION_JS, out, locale, null, null);
  }
  
  /**
   * Read the content of a resource, replace the variable ${PARAMNAME} with the
   * value and copy it to the out.
   * 
   * @param resName
   * @param out
   * @throws ManifoldCFException
   */
  private static void outputResource(String resName, IHTTPOutput out,
    Locale locale, AmazonCloudSearchParam params, String tabName) throws ManifoldCFException {
    Map<String,String> paramMap = null;
    if (params != null) {
      paramMap = params.buildMap();
      if (tabName != null) {
        paramMap.put("TabName", tabName);
      }
    }
    
    Messages.outputResourceWithVelocity(out,locale,resName,paramMap,false);
  }

  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected an output connection of the current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editjob".
  *@param out is the output to which any HTML should be sent.
  *@param os is the current output specification for this job.
  *@param tabName is the current tab name.
  */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, OutputSpecification os, String tabName)
    throws ManifoldCFException, IOException
  {
    int i = 0;
    
    // Field Mapping tab
    if (tabName.equals(Messages.getString(locale,"AmazonCloudSearchOutputConnector.FieldMappingTabName")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"AmazonCloudSearchOutputConnector.FieldMappings") + "</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"AmazonCloudSearchOutputConnector.MetadataFieldName") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"AmazonCloudSearchOutputConnector.CloudSearchFieldName") + "</nobr></td>\n"+
"        </tr>\n"
      );

      int fieldCounter = 0;
      i = 0;
      boolean keepMetadata = true;
      while (i < os.getChildCount()) {
        SpecificationNode sn = os.getChild(i++);
        if (sn.getType().equals(AmazonCloudSearchConfig.NODE_FIELDMAP)) {
          String source = sn.getAttributeValue(AmazonCloudSearchConfig.ATTRIBUTE_SOURCE);
          String target = sn.getAttributeValue(AmazonCloudSearchConfig.ATTRIBUTE_TARGET);
          if (target != null && target.length() == 0) {
            target = null;
          }
          String targetDisplay = target;
          if (target == null)
          {
            target = "";
            targetDisplay = "(remove)";
          }
          // It's prefix will be...
          String prefix = "cloudsearch_fieldmapping_" + Integer.toString(fieldCounter);
          out.print(
"        <tr class=\""+(((fieldCounter % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\""+prefix+"\">\n"+
"              <input type=\"button\" value=\"Delete\" alt=\""+Messages.getAttributeString(locale,"AmazonCloudSearchOutputConnector.DeleteFieldMapping")+Integer.toString(fieldCounter+1)+"\" onclick='javascript:deleteFieldMapping("+Integer.toString(fieldCounter)+");'/>\n"+
"              <input type=\"hidden\" name=\""+prefix+"_op\" value=\"Continue\"/>\n"+
"              <input type=\"hidden\" name=\""+prefix+"_source\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(source)+"\"/>\n"+
"              <input type=\"hidden\" name=\""+prefix+"_target\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(target)+"\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(source)+"</nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(targetDisplay)+"</nobr>\n"+
"          </td>\n"+
"        </tr>\n"
          );
          fieldCounter++;
        }
        else if(sn.getType().equals(AmazonCloudSearchConfig.NODE_KEEPMETADATA)) {
          keepMetadata = Boolean.parseBoolean(sn.getAttributeValue(AmazonCloudSearchConfig.ATTRIBUTE_VALUE));
        }
      }
      
      if (fieldCounter == 0)
      {
        out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"3\">" + Messages.getBodyString(locale,"AmazonCloudSearchOutputConnector.NoFieldMappingSpecified") + "</td></tr>\n"
        );
      }
      
      String keepMetadataValue;
      if (keepMetadata)
        keepMetadataValue = " checked=\"true\"";
      else
        keepMetadataValue = "";

      out.print(
"        <tr class=\"formrow\"><td class=\"formseparator\" colspan=\"3\"><hr/></td></tr>\n"+
"        <tr class=\"formrow\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\"cloudsearch_fieldmapping\">\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"AmazonCloudSearchOutputConnector.Add") + "\" alt=\"" + Messages.getAttributeString(locale,"AmazonCloudSearchOutputConnector.AddFieldMapping") + "\" onclick=\"javascript:addFieldMapping();\"/>\n"+
"            </a>\n"+
"            <input type=\"hidden\" name=\"cloudsearch_fieldmapping_count\" value=\""+fieldCounter+"\"/>\n"+
"            <input type=\"hidden\" name=\"cloudsearch_fieldmapping_op\" value=\"Continue\"/>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"15\" name=\"cloudsearch_fieldmapping_source\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"15\" name=\"cloudsearch_fieldmapping_target\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"        </tr>\n"+
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"AmazonCloudSearchOutputConnector.KeepAllMetadata")+"</nobr></td>\n"+
"    <td class=\"value\">\n"+
"       <input type=\"checkbox\""+keepMetadataValue+" name=\"cloudsearch_keepallmetadata\" value=\"true\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for field mapping
      i = 0;
      int fieldCounter = 0;
      String keepMetadataValue = "true";
      while (i < os.getChildCount()) {
        SpecificationNode sn = os.getChild(i++);
        if (sn.getType().equals(AmazonCloudSearchConfig.NODE_FIELDMAP)) {
          String source = sn.getAttributeValue(AmazonCloudSearchConfig.ATTRIBUTE_SOURCE);
          String target = sn.getAttributeValue(AmazonCloudSearchConfig.ATTRIBUTE_TARGET);
          if (target == null)
            target = "";
        // It's prefix will be...
          String prefix = "cloudsearch_fieldmapping_" + Integer.toString(fieldCounter);
          out.print(
"<input type=\"hidden\" name=\""+prefix+"_source\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(source)+"\"/>\n"+
"<input type=\"hidden\" name=\""+prefix+"_target\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(target)+"\"/>\n"
          );
          fieldCounter++;
        }
        else if(sn.getType().equals(AmazonCloudSearchConfig.NODE_KEEPMETADATA))
        {
          keepMetadataValue = sn.getAttributeValue(AmazonCloudSearchConfig.ATTRIBUTE_VALUE);
        }
      }
      out.print(
"<input type=\"hidden\" name=\"cloudsearch_keepallmetadata\" value=\""+keepMetadataValue+"\"/>\n"
      );
      out.print(
"<input type=\"hidden\" name=\"cloudsearch_fieldmapping_count\" value=\""+Integer.toString(fieldCounter)+"\"/>\n"
      );
    }
    

    // Content tab
    AmazonCloudSearchSpecs param = new AmazonCloudSearchSpecs(getSpecNode(os));
    outputResource(EDIT_SPECIFICATION_CONTENT_HTML, out, locale, param, tabName);
    
  }

  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the output specification accordingly.
  * The name of the posted form is "editjob".
  *@param variableContext contains the post data, including binary file-upload information.
  *@param os is the current output specification for this job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
  */
  @Override
  public String processSpecificationPost(IPostParameters variableContext,
      Locale locale, OutputSpecification os) throws ManifoldCFException {
    
    ConfigurationNode specNode = getSpecNode(os);
    boolean bAdd = (specNode == null);
    if (bAdd) 
    {
      specNode = new SpecificationNode(AmazonCloudSearchSpecs.AMAZONCLOUDSEARCH_SPECS_NODE);
    }
    AmazonCloudSearchSpecs.contextToSpecNode(variableContext, specNode);
    if (bAdd)
    {
      os.addChild(os.getChildCount(), specNode);
    }
    
    String x = variableContext.getParameter("cloudsearch_fieldmapping_count");
    if (x != null && x.length() > 0)
    {
      // About to gather the fieldmapping nodes, so get rid of the old ones.
      int i = 0;
      while (i < os.getChildCount())
      {
        SpecificationNode node = os.getChild(i);
        if (node.getType().equals(AmazonCloudSearchConfig.NODE_FIELDMAP) || node.getType().equals(AmazonCloudSearchConfig.NODE_KEEPMETADATA))
          os.removeChild(i);
        else
          i++;
      }
      int count = Integer.parseInt(x);
      i = 0;
      while (i < count)
      {
        String prefix = "cloudsearch_fieldmapping_"+Integer.toString(i);
        String op = variableContext.getParameter(prefix+"_op");
        if (op == null || !op.equals("Delete"))
        {
          // Gather the fieldmap etc.
          String source = variableContext.getParameter(prefix+"_source");
          String target = variableContext.getParameter(prefix+"_target");
          if (target == null)
            target = "";
          SpecificationNode node = new SpecificationNode(AmazonCloudSearchConfig.NODE_FIELDMAP);
          node.setAttribute(AmazonCloudSearchConfig.ATTRIBUTE_SOURCE,source);
          node.setAttribute(AmazonCloudSearchConfig.ATTRIBUTE_TARGET,target);
          os.addChild(os.getChildCount(),node);
        }
        i++;
      }
      
      String addop = variableContext.getParameter("cloudsearch_fieldmapping_op");
      if (addop != null && addop.equals("Add"))
      {
        String source = variableContext.getParameter("cloudsearch_fieldmapping_source");
        String target = variableContext.getParameter("cloudsearch_fieldmapping_target");
        if (target == null)
          target = "";
        SpecificationNode node = new SpecificationNode(AmazonCloudSearchConfig.NODE_FIELDMAP);
        node.setAttribute(AmazonCloudSearchConfig.ATTRIBUTE_SOURCE,source);
        node.setAttribute(AmazonCloudSearchConfig.ATTRIBUTE_TARGET,target);
        os.addChild(os.getChildCount(),node);
      }
      
      // Gather the keep all metadata parameter to be the last one
      SpecificationNode node = new SpecificationNode(AmazonCloudSearchConfig.NODE_KEEPMETADATA);
      String keepAll = variableContext.getParameter("cloudsearch_keepallmetadata");
      if (keepAll != null)
      {
        node.setAttribute(AmazonCloudSearchConfig.ATTRIBUTE_VALUE, keepAll);
      }
      else
      {
        node.setAttribute(AmazonCloudSearchConfig.ATTRIBUTE_VALUE, "false");
      }
      // Add the new keepallmetadata config parameter 
      os.addChild(os.getChildCount(), node);
    }
    
    return null;
  }
  

  final private SpecificationNode getSpecNode(OutputSpecification os) {
    int l = os.getChildCount();
    for (int i = 0; i < l; i++)
    {
      SpecificationNode node = os.getChild(i);
      if (AmazonCloudSearchSpecs.AMAZONCLOUDSEARCH_SPECS_NODE.equals(node.getType()))
      {
        return node;
      }
    }
    return null;
  }
  
  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the output specification information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param out is the output to which any HTML should be sent.
  *@param os is the current output specification for this job.
  */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, OutputSpecification os)
    throws ManifoldCFException, IOException
  {
    // Prep for field mappings
    int i = 0;
    
    // Display field mappings
    out.print(
"\n"+
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"AmazonCloudSearchOutputConnector.FieldMappings") + "</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"AmazonCloudSearchOutputConnector.MetadataFieldName") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"AmazonCloudSearchOutputConnector.CloudSearchFieldName") + "</nobr></td>\n"+
"        </tr>\n"
    );

    int fieldCounter = 0;
    i = 0;
    String keepAllMetadataValue = "true";
    while (i < os.getChildCount()) {
      SpecificationNode sn = os.getChild(i++);
      if (sn.getType().equals(AmazonCloudSearchConfig.NODE_FIELDMAP)) {
        String source = sn.getAttributeValue(AmazonCloudSearchConfig.ATTRIBUTE_SOURCE);
        String target = sn.getAttributeValue(AmazonCloudSearchConfig.ATTRIBUTE_TARGET);
        String targetDisplay = target;
        if (target == null)
        {
          target = "";
          targetDisplay = "(remove)";
        }
        out.print(
"        <tr class=\""+(((fieldCounter % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(source)+"</nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(targetDisplay)+"</nobr>\n"+
"          </td>\n"+
"        </tr>\n"
        );
        fieldCounter++;
      }
      else if (sn.getType().equals(AmazonCloudSearchConfig.NODE_KEEPMETADATA))
      {
        keepAllMetadataValue = sn.getAttributeValue(AmazonCloudSearchConfig.ATTRIBUTE_VALUE);
      }
    }
    
    if (fieldCounter == 0)
    {
      out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"3\">" + Messages.getBodyString(locale,"AmazonCloudSearchOutputConnector.NoFieldMappingSpecified") + "</td></tr>\n"
      );
    }
    out.print(
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"AmazonCloudSearchOutputConnector.KeepAllMetadata") + "</nobr></td>\n"+
"    <td class=\"value\"><nobr>" + keepAllMetadataValue + "</nobr></td>\n"+
"  </tr>\n"+
"</table>\n"
    );
    
    AmazonCloudSearchSpecs spec = new AmazonCloudSearchSpecs(getSpecNode(os));
    outputResource(VIEW_SPEC_FORWARD, out, locale, spec, null);
    
  }
  
}
