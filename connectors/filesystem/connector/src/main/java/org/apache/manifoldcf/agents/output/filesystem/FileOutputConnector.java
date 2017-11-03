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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.StringReader;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.SystemUtils;

import java.net.URI;
import java.net.URISyntaxException;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.agents.output.BaseOutputConnector;
import org.apache.manifoldcf.agents.system.Logging;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.ConfigurationNode;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.core.interfaces.VersionContext;

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
  public VersionContext getPipelineDescription(Specification spec) throws ManifoldCFException, ServiceInterruption {
    FileOutputSpecs specs = new FileOutputSpecs(getSpecNode(spec));
    return new VersionContext(specs.toVersionString(),params,spec);
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
  public int addOrReplaceDocumentWithException(String documentURI, VersionContext outputDescription, RepositoryDocument document, String authorityNameString, IOutputAddActivity activities) throws ManifoldCFException, ServiceInterruption, IOException {
    // Establish a session
    getSession();

    FileOutputConfig config = getConfigParameters(null);

    FileOutputSpecs specs = new FileOutputSpecs(getSpecNode(outputDescription.getSpecification()));;
    StringBuffer path = new StringBuffer();

    String errorCode = "OK";
    String errorDesc = null;

    try {
      /*
        * make file path
        */
      if (specs.getRootPath() != null) {
        path.append(specs.getRootPath());
      }
        
      // If the path does not yet exist at the root level, it is dangerous to create it.
      File currentPath = new File(path.toString());
      if (!currentPath.exists())
        throw new ManifoldCFException("Root path does not yet exist: '"+currentPath+"'");
      if (!currentPath.isDirectory())
        throw new ManifoldCFException("Root path is not a directory: '"+currentPath+"'");
        
      String filePath = documentURItoFilePath(documentURI);
        
      // Build path one level at a time.  This is needed because there may be a collision at
      // every level.
      int index = 0;
      while (true)
      {
        int currentIndex = filePath.indexOf("/",index);
        if (currentIndex == -1)
          break;
        String dirName = filePath.substring(index,currentIndex);
        File newPath = new File(currentPath, dirName);
        index = currentIndex + 1;
        int suffix = 1;
        while (true)
        {
          if (newPath.exists() && newPath.isDirectory())
            break;
          // Try to create it.  If we fail, check if it now exists as a file.
          if (newPath.mkdir())
            break;
          // Hmm, didn't create.  If it is a file, we suffered a collision, so try again with ".N" as a suffix.
          if (newPath.exists())
          {
            if (newPath.isDirectory())
              break;
            newPath = new File(currentPath, dirName + "." + suffix);
            suffix++;
          }
          else
          {
              errorCode = activities.CREATED_DIRECTORY;
              errorDesc = "Could not create directory '\"+newPath+\"'.  Permission issue?";
              throw new ManifoldCFException(errorDesc);
          }
        }
        // Directory successfully created!
        currentPath = newPath;
        // Go on to the next one.
      }
        
      // Path successfully created.  Now create file.
      FileOutputStream output = null;
      String fileName = filePath.substring(index);
      File outputPath = new File(currentPath, fileName);
      int fileSuffix = 1;
      while (true)
      {
        try
        {
          output = new FileOutputStream(outputPath);
          break;
        }
        catch (FileNotFoundException e)
        {
          // Figure out why it could not be created.
          if (outputPath.exists() && !outputPath.isFile())
          {
            // try a new file
            outputPath = new File(currentPath, fileName + "." + fileSuffix);
            fileSuffix++;
            continue;
          }
          // Probably some other error
          errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
          errorDesc = "Could not create file '" + outputPath + "': " +e.getMessage();
          throw new ManifoldCFException(errorDesc,e);
        }
      }

      try {
        /*
          * lock file
          */
        FileChannel channel = output.getChannel();
        FileLock lock = channel.tryLock();
        if (lock == null){
          errorCode = ServiceInterruption.class.getSimpleName().toUpperCase(Locale.ROOT);
          errorDesc = "Could not lock file: '"+outputPath+"'";
          throw new ServiceInterruption(errorDesc,null,1000L,-1L,10,false);
        }


        try {

          /*
            * write file
            */
          InputStream input = document.getBinaryStream();
          byte buf[] = new byte[65536];
          int len;
          while((len = input.read(buf)) != -1) {
            output.write(buf, 0, len);
          }
          output.flush();
        } finally {
          // Unlock
          try {
            if (lock != null) {
              lock.release();
            }
          } catch (ClosedChannelException e) {
          }
        }
      } finally {
        try {
          output.close();
        } catch (IOException e) {
        }
      }
    } catch (URISyntaxException e) {
      errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
      errorDesc = "Failed to write document due to: " + e.getMessage();
      handleURISyntaxException(e);
      return DOCUMENTSTATUS_REJECTED;
    } catch (FileNotFoundException e) {
      errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
      errorDesc = "Failed to write document due to: " + e.getMessage();
      handleFileNotFoundException(e);
      return DOCUMENTSTATUS_REJECTED;
    } catch (SecurityException e) {
      errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
      errorDesc = "Failed to write document due to: " + e.getMessage();
      handleSecurityException(e);
      return DOCUMENTSTATUS_REJECTED;
    } catch (IOException e) {
      errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
      errorDesc = "Failed to write document due to: " + e.getMessage();
      handleIOException(e);
      return DOCUMENTSTATUS_REJECTED;
    } finally {
        activities.recordActivity(null, INGEST_ACTIVITY, new Long(document.getBinaryLength()), documentURI, errorCode, errorDesc);
    }

    return DOCUMENTSTATUS_ACCEPTED;
  }

  protected static void handleURISyntaxException(URISyntaxException e)
    throws ManifoldCFException, ServiceInterruption {
    Logging.agents.error("FileSystem: URISyntaxException: "+e.getMessage(),e);
    throw new ManifoldCFException(e.getMessage(),e);
  }

  protected static void handleSecurityException(SecurityException e)
    throws ManifoldCFException, ServiceInterruption {
    Logging.agents.error("FileSystem: SecurityException: "+e.getMessage(),e);
    throw new ManifoldCFException(e.getMessage(),e);
  }

  protected static void handleFileNotFoundException(FileNotFoundException e)
    throws ManifoldCFException, ServiceInterruption {
    Logging.agents.error("FileSystem: Path is illegal: "+e.getMessage(),e);
    throw new ManifoldCFException(e.getMessage(),e);
  }

  /** Handle IOException */
  protected static void handleIOException(IOException e)
    throws ManifoldCFException, ServiceInterruption
  {
    if (!(e instanceof java.net.SocketTimeoutException) && (e instanceof InterruptedIOException)) {
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
    }
    long currentTime = System.currentTimeMillis();
    Logging.agents.warn("FileSystem: IO exception: "+e.getMessage(),e);
    throw new ServiceInterruption("IO exception: "+e.getMessage(), e, currentTime + 300000L, currentTime + 3 * 60 * 60000L,-1,false);
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

    String errorCode = "OK";
    String errorDesc = null;

    FileOutputConfig config = getConfigParameters(null);

    FileOutputSpecs specs = new FileOutputSpecs(outputDescription);

    StringBuffer path = new StringBuffer();
    try {

      if(specs.getRootPath() != null) {
        path.append(specs.getRootPath());
      }

      // We cannot remove documents, because it is unsafe to do so.
      // Paths that were created when the document existed will not
      // be found if it goes away.  So we have to leave a grave marker,
      // in this case a zero-length file, instead.
      
      // If the path does not yet exist at the root level, it is dangerous to create it.
      File currentPath = new File(path.toString());
      if (!currentPath.exists())
        return;
      if (!currentPath.isDirectory())
        return;
      
      String filePath = documentURItoFilePath(documentURI);
      
      // Build path one level at a time.  This is needed because there may be a collision at
      // every level.  If we don't find a directory where we expect it, we just exit.
      int index = 0;
      while (true)
      {
        int currentIndex = filePath.indexOf("/",index);
        if (currentIndex == -1)
          break;
        String dirName = filePath.substring(index,currentIndex);
        File newPath = new File(currentPath, dirName);
        index = currentIndex + 1;
        int suffix = 1;
        while (true)
        {
          if (!newPath.exists())
            return;
          if (newPath.isDirectory())
            break;
          // It's a file.  Move on to the next one.
          newPath = new File(currentPath, dirName + "." + suffix);
          suffix++;
        }
        // Directory successfully created!
        currentPath = newPath;
        // Go on to the next level.
      }
      
      // Path found.  Now, see if we can find the file to null out.
      FileOutputStream output = null;
      String fileName = filePath.substring(index);
      File outputPath = new File(currentPath, fileName);
      int fileSuffix = 1;
      while (true)
      {
        if (!outputPath.exists())
          return;
        if (!outputPath.isFile())
        {
          // Try a new one
          outputPath = new File(currentPath, fileName + "." + fileSuffix);
          fileSuffix++;
          continue;
        }
        // Null it out!
        try
        {
          output = new FileOutputStream(outputPath);
          break;
        }
        catch (FileNotFoundException e)
        {
          // Probably some other error
          errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
          errorDesc = "Couldn't delete the file due to:" + e.getMessage();
          throw new ManifoldCFException("Could not zero out file '"+outputPath+"': "+e.getMessage(),e);
        }
      }
      // Just close it, to make a zero-length grave marker.
      output.close();
    } catch (URISyntaxException e) {
      errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
      errorDesc = "Failed to delete document due to: " + e.getMessage();
      handleURISyntaxException(e);
    } catch (FileNotFoundException e) {
      errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
      errorDesc = "Failed to delete document due to: " + e.getMessage();
      handleFileNotFoundException(e);
    } catch (SecurityException e) {
      errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
      errorDesc = "Failed to delete document due to: " + e.getMessage();
      handleSecurityException(e);
    } catch (IOException e) {
      errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
      errorDesc = "Failed to delete document due to: " + e.getMessage();
      handleIOException(e);
    } finally {
        activities.recordActivity(null, REMOVE_ACTIVITY, null, documentURI, errorCode, errorDesc);
    }
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
    tabsArray.add(Messages.getString(locale, "FileConnector.PathTabName"));
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
    FileOutputSpecs specs = getSpecParameters(os);
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
      specNode = new SpecificationNode(FileOutputConstant.PARAM_ROOTPATH);
    }
    FileOutputSpecs.contextToSpecNode(variableContext, specNode, connectionSequenceNumber);
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
  final private FileOutputSpecs getSpecParameters(Specification os) throws ManifoldCFException {
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
  private static void outputResource(String resName, IHTTPOutput out, Locale locale, FileOutputParam params, String tabName,
    Integer sequenceNumber, Integer currentSequenceNumber) throws ManifoldCFException {
    Map<String,String> paramMap = null;
    if (params != null) {
      paramMap = params.buildMap();
      if (tabName != null) {
        paramMap.put("TabName", tabName);
      }
      if (currentSequenceNumber != null)
        paramMap.put("SelectedNum", currentSequenceNumber.toString());
    }
    else
    {
      paramMap = new HashMap<String,String>();
    }
    if (sequenceNumber != null)
      paramMap.put("SeqNum", sequenceNumber.toString());
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
    URI uri = new URI(documentURI);
    if (SystemUtils.IS_OS_WINDOWS)
      windowsFileName(path, uri);
    else
      unixFileName(path, uri);
    return path.toString();
  }
  
  final private void windowsFileName(final StringBuffer path, final URI uri) {
    // Illegal characters: \ / : * ? " < > |
    boolean endsWithSlash = false;
    if (uri.getScheme() != null) {
      path.append(uri.getScheme());
      path.append("/");
      endsWithSlash = true;
    }

    if (uri.getHost() != null) {
      path.append(uri.getHost());
      endsWithSlash = false;
      if (uri.getPort() != -1) {
        path.append("_");
        path.append(uri.getPort());
      }
      if (uri.getRawPath() != null) {
        if (uri.getRawPath().length() == 0) {
          path.append("/");
          endsWithSlash = true;
        } else if (uri.getRawPath().equals("/")) {
          path.append(uri.getRawPath());
          endsWithSlash = false;
        } else {
          for (String name : uri.getRawPath().split("/")) {
            if (name.length() > 0) {
              path.append("/");
              path.append(convertWindowsString(name));
              endsWithSlash = false;
            }
          }
        }
      }
      if (uri.getRawQuery() != null) {
        path.append("/");
        path.append(convertWindowsString(uri.getRawQuery()));
        endsWithSlash = false;
      }
    } else {
      if (uri.getRawSchemeSpecificPart() != null) {
        for (String name : uri.getRawSchemeSpecificPart().split("/")) {
          if (name.length() > 0) {
            path.append("/");
            path.append(convertWindowsString(name));
            endsWithSlash = false;
          }
        }
      }
    }

    if (endsWithSlash) {
      path.append(".content");
    }
  }
  
  final private String convertWindowsString(final String input) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      // Handle filename disallowed special characters!
      if (c == ':' || c == '/' || c == '\\' | c == '*' | c == '"' | c == '?' | c == '|' || c == '<' || c == '>') {
        sb.append('_');
      }
      else
        sb.append(c);
    }
    return sb.toString();
  }

  final private void unixFileName(final StringBuffer path, final URI uri) {
    boolean endsWithSlash = false;
    if (uri.getScheme() != null) {
      path.append(uri.getScheme());
      path.append("/");
      endsWithSlash = true;
    }

    if (uri.getHost() != null) {
      path.append(uri.getHost());
      endsWithSlash = false;
      if (uri.getPort() != -1) {
        path.append(":");
        path.append(uri.getPort());
      }
      if (uri.getRawPath() != null) {
        if (uri.getRawPath().length() == 0) {
          path.append("/");
          endsWithSlash = true;
        } else if (uri.getRawPath().equals("/")) {
          path.append(uri.getRawPath());
          endsWithSlash = false;
        } else {
          for (String name : uri.getRawPath().split("/")) {
            if (name.length() > 0) {
              path.append("/");
              path.append(convertUnixString(name));
              endsWithSlash = false;
            }
          }
        }
      }
      if (uri.getRawQuery() != null) {
        path.append("?");
        path.append(convertUnixString(uri.getRawQuery()));
        endsWithSlash = false;
      }
    } else {
      if (uri.getRawSchemeSpecificPart() != null) {
        for (String name : uri.getRawSchemeSpecificPart().split("/")) {
          if (name.length() > 0) {
            path.append("/");
            path.append(convertUnixString(name));
            endsWithSlash = false;
          }
        }
      }
    }

    if (endsWithSlash) {
      path.append(".content");
    }
  }
  
  final private String convertUnixString(final String input) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      // Handle filename disallowed special characters!
      if (c == ':') {
        // MHL for what really happens to colons
      }
      else
        sb.append(c);
    }
    return sb.toString();
  }
  
  protected static class FileOutputSpecs extends FileOutputParam {
    /**
     * 
     */
    private static final long serialVersionUID = 1859652730572662025L;

    final public static ParameterEnum[] SPECIFICATIONLIST = {
      ParameterEnum.ROOTPATH
    };

    private final String rootPath;

    /** Build a set of parameters from a packed version string.
     *
     * @param versionString composed of "packed" output specification parameters.
     * @throws ManifoldCFException
     */
    public FileOutputSpecs(String versionString) throws ManifoldCFException {
      super(SPECIFICATIONLIST);
      int index = 0;
      for (ParameterEnum param : SPECIFICATIONLIST) {
        StringBuilder sb = new StringBuilder();
        index = unpack(sb, versionString, index, '+');
        put(param, sb.toString());
      }
      this.rootPath = this.getRootPath();
    }

    /** Build a set of ElasticSearch parameters by reading an instance of
     * SpecificationNode.
     * 
     * @param node
     * @throws ManifoldCFException
     */
    public FileOutputSpecs(ConfigurationNode node) throws ManifoldCFException {
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

    /** @return a version string representation of the parameter list */
    public String toVersionString() {
      StringBuilder sb = new StringBuilder();
      pack(sb,rootPath,'+');
      return sb.toString();
    }

    /**
     * @return
     */
    public String getRootPath() {
      return get(ParameterEnum.ROOTPATH);
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
        throw new ManifoldCFException(e);
      } finally {
        if (br != null) {
          IOUtils.closeQuietly(br);
        }
      }
    }

  }

}
