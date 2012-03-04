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

package org.apache.manifoldcf.agents.output.opensearchserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Locale;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputNotifyActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputRemoveActivity;
import org.apache.manifoldcf.agents.interfaces.OutputSpecification;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.output.BaseOutputConnector;
import org.apache.manifoldcf.agents.output.opensearchserver.OpenSearchServerAction.CommandEnum;
import org.apache.manifoldcf.agents.output.opensearchserver.OpenSearchServerConnection.Result;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.ConfigurationNode;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.core.system.Logging;
import org.json.JSONException;
import org.json.JSONObject;

public class OpenSearchServerConnector extends BaseOutputConnector {

  private final static String OPENSEARCHSERVER_INDEXATION_ACTIVITY = "Optimize";
  private final static String OPENSEARCHSERVER_DELETION_ACTIVITY = "Deletion";
  private final static String OPENSEARCHSERVER_OPTIMIZE_ACTIVITY = "Indexation";

  private final static String[] OPENSEARCHSERVER_ACTIVITIES = {
      OPENSEARCHSERVER_INDEXATION_ACTIVITY, OPENSEARCHSERVER_DELETION_ACTIVITY,
      OPENSEARCHSERVER_OPTIMIZE_ACTIVITY };

  private final static String OPENSEARCHSERVER_TAB_MESSAGE = "OpenSearchServerConnector.OpenSearchServer";
  private final static String PARAMETERS_TAB_MESSAGE = "OpenSearchServerConnector.Parameters";
      
  /** Forward to the HTML template for rendering hidden fields when the Server tab is not selected */
  private static final String HIDDEN_CONFIG_FORWARD = "hiddenConfiguration.html";
  
  /** Forward to the HTML template for rendering hidden fields when the CMIS Query tab is not selected */
  private static final String HIDDEN_SPEC_FORWARD = "hiddenSpecification.html";
  
  /** Forward to the HTML template to edit the configuration parameters */
  private static final String EDIT_CONFIG_FORWARD = "editConfiguration.html";
  
  /** Forward to the HTML template to view the configuration parameters */
  private static final String VIEW_CONFIG_FORWARD = "viewConfiguration.html";
 
  /** Forward to the javascript to check the configuration parameters */
  private static final String EDIT_CONFIG_HEADER_FORWARD = "editConfiguration.js";

  /** Forward to the template to view the specification parameters for the job */
  private static final String VIEW_SPEC_FORWARD = "viewSpecification.html";
  
  /** Forward to the template to edit the configuration parameters for the job */
  private static final String EDIT_SPEC_FORWARD = "editSpecification.html";

  /** Forward to the javascript to check the specification parameters for the job */
  private static final String EDIT_SPEC_HEADER_FORWARD = "editSpecification.js";

  private String specsCacheOutpuDescription;
  private OpenSearchServerSpecs specsCache;

  public OpenSearchServerConnector() {
    specsCacheOutpuDescription = null;
    specsCache = null;
  }

  @Override
  public String[] getActivitiesList() {
    return OPENSEARCHSERVER_ACTIVITIES;
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
      Locale locale, OpenSearchServerParam params) throws ManifoldCFException {
    Messages.outputResourceWithVelocity(out,locale,resName,(params==null)?null:params.buildMap(),false);
  }

  @Override
  public void outputConfigurationHeader(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters, List<String> tabsArray)
      throws ManifoldCFException, IOException {
    super.outputConfigurationHeader(threadContext, out, locale, parameters, tabsArray);
    tabsArray.add(Messages.getString(locale,PARAMETERS_TAB_MESSAGE));
    outputResource(EDIT_CONFIG_HEADER_FORWARD, out, locale, null);
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
      throws ManifoldCFException, IOException {
    super.outputConfigurationBody(threadContext, out, locale, parameters, tabName);
    OpenSearchServerConfig config = this.getConfigParameters(parameters);
    if (Messages.getString(locale,PARAMETERS_TAB_MESSAGE).equals(tabName)) {
      outputResource(EDIT_CONFIG_FORWARD, out, locale, config);
    } else {
      outputResource(HIDDEN_CONFIG_FORWARD, out, locale, config);
    }
  }

  @Override
  public void outputSpecificationHeader(IHTTPOutput out,
      Locale locale, OutputSpecification os, List<String> tabsArray)
      throws ManifoldCFException, IOException {
    super.outputSpecificationHeader(out, locale, os, tabsArray);
    tabsArray.add(Messages.getString(locale,OPENSEARCHSERVER_TAB_MESSAGE));
    outputResource(EDIT_SPEC_HEADER_FORWARD, out, locale, null);
  }

  final private SpecificationNode getSpecNode(OutputSpecification os) {
    int l = os.getChildCount();
    for (int i = 0; i < l; i++) {
      SpecificationNode node = os.getChild(i);
      if (OpenSearchServerSpecs.OPENSEARCHSERVER_SPECS_NODE.equals(node
          .getType())) {
        return node;
      }
    }
    return null;
  }

  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, OutputSpecification os,
      String tabName) throws ManifoldCFException, IOException {
    super.outputSpecificationBody(out, locale, os, tabName);
    OpenSearchServerSpecs specs = getSpecParameters(os);
    if (Messages.getString(locale,OPENSEARCHSERVER_TAB_MESSAGE).equals(tabName)) {
      outputResource(EDIT_SPEC_FORWARD, out, locale, specs);
    } else {
      outputResource(HIDDEN_SPEC_FORWARD, out, locale, specs);
    }
  }

  @Override
  public String processSpecificationPost(IPostParameters variableContext,
      Locale locale, OutputSpecification os) throws ManifoldCFException {
    ConfigurationNode specNode = getSpecNode(os);
    boolean bAdd = (specNode == null);
    if (bAdd) {
      specNode = new SpecificationNode(
          OpenSearchServerSpecs.OPENSEARCHSERVER_SPECS_NODE);
    }
    OpenSearchServerSpecs.contextToSpecNode(variableContext, specNode);
    if (bAdd)
      os.addChild(os.getChildCount(), specNode);
    return null;
  }

  /**
   * Build a Set of OpenSearchServer parameters. If configParams is null,
   * getConfiguration() is used.
   * 
   * @param configParams
   */
  final private OpenSearchServerConfig getConfigParameters(
      ConfigParams configParams) {
    if (configParams == null)
      configParams = getConfiguration();
    synchronized (this) {
      return new OpenSearchServerConfig(configParams);
    }
  }

  final private OpenSearchServerSpecs getSpecParameters(OutputSpecification os)
      throws ManifoldCFException {
    return new OpenSearchServerSpecs(getSpecNode(os));
  }

  final private OpenSearchServerSpecs getSpecsCache(String outputDescription)
      throws ManifoldCFException {
    try {
      synchronized (this) {
        if (!outputDescription.equals(specsCacheOutpuDescription))
          specsCache = null;
        if (specsCache == null)
          specsCache = new OpenSearchServerSpecs(new JSONObject(
              outputDescription));
        return specsCache;
      }
    } catch (JSONException e) {
      throw new ManifoldCFException(e);
    }
  }

  @Override
  public String getOutputDescription(OutputSpecification os)
      throws ManifoldCFException {
    OpenSearchServerSpecs specs = new OpenSearchServerSpecs(getSpecNode(os));
    return specs.toJson().toString();
  }

  @Override
  public boolean checkLengthIndexable(String outputDescription, long length)
      throws ManifoldCFException, ServiceInterruption {
    OpenSearchServerSpecs specs = getSpecsCache(outputDescription);
    long maxFileSize = specs.getMaxFileSize();
    if (length > maxFileSize)
      return false;
    return super.checkLengthIndexable(outputDescription, length);
  }

  @Override
  public boolean checkDocumentIndexable(String outputDescription, File localFile)
      throws ManifoldCFException, ServiceInterruption {
    OpenSearchServerSpecs specs = getSpecsCache(outputDescription);
    return specs
        .checkExtension(FilenameUtils.getExtension(localFile.getName()));
  }

  @Override
  public boolean checkMimeTypeIndexable(String outputDescription,
      String mimeType) throws ManifoldCFException, ServiceInterruption {
    OpenSearchServerSpecs specs = getSpecsCache(outputDescription);
    return specs.checkMimeType(mimeType);
  }

  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
      Locale locale, ConfigParams parameters) throws ManifoldCFException, IOException {
    outputResource(VIEW_CONFIG_FORWARD, out, locale, getConfigParameters(parameters));
  }

  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, OutputSpecification os)
      throws ManifoldCFException, IOException {
    outputResource(VIEW_SPEC_FORWARD, out, locale, getSpecParameters(os));
  }

  @Override
  public String processConfigurationPost(IThreadContext threadContext,
      IPostParameters variableContext, ConfigParams parameters)
      throws ManifoldCFException {
    OpenSearchServerConfig.contextToConfig(variableContext, parameters);
    return null;
  }

  private static Map<String, Integer> ossInstances = null;

  private synchronized final Integer addInstance(OpenSearchServerConfig config) {
    if (ossInstances == null)
      ossInstances = new TreeMap<String, Integer>();
    synchronized (ossInstances) {
      String uii = config.getUniqueIndexIdentifier();
      Integer count = ossInstances.get(uii);
      if (count == null) {
        count = new Integer(1);
        ossInstances.put(uii, count);
      } else
        count++;
      return count;
    }
  }

  private synchronized final void removeInstance(OpenSearchServerConfig config) {
    if (ossInstances == null)
      return;
    synchronized (ossInstances) {
      String uii = config.getUniqueIndexIdentifier();
      Integer count = ossInstances.get(uii);
      if (count == null)
        return;
      if (--count == 0)
        ossInstances.remove(uii);
    }
  }

  @Override
  public int addOrReplaceDocument(String documentURI, String outputDescription,
      RepositoryDocument document, String authorityNameString,
      IOutputAddActivity activities) throws ManifoldCFException,
      ServiceInterruption {
    OpenSearchServerConfig config = getConfigParameters(null);
    Integer count = addInstance(config);
    synchronized (count) {
      InputStream inputStream = document.getBinaryStream();
      try {
        long startTime = System.currentTimeMillis();
        OpenSearchServerIndex oi = new OpenSearchServerIndex(documentURI,
            inputStream, config);
        activities.recordActivity(startTime,
            OPENSEARCHSERVER_INDEXATION_ACTIVITY, document.getBinaryLength(),
            documentURI, oi.getResult().name(), oi.getResultDescription());
        if (oi.getResult() != Result.OK)
          return DOCUMENTSTATUS_REJECTED;
      } finally {
        removeInstance(config);
      }
      return DOCUMENTSTATUS_ACCEPTED;
    }
  }

  @Override
  public void removeDocument(String documentURI, String outputDescription,
      IOutputRemoveActivity activities) throws ManifoldCFException,
      ServiceInterruption {
    long startTime = System.currentTimeMillis();
    OpenSearchServerDelete od = new OpenSearchServerDelete(documentURI,
        getConfigParameters(null));
    activities.recordActivity(startTime, OPENSEARCHSERVER_DELETION_ACTIVITY,
        null, documentURI, od.getResult().name(), od.getResultDescription());
  }

  @Override
  public String check() throws ManifoldCFException {
    OpenSearchServerSchema oss = new OpenSearchServerSchema(
        getConfigParameters(null));
    return oss.getResult().name() + " " + oss.getResultDescription();
  }

  @Override
  public void noteJobComplete(IOutputNotifyActivity activities)
      throws ManifoldCFException, ServiceInterruption {
    long startTime = System.currentTimeMillis();
    OpenSearchServerAction oo = new OpenSearchServerAction(
        CommandEnum.optimize, getConfigParameters(null));
    activities.recordActivity(startTime, OPENSEARCHSERVER_OPTIMIZE_ACTIVITY,
        null, oo.getCallUrlSnippet(), oo.getResult().name(),
        oo.getResultDescription());
  }

}
