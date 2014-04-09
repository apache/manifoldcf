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
import java.util.List;
import java.util.Map;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
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
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
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

public class AmazonCloudSearchConnector  extends BaseOutputConnector {

  /** Ingestion activity */
  public final static String INGEST_ACTIVITY = "document ingest";
  /** Document removal activity */
  public final static String REMOVE_ACTIVITY = "document deletion";

  /** Local connection */
  protected HttpPost poster = null;

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
    //curl -X POST --upload-file data1.json doc.movies-123456789012.us-east-1.cloudsearch.amazonaws.com/2013-01-01/documents/batch --header "Content-Type:application/json"
    String documentEndpointUrl = "doc-test1-hjzolhfixtfctmuaezbzinjduu.us-east-1.cloudsearch.amazonaws.com";
    String urlStr = "https://" + documentEndpointUrl + "/2013-01-01/documents/batch";
    poster = new HttpPost(urlStr);
    
    //set proxy
    String proxyHost = System.getenv().get("HTTP_PROXY");
    if(proxyHost != null)
    {
      String host = proxyHost.substring(proxyHost.indexOf("://")+3,proxyHost.lastIndexOf(":"));
      String port = proxyHost.substring(proxyHost.lastIndexOf(":")+1,proxyHost.length()-1);
      HttpHost proxy = new HttpHost(host, Integer.parseInt(port), "http");
      RequestConfig config = RequestConfig.custom().setProxy(proxy).build();
      poster.setConfig(config);
    }
    
    poster.addHeader("Content-Type", "application/json");
  }

  /** Test the connection.  Returns a string describing the connection integrity.
  *@return the connection's status as a displayable string.
  */
  @Override
  public String check()
    throws ManifoldCFException
  {
    try
    {
      getSession();
      String responsbody = postData("[]");
      String status = getStatusFromJsonResponse(responsbody);
      
      //check status message
      String message = "";
      if("error".equals(status))
      {
        JsonParser parser = new JsonFactory().createJsonParser(responsbody);
        while (parser.nextToken() != JsonToken.END_OBJECT) {
          String name = parser.getCurrentName();
          if("errors".equalsIgnoreCase(name)){
            message = parseMessage(parser);
            break;
          }
        }
      }
      
      if("error".equalsIgnoreCase(status) &&
          "batch must contain at least one operation".equals(message)){
        return "Connection working.";
      }
      return "Connection NOT working.";
    }
    catch (ClientProtocolException e) {
      throw new ManifoldCFException(e);
    } catch (IOException e) {
      throw new ManifoldCFException(e);
    } catch (ServiceInterruption e) {
      throw new ManifoldCFException(e);
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
  *@param spec is the current output specification for the job that is doing the crawling.
  *@return a string, of unlimited length, which uniquely describes output configuration and specification in such a way that if two such strings are equal,
  * the document will not need to be sent again to the output data store.
  */
  @Override
  public String getOutputDescription(OutputSpecification spec)
    throws ManifoldCFException, ServiceInterruption
  {
    return "";
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
    getSession();
    
    if(("text/html").equalsIgnoreCase(mimeType)){
      return super.checkMimeTypeIndexable(outputDescription,mimeType);
    }
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
  public int addOrReplaceDocument(String documentURI, String outputDescription, RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    // Establish a session
    getSession();
    String jsondata = "";
    try {
      InputStream is = document.getBinaryStream();
      Parser parser = new HtmlParser();
      ContentHandler handler = new BodyContentHandler();
      Metadata metadata = new Metadata();
      parser.parse(is, handler, metadata, new ParseContext());
      
      //build json..
      SDFModel model = new SDFModel();
      Document doc = model.new Document();
      doc.setType("add");
      doc.setId(documentURI);
      
      //set body text.
      Map<String,Object> fields = new HashMap<String,Object>();
      String bodyStr = handler.toString();
      if(bodyStr != null){
        bodyStr = handler.toString().replaceAll("\\n", "").replaceAll("\\t", "");
        fields.put("body", bodyStr);
      }
      
      //mapping metadata to SDF fields.
      String contenttype = metadata.get("Content-Style-Type");
      String title = metadata.get("dc:title");
      String size = String.valueOf(bodyStr.length());
      String description = metadata.get("description");
      String keywords = metadata.get("keywords");
      if(contenttype != null && !"".equals(contenttype)) fields.put("content_type", contenttype);
      if(title != null && !"".equals(title)) fields.put("title", title);
      if(size != null && !"".equals(size)) fields.put("size", size);
      if(description != null && !"".equals(description)) fields.put("description", description);
      if(keywords != null && !"".equals(keywords))
      {
        List<String> keywordList = new ArrayList<String>();
        for(String tmp : keywords.split(",")){
          keywordList.add(tmp);
        }
        fields.put("keywords", keywordList);
      }
      doc.setFields(fields);
      model.addDocument(doc);
      
      //generate json data.
      jsondata = model.toJSON();
      
    } 
    catch (SAXException e) {
      // if document data could not be converted to JSON by jackson.
      throw new ManifoldCFException(e);
    } catch (JsonProcessingException e) {
      // if document data could not be converted to JSON by jackson.
      throw new ManifoldCFException(e);
    } catch (TikaException e) {
      // if document could not be parsed by tika.
      return DOCUMENTSTATUS_REJECTED;
    } catch (IOException e) {
      // if document data could not be read when the document parsing by tika.
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
      throw new ManifoldCFException("recieved error status from service after feeding document.");
    }
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
  
}