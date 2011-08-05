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
package org.apache.manifoldcf.crawler.connectors.cmis;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.Property;
import org.apache.chemistry.opencmis.client.api.QueryResult;
import org.apache.chemistry.opencmis.client.api.Repository;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.api.SessionFactory;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.enums.PropertyType;
import org.apache.chemistry.opencmis.commons.exceptions.CmisConnectionException;
import org.apache.chemistry.opencmis.commons.impl.Constants;
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

/**
 * This is the "repository connector" for a CMIS-compliant repository.
 * 
 * @author Piergiorgio Lucidi
 */
public class CmisRepositoryConnector extends BaseRepositoryConnector {

  public static final String CONFIG_PARAM_USERNAME = "username";
  public static final String CONFIG_PARAM_PASSWORD = "password";
  public static final String CONFIG_PARAM_ENDPOINT = "endpoint";
  public static final String CONFIG_PARAM_REPOSITORY_ID = "repositoryId";
  public static final String CONFIG_PARAM_CMIS_QUERY = "cmisQuery";
  public static final String CONFIG_PARAM_BINDING = "binding";
  
  private static final String BINDING_ATOM_VALUE = "atom";
  private static final String BINDING_WS_VALUE = "ws";

  private static final String JOB_STARTPOINT_NODE_TYPE = "startpoint";
  private static final String TAB_LABEL_CMIS_QUERY = "CMIS Query";

  protected final static String ACTIVITY_READ = "read document";
  protected static final String RELATIONSHIP_CHILD = "child";

  private static final String CMIS_FOLDER_BASE_TYPE = "cmis:folder";
  private static final String CMIS_DOCUMENT_BASE_TYPE = "cmis:document";
  private static final SimpleDateFormat ISO8601_DATE_FORMATTER = new SimpleDateFormat(
      "yyyy-MM-dd'T'HH:mm:ssZ");

  /**
   * CMIS Session handle
   */
  Session session = null;

  protected String username = null;
  protected String password = null;
  protected String endpoint = null;
  protected String repositoryId = null;
  protected String binding = null;

  protected SessionFactory factory = SessionFactoryImpl.newInstance();
  protected Map<String, String> parameters = new HashMap<String, String>();

  public final static String ACTIVITY_FETCH = "fetch";

  protected static final long timeToRelease = 300000L;
  protected long lastSessionFetch = -1L;

  public CmisRepositoryConnector() {
    super();
  }

  @Override
  public String[] getActivitiesList() {
    return new String[] { ACTIVITY_FETCH };
  }

  @Override
  public String[] getBinNames(String documentIdentifier) {
    return new String[] { endpoint };
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

        // connection settings
        if(BINDING_ATOM_VALUE.equals(binding)){
          //AtomPub protocol
          parameters.put(SessionParameter.ATOMPUB_URL, endpoint);
          parameters.put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());
        } else if(BINDING_WS_VALUE.equals(binding)){
          //Web Services - SOAP - protocol
          parameters.put(SessionParameter.BINDING_TYPE, BindingType.WEBSERVICES.value());
          parameters.put(SessionParameter.WEBSERVICES_ACL_SERVICE, endpoint+"/ACLService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_DISCOVERY_SERVICE, endpoint+"/DiscoveryService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_MULTIFILING_SERVICE, endpoint+"/MultiFilingService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_NAVIGATION_SERVICE, endpoint+"/NavigationService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_OBJECT_SERVICE, endpoint+"/ObjectService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_POLICY_SERVICE, endpoint+"/PolicyService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_RELATIONSHIP_SERVICE, endpoint+"/RelationshipService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_REPOSITORY_SERVICE, endpoint+"/RepositoryService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_VERSIONING_SERVICE, endpoint+"/VersioningService?wsdl");
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
            Logging.connectors.error("CMIS: Error during getting CMIS repositories. Please check the endpoint parameters: " + e.getMessage(), e);
            this.exception = e;
          }
          
        } else {

          // get a session from the repository specified in the
          // configuration with its own ID
          parameters.put(SessionParameter.REPOSITORY_ID, repositoryId);
          
          try {
            session = factory.createSession(parameters);
          } catch (Exception e) {
            Logging.connectors.error("CMIS: Error during the creation of the new session. Please check the endpoint parameters: " + e.getMessage(), e);
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
        Logging.connectors.warn("CMIS: Error checking repository: "+e.getMessage(),e);
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
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
            ManifoldCFException.INTERRUPTED);
      } catch (RemoteException e) {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException
            || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(), e2,
              ManifoldCFException.INTERRUPTED);
        session = null;
        lastSessionFetch = -1L;
        // Treat this as a transient problem
        Logging.connectors.warn(
            "CMIS: Transient remote exception closing session: "
                + e.getMessage(), e);
      }

    }

    username = null;
    password = null;
    endpoint = null;
    repositoryId = null;

  }

  /**
   * This method create a new CMIS session for a CMIS repository, if the
   * repositoryId is not provided in the configuration, the connector will
   * retrieve all the repositories exposed for this endpoint the it will start
   * to use the first one.
   */
  @Override
  public void connect(ConfigParams configParams) {
    super.connect(configParams);
    username = params.getParameter(CONFIG_PARAM_USERNAME);
    password = params.getParameter(CONFIG_PARAM_PASSWORD);
    endpoint = params.getParameter(CONFIG_PARAM_ENDPOINT);
    binding = params.getParameter(CONFIG_PARAM_BINDING);
    if (StringUtils.isNotEmpty(params.getParameter(CONFIG_PARAM_REPOSITORY_ID)))
      repositoryId = params.getParameter(CONFIG_PARAM_REPOSITORY_ID);
  }

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

  protected void getSession() throws ManifoldCFException, ServiceInterruption {
    if (session == null) {
      // Check for parameter validity
      
      if (StringUtils.isEmpty(binding))
        throw new ManifoldCFException("Parameter " + CONFIG_PARAM_BINDING
            + " required but not set");
      
      if (StringUtils.isEmpty(username))
        throw new ManifoldCFException("Parameter " + CONFIG_PARAM_USERNAME
            + " required but not set");

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("CMIS: Username = '" + username + "'");

      if (StringUtils.isEmpty(password))
        throw new ManifoldCFException("Parameter " + CONFIG_PARAM_PASSWORD
            + " required but not set");

      Logging.connectors.debug("CMIS: Password exists");

      if (StringUtils.isEmpty(endpoint))
        throw new ManifoldCFException("Parameter " + CONFIG_PARAM_ENDPOINT
            + " required but not set");

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
          else
            throw (Error) thr;
        }
      } catch (InterruptedException e) {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
            ManifoldCFException.INTERRUPTED);
      } catch (java.net.MalformedURLException e) {
        throw new ManifoldCFException(e.getMessage(), e);
      } catch (NotBoundException e) {
        // Transient problem: Server not available at the moment.
        Logging.connectors.warn(
            "CMIS: Server not up at the moment: " + e.getMessage(), e);
        currentTime = System.currentTimeMillis();
        throw new ServiceInterruption(e.getMessage(), currentTime + 60000L);
      } catch (RemoteException e) {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException
            || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(), e2,
              ManifoldCFException.INTERRUPTED);
        // Treat this as a transient problem
        Logging.connectors.warn(
            "CMIS: Transient remote exception creating session: "
                + e.getMessage(), e);
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
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
            ManifoldCFException.INTERRUPTED);
      } catch (RemoteException e) {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException
            || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(), e2,
              ManifoldCFException.INTERRUPTED);
        session = null;
        lastSessionFetch = -1L;
        // Treat this as a transient problem
        Logging.connectors.warn(
            "CMIS: Transient remote exception closing session: "
                + e.getMessage(), e);
      }

    }
  }

  protected void checkConnection() throws ManifoldCFException,
      ServiceInterruption {
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
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
            ManifoldCFException.INTERRUPTED);
      } catch (RemoteException e) {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException
            || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(), e2,
              ManifoldCFException.INTERRUPTED);
        if (noSession) {
          currentTime = System.currentTimeMillis();
          throw new ServiceInterruption(
              "Transient error connecting to filenet service: "
                  + e.getMessage(), currentTime + 60000L);
        }
        session = null;
        lastSessionFetch = -1L;
        continue;
      }
    }
  }

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
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
            ManifoldCFException.INTERRUPTED);
      } catch (RemoteException e) {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException
            || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(), e2,
              ManifoldCFException.INTERRUPTED);
        session = null;
        lastSessionFetch = -1L;
        // Treat this as a transient problem
        Logging.connectors.warn(
            "CMIS: Transient remote exception closing session: "
                + e.getMessage(), e);
      }

    }
  }

  @Override
  public void addSeedDocuments(ISeedingActivity activities,
      DocumentSpecification spec, long startTime, long endTime, int jobMode)
      throws ManifoldCFException, ServiceInterruption {

    getSession();

    String cmisQuery = StringUtils.EMPTY;
    int i = 0;
    while (i < spec.getChildCount()) {
      SpecificationNode sn = spec.getChild(i);
      if (sn.getType().equals(JOB_STARTPOINT_NODE_TYPE)) {
        cmisQuery = sn.getAttributeValue(CONFIG_PARAM_CMIS_QUERY);
        break;
      }
      i++;
    }

    if (StringUtils.isEmpty(cmisQuery)) {
      // get root Documents from the CMIS Repository
      ItemIterable<CmisObject> cmisObjects = session.getRootFolder()
          .getChildren();
      for (CmisObject cmisObject : cmisObjects) {
        activities.addSeedDocument(cmisObject.getId());
      }
    } else {
      ItemIterable<QueryResult> results = session.query(cmisQuery, false);
      for (QueryResult result : results) {
        String id = result.getPropertyValueById(PropertyIds.OBJECT_ID);
        activities.addSeedDocument(id);
      }
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
    return new String[] { RELATIONSHIP_CHILD };
  }

  /**
   * View configuration. This method is called in the body section of the
   * connector's view configuration page. Its purpose is to present the
   * connection information to the user. The coder can presume that the HTML that
   * is output from this configuration will be within appropriate <html> and
   * <body> tags.
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
    out.print("<table class=\"displaytable\">\n"
        + "  <tr>\n"
        + "    <td class=\"description\" colspan=\"1\"><nobr>Parameters:</nobr></td>\n"
        + "    <td class=\"value\" colspan=\"3\">\n");
    Iterator iter = parameters.listParameters();
    while (iter.hasNext()) {
      String param = (String) iter.next();
      String value = parameters.getParameter(param);
      if (param.length() >= "password".length()
          && param.substring(param.length() - "password".length())
              .equalsIgnoreCase("password")) {
        out.print("      <nobr>"
            + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)
            + "=********</nobr><br/>\n");
      } else {
        out.print("      <nobr>"
            + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param) + "="
            + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(value)
            + "</nobr><br/>\n");
      }
    }
    out.print("</td>\n" + "  </tr>\n" + "</table>\n");
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
    out.print("<script type=\"text/javascript\">\n" + "<!--\n"
        + "function checkConfig()\n" + "{\n"
        + "  if (editconnection.username.value == \"\")\n" + "  {\n"
        + "    alert(\"The username must be not null\");\n"
        + "    editconnection.username.focus();\n" + "    return false;\n"
        + "  }\n" + "  if (editconnection.password.value == \"\")\n" + "  {\n"
        + "    alert(\"The password must be not null\");\n"
        + "    editconnection.password.focus();\n" + "    return false;\n"
        + "  }\n" + "  if (editconnection.endpoint.value == \"\")\n" + "  {\n"
        + "    alert(\"The endpoint must be not null\");\n"
        + "    editconnection.endpoint.focus();\n" + "    return false;\n"
        + "  }\n" + "  if (editconnection.binding.value == \"\")\n" + "  {\n"
        + "    alert(\"The binding must be not null\");\n"
        + "    editconnection.binding.focus();\n" + "    return false;\n"
        + "  }\n" + "\n" + "  return true;\n" + "}\n" + " \n"
        + "function checkConfigForSave()\n" + "{\n"
        + "  if (editconnection.username.value == \"\")\n" + "  {\n"
        + "    alert(\"The username must be not null\");\n"
        + "    editconnection.username.focus();\n" + "    return false;\n"
        + "  }\n" + "  if (editconnection.password.value == \"\")\n" + "  {\n"
        + "    alert(\"The password must be not null\");\n"
        + "    editconnection.password.focus();\n" + "    return false;\n"
        + "  }\n" + "  if (editconnection.binding.value == \"\")\n" + "  {\n"
        + "    alert(\"The binding must be not null\");\n"
        + "    editconnection.binding.focus();\n" + "    return false;\n"
        + "  }\n" + "  if (editconnection.endpoint.value == \"\")\n" + "  {\n"
        + "    alert(\"The endpoint must be not null\");\n"
        + "    editconnection.endpoint.focus();\n" + "    return false;\n"
        + "  }\n" + "  return true;\n" + "}\n" + "\n" + "//-->\n"
        + "</script>\n");
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext,
      IHTTPOutput out, ConfigParams parameters, String tabName)
      throws ManifoldCFException, IOException {
    
    String username = parameters.getParameter(CONFIG_PARAM_USERNAME);
    String password = parameters.getParameter(CONFIG_PARAM_PASSWORD);
    String endpoint = parameters.getParameter(CONFIG_PARAM_ENDPOINT);
    String repositoryId = parameters.getParameter(CONFIG_PARAM_REPOSITORY_ID);
    String binding = parameters.getParameter(CONFIG_PARAM_BINDING);
    
    if(StringUtils.isEmpty(username))
      username = StringUtils.EMPTY;
    if(StringUtils.isEmpty(password))
      password = StringUtils.EMPTY;
    if(StringUtils.isEmpty(endpoint))
      endpoint = StringUtils.EMPTY;
    if(StringUtils.isEmpty(repositoryId))
      repositoryId = StringUtils.EMPTY;
    if(StringUtils.isEmpty(binding))
      binding = BINDING_ATOM_VALUE;
    
    out.print("<table class=\"displaytable\">\n"
        + "  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n");
    out.print(
         "<tr><td class=\"description\"><nobr>Binding:</nobr></td>\n"
        +"<td class=\"value\"><select name=\"binding\">");
    
    if(BINDING_ATOM_VALUE.equals(binding)){    
      out.print("<option value=\""+BINDING_ATOM_VALUE+"\" selected=\"selected\">AtomPub</option>"
          +"<option value=\""+BINDING_WS_VALUE+"\">Web Services</option>"
          +"</select></td></tr>");
      
    } else if(BINDING_WS_VALUE.equals(binding)) {
      out.print("<option value=\""+BINDING_ATOM_VALUE+"\">AtomPub</option>"
          +"<option value=\""+BINDING_WS_VALUE+"\" selected=\"selected\">Web Services</option>"
          +"</select></td></tr>");
    }
    
    out.print("<tr><td class=\"description\"><nobr>Username:</nobr></td>\n"
        +"<td class=\"value\"><input type=\"text\" name=\""
        + CONFIG_PARAM_USERNAME + "\" value=\""+username+"\"/></td></tr>\n");
    out.print("<tr><td class=\"description\"><nobr>Password:</nobr></td>" +
    		"<td class=\"value\"><input type=\"password\" name=\""
        + CONFIG_PARAM_PASSWORD + "\" value=\""+password+"\"/></td></tr>\n");
    out.print("<tr><td class=\"description\"><nobr>Endpoint:</nobr></td>" +
    		"<td class=\"value\"><input type=\"text\" name=\""
        + CONFIG_PARAM_ENDPOINT + "\" value=\""+endpoint+"\" size=\"50\"/></td></tr>\n");
    out.print("<tr><td class=\"description\"><nobr>Repository ID:</nobr></td>" +
    		"<td class=\"value\"><input type=\"text\" name=\""
        + CONFIG_PARAM_REPOSITORY_ID + "\" value=\""+repositoryId+"\"/><nobr>(optional)</nobr></td></tr>\n");
    out.print("</table>\n");
    
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
    
    String binding = variableContext.getParameter(CONFIG_PARAM_BINDING);
    if (StringUtils.isNotEmpty(binding))
      parameters.setParameter(CONFIG_PARAM_BINDING, binding);
    
    String username = variableContext.getParameter(CONFIG_PARAM_USERNAME);
    if (StringUtils.isNotEmpty(username))
      parameters.setParameter(CONFIG_PARAM_USERNAME, username);

    String password = variableContext.getParameter(CONFIG_PARAM_PASSWORD);
    if (StringUtils.isNotEmpty(password))
      parameters.setParameter(CONFIG_PARAM_PASSWORD, password);

    String endpoint = variableContext.getParameter(CONFIG_PARAM_ENDPOINT);
    if (StringUtils.isNotEmpty(endpoint) && endpoint.length() > 0)
      parameters.setParameter(CONFIG_PARAM_ENDPOINT, endpoint);

    String repositoryId = variableContext
        .getParameter(CONFIG_PARAM_REPOSITORY_ID);
    if (StringUtils.isNotEmpty(repositoryId))
      parameters.setParameter(CONFIG_PARAM_REPOSITORY_ID, repositoryId);

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

    out.print("<table class=\"displaytable\">\n");
    int i = 0;
    boolean seenAny = false;
    while (i < ds.getChildCount()) {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals(JOB_STARTPOINT_NODE_TYPE)) {
        if (seenAny == false) {
          seenAny = true;
        }
        out.print("  <tr>\n"
            + "    <td class=\"description\">CMIS Query:</td>\n"
            + "    <td class=\"value\">\n"
            + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(sn
                .getAttributeValue(CONFIG_PARAM_CMIS_QUERY)));
        out.print("    </td>\n" + "  </tr>\n");
      }
      i++;
    }

    if (seenAny == false) {
      out.print("  <tr><td class=\"message\">No documents specified</td></tr>\n");
    }
    out.print("</table>\n");

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
    String cmisQuery = variableContext.getParameter(CONFIG_PARAM_CMIS_QUERY);
    if (StringUtils.isNotEmpty(cmisQuery)) {
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
      node.setAttribute(CONFIG_PARAM_CMIS_QUERY, cmisQuery);
      variableContext.setParameter(CONFIG_PARAM_CMIS_QUERY, cmisQuery);
      ds.addChild(ds.getChildCount(), node);
    } else {
      int i = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode oldNode = ds.getChild(i);
        if (oldNode.getType().equals(JOB_STARTPOINT_NODE_TYPE)) {
          variableContext.setParameter(CONFIG_PARAM_CMIS_QUERY,
              oldNode.getAttributeValue(CONFIG_PARAM_CMIS_QUERY));
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
    if (tabName.equals(TAB_LABEL_CMIS_QUERY)) {
      String cmisQuery = StringUtils.EMPTY;
      int i = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode n = ds.getChild(i);
        if (n.getType().equals(JOB_STARTPOINT_NODE_TYPE)) {
          cmisQuery = n.getAttributeValue(CONFIG_PARAM_CMIS_QUERY);
          break;
        }
        i++;
      }

      out.print("<table class=\"displaytable\">\n"
          + "  <tr><td class=\"separator\" colspan=\"3\"><hr/></td></tr>\n"
          + "        <tr>\n"
          + "       <td class=\"description\"><nobr>CMIS Query:</nobr></td>"
          + "          <td class=\"value\">\n"
          + "            <nobr>\n"
          + "              <input type=\"text\" size=\"120\" name=\"cmisQuery\" value=\""+cmisQuery+"\"/>\n"
          + "            </nobr>\n"
          + "          </td>\n"
          + "  			</tr>\n"
          + "  </tr>"
          + "</table>\n");
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
    tabsArray.add(TAB_LABEL_CMIS_QUERY);

    out.print("<script type=\"text/javascript\">\n"
        + "function checkSpecification()\n" + "{\n"
        + "  // Does nothing right now.\n" + "  return true;\n" + "}\n" + "\n"
        + "function SpecOp(n, opValue, anchorvalue)\n" + "{\n"
        + "  eval(\"editjob.\"+n+\".value = \\\"\"+opValue+\"\\\"\");\n"
        + "  postFormSetAnchor(anchorvalue);\n" + "}\n" + "</script>\n");

  }

  @Override
  public void processDocuments(String[] documentIdentifiers, String[] versions,
      IProcessActivity activities, DocumentSpecification spec,
      boolean[] scanOnly) throws ManifoldCFException, ServiceInterruption {

    getSession();
    Logging.connectors.debug("CMIS: Inside processDocuments");
    int i = 0;

    while (i < documentIdentifiers.length) {
      long startTime = System.currentTimeMillis();
      String nodeId = documentIdentifiers[i];

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("CMIS: Processing document identifier '"
            + nodeId + "'");

      CmisObject cmisObject = session.getObject(nodeId);
      
      String errorCode = "OK";
      String errorDesc = StringUtils.EMPTY;
      String baseTypeId = cmisObject.getBaseType().getId();

      if (baseTypeId.equals(CMIS_FOLDER_BASE_TYPE)) {

        // adding all the children for a folder

        Folder folder = (Folder) cmisObject;
        ItemIterable<CmisObject> children = folder.getChildren();
        for (CmisObject child : children) {
          activities.addDocumentReference(child.getId(), nodeId,
              RELATIONSHIP_CHILD);
        }

      } else if(baseTypeId.equals(CMIS_DOCUMENT_BASE_TYPE)){

        // content ingestion

        Document document = (Document) cmisObject;
        long fileLength = document.getContentStreamLength();

        InputStream is = document.getContentStream().getStream();

        try {
          RepositoryDocument rd = new RepositoryDocument();
          
          //binary
          rd.setBinary(is, fileLength);

          //properties
          List<Property<?>> properties = document.getProperties();
          String id = StringUtils.EMPTY;
          for (Property<?> property : properties) {
            String propertyId = property.getId();
            Object propertyValue = property.getValue();
            if (propertyId.endsWith(Constants.PARAM_OBJECT_ID))
              id = (String) propertyValue;

            if (propertyValue != null) {
              PropertyType propertyType = property.getType();

              switch (propertyType) {

              case STRING:
              case ID:
              case URI:
              case HTML:
                String stringValue = (String) propertyValue;
                rd.addField(propertyId, stringValue);
                break;

              case BOOLEAN:
                Boolean booleanValue = (Boolean) propertyValue;
                rd.addField(propertyId, booleanValue.toString());
                break;

              case INTEGER:
                BigInteger integerValue = (BigInteger) propertyValue;
                rd.addField(propertyId, integerValue.toString());
                break;

              case DECIMAL:
                BigDecimal decimalValue = (BigDecimal) propertyValue;
                rd.addField(propertyId, decimalValue.toString());
                break;

              case DATETIME:
                GregorianCalendar dateValue = (GregorianCalendar) propertyValue;
                rd.addField(propertyId,
                    ISO8601_DATE_FORMATTER.format(dateValue.getTime()));
                break;

              default:
                break;
              }
            }
          }
          
          //ingestion
          String version = document.getVersionLabel();
          String documentURI = endpoint+"/"+id+"/"+version;
          activities.ingestDocument(id, version, documentURI, rd);

        } finally {
          try {
            is.close();
          } catch (InterruptedIOException e) {
            errorCode = "Interrupted error";
            errorDesc = e.getMessage();
            throw new ManifoldCFException(e.getMessage(), e,
                ManifoldCFException.INTERRUPTED);
          } catch (IOException e) {
            errorCode = "IO ERROR";
            errorDesc = e.getMessage();
            Logging.connectors.warn(
                "CMIS: IOException closing file input stream: "
                    + e.getMessage(), e);
          }

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
    
    getSession();
    
    String[] rval = new String[documentIdentifiers.length];
    int i = 0;
    while (i < rval.length){
      CmisObject cmisObject = session.getObject(documentIdentifiers[i]);
      if (cmisObject.getBaseType().getId().equals(CMIS_DOCUMENT_BASE_TYPE)) {
        Document document = (Document) cmisObject;
        
        //we have to check if this CMIS repository support versioning
        // or if the versioning is disabled for this content
        if(StringUtils.isNotEmpty(document.getVersionLabel())){
          rval[i] = document.getVersionLabel();
        } else {
        //a CMIS document that doesn't contain versioning information will always be processed
          rval[i] = StringUtils.EMPTY;
        }
      } else {
        //a CMIS folder will always be processed
        rval[i] = StringUtils.EMPTY;
      }
      i++;
    }
    return rval;
  }
}