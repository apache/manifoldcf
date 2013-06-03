/* $Id: FileOutputConnector.java 991374 2013-05-31 23:04:08Z minoru $ */

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
package org.apache.manifoldcf.agents.output.filesystem;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputRemoveActivity;
import org.apache.manifoldcf.agents.interfaces.OutputSpecification;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.output.BaseOutputConnector;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.ConfigurationNode;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.json.JSONException;

public class FileOutputConnector extends BaseOutputConnector {

  public static final String _rcsid = "@(#)$Id: FileOutputConnector.java 988245 2010-08-23 18:39:35Z minoru $";

  // Activities we log

  /** Ingestion activity */
  public final static String INGEST_ACTIVITY = "document ingest";
  /** Document removal activity */
  public final static String REMOVE_ACTIVITY = "document deletion";

  // Activities list
  protected static final String[] activitiesList = new String[]{INGEST_ACTIVITY, REMOVE_ACTIVITY};

  /** Forward to the javascript to check the configuration parameters */
  private static final String EDIT_CONFIGURATION_JS = "editConfiguration.js";

  /** Forward to the HTML template to edit the configuration parameters */
  private static final String EDIT_CONFIGURATION_HTML = "editConfiguration.html";

  /** Forward to the HTML template to view the configuration parameters */
  private static final String VIEW_CONFIGURATION_HTML = "viewConfiguration.html";

  /** Forward to the javascript to check the specification parameters for the job */
  private static final String EDIT_SPECIFICATION_JS = "editSpecification.js";

  /** Forward to the template to edit the configuration parameters for the job */
  private static final String EDIT_SPECIFICATION_HTML = "editSpecification.html";

  /** Forward to the template to view the specification parameters for the job */
  private static final String VIEW_SPECIFICATION_HTML = "viewSpecification.html";

  /** Constructor.
   */
  public FileOutputConnector() {
  }

  /** Return the list of activities that this connector supports (i.e. writes into the log).
   *@return the list.
   */
  @Override
  public String[] getActivitiesList() {
    return activitiesList;
  }

  /** Connect.
   *@param configParameters is the set of configuration parameters, which
   * in this case describe the target appliance, basic auth configuration, etc.  (This formerly came
   * out of the ini file.)
   */
  @Override
  public void connect(ConfigParams configParameters) {
    super.connect(configParameters);
  }

  /** Close the connection.  Call this before discarding the connection.
   */
  @Override
  public void disconnect() throws ManifoldCFException {
    super.disconnect();
  }

  /** Set up a session */
  protected void getSession() throws ManifoldCFException, ServiceInterruption {
  }

  /** Test the connection.  Returns a string describing the connection integrity.
   *@return the connection's status as a displayable string.
   */
  @Override
  public String check() throws ManifoldCFException {
    try {
      getSession();
      return super.check();
    } catch (ServiceInterruption e) {
      return "Transient error: "+e.getMessage();
    }
  }

  /** Get an output version string, given an output specification.  The output version string is used to uniquely describe the pertinent details of
   * the output specification and the configuration, to allow the Connector Framework to determine whether a document will need to be output again.
   * Note that the contents of the document cannot be considered by this method, and that a different version string (defined in IRepositoryConnector)
   * is used to describe the version of the actual document.
   *
   * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be
   * necessary.
   *@param spec is the current output specification for the job that is doing the crawling.
   *@return a string, of unlimited length, which uniquely describes output configuration and specification in such a way that if two such strings are equal,
   * the document will not need to be sent again to the output data store.
   */
  @Override
  public String getOutputDescription(OutputSpecification spec) throws ManifoldCFException, ServiceInterruption {
    FileOutputSpecs specs = new FileOutputSpecs(getSpecNode(spec));
    return specs.toJson().toString();
  }

  /** Add (or replace) a document in the output data store using the connector.
   * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be
   * necessary.
   * The OutputSpecification is *not* provided to this method, because the goal is consistency, and if output is done it must be consistent with the
   * output description, since that was what was partly used to determine if output should be taking place.  So it may be necessary for this method to decode
   * an output description string in order to determine what should be done.
   *@param documentURI is the URI of the document.  The URI is presumed to be the unique identifier which the output data store will use to process
   * and serve the document.  This URI is constructed by the repository connector which fetches the document, and is thus universal across all output connectors.
   *@param outputDescription is the description string that was constructed for this document by the getOutputDescription() method.
   *@param document is the document data to be processed (handed to the output data store).
   *@param authorityNameString is the name of the authority responsible for authorizing any access tokens passed in with the repository document.  May be null.
   *@param activities is the handle to an object that the implementer of an output connector may use to perform operations, such as logging processing activity.
   *@return the document status (accepted or permanently rejected).
   */
  @Override
  public int addOrReplaceDocument(String documentURI, String outputDescription, RepositoryDocument document, String authorityNameString, IOutputAddActivity activities) throws ManifoldCFException, ServiceInterruption {
    // Establish a session
    getSession();

    FileOutputConfig config = getConfigParameters(null);

    FileOutputSpecs specs = null;
    StringBuffer path = new StringBuffer();
    InputStream input = null;
    FileOutputStream output = null;
    FileLock lock = null;
    try {
      specs = new FileOutputSpecs(outputDescription);

      /*
       * make file path
       */
      if (specs.getRootPath() != null) {
        path.append(specs.getRootPath());
      }
      path.append(documentURItoFilePath(documentURI));

      File file = new File(path.toString());

      /*
       * make directory
       */
      if (!file.getParentFile().exists()) {
        file.getParentFile().mkdirs();
      }

      /*
       * delete old file
       */
      if (file.exists()) {
        file.delete();
      }

      input = document.getBinaryStream();
      output = new FileOutputStream(file);

      try {
        /*
         * lock file
         */
        boolean locked = false;
        int retryCount = 0;
        FileChannel channel = output.getChannel();
        while(retryCount < 10) {
          lock = channel.tryLock();
          if (lock == null) {
            retryCount++;
            try {
              Thread.sleep(1000L);
            } catch(InterruptedException e) {
            }
          } else {
            locked = true;
            break;
          }
        }
        if (!locked) {
          throw new ManifoldCFException("Lock failed.");
        }

        /*
         * write file
         */
        byte buf[] = new byte[1024];
        int len;
        while((len = input.read(buf)) != -1) {
          output.write(buf, 0, len);
        }
        output.flush();
      } finally {
        /*
         * release file
         */
        try {
          if (lock != null) {
            lock.release();
          }
        } catch (ClosedChannelException e) {
        }
      }
    } catch (JSONException e) {
      return DOCUMENTSTATUS_REJECTED;
    } catch (URISyntaxException e) {
      return DOCUMENTSTATUS_REJECTED;
    } catch (SecurityException e) {
      return DOCUMENTSTATUS_REJECTED;
    } catch (FileNotFoundException e) {
      return DOCUMENTSTATUS_REJECTED;
    } catch (IOException e) {
      return DOCUMENTSTATUS_REJECTED;
    } catch (NullPointerException e) {
      return DOCUMENTSTATUS_REJECTED;
    } finally {
      try {
        input.close();
      } catch (IOException e) {
      }
      try {
        output.close();
      } catch (IOException e) {
      }
    }

    activities.recordActivity(null, INGEST_ACTIVITY, new Long(document.getBinaryLength()), documentURI, "OK", null);
    return DOCUMENTSTATUS_ACCEPTED;
  }

  /** Remove a document using the connector.
   * Note that the last outputDescription is included, since it may be necessary for the connector to use such information to know how to properly remove the document.
   *@param documentURI is the URI of the document.  The URI is presumed to be the unique identifier which the output data store will use to process
   * and serve the document.  This URI is constructed by the repository connector which fetches the document, and is thus universal across all output connectors.
   *@param outputDescription is the last description string that was constructed for this document by the getOutputDescription() method above.
   *@param activities is the handle to an object that the implementer of an output connector may use to perform operations, such as logging processing activity.
   */
  @Override
  public void removeDocument(String documentURI, String outputDescription, IOutputRemoveActivity activities) throws ManifoldCFException, ServiceInterruption {
    // Establish a session
    getSession();

    FileOutputConfig config = getConfigParameters(null);

    FileOutputSpecs specs = null;
    StringBuffer path = new StringBuffer();
    try {
      specs = new FileOutputSpecs(outputDescription);

      /*
       * make path
       */
      if (specs.getRootPath() != null) {
        path.append(specs.getRootPath());
      }
      path.append(documentURItoFilePath(documentURI));

      File file = new File(path.toString());

      /*
       * delete old file
       */
      if (file.exists()) {
        file.delete();
      }
    } catch (JSONException e) {
    } catch (URISyntaxException e) {
    } catch (NullPointerException e) {
    }

    activities.recordActivity(null, REMOVE_ACTIVITY, null, documentURI, "OK", null);
  }

  /** Output the configuration header section.
   * This method is called in the head section of the connector's configuration page.  Its purpose is to add the required tabs to the list, and to output any
   * javascript methods that might be needed by the configuration editing HTML.
   *@param threadContext is the local thread context.
   *@param out is the output to which any HTML should be sent.
   *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
   *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
   */
  @Override
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters, List<String> tabsArray) throws ManifoldCFException, IOException {
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
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName) throws ManifoldCFException, IOException {
  }

  /** Process a configuration post.
   * This method is called at the start of the connector's configuration page, whenever there is a possibility that form data for a connection has been
   * posted.  Its purpose is to gather form information and modify the configuration parameters accordingly.
   * The name of the posted form is "editconnection".
   *@param threadContext is the local thread context.
   *@param variableContext is the set of variables available from the post, including binary file post information.
   *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
   *@return null if all is well, or a string error message if there is an error that should prevent saving of the connection (and cause a redirection to an error page).
   */
  @Override
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext, Locale locale, ConfigParams parameters) throws ManifoldCFException {
    return null;
  }

  /** View configuration.
   * This method is called in the body section of the connector's view configuration page.  Its purpose is to present the connection information to the user.
   * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
   *@param threadContext is the local thread context.
   *@param out is the output to which any HTML should be sent.
   *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
   */
  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters) throws ManifoldCFException, IOException {
  }

  /** Output the specification header section.
   * This method is called in the head section of a job page which has selected an output connection of the current type.  Its purpose is to add the required tabs
   * to the list, and to output any javascript methods that might be needed by the job editing HTML.
   *@param out is the output to which any HTML should be sent.
   *@param os is the current output specification for this job.
   *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
   */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, OutputSpecification os, List<String> tabsArray) throws ManifoldCFException, IOException {
    super.outputSpecificationHeader(out, locale, os, tabsArray);
    tabsArray.add(Messages.getString(locale, "FileConnector.PathTabName"));
    outputResource(EDIT_SPECIFICATION_JS, out, locale, null, null);
  }

  /** Output the specification body section.
   * This method is called in the body section of a job page which has selected an output connection of the current type.  Its purpose is to present the required form elements for editing.
   * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
   * form is "editjob".
   *@param out is the output to which any HTML should be sent.
   *@param os is the current output specification for this job.
   *@param tabName is the current tab name.
   */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, OutputSpecification os, String tabName) throws ManifoldCFException, IOException {
    super.outputSpecificationBody(out, locale, os, tabName);
    FileOutputSpecs specs = getSpecParameters(os);
    outputResource(EDIT_SPECIFICATION_HTML, out, locale, specs, tabName);
  }

  /** Process a specification post.
   * This method is called at the start of job's edit or view page, whenever there is a possibility that form data for a connection has been
   * posted.  Its purpose is to gather form information and modify the output specification accordingly.
   * The name of the posted form is "editjob".
   *@param variableContext contains the post data, including binary file-upload information.
   *@param os is the current output specification for this job.
   *@return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
   */
  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, OutputSpecification os) throws ManifoldCFException {
    ConfigurationNode specNode = getSpecNode(os);
    boolean bAdd = (specNode == null);
    if (bAdd) {
      specNode = new SpecificationNode(FileOutputConstant.PARAM_ROOTPATH);
    }
    FileOutputSpecs.contextToSpecNode(variableContext, specNode);
    if (bAdd) {
      os.addChild(os.getChildCount(), specNode);
    }

    return null;
  }

  /** View specification.
   * This method is called in the body section of a job's view page.  Its purpose is to present the output specification information to the user.
   * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
   *@param out is the output to which any HTML should be sent.
   *@param os is the current output specification for this job.
   */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, OutputSpecification os) throws ManifoldCFException, IOException {
    outputResource(VIEW_SPECIFICATION_HTML, out, locale, getSpecParameters(os), null);
  }

  /**
   * @param os
   * @return
   */
  final private SpecificationNode getSpecNode(OutputSpecification os)
  {
    int l = os.getChildCount();
    for (int i = 0; i < l; i++) {
      SpecificationNode node = os.getChild(i);
      if (node.getType().equals(FileOutputConstant.PARAM_ROOTPATH)) {
        return node;
      }
    }
    return null;
  }

  /**
   * @param os
   * @return
   * @throws ManifoldCFException
   */
  final private FileOutputSpecs getSpecParameters(OutputSpecification os) throws ManifoldCFException {
    return new FileOutputSpecs(getSpecNode(os));
  }

  /**
   * @param configParams
   * @return
   */
  final private FileOutputConfig getConfigParameters(ConfigParams configParams) {
    if (configParams == null)
      configParams = getConfiguration();
    return new FileOutputConfig(configParams);
  }

  /** Read the content of a resource, replace the variable ${PARAMNAME} with the
   * value and copy it to the out.
   * 
   * @param resName
   * @param out
   * @throws ManifoldCFException */
  private static void outputResource(String resName, IHTTPOutput out, Locale locale, FileOutputParam params, String tabName) throws ManifoldCFException {
    Map<String,String> paramMap = null;
    if (params != null) {
      paramMap = params.buildMap();
      if (tabName != null) {
        paramMap.put("TabName", tabName);
      }
    }
    Messages.outputResourceWithVelocity(out, locale, resName, paramMap, true);
  }

  /**
   * @param documentURI
   * @return
   * @throws URISyntaxException
   * @throws NullPointerException
   */
  final private String documentURItoFilePath(String documentURI) throws URISyntaxException, NullPointerException {
    StringBuffer path = new StringBuffer();
    URI uri = null;

    uri = new URI(documentURI);

    if (uri.getScheme() != null) {
      if (!path.toString().endsWith("/")) {
        path.append("/");
      }
      path.append(uri.getScheme());
    }

    if (uri.getAuthority() != null) {
      if (!path.toString().endsWith("/")) {
        path.append("/");
      }
      try {
        path.append(URLEncoder.encode(uri.getAuthority(), "UTF-8"));
      } catch(UnsupportedEncodingException e) {
        path.append(uri.getAuthority());
      }
    } else {
      if (uri.getSchemeSpecificPart() != null) {
        for (String name : uri.getSchemeSpecificPart().split("/")) {
          if (name.length() > 0) {
            path.append("/");
            try {
              path.append(URLEncoder.encode(name, "UTF-8"));
            } catch(UnsupportedEncodingException e) {
              path.append(name);
            }
          }
        }

        if (path.toString().endsWith("/")) {
          path.append(".content");
        }

        return path.toString();
      }
    }

    if (uri.getPath() != null) {
        if (uri.getPath().length() == 0) {
          path.append("/");
        } else if (uri.getPath().equals("/")) {
          path.append(uri.getPath());
        } else {
          for (String name : uri.getPath().split("/")) {
            if (name.length() > 0) {
              path.append("/");
              try {
                path.append(URLEncoder.encode(name, "UTF-8"));
              } catch(UnsupportedEncodingException e) {
                path.append(name);
              }
            }
          }
        }
      }

    if (path.toString().endsWith("/")) {
      path.append(".content");
    }

    return path.toString();
  }
}
