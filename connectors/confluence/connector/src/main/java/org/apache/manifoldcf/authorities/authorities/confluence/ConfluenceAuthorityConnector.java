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

package org.apache.manifoldcf.authorities.authorities.confluence;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.authorities.authorities.BaseAuthorityConnector;
import org.apache.manifoldcf.authorities.interfaces.AuthorizationResponse;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPasswordMapperActivity;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.crawler.connectors.confluence.ConfluenceConfiguration;
import org.apache.manifoldcf.crawler.connectors.confluence.client.ConfluenceClient;
import org.apache.manifoldcf.crawler.connectors.confluence.model.ConfluenceUser;
import org.apache.manifoldcf.authorities.system.Logging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Confluence Authority Connector class
 * </p>
 * <p>
 * ManifoldCF Authority connector to deal with Confluence documents
 * </p>
 * 
 * @author Antonio David Perez Morales <adperezmorales@gmail.com>
 *
 */
public class ConfluenceAuthorityConnector extends BaseAuthorityConnector {

  /*
   * Prefix for Confluence configuration and specification parameters
   */
  private static final String PARAMETER_PREFIX = "confluence_";

  /* Configuration tabs */
  private static final String CONF_SERVER_TAB_PROPERTY = "ConfluenceAuthorityConnector.Server";

  // pages & js
  // Template names for Confluence configuration
  /**
   * Forward to the javascript to check the configuration parameters
   */
  private static final String EDIT_CONFIG_HEADER_FORWARD = "editConfiguration_conf.js";
  /**
   * Server tab template
   */
  private static final String EDIT_CONFIG_FORWARD_SERVER = "editConfiguration_conf_server.html";

  /**
   * Forward to the HTML template to view the configuration parameters
   */
  private static final String VIEW_CONFIG_FORWARD = "viewConfiguration_conf.html";


  private Logger logger = LoggerFactory
      .getLogger(ConfluenceAuthorityConnector.class);

  /* Confluence instance parameters */
  protected String protocol = null;
  protected String host = null;
  protected String port = null;
  protected String path = null;
  protected String username = null;
  protected String password = null;

  protected ConfluenceClient confluenceClient = null;

  /**
   * <p>
   * Default constructor
   * </p>
   */
  public ConfluenceAuthorityConnector() {
    super();
  }
  
  /**
   * Used Mainly for testing
   * 
   * @param client Injected Confluence Client
   */
  public void setConfluenceClient(ConfluenceClient client){
    this.confluenceClient = client;
  }

  

  /**
   * Close the connection. Call this before discarding the connection.
   */
  @Override
  public void disconnect() throws ManifoldCFException {
    if (confluenceClient != null) {
      confluenceClient = null;
    }

    protocol = null;
    host = null;
    port = null;
    path = null;
    username = null;
    password = null;

  }

  /**
   * Makes connection to server
   * 
   * 
   */
  @Override
  public void connect(ConfigParams configParams) {
    super.connect(configParams);

    protocol = params.getParameter(ConfluenceConfiguration.Server.PROTOCOL);
    host = params.getParameter(ConfluenceConfiguration.Server.HOST);
    port = params.getParameter(ConfluenceConfiguration.Server.PORT);
    path = params.getParameter(ConfluenceConfiguration.Server.PATH);
    username = params.getParameter(ConfluenceConfiguration.Server.USERNAME);
    password = params
        .getObfuscatedParameter(ConfluenceConfiguration.Server.PASSWORD);

    try {
      initConfluenceClient();
    } catch (ManifoldCFException e) {
      logger.debug(
          "Not possible to initialize Confluence client. Reason: {}",
          e.getMessage());
    }
  }

  /**
   * Checks if connection is available
   */
  @Override
  public String check() throws ManifoldCFException {
    try {
      if (!isConnected()) {
        initConfluenceClient();
      }
      Boolean result = confluenceClient.checkAuth();
      if (result)
        return super.check();
      else
        throw new ManifoldCFException(
            "Confluence instance could not be reached");
    } catch (ServiceInterruption e) {
      return "Connection temporarily failed: " + e.getMessage();
    } catch (ManifoldCFException e) {
      return "Connection failed: " + e.getMessage();
    } catch (Exception e) {
      return "Connection failed: " + e.getMessage();
    }
  }

  /**
   * <p>
   * Initialize Confluence client using the configured parameters
   * 
   * @throws ManifoldCFException
   */
  protected void initConfluenceClient() throws ManifoldCFException {
    if (confluenceClient == null) {

      if (StringUtils.isEmpty(protocol)) {
        throw new ManifoldCFException("Parameter "
            + ConfluenceConfiguration.Server.PROTOCOL
            + " required but not set");
      }

      if (Logging.authorityConnectors.isDebugEnabled()) {
        Logging.authorityConnectors.debug("Confluence protocol = '" + protocol
            + "'");
      }

      if (StringUtils.isEmpty(host)) {
        throw new ManifoldCFException("Parameter "
            + ConfluenceConfiguration.Server.HOST
            + " required but not set");
      }

      if (Logging.authorityConnectors.isDebugEnabled()) {
        Logging.authorityConnectors.debug("Confluence host = '" + host + "'");
      }

      if (Logging.authorityConnectors.isDebugEnabled()) {
        Logging.authorityConnectors.debug("Confluence port = '" + port + "'");
      }

      if (StringUtils.isEmpty(path)) {
        throw new ManifoldCFException("Parameter "
            + ConfluenceConfiguration.Server.PATH
            + " required but not set");
      }

      if (Logging.authorityConnectors.isDebugEnabled()) {
        Logging.authorityConnectors.debug("Confluence path = '" + path + "'");
      }

      if (Logging.authorityConnectors.isDebugEnabled()) {
        Logging.authorityConnectors.debug("Confluence username = '" + username
            + "'");
      }

      if (Logging.authorityConnectors.isDebugEnabled()) {
        Logging.authorityConnectors
            .debug("Confluence password '" + password != null ? "set"
                : "not set" + "'");
      }

      int portInt;
      if (port != null && port.length() > 0) {
        try {
          portInt = Integer.parseInt(port);
        } catch (NumberFormatException e) {
          throw new ManifoldCFException("Bad number: "
              + e.getMessage(), e);
        }
      } else {
        if (protocol.toLowerCase(Locale.ROOT).equals("http"))
          portInt = 80;
        else
          portInt = 443;
      }

      /* Generating a client to perform Confluence requests */
      confluenceClient = new ConfluenceClient(protocol, host, portInt,
          path, username, password);
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
    return confluenceClient != null;
  }


  private void fillInServerConfigurationMap(Map<String, String> serverMap,
      IPasswordMapperActivity mapper, ConfigParams parameters) {
    String confluenceProtocol = parameters
        .getParameter(ConfluenceConfiguration.Server.PROTOCOL);
    String confluenceHost = parameters
        .getParameter(ConfluenceConfiguration.Server.HOST);
    String confluencePort = parameters
        .getParameter(ConfluenceConfiguration.Server.PORT);
    String confluencePath = parameters
        .getParameter(ConfluenceConfiguration.Server.PATH);
    String confluenceUsername = parameters
        .getParameter(ConfluenceConfiguration.Server.USERNAME);
    String confluencePassword = parameters
        .getObfuscatedParameter(ConfluenceConfiguration.Server.PASSWORD);

    if (confluenceProtocol == null)
      confluenceProtocol = ConfluenceConfiguration.Server.PROTOCOL_DEFAULT_VALUE;
    if (confluenceHost == null)
      confluenceHost = ConfluenceConfiguration.Server.HOST_DEFAULT_VALUE;
    if (confluencePort == null)
      confluencePort = ConfluenceConfiguration.Server.PORT_DEFAULT_VALUE;
    if (confluencePath == null)
      confluencePath = ConfluenceConfiguration.Server.PATH_DEFAULT_VALUE;

    if (confluenceUsername == null)
      confluenceUsername = ConfluenceConfiguration.Server.USERNAME_DEFAULT_VALUE;
    if (confluencePassword == null)
      confluencePassword = ConfluenceConfiguration.Server.PASSWORD_DEFAULT_VALUE;
    else
      confluencePassword = mapper.mapPasswordToKey(confluencePassword);

    serverMap.put(PARAMETER_PREFIX
        + ConfluenceConfiguration.Server.PROTOCOL, confluenceProtocol);
    serverMap.put(PARAMETER_PREFIX + ConfluenceConfiguration.Server.HOST,
        confluenceHost);
    serverMap.put(PARAMETER_PREFIX + ConfluenceConfiguration.Server.PORT,
        confluencePort);
    serverMap.put(PARAMETER_PREFIX + ConfluenceConfiguration.Server.PATH,
        confluencePath);
    serverMap.put(PARAMETER_PREFIX
        + ConfluenceConfiguration.Server.USERNAME, confluenceUsername);
    serverMap.put(PARAMETER_PREFIX
        + ConfluenceConfiguration.Server.PASSWORD, confluencePassword);
  }

  @Override
  public void viewConfiguration(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters)
      throws ManifoldCFException, IOException {
    Map<String, String> paramMap = new HashMap<String, String>();

    /* Fill server configuration parameters */
    fillInServerConfigurationMap(paramMap, out, parameters);

    Messages.outputResourceWithVelocity(out, locale, VIEW_CONFIG_FORWARD,
        paramMap, true);
  }

  @Override
  public void outputConfigurationHeader(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters,
      List<String> tabsArray) throws ManifoldCFException, IOException {
    // Add the Server tab
    tabsArray.add(Messages.getString(locale, CONF_SERVER_TAB_PROPERTY));
    // Map the parameters
    Map<String, String> paramMap = new HashMap<String, String>();

    /* Fill server configuration parameters */
    fillInServerConfigurationMap(paramMap, out, parameters);

    // Output the Javascript - only one Velocity template for all tabs
    Messages.outputResourceWithVelocity(out, locale,
        EDIT_CONFIG_HEADER_FORWARD, paramMap, true);
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters,
      String tabName) throws ManifoldCFException, IOException {

    // Call the Velocity templates for each tab
    Map<String, String> paramMap = new HashMap<String, String>();
    // Set the tab name
    paramMap.put("TabName", tabName);

    // Fill in the parameters
    fillInServerConfigurationMap(paramMap, out, parameters);

    // Server tab
    Messages.outputResourceWithVelocity(out, locale,
        EDIT_CONFIG_FORWARD_SERVER, paramMap, true);

  }

  /*
   * Repository specification post handle, (server and proxy & client secret
   * etc)
   * 
   * @see
   * org.apache.manifoldcf.core.connector.BaseConnector#processConfigurationPost
   * (org.apache.manifoldcf.core.interfaces.IThreadContext,
   * org.apache.manifoldcf.core.interfaces.IPostParameters,
   * org.apache.manifoldcf.core.interfaces.ConfigParams)
   */
  @Override
  public String processConfigurationPost(IThreadContext threadContext,
      IPostParameters variableContext, ConfigParams parameters)
      throws ManifoldCFException {

    String confluenceProtocol = variableContext
        .getParameter(PARAMETER_PREFIX
            + ConfluenceConfiguration.Server.PROTOCOL);
    if (confluenceProtocol != null)
      parameters.setParameter(ConfluenceConfiguration.Server.PROTOCOL,
          confluenceProtocol);

    String confluenceHost = variableContext.getParameter(PARAMETER_PREFIX
        + ConfluenceConfiguration.Server.HOST);
    if (confluenceHost != null)
      parameters.setParameter(ConfluenceConfiguration.Server.HOST,
          confluenceHost);

    String confluencePort = variableContext.getParameter(PARAMETER_PREFIX
        + ConfluenceConfiguration.Server.PORT);
    if (confluencePort != null)
      parameters.setParameter(ConfluenceConfiguration.Server.PORT,
          confluencePort);

    String confluencePath = variableContext.getParameter(PARAMETER_PREFIX
        + ConfluenceConfiguration.Server.PATH);
    if (confluencePath != null)
      parameters.setParameter(ConfluenceConfiguration.Server.PATH,
          confluencePath);

    String confluenceUsername = variableContext
        .getParameter(PARAMETER_PREFIX
            + ConfluenceConfiguration.Server.USERNAME);
    if (confluenceUsername != null)
      parameters.setParameter(ConfluenceConfiguration.Server.USERNAME,
          confluenceUsername);

    String confluencePassword = variableContext
        .getParameter(PARAMETER_PREFIX
            + ConfluenceConfiguration.Server.PASSWORD);
    if (confluencePassword != null)
      parameters.setObfuscatedParameter(
          ConfluenceConfiguration.Server.PASSWORD,
          variableContext.mapKeyToPassword(confluencePassword));

    /* null means process configuration has been successful */
    return null;
  }
  
    /*
     * (non-Javadoc)
     * @see org.apache.manifoldcf.authorities.authorities.BaseAuthorityConnector#getDefaultAuthorizationResponse(java.lang.String)
     */
    @Override
    public AuthorizationResponse getDefaultAuthorizationResponse(String userName) {
      return RESPONSE_UNREACHABLE;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.manifoldcf.authorities.authorities.BaseAuthorityConnector#getAuthorizationResponse(java.lang.String)
     */
    @Override
    public AuthorizationResponse getAuthorizationResponse(String userName)
        throws ManifoldCFException {
      try {
        ConfluenceUser confluenceUser = confluenceClient.getUserAuthorities(userName);
        if (confluenceUser.getUsername() == null
            || confluenceUser.getUsername().isEmpty()
            || confluenceUser.getAuthorities().isEmpty())
          return RESPONSE_USERNOTFOUND;
        else
          return new AuthorizationResponse(
              confluenceUser.getAuthorities().toArray(new String[confluenceUser.getAuthorities().size()]),
              AuthorizationResponse.RESPONSE_OK);
      } catch (Exception e) {
        return RESPONSE_UNREACHABLE;
      }
    }

}
