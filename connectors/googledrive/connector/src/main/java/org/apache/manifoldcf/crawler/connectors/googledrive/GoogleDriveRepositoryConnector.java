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

package org.apache.manifoldcf.crawler.connectors.googledrive;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.connectorcommon.common.XThreadInputStream;
import org.apache.manifoldcf.connectorcommon.common.XThreadStringBuffer;
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

import com.google.api.client.repackaged.com.google.common.base.Objects;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.ParentReference;

/**
 *
 * @author andrew
 */
public class GoogleDriveRepositoryConnector extends BaseRepositoryConnector {

  protected final static String ACTIVITY_READ = "read document";
  public final static String ACTIVITY_FETCH = "fetch";
  protected static final String RELATIONSHIP_CHILD = "child";
  
  /** Deny access token for default authority */
  private final static String defaultAuthorityDenyToken = "DEAD_AUTHORITY";

  // Nodes
  private static final String JOB_STARTPOINT_NODE_TYPE = "startpoint";
  private static final String JOB_QUERY_ATTRIBUTE = "query";
  private static final String JOB_ACCESS_NODE_TYPE = "access";
  private static final String JOB_TOKEN_ATTRIBUTE = "token";

  // Configuration tabs
  private static final String GOOGLEDRIVE_SERVER_TAB_PROPERTY = "GoogleDriveRepositoryConnector.Server";
  
  // Specification tabs
  private static final String GOOGLEDRIVE_QUERY_TAB_PROPERTY = "GoogleDriveRepositoryConnector.GoogleDriveQuery";
  private static final String GOOGLEDRIVE_SECURITY_TAB_PROPERTY = "GoogleDriveRepositoryConnector.Security";
  
  // Template names for configuration
  /**
   * Forward to the javascript to check the configuration parameters
   */
  private static final String EDIT_CONFIG_HEADER_FORWARD = "editConfiguration_google_server.js";
  /**
   * Server tab template
   */
  private static final String EDIT_CONFIG_FORWARD_SERVER = "editConfiguration_google_server.html";
  
  /**
   * Forward to the HTML template to view the configuration parameters
   */
  private static final String VIEW_CONFIG_FORWARD = "viewConfiguration_googledrive.html";
   
  // Template names for specification
  /**
   * Forward to the javascript to check the specification parameters for the job
   */
  private static final String EDIT_SPEC_HEADER_FORWARD = "editSpecification_googledrive.js";
  /**
   * Forward to the template to edit the query for the job
   */
  private static final String EDIT_SPEC_FORWARD_GOOGLEDRIVEQUERY = "editSpecification_googledriveQuery.html";
  /**
   * Forward to the template to edit the security parameters for the job
   */
  private static final String EDIT_SPEC_FORWARD_SECURITY = "editSpecification_googledriveSecurity.html";
  
  /**
   * Forward to the template to view the specification parameters for the job
   */
  private static final String VIEW_SPEC_FORWARD = "viewSpecification_googledrive.html";
  
  /** The content path param used for managing content migration deletion **/
  private static final String CONTENT_PATH_PARAM = "contentPath";
  
  private String SLASH = "/";
	
  /**
   * Endpoint server name
   */
  protected String server = "googledrive";
  protected GoogleDriveSession session = null;
  protected long lastSessionFetch = -1L;
  protected static final long timeToRelease = 300000L;
  protected String clientid = null;
  protected String clientsecret = null;
  protected String refreshtoken = null;

  public GoogleDriveRepositoryConnector() {
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
    return new String[]{ACTIVITY_FETCH, ACTIVITY_READ};
  }

  /**
   * Get the bin name strings for a document identifier. The bin name
   * describes the queue to which the document will be assigned for throttling
   * purposes. Throttling controls the rate at which items in a given queue
   * are fetched; it does not say anything about the overall fetch rate, which
   * may operate on multiple queues or bins. For example, if you implement a
   * web crawler, a good choice of bin name would be the server name, since
   * that is likely to correspond to a real resource that will need real
   * throttle protection.
   *
   * @param documentIdentifier is the document identifier.
   * @return the set of bin names. If an empty array is returned, it is
   * equivalent to there being no request rate throttling available for this
   * identifier.
   */
  @Override
  public String[] getBinNames(String documentIdentifier) {
    return new String[]{server};
  }

  /**
   * Close the connection. Call this before discarding the connection.
   */
  @Override
  public void disconnect() throws ManifoldCFException {
    if (session != null) {
      session.close();
      session = null;
      lastSessionFetch = -1L;
    }

    clientid = null;
    clientsecret = null;
    refreshtoken = null;
  }

  /**
   * This method create a new GOOGLEDRIVE session for a GOOGLEDRIVE
   * repository, if the repositoryId is not provided in the configuration, the
   * connector will retrieve all the repositories exposed for this endpoint
   * the it will start to use the first one.
   *
   * @param configParams is the set of configuration parameters, which in
   * this case describe the target appliance, basic auth configuration, etc.
   * (This formerly came out of the ini file.)
   */
  @Override
  public void connect(ConfigParams configParams) {
    super.connect(configParams);

    clientid = params.getParameter(GoogleDriveConfig.CLIENT_ID_PARAM);
    clientsecret = params.getObfuscatedParameter(GoogleDriveConfig.CLIENT_SECRET_PARAM);
    refreshtoken = params.getParameter(GoogleDriveConfig.REFRESH_TOKEN_PARAM);
  }

  /**
   * Test the connection. Returns a string describing the connection
   * integrity.
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
        this.exception = e;
      }
    }

    public Throwable getException() {
      return exception;
    }
  }

  protected void checkConnection() throws ManifoldCFException, ServiceInterruption {
    getSession();
    CheckConnectionThread t = new CheckConnectionThread();
    try {
      t.start();
      t.join();
      Throwable thr = t.getException();
      if (thr != null) {
        if (thr instanceof IOException) {
          throw (IOException) thr;
        } else if (thr instanceof RuntimeException) {
          throw (RuntimeException) thr;
        } else {
          throw (Error) thr;
        }
      }
      return;
    } catch (InterruptedException e) {
      t.interrupt();
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
        ManifoldCFException.INTERRUPTED);
    } catch (java.net.SocketTimeoutException e) {
      Logging.connectors.warn("GOOGLEDRIVE: Socket timeout: " + e.getMessage(), e);
      handleIOException(e);
    } catch (InterruptedIOException e) {
      t.interrupt();
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
        ManifoldCFException.INTERRUPTED);
    } catch (IOException e) {
      Logging.connectors.warn("GOOGLEDRIVE: Error checking repository: " + e.getMessage(), e);
      handleIOException(e);
    }
  }

  /**
   * Set up a session
   */
  protected void getSession() throws ManifoldCFException, ServiceInterruption {
    if (session == null) {
      // Check for parameter validity

      if (StringUtils.isEmpty(clientid)) {
        throw new ManifoldCFException("Parameter " + GoogleDriveConfig.CLIENT_ID_PARAM
            + " required but not set");
      }

      if (Logging.connectors.isDebugEnabled()) {
        Logging.connectors.debug("GOOGLEDRIVE: Clientid = '" + clientid + "'");
      }

      if (StringUtils.isEmpty(clientsecret)) {
        throw new ManifoldCFException("Parameter " + GoogleDriveConfig.CLIENT_SECRET_PARAM
            + " required but not set");
      }

      if (Logging.connectors.isDebugEnabled()) {
        Logging.connectors.debug("GOOGLEDRIVE: Clientsecret = '" + clientsecret + "'");
      }

      if (StringUtils.isEmpty(refreshtoken)) {
        throw new ManifoldCFException("Parameter " + GoogleDriveConfig.REFRESH_TOKEN_PARAM
            + " required but not set");
      }

      if (Logging.connectors.isDebugEnabled()) {
        Logging.connectors.debug("GOOGLEDRIVE: refreshtoken = '" + refreshtoken + "'");
      }



      long currentTime;
      GetSessionThread t = new GetSessionThread();
      try {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null) {
          if (thr instanceof IOException) {
            throw (IOException) thr;
          } else if (thr instanceof GeneralSecurityException) {
            throw (GeneralSecurityException) thr;
          } else {
            throw (Error) thr;
          }

        }
      } catch (InterruptedException e) {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
            ManifoldCFException.INTERRUPTED);
      } catch (java.net.SocketTimeoutException e) {
        Logging.connectors.warn("GOOGLEDRIVE: Socket timeout: " + e.getMessage(), e);
        handleIOException(e);
      } catch (InterruptedIOException e) {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
            ManifoldCFException.INTERRUPTED);
      } catch (GeneralSecurityException e) {
        Logging.connectors.error("GOOGLEDRIVE: " +  "General security error initializing transport: " + e.getMessage(), e);
        handleGeneralSecurityException(e);
      } catch (IOException e) {
        Logging.connectors.warn("GOOGLEDRIVE: IO error: " + e.getMessage(), e);
        handleIOException(e);
      }

    }
    lastSessionFetch = System.currentTimeMillis();
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
        session = new GoogleDriveSession(clientid, clientsecret, refreshtoken);
      } catch (Throwable e) {
        this.exception = e;
      }
    }

    public Throwable getException() {
      return exception;
    }
  }

  @Override
  public void poll() throws ManifoldCFException {
    if (lastSessionFetch == -1L) {
      return;
    }

    long currentTime = System.currentTimeMillis();
    if (currentTime >= lastSessionFetch + timeToRelease) {
      session.close();
      session = null;
      lastSessionFetch = -1L;
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

  /**
   * Get the maximum number of documents to amalgamate together into one
   * batch, for this connector.
   *
   * @return the maximum number. 0 indicates "unlimited".
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
    return new String[]{RELATIONSHIP_CHILD};
  }

  /**
   * Fill in a Server tab configuration parameter map for calling a Velocity
   * template.
   *
   * @param newMap is the map to fill in
   * @param parameters is the current set of configuration parameters
   */
  private static void fillInServerConfigurationMap(Map<String, Object> newMap, IPasswordMapperActivity mapper, ConfigParams parameters) {
    String clientid = parameters.getParameter(GoogleDriveConfig.CLIENT_ID_PARAM);
    String clientsecret = parameters.getObfuscatedParameter(GoogleDriveConfig.CLIENT_SECRET_PARAM);
    String refreshtoken = parameters.getParameter(GoogleDriveConfig.REFRESH_TOKEN_PARAM);

    if (clientid == null) {
      clientid = StringUtils.EMPTY;
    }
    
    if (clientsecret == null) {
      clientsecret = StringUtils.EMPTY;
    } else {
      clientsecret = mapper.mapPasswordToKey(clientsecret);
    }

    if (refreshtoken == null) {
      refreshtoken = StringUtils.EMPTY;
    }

    newMap.put("CLIENTID", clientid);
    newMap.put("CLIENTSECRET", clientsecret);
    newMap.put("REFRESHTOKEN", refreshtoken);
  }

  /**
   * View configuration. This method is called in the body section of the
   * connector's view configuration page. Its purpose is to present the
   * connection information to the user. The coder can presume that the HTML
   * that is output from this configuration will be within appropriate &lt;html&gt;
   * and &lt;body&gt; tags.
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

    Messages.outputResourceWithVelocity(out,locale,VIEW_CONFIG_FORWARD,paramMap);
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
    tabsArray.add(Messages.getString(locale, GOOGLEDRIVE_SERVER_TAB_PROPERTY));
    // Map the parameters
    Map<String, Object> paramMap = new HashMap<String, Object>();

    // Fill in the parameters from each tab
    fillInServerConfigurationMap(paramMap, out, parameters);

    // Output the Javascript - only one Velocity template for all tabs
    Messages.outputResourceWithVelocity(out,locale,EDIT_CONFIG_HEADER_FORWARD,paramMap);
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
      throws ManifoldCFException, IOException {


    // Call the Velocity templates for each tab
    Map<String, Object> paramMap = new HashMap<String, Object>();
    // Set the tab name
    paramMap.put("TabName", tabName);

    // Server tab
    // Fill in the parameters
    fillInServerConfigurationMap(paramMap, out, parameters);
    Messages.outputResourceWithVelocity(out,locale,EDIT_CONFIG_FORWARD_SERVER,paramMap);
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

    String clientid = variableContext.getParameter(GoogleDriveConfig.CLIENT_ID_PARAM);
    if (clientid != null) {
      parameters.setParameter(GoogleDriveConfig.CLIENT_ID_PARAM, clientid);
    }

    String clientsecret = variableContext.getParameter(GoogleDriveConfig.CLIENT_SECRET_PARAM);
    if (clientsecret != null) {
      parameters.setObfuscatedParameter(GoogleDriveConfig.CLIENT_SECRET_PARAM, variableContext.mapKeyToPassword(clientsecret));
    }

    String refreshtoken = variableContext.getParameter(GoogleDriveConfig.REFRESH_TOKEN_PARAM);
    if (refreshtoken != null) {
      parameters.setParameter(GoogleDriveConfig.REFRESH_TOKEN_PARAM, refreshtoken);
    }

    return null;
  }

  /**
   * Fill in specification Velocity parameter map for GOOGLEDRIVEQuery tab.
   */
  private static void fillInGOOGLEDRIVEQuerySpecificationMap(Map<String, Object> newMap, Specification ds) {
    String GoogleDriveQuery = GoogleDriveConfig.GOOGLEDRIVE_QUERY_DEFAULT;
    for (int i = 0; i < ds.getChildCount(); i++) {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals(JOB_STARTPOINT_NODE_TYPE)) {
        GoogleDriveQuery = sn.getAttributeValue(JOB_QUERY_ATTRIBUTE);
      }
    }
    newMap.put("GOOGLEDRIVEQUERY", GoogleDriveQuery);
  }

  /**
   * Fill in specification Velocity parameter map for GOOGLEDRIVESecurity tab.
   */
  private static void fillInGOOGLEDRIVESecuritySpecificationMap(Map<String, Object> newMap, Specification ds) {
    List<Map<String,String>> accessTokenList = new ArrayList<Map<String,String>>();
    for (int i = 0; i < ds.getChildCount(); i++) {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals(JOB_ACCESS_NODE_TYPE)) {
        String token = sn.getAttributeValue(JOB_TOKEN_ATTRIBUTE);
        Map<String,String> accessMap = new HashMap<String,String>();
        accessMap.put("TOKEN",token);
        accessTokenList.add(accessMap);
      }
    }
    newMap.put("ACCESSTOKENS", accessTokenList);
  }

  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the document
  * specification information to the user.  The coder can presume that the HTML that is output from
  * this configuration will be within appropriate &lt;html&gt; and &lt;body&gt;tags.
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

    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("SeqNum", Integer.toString(connectionSequenceNumber));

    // Fill in the map with data from all tabs
    fillInGOOGLEDRIVEQuerySpecificationMap(paramMap, ds);
    fillInGOOGLEDRIVESecuritySpecificationMap(paramMap, ds);

    Messages.outputResourceWithVelocity(out,locale,VIEW_SPEC_FORWARD,paramMap);
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

    String googleDriveQuery = variableContext.getParameter(seqPrefix+"googledrivequery");
    if (googleDriveQuery != null) {
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
      node.setAttribute(JOB_QUERY_ATTRIBUTE, googleDriveQuery);
      ds.addChild(ds.getChildCount(), node);
    }
    
    String xc = variableContext.getParameter(seqPrefix+"tokencount");
    if (xc != null) {
      // Delete all tokens first
      int i = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals(JOB_ACCESS_NODE_TYPE))
          ds.removeChild(i);
        else
          i++;
      }

      int accessCount = Integer.parseInt(xc);
      i = 0;
      while (i < accessCount) {
        String accessDescription = "_"+Integer.toString(i);
        String accessOpName = seqPrefix+"accessop"+accessDescription;
        xc = variableContext.getParameter(accessOpName);
        if (xc != null && xc.equals("Delete")) {
          // Next row
          i++;
          continue;
        }
        // Get the stuff we need
        String accessSpec = variableContext.getParameter(seqPrefix+"spectoken"+accessDescription);
        SpecificationNode node = new SpecificationNode(JOB_ACCESS_NODE_TYPE);
        node.setAttribute(JOB_TOKEN_ATTRIBUTE,accessSpec);
        ds.addChild(ds.getChildCount(),node);
        i++;
      }

      String op = variableContext.getParameter(seqPrefix+"accessop");
      if (op != null && op.equals("Add"))
      {
        String accessspec = variableContext.getParameter(seqPrefix+"spectoken");
        SpecificationNode node = new SpecificationNode(JOB_ACCESS_NODE_TYPE);
        node.setAttribute(JOB_TOKEN_ATTRIBUTE,accessspec);
        ds.addChild(ds.getChildCount(),node);
      }
    }

    return null;
  }

  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected a repository connection of the
  * current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate
  *  &lt;html&gt;, &lt;body&gt;, and &lt;form&gt; tags.  The name of the form is always "editjob".
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

    // Output GOOGLEDRIVEQuery tab
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("TabName", tabName);
    paramMap.put("SeqNum", Integer.toString(connectionSequenceNumber));
    paramMap.put("SelectedNum", Integer.toString(actualSequenceNumber));

    fillInGOOGLEDRIVEQuerySpecificationMap(paramMap, ds);
    fillInGOOGLEDRIVESecuritySpecificationMap(paramMap, ds);
    Messages.outputResourceWithVelocity(out,locale,EDIT_SPEC_FORWARD_GOOGLEDRIVEQUERY,paramMap);
    Messages.outputResourceWithVelocity(out,locale,EDIT_SPEC_FORWARD_SECURITY,paramMap);
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

    tabsArray.add(Messages.getString(locale, GOOGLEDRIVE_QUERY_TAB_PROPERTY));
    tabsArray.add(Messages.getString(locale, GOOGLEDRIVE_SECURITY_TAB_PROPERTY));

    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("SeqNum", Integer.toString(connectionSequenceNumber));

    // Fill in the specification header map, using data from all tabs.
    fillInGOOGLEDRIVEQuerySpecificationMap(paramMap, ds);
    fillInGOOGLEDRIVESecuritySpecificationMap(paramMap, ds);

    Messages.outputResourceWithVelocity(out,locale,EDIT_SPEC_HEADER_FORWARD,paramMap);
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
  *@param lastSeedVersion is the last seeding version string for this job, or null if the job has no previous seeding version string.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@return an updated seeding version string, to be stored with the job.
  */
  @Override
  public String addSeedDocuments(ISeedingActivity activities, Specification spec,
    String lastSeedVersion, long seedTime, int jobMode)
    throws ManifoldCFException, ServiceInterruption {

    String googleDriveQuery = GoogleDriveConfig.GOOGLEDRIVE_QUERY_DEFAULT;
    int i = 0;
    while (i < spec.getChildCount()) {
      SpecificationNode sn = spec.getChild(i);
      if (sn.getType().equals(JOB_STARTPOINT_NODE_TYPE)) {
        googleDriveQuery = sn.getAttributeValue(JOB_QUERY_ATTRIBUTE);
        break;
      }
      i++;
    }

    getSession();
    GetSeedsThread t = new GetSeedsThread(googleDriveQuery);
    try {
      t.start();
      boolean wasInterrupted = false;
      try {
        XThreadStringBuffer seedBuffer = t.getBuffer();
        // Pick up the paths, and add them to the activities, before we join with the child thread.
        while (true) {
          // The only kind of exceptions this can throw are going to shut the process down.
          String docPath = seedBuffer.fetch();
          if (docPath ==  null)
            break;
          // Add the pageID to the queue
          activities.addSeedDocument(docPath);
        }
      } catch (InterruptedException e) {
        wasInterrupted = true;
        throw e;
      } catch (ManifoldCFException e) {
        if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
          wasInterrupted = true;
        throw e;
      } finally {
        if (!wasInterrupted)
          t.finishUp();
      }
    } catch (InterruptedException e) {
      t.interrupt();
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
        ManifoldCFException.INTERRUPTED);
    } catch (java.net.SocketTimeoutException e) {
      Logging.connectors.warn("GOOGLEDRIVE: Socket timeout adding seed documents: " + e.getMessage(), e);
      handleIOException(e);
    } catch (InterruptedIOException e) {
      t.interrupt();
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
        ManifoldCFException.INTERRUPTED);
    } catch (IOException e) {
      Logging.connectors.warn("GOOGLEDRIVE: Error adding seed documents: " + e.getMessage(), e);
      handleIOException(e);
    }
    return "";
  }
  
  protected class GetSeedsThread extends Thread {

    protected Throwable exception = null;
    protected final String googleDriveQuery;
    protected final XThreadStringBuffer seedBuffer;
    
    public GetSeedsThread(String googleDriveQuery) {
      super();
      this.googleDriveQuery = googleDriveQuery;
      this.seedBuffer = new XThreadStringBuffer();
      setDaemon(true);
    }

    @Override
    public void run() {
      try {
        session.getSeeds(seedBuffer, googleDriveQuery);
      } catch (Throwable e) {
        this.exception = e;
      } finally {
        seedBuffer.signalDone();
      }
    }

    public XThreadStringBuffer getBuffer() {
      return seedBuffer;
    }
    
    public void finishUp()
      throws InterruptedException, IOException {
      seedBuffer.abandon();
      join();
      Throwable thr = exception;
      if (thr != null) {
        if (thr instanceof IOException)
          throw (IOException) thr;
        else if (thr instanceof RuntimeException)
          throw (RuntimeException) thr;
        else if (thr instanceof Error)
          throw (Error) thr;
        else
          throw new RuntimeException("Unhandled exception of type: "+thr.getClass().getName(),thr);
      }
    }
  }

  protected File getObject(String nodeId)
    throws ManifoldCFException, ServiceInterruption {
    getSession();
    GetObjectThread t = new GetObjectThread(nodeId);
    try {
      t.start();
      t.join();
      Throwable thr = t.getException();
      if (thr != null) {
        if (thr instanceof IOException) {
          throw (IOException) thr;
        } else if (thr instanceof RuntimeException) {
          throw (RuntimeException) thr;
        } else {
          throw (Error) thr;
        }
      }
    } catch (InterruptedException e) {
      t.interrupt();
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
        ManifoldCFException.INTERRUPTED);
    } catch (java.net.SocketTimeoutException e) {
      Logging.connectors.warn("GOOGLEDRIVE: Socket timeout getting object: " + e.getMessage(), e);
      handleIOException(e);
    } catch (InterruptedIOException e) {
      t.interrupt();
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
        ManifoldCFException.INTERRUPTED);
    } catch (IOException e) {
      Logging.connectors.warn("GOOGLEDRIVE: Error getting object: " + e.getMessage(), e);
      handleIOException(e);
    }
    return t.getResponse();
  }
  
  protected class GetObjectThread extends Thread {

    protected final String nodeId;
    protected Throwable exception = null;
    protected File response = null;

    public GetObjectThread(String nodeId) {
      super();
      setDaemon(true);
      this.nodeId = nodeId;
    }

    public void run() {
      try {
        response = session.getObject(nodeId);
      } catch (Throwable e) {
        this.exception = e;
      }
    }

    public File getResponse() {
      return response;
    }
    
    public Throwable getException() {
      return exception;
    }
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
      
    // Forced acls
    String[] acls = getAcls(spec);
    // Sort it,
    java.util.Arrays.sort(acls);

    for (String documentIdentifier : documentIdentifiers) {
      File googleFile = getObject(documentIdentifier);

      // StringBuilder log = new StringBuilder();
      // log.append("File Original Name: " + googleFile.getOriginalFilename());
      // log.append(System.getProperty("line.separator"));
      // log.append("File Title: " + googleFile.getTitle());
      // log.append(System.getProperty("line.separator"));
      // log.append("File Description: " + googleFile.getDescription());
      // log.append(System.getProperty("line.separator"));
      // log.append("File Extension: " + googleFile.getFileExtension());
      // log.append(System.getProperty("line.separator"));
      // log.append("File MimeType: " + googleFile.getMimeType());
      // log.append(System.getProperty("line.separator"));
      // log.append("File Version: " + googleFile.getVersion());
      // log.append(System.getProperty("line.separator"));
      //
      // System.out.println(log);

      String versionString;
      
      if (googleFile == null || (googleFile.containsKey("explicitlyTrashed") && googleFile.getExplicitlyTrashed())) {
        //its deleted, move on
        activities.deleteDocument(documentIdentifier);
        continue;
      }

      if (!isDir(googleFile)) {
        String rev = googleFile.getModifiedDate().toStringRfc3339();
        if (StringUtils.isNotEmpty(rev)) {
          StringBuilder sb = new StringBuilder();

          // Acls
          packList(sb,acls,'+');
          if (acls.length > 0) {
            sb.append('+');
            pack(sb,defaultAuthorityDenyToken,'+');
          }
          else
            sb.append('-');

          sb.append(rev);
          versionString = sb.toString();
        } else {
          //a google document that doesn't contain versioning information will NEVER be processed.
          // I don't know what this means, and whether it can ever occur.
          activities.deleteDocument(documentIdentifier);
          continue;
        }
      } else {
        //a google folder will always be processed
        versionString = StringUtils.EMPTY;
      }

      if (versionString.length() == 0 || activities.checkDocumentNeedsReindexing(documentIdentifier,versionString)) {
        long startTime = System.currentTimeMillis();
        String errorCode = null;
        String errorDesc = StringUtils.EMPTY;
        Long fileSize = null;
        boolean doLog = false;
        String nodeId = documentIdentifier;
        String version = versionString;

        try {
          if (Logging.connectors.isDebugEnabled()) {
            Logging.connectors.debug("GOOGLEDRIVE: Processing document identifier '"
                + nodeId + "'");
            Logging.connectors.debug("GOOGLEDRIVE: have this file:\t" + googleFile.getTitle());
          }

          if ("application/vnd.google-apps.folder".equals(googleFile.getMimeType())) {
            //if directory add its children

            if (Logging.connectors.isDebugEnabled()) {
              Logging.connectors.debug("GOOGLEDRIVE: its a directory");
            }

            // adding all the children + subdirs for a folder

            getSession();
            GetChildrenThread t = new GetChildrenThread(nodeId);
            try {
              t.start();
              boolean wasInterrupted = false;
              try {
                XThreadStringBuffer childBuffer = t.getBuffer();
                // Pick up the paths, and add them to the activities, before we join with the child thread.
                while (true) {
                  // The only kind of exceptions this can throw are going to shut the process down.
                  String child = childBuffer.fetch();
                  if (child ==  null)
                    break;
                  // Add the pageID to the queue
                  activities.addDocumentReference(child, nodeId, RELATIONSHIP_CHILD);
                }
              } catch (InterruptedException e) {
                wasInterrupted = true;
                throw e;
              } catch (ManifoldCFException e) {
                if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
                  wasInterrupted = true;
                throw e;
              } finally {
                if (!wasInterrupted)
                  t.finishUp();
              }
            } catch (InterruptedException e) {
              t.interrupt();
              throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
                ManifoldCFException.INTERRUPTED);
            } catch (java.net.SocketTimeoutException e) {
              Logging.connectors.warn("GOOGLEDRIVE: Socket timeout adding child documents: " + e.getMessage(), e);
              handleIOException(e);
            } catch (InterruptedIOException e) {
              t.interrupt();
              throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
                ManifoldCFException.INTERRUPTED);
            } catch (IOException e) {
              Logging.connectors.warn("GOOGLEDRIVE: Error adding child documents: " + e.getMessage(), e);
              handleIOException(e);
            }

          } else {
            // its a file
            doLog = true;

            if (Logging.connectors.isDebugEnabled()) {
              Logging.connectors.debug("GOOGLEDRIVE: its a file");
            }

            // Get the file length
            Long fileLengthLong = Objects.firstNonNull(googleFile.getFileSize(), 0L);
            if (fileLengthLong != null) {

              // Now do standard stuff
              long fileLength = fileLengthLong.longValue();
              String mimeType = googleFile.getMimeType();
              DateTime createdDateObject = googleFile.getCreatedDate();
              DateTime modifiedDateObject = googleFile.getModifiedDate();
              String extension = googleFile.getFileExtension();
              String title = cleanupFileFolderName(googleFile.getTitle());
              Date createdDate = (createdDateObject==null)?null:new Date(createdDateObject.getValue());
              Date modifiedDate = (modifiedDateObject==null)?null:new Date(modifiedDateObject.getValue());
              // We always direct to the PDF except for Spreadsheets
              String documentURI = null;
              // if (!mimeType.equals("application/vnd.google-apps.spreadsheet")) {
              // documentURI = getUrl(googleFile, "application/pdf");
              // } else {
              // documentURI = getUrl(googleFile,
              // "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
              // }

              switch (mimeType) {
                case "application/vnd.google-apps.spreadsheet":
                  documentURI = getUrl(googleFile, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                  break;

                case "application/vnd.google-apps.document":
                  documentURI = getUrl(googleFile, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                  break;

                case "application/vnd.google-apps.presentation":
                  documentURI = getUrl(googleFile, "application/vnd.openxmlformats-officedocument.presentationml.presentation");
                  break;

                default:
                  documentURI = getUrl(googleFile, "application/pdf");
                  break;
              }

              // Google native format documents may exist, but have 0 byte in size.
              // In cases like this, there is no way to export it, and because of that, it is going to be ignored
              if (documentURI == null) {
                  errorCode = "NOLENGTH";
                  errorDesc = "Document "+nodeId+" had no length; skipping";
                  continue;
              }

              String fullContentPath = getDocumentContentPath(googleFile, documentURI);
              
              // Append the new parameters in the query string
              if (StringUtils.contains(documentURI, '?')) {
                documentURI = documentURI + "&" + CONTENT_PATH_PARAM + "=" + fullContentPath;
              } else {
                documentURI = documentURI + "?" + CONTENT_PATH_PARAM + "=" + fullContentPath;
              }

              if (!activities.checkLengthIndexable(fileLength)) {
                errorCode = activities.EXCLUDED_LENGTH;
                errorDesc = "Excluding document because of file length ('"+fileLength+"')";
                activities.noDocument(nodeId,version);
                continue;
              }
              
              if (!activities.checkURLIndexable(documentURI))
              {
                errorCode = activities.EXCLUDED_URL;
                errorDesc = "Excluding document because of URL ('"+documentURI+"')";
                activities.noDocument(nodeId,version);
                continue;
              }
              
              if (!activities.checkMimeTypeIndexable(mimeType))
              {
                errorCode = activities.EXCLUDED_MIMETYPE;
                errorDesc = "Excluding document because of mime type ("+mimeType+")";
                activities.noDocument(nodeId,version);
                continue;
              }
              
              if (!activities.checkDateIndexable(modifiedDate))
              {
                errorCode = activities.EXCLUDED_DATE;
                errorDesc = "Excluding document because of date ("+modifiedDate+")";
                activities.noDocument(nodeId,version);
                continue;
              }
              
              RepositoryDocument rd = new RepositoryDocument();

              if (acls != null) {
                rd.setSecurityACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT,acls);
                if (acls.length > 0) {
                  String[] denyAclArray = new String[]{defaultAuthorityDenyToken};
                  rd.setSecurityDenyACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT,denyAclArray);
                }
              }
              
              if (mimeType != null)
                rd.setMimeType(getFixedMimeType(mimeType));
              if (createdDate != null)
                rd.setCreatedDate(createdDate);
              if (modifiedDate != null)
                rd.setModifiedDate(modifiedDate);
              if (extension != null)
              {
                if (title == null)
                  title = "";

                if (StringUtils.endsWithIgnoreCase(title, "." + extension)) {
                  rd.setFileName(title);
                } else {
                  String name = title + "." + extension;
                  
                  if (StringUtils.endsWithIgnoreCase(name, ".")) {
                    name = StringUtils.chomp(name, ".");
                  }
                  
                  rd.setFileName(name);
                }
              } else {
                if (title == null)
                  title = "";
              
                String name = title + "." + getExtensionByMimeType(mimeType);
                
                if (StringUtils.endsWithIgnoreCase(name, ".")) {
                    name = StringUtils.chomp(name, ".");
                }
                rd.setFileName(name);
              }

              // Get general document metadata
              for (Entry<String, Object> entry : googleFile.entrySet()) {
                rd.addField(entry.getKey(), entry.getValue().toString());
              }

              // Fire up the document reading thread
              DocumentReadingThread t = new DocumentReadingThread(documentURI);
              try {
                t.start();
                boolean wasInterrupted = false;
                try {
                  InputStream is = t.getSafeInputStream();
                  try {
                    // Can only index while background thread is running!
                	  
                	//filter the fields selected in the query
                	List<String> sourcePath = new ArrayList<>();
                	sourcePath.add(fullContentPath);
                	rd.setSourcePath(sourcePath);
                    //ingestion
                	  
                    rd.setBinary(is, fileLength);
                    activities.ingestDocumentWithException(nodeId, version, documentURI, rd);
                  } finally {
                    is.close();
                  }
                } catch (ManifoldCFException e) {
                  if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
                    wasInterrupted = true;
                  throw e;
                } catch (java.net.SocketTimeoutException e) {
                  throw e;
                } catch (InterruptedIOException e) {
                  wasInterrupted = true;
                  throw e;
                } finally {
                  if (!wasInterrupted)
                    t.finishUp();
                }

                // No errors.  Record the fact that we made it.
                fileSize = new Long(fileLength);
                errorCode = "OK";
              } catch (InterruptedException e) {
                t.interrupt();
                throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
                  ManifoldCFException.INTERRUPTED);
              } catch (java.net.SocketTimeoutException e) {
                Logging.connectors.warn("GOOGLEDRIVE: Socket timeout reading document: " + e.getMessage(), e);
                handleIOException(e);
              } catch (InterruptedIOException e) {
                t.interrupt();
                throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
                  ManifoldCFException.INTERRUPTED);
              } catch (IOException e) {
                errorCode = "IOEXCEPTION";
                errorDesc = e.getMessage();
                Logging.connectors.warn("GOOGLEDRIVE: Error reading document: " + e.getMessage(), e);
                handleIOException(e);
              }
            } else {
              errorCode = "NOLENGTH";
              errorDesc = "Document "+nodeId+" had no length; skipping";
            }
          }
        } finally {
          if (doLog && errorCode != null)
            activities.recordActivity(new Long(startTime), ACTIVITY_READ,
              fileSize, nodeId, errorCode, errorDesc, null);
        }
      }
    }

  }

  private String getFixedMimeType(String mimeType) {
    switch (mimeType) {
      case "application/vnd.google-apps.spreadsheet":
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

      case "application/vnd.google-apps.document":
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    		
      case "application/vnd.google-apps.presentation":
        return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    		
      default:
        return mimeType;
    }
  }

  private String getExtensionByMimeType(String mimeType) {
    switch (mimeType) {
      case "application/vnd.google-apps.spreadsheet":
      return "xlsx";

      case "application/vnd.google-apps.document":
        return "docx";

      case "application/vnd.google-apps.presentation":
        return "pptx";

      default:
        return null;
    }
  }

  private String getDocumentContentPath(File googleFile, String documentURI) {
    String fullContentPath = null;
	try {
      if (!isDir(googleFile)) {
        if (googleFile.getParents() != null && !googleFile.getParents().isEmpty()) {
          ParentReference parentRef = googleFile.getParents().get(0);
          File parent;

          parent = getObject(parentRef.getId());

          String path = getFilePath(parent);
          String name;
          String title = cleanupFileFolderName(googleFile.getTitle());

          String extension = googleFile.getFileExtension();

          if (extension != null) {
            if (title == null)
              title = "";

            if (StringUtils.endsWithIgnoreCase(title, "." + extension)) {
              name = title;
            } else {
              name = title + "." + extension;
            }
          } else {
            if (title == null)
              title = "";
            name = title + "." + getExtensionByMimeType(googleFile.getMimeType());
          }

          if (StringUtils.endsWithIgnoreCase(name, ".")) {
            name = StringUtils.chomp(name, ".");
          }
          
          fullContentPath = path + SLASH + StringUtils.trim(name);
        }
      } else {
        String path = getFilePath(googleFile);
        String name = cleanupFileFolderName(googleFile.getTitle());
        fullContentPath = path + SLASH + name;
      }
    } catch (ManifoldCFException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (ServiceInterruption e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
	}
    return fullContentPath;
  }

  private String getFilePath(File file) throws IOException, ManifoldCFException, ServiceInterruption {
    String folderPath = "";
    String fullFilePath = null;

    List<ParentReference> parentReferencesList = file.getParents();
    List<String> folderList = new ArrayList<String>();

    List<String> finalFolderList = getfoldersList(parentReferencesList, folderList);
    Collections.reverse(finalFolderList);

    for (String folder : finalFolderList) {
      folderPath += "/" + folder;
    }

    fullFilePath = folderPath + "/" + cleanupFileFolderName(file.getTitle());

    return fullFilePath;
  }

  private List<String> getfoldersList(List<ParentReference> parentReferencesList, List<String> folderList)
    throws IOException, ManifoldCFException, ServiceInterruption {
    for (int i = 0; i < parentReferencesList.size(); i++) {
      String id = parentReferencesList.get(i).getId();

      File file = getObject(id);
      folderList.add(cleanupFileFolderName(file.getTitle()));

      if (!(file.getParents().isEmpty())) {
        List<ParentReference> parentReferenceslist2 = file.getParents();
        getfoldersList(parentReferenceslist2, folderList);
      }
    }
    return folderList;
  }

  protected class DocumentReadingThread extends Thread {

    protected Throwable exception = null;    protected final String fileURL;
    protected final XThreadInputStream stream;
    
    public DocumentReadingThread(String fileURL) {
      super();
      this.fileURL = fileURL;
      this.stream = new XThreadInputStream();
      setDaemon(true);
    }

    @Override
    public void run() {
      try {
        session.getGoogleDriveOutputStream(stream, fileURL);
      } catch (Throwable e) {
        this.exception = e;
      }
    }

    public InputStream getSafeInputStream() {
      return stream;
    }
    
    public void finishUp()
      throws InterruptedException, IOException
    {
      // This will be called during the finally
      // block in the case where all is well (and
      // the stream completed) and in the case where
      // there were exceptions.
      stream.abort();
      join();
      Throwable thr = exception;
      if (thr != null) {
        if (thr instanceof IOException)
          throw (IOException) thr;
        else if (thr instanceof RuntimeException)
          throw (RuntimeException) thr;
        else if (thr instanceof Error)
          throw (Error) thr;
        else
          throw new RuntimeException("Unhandled exception of type: "+thr.getClass().getName(),thr);
      }
    }

  }

  /** Get the URL of a file in google land.
  */
  protected static String getUrl(File googleFile, String exportType) {
    if (googleFile.containsKey("fileSize")) {
      return googleFile.getDownloadUrl();
    } else {
      return (googleFile.getExportLinks() != null) ? googleFile.getExportLinks().get(exportType) : null;
    }
  }

  protected class GetChildrenThread extends Thread {

    protected Throwable exception = null;
    protected final String nodeId;
    protected final XThreadStringBuffer childBuffer;
    
    public GetChildrenThread(String nodeId) {
      super();
      this.nodeId = nodeId;
      this.childBuffer = new XThreadStringBuffer();
      setDaemon(true);
    }

    @Override
    public void run() {
      try {
        session.getChildren(childBuffer, nodeId);
      } catch (Throwable e) {
        this.exception = e;
      } finally {
        childBuffer.signalDone();
      }
    }

    public XThreadStringBuffer getBuffer() {
      return childBuffer;
    }
    
    public void finishUp()
      throws InterruptedException, IOException {
      childBuffer.abandon();
      join();
      Throwable thr = exception;
      if (thr != null) {
        if (thr instanceof IOException)
          throw (IOException) thr;
        else if (thr instanceof RuntimeException)
          throw (RuntimeException) thr;
        else if (thr instanceof Error)
          throw (Error) thr;
        else
          throw new RuntimeException("Unhandled exception of type: "+thr.getClass().getName(),thr);
      }
    }
  }

  /** Grab forced acl out of document specification.
  *@param spec is the document specification.
  *@return the acls.
  */
  protected static String[] getAcls(Specification spec) {
    Set<String> map = new HashSet<String>();
    for (int i = 0; i < spec.getChildCount(); i++) {
      SpecificationNode sn = spec.getChild(i);
      if (sn.getType().equals(JOB_ACCESS_NODE_TYPE)) {
        String token = sn.getAttributeValue(JOB_TOKEN_ATTRIBUTE);
        map.add(token);
      }
    }

    String[] rval = new String[map.size()];
    Iterator<String> iter = map.iterator();
    int i = 0;
    while (iter.hasNext()) {
      rval[i++] = (String)iter.next();
    }
    return rval;
  }

  private boolean isDir(File f) {
    return f.getMimeType().compareToIgnoreCase("application/vnd.google-apps.folder") == 0;
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
  
  private static void handleGeneralSecurityException(GeneralSecurityException e)
    throws ManifoldCFException, ServiceInterruption {
    // Permanent problem: can't initialize transport layer
    throw new ManifoldCFException("GoogleDrive exception: "+e.getMessage(), e);
  }
  
  private String cleanupFileFolderName(String name) {
	  name = name.trim();
	  name = name.replaceAll("[\\\\/:*?\"<>%|]", "_");
	  if (StringUtils.endsWithIgnoreCase(name, ".")) {
	    name = StringUtils.chomp(name, ".");
	  }
	  return name;
  }
}