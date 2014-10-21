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
package org.apache.manifoldcf.crawler.connectors.alfresco;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.alfresco.webservice.authentication.AuthenticationFault;
import org.alfresco.webservice.content.Content;
import org.alfresco.webservice.repository.QueryResult;
import org.alfresco.webservice.types.NamedValue;
import org.alfresco.webservice.types.Node;
import org.alfresco.webservice.types.Predicate;
import org.alfresco.webservice.types.Reference;
import org.alfresco.webservice.types.ResultSet;
import org.alfresco.webservice.types.ResultSetRow;
import org.alfresco.webservice.util.AuthenticationDetails;
import org.alfresco.webservice.util.AuthenticationUtils;
import org.alfresco.webservice.util.WebServiceException;
import org.alfresco.webservice.util.WebServiceFactory;
import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPasswordMapperActivity;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector;
import org.apache.manifoldcf.crawler.interfaces.IProcessActivity;
import org.apache.manifoldcf.crawler.interfaces.ISeedingActivity;
import org.apache.manifoldcf.crawler.interfaces.IExistingVersions;
import org.apache.manifoldcf.crawler.system.Logging;

public class AlfrescoRepositoryConnector extends BaseRepositoryConnector {

  /** Fetch activity */
  public final static String ACTIVITY_FETCH = "fetch";

  /** Username value for the Alfresco session */
  protected String username = null;
  
  /** Password value for the Alfresco session */
  protected String password = null;
  
  /** Endpoint protocol */
  protected String protocol = null;
  
  /** Endpoint server name */
  protected String server = null;
  
  /** Endpoint port */
  protected String port = null;
  
  /** Endpoint context path of the Alfresco webapp */
  protected String path = null;
  
  /** Endpoint with all the details */
  protected String endpoint = null;
  
  /** Alfresco Tenant domain */
  protected String tenantDomain = null;
  
  /** Socket Timeout for the Alfresco Web Service Client */
  protected int socketTimeout = -1;
  
  protected AuthenticationDetails session = null;

  protected static final long timeToRelease = 300000L;
  protected long lastSessionFetch = -1L;

  protected static final String RELATIONSHIP_CHILD = "child";

  // Tabs
  
  /** Tab name parameter for managin the view of the Web UI */
  private static final String TAB_NAME_PARAM = "TabName";
  
  /** The sequence number parameter */
  private static final String SEQ_NUM_PARAM = "SeqNum";
  
  /** The selected sequence number parameter */
  private static final String SELECTED_NUM_PARAM = "SelectedNum";
  
  /** The Lucene Query label for the configuration tab of the job settings */
  private static final String TAB_LABEL_LUCENE_QUERY_RESOURCE = "AlfrescoConnector.LuceneQuery";
  /** Alfresco Server configuration tab name */
  private static final String ALFRESCO_SERVER_TAB_RESOURCE = "AlfrescoConnector.Server";

  // Velocity template names

  /** Forward to the javascript to check the configuration parameters */
  private static final String EDIT_CONFIG_HEADER_FORWARD = "editConfiguration.js";

  /** Forward to the HTML template to edit the configuration parameters */
  private static final String EDIT_CONFIG_FORWARD_SERVER = "editConfiguration_Server.html";
  
  /** Forward to the javascript to check the specification parameters for the job */
  private static final String EDIT_SPEC_HEADER_FORWARD = "editSpecification.js";

  /** Forward to the template to edit the configuration parameters for the job */
  private static final String EDIT_SPEC_FORWARD_LUCENEQUERY = "editSpecification_LuceneQuery.html";
  
  /** Forward to the HTML template to view the configuration parameters */
  private static final String VIEW_CONFIG_FORWARD = "viewConfiguration.html";
  
  /** Forward to the template to view the specification parameters for the job */
  private static final String VIEW_SPEC_FORWARD = "viewSpecification.html";

  // Other miscellaneous constants
  
  /** The root node for the Alfresco connector configuration in ManifoldCF */
  private static final String JOB_STARTPOINT_NODE_TYPE = "startpoint";

  /** Read activity */
  protected final static String ACTIVITY_READ = "read document";
  
  /** Separator used when a node has more than one content stream. More than one d:content property */
  private static final String INGESTION_SEPARATOR_FOR_MULTI_BINARY = ";";

  /**
   * Constructor
   */
  public AlfrescoRepositoryConnector() {
    super();
  }

  /** 
   * Return the list of activities that this connector supports (i.e. writes into the log).
   * @return the list.
   */
  @Override
  public String[] getActivitiesList() {
    return new String[] { ACTIVITY_FETCH };
  }

  /** Get the bin name strings for a document identifier.  The bin name describes the queue to which the
   * document will be assigned for throttling purposes.  Throttling controls the rate at which items in a
   * given queue are fetched; it does not say anything about the overall fetch rate, which may operate on
   * multiple queues or bins.
   * For example, if you implement a web crawler, a good choice of bin name would be the server name, since
   * that is likely to correspond to a real resource that will need real throttle protection.
   *@param documentIdentifier is the document identifier.
   *@return the set of bin names.  If an empty array is returned, it is equivalent to there being no request
   * rate throttling available for this identifier.
   */
  @Override
  public String[] getBinNames(String documentIdentifier) {
    return new String[] { server };
  }

  /** 
   * Close the connection.  Call this before discarding the connection.
   */
  @Override
  public void disconnect() throws ManifoldCFException {
    if (session != null) {
      try {
        AuthenticationUtils.endSession();
      } catch (Exception e) {
        Logging.connectors.error("Alfresco: error disconnect:"+e.getMessage(), e);
        throw new ManifoldCFException("Alfresco: error disconnect:"+e.getMessage(), e);
      }
      session = null;
      lastSessionFetch = -1L;
    }

    username = null;
    password = null;
    protocol = null;
    server = null;
    port = null;
    path = null;
    endpoint = null;
    tenantDomain = null;
    socketTimeout = AlfrescoConfig.SOCKET_TIMEOUT_DEFAULT_VALUE;

  }

  /** Connect.
   *@param configParameters is the set of configuration parameters, which
   * in this case describe the target appliance, basic auth configuration, etc.  (This formerly came
   * out of the ini file.)
   */
  @Override
  public void connect(ConfigParams configParams) {
    super.connect(configParams);
    username = params.getParameter(AlfrescoConfig.USERNAME_PARAM);
    password = params.getParameter(AlfrescoConfig.PASSWORD_PARAM);
    protocol = params.getParameter(AlfrescoConfig.PROTOCOL_PARAM);
    server = params.getParameter(AlfrescoConfig.SERVER_PARAM);
    port = params.getParameter(AlfrescoConfig.PORT_PARAM);
    path = params.getParameter(AlfrescoConfig.PATH_PARAM);
    tenantDomain = params.getParameter(AlfrescoConfig.TENANT_DOMAIN_PARAM);
    
    if(params.getParameter(AlfrescoConfig.SOCKET_TIMEOUT_PARAM)!=null){
      socketTimeout = Integer.parseInt(params.getParameter(AlfrescoConfig.SOCKET_TIMEOUT_PARAM));
    } else {
      socketTimeout = AlfrescoConfig.SOCKET_TIMEOUT_DEFAULT_VALUE;
    }
    
    //endpoint
    if(StringUtils.isNotEmpty(protocol)
        && StringUtils.isNotEmpty(server)
        && StringUtils.isNotEmpty(port)
        && StringUtils.isNotEmpty(path)){
      endpoint = protocol+"://"+server+":"+port+path;
    }
    
    //tenant domain (optional parameter). Pattern: username@tenantDomain
    if(StringUtils.isNotEmpty(tenantDomain)){
      username += AlfrescoConfig.TENANT_DOMAIN_SEP + tenantDomain;
    }
    
  }

  /** Test the connection.  Returns a string describing the connection integrity.
   *@return the connection's status as a displayable string.
   */
  @Override
  public String check() throws ManifoldCFException {
    try {
      checkConnection();
      return super.check();
    } catch (ServiceInterruption e) {
      return "Connection temporarily failed: " + e.getMessage();
    } catch (ManifoldCFException e) {
      return "Connection failed: " + e.getMessage();
    }
  }

  /** Set up a session */
  protected void getSession() throws ManifoldCFException, ServiceInterruption {
    if (session == null) {
      // Check for parameter validity

      if (StringUtils.isEmpty(username))
        throw new ManifoldCFException("Parameter " + AlfrescoConfig.USERNAME_PARAM
            + " required but not set");

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("Alfresco: Username = '" + username + "'");

      if (StringUtils.isEmpty(password))
        throw new ManifoldCFException("Parameter " + AlfrescoConfig.PASSWORD_PARAM
            + " required but not set");

      Logging.connectors.debug("Alfresco: Password exists");

      if (StringUtils.isEmpty(protocol))
        throw new ManifoldCFException("Parameter " + AlfrescoConfig.PROTOCOL_PARAM
            + " required but not set");
      
      if (StringUtils.isEmpty(server))
        throw new ManifoldCFException("Parameter " + AlfrescoConfig.SERVER_PARAM
            + " required but not set");
      
      if (StringUtils.isEmpty(port))
        throw new ManifoldCFException("Parameter " + AlfrescoConfig.PORT_PARAM
            + " required but not set");
      
      if (StringUtils.isEmpty(path))
        throw new ManifoldCFException("Parameter " + AlfrescoConfig.PATH_PARAM
            + " required but not set");
    
    endpoint = protocol+"://"+server+":"+port+path;
    try {
    
      WebServiceFactory.setEndpointAddress(endpoint);
      WebServiceFactory.setTimeoutMilliseconds(socketTimeout);
      AuthenticationUtils.startSession(username, password);
      session = AuthenticationUtils.getAuthenticationDetails();
      
    }catch (AuthenticationFault e) {
        Logging.connectors.warn(
            "Alfresco: Error during authentication. Username: "+username + ", endpoint: "+endpoint+". "
                + e.getMessage(), e);
        handleIOException(e);
      } catch (WebServiceException e){
        Logging.connectors.warn(
            "Alfresco: Error during trying to authenticate the user. Username: "+username + ", endpoint: "+endpoint
            +". Please check the connector parameters. " 
            + e.getMessage(), e);
        throw new ManifoldCFException("Alfresco: Error during trying to authenticate the user. Username: "+username + ", endpoint: "+endpoint
            +". Please check the connector parameters. "
            + e.getMessage(), e);
      }
    
    lastSessionFetch = System.currentTimeMillis();
    
    }
  }

  /**
   * Release the session, if it's time.
   */
  protected void releaseCheck() throws ManifoldCFException {
    if (lastSessionFetch == -1L)
      return;

    long currentTime = System.currentTimeMillis();
    if (currentTime >= lastSessionFetch + timeToRelease) {
        try {
          AuthenticationUtils.endSession();
        } catch (Exception e) {
          Logging.connectors.error(
              "Alfresco: Error during releasing the connection.");
          throw new ManifoldCFException( "Alfresco: Error during releasing the connection.");
        }
        session = null;
        lastSessionFetch = -1L;
    }
  }

  protected void checkConnection() throws ManifoldCFException,
      ServiceInterruption {
    while (true) {
      try {
          getSession();
          String ticket = AuthenticationUtils.getTicket();
          if(StringUtils.isEmpty(ticket)){
            Logging.connectors.error(
                "Alfresco: Error during checking the connection.");
            throw new ManifoldCFException( "Alfresco: Error during checking the connection.");
          }
          AuthenticationUtils.endSession();
        } catch (Exception e) {
          Logging.connectors.error(
              "Alfresco: Error during checking the connection.");
          throw new ManifoldCFException( "Alfresco: Error during checking the connection.");
        }
        session=null;
        return;
    }
  }

  /** This method is periodically called for all connectors that are connected but not
   * in active use.
   */
  @Override
  public void poll() throws ManifoldCFException {
    if (lastSessionFetch == -1L)
      return;

    long currentTime = System.currentTimeMillis();
    if (currentTime >= lastSessionFetch + timeToRelease) {
        try {
          AuthenticationUtils.endSession();
          session = null;
          lastSessionFetch = -1L;
        } catch (Exception e) {
          Logging.connectors.error(
              "Alfresco: Error during polling: "
                  + e.getMessage(), e);
          throw new ManifoldCFException("Alfresco: Error during polling: "
              + e.getMessage(),e);
        }
    }
  }

  /** This method is called to assess whether to count this connector instance should
  * actually be counted as being connected.
  *@return true if the connector instance is actually connected.
  */
  @Override
  public boolean isConnected()
  {
    return session != null;
  }

  /** Queue "seed" documents.  Seed documents are the starting places for crawling activity.  Documents
  * are seeded when this method calls appropriate methods in the passed in ISeedingActivity object.
  *
  * This method can choose to find repository changes that happen only during the specified time interval.
  * The seeds recorded by this method will be viewed by the framework based on what the
  * getConnectorModel() method returns.
  *
  * It is not a big problem if the connector chooses to create more seeds than are
  * strictly necessary; it is merely a question of overall work required.
  *
  * The end time and seeding version string passed to this method may be interpreted for greatest efficiency.
  * For continuous crawling jobs, this method will
  * be called once, when the job starts, and at various periodic intervals as the job executes.
  *
  * When a job's specification is changed, the framework automatically resets the seeding version string to null.  The
  * seeding version string may also be set to null on each job run, depending on the connector model returned by
  * getConnectorModel().
  *
  * Note that it is always ok to send MORE documents rather than less to this method.
  * The connector will be connected before this method can be called.
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is a document specification (that comes from the job).
  *@param seedTime is the end of the time range of documents to consider, exclusive.
  *@param lastSeedVersionString is the last seeding version string for this job, or null if the job has no previous seeding version string.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@return an updated seeding version string, to be stored with the job.
  */
  @Override
  public String addSeedDocuments(ISeedingActivity activities, Specification spec,
    String lastSeedVersion, long seedTime, int jobMode)
    throws ManifoldCFException, ServiceInterruption {

    String luceneQuery = StringUtils.EMPTY;
    int i = 0;
    while (i < spec.getChildCount()) {
      SpecificationNode sn = spec.getChild(i);
      if (sn.getType().equals(JOB_STARTPOINT_NODE_TYPE)) {
        luceneQuery = sn.getAttributeValue(AlfrescoConfig.LUCENE_QUERY_PARAM);
        break;
      }
      i++;
    }
    
    try{
      QueryResult queryResult = null;
      if (StringUtils.isEmpty(luceneQuery)) {
        // get documents from the root of the Alfresco Repository
        queryResult = SearchUtils.getChildrenFromCompanyHome(endpoint, username, password, socketTimeout, session);
      } else {
        // execute a Lucene query against the repository
        queryResult = SearchUtils.luceneSearch(endpoint, username, password, socketTimeout, session, luceneQuery);
      }
  
      if(queryResult!=null){
        ResultSet resultSet = queryResult.getResultSet();
        ResultSetRow[] resultSetRows = resultSet.getRows();
        for (ResultSetRow resultSetRow : resultSetRows) {
            NamedValue[] properties = resultSetRow.getColumns();
            String nodeReference = PropertiesUtils.getNodeReference(properties);
            activities.addSeedDocument(nodeReference);
          }
      }
    } catch(IOException e){
      Logging.connectors.warn("Alfresco: IOException: " + e.getMessage(), e);
      handleIOException(e);
    }
    return "";
  }

  /** Get the maximum number of documents to amalgamate together into one batch, for this connector.
   *@return the maximum number. 0 indicates "unlimited".
   */
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
    return new String[] { RELATIONSHIP_CHILD };
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
      Locale locale, Map<String,String> paramMap) throws ManifoldCFException {
    Messages.outputResourceWithVelocity(out,locale,resName,paramMap,true);
  }

  /** Fill in Velocity parameters for the Server tab.
  */
  private static void fillInServerParameters(Map<String,String> paramMap, IPasswordMapperActivity mapper, ConfigParams parameters)
  {
    String username = parameters.getParameter(AlfrescoConfig.USERNAME_PARAM);
    if (username == null)
      username = AlfrescoConfig.USERNAME_DEFAULT_VALUE;
    paramMap.put(AlfrescoConfig.USERNAME_PARAM, username);

    String password = parameters.getParameter(AlfrescoConfig.PASSWORD_PARAM);
    if (password == null) 
      password = AlfrescoConfig.PASSWORD_DEFAULT_VALUE;
    else
      password = mapper.mapPasswordToKey(password);
    paramMap.put(AlfrescoConfig.PASSWORD_PARAM, password);
    
    String protocol = parameters.getParameter(AlfrescoConfig.PROTOCOL_PARAM);
    if (protocol == null)
      protocol = AlfrescoConfig.PROTOCOL_DEFAULT_VALUE;
    paramMap.put(AlfrescoConfig.PROTOCOL_PARAM, protocol);
    
    String server = parameters.getParameter(AlfrescoConfig.SERVER_PARAM);
    if (server == null)
      server = AlfrescoConfig.SERVER_DEFAULT_VALUE;
    paramMap.put(AlfrescoConfig.SERVER_PARAM, server);
    
    String port = parameters.getParameter(AlfrescoConfig.PORT_PARAM);
    if (port == null)
      port = AlfrescoConfig.PORT_DEFAULT_VALUE;
    paramMap.put(AlfrescoConfig.PORT_PARAM, port);
    
    String path = parameters.getParameter(AlfrescoConfig.PATH_PARAM);
    if (path == null)
      path = AlfrescoConfig.PATH_DEFAULT_VALUE;
    paramMap.put(AlfrescoConfig.PATH_PARAM, path);
    
    String tenantDomain = parameters.getParameter(AlfrescoConfig.TENANT_DOMAIN_PARAM);
    if (tenantDomain == null)
      tenantDomain = StringUtils.EMPTY;
    paramMap.put(AlfrescoConfig.TENANT_DOMAIN_PARAM, tenantDomain);
    
    String socketTimeout = parameters.getParameter(AlfrescoConfig.SOCKET_TIMEOUT_PARAM);
    if (socketTimeout == null)
      socketTimeout = String.valueOf(AlfrescoConfig.SOCKET_TIMEOUT_DEFAULT_VALUE);
    paramMap.put(AlfrescoConfig.SOCKET_TIMEOUT_PARAM, socketTimeout);
    
  }

  /**
   * View configuration. This method is called in the body section of the
   * connector's view configuration page. Its purpose is to present the
   * connection information to the user. The coder can presume that the HTML
   * that is output from this configuration will be within appropriate <html>
   * and <body> tags.
   * 
   * @param threadContext
   *          is the local thread context.
   * @param out
   *          is the output to which any HTML should be sent.
   * @param parameters
   *          are the configuration parameters, as they currently exist, for
   *          this connection being configured.
   */
  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters) throws ManifoldCFException, IOException {
        
    Map<String,String> paramMap = new HashMap<String,String>();
        
    // Fill in parameters for all tabs

    // Server tab
    fillInServerParameters(paramMap, out, parameters);
  
    outputResource(VIEW_CONFIG_FORWARD, out, locale, paramMap);
  }

  /**
   * Output the configuration header section. This method is called in the head
   * section of the connector's configuration page. Its purpose is to add the
   * required tabs to the list, and to output any javascript methods that might
   * be needed by the configuration editing HTML.
   * 
   * @param threadContext
   *          is the local thread context.
   * @param out
   *          is the output to which any HTML should be sent.
   * @param parameters
   *          are the configuration parameters, as they currently exist, for
   *          this connection being configured.
   * @param tabsArray
   *          is an array of tab names. Add to this array any tab names that are
   *          specific to the connector.
   */
  @Override
  public void outputConfigurationHeader(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters, List<String> tabsArray)
      throws ManifoldCFException, IOException {
    // Add Server tab
    tabsArray.add(Messages.getString(locale,ALFRESCO_SERVER_TAB_RESOURCE));

    Map<String,String> paramMap = new HashMap<String,String>();
        
    // Fill in parameters for all tabs
    fillInServerParameters(paramMap, out, parameters);

    outputResource(EDIT_CONFIG_HEADER_FORWARD, out, locale, paramMap);
  }

  /** Output the configuration body section.
   * This method is called in the body section of the connector's configuration page.  Its purpose is to present the required form elements for editing.
   * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
   * form is "editconnection".
   *@param threadContext is the local thread context.
   *@param out is the output to which any HTML should be sent.
   *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
   *@param tabName is the current tab name.
   */
  @Override
  public void outputConfigurationBody(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
      throws ManifoldCFException, IOException {
    
    // Do the Server tab
    Map<String,String> paramMap = new HashMap<String,String>();
    paramMap.put(TAB_NAME_PARAM, tabName);
    fillInServerParameters(paramMap, out, parameters);
    outputResource(EDIT_CONFIG_FORWARD_SERVER, out, locale, paramMap);  
  }

  /**
   * Process a configuration post. This method is called at the start of the
   * connector's configuration page, whenever there is a possibility that form
   * data for a connection has been posted. Its purpose is to gather form
   * information and modify the configuration parameters accordingly. The name
   * of the posted form is "editconnection".
   * 
   * @param threadContext
   *          is the local thread context.
   * @param variableContext
   *          is the set of variables available from the post, including binary
   *          file post information.
   * @param parameters
   *          are the configuration parameters, as they currently exist, for
   *          this connection being configured.
   * @return null if all is well, or a string error message if there is an error
   *         that should prevent saving of the connection (and cause a
   *         redirection to an error page).
   */
  @Override
  public String processConfigurationPost(IThreadContext threadContext,
      IPostParameters variableContext, Locale locale, ConfigParams parameters)
      throws ManifoldCFException {
        
    String username = variableContext.getParameter(AlfrescoConfig.USERNAME_PARAM);
    if (username != null) {
      parameters.setParameter(AlfrescoConfig.USERNAME_PARAM, username);
    }

    String password = variableContext.getParameter(AlfrescoConfig.PASSWORD_PARAM);
    if (password != null) {
      parameters.setParameter(AlfrescoConfig.PASSWORD_PARAM, variableContext.mapKeyToPassword(password));
    }
    
    String protocol = variableContext.getParameter(AlfrescoConfig.PROTOCOL_PARAM);
    if (protocol != null) {
      parameters.setParameter(AlfrescoConfig.PROTOCOL_PARAM, protocol);
    }
    
    String server = variableContext.getParameter(AlfrescoConfig.SERVER_PARAM);
    if (server != null) {
      parameters.setParameter(AlfrescoConfig.SERVER_PARAM, server);
    }
    
    String port = variableContext.getParameter(AlfrescoConfig.PORT_PARAM);
    if (port != null){
      parameters.setParameter(AlfrescoConfig.PORT_PARAM, port);
    }
    
    String path = variableContext.getParameter(AlfrescoConfig.PATH_PARAM);
    if (path != null) {
      parameters.setParameter(AlfrescoConfig.PATH_PARAM, path);
    }
    
    String tenantDomain = variableContext.getParameter(AlfrescoConfig.TENANT_DOMAIN_PARAM);
    if (tenantDomain != null){
      parameters.setParameter(AlfrescoConfig.TENANT_DOMAIN_PARAM, tenantDomain);
    }
    
    String socketTimeout = variableContext.getParameter(AlfrescoConfig.SOCKET_TIMEOUT_PARAM);
    if (socketTimeout != null){
      parameters.setParameter(AlfrescoConfig.SOCKET_TIMEOUT_PARAM, socketTimeout);
    }
    
    return null;
  }

  /** Fill in Velocity parameters for the LuceneQuery tab.
  */
  private static void fillInLuceneQueryParameters(Map<String,String> paramMap, Specification ds)
  {
    int i = 0;
    String luceneQuery = "";
    while (i < ds.getChildCount()) {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals(JOB_STARTPOINT_NODE_TYPE)) {
        luceneQuery = sn.getAttributeValue(AlfrescoConfig.LUCENE_QUERY_PARAM);
      }
      i++;
    }
    paramMap.put(AlfrescoConfig.LUCENE_QUERY_PARAM, luceneQuery);
  }

  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the document
  * specification information to the user.  The coder can presume that the HTML that is output from
  * this configuration will be within appropriate <html> and <body> tags.
  * The connector will be connected before this method can be called.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber)
    throws ManifoldCFException, IOException {
    Map<String,String> paramMap = new HashMap<String,String>();
        
    // Fill in parameters from all tabs
    fillInLuceneQueryParameters(paramMap, ds);
    paramMap.put(SEQ_NUM_PARAM, Integer.toString(connectionSequenceNumber));

    outputResource(VIEW_SPEC_FORWARD, out, locale, paramMap);
  }

  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form
  * data for a connection has been posted.  Its purpose is to gather form information and modify the
  * document specification accordingly.  The name of the posted form is always "editjob".
  * The connector will be connected before this method can be called.
  *@param variableContext contains the post data, including binary file-upload information.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of
  * the job (and cause a redirection to an error page).
  */
  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, Specification ds,
    int connectionSequenceNumber)
    throws ManifoldCFException {
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    String luceneQuery = variableContext.getParameter(seqPrefix + AlfrescoConfig.LUCENE_QUERY_PARAM);
    if (luceneQuery != null) {
      int i = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode oldNode = ds.getChild(i);
        if (oldNode.getType().equals(JOB_STARTPOINT_NODE_TYPE)) {
          ds.removeChild(i);
        }
        else
          i++;
      }
      SpecificationNode node = new SpecificationNode(JOB_STARTPOINT_NODE_TYPE);
      node.setAttribute(AlfrescoConfig.LUCENE_QUERY_PARAM, luceneQuery);
      ds.addChild(ds.getChildCount(), node);
    }

    return null;
  }

  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected a repository connection of the
  * current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate
  *  <html>, <body>, and <form> tags.  The name of the form is always "editjob".
  * The connector will be connected before this method can be called.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param actualSequenceNumber is the connection within the job that has currently been selected.
  *@param tabName is the current tab name.  (actualSequenceNumber, tabName) form a unique tuple within
  *  the job.
  */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber, int actualSequenceNumber, String tabName)
    throws ManifoldCFException, IOException {
    
    // Do all tabs in turn.
        
    // LuceneQuery tab
    Map<String,String> paramMap = new HashMap<String,String>();
    paramMap.put(TAB_NAME_PARAM, tabName);
    paramMap.put(SEQ_NUM_PARAM, Integer.toString(connectionSequenceNumber));
    paramMap.put(SELECTED_NUM_PARAM, Integer.toString(actualSequenceNumber));

    fillInLuceneQueryParameters(paramMap, ds);
    outputResource(EDIT_SPEC_FORWARD_LUCENEQUERY, out, locale, paramMap);
  }

  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected a repository connection of the
  * current type.  Its purpose is to add the required tabs to the list, and to output any javascript methods
  * that might be needed by the job editing HTML.
  * The connector will be connected before this method can be called.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber, List<String> tabsArray)
    throws ManifoldCFException, IOException {
    // Add LuceneQuery tab
    tabsArray.add(Messages.getString(locale,TAB_LABEL_LUCENE_QUERY_RESOURCE));
        
    // Fill in parameters from all tabs
    Map<String,String> paramMap = new HashMap<String,String>();
    paramMap.put(SEQ_NUM_PARAM, Integer.toString(connectionSequenceNumber));

    // LuceneQuery tab
    fillInLuceneQueryParameters(paramMap, ds);

    outputResource(EDIT_SPEC_HEADER_FORWARD, out, locale, paramMap);
  }

  /** Process a set of documents.
  * This is the method that should cause each document to be fetched, processed, and the results either added
  * to the queue of documents for the current job, and/or entered into the incremental ingestion manager.
  * The document specification allows this class to filter what is done based on the job.
  * The connector will be connected before this method can be called.
  *@param documentIdentifiers is the set of document identifiers to process.
  *@param statuses are the currently-stored document versions for each document in the set of document identifiers
  * passed in above.
  *@param activities is the interface this method should use to queue up new document references
  * and ingest documents.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@param usesDefaultAuthority will be true only if the authority in use for these documents is the default one.
  */
  @Override
  public void processDocuments(String[] documentIdentifiers, IExistingVersions statuses, Specification spec,
    IProcessActivity activities, int jobMode, boolean usesDefaultAuthority)
    throws ManifoldCFException, ServiceInterruption {
      
    for (String documentIdentifier : documentIdentifiers) {
      // Prepare to access the document
      String nodeReference = documentIdentifier;
      String uuid = NodeUtils.getUuidFromNodeReference(nodeReference);
      
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("Alfresco: Processing document identifier '"
            + nodeReference + "'");

      Reference reference = new Reference();
      reference.setStore(SearchUtils.STORE);
      reference.setUuid(uuid);
      
      Predicate predicate = new Predicate();
      predicate.setStore(SearchUtils.STORE);
      predicate.setNodes(new Reference[]{reference});
      
      Node resultNode = null;
      try {
        resultNode = NodeUtils.get(endpoint, username, password, socketTimeout, session, predicate);
      } catch (IOException e) {
        Logging.connectors.warn(
            "Alfresco: IOException getting node: "
                + e.getMessage(), e);
        handleIOException(e);
      }
      
      NamedValue[] properties = resultNode.getProperties();
      boolean isDocument;
      String versionString = "";
      if (properties != null)
        isDocument = ContentModelUtils.isDocument(properties);
      else
        isDocument = false;
      if (isDocument){
        boolean isVersioned = NodeUtils.isVersioned(resultNode.getAspects());
        if(isVersioned){
          versionString = NodeUtils.getVersionLabel(properties);
        }
      }

      if (versionString.length() == 0 || activities.checkDocumentNeedsReindexing(documentIdentifier,versionString)) {
        // Need to (re)index
      
        String errorCode = "OK";
        String errorDesc = StringUtils.EMPTY;
        Long fileLengthLong = null;
        
        long startTime = System.currentTimeMillis();
        
        try{    
          
          try{
            boolean isFolder = ContentModelUtils.isFolder(endpoint, username, password, socketTimeout, session, reference);
            
            //a generic node in Alfresco could have child-associations
            if (isFolder) {
              // queue all the children of the folder
              QueryResult queryResult = SearchUtils.getChildren(endpoint, username, password, socketTimeout, session, reference);
              ResultSet resultSet = queryResult.getResultSet();
              ResultSetRow[] resultSetRows = resultSet.getRows();
              for (ResultSetRow resultSetRow : resultSetRows) {
                NamedValue[] childProperties = resultSetRow.getColumns();
                String childNodeReference = PropertiesUtils.getNodeReference(childProperties);
                activities.addDocumentReference(childNodeReference, nodeReference, RELATIONSHIP_CHILD);
              }
            } 

          }catch(IOException e){
            errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
            errorDesc = e.getMessage();
            Logging.connectors.warn(
                "Alfresco: IOException finding children: "
                    + e.getMessage(), e);
            handleIOException(e);
          }
        
          //a generic node in Alfresco could also have binaries content
          if (isDocument) {
            // this is a content to ingest
            InputStream is = null;
            long fileLength = 0;
            try {
              //properties ingestion
              RepositoryDocument rd = new RepositoryDocument();      
              List<NamedValue> contentProperties = PropertiesUtils.getContentProperties(properties);
              PropertiesUtils.ingestProperties(rd, properties, contentProperties);

              // binaries ingestion - in Alfresco we could have more than one binary for each node (custom content models)
              for (NamedValue contentProperty : contentProperties) {
                //we are ingesting all the binaries defined as d:content property in the Alfresco content model
                Content binary = ContentReader.read(endpoint, username, password, socketTimeout, session, predicate, contentProperty.getName());
                fileLength = binary.getLength();
                is = ContentReader.getBinary(endpoint, binary, username, password, socketTimeout, session);
                rd.setBinary(is, fileLength);
                  
                //id is the node reference only if the node has an unique content stream
                //For a node with a single d:content property: id = node reference
                String id = PropertiesUtils.getNodeReference(properties);
                  
                //For a node with multiple d:content properties: id = node reference;QName
                //The QName of a property of type d:content will be appended to the node reference
                if(contentProperties.size()>1){
                  id = id + INGESTION_SEPARATOR_FOR_MULTI_BINARY + contentProperty.getName();
                }
                  
                //the document uri is related to the specific d:content property available in the node
                //we want to ingest each content stream that are nested in a single node
                String documentURI = binary.getUrl();
                activities.ingestDocumentWithException(documentIdentifier, id, versionString, documentURI, rd);
                fileLengthLong = new Long(fileLength);
              }
                
              AuthenticationUtils.endSession();
            
            } catch (ParseException e) {
              errorCode = "PARSEEXCEPTION";
              errorDesc = e.getMessage();
              Logging.connectors.warn(
                  "Alfresco: Error during the reading process of dates: "
                      + e.getMessage(), e);
              handleParseException(e);
            } catch (IOException e) {
              errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
              errorDesc = e.getMessage();
              Logging.connectors.warn(
                  "Alfresco: IOException: "
                      + e.getMessage(), e);
              handleIOException(e);
            } finally {
              session = null;
              try {
                if(is!=null){
                  is.close();
                }
              } catch (InterruptedIOException e) {
                errorCode = null;
                throw new ManifoldCFException(e.getMessage(), e,
                    ManifoldCFException.INTERRUPTED);
              } catch (IOException e) {
                errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
                errorDesc = e.getMessage();
                Logging.connectors.warn(
                    "Alfresco: IOException closing file input stream: "
                        + e.getMessage(), e);
                handleIOException(e);
              }
            }

          }
        } catch (ManifoldCFException e) {
          if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
            errorCode = null;
          throw e;
        } finally {
          if (errorCode != null)
            activities.recordActivity(new Long(startTime), ACTIVITY_READ,
              fileLengthLong, nodeReference, errorCode, errorDesc, null);
        }
      }
    }
    
  }
  
  private static void handleIOException(IOException e)
      throws ManifoldCFException, ServiceInterruption {
      if (!(e instanceof java.net.SocketTimeoutException) && (e instanceof InterruptedIOException)) {
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
          ManifoldCFException.INTERRUPTED);
      }
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("IO exception: "+e.getMessage(), e, currentTime + 300000L,
        currentTime + 3 * 60 * 60000L,-1,false);
  }
  
  private void handleParseException(ParseException e) 
      throws ManifoldCFException {
    throw new ManifoldCFException(
        "Alfresco: Error during parsing date values. This should never happen: "+e.getMessage(),e);
  }

}
