/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.manifoldcf.crawler.connectors.amazons3;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.amazons3.S3Artifact;
import org.apache.manifoldcf.amazons3.XThreadBuffer;
import org.apache.manifoldcf.core.connector.BaseConnector;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPasswordMapperActivity;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector;
import org.apache.manifoldcf.crawler.interfaces.IExistingVersions;
import org.apache.manifoldcf.crawler.interfaces.IProcessActivity;
import org.apache.manifoldcf.crawler.interfaces.ISeedingActivity;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.CanonicalGrantee;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.Grant;
import com.amazonaws.services.s3.model.Grantee;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.Owner;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

/**
 * @author Kuhajeyan
 *
 */
public class AmazonS3Connector extends BaseRepositoryConnector {

  private static final String BUCKET_SPLITTER = ",";

  private static final String TAB_NAME = "TabName";

  private static final String SELECTED_NUM = "SelectedNum";

  private static final String SEQ_NUM = "SeqNum";

  protected final static String ACTIVITY_READ = "read document";

  protected long lastSessionFetch = -1L;

  protected static final long timeToRelease = 300000L;

  protected AmazonS3 amazonS3;

  protected boolean connected = false;

  protected String amazons3ProxyHost = null;

  protected String amazons3ProxyPort = null;

  protected String amazons3ProxyDomain = null;

  protected String amazons3ProxyUserName = null;

  protected String amazons3ProxyPassword = null;

  protected String amazons3AwsAccessKey = null;

  protected String amazons3AwsSecretKey = null;

  private static final String STD_SEPARATOR_BUCKET_AND_KEY = BUCKET_SPLITTER;

  private String[] buckets;

  private DocumentProcess documentProcess;

  public AmazonS3Connector() {
    super();
    documentProcess = new GenericDocumentProcess();
  }

  @Override
  public String[] getActivitiesList() {
    return new String[] { ACTIVITY_READ };
  }

  @Override
  public String[] getBinNames(String documentIdentifier) {
    return new String[] { amazons3AwsAccessKey };
  }

  /**
   * Close the connection. Call this before discarding the connection.
   */
  @Override
  public void disconnect() throws ManifoldCFException {
    amazons3AwsAccessKey = null;
    amazons3AwsSecretKey = null;

    amazons3ProxyHost = null;
    amazons3ProxyPort = null;
    amazons3ProxyDomain = null;
    amazons3ProxyUserName = null;
    amazons3ProxyPassword = null;
  }

  /**
   * Connect method initializes the configparams
   * */
  @Override
  public void connect(ConfigParams configParams) {
    super.connect(configParams);

    // aws access and secret keys
    amazons3AwsAccessKey = configParams
        .getParameter(AmazonS3Config.AWS_ACCESS_KEY);
    amazons3AwsSecretKey = configParams
        .getObfuscatedParameter(AmazonS3Config.AWS_SECRET_KEY);

    // proxy values
    amazons3ProxyHost = configParams
        .getParameter(AmazonS3Config.AMAZONS3_PROXY_HOST);
    amazons3ProxyPort = configParams
        .getParameter(AmazonS3Config.AMAZONS3_PROXY_PORT);
    amazons3ProxyDomain = configParams
        .getParameter(AmazonS3Config.AMAZONS3_PROXY_DOMAIN);
    amazons3ProxyUserName = configParams
        .getParameter(AmazonS3Config.AMAZONS3_PROXY_USERNAME);
    amazons3ProxyPassword = configParams
        .getObfuscatedParameter(AmazonS3Config.AMAZONS3_PROXY_PASSWORD);
  }

  /**
   * Get the Amazons3 client, relevant access keys should have been posted
   * already
   * @return
 * @throws ManifoldCFException 
   */
  protected AmazonS3 getClient() throws ManifoldCFException {
    if (amazonS3 == null) {
      try {
        BasicAWSCredentials awsCreds = new BasicAWSCredentials(
            amazons3AwsAccessKey, amazons3AwsSecretKey);
        amazonS3 = new AmazonS3Client(awsCreds);
      }
      catch (Exception e) {
        Logging.connectors
            .error("Error while amazon s3 connectionr", e);
        throw new ManifoldCFException(
                "Amazon client can not connect at the moment",e.getCause());
      }
    }
    lastSessionFetch = System.currentTimeMillis();
    return amazonS3;
  }

  /**
   * 
   */
  @Override
  public String check() throws ManifoldCFException {
    // connect with amazons3 client
    Logging.connectors.info("Checking connection");

    try {
      // invokes the check thread
      CheckThread checkThread = new CheckThread(getClient());
      checkThread.start();
      checkThread.join();// should wait for join
      if (checkThread.getException() != null) {
        Throwable thr = checkThread.getException();
        return "Check exception: " + thr.getMessage();
      }
      return checkThread.getResult();
    }
    catch (InterruptedException ex) {
      Logging.connectors.error("Error while checking connection", ex);
      throw new ManifoldCFException(ex.getMessage(), ex,
          ManifoldCFException.INTERRUPTED);
    }
  }

  @Override
  public boolean isConnected() {
    return amazonS3 != null && amazonS3.getS3AccountOwner() != null;
  }

  @Override
  public void poll() throws ManifoldCFException {
    if (lastSessionFetch == -1L) {
      return;
    }

    long currentTime = System.currentTimeMillis();
    if (currentTime >= lastSessionFetch + timeToRelease) {
      amazonS3 = null;
      lastSessionFetch = -1L;
    }
  }

  @Override
  public int getMaxDocumentRequest() {
    return 1;
  }

  /**
   * Return the list of relationship types that this connector recognizes.
   *
   * @return the list.
   */
  @Override
  public String[] getRelationshipTypes() {
    return new String[] { AmazonS3Config.RELATIONSHIP_RELATED };
  }

  private void fillInServerConfigurationMap(Map<String, Object> newMap,
      IPasswordMapperActivity mapper, ConfigParams parameters) {

    String amazons3AccessKey = parameters
        .getParameter(AmazonS3Config.AWS_ACCESS_KEY);
    String amazons3SecretKey = parameters
        .getObfuscatedParameter(AmazonS3Config.AWS_SECRET_KEY);

    // default values
    if (amazons3AccessKey == null)
      amazons3AccessKey = AmazonS3Config.AMAZONS3_AWS_ACCESS_KEY_DEFAULT;
    if (amazons3SecretKey == null)
      amazons3SecretKey = AmazonS3Config.AMAZONS3_AWS_SECRET_KEY_DEFAULT;
    else
      amazons3SecretKey = mapper.mapPasswordToKey(amazons3SecretKey);

    // fill the map
    newMap.put("AMAZONS3_AWS_ACCESS_KEY", amazons3AccessKey);
    newMap.put("AMAZONS3_AWS_SECRET_KEY", amazons3SecretKey);
  }

  private void fillInProxyConfigurationMap(Map<String, Object> newMap,
      IPasswordMapperActivity mapper, ConfigParams parameters) {
    String amazons3ProxyHost = parameters
        .getParameter(AmazonS3Config.AMAZONS3_PROXY_HOST);
    String amazons3ProxyPort = parameters
        .getParameter(AmazonS3Config.AMAZONS3_PROXY_PORT);
    String amazons3ProxyDomain = parameters
        .getParameter(AmazonS3Config.AMAZONS3_PROXY_DOMAIN);
    String amazons3ProxyUserName = parameters
        .getParameter(AmazonS3Config.AMAZONS3_PROXY_USERNAME);
    String amazons3ProxyPassword = parameters
        .getObfuscatedParameter(AmazonS3Config.AMAZONS3_PROXY_PASSWORD);

    if (amazons3ProxyHost == null)
      amazons3ProxyHost = AmazonS3Config.AMAZONS3_PROXY_HOST_DEFAULT;
    if (amazons3ProxyPort == null)
      amazons3ProxyPort = AmazonS3Config.AMAZONS3_PROXY_PORT_DEFAULT;
    if (amazons3ProxyDomain == null)
      amazons3ProxyDomain = AmazonS3Config.AMAZONS3_PROXY_DOMAIN_DEFAULT;
    if (amazons3ProxyUserName == null)
      amazons3ProxyUserName = AmazonS3Config.AMAZONS3_PROXY_USERNAME_DEFAULT;
    if (amazons3ProxyPassword == null)
      amazons3ProxyPassword = AmazonS3Config.AMAZONS3_PROXY_PASSWORD_DEFAULT;
    else
      amazons3ProxyPassword = mapper
          .mapPasswordToKey(amazons3ProxyPassword);

    // fill the map
    newMap.put("AMAZONS3_PROXY_HOST", amazons3ProxyHost);
    newMap.put("AMAZONS3_PROXY_PORT", amazons3ProxyPort);
    newMap.put("AMAZONS3_PROXY_DOMAIN", amazons3ProxyDomain);
    newMap.put("AMAZONS3_PROXY_USERNAME", amazons3ProxyUserName);
    newMap.put("AMAZONS3_PROXY_PWD", amazons3ProxyPassword);
  }

  @Override
  public void viewConfiguration(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters)
      throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();

    // Fill in map from each tab
    fillInServerConfigurationMap(paramMap, out, parameters);
    fillInProxyConfigurationMap(paramMap, out, parameters);

    Messages.outputResourceWithVelocity(out, locale,
        AmazonS3Config.VIEW_CONFIG_FORWARD, paramMap);
  }

  @Override
  public void outputConfigurationHeader(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters,
      List<String> tabsArray) throws ManifoldCFException, IOException {
    // Add the Server tab
    tabsArray.add(Messages.getString(locale,
        AmazonS3Config.AMAZONS3_SERVER_TAB_PROPERTY));
    // Add the Proxy tab
    tabsArray.add(Messages.getString(locale,
        AmazonS3Config.AMAZONS3_PROXY_TAB_PROPERTY));
    // Map the parameters
    Map<String, Object> paramMap = new HashMap<String, Object>();

    // Fill in the parameters from each tab
    fillInServerConfigurationMap(paramMap, out, parameters);
    fillInProxyConfigurationMap(paramMap, out, parameters);

    // Output the Javascript - only one Velocity template for all tabs
    Messages.outputResourceWithVelocity(out, locale,
        AmazonS3Config.EDIT_CONFIG_HEADER_FORWARD, paramMap);
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters,
      String tabName) throws ManifoldCFException, IOException {
    // Call the Velocity templates for each tab
    Map<String, Object> paramMap = new HashMap<String, Object>();
    // Set the tab name
    paramMap.put(TAB_NAME, tabName);

    // Fill in the parameters
    fillInServerConfigurationMap(paramMap, out, parameters);
    fillInProxyConfigurationMap(paramMap, out, parameters);

    // Server tab
    Messages.outputResourceWithVelocity(out, locale,
        AmazonS3Config.EDIT_CONFIG_FORWARD_SERVER, paramMap);
    // Proxy tab
    Messages.outputResourceWithVelocity(out, locale,
        AmazonS3Config.EDIT_CONFIG_FORWARD_PROXY, paramMap);
  }

  private static void fillInBucketsSpecificationMap(
      Map<String, Object> newMap, Specification ds) {
    String s3Buckets = AmazonS3Config.AMAZONS3_BUCKETS_DEFAULT;
    newMap.put("AMAZONS3BUCKETS", s3Buckets);
    s3Buckets = getExcludedBuckets(ds);
    if (s3Buckets != null && !StringUtils.isEmpty(s3Buckets)) {
      String[] buckets = s3Buckets.split(BUCKET_SPLITTER);

      newMap.put("AMAZONS3BUCKETS", s3Buckets);

      Logging.connectors.info("resolved s3 bucket values : " + s3Buckets);
    }
    else {
      Logging.connectors.info("No exclusion buckets available");
    }
  }

  private static String getExcludedBuckets(Specification ds) {
    String buckets = null;
    for (int i = 0; i < ds.getChildCount(); i++) {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals(AmazonS3Config.JOB_STARTPOINT_NODE_TYPE)) {
        buckets = sn
            .getAttributeValue(AmazonS3Config.JOB_BUCKETS_ATTRIBUTE);
      }
    }
    return buckets;
  }

  @Override
  public String processConfigurationPost(IThreadContext threadContext,
      IPostParameters variableContext, Locale locale,
      ConfigParams parameters) throws ManifoldCFException {
    // server tab
    String awsAccessKey = variableContext.getParameter("aws_access_key");
    if (awsAccessKey != null) {
      parameters
          .setParameter(AmazonS3Config.AWS_ACCESS_KEY, awsAccessKey);
    }
    String awsSecretKey = variableContext.getParameter("aws_secret_key");
    if (awsSecretKey != null) {
      // set as obfuscated parameter
      parameters.setObfuscatedParameter(AmazonS3Config.AWS_SECRET_KEY,
          variableContext.mapKeyToPassword(awsSecretKey));
    }

    // proxy tab
    String amazons3ProxyHost = variableContext
        .getParameter("amazons3_proxy_host");
    if (amazons3ProxyHost != null) {
      parameters.setParameter(AmazonS3Config.AMAZONS3_PROXY_HOST,
          amazons3ProxyHost);
    }
    String amazons3ProxyPort = variableContext
        .getParameter("amazons3_proxy_port");
    if (amazons3ProxyPort != null) {
      parameters.setParameter(AmazonS3Config.AMAZONS3_PROXY_PORT,
          amazons3ProxyPort);
    }
    String amazons3ProxyDomain = variableContext
        .getParameter("amazons3_proxy_domain");
    if (amazons3ProxyDomain != null) {
      parameters.setParameter(AmazonS3Config.AMAZONS3_PROXY_DOMAIN,
          amazons3ProxyDomain);
    }
    String amazons3ProxyUserName = variableContext
        .getParameter("amazons3_proxy_username");
    if (amazons3ProxyUserName != null) {
      parameters.setParameter(AmazonS3Config.AMAZONS3_PROXY_USERNAME,
          amazons3ProxyUserName);
    }
    String amazons3ProxyPassword = variableContext
        .getParameter("amazons3_proxy_pwd");
    if (amazons3ProxyPassword != null) {
      // set as obfuscated parameter
      parameters.setObfuscatedParameter(
          AmazonS3Config.AMAZONS3_PROXY_PASSWORD,
          variableContext.mapKeyToPassword(amazons3ProxyPassword));
    }

    return null;
  }

  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale,
      Specification ds, int connectionSequenceNumber)
      throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put(SEQ_NUM, Integer.toString(connectionSequenceNumber));
    fillInBucketsSpecificationMap(paramMap, ds);
    Messages.outputResourceWithVelocity(out, locale,
        AmazonS3Config.VIEW_SPEC_FORWARD, paramMap);
  }

  /**
   * Process a specification post. This method is called at the start of job's
   * edit or view page, whenever there is a possibility that form data for a
   * connection has been posted. Its purpose is to gather form information and
   * modify the document specification accordingly. The name of the posted
   * form is always "editjob". The connector will be connected before this
   * method can be called.
   *
   * @param variableContext contains the post data, including binary
   * file-upload information.
   * @param locale is the locale the output is preferred to be in.
   * @param ds is the current document specification for this job.
   * @param connectionSequenceNumber is the unique number of this connection
   * within the job.
   * @return null if all is well, or a string error message if there is an
   * error that should prevent saving of the job (and cause a redirection to
   * an error page).
   */
  @Override
  public String processSpecificationPost(IPostParameters variableContext,
      Locale locale, Specification ds, int connectionSequenceNumber)
      throws ManifoldCFException {
    String seqPrefix = "s" + connectionSequenceNumber + "_";
    String s3Buckets = variableContext.getParameter(seqPrefix
        + AmazonS3Config.JOB_BUCKETS_ATTRIBUTE);
    // strip off buckets
    if (StringUtils.isNotEmpty(s3Buckets)) {
      s3Buckets = s3Buckets.replaceAll("\\s+", "");
      buckets = s3Buckets.split(BUCKET_SPLITTER);

      if (buckets != null) {
        int i = 0;
        while (i < ds.getChildCount()) {
          SpecificationNode oldNode = ds.getChild(i);
          if (oldNode.getType().equals(
              AmazonS3Config.JOB_STARTPOINT_NODE_TYPE)) {
            ds.removeChild(i);
            break;
          }
          i++;
        }
        SpecificationNode node = new SpecificationNode(
            AmazonS3Config.JOB_STARTPOINT_NODE_TYPE);
        node.setAttribute(AmazonS3Config.JOB_BUCKETS_ATTRIBUTE,
            s3Buckets);
        ds.addChild(ds.getChildCount(), node);
      }

    }

    String xc = variableContext.getParameter(seqPrefix + "tokencount");
    if (xc != null) {
      // Delete all tokens first
      int i = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals(AmazonS3Config.JOB_ACCESS_NODE_TYPE))
          ds.removeChild(i);
        else
          i++;
      }

      int accessCount = Integer.parseInt(xc);
      i = 0;
      while (i < accessCount) {
        String accessDescription = "_" + Integer.toString(i);
        String accessOpName = seqPrefix + "accessop"
            + accessDescription;
        xc = variableContext.getParameter(accessOpName);
        if (xc != null && xc.equals("Delete")) {
          // Next row
          i++;
          continue;
        }
        // Get the stuff we need
        String accessSpec = variableContext.getParameter(seqPrefix
            + "spectoken" + accessDescription);
        SpecificationNode node = new SpecificationNode(
            AmazonS3Config.JOB_ACCESS_NODE_TYPE);
        node.setAttribute(AmazonS3Config.JOB_TOKEN_ATTRIBUTE,
            accessSpec);
        ds.addChild(ds.getChildCount(), node);
        i++;
      }

      String op = variableContext.getParameter(seqPrefix + "accessop");
      if (op != null && op.equals("Add")) {
        String accessspec = variableContext.getParameter(seqPrefix
            + "spectoken");
        SpecificationNode node = new SpecificationNode(
            AmazonS3Config.JOB_ACCESS_NODE_TYPE);
        node.setAttribute(AmazonS3Config.JOB_TOKEN_ATTRIBUTE,
            accessspec);
        ds.addChild(ds.getChildCount(), node);
      }
    }

    return null;
  }

  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale,
      Specification ds, int connectionSequenceNumber,
      int actualSequenceNumber, String tabName)
      throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put(TAB_NAME, tabName);
    paramMap.put(SEQ_NUM, Integer.toString(connectionSequenceNumber));
    paramMap.put(SELECTED_NUM, Integer.toString(actualSequenceNumber));

    fillInBucketsSpecificationMap(paramMap, ds);
    Messages.outputResourceWithVelocity(out, locale,
        AmazonS3Config.EDIT_SPEC_FORWARD_BUCKETS, paramMap);
  }

  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale,
      Specification ds, int connectionSequenceNumber,
      List<String> tabsArray) throws ManifoldCFException, IOException {
    tabsArray.add(Messages.getString(locale,
        AmazonS3Config.AMAZONS3_BUCKETS_TAB_PROPERTY));

    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put(SEQ_NUM, Integer.toString(connectionSequenceNumber));

    fillInBucketsSpecificationMap(paramMap, ds);
    Messages.outputResourceWithVelocity(out, locale,
        AmazonS3Config.EDIT_SPEC_HEADER_FORWARD, paramMap);
  }

  @Override
  public String addSeedDocuments(ISeedingActivity activities,
      Specification spec, String lastSeedVersion, long seedTime,
      int jobMode) throws ManifoldCFException, ServiceInterruption {

    long startTime;
    if (lastSeedVersion == null)
      startTime = 0L;
    else {
      // Unpack seed time from seed version string
      startTime = new Long(lastSeedVersion).longValue();
    }

    String[] bucketsToRemove = null;
    String unparsedBuckets = getExcludedBuckets(spec);
    if (unparsedBuckets != null && StringUtils.isNotEmpty(unparsedBuckets))
      bucketsToRemove = unparsedBuckets.split(BUCKET_SPLITTER);
    // get seeds
    getSeeds(activities, bucketsToRemove);

    return new Long(seedTime).toString();
  }

  private void getSeeds(ISeedingActivity activities, String[] buckets)
      throws ManifoldCFException, ServiceInterruption {
    GetSeedsThread t = new GetSeedsThread(getClient(), buckets);
    try {
      t.start();

      boolean wasInterrupted = false;
      try {
        XThreadBuffer<S3Artifact> seedBuffer = t.getBuffer();
        // Pick up the paths, and add them to the activities, before we
        // join with the child thread.
        while (true) {
          // The only kind of exceptions this can throw are going to
          // shut the process down.
          S3Artifact s3Artifact = seedBuffer.fetch();
          if (s3Artifact == null) {
            Logging.connectors.info("No artifact retured");
            break;
          }

          String issueKey = s3Artifact.getBucketName()
              + STD_SEPARATOR_BUCKET_AND_KEY
              + s3Artifact.getKey();

          Logging.connectors.info("Issue key is : " + issueKey);
          activities.addSeedDocument(issueKey);

        }
      }
      catch (InterruptedException e) {

        Logging.connectors.error(e);

        wasInterrupted = true;
        throw e;
      }
      catch (ManifoldCFException e) {

        Logging.connectors.error(e);

        if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
          wasInterrupted = true;
        throw e;
      }
      finally {
        if (!wasInterrupted)
          t.finishUp();
      }
    }
    catch (InterruptedException e) {

      Logging.connectors.error(e);

      t.interrupt();
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
          ManifoldCFException.INTERRUPTED);
    }
    catch (java.net.SocketTimeoutException e) {

      Logging.connectors.error(e);

      handleIOException(e);
    }
    catch (InterruptedIOException e) {

      Logging.connectors.error(e);

      t.interrupt();
      handleIOException(e);
    }
    catch (IOException e) {

      Logging.connectors.error(e);

      handleIOException(e);
    }
    catch (ResponseException e) {

      Logging.connectors.error(e);

      handleResponseException(e);
    }
  }

  private static void handleIOException(IOException e)
      throws ManifoldCFException, ServiceInterruption {
    if (!(e instanceof java.net.SocketTimeoutException)
        && (e instanceof InterruptedIOException)) {
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
          ManifoldCFException.INTERRUPTED);
    }
    Logging.connectors.warn("Amazons3: IO exception: " + e.getMessage(), e);
    long currentTime = System.currentTimeMillis();
    throw new ServiceInterruption("IO exception: " + e.getMessage(), e,
        currentTime + 300000L, currentTime + 3 * 60 * 60000L, -1, false);
  }

  private static void handleResponseException(ResponseException e)
      throws ManifoldCFException, ServiceInterruption {
    throw new ManifoldCFException("Unexpected response: " + e.getMessage(),
        e);
  }

  @Override
  public void processDocuments(String[] documentIdentifiers,
      IExistingVersions statuses, Specification spec,
      IProcessActivity activities, int jobMode,
      boolean usesDefaultAuthority) throws ManifoldCFException,
      ServiceInterruption {
    AmazonS3 amazons3Client = getClient();    
    documentProcess.doProcessDocument(documentIdentifiers, statuses, spec,
        activities, jobMode, usesDefaultAuthority, amazons3Client);

  }

  protected static class GetSeedsThread extends Thread {
    protected Throwable exception = null;

    protected String[] bucketsToBeRemoved;

    protected AmazonS3 s3client = null;

    protected XThreadBuffer<S3Artifact> seedBuffer;

    public XThreadBuffer<S3Artifact> getBuffer() {
      return seedBuffer;
    }

    public void setBuffer(XThreadBuffer<S3Artifact> buffer) {
      this.seedBuffer = buffer;
    }

    public GetSeedsThread(AmazonS3 s3, String[] buckets) {
      super();
      this.bucketsToBeRemoved = buckets;
      this.s3client = s3;
      seedBuffer = new XThreadBuffer<S3Artifact>();
      setDaemon(true);
    }

    @Override
    public void run() {
      try {
        // push the keys for all documents
        processSeeds();
      }
      catch (Exception e) {

        Logging.connectors.error(e);
        this.exception = e;
      }
      finally {
        seedBuffer.signalDone();
      }
    }

    private void processSeeds() {

      if (s3client != null) {

        List<Bucket> listBuckets = s3client.listBuckets();
        List<String> refinedBuckets = new ArrayList<String>();
        
        for (Bucket bucket : listBuckets) {
          if (bucketsToBeRemoved != null && bucketsToBeRemoved.length > 0 &&  !Arrays.asList(bucketsToBeRemoved).contains(
              bucket.getName())) {
            refinedBuckets.add(bucket.getName());
          }
          else{
            refinedBuckets.add(bucket.getName());
          }
        }

        for (String bucket : refinedBuckets) {
          String bucketName = bucket;
          try {
            PushSeeds(bucketName);
          }
          catch (Exception e) {
            Logging.connectors.error(e);
          }
        }

      }
      else {
        Logging.connectors.info("Could not connect amazon");
      }
    }

    private void PushSeeds(String bucketName) {
      try {
        ObjectListing objectListing = s3client
            .listObjects(new ListObjectsRequest()
                .withBucketName(bucketName));
        for (S3ObjectSummary objectSummary : objectListing
            .getObjectSummaries()) {
          try {
            addSeed(bucketName, objectSummary);
          }
          catch (Exception e) {

            Logging.connectors.error(e);

          }
        }

      }
      catch (Exception e) {

        Logging.connectors.error(e);
      }
    }

    private void addSeed(String bucketName, S3ObjectSummary objectSummary)
        throws InterruptedException {
      String objectKey = objectSummary.getKey();
      String combinedKey = bucketName + STD_SEPARATOR_BUCKET_AND_KEY
          + objectKey;
      // push the key
      seedBuffer.add(new S3Artifact(bucketName, objectKey));

      Logging.connectors
          .info("Pused a new key(combined) in seed buffer : "
              + combinedKey);
    }

    public void finishUp() throws InterruptedException, IOException,
        ResponseException {
      seedBuffer.abandon();
      join();
      Throwable thr = exception;
      if (thr != null) {
        if (thr instanceof IOException)
          throw (IOException) thr;
        else if (thr instanceof ResponseException)
          throw (ResponseException) thr;
        else if (thr instanceof RuntimeException)
          throw (RuntimeException) thr;
        else if (thr instanceof Error)
          throw (Error) thr;
        else
          throw new RuntimeException("Unhandled exception of type: "
              + thr.getClass().getName(), thr);
      }
    }

  }

  protected static class CheckThread extends Thread {
    protected String result = "Unknown";

    protected AmazonS3 s3 = null;

    protected Throwable exception = null;

    public CheckThread(AmazonS3 s3) {
      this.s3 = s3;
    }

    public String getResult() {
      return result;
    }

    public Throwable getException() {
      return exception;
    }

    @Override
    public void run() {
      try {
        if (s3 != null) {
          Owner s3AccountOwner = s3.getS3AccountOwner();
          if (s3AccountOwner != null) {
            result = StringUtils.isNotEmpty(s3AccountOwner
                .getDisplayName()) ? "Connection OK"
                : "Connection Failed";
          }

        }
      }
      catch (AmazonServiceException e) {
        result = "Connection Failed : " + e.getMessage();
        exception = e;

        Logging.connectors.error(e);
      }
      catch (AmazonClientException e) {
        result = "Connection Failed : " + e.getMessage();
        exception = e;

        Logging.connectors.error(e);
      }
    }
  }

}
