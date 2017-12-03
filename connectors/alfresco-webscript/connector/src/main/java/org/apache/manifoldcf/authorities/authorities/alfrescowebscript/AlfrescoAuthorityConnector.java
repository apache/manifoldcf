/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.manifoldcf.authorities.authorities.alfrescowebscript;

import com.github.maoo.indexer.client.AlfrescoClient;
import com.github.maoo.indexer.client.AlfrescoDownException;
import com.github.maoo.indexer.client.AlfrescoUser;
import com.github.maoo.indexer.client.WebScriptsAlfrescoClient;
import org.apache.manifoldcf.authorities.authorities.BaseAuthorityConnector;
import org.apache.manifoldcf.authorities.interfaces.AuthorizationResponse;
import org.apache.manifoldcf.authorities.system.Logging;
import org.apache.manifoldcf.core.interfaces.*;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

public class AlfrescoAuthorityConnector extends BaseAuthorityConnector {

  private AlfrescoClient alfrescoClient;

  public void setClient(AlfrescoClient client) {
    alfrescoClient = client;
  }

  @Override
  public void connect(ConfigParams config) {
    super.connect(config);

    String protocol = getConfig(config, "protocol", "http");
    String hostname = getConfig(config, "hostname", "localhost");
    String port = getConfig(config, "port", "8080");
    String endpoint = getConfig(config, "endpoint", "/alfresco/service");
    String username = getConfig(config, "username", null);
    String password = getObfuscatedConfig(config, "password", null);

    alfrescoClient = new WebScriptsAlfrescoClient(protocol, hostname + ":" + port, endpoint,
        null, null, username, password);
  }

  private static String getConfig(ConfigParams config,
                                  String parameter,
                                  String defaultValue) {
    final String protocol = config.getParameter(parameter);
    if (protocol == null) {
      return defaultValue;
    }
    return protocol;
  }

  private static String getObfuscatedConfig(ConfigParams config,
                                  String parameter,
                                  String defaultValue) {
    final String protocol = config.getObfuscatedParameter(parameter);
    if (protocol == null) {
      return defaultValue;
    }
    return protocol;
  }

  /*
   * (non-Javadoc)
   * @see org.apache.manifoldcf.core.connector.BaseConnector#check()
   */
  @Override
  public String check() throws ManifoldCFException {
    try {
      alfrescoClient.fetchUserAuthorities("admin");
      return super.check();
    } catch (AlfrescoDownException e) {
      if (Logging.authorityConnectors != null) {
        Logging.authorityConnectors.warn(e.getMessage(), e);
      }
      return "Connection failed: " + e.getMessage();
    }
  }

  /*
   * (non-Javadoc)
   * @see org.apache.manifoldcf.core.connector.BaseConnector#disconnect()
   */
  @Override
  public void disconnect() throws ManifoldCFException {
    alfrescoClient = null;
    super.disconnect();
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
      AlfrescoUser permissions = alfrescoClient.fetchUserAuthorities(userName);
      if (permissions.getUsername() == null
          || permissions.getUsername().isEmpty()
          || permissions.getAuthorities().isEmpty()) {
        return RESPONSE_USERNOTFOUND;
      } else {
        final List<String> rval = new ArrayList<>(permissions.getAuthorities());
        rval.add(permissions.getUsername());
        return new AuthorizationResponse(
            rval.toArray(new String[rval.size()]),
            AuthorizationResponse.RESPONSE_OK);
      }
    } catch (AlfrescoDownException e) {
      return RESPONSE_UNREACHABLE;
    }
  }

  @Override
  public void outputConfigurationHeader(IThreadContext threadContext,
                                        IHTTPOutput out, Locale locale, ConfigParams parameters,
                                        List<String> tabsArray) throws ManifoldCFException, IOException {
    ConfigurationHandler.outputConfigurationHeader(threadContext, out, locale,
        parameters, tabsArray);
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext,
                                      IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
      throws ManifoldCFException, IOException {
    ConfigurationHandler.outputConfigurationBody(threadContext, out, locale,
        parameters, tabName);
  }

  @Override
  public String processConfigurationPost(IThreadContext threadContext,
                                         IPostParameters variableContext, Locale locale, ConfigParams parameters)
      throws ManifoldCFException {
    return ConfigurationHandler.processConfigurationPost(threadContext,
        variableContext, locale, parameters);
  }

  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
                                Locale locale, ConfigParams parameters) throws ManifoldCFException,
      IOException {
    ConfigurationHandler.viewConfiguration(threadContext, out, locale,
        parameters);
  }
}
