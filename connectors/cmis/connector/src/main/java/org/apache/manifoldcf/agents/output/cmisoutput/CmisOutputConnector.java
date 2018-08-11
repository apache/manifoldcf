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
package org.apache.manifoldcf.agents.output.cmisoutput;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.ObjectId;
import org.apache.chemistry.opencmis.client.api.QueryResult;
import org.apache.chemistry.opencmis.client.api.Repository;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.api.SessionFactory;
import org.apache.chemistry.opencmis.client.runtime.ObjectIdImpl;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.apache.chemistry.opencmis.commons.exceptions.CmisConnectionException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisContentAlreadyExistsException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisNameConstraintViolationException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisPermissionDeniedException;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ContentStreamImpl;
import org.apache.chemistry.opencmis.commons.impl.jaxb.EnumBaseObjectTypeIds;
import org.apache.commons.lang.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputRemoveActivity;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.output.BaseOutputConnector;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPasswordMapperActivity;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.VersionContext;
import org.apache.manifoldcf.crawler.system.Logging;

/**
 * This is the "output connector" for a CMIS-compliant repository.
 *
 * @author Piergiorgio Lucidi
 */
public class CmisOutputConnector extends BaseOutputConnector {

  protected final static String ACTIVITY_READ = "read document";
  protected static final String RELATIONSHIP_CHILD = "child";

  // Tab name properties

  private static final String CMIS_SERVER_TAB_PROPERTY = "CmisOutputConnector.Server";

  // Template names

  /** Forward to the javascript to check the configuration parameters */
  private static final String EDIT_CONFIG_HEADER_FORWARD = "editConfiguration.js";

  /** Server tab template */
  private static final String EDIT_CONFIG_FORWARD_SERVER = "editConfiguration_Server.html";

  /** Forward to the HTML template to view the configuration parameters */
  private static final String VIEW_CONFIG_FORWARD = "viewConfiguration.html";

  /**
   * CMIS Session handle
   */
  Session session = null;

  protected String username = null;
  protected String password = null;

  /** Endpoint protocol */
  protected String protocol = null;

  /** Endpoint server name */
  protected String server = null;

  /** Endpoint port */
  protected String port = null;

  /** Endpoint context path of the Alfresco webapp */
  protected String path = null;

  protected String repositoryId = null;
  protected String binding = null;

  /** Target folder for the drop zone */
  protected String cmisQuery = null;
  
  /** Flag for creating the new tree structure using timestamp**/
  protected String createTimestampTree = Boolean.FALSE.toString();
  
  protected SessionFactory factory = SessionFactoryImpl.newInstance();
  protected Map<String, String> parameters = new HashMap<String, String>();

  protected static final long timeToRelease = 300000L;
  protected long lastSessionFetch = -1L;

  protected Folder parentDropZoneFolder = null;

  /** Save activity */
  protected final static String ACTIVITY_INJECTION = "Injection";

  /** Delete activity */
  protected final static String ACTIVITY_DELETE = "Delete";

  private static final String CMIS_PROPERTY_PREFIX = "cmis:";

  /** Document accepted */
  private final static int DOCUMENT_STATUS_ACCEPTED = 0;

  private static final String DOCUMENT_STATUS_ACCEPTED_DESC = "Injection OK - ";

  private static final String DOCUMENT_STATUS_REJECTED_DESC = "Injection KO - ";

  /** Document permanently rejected */
  private final static int DOCUMENT_STATUS_REJECTED = 1;

  /** Document remove accepted */
  private final static String DOCUMENT_DELETION_STATUS_ACCEPTED = "Remove request accepted";

  /** Document remove permanently rejected */
  private final static String DOCUMENT_DELETION_STATUS_REJECTED = "Remove request rejected";
  
  private static final String CONTENT_PATH_PARAM = "contentPath";
  
  /**
   * Constructor
   */
  public CmisOutputConnector() {
    super();
  }

  /**
   * Return the list of activities that this connector supports (i.e. writes
   * into the log).
   * 
   * @return the list.
   */
  @Override
  public String[] getActivitiesList() {
    return new String[] { ACTIVITY_INJECTION, ACTIVITY_DELETE };
  }

  protected class GetSessionThread extends Thread {
    protected Throwable exception = null;

    public GetSessionThread() {
      super();
      setDaemon(true);
    }

    public void run() {
      try {
        // Create a session
        parameters.clear();

        // user credentials
        parameters.put(SessionParameter.USER, username);
        parameters.put(SessionParameter.PASSWORD, password);

        String endpoint = protocol + "://" + server + ":" + port + path;

        // connection settings
        if (CmisOutputConfig.BINDING_ATOM_VALUE.equals(binding)) {
          // AtomPub protocol
          parameters.put(SessionParameter.ATOMPUB_URL, endpoint);
          parameters.put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());
        } else if (CmisOutputConfig.BINDING_WS_VALUE.equals(binding)) {
          // Web Services - SOAP - protocol
          parameters.put(SessionParameter.BINDING_TYPE, BindingType.WEBSERVICES.value());
          parameters.put(SessionParameter.WEBSERVICES_ACL_SERVICE, endpoint + "/ACLService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_DISCOVERY_SERVICE, endpoint + "/DiscoveryService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_MULTIFILING_SERVICE, endpoint + "/MultiFilingService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_NAVIGATION_SERVICE, endpoint + "/NavigationService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_OBJECT_SERVICE, endpoint + "/ObjectService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_POLICY_SERVICE, endpoint + "/PolicyService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_RELATIONSHIP_SERVICE, endpoint + "/RelationshipService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_REPOSITORY_SERVICE, endpoint + "/RepositoryService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_VERSIONING_SERVICE, endpoint + "/VersioningService?wsdl");
        }
        // create session
        if (StringUtils.isEmpty(repositoryId)) {

          // get a session from the first CMIS repository exposed by
          // the endpoint
          List<Repository> repos = null;
          try {
            repos = factory.getRepositories(parameters);
            session = repos.get(0).createSession();
          } catch (Exception e) {
            Logging.connectors.error(
                "CMIS: Error during getting CMIS repositories. Please check the endpoint parameters: " + e.getMessage(),
                e);
            this.exception = e;
          }

        } else {

          // get a session from the repository specified in the
          // configuration with its own ID
          parameters.put(SessionParameter.REPOSITORY_ID, repositoryId);

          try {
            session = factory.createSession(parameters);
          } catch (Exception e) {
            Logging.connectors
                .error("CMIS: Error during the creation of the new session. Please check the endpoint parameters: "
                    + e.getMessage(), e);
            this.exception = e;
          }

        }

      } catch (Throwable e) {
        this.exception = e;
      }
    }

    public Throwable getException() {
      return exception;
    }
  }

  protected class CheckConnectionThread extends Thread {
    protected Throwable exception = null;

    public CheckConnectionThread() {
      super();
      setDaemon(true);
    }

    public void run() {
      try {
        session.getRepositoryInfo();
      } catch (Throwable e) {
        Logging.connectors.warn("CMIS: Error checking repository: " + e.getMessage(), e);
        this.exception = e;
      }
    }

    public Throwable getException() {
      return exception;
    }

  }

  protected class DestroySessionThread extends Thread {
    protected Throwable exception = null;

    public DestroySessionThread() {
      super();
      setDaemon(true);
    }

    public void run() {
      try {
        session = null;
      } catch (Throwable e) {
        this.exception = e;
      }
    }

    public Throwable getException() {
      return exception;
    }

  }

  /**
   * Close the connection. Call this before discarding the connection.
   */
  @Override
  public void disconnect() throws ManifoldCFException {
    if (session != null) {
      DestroySessionThread t = new DestroySessionThread();
      try {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null) {
          if (thr instanceof RemoteException)
            throw (RemoteException) thr;
          else
            throw (Error) thr;
        }
        session = null;
        lastSessionFetch = -1L;
      } catch (InterruptedException e) {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
      } catch (RemoteException e) {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(), e2, ManifoldCFException.INTERRUPTED);
        session = null;
        lastSessionFetch = -1L;
        // Treat this as a transient problem
        Logging.connectors.warn("CMIS: Transient remote exception closing session: " + e.getMessage(), e);
      }

    }

    username = null;
    password = null;
    protocol = null;
    server = null;
    port = null;
    path = null;
    binding = null;
    repositoryId = null;
    cmisQuery = null;
    createTimestampTree = Boolean.FALSE.toString();

  }

  /**
   * This method create a new CMIS session for a CMIS repository, if the
   * repositoryId is not provided in the configuration, the connector will
   * retrieve all the repositories exposed for this endpoint the it will start
   * to use the first one.
   * 
   * @param configParameters
   *          is the set of configuration parameters, which in this case
   *          describe the target appliance, basic auth configuration, etc.
   *          (This formerly came out of the ini file.)
   */
  @Override
  public void connect(ConfigParams configParams) {
    super.connect(configParams);
    username = params.getParameter(CmisOutputConfig.USERNAME_PARAM);
    password = params.getParameter(CmisOutputConfig.PASSWORD_PARAM);
    protocol = params.getParameter(CmisOutputConfig.PROTOCOL_PARAM);
    server = params.getParameter(CmisOutputConfig.SERVER_PARAM);
    port = params.getParameter(CmisOutputConfig.PORT_PARAM);
    path = params.getParameter(CmisOutputConfig.PATH_PARAM);
    
    binding = params.getParameter(CmisOutputConfig.BINDING_PARAM);
    cmisQuery = params.getParameter(CmisOutputConfig.CMIS_QUERY_PARAM);
    createTimestampTree = params.getParameter(CmisOutputConfig.CREATE_TIMESTAMP_TREE_PARAM);
    
    if (StringUtils.isNotEmpty(params.getParameter(CmisOutputConfig.REPOSITORY_ID_PARAM))) {
      repositoryId = params.getParameter(CmisOutputConfig.REPOSITORY_ID_PARAM);
    }
    
  }

  /**
   * Test the connection. Returns a string describing the connection integrity.
   * 
   * @return the connection's status as a displayable string.
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

      if (StringUtils.isEmpty(binding))
        throw new ManifoldCFException("Parameter " + CmisOutputConfig.BINDING_PARAM + " required but not set");

      if (StringUtils.isEmpty(username))
        throw new ManifoldCFException("Parameter " + CmisOutputConfig.USERNAME_PARAM + " required but not set");

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("CMIS: Username = '" + username + "'");

      if (StringUtils.isEmpty(password))
        throw new ManifoldCFException("Parameter " + CmisOutputConfig.PASSWORD_PARAM + " required but not set");

      Logging.connectors.debug("CMIS: Password exists");

      if (StringUtils.isEmpty(protocol))
        throw new ManifoldCFException("Parameter " + CmisOutputConfig.PROTOCOL_PARAM + " required but not set");

      if (StringUtils.isEmpty(server))
        throw new ManifoldCFException("Parameter " + CmisOutputConfig.SERVER_PARAM + " required but not set");

      if (StringUtils.isEmpty(port))
        throw new ManifoldCFException("Parameter " + CmisOutputConfig.PORT_PARAM + " required but not set");

      if (StringUtils.isEmpty(path))
        throw new ManifoldCFException("Parameter " + CmisOutputConfig.PATH_PARAM + " required but not set");
      
      if (StringUtils.isEmpty(cmisQuery))
        throw new ManifoldCFException("Parameter " + CmisOutputConfig.CMIS_QUERY_PARAM + " required but not set");

      long currentTime;
      GetSessionThread t = new GetSessionThread();
      try {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null) {
          if (thr instanceof java.net.MalformedURLException)
            throw (java.net.MalformedURLException) thr;
          else if (thr instanceof NotBoundException)
            throw (NotBoundException) thr;
          else if (thr instanceof RemoteException)
            throw (RemoteException) thr;
          else if (thr instanceof CmisConnectionException)
            throw new ManifoldCFException("CMIS: Error during getting a new session: " + thr.getMessage(), thr);
          else if (thr instanceof CmisPermissionDeniedException)
            throw new ManifoldCFException("CMIS: Wrong credentials during getting a new session: " + thr.getMessage(),
                thr);
          else
            throw (Error) thr;
        }
      } catch (InterruptedException e) {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
      } catch (java.net.MalformedURLException e) {
        throw new ManifoldCFException(e.getMessage(), e);
      } catch (NotBoundException e) {
        // Transient problem: Server not available at the moment.
        Logging.connectors.warn("CMIS: Server not up at the moment: " + e.getMessage(), e);
        currentTime = System.currentTimeMillis();
        throw new ServiceInterruption(e.getMessage(), currentTime + 60000L);
      } catch (RemoteException e) {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(), e2, ManifoldCFException.INTERRUPTED);
        // Treat this as a transient problem
        Logging.connectors.warn("CMIS: Transient remote exception creating session: " + e.getMessage(), e);
        currentTime = System.currentTimeMillis();
        throw new ServiceInterruption(e.getMessage(), currentTime + 60000L);
      }

    }

    lastSessionFetch = System.currentTimeMillis();
  }

  /**
   * Release the session, if it's time.
   */
  protected void releaseCheck() throws ManifoldCFException {
    if (lastSessionFetch == -1L)
      return;

    long currentTime = System.currentTimeMillis();
    if (currentTime >= lastSessionFetch + timeToRelease) {
      DestroySessionThread t = new DestroySessionThread();
      try {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null) {
          if (thr instanceof RemoteException)
            throw (RemoteException) thr;
          else
            throw (Error) thr;
        }
        session = null;
        lastSessionFetch = -1L;
      } catch (InterruptedException e) {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
      } catch (RemoteException e) {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(), e2, ManifoldCFException.INTERRUPTED);
        session = null;
        lastSessionFetch = -1L;
        // Treat this as a transient problem
        Logging.connectors.warn("CMIS: Transient remote exception closing session: " + e.getMessage(), e);
      }

    }
  }

  protected void checkConnection() throws ManifoldCFException, ServiceInterruption {
    while (true) {
      boolean noSession = (session == null);
      getSession();
      long currentTime;
      CheckConnectionThread t = new CheckConnectionThread();
      try {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null) {
          if (thr instanceof RemoteException)
            throw (RemoteException) thr;
          else if (thr instanceof CmisConnectionException)
            throw new ManifoldCFException("CMIS: Error during checking connection: " + thr.getMessage(), thr);
          else
            throw (Error) thr;
        }
        return;
      } catch (InterruptedException e) {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
      } catch (RemoteException e) {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(), e2, ManifoldCFException.INTERRUPTED);
        if (noSession) {
          currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("Transient error connecting to filenet service: " + e.getMessage(),
              currentTime + 60000L);
        }
        session = null;
        lastSessionFetch = -1L;
        continue;
      }
    }
  }

  /**
   * This method is periodically called for all connectors that are connected
   * but not in active use.
   */
  @Override
  public void poll() throws ManifoldCFException {
    if (lastSessionFetch == -1L)
      return;

    long currentTime = System.currentTimeMillis();
    if (currentTime >= lastSessionFetch + timeToRelease) {
      DestroySessionThread t = new DestroySessionThread();
      try {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null) {
          if (thr instanceof RemoteException)
            throw (RemoteException) thr;
          else
            throw (Error) thr;
        }
        session = null;
        lastSessionFetch = -1L;
      } catch (InterruptedException e) {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
      } catch (RemoteException e) {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(), e2, ManifoldCFException.INTERRUPTED);
        session = null;
        lastSessionFetch = -1L;
        // Treat this as a transient problem
        Logging.connectors.warn("CMIS: Transient remote exception closing session: " + e.getMessage(), e);
      }

    }
  }

  /**
   * This method is called to assess whether to count this connector instance
   * should actually be counted as being connected.
   * 
   * @return true if the connector instance is actually connected.
   */
  @Override
  public boolean isConnected() {
    return session != null;
  }

  /**
   * Read the content of a resource, replace the variable ${PARAMNAME} with the
   * value and copy it to the out.
   *
   * @param resName
   * @param out
   * @throws ManifoldCFException
   */
  private static void outputResource(String resName, IHTTPOutput out, Locale locale, Map<String, String> paramMap)
      throws ManifoldCFException {
    Messages.outputResourceWithVelocity(out, locale, resName, paramMap, true);
  }

  /**
   * Fill in a Server tab configuration parameter map for calling a Velocity
   * template.
   * 
   * @param newMap
   *          is the map to fill in
   * @param parameters
   *          is the current set of configuration parameters
   */
  private static void fillInServerConfigurationMap(Map<String, String> newMap, IPasswordMapperActivity mapper,
      ConfigParams parameters) {
    String username = parameters.getParameter(CmisOutputConfig.USERNAME_PARAM);
    String password = parameters.getParameter(CmisOutputConfig.PASSWORD_PARAM);
    String protocol = parameters.getParameter(CmisOutputConfig.PROTOCOL_PARAM);
    String server = parameters.getParameter(CmisOutputConfig.SERVER_PARAM);
    String port = parameters.getParameter(CmisOutputConfig.PORT_PARAM);
    String path = parameters.getParameter(CmisOutputConfig.PATH_PARAM);
    String repositoryId = parameters.getParameter(CmisOutputConfig.REPOSITORY_ID_PARAM);
    String binding = parameters.getParameter(CmisOutputConfig.BINDING_PARAM);
    String cmisQuery = parameters.getParameter(CmisOutputConfig.CMIS_QUERY_PARAM);
    String createTimestampTree = parameters.getParameter(CmisOutputConfig.CREATE_TIMESTAMP_TREE_PARAM);
    
    if (username == null)
      username = StringUtils.EMPTY;
    if (password == null)
      password = StringUtils.EMPTY;
    else
      password = mapper.mapPasswordToKey(password);
    if (protocol == null)
      protocol = CmisOutputConfig.PROTOCOL_DEFAULT_VALUE;
    if (server == null)
      server = CmisOutputConfig.SERVER_DEFAULT_VALUE;
    if (port == null)
      port = CmisOutputConfig.PORT_DEFAULT_VALUE;
    if (path == null)
      path = CmisOutputConfig.PATH_DEFAULT_VALUE;
    if (repositoryId == null)
      repositoryId = StringUtils.EMPTY;
    if (binding == null)
      binding = CmisOutputConfig.BINDING_ATOM_VALUE;
    if (cmisQuery == null)
      cmisQuery = CmisOutputConfig.CMIS_QUERY_DEFAULT_VALUE;
    if(createTimestampTree == null)
      createTimestampTree = CmisOutputConfig.CREATE_TIMESTAMP_TREE_DEFAULT_VALUE;
    
    newMap.put(CmisOutputConfig.USERNAME_PARAM, username);
    newMap.put(CmisOutputConfig.PASSWORD_PARAM, password);
    newMap.put(CmisOutputConfig.PROTOCOL_PARAM, protocol);
    newMap.put(CmisOutputConfig.SERVER_PARAM, server);
    newMap.put(CmisOutputConfig.PORT_PARAM, port);
    newMap.put(CmisOutputConfig.PATH_PARAM, path);
    newMap.put(CmisOutputConfig.REPOSITORY_ID_PARAM, repositoryId);
    newMap.put(CmisOutputConfig.BINDING_PARAM, binding);
    newMap.put(CmisOutputConfig.CMIS_QUERY_PARAM, cmisQuery);
    newMap.put(CmisOutputConfig.CREATE_TIMESTAMP_TREE_PARAM, createTimestampTree);
  }

  /**
   * View configuration. This method is called in the body section of the
   * connector's view configuration page. Its purpose is to present the
   * connection information to the user. The coder can presume that the HTML
   * that is output from this configuration will be within appropriate
   * <html> and <body> tags.
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
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters)
      throws ManifoldCFException, IOException {
    Map<String, String> paramMap = new HashMap<String, String>();

    // Fill in map from each tab
    fillInServerConfigurationMap(paramMap, out, parameters);

    outputResource(VIEW_CONFIG_FORWARD, out, locale, paramMap);
  }

  /**
   * 
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
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, Locale locale,
      ConfigParams parameters, List<String> tabsArray) throws ManifoldCFException, IOException {
    // Add the Server tab
    tabsArray.add(Messages.getString(locale, CMIS_SERVER_TAB_PROPERTY));
    // Map the parameters
    Map<String, String> paramMap = new HashMap<String, String>();

    // Fill in the parameters from each tab
    fillInServerConfigurationMap(paramMap, out, parameters);

    // Output the Javascript - only one Velocity template for all tabs
    outputResource(EDIT_CONFIG_HEADER_FORWARD, out, locale, paramMap);
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out, Locale locale,
      ConfigParams parameters, String tabName) throws ManifoldCFException, IOException {

    // Call the Velocity templates for each tab

    // Server tab
    Map<String, String> paramMap = new HashMap<String, String>();
    // Set the tab name
    paramMap.put("TabName", tabName);
    // Fill in the parameters
    fillInServerConfigurationMap(paramMap, out, parameters);
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
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext,
      ConfigParams parameters) throws ManifoldCFException {

    String binding = variableContext.getParameter(CmisOutputConfig.BINDING_PARAM);
    if (binding != null)
      parameters.setParameter(CmisOutputConfig.BINDING_PARAM, binding);

    String username = variableContext.getParameter(CmisOutputConfig.USERNAME_PARAM);
    if (username != null)
      parameters.setParameter(CmisOutputConfig.USERNAME_PARAM, username);

    String password = variableContext.getParameter(CmisOutputConfig.PASSWORD_PARAM);
    if (password != null)
      parameters.setParameter(CmisOutputConfig.PASSWORD_PARAM, variableContext.mapKeyToPassword(password));

    String protocol = variableContext.getParameter(CmisOutputConfig.PROTOCOL_PARAM);
    if (protocol != null) {
      parameters.setParameter(CmisOutputConfig.PROTOCOL_PARAM, protocol);
    }

    String server = variableContext.getParameter(CmisOutputConfig.SERVER_PARAM);
    if (server != null && !StringUtils.contains(server, '/')) {
      parameters.setParameter(CmisOutputConfig.SERVER_PARAM, server);
    }

    String port = variableContext.getParameter(CmisOutputConfig.PORT_PARAM);
    if (port != null) {
      try {
        Integer.parseInt(port);
        parameters.setParameter(CmisOutputConfig.PORT_PARAM, port);
      } catch (NumberFormatException e) {

      }
    }

    String path = variableContext.getParameter(CmisOutputConfig.PATH_PARAM);
    if (path != null) {
      parameters.setParameter(CmisOutputConfig.PATH_PARAM, path);
    }
    
    String cmisQuery = variableContext.getParameter(CmisOutputConfig.CMIS_QUERY_PARAM);
    if (cmisQuery != null) {
      parameters.setParameter(CmisOutputConfig.CMIS_QUERY_PARAM, cmisQuery);
    }
    
    String createTimestampTree = variableContext.getParameter(CmisOutputConfig.CREATE_TIMESTAMP_TREE_PARAM);
    if (createTimestampTree != null) {
      parameters.setParameter(CmisOutputConfig.CREATE_TIMESTAMP_TREE_PARAM, createTimestampTree);
    }

    String repositoryId = variableContext.getParameter(CmisOutputConfig.REPOSITORY_ID_PARAM);
    if (repositoryId != null) {
      parameters.setParameter(CmisOutputConfig.REPOSITORY_ID_PARAM, repositoryId);
    }

    return null;
  }

  protected static void handleIOException(IOException e, String context)
      throws ManifoldCFException, ServiceInterruption {
    if (e instanceof InterruptedIOException) {
      throw new ManifoldCFException(e.getMessage(), e, ManifoldCFException.INTERRUPTED);
    } else {
      Logging.connectors.warn("CMIS: IOException " + context + ": " + e.getMessage(), e);
      throw new ManifoldCFException(e.getMessage(), e);
    }
  }

  /**
   * Check if the target drop zone is a CMIS folder
   * 
   * @return
   */
  private boolean isDropZoneFolder(String cmisQuery) {
    boolean isDropZoneFolder = false;

    // Get the drop zone folder
    ItemIterable<QueryResult> dropZoneItemIterable = session.query(cmisQuery, false);
    Iterator<QueryResult> dropZoneIterator = dropZoneItemIterable.iterator();
    String baseTypeId = null;
    while (dropZoneIterator.hasNext()) {
      QueryResult dropZoneResult = (QueryResult) dropZoneIterator.next();

      // check if it is a base folder content type
      baseTypeId = dropZoneResult.getPropertyByQueryName(PropertyIds.BASE_TYPE_ID).getFirstValue().toString();
      if (StringUtils.isNotEmpty(baseTypeId) && StringUtils.equals(baseTypeId, EnumBaseObjectTypeIds.CMIS_FOLDER.value())) {
        String objectId = dropZoneResult.getPropertyValueById(PropertyIds.OBJECT_ID);
        parentDropZoneFolder = (Folder) session.getObject(objectId);
        isDropZoneFolder = true;
      }
    }
    return isDropZoneFolder;
  }

  private boolean isSourceRepoCmisCompliant(RepositoryDocument document) {
    Iterator<String> fields = document.getFields();
    while (fields.hasNext()) {
      String fieldName = (String) fields.next();
      if (StringUtils.startsWith(fieldName, CMIS_PROPERTY_PREFIX)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int addOrReplaceDocumentWithException(String documentURI, VersionContext pipelineDescription,
      RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
          throws ManifoldCFException, ServiceInterruption, IOException {

    getSession();
    
    boolean isDropZoneFolder = isDropZoneFolder(cmisQuery);
    long startTime = System.currentTimeMillis();
    String resultDescription = StringUtils.EMPTY;
    Folder leafParent = null;
    String fileName = StringUtils.EMPTY;
    InputStream inputStream = null;
    ContentStream contentStream = null;
     // properties
    // (minimal set: name and object type id)
    Map<String, Object> properties = new HashMap<String, Object>();
    Long binaryLength = null;
    String mimeType = StringUtils.EMPTY;
    try {
      if (isDropZoneFolder) {

        // Creation of the new Repository Node
        fileName = document.getFileName();
        Date creationDate = document.getCreatedDate();
        Date lastModificationDate = document.getModifiedDate();
        String objectId = StringUtils.EMPTY;
        mimeType = document.getMimeType();
        binaryLength = document.getBinaryLength();
        
        //check if the repository connector includes the content path
        String primaryPath = StringUtils.EMPTY;
        List<String> sourcePath = document.getSourcePath();
        if(sourcePath != null && !sourcePath.isEmpty()) {
          primaryPath = sourcePath.get(0);
        }
  
       
        
        //if the source is CMIS Repository Connector we override the objectId for synchronizing with removeDocument method
        if(isSourceRepoCmisCompliant(document)) {
          String[] cmisObjectIdArray = (String[]) document.getField(PropertyIds.OBJECT_ID);
          if(cmisObjectIdArray!=null && cmisObjectIdArray.length>0) {
            objectId = cmisObjectIdArray[0];
          }

          //Mapping all the CMIS properties ...
          /*
          Iterator<String> fields = document.getFields();
          while (fields.hasNext()) {
            String field = (String) fields.next();
            if(!StringUtils.equals(field, "cm:lastThumbnailModification")
                || !StringUtils.equals(field, "cmis:secondaryObjectTypeIds")) {
              String[] valuesArray = (String[]) document.getField(field);
              properties.put(field,valuesArray);
            }
          }
          */
        }

        //Agnostic metadata
        properties.put(PropertyIds.OBJECT_TYPE_ID, EnumBaseObjectTypeIds.CMIS_DOCUMENT.value());
        properties.put(PropertyIds.NAME, fileName);
        properties.put(PropertyIds.CREATION_DATE, creationDate);
        properties.put(PropertyIds.LAST_MODIFICATION_DATE, lastModificationDate);
        
        //check objectId
        if(StringUtils.isNotEmpty(objectId)){
          ObjectId objId = new ObjectIdImpl(objectId);
          properties.put(PropertyIds.OBJECT_ID, objId);
        }
        
        // Content Stream
        inputStream = document.getBinaryStream();
        contentStream = new ContentStreamImpl(fileName, BigInteger.valueOf(binaryLength), mimeType,
            inputStream);

        // create a major version
        leafParent = getOrCreateLeafParent(parentDropZoneFolder, creationDate, Boolean.valueOf(createTimestampTree), primaryPath);
        leafParent.createDocument(properties, contentStream, VersioningState.NONE);
        resultDescription = DOCUMENT_STATUS_ACCEPTED_DESC;
        return DOCUMENT_STATUS_ACCEPTED;

      } else {
        resultDescription = DOCUMENT_STATUS_REJECTED_DESC;
        return DOCUMENT_STATUS_REJECTED;
      }

    } catch (CmisContentAlreadyExistsException | CmisNameConstraintViolationException e) {
      
      //updating the existing content
      if(leafParent != null) {
        String documentFullPath = leafParent.getPath() + CmisOutputConnectorUtils.SLASH + fileName;
        String newFileName = fileName+System.currentTimeMillis();
        
        Document currentContent = (Document) session.getObjectByPath(documentFullPath);
        currentContent.updateProperties(properties);
        contentStream = new ContentStreamImpl(newFileName, BigInteger.valueOf(binaryLength), mimeType, inputStream);
        currentContent.setContentStream(contentStream, true);
        
        Logging.connectors.warn(
            "CMIS: Document already exists - Updating: " + documentFullPath);
      }

      resultDescription = DOCUMENT_STATUS_ACCEPTED_DESC;
      return DOCUMENT_STATUS_ACCEPTED;
      
    } catch (Exception e) {
      resultDescription = DOCUMENT_STATUS_REJECTED_DESC;
      throw new ManifoldCFException(e.getMessage(), e);
      
    } finally {
      
      if(inputStream != null) {
        inputStream.close();
      }
      
      activities.recordActivity(startTime, ACTIVITY_INJECTION, document.getBinaryLength(), documentURI, resultDescription,
          resultDescription);
    }

  }

  /**
   * Check and create the leaf folder dedicate to inject the content
   * @param folder: this is the root folder where starts the tree
   * @param creationDate: this is the creation date of the current content
   * @param createTimestampTree: this is the flag checked in the ManifoldCF configuration panel
   * @return the target folder created using the creationDate related to the injected content
   */
  private Folder getOrCreateLeafParent(Folder folder, Date creationDate, boolean createTimestampTree, String primaryPath) {
    Folder leafParent = folder;
    if (createTimestampTree) {
      GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT);
      calendar.setTime(creationDate);
      String year = String.valueOf(calendar.get(GregorianCalendar.YEAR));
      String month = String.valueOf((calendar.get(GregorianCalendar.MONTH)+1));
      String day = String.valueOf(calendar.get(GregorianCalendar.DAY_OF_MONTH));
      
      //Check and create all the new folders
      Folder yearFolder = createFolderIfNotExist(leafParent, year);
      Folder monthFolder = createFolderIfNotExist(yearFolder, month);
      Folder dayFolder = createFolderIfNotExist(monthFolder, day);
      
      leafParent = dayFolder;
      
    } else if(StringUtils.isNotEmpty(primaryPath)) {
      String[] primaryPathArray = StringUtils.split(primaryPath, CmisOutputConnectorUtils.SLASH);
      leafParent = folder;
      for (int i = 0; i < primaryPathArray.length - 1; i++) {
        String pathSegment = primaryPathArray[i];
        Folder pathSegmentFolder = createFolderIfNotExist(leafParent, pathSegment);
        leafParent = pathSegmentFolder;
      }
    }
    return leafParent;
  }

  /**
   * Create a new CMIS folder as a child node of leafParent
   * @param leafParent
   * @param folderName
   * @return the current CMIS folder if exists otherwise it will return a new one 
   */
  private Folder createFolderIfNotExist(Folder leafParent, String folderName) {
    Folder folder = null;
    try {
      folder = (Folder) session.getObjectByPath(leafParent.getPath() + CmisOutputConnectorUtils.SLASH + folderName);
    } catch (CmisObjectNotFoundException onfe) {
      Map<String, Object> props = new HashMap<String, Object>();
      props.put(PropertyIds.OBJECT_TYPE_ID,  BaseTypeId.CMIS_FOLDER.value());
      props.put(PropertyIds.NAME, folderName);
      folder = leafParent.createFolder(props);
      
      String folderId = folder.getId();
      String folderPath = folder.getPath();
      Logging.connectors.info(
          "CMIS: Created a new folder - id: " + folderId +
          " | Path: " + folderPath);
    }
    return folder;
  }
  
  
  /**
   * Encoding process to retrieve the contentPath parameter from the documentURI.
   * The contentPath parameter can be passed from any repository connector that is currently supporting the content migration capability.
   * @param documentURI
   * @return contentPath
   * @throws URISyntaxException
   * @throws UnsupportedEncodingException
   */
  private String getContentPath(String documentURI) throws URISyntaxException, UnsupportedEncodingException {
    String contentPath = StringUtils.EMPTY;
    String documentURIWithFixedEncoding = StringUtils.replace(documentURI, " ", "%20");
    List<NameValuePair> params = URLEncodedUtils.parse(new URI(documentURIWithFixedEncoding), StandardCharsets.UTF_8);
    Iterator<NameValuePair> paramsIterator = params.iterator();
    while (paramsIterator.hasNext()) {
      NameValuePair param = (NameValuePair) paramsIterator.next();
      if(StringUtils.equals(CONTENT_PATH_PARAM, param.getName())){
        contentPath = param.getValue();
      }
    }
    return contentPath;
  }
  
  @Override
  public void removeDocument(String documentURI, String outputDescription, IOutputRemoveActivity activities)
      throws ManifoldCFException, ServiceInterruption {
    getSession();
    long startTime = System.currentTimeMillis();
    String result = StringUtils.EMPTY;
    boolean isDropZoneFolder = isDropZoneFolder(cmisQuery);
    
    //append the prefix for the relative path in the target repo
    try {
      if(isDropZoneFolder 
          && parentDropZoneFolder != null 
          && StringUtils.isNotEmpty(documentURI)) {
        String parentDropZonePath = parentDropZoneFolder.getPath();
        
        String contentPath = getContentPath(documentURI);
        String fullDocumentURIinTargetRepo = parentDropZonePath + contentPath;
        
          if(session.existsPath(fullDocumentURIinTargetRepo)) {
            session.deleteByPath(fullDocumentURIinTargetRepo);
            result = DOCUMENT_DELETION_STATUS_ACCEPTED;
          } else {
            result = DOCUMENT_DELETION_STATUS_REJECTED;
          }
      } else { 
        result = DOCUMENT_DELETION_STATUS_REJECTED;
      }
    } catch (Exception e) {
      result = DOCUMENT_DELETION_STATUS_REJECTED;
      throw new ManifoldCFException(e.getMessage(), e);
    } finally {
      activities.recordActivity(startTime, ACTIVITY_DELETE, null, documentURI, null, result);
    }
  }
  
  
}