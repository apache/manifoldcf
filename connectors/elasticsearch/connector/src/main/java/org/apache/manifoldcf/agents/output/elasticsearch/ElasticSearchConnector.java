/* $Id: ElasticSearchConnector.java 1299512 2012-03-12 00:58:38Z piergiorgio $ */

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

package org.apache.manifoldcf.agents.output.elasticsearch;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Iterator;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.config.SocketConfig;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;

import org.apache.commons.io.FilenameUtils;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputNotifyActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputRemoveActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputCheckActivity;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.output.BaseOutputConnector;
import org.apache.manifoldcf.agents.output.elasticsearch.ElasticSearchAction.CommandEnum;
import org.apache.manifoldcf.agents.output.elasticsearch.ElasticSearchConnection.Result;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.ConfigurationNode;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.core.interfaces.VersionContext;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This is the "output connector" for elasticsearch.
 * 
 * @author Luca Stancapiano
 */
public class ElasticSearchConnector extends BaseOutputConnector
{

  private final static String ELASTICSEARCH_INDEXATION_ACTIVITY = "Indexation";
  private final static String ELASTICSEARCH_DELETION_ACTIVITY = "Deletion";
  private final static String ELASTICSEARCH_OPTIMIZE_ACTIVITY = "Optimize";

  private final static String[] ELASTICSEARCH_ACTIVITIES =
  { ELASTICSEARCH_INDEXATION_ACTIVITY, ELASTICSEARCH_DELETION_ACTIVITY,
      ELASTICSEARCH_OPTIMIZE_ACTIVITY };

  private final static String ELASTICSEARCH_TAB_ELASTICSEARCH = "ElasticSearchConnector.ElasticSearch";
  private final static String ELASTICSEARCH_TAB_PARAMETERS = "ElasticSearchConnector.Parameters";

  /** Forward to the javascript to check the configuration parameters */
  private static final String EDIT_CONFIG_HEADER_FORWARD = "editConfiguration.js";

  /** Forward to the HTML template to edit the configuration parameters */
  private static final String EDIT_CONFIG_FORWARD_PARAMETERS = "editConfiguration_Parameters.html";

  /** Forward to the HTML template to view the configuration parameters */
  private static final String VIEW_CONFIG_FORWARD = "viewConfiguration.html";

  /** Forward to the javascript to check the specification parameters for the job */
  private static final String EDIT_SPEC_HEADER_FORWARD = "editSpecification.js";

  /** Forward to the template to edit the configuration parameters for the job */
  private static final String EDIT_SPEC_FORWARD_ELASTICSEARCH = "editSpecification_ElasticSearch.html";

  /** Forward to the template to view the specification parameters for the job */
  private static final String VIEW_SPEC_FORWARD = "viewSpecification.html";

  /** Connection expiration interval */
  private static final long EXPIRATION_INTERVAL = 60000L;

  private HttpClientConnectionManager connectionManager = null;
  private HttpClient client = null;
  private long expirationTime = -1L;
  
  public ElasticSearchConnector()
  {
  }

  @Override
  public void connect(ConfigParams configParams)
  {
    super.connect(configParams);
  }
  
  protected HttpClient getSession()
    throws ManifoldCFException
  {
    if (client == null)
    {
      connectionManager = new PoolingHttpClientConnectionManager();
      
      int socketTimeout = 900000;
      int connectionTimeout = 60000;
      
      // Set up connection manager
      connectionManager = new PoolingHttpClientConnectionManager();

      CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

      RequestConfig.Builder requestBuilder = RequestConfig.custom()
          .setCircularRedirectsAllowed(true)
          .setSocketTimeout(socketTimeout)
          .setStaleConnectionCheckEnabled(true)
          .setExpectContinueEnabled(true)
          .setConnectTimeout(connectionTimeout)
          .setConnectionRequestTimeout(socketTimeout);
          
      client = HttpClients.custom()
        .setConnectionManager(connectionManager)
        .setMaxConnTotal(1)
        .disableAutomaticRetries()
        .setDefaultRequestConfig(requestBuilder.build())
        .setDefaultSocketConfig(SocketConfig.custom()
          //.setTcpNoDelay(true)
          .setSoTimeout(socketTimeout)
          .build())
        .setDefaultCredentialsProvider(credentialsProvider)
        .setRequestExecutor(new HttpRequestExecutor(socketTimeout))
        .build();


    }
    expirationTime = System.currentTimeMillis() + EXPIRATION_INTERVAL;
    return client;
  }

  protected void closeSession()
  {
    if (connectionManager != null)
    {
      connectionManager.shutdown();
      connectionManager = null;
    }
    client = null;
    expirationTime = -1L;
  }
  
  /** This method is called to assess whether to count this connector instance should
  * actually be counted as being connected.
  *@return true if the connector instance is actually connected.
  */
  @Override
  public boolean isConnected()
  {
    return connectionManager != null;
  }

  @Override
  public void disconnect()
    throws ManifoldCFException
  {
    super.disconnect();
    closeSession();
  }
  
  
  @Override
  public void poll()
    throws ManifoldCFException
  {
    super.poll();
    if (connectionManager != null)
    {
      if (System.currentTimeMillis() > expirationTime)
      {
        closeSession();
      }
    }
  }
  
  @Override
  public String[] getActivitiesList()
  {
    return ELASTICSEARCH_ACTIVITIES;
  }

  /** Read the content of a resource, replace the variable ${PARAMNAME} with the
   * value and copy it to the out.
   * 
   * @param resName
   * @param out
   * @throws ManifoldCFException */
  private static void outputResource(String resName, IHTTPOutput out,
      Locale locale, ElasticSearchParam params,
      String tabName, Integer sequenceNumber, Integer currentSequenceNumber) throws ManifoldCFException
  {
    Map<String,String> paramMap = null;
    if (params != null) {
      paramMap = params.buildMap();
      if (tabName != null) {
        paramMap.put("TabName", tabName);
      }
      if (currentSequenceNumber != null)
        paramMap.put("SelectedNum",currentSequenceNumber.toString());
    }
    else
    {
      paramMap = new HashMap<String,String>();
    }
    if (sequenceNumber != null)
      paramMap.put("SeqNum",sequenceNumber.toString());

    Messages.outputResourceWithVelocity(out, locale, resName, paramMap, true);
  }

  @Override
  public void outputConfigurationHeader(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters,
      List<String> tabsArray) throws ManifoldCFException, IOException
  {
    super.outputConfigurationHeader(threadContext, out, locale, parameters,
        tabsArray);
    tabsArray.add(Messages.getString(locale, ELASTICSEARCH_TAB_PARAMETERS));
    outputResource(EDIT_CONFIG_HEADER_FORWARD, out, locale, null, null, null, null);
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
      throws ManifoldCFException, IOException
  {
    super.outputConfigurationBody(threadContext, out, locale, parameters,
        tabName);
    ElasticSearchConfig config = this.getConfigParameters(parameters);
    outputResource(EDIT_CONFIG_FORWARD_PARAMETERS, out, locale, config, tabName, null, null);
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
    super.outputSpecificationHeader(out, locale, os, connectionSequenceNumber, tabsArray);
    tabsArray.add(Messages.getString(locale, ELASTICSEARCH_TAB_ELASTICSEARCH));
    
    outputResource(EDIT_SPEC_HEADER_FORWARD, out, locale, null, null, new Integer(connectionSequenceNumber), null);
  }

  final private SpecificationNode getSpecNode(Specification os)
  {
    int l = os.getChildCount();
    for (int i = 0; i < l; i++)
    {
      SpecificationNode node = os.getChild(i);
      if (ElasticSearchSpecs.ELASTICSEARCH_SPECS_NODE.equals(node.getType()))
      {
        return node;
      }
    }
    return null;
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

    ElasticSearchSpecs specs = getSpecParameters(os);
    
    outputResource(EDIT_SPEC_FORWARD_ELASTICSEARCH, out, locale, specs, tabName, new Integer(connectionSequenceNumber), new Integer(actualSequenceNumber));
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
    throws ManifoldCFException
  {
    ConfigurationNode specNode = getSpecNode(os);
    boolean bAdd = (specNode == null);
    if (bAdd)
    {
      specNode = new SpecificationNode(
          ElasticSearchSpecs.ELASTICSEARCH_SPECS_NODE);
    }
    ElasticSearchSpecs.contextToSpecNode(variableContext, specNode, connectionSequenceNumber);
    if (bAdd)
      os.addChild(os.getChildCount(), specNode);
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
    outputResource(VIEW_SPEC_FORWARD, out, locale, getSpecParameters(os), null, new Integer(connectionSequenceNumber),null);

  }


  /** Build a Set of ElasticSearch parameters. If configParams is null,
   * getConfiguration() is used.
   * 
   * @param configParams */
  final private ElasticSearchConfig getConfigParameters(
      ConfigParams configParams)
  {
    if (configParams == null)
      configParams = getConfiguration();
    return new ElasticSearchConfig(configParams);
  }

  final private ElasticSearchSpecs getSpecParameters(Specification os)
      throws ManifoldCFException
  {
    return new ElasticSearchSpecs(getSpecNode(os));
  }

  final private ElasticSearchSpecs getSpecsCache(String outputDescription)
      throws ManifoldCFException
  {
    try
    {
      return new ElasticSearchSpecs(new JSONObject(outputDescription));
    } catch (JSONException e)
    {
      throw new ManifoldCFException(e);
    }
  }

  @Override
  public VersionContext getPipelineDescription(Specification os)
      throws ManifoldCFException
  {
    ElasticSearchSpecs specs = new ElasticSearchSpecs(getSpecNode(os));
    return new VersionContext(specs.toJson().toString(),params,os);
  }

  @Override
  public boolean checkLengthIndexable(VersionContext outputDescription, long length, IOutputCheckActivity activities)
      throws ManifoldCFException, ServiceInterruption
  {
    ElasticSearchSpecs specs = getSpecsCache(outputDescription.getVersionString());
    long maxFileSize = specs.getMaxFileSize();
    if (length > maxFileSize)
      return false;
    return super.checkLengthIndexable(outputDescription, length, activities);
  }

  @Override
  public boolean checkDocumentIndexable(VersionContext outputDescription, File localFile, IOutputCheckActivity activities)
      throws ManifoldCFException, ServiceInterruption
  {
    // No filtering here; we don't look inside the file and don't know its extension.  That's done via the url
    // filter
    return true;
  }
  
  /** Pre-determine whether a document's URL is indexable by this connector.  This method is used by participating repository connectors
  * to help filter out documents that are not worth indexing.
  *@param outputDescription is the document's output version.
  *@param url is the URL of the document.
  *@return true if the file is indexable.
  */
  @Override
  public boolean checkURLIndexable(VersionContext outputDescription, String url, IOutputCheckActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    ElasticSearchSpecs specs = getSpecsCache(outputDescription.getVersionString());
    return specs.checkExtension(FilenameUtils.getExtension(url));
  }

  @Override
  public boolean checkMimeTypeIndexable(String outputDescription,
      String mimeType) throws ManifoldCFException, ServiceInterruption
  {
    ElasticSearchSpecs specs = getSpecsCache(outputDescription);
    return specs.checkMimeType(mimeType);
  }

  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
      Locale locale, ConfigParams parameters) throws ManifoldCFException,
      IOException
  {
    outputResource(VIEW_CONFIG_FORWARD, out, locale,
        getConfigParameters(parameters), null, null, null);
  }

  @Override
  public String processConfigurationPost(IThreadContext threadContext,
      IPostParameters variableContext, ConfigParams parameters)
      throws ManifoldCFException
  {
    ElasticSearchConfig.contextToConfig(variableContext, parameters);
    return null;
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

  @Override
  public int addOrReplaceDocument(String documentURI, String outputDescription,
      RepositoryDocument document, String authorityNameString,
      IOutputAddActivity activities) throws ManifoldCFException,
      ServiceInterruption
  {
    HttpClient client = getSession();
    ElasticSearchConfig config = getConfigParameters(null);
    InputStream inputStream = document.getBinaryStream();
    // For ES, we have to have fixed fields only; nothing else is possible b/c we don't have
    // default field values.
    String[] acls = null;
    String[] denyAcls = null;
    String[] shareAcls = null;
    String[] shareDenyAcls = null;
    String[] parentAcls = null;
    String[] parentDenyAcls = null;
    Iterator<String> a = document.securityTypesIterator();
    while (a.hasNext())
    {
      String securityType = a.next();
      String[] convertedAcls = convertACL(document.getSecurityACL(securityType),authorityNameString,activities);
      String[] convertedDenyAcls = convertACL(document.getSecurityDenyACL(securityType),authorityNameString,activities);
      if (securityType.equals(RepositoryDocument.SECURITY_TYPE_DOCUMENT))
      {
        acls = convertedAcls;
        denyAcls = convertedDenyAcls;
      }
      else if (securityType.equals(RepositoryDocument.SECURITY_TYPE_SHARE))
      {
        shareAcls = convertedAcls;
        shareDenyAcls = convertedDenyAcls;
      }
      else if (securityType.equals(RepositoryDocument.SECURITY_TYPE_PARENT))
      {
        parentAcls = convertedAcls;
        parentDenyAcls = convertedDenyAcls;
      }
      else
      {
        // Don't know how to deal with it
        return DOCUMENTSTATUS_REJECTED;
      }
    }
    
    long startTime = System.currentTimeMillis();
    ElasticSearchIndex oi = new ElasticSearchIndex(client, config);
    try
    {
      oi.execute(documentURI, document, inputStream, acls, denyAcls, shareAcls, shareDenyAcls, parentAcls, parentDenyAcls);
      if (oi.getResult() != Result.OK)
        return DOCUMENTSTATUS_REJECTED;
      return DOCUMENTSTATUS_ACCEPTED;
    }
    finally
    {
      activities.recordActivity(startTime, ELASTICSEARCH_INDEXATION_ACTIVITY,
        document.getBinaryLength(), documentURI, oi.getResult().name(), oi.getResultDescription());
    }
  }

  @Override
  public void removeDocument(String documentURI, String outputDescription,
      IOutputRemoveActivity activities) throws ManifoldCFException,
      ServiceInterruption
  {
    HttpClient client = getSession();
    long startTime = System.currentTimeMillis();
    ElasticSearchDelete od = new ElasticSearchDelete(client, getConfigParameters(null));
    try
    {
      od.execute(documentURI);
    }
    finally
    {
      activities.recordActivity(startTime, ELASTICSEARCH_DELETION_ACTIVITY, null,
          documentURI, od.getResult().name(), od.getResultDescription());
    }
  }

  @Override
  public String check() throws ManifoldCFException
  {
    HttpClient client = getSession();
    ElasticSearchAction oss = new ElasticSearchAction(client, getConfigParameters(null));
    try
    {
      oss.execute(CommandEnum._status, true);
      String resultName = oss.getResult().name();
      if (resultName.equals("OK"))
        return super.check();
      return resultName + " " + oss.getResultDescription();
    }
    catch (ServiceInterruption e)
    {
      return "Transient exception: "+e.getMessage();
    }
  }

  @Override
  public void noteJobComplete(IOutputNotifyActivity activities)
      throws ManifoldCFException, ServiceInterruption
  {
    HttpClient client = getSession();
    long startTime = System.currentTimeMillis();
    ElasticSearchAction oo = new ElasticSearchAction(client, getConfigParameters(null));
    try
    {
      oo.execute(CommandEnum._optimize, false);
    }
    finally
    {
      activities.recordActivity(startTime, ELASTICSEARCH_OPTIMIZE_ACTIVITY, null,
          oo.getCallUrlSnippet(), oo.getResult().name(),
          oo.getResultDescription());
    }
  }

}
