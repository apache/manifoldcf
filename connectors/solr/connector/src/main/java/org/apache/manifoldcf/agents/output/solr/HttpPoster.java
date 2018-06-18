/* $Id: HttpPoster.java 991295 2010-08-31 19:12:14Z kwright $ */

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
package org.apache.manifoldcf.agents.output.solr;

import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.connectorcommon.interfaces.*;
import org.apache.manifoldcf.core.common.DateParser;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.agents.system.*;

import java.io.*;
import java.net.MalformedURLException;
import java.util.*;
import java.util.regex.*;

import org.apache.http.Consts;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.impl.XMLResponseParser;
import org.apache.solr.common.util.ContentStreamBase;
import org.apache.solr.client.solrj.SolrResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.ContentStream;
import org.apache.solr.common.SolrException;
import org.apache.solr.client.solrj.impl.HttpClientUtil;
import org.apache.solr.common.SolrInputDocument;

import org.apache.commons.lang.StringUtils;

/**
* Posts an input stream to SOLR
*
* @author James Sablatura, modified by Karl Wright
*/
public class HttpPoster
{
  public static final String _rcsid = "@(#)$Id: HttpPoster.java 991295 2010-08-31 19:12:14Z kwright $";

  /** Ingestion buffer size property. */
  public static String ingestBufferSizeProperty = "org.apache.manifoldcf.ingest.buffersize";
  public static String ingestCredentialsRealm = "org.apache.manifoldcf.ingest.credentialrealm";
  public static String ingestResponseRetryCount = "org.apache.manifoldcf.ingest.responseretrycount";
  public static String ingestResponseRetryInterval = "org.apache.manifoldcf.ingest.retryinterval";
  public static String ingestRescheduleInterval = "org.apache.manifoldcf.ingest.rescheduleinterval";
  public static String ingestURIProperty = "org.apache.manifoldcf.ingest.uri";
  public static String ingestUserProperty = "org.apache.manifoldcf.ingest.user";
  public static String ingestPasswordProperty = "org.apache.manifoldcf.ingest.password";
  public static String ingestMaxConnectionsProperty = "org.apache.manifoldcf.ingest.maxconnections";

  // Solrj connection-associated objects
  protected PoolingHttpClientConnectionManager connectionManager = null;
  protected SolrClient solrServer = null;
  
  // Action URI pieces
  private final String postUpdateAction;
  private final String postRemoveAction;
  private final String postStatusAction;
  
  // Attribute names
  private final String allowAttributeName;
  private final String denyAttributeName;
  private final String idAttributeName;
  private final String originalSizeAttributeName;
  private final String modifiedDateAttributeName;
  private final String createdDateAttributeName;
  private final String indexedDateAttributeName;
  private final String fileNameAttributeName;
  private final String mimeTypeAttributeName;
  private final String contentAttributeName;
  
  // Whether we use extract/update handler or not
  private final boolean useExtractUpdateHandler;
  
  // Document max length
  private final Long maxDocumentLength;

  // Included and excluded mime types
  private final Set<String> includedMimeTypes;
  private final Set<String>excludedMimeTypes;
  
  // Commit-within flag
  private final String commitWithin;

  // Constants we need
  private static final String LITERAL = "literal.";
  private static final String NOTHING = "__NOTHING__";
  private static final String ID_METADATA = "lcf_metadata_id";
  private static final String COMMITWITHIN_METADATA = "commitWithin";
  
  /** How long to wait before retrying a failed ingestion */
  private static final long interruptionRetryTime = 60000L;

  /** Initialize the SolrCloud http poster.
  */
  public HttpPoster(String zookeeperHosts, String collection,
    int zkClientTimeout, int zkConnectTimeout,
    String updatePath, String removePath, String statusPath,
    String allowAttributeName, String denyAttributeName, String idAttributeName,
    String originalSizeAttributeName, String modifiedDateAttributeName, String createdDateAttributeName, String indexedDateAttributeName,
    String fileNameAttributeName, String mimeTypeAttributeName, String contentAttributeName,
    Long maxDocumentLength,
    String commitWithin, boolean useExtractUpdateHandler,
    final Set<String> includedMimeTypes, final Set<String> excludedMimeTypes,
    boolean allowCompression)
    throws ManifoldCFException
  {
    // These are the paths to the handlers in Solr that deal with the actions we need to do
    this.postUpdateAction = updatePath;
    this.postRemoveAction = removePath;
    this.postStatusAction = statusPath;
    
    this.commitWithin = commitWithin;
    
    this.allowAttributeName = allowAttributeName;
    this.denyAttributeName = denyAttributeName;
    this.idAttributeName = idAttributeName;
    this.originalSizeAttributeName = originalSizeAttributeName;
    this.modifiedDateAttributeName = modifiedDateAttributeName;
    this.createdDateAttributeName = createdDateAttributeName;
    this.indexedDateAttributeName = indexedDateAttributeName;
    this.fileNameAttributeName = fileNameAttributeName;
    this.mimeTypeAttributeName = mimeTypeAttributeName;
    this.contentAttributeName = contentAttributeName;
    this.useExtractUpdateHandler = useExtractUpdateHandler;
    this.includedMimeTypes = includedMimeTypes;
    this.excludedMimeTypes = excludedMimeTypes;
    
    this.maxDocumentLength = maxDocumentLength;
    
    try
    {
      CloudSolrClient cloudSolrServer = new CloudSolrClient.Builder()
        .withZkHost(zookeeperHosts)
        .withLBHttpSolrClient(new ModifiedLBHttpSolrClient(HttpClientUtil.createClient(null), allowCompression))
        .build();
      cloudSolrServer.setZkClientTimeout(zkClientTimeout);
      cloudSolrServer.setZkConnectTimeout(zkConnectTimeout);
      cloudSolrServer.setDefaultCollection(collection);
      // Set the solrj instance we want to use
      solrServer = cloudSolrServer;
    }
    catch (MalformedURLException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }

  /** Initialize the standard http poster.
  */
  public HttpPoster(String protocol, String server, int port, String webapp, String core,
    int connectionTimeout, int socketTimeout,
    String updatePath, String removePath, String statusPath,
    String realm, String userID, String password,
    String allowAttributeName, String denyAttributeName, String idAttributeName,
    String originalSizeAttributeName, String modifiedDateAttributeName, String createdDateAttributeName, String indexedDateAttributeName,
    String fileNameAttributeName, String mimeTypeAttributeName, String contentAttributeName,
    IKeystoreManager keystoreManager, Long maxDocumentLength,
    String commitWithin, boolean useExtractUpdateHandler,
    final Set<String> includedMimeTypes, final Set<String> excludedMimeTypes,
    boolean allowCompression)
    throws ManifoldCFException
  {
    // These are the paths to the handlers in Solr that deal with the actions we need to do
    this.postUpdateAction = updatePath;
    this.postRemoveAction = removePath;
    this.postStatusAction = statusPath;
    
    this.commitWithin = commitWithin;
    
    this.allowAttributeName = allowAttributeName;
    this.denyAttributeName = denyAttributeName;
    this.idAttributeName = idAttributeName;
    this.originalSizeAttributeName = originalSizeAttributeName;
    this.modifiedDateAttributeName = modifiedDateAttributeName;
    this.createdDateAttributeName = createdDateAttributeName;
    this.indexedDateAttributeName = indexedDateAttributeName;
    this.fileNameAttributeName = fileNameAttributeName;
    this.mimeTypeAttributeName = mimeTypeAttributeName;
    this.contentAttributeName = contentAttributeName;
    this.useExtractUpdateHandler = useExtractUpdateHandler;
    this.includedMimeTypes = includedMimeTypes;
    this.excludedMimeTypes = excludedMimeTypes;
    
    this.maxDocumentLength = maxDocumentLength;

    String location = "";
    if (webapp != null)
      location = "/" + webapp;
    if (core != null)
    {
      if (webapp == null)
        throw new ManifoldCFException("Webapp must be specified if core is specified.");
      location += "/" + core;
    }

    // Initialize standard solr-j.
    
    SSLConnectionSocketFactory myFactory;
    if (keystoreManager != null)
    {
      myFactory = new SSLConnectionSocketFactory(keystoreManager.getSecureSocketFactory(), NoopHostnameVerifier.INSTANCE);
    }
    else
    {
      // Use the "trust everything" one
      myFactory = new SSLConnectionSocketFactory(KeystoreManagerFactory.getTrustingSecureSocketFactory(),NoopHostnameVerifier.INSTANCE);
    }

    // First, we need an HttpClient where basic auth is properly set up.
    connectionManager = new PoolingHttpClientConnectionManager(RegistryBuilder.<ConnectionSocketFactory>create()
      .register("http", PlainConnectionSocketFactory.getSocketFactory())
      .register("https", myFactory)
      .build());
    connectionManager.setDefaultMaxPerRoute(1);
    connectionManager.setValidateAfterInactivity(2000);
    connectionManager.setDefaultSocketConfig(SocketConfig.custom()
      .setTcpNoDelay(true)
      .setSoTimeout(socketTimeout)
      .build());
    
    RequestConfig.Builder requestBuilder = RequestConfig.custom()
      .setCircularRedirectsAllowed(true)
      .setSocketTimeout(socketTimeout)
      .setExpectContinueEnabled(true)
      .setConnectTimeout(connectionTimeout)
      .setConnectionRequestTimeout(socketTimeout);

    HttpClientBuilder clientBuilder = HttpClients.custom()
      .setConnectionManager(connectionManager)
      .disableAutomaticRetries()
      .setDefaultRequestConfig(requestBuilder.build())
      .setRedirectStrategy(new LaxRedirectStrategy())
      .setRequestExecutor(new HttpRequestExecutor(socketTimeout));


    if (userID != null && userID.length() > 0 && password != null)
    {
      CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      Credentials credentials = new UsernamePasswordCredentials(userID, password);
      if (realm != null)
        credentialsProvider.setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, realm), credentials);
      else
        credentialsProvider.setCredentials(AuthScope.ANY, credentials);

      clientBuilder.setDefaultCredentialsProvider(credentialsProvider);
    }

    HttpClient localClient = clientBuilder.build();


    String httpSolrServerUrl = protocol + "://" + server + ":" + port + location;
    solrServer = new ModifiedHttpSolrClient(httpSolrServerUrl, localClient, new XMLResponseParser(), allowCompression);
  }

  /** Shut down the poster.
  */
  public void shutdown()
  {
    if (solrServer != null)
    {
      try
      {
        solrServer.close();
      }
      catch (IOException ioe)
      {
        // Eat this exception
      }
      solrServer = null;
    }
    if (connectionManager != null)
      connectionManager.shutdown();
    connectionManager = null;
  }
  
  /** Cause a commit to happen.
  */
  public void commitPost()
    throws ManifoldCFException, ServiceInterruption
  {
    if (Logging.ingest.isDebugEnabled())
      Logging.ingest.debug("commitPost()");

    // Open a socket to ingest, and to the response stream to get the post result
    try
    {
      CommitThread t = new CommitThread();
      try
      {
        t.start();
        t.finishUp();
        return;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),ManifoldCFException.INTERRUPTED);
      }
    }
    catch (SolrServerException e)
    {
      handleSolrServerException(e, "commit");
      return;
    }
    catch (SolrException e)
    {
      handleSolrException(e, "commit");
      return;
    }
    catch (RuntimeException e)
    {
      handleRuntimeException(e, "commit");
      return;
    }
    catch (IOException ioe)
    {
      handleIOException(ioe, "commit");
      return;
    }
  }
  
  /** Handle a RuntimeException.
  * Unfortunately, SolrCloud 4.6.x throws RuntimeExceptions whenever ZooKeeper is not happy.
  * We have to catch these too.  I've logged a ticket: SOLR-5678.
  */
  protected static void handleRuntimeException(RuntimeException e, String context)
    throws ManifoldCFException, ServiceInterruption
  {
    Throwable childException = e.getCause();
    if (childException != null && childException instanceof java.util.concurrent.TimeoutException)
    {
      Logging.ingest.warn("SolrJ runtime exception during "+context+": "+childException.getMessage(),childException);
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption(childException.getMessage(),childException,
        currentTime + interruptionRetryTime,
        currentTime + 2L * 60L * 60000L,
        -1,
        true);
    }
    throw e;
  }
  
  /** Handle a SolrServerException.
  * These exceptions seem to be catch-all exceptions having to do either with misconfiguration or
  * with underlying IO exceptions.
  * If this method doesn't throw an exception, it means that the exception should be interpreted
  * as meaning that the document or action is illegal and should not be repeated.
  */
  protected static void handleSolrServerException(SolrServerException e, String context)
    throws ManifoldCFException, ServiceInterruption
  {
    Throwable childException = e.getCause();
    if (childException instanceof IOException)
    {
      handleIOException((IOException)childException, context);
      return;
    }
    throw new ManifoldCFException("Unhandled SolrServerException: "+e.getMessage(),e);
  }

  /** Handle a SolrException.
  * These exceptions are mainly Http errors having to do with actual responses from Solr.
  * If this method doesn't throw an exception, it means that the exception should be interpreted
  * as meaning that the document or action is illegal and should not be repeated.
  */
  protected static void handleSolrException(SolrException e, String context)
    throws ManifoldCFException, ServiceInterruption
  {
    int code = e.code();
    if (code == 0)
    {
      try
      {
        // Solrj doesn't always set the code properly.  If it doesn't, we have to parse it out of the exception string.  Ugh.
        Pattern p = Pattern.compile("non ok status:([0-9]*),");
        Matcher m = p.matcher(e.getMessage());
        if (m.find())
          code = Integer.parseInt(m.group(1));
      }
      catch (PatternSyntaxException e2)
      {
        throw new ManifoldCFException("Unexpected error: "+e2.getMessage());
      }
      catch (NumberFormatException e2)
      {
        throw new ManifoldCFException("Unexpected error: "+e2.getMessage());
      }
    }
      
    // Use the exception text to determine the proper result.
    if (code == 500 && e.getMessage().indexOf("org.apache.tika.exception.TikaException") != -1)
      // Can't process the document, so don't keep trying.
      return;

    // If code is 401, we should abort the job because security credentials are incorrect
    if (code == 401)
    {
      String message = "Solr authorization failure, code "+code+": aborting job";
      Logging.ingest.error(message);
      throw new ManifoldCFException(message);
    }
    
    // If the code is in the 400 range, the document will never be accepted, so indicate that.
    if (code >= 400 && code < 500)
      return;
    
    // The only other kind of return code we know how to handle is 50x.
    // For these, we should retry for a while.
    if (code == 500)
    {
      long currentTime = System.currentTimeMillis();
      
      // Log the error
      String message = "Solr exception during "+context+" ("+e.code()+"): "+e.getMessage();
      Logging.ingest.warn(message,e);
      throw new ServiceInterruption(message,
        e,
        currentTime + interruptionRetryTime,
        currentTime + 2L * 60L * 60000L,
        -1,
        true);
    }
    
    // Unknown code: end the job.
    throw new ManifoldCFException("Unhandled Solr exception during "+context+" ("+e.code()+"): "+e.getMessage());
  }
  
  /** Handle an IOException.
  * I'm not actually sure where these exceptions come from in SolrJ, but we handle them
  * as real I/O errors, meaning they should be retried.
  */
  protected static void handleIOException(IOException e, String context)
    throws ManifoldCFException, ServiceInterruption
  {
    if ((e instanceof InterruptedIOException) && (!(e instanceof java.net.SocketTimeoutException)))
      throw new ManifoldCFException(e.getMessage(), ManifoldCFException.INTERRUPTED);

    long currentTime = System.currentTimeMillis();
    
    if (e instanceof java.net.ConnectException)
    {
      // Server isn't up at all.  Try for a brief time then give up.
      String message = "Server could not be contacted during "+context+": "+e.getMessage();
      Logging.ingest.warn(message,e);
      throw new ServiceInterruption(message,
        e,
        currentTime + interruptionRetryTime,
        -1L,
        3,
        true);
    }
    
    if (e instanceof java.net.SocketTimeoutException)
    {
      String message2 = "Socket timeout exception during "+context+": "+e.getMessage();
      Logging.ingest.warn(message2,e);
      throw new ServiceInterruption(message2,
        e,
        currentTime + interruptionRetryTime,
        currentTime + 20L * 60000L,
        -1,
        false);
    }
      
    if (e.getClass().getName().equals("java.net.SocketException"))
    {
      // In the past we would have treated this as a straight document rejection, and
      // treated it in the same manner as a 400.  The reasoning is that the server can
      // perfectly legally send out a 400 and drop the connection immediately thereafter,
      // this a race condition.
      // However, Solr 4.0 (or the Jetty version that the example runs on) seems
      // to have a bug where it drops the connection when two simultaneous documents come in
      // at the same time.  This is the final version of Solr 4.0 so we need to deal with
      // this.
      if (e.getMessage().toLowerCase(Locale.ROOT).indexOf("broken pipe") != -1 ||
        e.getMessage().toLowerCase(Locale.ROOT).indexOf("connection reset") != -1 ||
        e.getMessage().toLowerCase(Locale.ROOT).indexOf("target server failed to respond") != -1)
      {
        // Treat it as a service interruption, but with a limited number of retries.
        // In that way we won't burden the user with a huge retry interval; it should
        // give up fairly quickly, and yet NOT give up if the error was merely transient
        String message = "Server dropped connection during "+context+": "+e.getMessage();
        Logging.ingest.warn(message,e);
        throw new ServiceInterruption(message,
          e,
          currentTime + interruptionRetryTime,
          -1L,
          3,
          false);
      }
      
      // Other socket exceptions are service interruptions - but if we keep getting them, it means 
      // that a socket timeout is probably set too low to accept this particular document.  So
      // we retry for a while, then skip the document.
      String message2 = "Socket exception during "+context+": "+e.getMessage();
      Logging.ingest.warn(message2,e);
      throw new ServiceInterruption(message2,
        e,
        currentTime + interruptionRetryTime,
        currentTime + 20L * 60000L,
        -1,
        false);
    }

    // Otherwise, no idea what the trouble is, so presume that retries might fix it.
    String message3 = "IO exception during "+context+": "+e.getMessage();
    Logging.ingest.warn(message3,e);
    throw new ServiceInterruption(message3,
      e,
      currentTime + interruptionRetryTime,
      currentTime + 2L * 60L * 60000L,
      -1,
      true);
  }
  
  /**
  * Post the input stream to ingest
  *
   * @param documentURI is the document's uri.
   * @param document is the document structure to ingest.
   * @param arguments are the configuration arguments to pass in the post.  Key is argument name, value is a list of the argument values.
   * @param authorityNameString is the name of the governing authority for this document's acls, or null if none.
   * @param activities is the activities object, so we can report what's happening.   @return true if the ingestion was successful, or false if the ingestion is illegal.
  * @throws ManifoldCFException, ServiceInterruption
  */
  public boolean indexPost(String documentURI,
    RepositoryDocument document, Map<String,List<String>> arguments,
    String authorityNameString, IOutputAddActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    if (Logging.ingest.isDebugEnabled())
      Logging.ingest.debug("indexPost(): '" + documentURI + "'");

    // If the document is too long, reject it.
    if (maxDocumentLength != null && document.getBinaryLength() > maxDocumentLength.longValue()){
      activities.recordActivity(null,SolrConnector.INGEST_ACTIVITY,null,documentURI,activities.EXCLUDED_LENGTH,"Solr connector rejected document due to its big size: ('"+document.getBinaryLength()+"')");
      return false;
    }

    // If not the right mime type, reject it.
    if ((includedMimeTypes !=null || excludedMimeTypes != null) && !checkMimeTypeIndexable(document.getMimeType(), useExtractUpdateHandler, includedMimeTypes, excludedMimeTypes)) {
      activities.recordActivity(null,SolrConnector.INGEST_ACTIVITY,null,documentURI,activities.EXCLUDED_MIMETYPE,"Solr connector rejected document due to mime type restrictions: ("+document.getMimeType()+")");
      return false;
    }
    
    // Convert the incoming acls that we know about to qualified forms, and reject the document if
    // we don't know how to deal with its acls
    Map<String,String[]> aclsMap = new HashMap<String,String[]>();
    Map<String,String[]> denyAclsMap = new HashMap<String,String[]>();

    Iterator<String> aclTypes = document.securityTypesIterator();
    while (aclTypes.hasNext())
    {
      String aclType = aclTypes.next();
      aclsMap.put(aclType,convertACL(document.getSecurityACL(aclType),authorityNameString,activities));
      denyAclsMap.put(aclType,convertACL(document.getSecurityDenyACL(aclType),authorityNameString,activities));
      
      // Reject documents that have security we don't know how to deal with in the Solr plugin!!  Only safe thing to do.
      if (!aclType.equals(RepositoryDocument.SECURITY_TYPE_DOCUMENT) &&
        !aclType.equals(RepositoryDocument.SECURITY_TYPE_SHARE) &&
        !aclType.startsWith(RepositoryDocument.SECURITY_TYPE_PARENT)){
          activities.recordActivity(null,SolrConnector.INGEST_ACTIVITY,null,documentURI,activities.UNKNOWN_SECURITY,"Solr connector rejected document that has security info which Solr does not recognize: '"+aclType + "'");
          return false;
      }

    }

    try
    {
      IngestThread t = new IngestThread(documentURI,document,arguments,
                                        aclsMap,denyAclsMap);
      try
      {
        t.start();
        t.finishUp();

        if (t.getActivityCode() != null)
          activities.recordActivity(t.getActivityStart(),SolrConnector.INGEST_ACTIVITY,t.getActivityBytes(),documentURI,t.getActivityCode(),t.getActivityDetails());

        return t.getRval();
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),ManifoldCFException.INTERRUPTED);
      }
      catch (SolrServerException e)
      {
        if (t.getActivityCode() != null)
          activities.recordActivity(t.getActivityStart(),SolrConnector.INGEST_ACTIVITY,t.getActivityBytes(),documentURI,t.getActivityCode(),t.getActivityDetails());
        throw e;
      }
      catch (SolrException e)
      {
        if (t.getActivityCode() != null)
          activities.recordActivity(t.getActivityStart(),SolrConnector.INGEST_ACTIVITY,t.getActivityBytes(),documentURI,t.getActivityCode(),t.getActivityDetails());
        throw e;
      }
      catch (RuntimeException e)
      {
        if (t.getActivityCode() != null)
          activities.recordActivity(t.getActivityStart(),SolrConnector.INGEST_ACTIVITY,t.getActivityBytes(),documentURI,t.getActivityCode(),t.getActivityDetails());
        throw e;
      }
      catch (IOException e)
      {
        if (t.getActivityCode() != null)
          activities.recordActivity(t.getActivityStart(),SolrConnector.INGEST_ACTIVITY,t.getActivityBytes(),documentURI,t.getActivityCode(),t.getActivityDetails());
        throw e;
      }
    }
    catch (SolrServerException e)
    {
      handleSolrServerException(e, "indexing "+documentURI);
      return false;
    }
    catch (SolrException e)
    {
      handleSolrException(e, "indexing "+documentURI);
      return false;
    }
    catch (RuntimeException e)
    {
      handleRuntimeException(e, "indexing "+documentURI);
      return false;
    }
    catch (IOException ioe)
    {
      handleIOException(ioe, "indexing "+documentURI);
      return false;
    }

  }

  /** Post a check request.
  */
  public void checkPost()
    throws ManifoldCFException, ServiceInterruption
  {
    if (Logging.ingest.isDebugEnabled())
      Logging.ingest.debug("checkPost()");

    // Open a socket to ingest, and to the response stream to get the post result
    try
    {
      StatusThread t = new StatusThread();
      try
      {
        t.start();
        t.finishUp();
        return;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),ManifoldCFException.INTERRUPTED);
      }
    }
    catch (SolrServerException e)
    {
      handleSolrServerException(e, "check");
      return;
    }
    catch (SolrException e)
    {
      handleSolrException(e, "check");
      return;
    }
    catch (RuntimeException e)
    {
      handleRuntimeException(e, "check");
      return;
    }
    catch (IOException ioe)
    {
      handleIOException(ioe, "check");
      return;
    }

  }

  /** Post a delete request.
  *@param documentURI is the document's URI.
  */
  public void deletePost(String documentURI, IOutputRemoveActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    if (Logging.ingest.isDebugEnabled())
      Logging.ingest.debug("deletePost(): '" + documentURI + "'");

    try
    {
      DeleteThread t = new DeleteThread(documentURI);
      try
      {
        t.start();
        t.finishUp();
        
        if (t.getActivityCode() != null)
          activities.recordActivity(t.getActivityStart(),SolrConnector.REMOVE_ACTIVITY,null,documentURI,t.getActivityCode(),t.getActivityDetails());

        return;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),ManifoldCFException.INTERRUPTED);
      }
      catch (SolrServerException e)
      {
        if (t.getActivityCode() != null)
          activities.recordActivity(t.getActivityStart(),SolrConnector.REMOVE_ACTIVITY,null,documentURI,t.getActivityCode(),t.getActivityDetails());
        throw e;
      }
      catch (SolrException e)
      {
        if (t.getActivityCode() != null)
          activities.recordActivity(t.getActivityStart(),SolrConnector.REMOVE_ACTIVITY,null,documentURI,t.getActivityCode(),t.getActivityDetails());
        throw e;
      }
      catch (RuntimeException e)
      {
        if (t.getActivityCode() != null)
          activities.recordActivity(t.getActivityStart(),SolrConnector.REMOVE_ACTIVITY,null,documentURI,t.getActivityCode(),t.getActivityDetails());
        throw e;
      }
      catch (IOException e)
      {
        if (t.getActivityCode() != null)
          activities.recordActivity(t.getActivityStart(),SolrConnector.REMOVE_ACTIVITY,null,documentURI,t.getActivityCode(),t.getActivityDetails());
        throw e;
      }
    }
    catch (SolrServerException e)
    {
      handleSolrServerException(e, "delete");
      return;
    }
    catch (SolrException e)
    {
      handleSolrException(e, "delete");
      return;
    }
    catch (RuntimeException e)
    {
      handleRuntimeException(e, "delete");
      return;
    }
    catch (IOException ioe)
    {
      handleIOException(ioe, "delete");
      return;
    }

  }

  private final static Set<String> acceptableMimeTypes = new HashSet<String>();
  static
  {
    acceptableMimeTypes.add("text/plain;charset=utf-8");
    acceptableMimeTypes.add("text/plain;charset=ascii");
    acceptableMimeTypes.add("text/plain;charset=us-ascii");
    acceptableMimeTypes.add("text/plain; charset=utf-8");
    acceptableMimeTypes.add("text/plain; charset=ascii");
    acceptableMimeTypes.add("text/plain; charset=us-ascii");
    acceptableMimeTypes.add("text/plain");
  }

  public static boolean checkMimeTypeIndexable(final String mimeType, final boolean useExtractUpdateHandler,
    final Set<String> includedMimeTypes, final Set<String> excludedMimeTypes)
  {
    final String lowerMimeType = (mimeType==null)?null:mimeType.toLowerCase(Locale.ROOT);
    if (useExtractUpdateHandler)
    {
      if (includedMimeTypes != null && !includedMimeTypes.contains(lowerMimeType))
        return false;
      if (excludedMimeTypes != null && excludedMimeTypes.contains(lowerMimeType))
        return false;
      return true;
    }
    return acceptableMimeTypes.contains(lowerMimeType);
  }

  /** Convert an unqualified ACL to qualified form.
  * @param acl is the initial, unqualified ACL.
  * @param authorityNameString is the name of the governing authority for this document's acls, or null if none.
  * @param activities is the activities object, so we can report what's happening.
  * @return the modified ACL.
  */
  protected static String[] convertACL(String[] acl, String authorityNameString, IOutputAddActivity activities)
    throws ManifoldCFException
  {
    if (acl != null)
    {
      String[] rval = new String[acl.length];
      int i = 0;
      while (i < rval.length)
      {
        rval[i] = activities.qualifyAccessToken(authorityNameString,acl[i]);
        i++;
      }
      return rval;
    }
    return new String[0];
  }

  /** Write a field */
  protected static void writeField(ModifiableSolrParams out, String fieldName, String[] fieldValues)
  {
    out.add(fieldName, fieldValues);
  }
  
  /** Write a field */
  protected static void writeField(ModifiableSolrParams out, String fieldName, List<String> fieldValues)
  {
    String[] values = new String[fieldValues.size()];
    int i = 0;
    for (String fieldValue : fieldValues) {
      values[i++] = fieldValue;
    }
    writeField(out, fieldName, values);
  }
  
  /** Write a field */
  protected static void writeField(ModifiableSolrParams out, String fieldName, String fieldValue)
  {
    out.add(fieldName, fieldValue);
  }

  /** Output an acl level */
  protected void writeACLs(ModifiableSolrParams out, String aclType, String[] acl, String[] denyAcl)
  {
    String metadataACLName = LITERAL + allowAttributeName + aclType;
    for (int i = 0; i < acl.length; i++)
    {
      writeField(out,metadataACLName,acl[i]);
    }
    String metadataDenyACLName = LITERAL + denyAttributeName + aclType;
    for (int i = 0; i < denyAcl.length; i++)
    {
      writeField(out,metadataDenyACLName,denyAcl[i]);
    }
  }
  
  /**
    * Output an acl level in a SolrInputDocument
    */
  protected void writeACLsInSolrDoc( SolrInputDocument inputDoc, String aclType, String[] acl, String[] denyAcl )
  {
    String metadataACLName = allowAttributeName + aclType;
    inputDoc.addField( metadataACLName, acl );

    String metadataDenyACLName = denyAttributeName + aclType;
    inputDoc.addField( metadataDenyACLName, denyAcl );
  }

  /** Killable thread that does ingestions.
  * Java 1.5 stopped permitting thread interruptions to abort socket waits.  As a result, it is impossible to get threads to shutdown cleanly that are doing
  * such waits.  So, the places where this happens are segregated in their own threads so that they can be just abandoned.
  *
  * This thread does a single document ingestion.
  */
  protected class IngestThread extends java.lang.Thread
  {
    protected final String documentURI;
    protected final RepositoryDocument document;
    protected final Map<String,List<String>> arguments;
    protected final Map<String,String[]> aclsMap;
    protected final Map<String,String[]> denyAclsMap;
    
    protected Long activityStart = null;
    protected Long activityBytes = null;
    protected String activityCode = null;
    protected String activityDetails = null;
    protected Throwable exception = null;
    protected boolean readFromDocumentStreamYet = false;
    protected boolean rval = false;

    public IngestThread(String documentURI, RepositoryDocument document,
      Map<String, List<String>> arguments,
      Map<String,String[]> aclsMap, Map<String,String[]> denyAclsMap)
    {
      super();
      setDaemon(true);
      this.documentURI = documentURI;
      this.document = document;
      this.arguments = arguments;
      this.aclsMap = aclsMap;
      this.denyAclsMap = denyAclsMap;
    }

    public void run()
    {
      long length = document.getBinaryLength();
      InputStream is = document.getBinaryStream();
      String contentType = document.getMimeType();
      String contentName = document.getFileName();

      try
      {
        // Do the operation!
        long fullStartTime = System.currentTimeMillis();

        // Open a socket to ingest, and to the response stream to get the post result
        try
        {
          SolrInputDocument currentSolrDoc = new SolrInputDocument();
          ContentStreamUpdateRequest contentStreamUpdateRequest = new ContentStreamUpdateRequest(postUpdateAction);
          if ( useExtractUpdateHandler )
          {
            buildExtractUpdateHandlerRequest( length, is, contentType, (contentName==null || contentName.length()==0)?"docname":contentName,
              contentStreamUpdateRequest );
          }
          else
          {
            currentSolrDoc = buildSolrDocument( length, is );
          }

          // Fire off the request.
          // Note: I need to know whether the document has been permanently rejected or not, but we currently have
          // no means to determine that.  Analysis of SolrServerExceptions that have been thrown is likely needed.
          try
          {
            readFromDocumentStreamYet = true;
            UpdateResponse response;
            if ( useExtractUpdateHandler )
            {
              response = contentStreamUpdateRequest.process(solrServer);
            }
            else
            {
              final ModifiableSolrParams params = new ModifiableSolrParams();
              // Write the arguments
              for (final String name : arguments.keySet())
              {
                final List<String> values = arguments.get(name);
                writeField(params, name, values);
              }
              final UpdateRequest req = new UpdateRequest();
              req.setParams(params);
              req.add(currentSolrDoc);
              if (commitWithin != null) {
                req.setCommitWithin(Integer.parseInt(commitWithin));
              }
              response =  req.process(solrServer);
            }

            // Successful completion
            activityStart = new Long(fullStartTime);
            activityBytes = new Long(length);
            activityCode = "OK";
            activityDetails = null;

            rval = true;
            return;
          }
          catch (SolrServerException e)
          {
            // Log what happened to us
            activityStart = new Long(fullStartTime);
            activityBytes = new Long(length);
            activityDetails = e.getMessage() +
              ((e.getCause() != null)?": "+e.getCause().getMessage():"");
            
            // Broken pipe exceptions we log specially because they usually mean
            // Solr has rejected the document, and the user will want to know that.
            if (e.getCause() != null && e.getCause().getClass().getName().equals("java.net.SocketException") &&
              (activityDetails.toLowerCase(Locale.ROOT).indexOf("broken pipe") != -1 ||
                activityDetails.toLowerCase(Locale.ROOT).indexOf("connection reset") != -1 ||
                activityDetails.toLowerCase(Locale.ROOT).indexOf("target server failed to respond") != -1))
              activityCode = "SOLRREJECT";
            else
              activityCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);

            // Rethrow; will interpret at a higher level
            throw e;
          }
          catch (SolrException e)
          {
            // Log what happened to us
            activityStart = new Long(fullStartTime);
            activityBytes = new Long(length);
            activityCode = Integer.toString(e.code());
            activityDetails = e.getMessage() +
              ((e.getCause() != null)?": "+e.getCause().getMessage():"");
            
            // Rethrow; we'll interpret at the next level
            throw e;
          }
        }
        catch (IOException ioe)
        {
          if ((ioe instanceof InterruptedIOException) && (!(ioe instanceof java.net.SocketTimeoutException)))
            return;
          
          activityStart = new Long(fullStartTime);
          activityCode = ioe.getClass().getSimpleName().toUpperCase(Locale.ROOT);
          activityDetails = ioe.getMessage();

          // Log the error
          Logging.ingest.warn("Error indexing into Solr: "+ioe.getMessage(),ioe);

          throw ioe;
        }
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    private SolrInputDocument buildSolrDocument( long length, InputStream is )
      throws IOException
    {
      SolrInputDocument outputDoc = new SolrInputDocument();

      // Write the id field
      outputDoc.addField( idAttributeName, documentURI );
      
      if (contentAttributeName != null)
      {
        // Copy the content into a string.  This is a bad thing to do, but we have no choice given SolrJ architecture at this time.
        // We enforce a size limit upstream.
        Reader r = new InputStreamReader(is, Consts.UTF_8);
        StringBuilder sb = new StringBuilder((int)length);
        char[] buffer = new char[65536];
        while (true)
        {
          int amt = r.read(buffer,0,buffer.length);
          if (amt == -1)
            break;
          sb.append(buffer,0,amt);
        }
        outputDoc.addField( contentAttributeName, sb.toString() );
      }
      
      // Write the rest of the attributes
      if ( originalSizeAttributeName != null )
      {
        Long size = document.getOriginalSize();
        if ( size != null )
        {
          outputDoc.addField( originalSizeAttributeName, size.toString() );
        }
      }
      if ( modifiedDateAttributeName != null )
      {
        Date date = document.getModifiedDate();
        if ( date != null )
        {
          outputDoc.addField( modifiedDateAttributeName, DateParser.formatISO8601Date( date ) );
        }
      }
      if ( createdDateAttributeName != null )
      {
        Date date = document.getCreatedDate();
        if ( date != null )
        {
          outputDoc.addField( createdDateAttributeName, DateParser.formatISO8601Date( date ) );
        }

      }
      if ( indexedDateAttributeName != null )
      {
        Date date = document.getIndexingDate();
        if ( date != null )
        {
          outputDoc.addField( indexedDateAttributeName, DateParser.formatISO8601Date( date ) );
        }
      }
      if ( fileNameAttributeName != null )
      {
        String fileName = document.getFileName();
        if ( !StringUtils.isBlank(fileName) )
        {
          outputDoc.addField( fileNameAttributeName, fileName );
        }
      }
      if ( mimeTypeAttributeName != null )
      {
        String mimeType = document.getMimeType();
        if ( !StringUtils.isBlank(mimeType) )
        {
          outputDoc.addField( mimeTypeAttributeName, mimeType );
        }
      }

      Iterator<String> typeIterator = aclsMap.keySet().iterator();
      while (typeIterator.hasNext())
      {
        String aclType = typeIterator.next();
        writeACLsInSolrDoc(outputDoc,aclType,aclsMap.get(aclType),denyAclsMap.get(aclType));
      }

      // Write the metadata, each in a field by itself
      buildSolrParamsFromMetadata( outputDoc );

      return outputDoc;
    }

    private void buildExtractUpdateHandlerRequest( long length, InputStream is, String contentType,
      String contentName,
      ContentStreamUpdateRequest contentStreamUpdateRequest )
      throws IOException
    {
      ModifiableSolrParams out = new ModifiableSolrParams();
      Logging.ingest.debug("Solr: Writing document '"+documentURI);
      
      // Write the id field
      writeField(out,LITERAL+idAttributeName,documentURI);
      // Write the rest of the attributes
      if (originalSizeAttributeName != null)
      {
        Long size = document.getOriginalSize();
        if (size != null)
          // Write value
          writeField(out,LITERAL+originalSizeAttributeName,size.toString());
      }
      if (modifiedDateAttributeName != null)
      {
        Date date = document.getModifiedDate();
        if (date != null)
          // Write value
          writeField(out,LITERAL+modifiedDateAttributeName,DateParser.formatISO8601Date(date));
      }
      if (createdDateAttributeName != null)
      {
        Date date = document.getCreatedDate();
        if (date != null)
          // Write value
          writeField(out,LITERAL+createdDateAttributeName,DateParser.formatISO8601Date(date));
      }
      if (indexedDateAttributeName != null)
      {
        Date date = document.getIndexingDate();
        if (date != null)
          // Write value
          writeField(out,LITERAL+indexedDateAttributeName,DateParser.formatISO8601Date(date));
      }
      if (fileNameAttributeName != null)
      {
        String fileName = document.getFileName();
        if (!StringUtils.isBlank(fileName))
          writeField(out,LITERAL+fileNameAttributeName,fileName);
      }
      if (mimeTypeAttributeName != null)
      {
        String mimeType = document.getMimeType();
        if (!StringUtils.isBlank(mimeType))
          writeField(out,LITERAL+mimeTypeAttributeName,mimeType);
      }
          
      // Write the access token information
      // Both maps have the same keys.
      Iterator<String> typeIterator = aclsMap.keySet().iterator();
      while (typeIterator.hasNext())
      {
        String aclType = typeIterator.next();
        writeACLs(out,aclType,aclsMap.get(aclType),denyAclsMap.get(aclType));
      }

      // Write the arguments
      for (String name : arguments.keySet())
      {
        List<String> values = arguments.get(name);
        writeField(out,name,values);
      }

      // Write the metadata, each in a field by itself
      buildSolrParamsFromMetadata(out);
             
      // These are unnecessary now in the case of non-solrcloud setups, because we overrode the SolrJ posting method to use multipart.
      //writeField(out,LITERAL+"stream_size",String.valueOf(length));
      //writeField(out,LITERAL+"stream_name",document.getFileName());
          
      // General hint for Tika
      if (!StringUtils.isBlank(document.getFileName()))
        writeField(out,"resource.name",document.getFileName());
          
      // Write the commitWithin parameter
      if (commitWithin != null)
        writeField(out,COMMITWITHIN_METADATA,commitWithin);

      contentStreamUpdateRequest.setParams(out);
          
      contentStreamUpdateRequest.addContentStream(new RepositoryDocumentStream(is,length,contentType,contentName));
      
      Logging.ingest.debug("Solr: Done writing '"+documentURI+"'");
    }

    /**
      * builds the solr parameter maps for the update request.
      * For each mapping expressed is applied the renaming for the metadata field name.
      * If we set to keep all the metadata, the metadata non present in the mapping will be kept with their original names.
      * In the other case ignored
      * @param out
      * @throws IOException
      */
    private void buildSolrParamsFromMetadata(ModifiableSolrParams out) throws IOException
    {
      Iterator<String> iter = document.getFields();
      while (iter.hasNext())
      {
        String originalFieldName = iter.next();
        String fieldName = makeSafeLuceneField(originalFieldName);
        Logging.ingest.debug("Solr: Saw field '"+originalFieldName+"'; converted to '"+fieldName+"'");
        applySingleMapping(originalFieldName, out, fieldName);
      }
    }

    private void buildSolrParamsFromMetadata(SolrInputDocument outputDocument) throws IOException
    {
      Iterator<String> iter = document.getFields();
      while (iter.hasNext())
      {
        String originalFieldName = iter.next();
        String fieldName = makeSafeLuceneField(originalFieldName);
        applySingleMapping(originalFieldName, outputDocument, fieldName);
      }
    }

    private void applySingleMapping(String originalFieldName, ModifiableSolrParams out, String newFieldName) throws IOException {
      if(newFieldName != null && !newFieldName.isEmpty()) {
        if (newFieldName.toLowerCase(Locale.ROOT).equals(idAttributeName.toLowerCase(Locale.ROOT))) {
          newFieldName = ID_METADATA;
        }
        String[] values = document.getFieldAsStrings(originalFieldName);
        writeField(out,LITERAL+newFieldName,values);
      }
    }

    private void applySingleMapping(String originalFieldName, SolrInputDocument outputDocument, String newFieldName) throws IOException {
      if(newFieldName != null && !newFieldName.isEmpty()) {
        if (newFieldName.toLowerCase(Locale.ROOT).equals(idAttributeName.toLowerCase(Locale.ROOT))) {
          newFieldName = ID_METADATA;
        }
        String[] values = document.getFieldAsStrings(originalFieldName);
        outputDocument.addField( newFieldName, values );
      }
    }

    public void finishUp()
      throws InterruptedException, SolrServerException, IOException
    {
      join();

      Throwable thr = exception;
      if (thr != null)
      {
        if (thr instanceof SolrServerException)
          throw (SolrServerException)thr;
        if (thr instanceof IOException)
          throw (IOException)thr;
        if (thr instanceof RuntimeException)
          throw (RuntimeException)thr;
        if (thr instanceof Error)
          throw (Error)thr;
        else
          throw new RuntimeException("Unexpected exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
    }

    public Long getActivityStart()
    {
      return activityStart;
    }

    public Long getActivityBytes()
    {
      return activityBytes;
    }

    public String getActivityCode()
    {
      return activityCode;
    }

    public String getActivityDetails()
    {
      return activityDetails;
    }

    public boolean getReadFromDocumentStreamYet()
    {
      return readFromDocumentStreamYet;
    }

    public boolean getRval()
    {
      return rval;
    }
  }

  /** Killable thread that does deletions.
  * Java 1.5 stopped permitting thread interruptions to abort socket waits.  As a result, it is impossible to get threads to shutdown cleanly that are doing
  * such waits.  So, the places where this happens are segregated in their own threads so that they can be just abandoned.
  *
  * This thread does a single document deletion.
  */
  protected class DeleteThread extends java.lang.Thread
  {
    protected String documentURI;

    protected Long activityStart = null;
    protected String activityCode = null;
    protected String activityDetails = null;
    protected Throwable exception = null;

    public DeleteThread(String documentURI)
    {
      super();
      setDaemon(true);
      this.documentURI = documentURI;
    }

    public void run()
    {
      try
      {
        // Do the operation!
        long fullStartTime = System.currentTimeMillis();
        // Open a socket to ingest, and to the response stream to get the post result
        try
        {
          UpdateResponse response = new UpdateRequest(postRemoveAction).deleteById(documentURI).process(solrServer);
            
          // Success
          activityStart = new Long(fullStartTime);
          activityCode = "OK";
          activityDetails = null;
          return;
        }
        catch (InterruptedIOException ioe)
        {
          return;
        }
        catch (SolrServerException e)
        {
          activityStart = new Long(fullStartTime);
          activityCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
          activityDetails = e.getMessage() +
            ((e.getCause() != null)?": "+e.getCause().getMessage():"");

          throw e;
        }
        catch (SolrException e)
        {
          activityStart = new Long(fullStartTime);
          activityCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
          activityDetails = e.getMessage() +
            ((e.getCause() != null)?": "+e.getCause().getMessage():"");

          throw e;
        }
        catch (IOException ioe)
        {
          // Log the error
          Logging.ingest.warn("Error deleting document: "+ioe.getMessage(),ioe);

          activityStart = new Long(fullStartTime);
          activityCode = ioe.getClass().getSimpleName().toUpperCase(Locale.ROOT);
          activityDetails = ioe.getMessage();

          throw ioe;
        }
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public void finishUp()
      throws InterruptedException, SolrServerException, IOException
    {
      join();

      Throwable thr = exception;
      if (thr != null)
      {
        if (thr instanceof SolrServerException)
          throw (SolrServerException)thr;
        if (thr instanceof IOException)
          throw (IOException)thr;
        if (thr instanceof RuntimeException)
          throw (RuntimeException)thr;
        if (thr instanceof Error)
          throw (Error)thr;
        else
          throw new RuntimeException("Unexpected exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
    }
    public Long getActivityStart()
    {
      return activityStart;
    }

    public String getActivityCode()
    {
      return activityCode;
    }

    public String getActivityDetails()
    {
      return activityDetails;
    }
  }
  
  /** Killable thread that does a commit.
  * Java 1.5 stopped permitting thread interruptions to abort socket waits.  As a result, it is impossible to get threads to shutdown cleanly that are doing
  * such waits.  So, the places where this happens are segregated in their own threads so that they can be just abandoned.
  *
  * This thread does a commit.
  */
  protected class CommitThread extends java.lang.Thread
  {
    protected Throwable exception = null;

    public CommitThread()
    {
      super();
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        try
        {
          // Do the operation!
          UpdateRequest updateRequest = new UpdateRequest(postUpdateAction + "?commit=true");
          UpdateResponse response = updateRequest.process(solrServer);
          //UpdateResponse response = solrServer.commit();
        }
        catch (InterruptedIOException ioe)
        {
          return;
        }
        catch (IOException ioe)
        {
          // Log the error
          Logging.ingest.warn("Error committing: "+ioe.getMessage(),ioe);
          throw ioe;
        }
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public void finishUp()
      throws InterruptedException, SolrServerException, IOException
    {
      join();

      Throwable thr = exception;
      if (thr != null)
      {
        if (thr instanceof SolrServerException)
          throw (SolrServerException)thr;
        if (thr instanceof IOException)
          throw (IOException)thr;
        if (thr instanceof RuntimeException)
          throw (RuntimeException)thr;
        if (thr instanceof Error)
          throw (Error)thr;
        else
          throw new RuntimeException("Unexpected exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
    }

  }


  /** Killable thread that does a status check.
  * Java 1.5 stopped permitting thread interruptions to abort socket waits.  As a result, it is impossible to get threads to shutdown cleanly that are doing
  * such waits.  So, the places where this happens are segregated in their own threads so that they can be just abandoned.
  *
  * This thread does a status check.
  */
  protected class StatusThread extends java.lang.Thread
  {
    protected Throwable exception = null;

    public StatusThread()
    {
      super();
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        // Do the operation!
        try
        {
          SolrResponse response = new SolrPing(postStatusAction).process(solrServer);
        }
        catch (InterruptedIOException ioe)
        {
          // Exit the thread.
          return;
        }
        catch (IOException ioe)
        {
          // Log the error
          Logging.ingest.warn("Error checking status: "+ioe.getMessage(),ioe);
          throw ioe;
        }
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public void finishUp()
      throws InterruptedException, SolrServerException, IOException
    {
      join();

      Throwable thr = exception;
      if (thr != null)
      {
        if (thr instanceof SolrServerException)
          throw (SolrServerException)thr;
        if (thr instanceof IOException)
          throw (IOException)thr;
        if (thr instanceof RuntimeException)
          throw (RuntimeException)thr;
        if (thr instanceof Error)
          throw (Error)thr;
        else
          throw new RuntimeException("Unexpected exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
    }

  }

  /** Class for importing documents into Solr via SolrJ
  */
  protected static class RepositoryDocumentStream extends ContentStreamBase
  {
    protected final InputStream is;
    protected final long length;
    protected final String contentType;
    protected final String contentName;
    
    public RepositoryDocumentStream(InputStream is, long length, String contentType, String contentName)
    {
      this.is = is;
      this.length = length;
      this.contentType = contentType;
      this.contentName = contentName;
    }
    
    @Override
    public Long getSize()
    {
      return new Long(length);
    }
    
    @Override
    public InputStream getStream() throws IOException
    {
      return is;
    }
    
    @Override
    public Reader getReader() throws IOException
    {
      return null;
    }

    @Override
    public String getContentType()
    {
      return contentType;
    }

    @Override
    public String getName()
    {
      return contentName;
    }
  }

  /** Special version of ping class where we can control the URL
  */
  protected static class SolrPing extends SolrRequest
  {
    /** Request parameters. */
    private ModifiableSolrParams params;
    
    /**
     * Create a new SolrPing object.
     */
    public SolrPing() {
      super(METHOD.GET, "/admin/ping");
      params = new ModifiableSolrParams();
    }
    
    public SolrPing(String url)
    {
      super( METHOD.GET, url );
      params = new ModifiableSolrParams();
    }

    @Override
    public Collection<ContentStream> getContentStreams() {
      return null;
    }

    @Override
    protected SolrPingResponse createResponse(SolrClient client) {
      return new SolrPingResponse();
    }

    @Override
    public ModifiableSolrParams getParams() {
      return params;
    }
    
    /**
     * Remove the action parameter from this request. This will result in the same
     * behavior as {@code SolrPing#setActionPing()}. For Solr server version 4.0
     * and later.
     * 
     * @return this
     */
    public SolrPing removeAction() {
      params.remove(CommonParams.ACTION);
      return this;
    }
    
    /**
     * Set the action parameter on this request to enable. This will delete the
     * health-check file for the Solr core. For Solr server version 4.0 and later.
     * 
     * @return this
     */
    public SolrPing setActionDisable() {
      params.set(CommonParams.ACTION, CommonParams.DISABLE);
      return this;
    }
    
    /**
     * Set the action parameter on this request to enable. This will create the
     * health-check file for the Solr core. For Solr server version 4.0 and later.
     * 
     * @return this
     */
    public SolrPing setActionEnable() {
      params.set(CommonParams.ACTION, CommonParams.ENABLE);
      return this;
    }
    
    /**
     * Set the action parameter on this request to ping. This is the same as not
     * including the action at all. For Solr server version 4.0 and later.
     * 
     * @return this
     */
    public SolrPing setActionPing() {
      params.set(CommonParams.ACTION, CommonParams.PING);
      return this;
    }

  }

  /** See CONNECTORS-956.  Make a safe lucene field name from a possibly
  * unsafe input field name from a repository connector.
  */
  protected static String makeSafeLuceneField(String inputField)
  {
    StringBuilder sb = new StringBuilder();
    boolean isFirst = true;
    for (int i = 0; i < inputField.length(); i++)
    {
      char x = inputField.charAt(i);
      if (isFirst && !Character.isJavaIdentifierStart(x) || !isFirst && !Character.isJavaIdentifierPart(x))
      {
        // Check for exceptions for Lucene
        if (!isFirst && (x == '.' || x == '-'))
          sb.append(x);
        else
          sb.append('_');
      }
      else
      {
        // Check for exceptions for Lucene
        if (isFirst && x == '$')
          sb.append('_');
        else
          sb.append(x);
      }
      isFirst = false;
    }
    return sb.toString();
  }
  
}

