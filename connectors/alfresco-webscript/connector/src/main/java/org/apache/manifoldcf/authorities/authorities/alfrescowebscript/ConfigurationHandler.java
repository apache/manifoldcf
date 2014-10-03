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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

public class ConfigurationHandler {
  private static final String PARAM_PROTOCOL = "protocol";
  private static final String PARAM_HOSTNAME = "hostname";
  private static final String PARAM_ENDPOINT = "endpoint";
  private static final String PARAM_USERNAME = "username";
  private static final String PARAM_PASSWORD = "password";
  
  private static final String EDIT_CONFIG_HEADER = "editConfiguration.js";
  private static final String EDIT_CONFIG_SERVER = "editConfiguration_Server.html";
  private static final String VIEW_CONFIG = "viewConfiguration.html";

  private static final Map<String, String> DEFAULT_CONFIGURATION_PARAMETERS = new HashMap<String, String>();
  static {
    DEFAULT_CONFIGURATION_PARAMETERS.put(PARAM_PROTOCOL, "http");
    DEFAULT_CONFIGURATION_PARAMETERS.put(PARAM_HOSTNAME, "localhost");
    DEFAULT_CONFIGURATION_PARAMETERS.put(PARAM_ENDPOINT, "/alfresco/service");
    DEFAULT_CONFIGURATION_PARAMETERS.put(PARAM_USERNAME, "");
    DEFAULT_CONFIGURATION_PARAMETERS.put(PARAM_PASSWORD, "");
  }

  private ConfigurationHandler() {
  }

  public static void outputConfigurationHeader(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters,
      List<String> tabsArray) throws ManifoldCFException, IOException {
    tabsArray.add("Server");
    Map<String, Object> paramMap = new HashMap<String, Object>();
    fillInParameters(paramMap, parameters);
    Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIG_HEADER, paramMap);
  }

  private static void fillInParameters(Map<String, Object> paramMap,
      ConfigParams parameters) {
    for (Map.Entry<String, String> parameter : DEFAULT_CONFIGURATION_PARAMETERS
        .entrySet()) {
      String paramValue = parameters.getParameter(parameter.getKey());
      if (paramValue == null) {
        paramValue = parameter.getValue();
      }
      paramMap.put(parameter.getKey(), paramValue);
    }
  }

  public static void outputConfigurationBody(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
      throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("tabName", tabName);
    fillInParameters(paramMap, parameters);
    Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIG_SERVER, paramMap);
  }

  private static VelocityContext createVelocityContext(Map<String, String> paramMap) {
    VelocityContext context = new VelocityContext();
    for (Map.Entry<String, String> entry : paramMap.entrySet()) {
      context.put(entry.getKey(), entry.getValue());
    }
    return context;
  }

  public static String processConfigurationPost(IThreadContext threadContext,
      IPostParameters variableContext, Locale locale, ConfigParams parameters)
      throws ManifoldCFException {
    for (String paramName : DEFAULT_CONFIGURATION_PARAMETERS.keySet()) {
      String paramValue = variableContext.getParameter(paramName);
      if (paramValue != null) {
        parameters.setParameter(paramName, paramValue);
      }
    }
    return null;
  }

  public static void viewConfiguration(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters)
      throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    fillInParameters(paramMap, parameters);
    Messages.outputResourceWithVelocity(out, locale, VIEW_CONFIG, paramMap);
  }
}
