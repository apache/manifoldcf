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
package org.apache.manifoldcf.crawler.notifications.rocketchat;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPasswordMapperActivity;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.crawler.system.Logging;

/**
 */
public class RocketChatConnector extends org.apache.manifoldcf.crawler.notifications.BaseNotificationConnector {

  protected final static long SESSION_EXPIRATION_MILLISECONDS = 300000L;

  // Local variables.
  protected long sessionExpiration = -1L;

  // Parameters for establishing a session

  protected String serverUrl = null;
  protected String user;
  protected String password;

  // Parameters for proxy connection
  protected RocketChatSession.ProxySettings proxySettings = null;

  // Local session handle
  protected RocketChatSession session = null;

  //////////////////////////////////Start of Basic Connector Methods/////////////////////////

  /**
   * Connect.
   *
   * @param configParameters is the set of configuration parameters, which
   * in this case describe the root directory.
   */
  @Override
  public void connect(ConfigParams configParameters) {
    super.connect(configParameters);
    this.serverUrl = configParameters.getParameter(RocketChatConfig.SERVER_URL_PARAM);
    this.user = configParameters.getParameter(RocketChatConfig.USER_PARAM);
    this.password = configParameters.getObfuscatedParameter(RocketChatConfig.PASSWORD_PARAM);

    String proxyHost = configParameters.getParameter(RocketChatConfig.PROXY_HOST_PARAM);
    String proxyPortString = configParameters.getParameter(RocketChatConfig.PROXY_PORT_PARAM);
    if(StringUtils.isNotEmpty(proxyHost) && StringUtils.isNotEmpty(proxyPortString)) {
      String proxyUsername = configParameters.getParameter(RocketChatConfig.PROXY_USERNAME_PARAM);
      String proxyPassword = configParameters.getObfuscatedParameter(RocketChatConfig.PROXY_PASSWORD_PARAM);
      String proxyDomain = configParameters.getParameter(RocketChatConfig.PROXY_DOMAIN_PARAM);
      this.proxySettings = new RocketChatSession.ProxySettings(proxyHost, proxyPortString, proxyUsername, proxyPassword, proxyDomain);
    } else {
      Logging.connectors.info("Using no proxy settings - no proxyHost and no proxyPort found.");
    }
  }

  /**
   * Close the connection. Call this before discarding this instance of the
   * repository connector.
   */
  @Override
  public void disconnect()
      throws ManifoldCFException {
    this.serverUrl = null;
    this.user = null;
    this.password = null;
    this.proxySettings = null;
    finalizeConnection();
    super.disconnect();
  }

  /**
   * This method is periodically called for all connectors that are connected but not
   * in active use.
   */
  @Override
  public void poll() throws ManifoldCFException {
    if (session != null)
    {
      if (System.currentTimeMillis() >= sessionExpiration)
        finalizeConnection();
    }
  }

  /**
   * Test the connection. Returns a string describing the connection integrity.
   *
   * @return the connection's status as a displayable string.
   */
  @Override
  public String check()
      throws ManifoldCFException {
    try {
      checkConnection();
      return super.check();
    } catch (ServiceInterruption e) {
      return "Connection temporarily failed: " + e.getMessage();
    } catch (ManifoldCFException e) {
      return "Connection failed: " + e.getMessage();
    }
  }

  protected void checkConnection() throws ManifoldCFException, ServiceInterruption {
    // Force a re-connection
    finalizeConnection();
    getSession();
    try {
      CheckConnectionThread cct = new CheckConnectionThread(session);
      cct.start();
      cct.finishUp();
    } catch (InterruptedException e) {
      throw new ManifoldCFException(e.getMessage(),ManifoldCFException.INTERRUPTED);
    } catch (IOException e) {
      handleIOException(e,"checking the connection");
    }
  }

  protected void getSession()
      throws ManifoldCFException, ServiceInterruption {
    if (session == null) {

      // Check that all the required parameters are there.
      if (serverUrl == null)
        throw new ManifoldCFException("Missing serverUrl parameter");
      if (user == null)
        throw new ManifoldCFException("Missing user parameter");
      if (password == null)
        throw new ManifoldCFException("Missing password parameter");

      // Create a session.
      try {
        ConnectThread connectThread = new ConnectThread(serverUrl, user, password, proxySettings);
        connectThread.start();
        session = connectThread.finishUp();
      } catch (InterruptedException e) {
        throw new ManifoldCFException(e.getMessage(),ManifoldCFException.INTERRUPTED);
      } catch (IOException e) {
        handleIOException(e, "connecting");
      }
    }
    sessionExpiration = System.currentTimeMillis() + SESSION_EXPIRATION_MILLISECONDS;
  }

  protected void finalizeConnection() {
    if (session != null) {
      try {
        CloseSessionThread closeSessionThread = new CloseSessionThread(session);
        closeSessionThread.start();
        closeSessionThread.finishUp();
      } catch (InterruptedException e) {
      } catch (IOException e) {
        Logging.connectors.warn("Error while closing connection to server: " + e.getMessage(),e);
      } finally {
        session = null;
      }
    }
  }

  ///////////////////////////////End of Basic Connector Methods////////////////////////////////////////

  //////////////////////////////Start of Notification Connector Method///////////////////////////////////

  /** Notify of job stop due to error abort.
   *@param spec is the notification specification.
   */
  @Override
  public void notifyOfJobStopErrorAbort(final Specification spec)
      throws ManifoldCFException, ServiceInterruption {
    sendRocketChatMessage(spec, RocketChatConfig.NODE_ERRORABORTED);
  }

  /** Notify of job stop due to manual abort.
   *@param spec is the notification specification.
   */
  @Override
  public void notifyOfJobStopManualAbort(final Specification spec)
      throws ManifoldCFException, ServiceInterruption {
    sendRocketChatMessage(spec, RocketChatConfig.NODE_MANUALABORTED);
  }

  /** Notify of job stop due to manual pause.
   *@param spec is the notification specification.
   */
  @Override
  public void notifyOfJobStopManualPause(final Specification spec)
      throws ManifoldCFException, ServiceInterruption {
    sendRocketChatMessage(spec, RocketChatConfig.NODE_MANUALPAUSED);
  }

  /** Notify of job stop due to schedule pause.
   *@param spec is the notification specification.
   */
  @Override
  public void notifyOfJobStopSchedulePause(final Specification spec)
      throws ManifoldCFException, ServiceInterruption {
    sendRocketChatMessage(spec, RocketChatConfig.NODE_SCHEDULEPAUSED);
  }

  /** Notify of job stop due to restart.
   *@param spec is the notification specification.
   */
  @Override
  public void notifyOfJobStopRestart(final Specification spec)
      throws ManifoldCFException, ServiceInterruption {
    sendRocketChatMessage(spec, RocketChatConfig.NODE_RESTARTED);
  }

  /** Notify of job end.
   *@param spec is the notification specification.
   */
  @Override
  public void notifyOfJobEnd(final Specification spec)
      throws ManifoldCFException, ServiceInterruption {
    sendRocketChatMessage(spec, RocketChatConfig.NODE_FINISHED);
  }

  protected void sendRocketChatMessage(final Specification spec, final String nodeType)
      throws ManifoldCFException, ServiceInterruption
  {
    
    String defaultChannel = null;
    String alias = null;
    String emoji = null;
    String avatar = null;
    
    String channel = "";
    String message = "";
    // Look for node of the specified type
    if (nodeType != null)
    {
      for (int i = 0; i < spec.getChildCount(); i++) {
        SpecificationNode childNode = spec.getChild(i);
        
        // Global settings
        if (childNode.getType().equals(RocketChatConfig.NODE_GLOBALS)) {
          for (int j = 0; j < childNode.getChildCount(); j++) {
            SpecificationNode sn = childNode.getChild(j);
            if (sn.getType().equals(RocketChatConfig.NODE_DEFAULT_CHANNEL)) {
              defaultChannel = sn.getAttributeValue(RocketChatConfig.ATTRIBUTE_VALUE);
            }
            else if (sn.getType().equals(RocketChatConfig.NODE_ALIAS)) {
              alias = sn.getAttributeValue(RocketChatConfig.ATTRIBUTE_VALUE);
            }
            else if (sn.getType().equals(RocketChatConfig.NODE_EMOJI)) {
              emoji = sn.getAttributeValue(RocketChatConfig.ATTRIBUTE_VALUE);
            }
            else if (sn.getType().equals(RocketChatConfig.NODE_AVATAR)) {
              avatar = sn.getAttributeValue(RocketChatConfig.ATTRIBUTE_VALUE);
            }
          }
        }
        
        // notification type specific settings
        else if (childNode.getType().equals(nodeType))
        {
          for (int j = 0; j < childNode.getChildCount(); j++) {
            SpecificationNode sn = childNode.getChild(j);
            if (sn.getType().equals(RocketChatConfig.NODE_CHANNEL))
              channel = sn.getAttributeValue(RocketChatConfig.ATTRIBUTE_VALUE);
            else if (sn.getType().equals(RocketChatConfig.NODE_MESSAGE))
              message = sn.getAttributeValue(RocketChatConfig.ATTRIBUTE_VALUE);
          }
        }
      }
    }

    if (StringUtils.isBlank(message)) {
      return;
    }

    if (StringUtils.isBlank(channel)) {
      if (StringUtils.isBlank(defaultChannel)) {
        return;
      } else {
        channel = defaultChannel;
      }
    }

    // Construct and send a rocketchat message
    getSession();

    RocketChatMessage chatMessage = new RocketChatMessage();
    chatMessage.setChannel(channel);
    chatMessage.setText(message);
    chatMessage.setAlias(alias);
    chatMessage.setEmoji(emoji);
    chatMessage.setAvatar(avatar);

    SendThread st = new SendThread(session, chatMessage);
    st.start();
    try {
      st.finishUp();
    } catch (InterruptedException e) {
      throw new ManifoldCFException(e.getMessage(),ManifoldCFException.INTERRUPTED);
    } catch (IOException e) {
      handleIOException(e,"sending rocketchat message");
    }
  }


  //////////////////////////////End of Notification Connector Methods///////////////////////////////////


  ///////////////////////////////////////Start of Configuration UI/////////////////////////////////////

  /**
   * Output the configuration header section.
   * This method is called in the head section of the connector's configuration page. Its purpose is to
   * add the required tabs to the list, and to output any javascript methods that might be needed by
   * the configuration editing HTML.
   * The connector does not need to be connected for this method to be called.
   *
   * @param threadContext is the local thread context.
   * @param out is the output to which any HTML should be sent.
   * @param locale is the desired locale.
   * @param parameters are the configuration parameters, as they currently exist, for this connection being configured.
   * @param tabsArray is an array of tab names. Add to this array any tab names that are specific to the connector.
   */
  @Override
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out,
      Locale locale, ConfigParams parameters, List<String> tabsArray)
          throws ManifoldCFException, IOException {
    tabsArray.add(Messages.getString(locale, "RocketChatConnector.RestAPI"));
    // Map the parameters
    Map<String, Object> paramMap = new HashMap<String, Object>();

    // Fill in the parameters from each tab
    fillInServerConfigurationMap(paramMap, out, parameters);

    // Output the Javascript - only one Velocity template for all tabs
    Messages.outputResourceWithVelocity(out, locale, "ConfigurationHeader.js", paramMap);
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out,
      Locale locale, ConfigParams parameters, String tabName)
          throws ManifoldCFException, IOException {
    // Output the Server tab
    Map<String, Object> paramMap = new HashMap<String, Object>();
    // Set the tab name
    paramMap.put("TabName", tabName);
    // Fill in the parameters
    fillInServerConfigurationMap(paramMap, out, parameters);
    Messages.outputResourceWithVelocity(out, locale, "Configuration_Server.html", paramMap);
  }

  private static void fillInServerConfigurationMap(Map<String, Object> paramMap, IPasswordMapperActivity mapper, ConfigParams parameters) {
    String serverUrl = getEmptyOnNull(parameters, RocketChatConfig.SERVER_URL_PARAM);
    String user = getEmptyOnNull(parameters, RocketChatConfig.USER_PARAM);
    String password = parameters.getObfuscatedParameter(RocketChatConfig.PASSWORD_PARAM);
    if (password == null) {
      password = StringUtils.EMPTY;
    } else {
      password = mapper.mapPasswordToKey(password);
    }

    String proxyHost = getEmptyOnNull(parameters, RocketChatConfig.PROXY_HOST_PARAM);
    String proxyPort = getEmptyOnNull(parameters, RocketChatConfig.PROXY_PORT_PARAM);
    String proxyUsername = getEmptyOnNull(parameters, RocketChatConfig.PROXY_USERNAME_PARAM);

    String proxyPassword = parameters.getObfuscatedParameter(RocketChatConfig.PROXY_PASSWORD_PARAM);
    if(proxyPassword == null) {
      proxyPassword = StringUtils.EMPTY;
    } else {
      proxyPassword = mapper.mapPasswordToKey(proxyPassword);
    }

    String proxyDomain = getEmptyOnNull(parameters, RocketChatConfig.PROXY_DOMAIN_PARAM);

    paramMap.put("SERVER_URL", serverUrl);
    paramMap.put("USER", user);
    paramMap.put("PASSWORD", password);
    paramMap.put("PROXY_HOST", proxyHost);
    paramMap.put("PROXY_PORT", proxyPort);
    paramMap.put("PROXY_USERNAME", proxyUsername);
    paramMap.put("PROXY_PASSWORD", proxyPassword);
    paramMap.put("PROXY_DOMAIN", proxyDomain);
  }

  private static String getEmptyOnNull(ConfigParams parameters, String key) {
    String value = parameters.getParameter(key);
    if (value == null) {
      value = StringUtils.EMPTY;
    }
    return value;
  }

  /**
   * Process a configuration post.
   * This method is called at the start of the connector's configuration page, whenever there is a possibility
   * that form data for a connection has been posted. Its purpose is to gather form information and modify
   * the configuration parameters accordingly.
   * The name of the posted form is always "editconnection".
   * The connector does not need to be connected for this method to be called.
   *
   * @param threadContext is the local thread context.
   * @param variableContext is the set of variables available from the post, including binary file post information.
   * @param parameters are the configuration parameters, as they currently exist, for this connection being configured.
   * @return null if all is well, or a string error message if there is an error that should prevent saving of the
   * connection (and cause a redirection to an error page).
   */
  @Override
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext,
      ConfigParams parameters) throws ManifoldCFException {

    final String serverUrl = variableContext.getParameter("serverUrl");
    if (serverUrl != null) {
      parameters.setParameter(RocketChatConfig.SERVER_URL_PARAM, serverUrl);
    }

    final String user = variableContext.getParameter("user");
    if (user != null) {
      parameters.setParameter(RocketChatConfig.USER_PARAM, user);
    }

    final String password = variableContext.getParameter("password");
    if (password != null) {
      parameters.setObfuscatedParameter(RocketChatConfig.PASSWORD_PARAM, variableContext.mapKeyToPassword(password));
    }

    final String proxyHost = variableContext.getParameter("proxyHost");
    if (proxyHost != null) {
      parameters.setParameter(RocketChatConfig.PROXY_HOST_PARAM, proxyHost);
    }

    final String proxyPort = variableContext.getParameter("proxyPort");
    if (StringUtils.isNotEmpty(proxyPort)) {
      try {
        Integer.parseInt(proxyPort);
      } catch (NumberFormatException e) {
        Logging.connectors.warn("Proxy port must be a number. Found " + proxyPort);
        throw new ManifoldCFException("Proxy Port must be a number: " + e.getMessage(), e);
      }
      parameters.setParameter(RocketChatConfig.PROXY_PORT_PARAM, proxyPort);
    } else if(proxyPort != null){
      parameters.setParameter(RocketChatConfig.PROXY_PORT_PARAM, proxyPort);
    }

    final String proxyUsername = variableContext.getParameter("proxyUsername");
    if (proxyUsername != null) {
      parameters.setParameter(RocketChatConfig.PROXY_USERNAME_PARAM, proxyUsername);
    }

    final String proxyPassword = variableContext.getParameter("proxyPassword");
    if (proxyPassword != null) {
      parameters.setObfuscatedParameter(RocketChatConfig.PROXY_PASSWORD_PARAM, variableContext.mapKeyToPassword(proxyPassword));
    }

    final String proxyDomain = variableContext.getParameter("proxyDomain");
    if (proxyDomain != null) {
      parameters.setParameter(RocketChatConfig.PROXY_DOMAIN_PARAM, proxyDomain);
    }

    return null;
  }

  /**
   * View configuration. This method is called in the body section of the
   * connector's view configuration page. Its purpose is to present the
   * connection information to the user. The coder can presume that the HTML that
   * is output from this configuration will be within appropriate <html> and
   * <body> tags.
   *
   * @param threadContext is the local thread context.
   * @param out is the output to which any HTML should be sent.
   * @param parameters are the configuration parameters, as they currently exist, for
   * this connection being configured.
   */
  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
      Locale locale, ConfigParams parameters) throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();

    // Fill in map from each tab
    fillInServerConfigurationMap(paramMap, out, parameters);

    Messages.outputResourceWithVelocity(out, locale, "ConfigurationView.html", paramMap);
  }


  /////////////////////////////////End of configuration UI////////////////////////////////////////////////////


  /////////////////////////////////Start of Specification UI//////////////////////////////////////////////////

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
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("SeqNum", Integer.toString(connectionSequenceNumber));
    // Add the tabs
    tabsArray.add(Messages.getString(locale, "RocketChatConnector.Message"));
    Messages.outputResourceWithVelocity(out, locale, "SpecificationHeader.js", paramMap);
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
    outputMessageTab(out, locale, ds, tabName, connectionSequenceNumber, actualSequenceNumber);
  }

  /**
   * Take care of "Message" tab.
   */
  protected void outputMessageTab(IHTTPOutput out, Locale locale,
      Specification ds, String tabName, int connectionSequenceNumber, int actualSequenceNumber)
          throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("TabName", tabName);
    paramMap.put("SeqNum", Integer.toString(connectionSequenceNumber));
    paramMap.put("SelectedNum", Integer.toString(actualSequenceNumber));
    fillInMessageTab(paramMap, ds);
    Messages.outputResourceWithVelocity(out, locale, "Specification_Message.html", paramMap);
  }

  /**
   * Fill in Velocity context for Metadata tab.
   */
  protected static void fillInMessageTab(Map<String, Object> paramMap,
      Specification ds) {

    // Initialize all records with blanks
    addRecord(paramMap, RocketChatConfig.NODE_FINISHED, "", "");
    addRecord(paramMap, RocketChatConfig.NODE_ERRORABORTED, "", "");
    addRecord(paramMap, RocketChatConfig.NODE_MANUALABORTED, "", "");
    addRecord(paramMap, RocketChatConfig.NODE_MANUALPAUSED, "", "");
    addRecord(paramMap, RocketChatConfig.NODE_SCHEDULEPAUSED, "", "");
    addRecord(paramMap, RocketChatConfig.NODE_RESTARTED, "" ,"");

    String channel;
    String message;
    // Loop through nodes and pick them out that way
    for (int i = 0; i < ds.getChildCount(); i++) {
      SpecificationNode childNode = ds.getChild(i);
      if (childNode.getType().equals(RocketChatConfig.NODE_FINISHED) ||
          childNode.getType().equals(RocketChatConfig.NODE_ERRORABORTED) ||
          childNode.getType().equals(RocketChatConfig.NODE_MANUALABORTED) ||
          childNode.getType().equals(RocketChatConfig.NODE_MANUALPAUSED) ||
          childNode.getType().equals(RocketChatConfig.NODE_SCHEDULEPAUSED) ||
          childNode.getType().equals(RocketChatConfig.NODE_RESTARTED)) {
        channel = "";
        message = "";
        for (int j = 0; j < childNode.getChildCount(); j++) {
          SpecificationNode sn = childNode.getChild(j);
          if (sn.getType().equals(RocketChatConfig.NODE_CHANNEL)) {
            channel = sn.getAttributeValue(RocketChatConfig.ATTRIBUTE_VALUE);
          } else if (sn.getType().equals(RocketChatConfig.NODE_MESSAGE)) {
            message = sn.getAttributeValue(RocketChatConfig.ATTRIBUTE_VALUE);
          }
        }
        addRecord(paramMap, childNode.getType(), channel, message);
      }
    }

    // Global Settings
    
    // Initialize globals with blanks
    paramMap.put(RocketChatConfig.NODE_DEFAULT_CHANNEL, "");
    paramMap.put(RocketChatConfig.NODE_ALIAS, "");
    paramMap.put(RocketChatConfig.NODE_EMOJI, "");
    paramMap.put(RocketChatConfig.NODE_AVATAR, "");
    
    for (int i = 0; i < ds.getChildCount(); i++) {
      SpecificationNode childNode = ds.getChild(i);
      if (childNode.getType().equals(RocketChatConfig.NODE_GLOBALS)) {
        for (int j = 0; j < childNode.getChildCount(); j++) {
          SpecificationNode sn = childNode.getChild(j);
          if (sn.getType().equals(RocketChatConfig.NODE_DEFAULT_CHANNEL)) {
            paramMap.put(RocketChatConfig.NODE_DEFAULT_CHANNEL, sn.getAttributeValue(RocketChatConfig.ATTRIBUTE_VALUE));
          } else if (sn.getType().equals(RocketChatConfig.NODE_ALIAS)) {
            paramMap.put(RocketChatConfig.NODE_ALIAS, sn.getAttributeValue(RocketChatConfig.ATTRIBUTE_VALUE));
          } else if (sn.getType().equals(RocketChatConfig.NODE_EMOJI)) {
            paramMap.put(RocketChatConfig.NODE_EMOJI, sn.getAttributeValue(RocketChatConfig.ATTRIBUTE_VALUE));
          } else if (sn.getType().equals(RocketChatConfig.NODE_AVATAR)) {
            paramMap.put(RocketChatConfig.NODE_AVATAR, sn.getAttributeValue(RocketChatConfig.ATTRIBUTE_VALUE));
          }
        }
      }
      
    }

  }

  protected static void addRecord(Map<String,Object> paramMap, String nodeType, String channel, String message) {
    paramMap.put(nodeType+"_CHANNEL", channel);
    paramMap.put(nodeType+"_MESSAGE", message);
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

    return processMessageTab(variableContext, ds, connectionSequenceNumber);
  }

  protected String processMessageTab(IPostParameters variableContext, Specification ds,
      int connectionSequenceNumber)
          throws ManifoldCFException {

    String seqPrefix = "s"+connectionSequenceNumber+"_";

    // Gather all different kinds.
    gatherRecord(ds, seqPrefix, variableContext, RocketChatConfig.NODE_FINISHED);
    gatherRecord(ds, seqPrefix, variableContext, RocketChatConfig.NODE_ERRORABORTED);
    gatherRecord(ds, seqPrefix, variableContext, RocketChatConfig.NODE_MANUALABORTED);
    gatherRecord(ds, seqPrefix, variableContext, RocketChatConfig.NODE_MANUALPAUSED);
    gatherRecord(ds, seqPrefix, variableContext, RocketChatConfig.NODE_SCHEDULEPAUSED);
    gatherRecord(ds, seqPrefix, variableContext, RocketChatConfig.NODE_RESTARTED);

    // Gather global settings
    SpecificationNode sn = new SpecificationNode(RocketChatConfig.NODE_GLOBALS);
    
    String defaultChannel = variableContext.getParameter(RocketChatConfig.NODE_DEFAULT_CHANNEL);
    if (defaultChannel != null) {
      addNodeValue(sn, RocketChatConfig.NODE_DEFAULT_CHANNEL, defaultChannel);
    }
    String alias = variableContext.getParameter(RocketChatConfig.NODE_ALIAS);
    if (alias != null) {
      addNodeValue(sn, RocketChatConfig.NODE_ALIAS, alias);
    }
    String emoji = variableContext.getParameter(RocketChatConfig.NODE_EMOJI);
    if (emoji != null) {
      addNodeValue(sn, RocketChatConfig.NODE_EMOJI, emoji);
    }
    String avatar = variableContext.getParameter(RocketChatConfig.NODE_AVATAR);
    if (avatar != null) {
      addNodeValue(sn, RocketChatConfig.NODE_AVATAR, avatar);
    }
    ds.addChild(ds.getChildCount(), sn);
    
    return null;
  }

  protected static void gatherRecord(Specification ds, String seqPrefix, IPostParameters variableContext, String nodeType) {
    removeNodes(ds, nodeType);
    SpecificationNode sn = new SpecificationNode(nodeType);
    String channel = variableContext.getParameter(seqPrefix + nodeType + "_channel");
    if (channel != null)
    {
      addNodeValue(sn, RocketChatConfig.NODE_CHANNEL, channel);
    }
    String message = variableContext.getParameter(seqPrefix + nodeType + "_message");
    if (message != null)
    {
      addNodeValue(sn, RocketChatConfig.NODE_MESSAGE, message);
    }
    ds.addChild(ds.getChildCount(),sn);
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
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("SeqNum", Integer.toString(connectionSequenceNumber));
    fillInMessageTab(paramMap, ds);
    Messages.outputResourceWithVelocity(out, locale, "SpecificationView.html", paramMap);
  }

  ///////////////////////////////////////End of specification UI///////////////////////////////////////////////

  protected static void removeNodes(Specification ds, String nodeTypeName) {
    int i = 0;
    while (i < ds.getChildCount()) {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals(nodeTypeName))
        ds.removeChild(i);
      else
        i++;
    }
  }

  protected static void addNodeValue(SpecificationNode ds, String nodeType, String value)
  {
    SpecificationNode sn = new SpecificationNode(nodeType);
    sn.setAttribute(RocketChatConfig.ATTRIBUTE_VALUE,value);
    ds.addChild(ds.getChildCount(),sn);
  }


  /** Handle Messaging exceptions in a consistent global manner */
  protected static void handleIOException(IOException e, String context)
      throws ManifoldCFException, ServiceInterruption
  {
    Logging.connectors.error("RocketChat: Error "+context+": "+e.getMessage(),e);
    throw new ManifoldCFException("Error "+context+": "+e.getMessage(),e);
  }

  /** Class to set up connection.
   */
  protected static class ConnectThread extends Thread
  {
    protected final String serverUrl;
    protected final String user;
    protected final String password;
    protected final RocketChatSession.ProxySettings proxySettings;

    // Local session handle
    protected RocketChatSession session = null;
    protected Throwable exception = null;

    public ConnectThread(String serverUrl, String user, String password, RocketChatSession.ProxySettings proxySettings)
    {
      this.serverUrl = serverUrl;
      this.user = user;
      this.password = password;
      this.proxySettings = proxySettings;
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        session = new RocketChatSession(serverUrl, user, password, proxySettings);
      }
      catch (Throwable e)
      {
        exception = e;
      }
    }

    public RocketChatSession finishUp()
        throws IOException, InterruptedException
    {
      try
      {
        join();
        if (exception != null)
        {
          if (exception instanceof RuntimeException)
            throw (RuntimeException)exception;
          else if (exception instanceof Error)
            throw (Error)exception;
          else if (exception instanceof IOException)
            throw (IOException)exception;
          else
            throw new RuntimeException("Unknown exception type: "+exception.getClass().getName()+": "+exception.getMessage(),exception);
        }
        return session;
      } catch (InterruptedException e) {
        this.interrupt();
        throw e;
      }
    }
  }

  /** Class to close the session.
   */
  protected static class CloseSessionThread extends Thread
  {
    protected final RocketChatSession session;

    protected Throwable exception = null;

    public CloseSessionThread(RocketChatSession session)
    {
      this.session = session;
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        session.close();
      }
      catch (Throwable e)
      {
        exception = e;
      }
    }

    public void finishUp()
        throws IOException, InterruptedException
    {
      try
      {
        join();
        if (exception != null)
        {
          if (exception instanceof RuntimeException)
            throw (RuntimeException)exception;
          else if (exception instanceof Error)
            throw (Error)exception;
          else if (exception instanceof IOException)
            throw (IOException)exception;
          else
            throw new RuntimeException("Unknown exception type: "+exception.getClass().getName()+": "+exception.getMessage(),exception);
        }
      } catch (InterruptedException e) {
        this.interrupt();
        throw e;
      }
    }
  }

  /** Class to check the connection.
   */
  protected static class CheckConnectionThread extends Thread
  {
    protected final RocketChatSession session;

    protected Throwable exception = null;

    public CheckConnectionThread(RocketChatSession session)
    {
      this.session = session;
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        session.checkConnection();
      }
      catch (Throwable e)
      {
        exception = e;
      }
    }

    public void finishUp()
        throws IOException, InterruptedException
    {
      try
      {
        join();
        if (exception != null)
        {
          if (exception instanceof RuntimeException)
            throw (RuntimeException)exception;
          else if (exception instanceof Error)
            throw (Error)exception;
          else if (exception instanceof IOException)
            throw (IOException)exception;
          else
            throw new RuntimeException("Unknown exception type: "+exception.getClass().getName()+": "+exception.getMessage(),exception);
        }
      } catch (InterruptedException e) {
        this.interrupt();
        throw e;
      }
    }
  }

  /** Class to send Rocket.Chat messages.
   */
  protected static class SendThread extends Thread
  {
    protected final RocketChatSession session;
    protected final RocketChatMessage message;

    protected Throwable exception = null;

    public SendThread(RocketChatSession session, RocketChatMessage message)
    {
      this.session = session;
      this.message = message;
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        session.send(message);
      }
      catch (Throwable e)
      {
        exception = e;
      }
    }

    public void finishUp()
        throws IOException, InterruptedException
    {
      try
      {
        join();
        if (exception != null)
        {
          if (exception instanceof RuntimeException)
            throw (RuntimeException)exception;
          else if (exception instanceof Error)
            throw (Error)exception;
          else if (exception instanceof IOException)
            throw (IOException)exception;
          else
            throw new RuntimeException("Unknown exception type: "+exception.getClass().getName()+": "+exception.getMessage(),exception);
        }
      } catch (InterruptedException e) {
        this.interrupt();
        throw e;
      }
    }
  }
}