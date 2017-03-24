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
package org.apache.manifoldcf.agents.output.hdfs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.IOUtils;

import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputRemoveActivity;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.system.Logging;
import org.apache.manifoldcf.agents.output.BaseOutputConnector;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.ConfigurationNode;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.core.interfaces.VersionContext;

public class HDFSOutputConnector extends BaseOutputConnector {

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

  protected String nameNodeProtocol = null;
  protected String nameNodeHost = null;
  protected String nameNodePort = null;
  protected String user = null;
  protected HDFSSession session = null;
  protected long lastSessionFetch = -1L;
  protected static final long timeToRelease = 300000L;

  /** Constructor.
   */
  public HDFSOutputConnector() {
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
  public void connect(ConfigParams configParams) {
    super.connect(configParams);
    nameNodeProtocol = configParams.getParameter(ParameterEnum.namenodeprotocol.name());
    if (nameNodeProtocol == null)
      nameNodeProtocol = "hdfs";
    nameNodeHost = configParams.getParameter(ParameterEnum.namenodehost.name());
    nameNodePort = configParams.getParameter(ParameterEnum.namenodeport.name());
    user = configParams.getParameter(ParameterEnum.user.name());
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

  /** Close the connection.  Call this before discarding the connection.
   */
  @Override
  public void disconnect() throws ManifoldCFException {
    closeSession();
    nameNodeProtocol = null;
    nameNodeHost = null;
    nameNodePort = null;
    user = null;
    super.disconnect();
  }

  /**
   * @throws ManifoldCFException
   */
  @Override
  public void poll() throws ManifoldCFException {
    if (lastSessionFetch == -1L) {
      return;
    }

    long currentTime = System.currentTimeMillis();
    if (currentTime >= lastSessionFetch + timeToRelease) {
      closeSession();
    }
  }

  protected void closeSession()
    throws ManifoldCFException {
    if (session != null) {
      try {
        // This can in theory throw an IOException, so it is possible it is doing socket
        // communication.  In practice, it's unlikely that there's any real IO, so I'm
        // NOT putting it in a background thread for now.
        session.close();
      } catch (InterruptedIOException e) {
        throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      } catch (IOException e) {
        Logging.agents.warn("HDFS: Error closing connection: "+e.getMessage(),e);
        // Eat the exception
      } finally {
        session = null;
        lastSessionFetch = -1L;
      }
    }
  }

  /** Set up a session */
  protected HDFSSession getSession() throws ManifoldCFException, ServiceInterruption {
    if (session == null) {
      if (nameNodeProtocol == null)
        nameNodeProtocol = "hdfs";

      if (nameNodeHost == null)
        throw new ManifoldCFException("Namenodehost must be specified");

      if (nameNodePort == null)
        throw new ManifoldCFException("Namenodeport must be specified");
      
      if (user == null)
        throw new ManifoldCFException("User must be specified");
      
      String nameNode = nameNodeProtocol + "://"+nameNodeHost+":"+nameNodePort;
      //System.out.println("Namenode = '"+nameNode+"'");

      /*
       * get connection to HDFS
       */
      GetSessionThread t = new GetSessionThread(nameNode,user);
      try {
        t.start();
        t.finishUp();
      } catch (InterruptedException e) {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
      } catch (java.net.SocketTimeoutException e) {
        handleIOException(e);
      } catch (InterruptedIOException e) {
        t.interrupt();
        handleIOException(e);
      } catch (URISyntaxException e) {
        handleURISyntaxException(e);
      } catch (IOException e) {
        handleIOException(e);
      }
      
      session = t.getResult();
    }
    lastSessionFetch = System.currentTimeMillis();
    return session;
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
  public VersionContext getPipelineDescription(Specification spec) throws ManifoldCFException, ServiceInterruption {
    HDFSOutputSpecs specs = new HDFSOutputSpecs(getSpecNode(spec));
    return new VersionContext(specs.toVersionString(),params,spec);
  }

  /** Add (or replace) a document in the output data store using the connector.
  * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be
  * necessary.
  *@param documentURI is the URI of the document.  The URI is presumed to be the unique identifier which the output data store will use to process
  * and serve the document.  This URI is constructed by the repository connector which fetches the document, and is thus universal across all output connectors.
  *@param pipelineDescription includes the description string that was constructed for this document by the getOutputDescription() method.
  *@param document is the document data to be processed (handed to the output data store).
  *@param authorityNameString is the name of the authority responsible for authorizing any access tokens passed in with the repository document.  May be null.
  *@param activities is the handle to an object that the implementer of a pipeline connector may use to perform operations, such as logging processing activity,
  * or sending a modified document to the next stage in the pipeline.
  *@return the document status (accepted or permanently rejected).
  *@throws IOException only if there's a stream error reading the document data.
  */
  @Override
  public int addOrReplaceDocumentWithException(String documentURI, VersionContext pipelineDescription, RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
    throws ManifoldCFException, ServiceInterruption, IOException {
    HDFSOutputSpecs specs = new HDFSOutputSpecs(getSpecNode(pipelineDescription.getSpecification()));
    try {

      /*
       * make file path
       */
      StringBuffer strBuff = new StringBuffer();
      if (specs.getRootPath() != null) {
        strBuff.append(specs.getRootPath());
      }
      strBuff.append("/");
      strBuff.append(documentURItoFilePath(documentURI));
      Path path = new Path(strBuff.toString());

      Long startTime = new Long(System.currentTimeMillis());
      createFile(path, document.getBinaryStream(),activities,documentURI);
      activities.recordActivity(startTime, INGEST_ACTIVITY, new Long(document.getBinaryLength()), documentURI, "OK", null);
      return DOCUMENTSTATUS_ACCEPTED;
    } catch (URISyntaxException e) {
      activities.recordActivity(null,INGEST_ACTIVITY,new Long(document.getBinaryLength()),documentURI,e.getClass().getSimpleName().toUpperCase(Locale.ROOT),"Failed to write document due to: " + e.getMessage());
      handleURISyntaxException(e);
      return DOCUMENTSTATUS_REJECTED;
    }

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

    try {
      HDFSOutputSpecs specs = new HDFSOutputSpecs(outputDescription);

      /*
       * make path
       */
      StringBuffer strBuff = new StringBuffer();
      if (specs.getRootPath() != null) {
        strBuff.append(specs.getRootPath());
      }
      strBuff.append("/");
      strBuff.append(documentURItoFilePath(documentURI));
      Path path = new Path(strBuff.toString());
      Long startTime = new Long(System.currentTimeMillis());
      deleteFile(path,activities,documentURI);
      activities.recordActivity(startTime, REMOVE_ACTIVITY, null, documentURI, "OK", null);
    } catch (URISyntaxException e) {
      activities.recordActivity(null,REMOVE_ACTIVITY,null,documentURI,e.getClass().getSimpleName().toUpperCase(Locale.ROOT),"Failed to delete document due to: " + e.getMessage());
      handleURISyntaxException(e);
    }
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
    super.outputConfigurationHeader(threadContext, out, locale, parameters, tabsArray);
    tabsArray.add(Messages.getString(locale,"HDFSOutputConnector.ServerTabName"));
    outputResource(EDIT_CONFIGURATION_JS, out, locale, null, null, null, null);
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
    super.outputConfigurationBody(threadContext, out, locale, parameters, tabName);
    HDFSOutputConfig config = this.getConfigParameters(parameters);
    outputResource(EDIT_CONFIGURATION_HTML, out, locale, config, tabName, null, null);
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
    HDFSOutputConfig.contextToConfig(variableContext, parameters);
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
    outputResource(VIEW_CONFIGURATION_HTML, out, locale, getConfigParameters(parameters), null, null, null);
  }

  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected a pipeline connection of the current type.  Its purpose is to add the required tabs
  * to the list, and to output any javascript methods that might be needed by the job editing HTML.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param os is the current pipeline specification for this connection.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber, List<String> tabsArray)
    throws ManifoldCFException, IOException {
    super.outputSpecificationHeader(out, locale, os, connectionSequenceNumber, tabsArray);
    tabsArray.add(Messages.getString(locale, "HDFSOutputConnector.PathTabName"));
    outputResource(EDIT_SPECIFICATION_JS, out, locale, null, null, new Integer(connectionSequenceNumber), null);
  }

  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected a pipeline connection of the current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editjob".
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param os is the current pipeline specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param actualSequenceNumber is the connection within the job that has currently been selected.
  *@param tabName is the current tab name.
  */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber, int actualSequenceNumber, String tabName)
    throws ManifoldCFException, IOException {
    super.outputSpecificationBody(out, locale, os, connectionSequenceNumber, actualSequenceNumber, tabName);
    HDFSOutputSpecs specs = getSpecParameters(os);
    outputResource(EDIT_SPECIFICATION_HTML, out, locale, specs, tabName, new Integer(connectionSequenceNumber), new Integer(actualSequenceNumber));
  }

  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the transformation specification accordingly.
  * The name of the posted form is "editjob".
  *@param variableContext contains the post data, including binary file-upload information.
  *@param locale is the preferred local of the output.
  *@param os is the current pipeline specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
  */
  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, Specification os,
    int connectionSequenceNumber)
    throws ManifoldCFException {
    ConfigurationNode specNode = getSpecNode(os);
    boolean bAdd = (specNode == null);
    if (bAdd) {
      specNode = new SpecificationNode(ParameterEnum.rootpath.name());
    }
    HDFSOutputSpecs.contextToSpecNode(variableContext, specNode, connectionSequenceNumber);
    if (bAdd) {
      os.addChild(os.getChildCount(), specNode);
    }

    return null;
  }

  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the pipeline specification information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param os is the current pipeline specification for this job.
  */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber)
    throws ManifoldCFException, IOException {
    outputResource(VIEW_SPECIFICATION_HTML, out, locale, getSpecParameters(os), null, new Integer(connectionSequenceNumber), null);
  }

  /**
   * @param os
   * @return
   */
  final private SpecificationNode getSpecNode(Specification os)
  {
    int l = os.getChildCount();
    for (int i = 0; i < l; i++) {
      SpecificationNode node = os.getChild(i);
      if (node.getType().equals(ParameterEnum.rootpath.name())) {
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
  final private HDFSOutputSpecs getSpecParameters(Specification os) throws ManifoldCFException {
    return new HDFSOutputSpecs(getSpecNode(os));
  }

  /**
   * @param configParams
   * @return
   */
  final private HDFSOutputConfig getConfigParameters(ConfigParams configParams) {
    if (configParams == null)
      configParams = getConfiguration();
    return new HDFSOutputConfig(configParams);
  }

  /** Read the content of a resource, replace the variable ${PARAMNAME} with the
   * value and copy it to the out.
   * 
   * @param resName
   * @param out
   * @throws ManifoldCFException */
  private static void outputResource(String resName, IHTTPOutput out, Locale locale, HDFSOutputParam params, String tabName, Integer sequenceNumber, Integer actualSequenceNumber) throws ManifoldCFException {
    Map<String,String> paramMap = null;
    if (params != null) {
      paramMap = params.buildMap();
      if (tabName != null) {
        paramMap.put("TabName", tabName);
      }
      if (actualSequenceNumber != null)
        paramMap.put("SelectedNum",actualSequenceNumber.toString());
    }
    else
    {
      paramMap = new HashMap<String,String>();
    }
    if (sequenceNumber != null)
      paramMap.put("SeqNum",sequenceNumber.toString());
    Messages.outputResourceWithVelocity(out, locale, resName, paramMap, true);
  }

  /**
   * @param documentURI
   * @return
   * @throws URISyntaxException
   */
  final private String documentURItoFilePath(String documentURI) throws URISyntaxException {
    StringBuffer path = new StringBuffer();
    URI uri = null;

    uri = new URI(documentURI);

    if (uri.getScheme() != null) {
      path.append(uri.getScheme());
      path.append("/");
    }

    if (uri.getHost() != null) {
      path.append(uri.getHost());
      if (uri.getPort() != -1) {
        path.append(":");
        path.append(Integer.toString(uri.getPort()));
      }
      if (uri.getRawPath() != null) {
        if (uri.getRawPath().length() == 0) {
          path.append("/");
        } else if (uri.getRawPath().equals("/")) {
          path.append(uri.getRawPath());
        } else {
          for (String name : uri.getRawPath().split("/")) {
            if (name != null && name.length() > 0) {
              path.append("/");
              path.append(name);
            }
          }
        }
      }
      if (uri.getRawQuery() != null) {
        path.append("?");
        path.append(uri.getRawQuery());
      }
    } else {
      if (uri.getRawSchemeSpecificPart() != null) {
        for (String name : uri.getRawSchemeSpecificPart().split("/")) {
          if (name != null && name.length() > 0) {
            path.append("/");
            path.append(name);
          }
        }
      }
    }

    if (path.toString().endsWith("/")) {
      path.append(".content");
    }
    return path.toString();
  }
  
  /** Handle URISyntaxException */
  protected static void handleURISyntaxException(URISyntaxException e)
    throws ManifoldCFException, ServiceInterruption
  {
    Logging.agents.error("Namenode URI is malformed: "+e.getMessage(),e);
    throw new ManifoldCFException("Namenode URI is malformed: "+e.getMessage(),e);
  }
  
  /** Handle IOException */
  protected static void handleIOException(IOException e)
    throws ManifoldCFException, ServiceInterruption
  {
    if (!(e instanceof java.net.SocketTimeoutException) && (e instanceof InterruptedIOException)) {
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
    }
    long currentTime = System.currentTimeMillis();
    Logging.agents.warn("HDFS output connection: IO exception: "+e.getMessage(),e);
    throw new ServiceInterruption("IO exception: "+e.getMessage(), e, currentTime + 300000L, currentTime + 3 * 60 * 60000L,-1,false);
  }

  protected static class CreateFileThread extends Thread {
    protected final HDFSSession session;
    protected final Path path;
    protected final InputStream input;
    protected Throwable exception = null;

    public CreateFileThread(HDFSSession session, Path path, InputStream input) {
      super();
      this.session = session;
      this.path = path;
      this.input = input;
      setDaemon(true);
    }

    public void run() {
      try {
        session.createFile(path,input);
      } catch (Throwable e) {
        this.exception = e;
      }
    }

    public void finishUp() throws InterruptedException, IOException {
      join();
      Throwable thr = exception;
      if (thr != null) {
        if (thr instanceof IOException) {
          throw (IOException) thr;
        } else if (thr instanceof RuntimeException) {
          throw (RuntimeException) thr;
        } else {
          throw (Error) thr;
        }
      }
    }
  }

  protected void createFile(Path path, InputStream input,IOutputAddActivity activities, String documentURI)
    throws ManifoldCFException, ServiceInterruption {
    CreateFileThread t = new CreateFileThread(getSession(), path, input);
    String errorCode = null;
    String errorDesc = null;
    try {
      t.start();
      t.finishUp();
    } catch (InterruptedException e) {
      t.interrupt();
      errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
      errorDesc = "Failed to write document due to: " + e.getMessage();
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    } catch (java.net.SocketTimeoutException e) {
      errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
      errorDesc = "Failed to write document due to: " + e.getMessage();
      handleIOException(e);
    } catch (InterruptedIOException e) {
      t.interrupt();
      errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
      errorDesc = "Failed to write document due to: " + e.getMessage();
      handleIOException(e);
    } catch (IOException e) {
      errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
      errorDesc = "Failed to write document due to: " + e.getMessage();
      handleIOException(e);
    } finally {
      if(errorCode != null & errorDesc != null){
        activities.recordActivity(null,INGEST_ACTIVITY,null,documentURI,errorCode,errorDesc);
      }
    }
  }

  protected static class DeleteFileThread extends Thread {
    protected final HDFSSession session;
    protected final Path path;
    protected Throwable exception = null;

    public DeleteFileThread(HDFSSession session, Path path) {
      super();
      this.session = session;
      this.path = path;
      setDaemon(true);
    }

    public void run() {
      try {
        session.deleteFile(path);
      } catch (Throwable e) {
        this.exception = e;
      }
    }

    public void finishUp() throws InterruptedException, IOException {
      join();
      Throwable thr = exception;
      if (thr != null) {
        if (thr instanceof IOException) {
          throw (IOException) thr;
        } else if (thr instanceof RuntimeException) {
          throw (RuntimeException) thr;
        } else {
          throw (Error) thr;
        }
      }
    }
  }

  protected void deleteFile(Path path,IOutputRemoveActivity activities,String documentURI)
    throws ManifoldCFException, ServiceInterruption {
    // Establish a session
    DeleteFileThread t = new DeleteFileThread(getSession(),path);
    String errorCode = null;
    String errorDesc = null;
    try {
      t.start();
      t.finishUp();
    } catch (InterruptedException e) {
        t.interrupt();
        errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
        errorDesc = "Failed to write document due to: " + e.getMessage();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    } catch (java.net.SocketTimeoutException e) {
        errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
        errorDesc = "Failed to write document due to: " + e.getMessage();
        handleIOException(e);
    } catch (InterruptedIOException e) {
        t.interrupt();
        errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
        errorDesc = "Failed to write document due to: " + e.getMessage();
        handleIOException(e);
    } catch (IOException e) {
        errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
        errorDesc = "Failed to write document due to: " + e.getMessage();
        handleIOException(e);
    } finally {
        if(errorCode != null & errorDesc != null){
            activities.recordActivity(null,REMOVE_ACTIVITY,null,documentURI,errorCode,errorDesc);
        }
    }
  }


  protected static class CheckConnectionThread extends Thread {
    protected final HDFSSession session;
    protected Throwable exception = null;

    public CheckConnectionThread(HDFSSession session) {
      super();
      this.session = session;
      setDaemon(true);
    }

    public void run() {
      try {
        session.getRepositoryInfo();
      } catch (Throwable e) {
        this.exception = e;
      }
    }

    public void finishUp() throws InterruptedException, IOException {
      join();
      Throwable thr = exception;
      if (thr != null) {
        if (thr instanceof IOException) {
          throw (IOException) thr;
        } else if (thr instanceof RuntimeException) {
          throw (RuntimeException) thr;
        } else {
          throw (Error) thr;
        }
      }
    }
  }

  /**
   * @throws ManifoldCFException
   * @throws ServiceInterruption
   */
  protected void checkConnection() throws ManifoldCFException, ServiceInterruption {
    CheckConnectionThread t = new CheckConnectionThread(getSession());
    try {
      t.start();
      t.finishUp();
      return;
    } catch (InterruptedException e) {
      t.interrupt();
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
    } catch (java.net.SocketTimeoutException e) {
      handleIOException(e);
    } catch (InterruptedIOException e) {
      t.interrupt();
      handleIOException(e);
    } catch (IOException e) {
      handleIOException(e);
    }
  }

  protected static class GetSessionThread extends Thread {
    protected final String nameNode;
    protected final String user;
    protected Throwable exception = null;
    protected HDFSSession session = null;

    public GetSessionThread(String nameNode, String user) {
      super();
      this.nameNode = nameNode;
      this.user = user;
      setDaemon(true);
    }

    public void run() {
      try {
        // Create a session
        session = new HDFSSession(nameNode, user);
      } catch (Throwable e) {
        this.exception = e;
      }
    }

    public void finishUp()
      throws InterruptedException, IOException, URISyntaxException {
      join();
      Throwable thr = exception;
      if (thr != null) {
        if (thr instanceof IOException) {
          throw (IOException) thr;
        } else if (thr instanceof URISyntaxException) {
          throw (URISyntaxException) thr;
        } else if (thr instanceof RuntimeException) {
          throw (RuntimeException) thr;
        } else {
          throw (Error) thr;
        }
      }
    }
    
    public HDFSSession getResult() {
      return session;
    }
  }

  public static class HDFSOutputSpecs extends HDFSOutputParam {
    /**
     * 
     */
    private static final long serialVersionUID = 1145652730572662025L;

    final public static ParameterEnum[] SPECIFICATIONLIST = {
      ParameterEnum.rootpath
    };

    private final String rootPath;

    /** Build a set of ElasticSearch parameters by reading an JSON object
     * 
     * @param json
     * @throws JSONException
     * @throws ManifoldCFException
     */
    public HDFSOutputSpecs(String versionString) throws ManifoldCFException {
      super(SPECIFICATIONLIST);
      int index = 0;
      StringBuilder rootPathBuffer = new StringBuilder();
      index = unpack(rootPathBuffer,versionString,index,'+');
      this.rootPath = rootPathBuffer.toString();
      // MHL
    }

    /** Build a set of ElasticSearch parameters by reading an instance of
     * SpecificationNode.
     * 
     * @param node
     * @throws ManifoldCFException
     */
    public HDFSOutputSpecs(ConfigurationNode node) throws ManifoldCFException {
      super(SPECIFICATIONLIST);
      String rootPath = null;
      for (ParameterEnum param : SPECIFICATIONLIST) {
        String value = null;
        if (node != null) {
          value = node.getAttributeValue(param.name());
        }
        if (value == null) {
          value = param.defaultValue;
        }
        put(param, value);
      }
      rootPath = getRootPath();
      this.rootPath = rootPath;
    }

    /**
      * @param variableContext
      * @param specNode
      */
    public static void contextToSpecNode(IPostParameters variableContext, ConfigurationNode specNode, int sequenceNumber) {
      for (ParameterEnum param : SPECIFICATIONLIST) {
        String p = variableContext.getParameter("s"+sequenceNumber+"_"+param.name().toLowerCase(Locale.ROOT));
        if (p != null) {
          specNode.setAttribute(param.name(), p);
        }
      }
    }

    /** @return a JSON representation of the parameter list */
    public String toVersionString() {
      StringBuilder sb = new StringBuilder();
      pack(sb,rootPath,'+');
      return sb.toString();
    }

    /**
     * @return
     */
    public String getRootPath() {
      return get(ParameterEnum.rootpath);
    }

    /**
     * @param content
     * @return
     * @throws ManifoldCFException
     */
    private final static TreeSet<String> createStringSet(String content) throws ManifoldCFException {
      TreeSet<String> set = new TreeSet<String>();
      BufferedReader br = null;
      StringReader sr = null;
      try {
        sr = new StringReader(content);
        br = new BufferedReader(sr);
        String line = null;
        while ((line = br.readLine()) != null) {
          line = line.trim();
          if (line.length() > 0) {
            set.add(line);
          }
        }
        return set;
      } catch (IOException e) {
        throw new ManifoldCFException(e.getMessage(),e);
      } finally {
        if (br != null) {
          IOUtils.closeQuietly(br);
        }
      }
    }

  }

}
