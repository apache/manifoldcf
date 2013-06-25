/* $Id: FileConnector.java 995085 2010-09-08 15:13:38Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.hdfs;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.connectors.hdfs.HDFSSession;
import org.apache.manifoldcf.crawler.connectors.hdfs.Messages;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.core.common.XThreadInputStream;
import org.apache.manifoldcf.core.common.XThreadStringBuffer;
import org.apache.manifoldcf.core.extmimemap.ExtensionMimeMap;

import java.security.GeneralSecurityException;
import java.util.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;

/** This is the "repository connector" for a file system.  It's a relative of the share crawler, and should have
* comparable basic functionality, with the exception of the ability to use ActiveDirectory and look at other shares.
*/
public class HDFSRepositoryConnector extends org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector
{
  public static final String _rcsid = "@(#)$Id: FileConnector.java 995085 2010-09-08 15:13:38Z kwright $";

  // Activities that we know about
  protected final static String ACTIVITY_READ = "read document";

  // Relationships we know about
  protected static final String RELATIONSHIP_CHILD = "child";

  // Activities list
  protected static final String[] activitiesList = new String[]{ACTIVITY_READ};

  protected String nameNode = null;
  protected String user = null;
  protected Configuration config = null;
  protected HDFSSession session = null;
  protected long lastSessionFetch = -1L;
  protected static final long timeToRelease = 300000L;

  /*
   * Constructor.
   */
  public HDFSRepositoryConnector()
  {
  }

  /** Tell the world what model this connector uses for getDocumentIdentifiers().
   * This must return a model value as specified above.
   *@return the model type value.
   */
  @Override
  public int getConnectorModel()
  {
    return MODEL_CHAINED_ADD_CHANGE;
  }

/** Return the list of relationship types that this connector recognizes.
   *@return the list.
   */
  @Override
  public String[] getRelationshipTypes()
  {
    return new String[]{RELATIONSHIP_CHILD};
  }

  /** List the activities we might report on.
   */
  @Override
  public String[] getActivitiesList()
  {
    return activitiesList;
  }

  /** For any given document, list the bins that it is a member of.
   */
  @Override
  public String[] getBinNames(String documentIdentifier)
  {
    return new String[]{"HDFS"};
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

/* (non-Javadoc)
   * @see org.apache.manifoldcf.core.connector.BaseConnector#connect(org.apache.manifoldcf.core.interfaces.ConfigParams)
   */
  @Override
  public void connect(ConfigParams configParams) {
    super.connect(configParams);

    nameNode = configParams.getParameter("namenode");
    
    user = configParams.getParameter("user");
    
    /*
     * make Configuration
     */
    ClassLoader ocl = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(org.apache.hadoop.conf.Configuration.class.getClassLoader());
      config = new Configuration();
      config.set("fs.default.name", nameNode);
    } finally {
      Thread.currentThread().setContextClassLoader(ocl);
    }
  }

/* (non-Javadoc)
   * @see org.apache.manifoldcf.core.connector.BaseConnector#disconnect()
   */
  @Override
  public void disconnect() throws ManifoldCFException {
    if (session != null) {
      try {
        session.close();
      } catch (IOException e) {
    	throw new ManifoldCFException(e);  
      } finally {
        session = null;
        lastSessionFetch = -1L;
      }
    }
  
    config.clear();
    config = null;
    user = null;
    nameNode = null;
    super.disconnect();
  }

/**
   * Set up a session
   */
  protected void getSession() throws ManifoldCFException, ServiceInterruption {
    if (session == null) {
      if (StringUtils.isEmpty(nameNode)) {
        throw new ManifoldCFException("Parameter namenode required but not set");
      }
      if (Logging.connectors.isDebugEnabled()) {
        Logging.connectors.debug("HDFS: NameNode = '" + nameNode + "'");
      }

      if (StringUtils.isEmpty(user)) {
        throw new ManifoldCFException("Parameter user required but not set");
      }
      if (Logging.connectors.isDebugEnabled()) {
        Logging.connectors.debug("HDFS: User = '" + user + "'");
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
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
      } catch (java.net.SocketTimeoutException e) {
        Logging.connectors.warn("HDFS: Socket timeout: " + e.getMessage(), e);
        handleIOException(e);
      } catch (InterruptedIOException e) {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
      } catch (GeneralSecurityException e) {
        Logging.connectors.error("HDFS: " +  "General security error initializing transport: " + e.getMessage(), e);
        handleGeneralSecurityException(e);
      } catch (IOException e) {
        Logging.connectors.warn("HDFS: IO error: " + e.getMessage(), e);
        handleIOException(e);
      }
    }
    lastSessionFetch = System.currentTimeMillis();
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

  /**
   * @throws ManifoldCFException
   * @throws ServiceInterruption
   */
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
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
    } catch (java.net.SocketTimeoutException e) {
      Logging.connectors.warn("HDFS: Socket timeout: " + e.getMessage(), e);
      handleIOException(e);
    } catch (InterruptedIOException e) {
      t.interrupt();
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
    } catch (IOException e) {
      Logging.connectors.warn("HDFS: Error checking repository: " + e.getMessage(), e);
      handleIOException(e);
    }
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
      if (session != null) {
        try {
          session.close();
        } catch (IOException e) {
          throw new ManifoldCFException(e);  
        } finally {
          session = null;
          lastSessionFetch = -1L;
        }
      }
    }
  }

  /**
   * Queue "seed" documents. Seed documents are the starting places for
   * crawling activity. Documents are seeded when this method calls
   * appropriate methods in the passed in ISeedingActivity object.
   *
   * This method can choose to find repository changes that happen only during
   * the specified time interval. The seeds recorded by this method will be
   * viewed by the framework based on what the getConnectorModel() method
   * returns.
   *
   * It is not a big problem if the connector chooses to create more seeds
   * than are strictly necessary; it is merely a question of overall work
   * required.
   *
   * The times passed to this method may be interpreted for greatest
   * efficiency. The time ranges any given job uses with this connector will
   * not overlap, but will proceed starting at 0 and going to the "current
   * time", each time the job is run. For continuous crawling jobs, this
   * method will be called once, when the job starts, and at various periodic
   * intervals as the job executes.
   *
   * When a job's specification is changed, the framework automatically resets
   * the seeding start time to 0. The seeding start time may also be set to 0
   * on each job run, depending on the connector model returned by
   * getConnectorModel().
   *
   * Note that it is always ok to send MORE documents rather than less to this
   * method.
   *
   * @param activities is the interface this method should use to perform
   * whatever framework actions are desired.
   * @param spec is a document specification (that comes from the job).
   * @param startTime is the beginning of the time range to consider,
   * inclusive.
   * @param endTime is the end of the time range to consider, exclusive.
   * @param jobMode is an integer describing how the job is being run, whether
   * continuous or once-only.
   */
  @Override
  public void addSeedDocuments(ISeedingActivity activities,
      DocumentSpecification spec, long startTime, long endTime, int jobMode)
      throws ManifoldCFException, ServiceInterruption {

    String path = StringUtils.EMPTY;
    int i = 0;
    while (i < spec.getChildCount()) {
      SpecificationNode sn = spec.getChild(i);
      if (sn.getType().equals("startpoint")) {
        path = sn.getAttributeValue("path");
  
        getSession();
        GetSeedsThread t = new GetSeedsThread(path);
        try {
          t.start();
          boolean wasInterrupted = false;
          try {
            XThreadStringBuffer seedBuffer = t.getBuffer();

            // Pick up the paths, and add them to the activities, before we join with the child thread.
            while (true) {
              // The only kind of exceptions this can throw are going to shut the process down.
              String docPath = seedBuffer.fetch();
              if (docPath ==  null) {
                break;
              }
              // Add the pageID to the queue
              activities.addSeedDocument(docPath);
            }
          } catch (InterruptedException e) {
            wasInterrupted = true;
            throw e;
          } catch (ManifoldCFException e) {
            if (e.getErrorCode() == ManifoldCFException.INTERRUPTED) {
              wasInterrupted = true;
            }
            throw e;
          } finally {
            if (!wasInterrupted) {
              t.finishUp();
            }
          }
        } catch (InterruptedException e) {
          t.interrupt();
          throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
        }
      }
      i++;
    }
  }

  /** Get document versions given an array of document identifiers.
  * This method is called for EVERY document that is considered. It is therefore important to perform
  * as little work as possible here.
  * The connector will be connected before this method can be called.
  *@param documentIdentifiers is the array of local document identifiers, as understood by this connector.
  *@param oldVersions is the corresponding array of version strings that have been saved for the document identifiers.
  *   A null value indicates that this is a first-time fetch, while an empty string indicates that the previous document
  *   had an empty version string.
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is the current document specification for the current job.  If there is a dependency on this
  * specification, then the version string should include the pertinent data, so that reingestion will occur
  * when the specification changes.  This is primarily useful for metadata.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@param usesDefaultAuthority will be true only if the authority in use for these documents is the default one.
  *@return the corresponding version strings, with null in the places where the document no longer exists.
  * Empty version strings indicate that there is no versioning ability for the corresponding document, and the document
  * will always be processed.
  */
  public String[] getDocumentVersions(String[] documentIdentifiers, String[] oldVersions, IVersionActivity activities,
    DocumentSpecification spec, int jobMode, boolean usesDefaultAuthority)
    throws ManifoldCFException, ServiceInterruption
  {
    int i = 0;
    
    /*
     * get filepathtouri value
     */
    boolean filePathToUri = false;
    i = 0;
    while (i < spec.getChildCount()) {
      SpecificationNode sn = spec.getChild(i++);
      if (sn.getType().equals("filepathtouri")) {
        filePathToUri = Boolean.valueOf(sn.getValue());
      }
    }

    String[] rval = new String[documentIdentifiers.length];
    for (i = 0; i < rval.length; i++) {
      getSession();
      GetObjectThread objt = new GetObjectThread(documentIdentifiers[i]);
      try {
        objt.start();
        objt.finishUp();
      } catch (InterruptedException e) {
        objt.interrupt();
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
      }
      
      try {
        Path path = objt.getResponse();
        if (session.getFileSystem().exists(path)) {
          if (session.getFileSystem().getFileStatus(path).isDir()) {
            long lastModified = session.getFileSystem().getFileStatus(path).getModificationTime();
            rval[i] = new Long(lastModified).toString();
          } else {
            long fileLength = session.getFileSystem().getFileStatus(path).getLen();
            if (activities.checkLengthIndexable(fileLength)) {
              long lastModified = session.getFileSystem().getFileStatus(path).getModificationTime();
              StringBuilder sb = new StringBuilder();
              if (filePathToUri) {
                sb.append("+");
              } else {
                sb.append("-");
              }
              sb.append(new Long(lastModified).toString()).append(":").append(new Long(fileLength).toString());
              rval[i] = sb.toString();
            } else {
              rval[i] = null;
            }
          }
        } else {
          rval[i] = null;
        }
      } catch (IOException e) {
        objt.interrupt();
        throw new ManifoldCFException(e);
      }
    }
    
    return rval;
  }


  /** Process a set of documents.
  * This is the method that should cause each document to be fetched, processed, and the results either added
  * to the queue of documents for the current job, and/or entered into the incremental ingestion manager.
  * The document specification allows this class to filter what is done based on the job.
  *@param documentIdentifiers is the set of document identifiers to process.
  *@param activities is the interface this method should use to queue up new document references
  * and ingest documents.
  *@param spec is the document specification.
  *@param scanOnly is an array corresponding to the document identifiers.  It is set to true to indicate when the processing
  * should only find other references, and should not actually call the ingestion methods.
  */
  @Override
  public void processDocuments(String[] documentIdentifiers, String[] versions, IProcessActivity activities, DocumentSpecification spec, boolean[] scanOnly)
    throws ManifoldCFException, ServiceInterruption
  {
    try
    {
      int i = 0;
      while (i < documentIdentifiers.length)
      {
        String version = versions[i];
        String documentIdentifier = documentIdentifiers[i];
        Path path = new Path(documentIdentifier);
        FileStatus fileStatus = session.getFileSystem().getFileStatus(path);
        if (session.getFileSystem().exists(path))
        {
          if (fileStatus.isDir())
          {
            // Queue up stuff for directory
            long startTime = System.currentTimeMillis();
            String errorCode = "OK";
            String errorDesc = null;
            String entityReference = documentIdentifier;
            try
            {
              try
              {
                FileStatus[] fileStatuses = session.getFileSystem().listStatus(path);
                if (fileStatuses != null)
                {
                  int j = 0;
                  while (j < fileStatuses.length)
                  {
                    FileStatus fs = fileStatuses[j++];
                    String canonicalPath = fs.getPath().toString();
                    if (checkInclude(session.getFileSystem().getUri().toString(),fs,canonicalPath,spec))
                      activities.addDocumentReference(canonicalPath,documentIdentifier,RELATIONSHIP_CHILD);
                  }
                }
              }
              catch (IOException e)
              {
                errorCode = "IO ERROR";
                errorDesc = e.getMessage();
                throw new ManifoldCFException("IO Error: "+e.getMessage(),e);
              }
            }
            finally
            {
              activities.recordActivity(new Long(startTime),ACTIVITY_READ,null,entityReference,errorCode,errorDesc,null);
            }
          }
          else
          {
            if (!scanOnly[i])
            {
              // We've already avoided queuing documents that we don't want, based on file specifications.
              // We still need to check based on file data.
              if (checkIngest(session.getFileSystem().getUri().toString(),fileStatus,spec))
              {
                int j = 0;

                /*
                 * get repository paths
                 */
                j = 0;
                List<String> repositoryPaths = new ArrayList<String>();
                while ( j < spec.getChildCount())
                {
                  SpecificationNode sn = spec.getChild(j++);
                  if (sn.getType().equals("startpoint"))
                  {
                    if (sn.getAttributeValue("path").length() > 0) {
                      repositoryPaths.add(session.getFileSystem().getUri().resolve(sn.getAttributeValue("path")).toString());
                    }
                  }
                }

                /*
                 * get filepathtouri value
                 */
                boolean filePathToUri = false;
                if (version.length() > 0 && version.startsWith("+")) {
                  filePathToUri = true;
                }

                long startTime = System.currentTimeMillis();
                String errorCode = "OK";
                String errorDesc = null;
                Long fileLength = null;
                String entityDescription = documentIdentifier;
                try
                {
                  // Ingest the document.
                  try
                  {
                    FSDataInputStream is = session.getFileSystem().open(path);
                    try
                    {
                      long fileBytes = fileStatus.getLen();
                      RepositoryDocument data = new RepositoryDocument();
                      data.setBinary(is,fileBytes);
                      String fileName = path.getName();
                      data.setFileName(fileName);
                      data.setMimeType(mapExtensionToMimeType(fileName));
                      data.setModifiedDate(new Date(fileStatus.getModificationTime()));
                      if (filePathToUri) {
                        data.addField("uri",convertToURI(documentIdentifier,repositoryPaths.toArray(new String[0])));
                        // MHL for other metadata
                        activities.ingestDocument(documentIdentifier,version,convertToURI(documentIdentifier,repositoryPaths.toArray(new String[0])),data);
                      } else {
                        data.addField("uri",path.toString());
                        // MHL for other metadata
                        activities.ingestDocument(documentIdentifier,version,convertToURI(documentIdentifier),data);
                      }
                      fileLength = new Long(fileBytes);
                    }
                    finally
                    {
                      is.close();
                    }
                  }
                  catch (IOException e)
                  {
                    errorCode = "IO ERROR";
                    errorDesc = e.getMessage();
                    throw new ManifoldCFException("IO Error: "+e.getMessage(),e);
                  }
                }
                finally
                {
                  activities.recordActivity(new Long(startTime),ACTIVITY_READ,fileLength,entityDescription,errorCode,errorDesc,null);
                }
              }
            }
          }
        }
        i++;
      }
    }
    catch(IOException e)
    {
      throw new ManifoldCFException(e);
    }
  }

  // UI support methods.
  //
  // These support methods come in two varieties.  The first bunch is involved in setting up connection configuration information.  The second bunch
  // is involved in presenting and editing document specification information for a job.  The two kinds of methods are accordingly treated differently,
  // in that the first bunch cannot assume that the current connector object is connected, while the second bunch can.  That is why the first bunch
  // receives a thread context argument for all UI methods, while the second bunch does not need one (since it has already been applied via the connect()
  // method, above).
    
  /** Output the configuration header section.
  * This method is called in the head section of the connector's configuration page.  Its purpose is to add the required tabs to the list, and to output any
  * javascript methods that might be needed by the configuration editing HTML.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters, List<String> tabsArray) throws ManifoldCFException, IOException
  {
    tabsArray.add(Messages.getString(locale,"HDFSRepositoryConnector.ServerTabName"));
    
    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function checkConfigForSave()\n"+
"{\n"+
"  return true;\n"+
"}\n"+
"\n"+
"//-->\n"+
"</script>\n"
    );
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
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException
  {
    String nameNode = parameters.getParameter("namenode");
    if (nameNode == null) {
    	nameNode = "hdfs://localhost:9000";
    }
    String user = parameters.getParameter("user");
    if (user == null) {
    	user = "";
    }
    
    if (tabName.equals(Messages.getString(locale,"HDFSRepositoryConnector.ServerTabName")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.NameNode") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"namenode\" type=\"text\" size=\"48\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(nameNode)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.User") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"user\" type=\"text\" size=\"48\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(user)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Server tab hiddens
      out.print(
"<input type=\"hidden\" name=\"namenode\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(nameNode)+"\"/>\n"+
"<input type=\"hidden\" name=\"user\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(user)+"\"/>\n"
      );
    }
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
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext, ConfigParams parameters)
    throws ManifoldCFException
  {
    String nameNode = variableContext.getParameter("namenode");
    if (nameNode != null) {
      parameters.setParameter("namenode", nameNode);
    }

    String user = variableContext.getParameter("user");
    if (user != null) {
      parameters.setParameter("user", user);
    }

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
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters)
    throws ManifoldCFException, IOException
  {
    String nameNode = parameters.getParameter("namenode");
    if (nameNode == null) {
      nameNode = "hdfs://localhost:9000";
    }
    
    String user = parameters.getParameter("user");
    if (user == null) {
      user = "user";
    }
    
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.NameNode") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(nameNode)+"</td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.User") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(user)+"</td>\n"+
"  </tr>\n"+
"</table>\n"
    );
  }
  
  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected a repository connection of the current type.  Its purpose is to add the required tabs
  * to the list, and to output any javascript methods that might be needed by the job editing HTML.
  *@param out is the output to which any HTML should be sent.
  *@param ds is the current document specification for this job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, DocumentSpecification ds, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    tabsArray.add(Messages.getString(locale,"HDFSRepositoryConnector.Paths"));
    tabsArray.add(Messages.getString(locale,"HDFSRepositoryConnector.FilePathToURITab"));

    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function checkSpecification()\n"+
"{\n"+
"  // Does nothing right now.\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function SpecOp(n, opValue, anchorvalue)\n"+
"{\n"+
"  eval(\"editjob.\"+n+\".value = \\\"\"+opValue+\"\\\"\");\n"+
"  postFormSetAnchor(anchorvalue);\n"+
"}\n"+
"//-->\n"+
"</script>\n"
    );
  }
  
  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected a repository connection of the current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editjob".
  *@param out is the output to which any HTML should be sent.
  *@param ds is the current document specification for this job.
  *@param tabName is the current tab name.
  */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, DocumentSpecification ds, String tabName)
    throws ManifoldCFException, IOException
  {
    int i;
    int k;

    // Paths tab
    if (tabName.equals(Messages.getString(locale,"HDFSRepositoryConnector.Paths")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"3\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.Paths2") + "</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.RootPath") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.Rules") + "</nobr></td>\n"+
"        </tr>\n"
      );
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("startpoint"))
        {
          String pathDescription = "_"+Integer.toString(k);
          String pathOpName = "specop"+pathDescription;
          out.print(
"        <tr class=\""+(((k % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <input type=\"hidden\" name=\""+pathOpName+"\" value=\"\"/>\n"+
"            <input type=\"hidden\" name=\""+"specpath"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(sn.getAttributeValue("path"))+"\"/>\n"+
"            <a name=\""+"path_"+Integer.toString(k)+"\">\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"HDFSRepositoryConnector.Delete") + "\" onClick='Javascript:SpecOp(\""+pathOpName+"\",\"Delete\",\"path_"+Integer.toString(k)+"\")' alt=\""+Messages.getAttributeString(locale,"HDFSRepositoryConnector.DeletePath")+Integer.toString(k)+"\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(sn.getAttributeValue("path"))+" \n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"boxcell\">\n"+
"            <input type=\"hidden\" name=\""+"specchildcount"+pathDescription+"\" value=\""+Integer.toString(sn.getChildCount())+"\"/>\n"+
"            <table class=\"formtable\">\n"+
"              <tr class=\"formheaderrow\">\n"+
"                <td class=\"formcolumnheader\"></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.IncludeExclude") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.FileDirectory") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.Match") + "</nobr></td>\n"+
"              </tr>\n"
          );
          int j = 0;
          while (j < sn.getChildCount())
          {
            SpecificationNode excludeNode = sn.getChild(j);
            String instanceDescription = "_"+Integer.toString(k)+"_"+Integer.toString(j);
            String instanceOpName = "specop" + instanceDescription;

            String nodeFlavor = excludeNode.getType();
            String nodeType = excludeNode.getAttributeValue("type");
            String nodeMatch = excludeNode.getAttributeValue("match");
            out.print(
"              <tr class=\"evenformrow\">\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"HDFSRepositoryConnector.InsertHere") + "\" onClick='Javascript:SpecOp(\"specop"+instanceDescription+"\",\"Insert Here\",\"match_"+Integer.toString(k)+"_"+Integer.toString(j+1)+"\")' alt=\""+Messages.getAttributeString(locale,"HDFSRepositoryConnector.InsertNewMatchForPath")+Integer.toString(k)+" before position #"+Integer.toString(j)+"\"/>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <select name=\""+"specflavor"+instanceDescription+"\">\n"+
"                      <option value=\"include\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.include") + "</option>\n"+
"                      <option value=\"exclude\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.exclude") + "</option>\n"+
"                    </select>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <select name=\""+"spectype"+instanceDescription+"\">\n"+
"                      <option value=\"file\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.File") + "</option>\n"+
"                      <option value=\"directory\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.Directory") + "</option>\n"+
"                    </select>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <input type=\"text\" size=\"10\" name=\""+"specmatch"+instanceDescription+"\" value=\"\"/>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"              </tr>\n"+
"              <tr class=\"oddformrow\">\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <input type=\"hidden\" name=\""+"specop"+instanceDescription+"\" value=\"\"/>\n"+
"                    <input type=\"hidden\" name=\""+"specfl"+instanceDescription+"\" value=\""+nodeFlavor+"\"/>\n"+
"                    <input type=\"hidden\" name=\""+"specty"+instanceDescription+"\" value=\""+nodeType+"\"/>\n"+
"                    <input type=\"hidden\" name=\""+"specma"+instanceDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(nodeMatch)+"\"/>\n"+
"                    <a name=\""+"match_"+Integer.toString(k)+"_"+Integer.toString(j)+"\">\n"+
"                      <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"HDFSRepositoryConnector.Delete") + "\" onClick='Javascript:SpecOp(\"specop"+instanceDescription+"\",\"Delete\",\"match_"+Integer.toString(k)+"_"+Integer.toString(j)+"\")' alt=\""+Messages.getAttributeString(locale,"HDFSRepositoryConnector.DeletePath")+Integer.toString(k)+", match spec #"+Integer.toString(j)+"\"/>\n"+
"                    </a>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    "+nodeFlavor+"\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    "+nodeType+"\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(nodeMatch)+"\n"+
"                  </nobr>\n"+
"                </td>\n"+
"              </tr>\n"
            );
            j++;
          }
          if (j == 0)
          {
            out.print(
"              <tr class=\"formrow\"><td class=\"message\" colspan=\"4\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.NoRulesDefined") + "</td></tr>\n"
            );
          }
          out.print(
"              <tr class=\"formrow\"><td class=\"lightseparator\" colspan=\"4\"><hr/></td></tr>\n"+
"              <tr class=\"formrow\">\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <a name=\""+"match_"+Integer.toString(k)+"_"+Integer.toString(j)+"\">\n"+
"                    <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"HDFSRepositoryConnector.Add") + "\" onClick='Javascript:SpecOp(\""+pathOpName+"\",\"Add\",\"match_"+Integer.toString(k)+"_"+Integer.toString(j+1)+"\")' alt=\""+Messages.getAttributeString(locale,"HDFSRepositoryConnector.AddNewMatchForPath")+Integer.toString(k)+"\"/>\n"+
"                  </a>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <select name=\""+"specflavor"+pathDescription+"\">\n"+
"                      <option value=\"include\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.include") + "</option>\n"+
"                      <option value=\"exclude\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.exclude") + "</option>\n"+
"                    </select>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <select name=\""+"spectype"+pathDescription+"\">\n"+
"                      <option value=\"file\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.File") + "</option>\n"+
"                      <option value=\"directory\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.Directory") + "</option>\n"+
"                    </select>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <input type=\"text\" size=\"10\" name=\""+"specmatch"+pathDescription+"\" value=\"\"/>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"              </tr>\n"+
"            </table>\n"+
"          </td>\n"+
"        </tr>\n"
          );
          k++;
        }
      }
      if (k == 0)
      {
        out.print(
"        <tr class=\"formrow\"><td class=\"message\" colspan=\"3\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.NoDocumentsSpecified") + "</td></tr>\n"
        );
      }
      out.print(
"        <tr class=\"formrow\"><td class=\"lightseparator\" colspan=\"3\"><hr/></td></tr>\n"+
"        <tr class=\"formrow\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <a name=\""+"path_"+Integer.toString(k)+"\">\n"+
"                <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"HDFSRepositoryConnector.Add") + "\" onClick='Javascript:SpecOp(\"specop\",\"Add\",\"path_"+Integer.toString(i+1)+"\")' alt=\"" + Messages.getAttributeString(locale,"HDFSRepositoryConnector.AddNewPath") + "\"/>\n"+
"                <input type=\"hidden\" name=\"pathcount\" value=\""+Integer.toString(k)+"\"/>\n"+
"                <input type=\"hidden\" name=\"specop\" value=\"\"/>\n"+
"              </a>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"text\" size=\"80\" name=\"specpath\" value=\"\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"          </td>\n"+
"        </tr>\n"+
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("startpoint"))
        {
          String pathDescription = "_"+Integer.toString(k);
          out.print(
"<input type=\"hidden\" name=\"specpath"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(sn.getAttributeValue("path"))+"\"/>\n"+
"<input type=\"hidden\" name=\"specchildcount"+pathDescription+"\" value=\""+Integer.toString(sn.getChildCount())+"\"/>\n"
          );

          int j = 0;
	  while (j < sn.getChildCount())
	  {
            SpecificationNode excludeNode = sn.getChild(j);
            String instanceDescription = "_"+Integer.toString(k)+"_"+Integer.toString(j);

            String nodeFlavor = excludeNode.getType();
            String nodeType = excludeNode.getAttributeValue("type");
            String nodeMatch = excludeNode.getAttributeValue("match");
            out.print(
"<input type=\"hidden\" name=\"specfl"+instanceDescription+"\" value=\""+nodeFlavor+"\"/>\n"+
"<input type=\"hidden\" name=\"specty"+instanceDescription+"\" value=\""+nodeType+"\"/>\n"+
"<input type=\"hidden\" name=\"specma"+instanceDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(nodeMatch)+"\"/>\n"
            );
            j++;
          }
          k++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\"pathcount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
    }
    
    
    /*
     * get filepathtouri value
     */
    boolean filePathToUri = false;
    i = 0;
    while (i < ds.getChildCount()) {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("filepathtouri")) {
        filePathToUri = Boolean.valueOf(sn.getValue());
      }
    }	    

    /*
     * File path to URI tab
     */
    if (tabName.equals(Messages.getString(locale,"HDFSRepositoryConnector.FilePathToURITab"))) {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.FilePathToURI") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"filepathtouri\" type=\"checkbox\" value=\"true\"" + (filePathToUri ? "checked" : "") +"/>&nbsp;" + Messages.getBodyString(locale,"HDFSRepositoryConnector.FilePathToURIExample") + "\n" +
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    } else {
      /*
       * File path to URI tab hiddens
       */
      out.print(
"<input type=\"hidden\" name=\"filepathtouri\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(Boolean.toString(filePathToUri)) + "\"/>\n"
      );
    }

  }
  
  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the document specification accordingly.
  * The name of the posted form is "editjob".
  *@param variableContext contains the post data, including binary file-upload information.
  *@param ds is the current document specification for this job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
  */
  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, DocumentSpecification ds)
    throws ManifoldCFException
  {
    String x = variableContext.getParameter("pathcount");
    if (x != null)
    {
      ds.clearChildren();
      // Find out how many children were sent
      int pathCount = Integer.parseInt(x);
      // Gather up these
      int i = 0;
      int k = 0;
      while (i < pathCount)
      {
        String pathDescription = "_"+Integer.toString(i);
        String pathOpName = "specop"+pathDescription;
        x = variableContext.getParameter(pathOpName);
        if (x != null && x.equals("Delete"))
        {
          // Skip to the next
          i++;
          continue;
        }
        // Path inserts won't happen until the very end
        String path = variableContext.getParameter("specpath"+pathDescription);
        SpecificationNode node = new SpecificationNode("startpoint");
        node.setAttribute("path",path);
        // Now, get the number of children
        String y = variableContext.getParameter("specchildcount"+pathDescription);
        int childCount = Integer.parseInt(y);
        int j = 0;
        int w = 0;
        while (j < childCount)
        {
          String instanceDescription = "_"+Integer.toString(i)+"_"+Integer.toString(j);
          // Look for an insert or a delete at this point
          String instanceOp = "specop"+instanceDescription;
          String z = variableContext.getParameter(instanceOp);
          String flavor;
          String type;
          String match;
          SpecificationNode sn;
          if (z != null && z.equals("Delete"))
          {
            // Process the deletion as we gather
            j++;
            continue;
          }
          if (z != null && z.equals("Insert Here"))
          {
            // Process the insertion as we gather.
            flavor = variableContext.getParameter("specflavor"+instanceDescription);
            type = variableContext.getParameter("spectype"+instanceDescription);
            match = variableContext.getParameter("specmatch"+instanceDescription);
            sn = new SpecificationNode(flavor);
            sn.setAttribute("type",type);
            sn.setAttribute("match",match);
            node.addChild(w++,sn);
          }
          flavor = variableContext.getParameter("specfl"+instanceDescription);
          type = variableContext.getParameter("specty"+instanceDescription);
          match = variableContext.getParameter("specma"+instanceDescription);
          sn = new SpecificationNode(flavor);
          sn.setAttribute("type",type);
          sn.setAttribute("match",match);
          node.addChild(w++,sn);
          j++;
        }
        if (x != null && x.equals("Add"))
        {
          // Process adds to the end of the rules in-line
          String match = variableContext.getParameter("specmatch"+pathDescription);
          String type = variableContext.getParameter("spectype"+pathDescription);
          String flavor = variableContext.getParameter("specflavor"+pathDescription);
          SpecificationNode sn = new SpecificationNode(flavor);
          sn.setAttribute("type",type);
          sn.setAttribute("match",match);
          node.addChild(w,sn);
        }
        ds.addChild(k++,node);
        i++;
      }

      // See if there's a global add operation
      String op = variableContext.getParameter("specop");
      if (op != null && op.equals("Add"))
      {
        String path = variableContext.getParameter("specpath");
        SpecificationNode node = new SpecificationNode("startpoint");
        node.setAttribute("path",path);
        
        // Now add in the defaults; these will be "include all directories" and "include all files".
        SpecificationNode sn = new SpecificationNode("include");
        sn.setAttribute("type","file");
        sn.setAttribute("match","*");
        node.addChild(node.getChildCount(),sn);
        sn = new SpecificationNode("include");
        sn.setAttribute("type","directory");
        sn.setAttribute("match","*");
        node.addChild(node.getChildCount(),sn);

        ds.addChild(k,node);
      }
    }
    
    /*
     * "filepathtouri"
     */
    String filepathtouri = variableContext.getParameter("filepathtouri");
    if (filepathtouri != null) {
      SpecificationNode sn;
      int i = 0;
      while (i < ds.getChildCount()) {
        if (ds.getChild(i).getType().equals("filepathtouri")) {
          ds.removeChild(i);
        } else {
          i++;
        }
      }
      sn = new SpecificationNode("filepathtouri");
      sn.setValue(filepathtouri);
      ds.addChild(ds.getChildCount(),sn);
    }
    
    return null;
  }
  
  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the document specification information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param out is the output to which any HTML should be sent.
  *@param ds is the current document specification for this job.
  */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, DocumentSpecification ds)
    throws ManifoldCFException, IOException
  {
    int i = 0;
    
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"  <td colspan=\"2\" class=\"message\">" + Messages.getAttributeString(locale,"HDFSRepositoryConnector.Paths") + "</td>\n"+
"  </tr>\n"
    );

    i = 0;
    boolean seenAny = false;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("startpoint"))
      {
        if (seenAny == false)
        {
          seenAny = true;
        }
        out.print(
"  <tr>\n"+
"    <td class=\"description\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(sn.getAttributeValue("path"))+":"+"</td>\n"+
"    <td class=\"value\">\n"
        );
        int j = 0;
        while (j < sn.getChildCount())
        {
          SpecificationNode excludeNode = sn.getChild(j++);
          out.print(
"      "+(excludeNode.getType().equals("include")?"Include ":"")+"\n"+
"      "+(excludeNode.getType().equals("exclude")?"Exclude ":"")+"\n"+
"      "+(excludeNode.getAttributeValue("type").equals("file")?"file ":"")+"\n"+
"      "+(excludeNode.getAttributeValue("type").equals("directory")?"directory ":"")+"\n"+
"      "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(excludeNode.getAttributeValue("match"))+"<br/>\n"
          );
        }
        out.print(
"    </td>\n"+
"  </tr>\n"
        );
      }
    }
    if (seenAny == false)
    {
      out.print(
"  <tr><td class=\"message\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.NoDocumentsSpecified") + "</td></tr>\n"
      );
    }
    out.print(
"</table>\n"
    );
    
    /*
     * get filepathtouri value
     */
    boolean filePathToUri = false;
    i = 0;
    while (i < ds.getChildCount()) {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("filepathtouri")) {
        filePathToUri = Boolean.valueOf(sn.getValue());
      }
    }

    out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.FilePathToURI") + "</nobr></td>\n"+
"    <td class=\"value\">" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(Boolean.toString(filePathToUri)) + "</td>\n"+
"  </tr>\n"+
"</table>\n"
    );
  
  }

  // Protected static methods

  /** Convert a document identifier to a URI.  The URI is the URI that will be the unique key from
  * the search index, and will be presented to the user as part of the search results.
  *@param filePath is the document filePath.
  *@param repositoryPath is the document repositoryPath.
  *@return the document uri.
  */
  protected String convertToURI(String documentIdentifier, String[] repositoryPaths)
    throws ManifoldCFException
  {
    //
    // Note well:  This MUST be a legal URI!!!
    try
    {
      String path = new Path(documentIdentifier).toString();
      for (String repositoryPath : repositoryPaths) {
        if (path.startsWith(repositoryPath)) {
          StringBuffer sb = new StringBuffer();
          path = path.replaceFirst(repositoryPath, "");
          if (path.startsWith("/")) {
            path = path.replaceFirst("/", "");
          }
          String[] tmp = path.split("/", 3);
          String scheme = "";
          String host = "";
          String other = "";
          try {
            scheme = tmp[0];
          } catch (ArrayIndexOutOfBoundsException e) {
            scheme = "hdfs";
          }
          try {
            host = tmp[1];
          } catch (ArrayIndexOutOfBoundsException e) {
            host = "localhost:9000";
          }
          try {
            other = "/" + tmp[2];
          } catch (ArrayIndexOutOfBoundsException e) {
            other = "/";
          }
          return new URI(scheme + "://" + host + other).toURL().toString();
        }
      }
      return convertToURI(documentIdentifier);
    }
    catch (URISyntaxException e)
    {
      throw new ManifoldCFException("Bad url",e);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("Bad url",e);
    }
  }

/** Convert a document identifier to a URI.  The URI is the URI that will be the unique key from
  * the search index, and will be presented to the user as part of the search results.
  *@param documentIdentifier is the document identifier.
  *@return the document uri.
  */
  protected String convertToURI(String documentIdentifier)
    throws ManifoldCFException
  {
    //
    // Note well:  This MUST be a legal URI!!!
    return new Path(documentIdentifier).toUri().toString();
  }

/** Map an extension to a mime type */
  protected static String mapExtensionToMimeType(String fileName)
  {
    int slashIndex = fileName.lastIndexOf("/");
    if (slashIndex != -1)
      fileName = fileName.substring(slashIndex+1);
    int dotIndex = fileName.lastIndexOf(".");
    if (dotIndex == -1)
      return null;
    return ExtensionMimeMap.mapToMimeType(fileName.substring(dotIndex+1).toLowerCase(java.util.Locale.ROOT));
  }

/** Check if a file or directory should be included, given a document specification.
  *@param fileName is the canonical file name.
  *@param documentSpecification is the specification.
  *@return true if it should be included.
  */
  protected static boolean checkInclude(String nameNode, FileStatus fileStatus, String fileName, DocumentSpecification documentSpecification)
    throws ManifoldCFException
  {
    /*
     * TODO:
     * fileName = hdfs://localhost:9000/user/minoru/KEN_ALL_UTF-8_UNIX_SHRINK.CSV
     * pathPart = hdfs://localhost:9000/user/minoru
     * fliePart = KEN_ALL_UTF-8_UNIX_SHRINK.CSV
     * path = /user/minoru => hdfs://localhost:9000/user/minoru
     */
    if (Logging.connectors.isDebugEnabled())
    {
      Logging.connectors.debug("Checking whether to include file '"+fileName+"'");
    }

    String pathPart;
    String filePart;
    if (fileStatus.isDir())
    {
      pathPart = fileName;
      filePart = null;
    }
    else
    {
      pathPart = fileStatus.getPath().getParent().toString();
      filePart = fileStatus.getPath().getName();
    }

    // Scan until we match a startpoint
    int i = 0;
    while (i < documentSpecification.getChildCount())
    {
      SpecificationNode sn = documentSpecification.getChild(i++);
      if (sn.getType().equals("startpoint"))
      {
        String path = null;
        try {
			path = new URI(nameNode).resolve(sn.getAttributeValue("path")).toString();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
        if (Logging.connectors.isDebugEnabled())
        {
          Logging.connectors.debug("Checking path '"+path+"' against canonical '"+pathPart+"'");
        }
        // Compare with filename
        int matchEnd = matchSubPath(path,pathPart);
        if (matchEnd == -1)
        {
          if (Logging.connectors.isDebugEnabled())
          {
            Logging.connectors.debug("Match check '"+path+"' against canonical '"+pathPart+"' failed");
          }

          continue;
        }
        // matchEnd is the start of the rest of the path (after the match) in fileName.
        // We need to walk through the rules and see whether it's in or out.
        int j = 0;
        while (j < sn.getChildCount())
        {
          SpecificationNode node = sn.getChild(j++);
          String flavor = node.getType();
          String match = node.getAttributeValue("match");
          String type = node.getAttributeValue("type");
          // If type is "file", then our match string is against the filePart.
          // If filePart is null, then this rule is simply skipped.
          String sourceMatch;
          int sourceIndex;
          if (type.equals("file"))
          {
            if (filePart == null)
            {
              continue;
            }
            sourceMatch = filePart;
            sourceIndex = 0;
          }
          else
          {
            if (filePart != null)
            {
              continue;
            }
            sourceMatch = pathPart;
            sourceIndex = matchEnd;
          }

          if (flavor.equals("include"))
          {
            if (checkMatch(sourceMatch,sourceIndex,match))
            {
              return true;
            }
          }
          else if (flavor.equals("exclude"))
          {
            if (checkMatch(sourceMatch,sourceIndex,match))
            {
              return false;
            }
          }
        }
      }
    }
    if (Logging.connectors.isDebugEnabled())
    {
      Logging.connectors.debug("Not including '"+fileName+"' because no matching rules");
    }

    return false;
  }

  /** Check if a file should be ingested, given a document specification.  It is presumed that
  * documents that do not pass checkInclude() will be checked with this method.
  *@param file is the file.
  *@param documentSpecification is the specification.
  */
  protected static boolean checkIngest(String nameNode, FileStatus fileStatus, DocumentSpecification documentSpecification)
    throws ManifoldCFException
  {
    // Since the only exclusions at this point are not based on file contents, this is a no-op.
    // MHL
    return true;
  }

  /** Match a sub-path.  The sub-path must match the complete starting part of the full path, in a path
  * sense.  The returned value should point into the file name beyond the end of the matched path, or
  * be -1 if there is no match.
  *@param subPath is the sub path.
  *@param fullPath is the full path.
  *@return the index of the start of the remaining part of the full path, or -1.
  */
  protected static int matchSubPath(String subPath, String fullPath)
  {
    if (subPath.length() > fullPath.length())
      return -1;
    if (fullPath.startsWith(subPath) == false)
      return -1;
    int rval = subPath.length();
    if (fullPath.length() == rval)
      return rval;
    char x = fullPath.charAt(rval);
    if (x == Path.SEPARATOR_CHAR)
      rval++;
    return rval;
  }

  /** Check a match between two strings with wildcards.
  *@param sourceMatch is the expanded string (no wildcards)
  *@param sourceIndex is the starting point in the expanded string.
  *@param match is the wildcard-based string.
  *@return true if there is a match.
  */
  protected static boolean checkMatch(String sourceMatch, int sourceIndex, String match)
  {
    // Note: The java regex stuff looks pretty heavyweight for this purpose.
    // I've opted to try and do a simple recursive version myself, which is not compiled.
    // Basically, the match proceeds by recursive descent through the string, so that all *'s cause
    // recursion.
    boolean caseSensitive = true;

    return processCheck(caseSensitive, sourceMatch, sourceIndex, match, 0);
  }

  /** Recursive worker method for checkMatch.  Returns 'true' if there is a path that consumes both
  * strings in their entirety in a matched way.
  *@param caseSensitive is true if file names are case sensitive.
  *@param sourceMatch is the source string (w/o wildcards)
  *@param sourceIndex is the current point in the source string.
  *@param match is the match string (w/wildcards)
  *@param matchIndex is the current point in the match string.
  *@return true if there is a match.
  */
  protected static boolean processCheck(boolean caseSensitive, String sourceMatch, int sourceIndex,
    String match, int matchIndex)
  {
    // Logging.connectors.debug("Matching '"+sourceMatch+"' position "+Integer.toString(sourceIndex)+
    //      " against '"+match+"' position "+Integer.toString(matchIndex));

    // Match up through the next * we encounter
    while (true)
    {
      // If we've reached the end, it's a match.
      if (sourceMatch.length() == sourceIndex && match.length() == matchIndex)
        return true;
      // If one has reached the end but the other hasn't, no match
      if (match.length() == matchIndex)
        return false;
      if (sourceMatch.length() == sourceIndex)
      {
        if (match.charAt(matchIndex) != '*')
          return false;
        matchIndex++;
        continue;
      }
      char x = sourceMatch.charAt(sourceIndex);
      char y = match.charAt(matchIndex);
      if (!caseSensitive)
      {
        if (x >= 'A' && x <= 'Z')
          x -= 'A'-'a';
        if (y >= 'A' && y <= 'Z')
          y -= 'A'-'a';
      }
      if (y == '*')
      {
        // Wildcard!
        // We will recurse at this point.
        // Basically, we want to combine the results for leaving the "*" in the match string
        // at this point and advancing the source index, with skipping the "*" and leaving the source
        // string alone.
        return processCheck(caseSensitive,sourceMatch,sourceIndex+1,match,matchIndex) ||
          processCheck(caseSensitive,sourceMatch,sourceIndex,match,matchIndex+1);
      }
      if (y == '?' || x == y)
      {
        sourceIndex++;
        matchIndex++;
      }
      else
        return false;
    }
  }

  /**
   * @param e
   * @throws ManifoldCFException
   * @throws ServiceInterruption
   */
  private static void handleIOException(IOException e) throws ManifoldCFException, ServiceInterruption {
    if (!(e instanceof java.net.SocketTimeoutException) && (e instanceof InterruptedIOException)) {
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
    }
    long currentTime = System.currentTimeMillis();
    throw new ServiceInterruption("IO exception: "+e.getMessage(), e, currentTime + 300000L, currentTime + 3 * 60 * 60000L,-1,false);
  }
  
  /**
   * @param e
   * @throws ManifoldCFException
   * @throws ServiceInterruption
   */
  private static void handleGeneralSecurityException(GeneralSecurityException e) throws ManifoldCFException, ServiceInterruption {
    // Permanent problem: can't initialize transport layer
    throw new ManifoldCFException("HDFS exception: "+e.getMessage(), e);
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

  protected class GetSessionThread extends Thread {
    protected Throwable exception = null;

    public GetSessionThread() {
      super();
      setDaemon(true);
    }

    public void run() {
      try {
        // Create a session
        session = new HDFSSession(nameNode, config, user);
      } catch (Throwable e) {
        this.exception = e;
      }
    }

    public Throwable getException() {
      return exception;
    }
  }

  protected class GetSeedsThread extends Thread {
    protected Throwable exception = null;
    protected final String path;
    protected final XThreadStringBuffer seedBuffer;

    public GetSeedsThread(String path) {
      super();
      this.path = path;
      this.seedBuffer = new XThreadStringBuffer();
      setDaemon(true);
    }

    @Override
    public void run() {
      try {
        session.getSeeds(seedBuffer, path);
        seedBuffer.signalDone();
      } catch (Throwable e) {
        this.exception = e;
      }
    }

    public XThreadStringBuffer getBuffer() {
      return seedBuffer;
    }

    public void finishUp() throws InterruptedException {
      seedBuffer.abandon();
      join();
      Throwable thr = exception;
      if (thr != null) {
        if (thr instanceof RuntimeException) {
          throw (RuntimeException) thr;
        } else if (thr instanceof Error) {
          throw (Error) thr;
        } else {
          throw new RuntimeException("Unhandled exception of type: "+thr.getClass().getName(),thr);
        }
      }
    }
  }

  protected class GetObjectThread extends Thread {
    protected final String nodeId;
    protected Throwable exception = null;
    protected Path response = null;

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

    public void finishUp() throws InterruptedException {
      join();
      Throwable thr = exception;
      if (thr != null) {
        if (thr instanceof RuntimeException) {
          throw (RuntimeException) thr;
        } else if (thr instanceof Error) {
          throw (Error) thr;
        } else {
          throw new RuntimeException("Unhandled exception of type: "+thr.getClass().getName(),thr);
        }
      }
    }

    public Path getResponse() {
      return response;
    }

    public Throwable getException() {
      return exception;
    }
  }

  protected class BackgroundStreamThread extends Thread
  {
    protected final String nodeId;
    
    protected boolean abortThread = false;
    protected Throwable responseException = null;
    protected InputStream sourceStream = null;
    protected XThreadInputStream threadStream = null;
    
    public BackgroundStreamThread(String nodeId)
    {
      super();
      setDaemon(true);
      this.nodeId = nodeId;
    }

    public void run()
    {
      try {
        try {
          synchronized (this) {
            if (!abortThread) {
              sourceStream = session.getFSDataInputStream(nodeId);
              threadStream = new XThreadInputStream(sourceStream);
              this.notifyAll();
            }
          }
          
          if (threadStream != null)
          {
            // Stuff the content until we are done
            threadStream.stuffQueue();
          }
        } finally {
          if (sourceStream != null) {
            sourceStream.close();
          }
        }
      } catch (Throwable e) {
        responseException = e;
      }
    }

    public InputStream getSafeInputStream() throws InterruptedException, IOException
    {
      // Must wait until stream is created, or until we note an exception was thrown.
      while (true)
      {
        synchronized (this)
        {
          if (responseException != null) {
            throw new IllegalStateException("Check for response before getting stream");
          }
          checkException(responseException);
          if (threadStream != null) {
            return threadStream;
          }
          wait();
        }
      }
    }
    
    public void finishUp() throws InterruptedException, IOException
    {
      // This will be called during the finally
      // block in the case where all is well (and
      // the stream completed) and in the case where
      // there were exceptions.
      synchronized (this) {
        if (threadStream != null) {
          threadStream.abort();
        }
        abortThread = true;
      }

      join();

      checkException(responseException);
    }
    
    protected synchronized void checkException(Throwable exception) throws IOException
    {
      if (exception != null)
      {
        Throwable e = exception;
        if (e instanceof IOException) {
          throw (IOException)e;
        } else if (e instanceof RuntimeException) {
          throw (RuntimeException)e;
        } else if (e instanceof Error) {
          throw (Error)e;
        } else {
          throw new RuntimeException("Unhandled exception of type: "+e.getClass().getName(),e);
        }
      }
    }
  }
}
