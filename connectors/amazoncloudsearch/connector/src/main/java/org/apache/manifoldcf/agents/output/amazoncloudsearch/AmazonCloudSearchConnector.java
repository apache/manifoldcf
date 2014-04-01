package org.apache.manifoldcf.agents.output.amazoncloudsearch;

import java.io.IOException;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.pool.BasicConnFactory;
import org.apache.http.util.EntityUtils;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputNotifyActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputRemoveActivity;
import org.apache.manifoldcf.agents.interfaces.OutputSpecification;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.output.BaseOutputConnector;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;

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
    String documentEndpointUrl = "doc-hoge2-qgylbfd7tjp5sn3giayslhfkye.us-east-1.cloudsearch.amazonaws.com";
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

      HttpClient httpclient = new DefaultHttpClient();
      HttpResponse res = httpclient.execute(poster);
      HttpEntity resEntity = res.getEntity();
      String responsbody = EntityUtils.toString(resEntity);
      
      String status = "";
      String message = "";
      JsonFactory factory = new JsonFactory();
      JsonParser parser = factory.createJsonParser(responsbody);
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        String name = parser.getCurrentName();
        if("status".equalsIgnoreCase(name)){
          status = parser.getValueAsString();
        }else if("errors".equalsIgnoreCase(name)){
          message = parseMessage(parser);
        }
      }
      if("error".equalsIgnoreCase(status) &&
          "Encountered unexpected end of file".equals(message)){
        return "Connection working.";
      }
      return "Connection NOT working.";
    }
    catch (ClientProtocolException e) {
      throw new ManifoldCFException(e);
    } catch (IOException e) {
      throw new ManifoldCFException(e);
    }
  }
  
  private String parseMessage(JsonParser parser) throws JsonParseException, IOException {
    while(parser.nextToken() != JsonToken.END_ARRAY){
      String name = parser.getCurrentName();
      if("message".equalsIgnoreCase(name)){
        return parser.getValueAsString();
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
//  public boolean checkMimeTypeIndexable(String outputDescription, String mimeType)
//    throws ManifoldCFException, ServiceInterruption
//  {
//    getSession();
//    
//    
//    
//    if (includedMimeTypes != null && includedMimeTypes.get(mimeType) == null)
//      return false;
//    if (excludedMimeTypes != null && excludedMimeTypes.get(mimeType) != null)
//      return false;
//    return super.checkMimeTypeIndexable(outputDescription,mimeType);
//  }

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
    
    document.getBinaryStream();
    
    Parser parser = new AutoDetectParser();
    
    activities.recordActivity(null,INGEST_ACTIVITY,new Long(document.getBinaryLength()),documentURI,"OK",null);
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
    
    CloseableHttpClient httpclient = HttpClients.createDefault();
    try {
      poster.setEntity(new StringEntity(jsonData));
      HttpResponse res = httpclient.execute(poster);
      
      HttpEntity resEntity = res.getEntity();
      String responsbody = EntityUtils.toString(resEntity);
      
      System.out.println(responsbody);
      
    } catch (ClientProtocolException e) {
      throw new ManifoldCFException(e);
    } catch (IOException e) {
      throw new ManifoldCFException(e);
    } finally {
      try {
        httpclient.close();
      } catch (IOException e) {
        //do nothing
      }
    }
    
    //activities.recordActivity(null,REMOVE_ACTIVITY,null,documentURI,"OK",null);
  }
  
}