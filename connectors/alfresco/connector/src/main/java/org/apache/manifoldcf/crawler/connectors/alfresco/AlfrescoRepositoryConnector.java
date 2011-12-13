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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.List;

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
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector;
import org.apache.manifoldcf.crawler.interfaces.DocumentSpecification;
import org.apache.manifoldcf.crawler.interfaces.IProcessActivity;
import org.apache.manifoldcf.crawler.interfaces.ISeedingActivity;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.ui.util.Encoder;

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
  
  protected AuthenticationDetails session = null;

  protected static final long timeToRelease = 300000L;
  protected long lastSessionFetch = -1L;

  protected static final String RELATIONSHIP_CHILD = "child";

  /** Forward to the HTML template to view the configuration parameters */
  private static final String VIEW_CONFIG_FORWARD = "viewConfiguration.html";
  
  /** Forward to the HTML template to edit the configuration parameters */
  private static final String EDIT_CONFIG_FORWARD = "editConfiguration.html";
  
  /** Forward to the javascript to check the configuration parameters */
  private static final String EDIT_CONFIG_HEADER_FORWARD = "editConfiguration.js";

  /** Forward to the template to view the specification parameters for the job */
  private static final String VIEW_SPEC_FORWARD = "viewSpecification.html";
  
  /** Forward to the template to edit the configuration parameters for the job */
  private static final String EDIT_SPEC_FORWARD = "editSpecification.html";
  
  /** Forward to the javascript to check the specification parameters for the job */
  private static final String EDIT_SPEC_HEADER_FORWARD = "editSpecification.js";
  
  /** Forward to the HTML template for rendering hidden fields when the Server tab is not selected */
  private static final String HIDDEN_CONFIG_FORWARD = "hiddenConfiguration.html";
  
  /** Forward to the HTML template for rendering hidden fields when the Lucene Query tab is not selected */
  private static final String HIDDEN_SPEC_FORWARD = "hiddenSpecification.html";

  /** The root node for the Alfresco connector configuration in ManifoldCF */
  private static final String JOB_STARTPOINT_NODE_TYPE = "startpoint";

  /** The Lucene Query label for the configuration tab of the job settings */
  private static final String TAB_LABEL_LUCENE_QUERY = "Lucene Query";

  /** Read activity */
  protected final static String ACTIVITY_READ = "read document";
  
  /** Alfresco Server configuration tab name */
  private static final String ALFRESCO_SERVER_TAB_NAME = "Server";

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
    return new String[] { protocol+"://"+server+":"+port+path };
  }

  /** 
   * Close the connection.  Call this before discarding the connection.
   */
  @Override
  public void disconnect() throws ManifoldCFException {
    if (session != null) {
      AuthenticationUtils.endSession();
      session = null;
      lastSessionFetch = -1L;
    }

    username = null;
    password = null;
    protocol = null;
    server = null;
    port = null;
    path = null;

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
    
      
    String endpoint = protocol+"://"+server+":"+port+path;
    WebServiceFactory.setEndpointAddress(endpoint);
    try {
      AuthenticationUtils.startSession(username, password);
      session = AuthenticationUtils.getAuthenticationDetails();
    } catch (AuthenticationFault e) {
      Logging.connectors.warn(
          "Alfresco: Error during authentication. Username: "+username + ", endpoint: "+endpoint
              + e.getMessage(), e);
      throw new ManifoldCFException("Alfresco: Error during authentication. Username: "+username + ", endpoint: "+endpoint
          + e.getMessage(), e);
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
        AuthenticationUtils.endSession();
        session = null;
        lastSessionFetch = -1L;
    }
  }

  protected void checkConnection() throws ManifoldCFException,
      ServiceInterruption {
    while (true) {
      getSession();
      String ticket = AuthenticationUtils.getTicket();
      if(StringUtils.isEmpty(ticket)){
        Logging.connectors.error(
            "Alfresco: Error during checking the connection.");
        throw new ManifoldCFException( "Alfresco: Error during checking the connection.");
      }
      AuthenticationUtils.endSession();
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
   * The times passed to this method may be interpreted for greatest efficiency.  The time ranges
   * any given job uses with this connector will not overlap, but will proceed starting at 0 and going
   * to the "current time", each time the job is run.  For continuous crawling jobs, this method will
   * be called once, when the job starts, and at various periodic intervals as the job executes.
   *
   * When a job's specification is changed, the framework automatically resets the seeding start time to 0.  The
   * seeding start time may also be set to 0 on each job run, depending on the connector model returned by
   * getConnectorModel().
   *
   * Note that it is always ok to send MORE documents rather than less to this method.
   *@param activities is the interface this method should use to perform whatever framework actions are desired.
   *@param spec is a document specification (that comes from the job).
   *@param startTime is the beginning of the time range to consider, inclusive.
   *@param endTime is the end of the time range to consider, exclusive.
   *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
   */
  @Override
  public void addSeedDocuments(ISeedingActivity activities,
      DocumentSpecification spec, long startTime, long endTime)
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

    QueryResult queryResult = null;
    if (StringUtils.isEmpty(luceneQuery)) {
      // get documents from the root of the Alfresco Repository
      queryResult = SearchUtils.getChildrenFromCompanyHome(username, password, session);
    } else {
      // execute a Lucene query against the repository
      queryResult = SearchUtils.luceneSearch(username, password, session, luceneQuery);
    }

    if(queryResult!=null){
      ResultSet resultSet = queryResult.getResultSet();
      ResultSetRow[] resultSetRows = resultSet.getRows();
      for (ResultSetRow resultSetRow : resultSetRows) {
        activities.addSeedDocument(resultSetRow.getNode().getId());
      }
    }

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
  private void outputResource(String resName, IHTTPOutput out,
      ConfigParams params) throws ManifoldCFException {
    InputStream is = getClass().getResourceAsStream(resName);
    BufferedReader br = null;
    try {
      br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
      String line;
      while ((line = br.readLine()) != null) {
        if (params != null) {
          @SuppressWarnings("unchecked")
          Iterator<String> i = params.listParameters();
          boolean parsedLine = false;
          while (i.hasNext()) {
            String key = (String) i.next();
            String value = Encoder.attributeEscape(params.getParameter(key));
            String replacer = "${" + key.toUpperCase() + "}";
            if (StringUtils.contains(line, replacer)) {
              if (StringUtils.isEmpty(value)) {
                out.println(StringUtils.replace(line, replacer,
                    StringUtils.EMPTY));
                parsedLine = true;
              } else {
                out.println(StringUtils.replace(line, replacer, value));
                parsedLine = true;
              }
            } else if (StringUtils.contains(line, "${")) {
              parsedLine = true;
            } else if (!parsedLine) {
              out.println(line);
              parsedLine = true;
            }
          }
        } else {
          break;
        }
      }
    } catch (UnsupportedEncodingException e) {
      throw new ManifoldCFException(e);
    } catch (IOException e) {
      throw new ManifoldCFException(e);
    } finally {
      if (br != null)
        IOUtils.closeQuietly(br);
      if (is != null)
        IOUtils.closeQuietly(is);
    }
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
      ConfigParams parameters) throws ManifoldCFException, IOException {
    
    String username = parameters.getParameter(AlfrescoConfig.USERNAME_PARAM);
    if (StringUtils.isEmpty(username)) {
      parameters.setParameter(AlfrescoConfig.USERNAME_PARAM, AlfrescoConfig.USERNAME_DEFAULT_VALUE);
    }

    String password = parameters.getParameter(AlfrescoConfig.PASSWORD_PARAM);
    if (StringUtils.isEmpty(password)) {
      parameters.setParameter(AlfrescoConfig.PASSWORD_PARAM, AlfrescoConfig.PASSWORD_DEFAULT_VALUE);
    }
    
    String protocol = parameters.getParameter(AlfrescoConfig.PROTOCOL_PARAM);
    if (StringUtils.isEmpty(protocol)) {
      parameters.setParameter(AlfrescoConfig.PROTOCOL_PARAM, AlfrescoConfig.PROTOCOL_DEFAULT_VALUE);
    }
    
    String server = parameters.getParameter(AlfrescoConfig.SERVER_PARAM);
    if (StringUtils.isEmpty(server)) {
      parameters.setParameter(AlfrescoConfig.SERVER_PARAM, AlfrescoConfig.SERVER_DEFAULT_VALUE);
    }
    
    String port = parameters.getParameter(AlfrescoConfig.PORT_PARAM);
    if (StringUtils.isEmpty(port)) {
      parameters.setParameter(AlfrescoConfig.PORT_PARAM, AlfrescoConfig.PORT_DEFAULT_VALUE);
    }
    
    String path = parameters.getParameter(AlfrescoConfig.PATH_PARAM);
    if (StringUtils.isEmpty(path)) {
      parameters.setParameter(AlfrescoConfig.PATH_PARAM, AlfrescoConfig.PATH_DEFAULT_VALUE);
    }
    
    outputResource(VIEW_CONFIG_FORWARD, out, parameters);
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
      IHTTPOutput out, ConfigParams parameters, List<String> tabsArray)
      throws ManifoldCFException, IOException {
    tabsArray.add(ALFRESCO_SERVER_TAB_NAME);
    outputResource(EDIT_CONFIG_HEADER_FORWARD, out, parameters);
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
      IHTTPOutput out, ConfigParams parameters, String tabName)
      throws ManifoldCFException, IOException {
    
    if(ALFRESCO_SERVER_TAB_NAME.equals(tabName)){
      String username = parameters.getParameter(AlfrescoConfig.USERNAME_PARAM);
      String password = parameters.getParameter(AlfrescoConfig.PASSWORD_PARAM);
      String protocol = parameters.getParameter(AlfrescoConfig.PROTOCOL_PARAM);
      String server = parameters.getParameter(AlfrescoConfig.SERVER_PARAM);
      String port = parameters.getParameter(AlfrescoConfig.PORT_PARAM);
      String path = parameters.getParameter(AlfrescoConfig.PATH_PARAM);
      
      if (StringUtils.isEmpty(username))
        username = StringUtils.EMPTY;
      if (StringUtils.isEmpty(password))
        password = StringUtils.EMPTY;
      if (StringUtils.isEmpty(protocol))
        protocol = StringUtils.EMPTY;
      if (StringUtils.isEmpty(server))
        server = StringUtils.EMPTY;
      if (StringUtils.isEmpty(port))
        port = StringUtils.EMPTY;
      if (StringUtils.isEmpty(path))
        path = StringUtils.EMPTY;
  
      parameters.setParameter(AlfrescoConfig.USERNAME_PARAM, username);
      parameters.setParameter(AlfrescoConfig.PASSWORD_PARAM, password);
      parameters.setParameter(AlfrescoConfig.PROTOCOL_PARAM, protocol);
      parameters.setParameter(AlfrescoConfig.SERVER_PARAM, server);
      parameters.setParameter(AlfrescoConfig.PORT_PARAM, port);
      parameters.setParameter(AlfrescoConfig.PATH_PARAM, path);
      
      outputResource(EDIT_CONFIG_FORWARD, out, parameters);  
    } else {
      outputResource(HIDDEN_CONFIG_FORWARD, out, parameters);
    }
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
      IPostParameters variableContext, ConfigParams parameters)
      throws ManifoldCFException {

    String username = variableContext.getParameter(AlfrescoConfig.USERNAME_PARAM);
    if (StringUtils.isNotEmpty(username)) {
      parameters.setParameter(AlfrescoConfig.USERNAME_PARAM, username);
    }

    String password = variableContext.getParameter(AlfrescoConfig.PASSWORD_PARAM);
    if (StringUtils.isNotEmpty(password)) {
      parameters.setParameter(AlfrescoConfig.PASSWORD_PARAM, password);
    }
    
    String protocol = variableContext.getParameter(AlfrescoConfig.PROTOCOL_PARAM);
    if (StringUtils.isNotEmpty(protocol)) {
      parameters.setParameter(AlfrescoConfig.PROTOCOL_PARAM, protocol);
    }
    
    String server = variableContext.getParameter(AlfrescoConfig.SERVER_PARAM);
    if (StringUtils.isNotEmpty(server) && !StringUtils.contains(server, '/')) {
      parameters.setParameter(AlfrescoConfig.SERVER_PARAM, server);
    }
    
    String port = variableContext.getParameter(AlfrescoConfig.PORT_PARAM);
    if (StringUtils.isNotEmpty(port)){
      try {
        Integer.parseInt(port);
        parameters.setParameter(AlfrescoConfig.PORT_PARAM, port);
      } catch (NumberFormatException e) {
        
      }
    }
    
    String path = variableContext.getParameter(AlfrescoConfig.PATH_PARAM);
    if (StringUtils.isNotEmpty(path)) {
      parameters.setParameter(AlfrescoConfig.PATH_PARAM, path);
    }
    return null;
  }

  /**
   * View specification. This method is called in the body section of a job's
   * view page. Its purpose is to present the document specification information
   * to the user. The coder can presume that the HTML that is output from this
   * configuration will be within appropriate <html> and <body> tags.
   * 
   * @param out
   *          is the output to which any HTML should be sent.
   * @param ds
   *          is the current document specification for this job.
   */
  @Override
  public void viewSpecification(IHTTPOutput out, DocumentSpecification ds)
      throws ManifoldCFException, IOException {
    int i = 0;
    boolean seenAny = false;
    ConfigParams specificationParams = new ConfigParams();
    while (i < ds.getChildCount()) {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals(JOB_STARTPOINT_NODE_TYPE)) {
        if (seenAny == false) {
          seenAny = true;
        }
        specificationParams.setParameter(
            AlfrescoConfig.LUCENE_QUERY_PARAM.toUpperCase(),
            sn.getAttributeValue(AlfrescoConfig.LUCENE_QUERY_PARAM));
      }
      i++;
    }
    outputResource(VIEW_SPEC_FORWARD, out, specificationParams);
  }

  /**
   * Process a specification post. This method is called at the start of job's
   * edit or view page, whenever there is a possibility that form data for a
   * connection has been posted. Its purpose is to gather form information and
   * modify the document specification accordingly. The name of the posted form
   * is "editjob".
   * 
   * @param variableContext
   *          contains the post data, including binary file-upload information.
   * @param ds
   *          is the current document specification for this job.
   * @return null if all is well, or a string error message if there is an error
   *         that should prevent saving of the job (and cause a redirection to
   *         an error page).
   */
  @Override
  public String processSpecificationPost(IPostParameters variableContext,
      DocumentSpecification ds) throws ManifoldCFException {
    String luceneQuery = variableContext
        .getParameter(AlfrescoConfig.LUCENE_QUERY_PARAM);
    if (StringUtils.isNotEmpty(luceneQuery)) {
      int i = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode oldNode = ds.getChild(i);
        if (oldNode.getType().equals(JOB_STARTPOINT_NODE_TYPE)) {
          ds.removeChild(i);
          break;
        }
        i++;
      }
      SpecificationNode node = new SpecificationNode(JOB_STARTPOINT_NODE_TYPE);
      node.setAttribute(AlfrescoConfig.LUCENE_QUERY_PARAM, luceneQuery);
      variableContext.setParameter(AlfrescoConfig.LUCENE_QUERY_PARAM, luceneQuery);
      ds.addChild(ds.getChildCount(), node);
    } else {
      int i = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode oldNode = ds.getChild(i);
        if (oldNode.getType().equals(JOB_STARTPOINT_NODE_TYPE)) {
          variableContext.setParameter(AlfrescoConfig.LUCENE_QUERY_PARAM,
              oldNode.getAttributeValue(AlfrescoConfig.LUCENE_QUERY_PARAM));
        }
        i++;
      }
    }

    return null;
  }

  /**
   * Output the specification body section. This method is called in the body
   * section of a job page which has selected a repository connection of the
   * current type. Its purpose is to present the required form elements for
   * editing. The coder can presume that the HTML that is output from this
   * configuration will be within appropriate <html>, <body>, and <form> tags.
   * The name of the form is "editjob".
   * 
   * @param out
   *          is the output to which any HTML should be sent.
   * @param ds
   *          is the current document specification for this job.
   * @param tabName
   *          is the current tab name.
   */
  @Override
  public void outputSpecificationBody(IHTTPOutput out,
      DocumentSpecification ds, String tabName) throws ManifoldCFException,
      IOException {
    String luceneQuery = StringUtils.EMPTY;
    int i = 0;
    while (i < ds.getChildCount()) {
      SpecificationNode n = ds.getChild(i);
      if (n.getType().equals(JOB_STARTPOINT_NODE_TYPE)) {
        luceneQuery = n.getAttributeValue(AlfrescoConfig.LUCENE_QUERY_PARAM);
        break;
      }
      i++;
    }
    
    ConfigParams params = new ConfigParams();
    params.setParameter(AlfrescoConfig.LUCENE_QUERY_PARAM, luceneQuery);
    if (tabName.equals(TAB_LABEL_LUCENE_QUERY)) {
      outputResource(EDIT_SPEC_FORWARD, out, params);
    } else {
      outputResource(HIDDEN_SPEC_FORWARD, out, params);
    }
  }

  /**
   * Output the specification header section. This method is called in the head
   * section of a job page which has selected a repository connection of the
   * current type. Its purpose is to add the required tabs to the list, and to
   * output any javascript methods that might be needed by the job editing HTML.
   * 
   * @param out
   *          is the output to which any HTML should be sent.
   * @param ds
   *          is the current document specification for this job.
   * @param tabsArray
   *          is an array of tab names. Add to this array any tab names that are
   *          specific to the connector.
   */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out,
      DocumentSpecification ds, List<String> tabsArray)
      throws ManifoldCFException, IOException {
    tabsArray.add(TAB_LABEL_LUCENE_QUERY);
    outputResource(EDIT_SPEC_HEADER_FORWARD, out, params);
  }

  /** Process a set of documents.
   * This is the method that should cause each document to be fetched, processed, and the results either added
   * to the queue of documents for the current job, and/or entered into the incremental ingestion manager.
   * The document specification allows this class to filter what is done based on the job.
   *@param documentIdentifiers is the set of document identifiers to process.
   *@param versions is the corresponding document versions to process, as returned by getDocumentVersions() above.
   *       The implementation may choose to ignore this parameter and always process the current version.
   *@param activities is the interface this method should use to queue up new document references
   * and ingest documents.
   *@param spec is the document specification.
   *@param scanOnly is an array corresponding to the document identifiers.  It is set to true to indicate when the processing
   * should only find other references, and should not actually call the ingestion methods.
   *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
   */
  @Override
  public void processDocuments(String[] documentIdentifiers, String[] versions,
      IProcessActivity activities, DocumentSpecification spec,
      boolean[] scanOnly) throws ManifoldCFException, ServiceInterruption {

    Logging.connectors.debug("Alfresco: Inside processDocuments");
    int i = 0;

    while (i < documentIdentifiers.length) {
      long startTime = System.currentTimeMillis();
      String nodeId = documentIdentifiers[i];

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("Alfresco: Processing document identifier '"
            + nodeId + "'");

      Reference reference = new Reference();
      reference.setStore(SearchUtils.STORE);
      reference.setUuid(nodeId);

      Predicate predicate = new Predicate();
      predicate.setStore(SearchUtils.STORE);
      predicate.setNodes(new Reference[] { reference });

      // getting properties
      Node resultNode = NodeUtils.get(username, password, session, predicate);
      
      String errorCode = "OK";
      String errorDesc = StringUtils.EMPTY;

      NamedValue[] properties = resultNode.getProperties();
      boolean isDocument = ContentModelUtils.isDocument(properties);
      
      boolean isFolder = ContentModelUtils.isFolder(username, password, session, reference);
      
      //a generic node in Alfresco could have child-associations
      if (isFolder) {
        // ingest all the children of the folder
        QueryResult queryResult = SearchUtils.getChildren(username, password, session, reference);
        ResultSet resultSet = queryResult.getResultSet();
        ResultSetRow[] resultSetRows = resultSet.getRows();
        for (ResultSetRow resultSetRow : resultSetRows) {
          String childNodeId = resultSetRow.getNode().getId();
          activities.addDocumentReference(childNodeId, nodeId,
              RELATIONSHIP_CHILD);
        }
      } 
      
      //a generic node in Alfresco could also have binaries content
      if (isDocument) {
        // this is a content to ingest
        InputStream is = null;
        long fileLength = 0;
        try {
          //properties ingestion
          RepositoryDocument rd = new RepositoryDocument();
          PropertiesUtils.ingestProperties(rd, properties);

          // binaries ingestion - in Alfresco we could have more than one binary for each node (custom content models)
          List<NamedValue> contentProperties = PropertiesUtils.getContentProperties(properties);
          for (NamedValue contentProperty : contentProperties) {
            //we are ingesting all the binaries defined as d:content property in the Alfresco content model
            Content binary = ContentReader.read(username, password, session, predicate, contentProperty.getName());
            fileLength = binary.getLength();
            is = ContentReader.getBinary(binary, username, password, session);
            rd.setBinary(is, fileLength);
          }

        } finally {
          try {
            if(is!=null){
              is.close();
            }
          } catch (InterruptedIOException e) {
            errorCode = "Interrupted error";
            errorDesc = e.getMessage();
            throw new ManifoldCFException(e.getMessage(), e,
                ManifoldCFException.INTERRUPTED);
          } catch (IOException e) {
            errorCode = "IO ERROR";
            errorDesc = e.getMessage();
            Logging.connectors.warn(
                "Alfresco: IOException closing file input stream: "
                    + e.getMessage(), e);
          }
          
          AuthenticationUtils.endSession();
          session = null;
          
          activities.recordActivity(new Long(startTime), ACTIVITY_READ,
              fileLength, nodeId, errorCode, errorDesc, null);
        }
        
      }
      i++;
    }
  }
  
  /** The short version of getDocumentVersions.
   * Get document versions given an array of document identifiers.
   * This method is called for EVERY document that is considered. It is
   * therefore important to perform as little work as possible here.
   *@param documentIdentifiers is the array of local document identifiers, as understood by this connector.
   *@param spec is the current document specification for the current job.  If there is a dependency on this
   * specification, then the version string should include the pertinent data, so that reingestion will occur
   * when the specification changes.  This is primarily useful for metadata.
   *@return the corresponding version strings, with null in the places where the document no longer exists.
   * Empty version strings indicate that there is no versioning ability for the corresponding document, and the document
   * will always be processed.
   */
  @Override
  public String[] getDocumentVersions(String[] documentIdentifiers,
      DocumentSpecification spec) throws ManifoldCFException,
      ServiceInterruption {
    String[] rval = new String[documentIdentifiers.length];
    int i = 0;
    while (i < rval.length){
      Reference reference = new Reference();
      reference.setStore(SearchUtils.STORE);
      reference.setUuid(documentIdentifiers[i]);
      
      Predicate predicate = new Predicate();
      predicate.setStore(SearchUtils.STORE);
      predicate.setNodes(new Reference[]{reference});
      
      Node node = NodeUtils.get(username, password, session, predicate);
      boolean isDocument = ContentModelUtils.isDocument(node.getProperties());
      if(isDocument){
        boolean isVersioned = NodeUtils.isVersioned(node.getAspects());
        if(isVersioned){
          rval[i] = NodeUtils.getVersionLabel(node.getProperties());
        } else {
          //a document that doesn't contain versioning information will always be processed
          rval[i] = StringUtils.EMPTY;
        }
      } else {
        //a space will always be processed
        rval[i] = StringUtils.EMPTY;
      }
      i++;
    }
    return rval;
  }

}
