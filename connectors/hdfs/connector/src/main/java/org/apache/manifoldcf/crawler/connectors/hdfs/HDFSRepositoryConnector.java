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
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.connectors.hdfs.HDFSSession;
import org.apache.manifoldcf.crawler.connectors.hdfs.Messages;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.connectorcommon.common.XThreadInputStream;
import org.apache.manifoldcf.connectorcommon.common.XThreadStringBuffer;
import org.apache.manifoldcf.connectorcommon.extmimemap.ExtensionMimeMap;

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

  protected String nameNodeProtocol = null;
  protected String nameNodeHost = null;
  protected String nameNodePort = null;
  protected String user = null;
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
    return new String[]{nameNodeHost};
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

    nameNodeProtocol = configParams.getParameter("namenodeprotocol");
    if (nameNodeProtocol == null)
      nameNodeProtocol = "hdfs";
    nameNodeHost = configParams.getParameter("namenodehost");
    nameNodePort = configParams.getParameter("namenodeport");
    user = configParams.getParameter("user");
    
  }

  /* (non-Javadoc)
   * @see org.apache.manifoldcf.core.connector.BaseConnector#disconnect()
   */
  @Override
  public void disconnect() throws ManifoldCFException {
    closeSession();
    user = null;
    nameNodeProtocol = null;
    nameNodeHost = null;
    nameNodePort = null;
    super.disconnect();
  }

  /**
   * Set up a session
   */
  protected HDFSSession getSession() throws ManifoldCFException, ServiceInterruption {
    if (session == null) {
      if (StringUtils.isEmpty(nameNodeProtocol)) {
        throw new ManifoldCFException("Parameter namenodeprotocol required but not set");
      }
      if (Logging.connectors.isDebugEnabled()) {
        Logging.connectors.debug("HDFS: NameNodeProtocol = '" + nameNodeProtocol + "'");
      }
      if (StringUtils.isEmpty(nameNodeHost)) {
        throw new ManifoldCFException("Parameter namenodehost required but not set");
      }
      if (Logging.connectors.isDebugEnabled()) {
        Logging.connectors.debug("HDFS: NameNodeHost = '" + nameNodeHost + "'");
      }
      if (StringUtils.isEmpty(nameNodePort)) {
        throw new ManifoldCFException("Parameter namenodeport required but not set");
      }
      if (Logging.connectors.isDebugEnabled()) {
        Logging.connectors.debug("HDFS: NameNodePort = '" + nameNodePort + "'");
      }

      if (StringUtils.isEmpty(user)) {
        throw new ManifoldCFException("Parameter user required but not set");
      }
      if (Logging.connectors.isDebugEnabled()) {
        Logging.connectors.debug("HDFS: User = '" + user + "'");
      }

      String nameNode = nameNodeProtocol+"://"+nameNodeHost+":"+nameNodePort;

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
        Logging.connectors.warn("HDFS: Error closing connection: "+e.getMessage(),e);
        // Eat the exception
      } finally {
        session = null;
        lastSessionFetch = -1L;
      }
    }
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
  *@param lastSeedVersionString is the last seeding version string for this job, or null if the job has no previous seeding version string.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@return an updated seeding version string, to be stored with the job.
  */
  @Override
  public String addSeedDocuments(ISeedingActivity activities, Specification spec,
    String lastSeedVersion, long seedTime, int jobMode)
    throws ManifoldCFException, ServiceInterruption {

    String path = StringUtils.EMPTY;
    int i = 0;
    while (i < spec.getChildCount()) {
      SpecificationNode sn = spec.getChild(i);
      if (sn.getType().equals("startpoint")) {
        path = sn.getAttributeValue("path");
        
        FileStatus fileStatus = getObject(new Path(path));
        if (fileStatus.isDirectory()) {
          activities.addSeedDocument(fileStatus.getPath().toUri().toString());
        }
      }
      i++;
    }
    return "";
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
      
    for (String documentIdentifier : documentIdentifiers) {
      
      String versionString;
      
      FileStatus fileStatus = getObject(new Path(documentIdentifier));
      if (fileStatus != null) {
        
        boolean isDirectory = fileStatus.isDirectory();
        
        if (isDirectory) {
          // If HDFS directory modify dates are transitive, as they are on Unix,
          // then getting the modify date of the current version is sufficient
          // to detect any downstream changes we need to be aware of.
          // (If this turns out to be a bad assumption, this should simply set rval[i] ="").
          long lastModified = fileStatus.getModificationTime();
          versionString = new Long(lastModified).toString();
          
          if (activities.checkDocumentNeedsReindexing(documentIdentifier,versionString)) {
            // Process directory!
            String entityReference = documentIdentifier;
            FileStatus[] fileStatuses = getChildren(fileStatus.getPath());
            if (fileStatuses == null) {
              continue;
            }
            for (int j = 0; j < fileStatuses.length; j++) {
              FileStatus fs = fileStatuses[j++];
              String canonicalPath = fs.getPath().toString();
              if (checkInclude(session.getUri().toString(),fs,canonicalPath,spec)) {
                activities.addDocumentReference(canonicalPath,documentIdentifier,RELATIONSHIP_CHILD);
              }
            }
          }
        } else {
          long lastModified = fileStatus.getModificationTime();
          StringBuilder sb = new StringBuilder();
          // Check if the path is to be converted.  We record that info in the version string so that we'll reindex documents whose
          // URI's change.
          String nameNode = nameNodeProtocol + "://" + nameNodeHost + ":" + nameNodePort;
          String convertPath = findConvertPath(nameNode, spec, fileStatus.getPath());
          if (convertPath != null)
          {
            // Record the path.
            sb.append("+");
            pack(sb,convertPath,'+');
          }
          else
            sb.append("-");
          sb.append(new Long(lastModified).toString());
          versionString = sb.toString();
          // We will record document fetch as an activity
          long startTime = System.currentTimeMillis();
          String errorCode = null;
          String errorDesc = null;
          long fileSize = 0;
          
          if (activities.checkDocumentNeedsReindexing(documentIdentifier,versionString)) {
            // Process file!
            if (!checkIngest(session.getUri().toString(),fileStatus,spec)) {
              activities.noDocument(documentIdentifier,versionString);
              continue;
            }

            // It is a file to be indexed.
            long fileLength = fileStatus.getLen();
            String fileName = fileStatus.getPath().getName();
            String mimeType = mapExtensionToMimeType(fileStatus.getPath().getName());
            Date modifiedDate = new Date(fileStatus.getModificationTime());
            try {
                String uri;
                if (convertPath != null) {
                    uri = convertToWGETURI(convertPath);
                } else {
                    uri = fileStatus.getPath().toUri().toString();
                }
            
                if (!activities.checkLengthIndexable(fileLength))
                {
                    errorCode = activities.EXCLUDED_LENGTH;
                    errorDesc = "Excluding document because of file length ('"+fileLength+"')";
                    activities.noDocument(documentIdentifier,versionString);
                    continue;
                }
            
                if (!activities.checkURLIndexable(uri))
                {
                    errorCode = activities.EXCLUDED_URL;
                    errorDesc = "Excluding document because of URL ('"+uri+"')";
                    activities.noDocument(documentIdentifier,versionString);
                    continue;
                }
            
                if (!activities.checkMimeTypeIndexable(mimeType))
                {
                    errorCode = activities.EXCLUDED_MIMETYPE;
                    errorDesc = "Excluding document because of mime type ("+mimeType+")";
                    activities.noDocument(documentIdentifier,versionString);
                    continue;
                }
            
                if (!activities.checkDateIndexable(modifiedDate))
                {
                    errorCode = activities.EXCLUDED_DATE;
                    errorDesc = "Excluding document because of date ("+modifiedDate+")";
                    activities.noDocument(documentIdentifier,versionString);
                    continue;
                }
            
                // Prepare the metadata part of RepositoryDocument
                RepositoryDocument data = new RepositoryDocument();

                data.setFileName(fileName);
                data.setMimeType(mimeType);
                data.setModifiedDate(modifiedDate);

                data.addField("uri",uri);

            
            
                BackgroundStreamThread t = new BackgroundStreamThread(getSession(),new Path(documentIdentifier));
                try {
                    t.start();
                    boolean wasInterrupted = false;
                    try {
                        InputStream is = t.getSafeInputStream();
                        try {
                            data.setBinary(is, fileSize);
                            activities.ingestDocumentWithException(documentIdentifier,versionString,uri,data);
                        } finally {
                            is.close();
                        }
                    } catch (java.net.SocketTimeoutException e) {
                        throw e;
                    } catch (InterruptedIOException e) {
                        wasInterrupted = true;
                        throw e;
                    } catch (ManifoldCFException e) {
                        if (e.getErrorCode() == ManifoldCFException.INTERRUPTED) {
                            wasInterrupted = true;
                        }
                        throw e;
                    } finally {
                        if (!wasInterrupted) {
                            // This does a join
                            t.finishUp();
                        }
                    }

                    // No errors.  Record the fact that we made it.
                    errorCode = "OK";
                    // Length we did in bytes
                    fileSize = fileStatus.getLen();

                } catch (InterruptedException e) {
                    // We were interrupted out of the join, most likely.  Before we abandon the thread,
                    // send a courtesy interrupt.
                    t.interrupt();
                    throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
                } catch (java.net.SocketTimeoutException e) {
                    errorCode = "IOERROR";
                    errorDesc = e.getMessage();
                    handleIOException(e);
                } catch (InterruptedIOException e) {
                    t.interrupt();
                    throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
                } catch (IOException e) {
                    errorCode = "IOERROR";
                    errorDesc = e.getMessage();
                    handleIOException(e);
                }
            } finally {
                if(errorCode != null){
                    activities.recordActivity(new Long(startTime),ACTIVITY_READ,new Long(fileSize),documentIdentifier,errorCode,errorDesc,null);
                }
            }
          }
        }
      } else {
        activities.deleteDocument(documentIdentifier);
        continue;
      }
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
"  if (editconnection.namenodehost.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"HDFSRepositoryConnector.NameNodeHostCannotBeNull")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"HDFSRepositoryConnector.ServerTabName")+"\");\n"+
"    editconnection.namenodehost.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.namenodeport.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"HDFSRepositoryConnector.NameNodePortCannotBeNull")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"HDFSRepositoryConnector.ServerTabName")+"\");\n"+
"    editconnection.namenodeport.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (!isInteger(editconnection.namenodeport.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"HDFSRepositoryConnector.NameNodePortMustBeAnInteger")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"HDFSRepositoryConnector.ServerTabName")+"\");\n"+
"    editconnection.namenodeport.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.user.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"HDFSRepositoryConnector.UserCannotBeNull")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"HDFSRepositoryConnector.ServerTabName")+"\");\n"+
"    editconnection.user.focus();\n"+
"    return false;\n"+
"  }\n"+
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
    String nameNodeProtocol = parameters.getParameter("namenodeprotocol");
    if (nameNodeProtocol == null) {
      nameNodeProtocol = "hdfs";
    }
    
    String nameNodeHost = parameters.getParameter("namenodehost");
    if (nameNodeHost == null) {
      nameNodeHost = "localhost";
    }
    
    String nameNodePort = parameters.getParameter("namenodeport");
    if (nameNodePort == null) {
      nameNodePort = "9000";
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
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.NameNodeProtocol") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"namenodeprotocol\" size=\"2\">\n"+
"        <option value=\"file\"" + (nameNodeProtocol.equals("file")?" selected=\"true\"":"") + ">file</option>\n"+
"        <option value=\"ftp\"" + (nameNodeProtocol.equals("ftp")?" selected=\"true\"":"") + ">ftp</option>\n"+
"        <option value=\"har\"" + (nameNodeProtocol.equals("har")?" selected=\"true\"":"") + ">har</option>\n"+
"        <option value=\"hdfs\"" + (nameNodeProtocol.equals("hdfs")?" selected=\"true\"":"") + ">hdfs</option>\n"+
"        <option value=\"s3\"" + (nameNodeProtocol.equals("s3")?" selected=\"true\"":"") + ">s3</option>\n"+
"        <option value=\"s3n\"" + (nameNodeProtocol.equals("s3n")?" selected=\"true\"":"") + ">s3n</option>\n"+
"        <option value=\"viewfs\"" + (nameNodeProtocol.equals("viewfs")?" selected=\"true\"":"") + ">viewfs</option>\n"+
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.NameNodeHost") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"namenodehost\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(nameNodeHost)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.NameNodePort") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"namenodeport\" type=\"text\" size=\"5\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(nameNodePort)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.User") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"user\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(user)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Server tab hiddens
      out.print(
"<input type=\"hidden\" name=\"namenodeprotocol\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(nameNodeProtocol)+"\"/>\n"+
"<input type=\"hidden\" name=\"namenodehost\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(nameNodeHost)+"\"/>\n"+
"<input type=\"hidden\" name=\"namenodeport\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(nameNodePort)+"\"/>\n"+
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
    String nameNodeProtocol = variableContext.getParameter("namenodeprotocol");
    if (nameNodeProtocol != null) {
      parameters.setParameter("namenodeprotocol", nameNodeProtocol);
    }
    
    String nameNodeHost = variableContext.getParameter("namenodehost");
    if (nameNodeHost != null) {
      parameters.setParameter("namenodehost", nameNodeHost);
    }

    String nameNodePort = variableContext.getParameter("namenodeport");
    if (nameNodePort != null) {
      parameters.setParameter("namenodeport", nameNodePort);
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
    String nameNodeProtocol = parameters.getParameter("namenodeprotocol");
    if (nameNodeProtocol == null)
      nameNodeProtocol = "hdfs";

    String nameNodeHost = parameters.getParameter("namenodehost");
    String nameNodePort = parameters.getParameter("namenodeport");
    String user = parameters.getParameter("user");
    
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.NameNodeProtocol") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(nameNodeProtocol)+"</td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.NameNodeHost") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(nameNodeHost)+"</td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.NameNodePort") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(nameNodePort)+"</td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.User") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(user)+"</td>\n"+
"  </tr>\n"+
"</table>\n"
    );
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
    throws ManifoldCFException, IOException
  {
    tabsArray.add(Messages.getString(locale,"HDFSRepositoryConnector.Paths"));

    String seqPrefix = "s"+connectionSequenceNumber+"_";

    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"\n"+
"function "+seqPrefix+"SpecOp(n, opValue, anchorvalue)\n"+
"{\n"+
"  eval(\"editjob.\"+n+\".value = \\\"\"+opValue+\"\\\"\");\n"+
"  postFormSetAnchor(anchorvalue);\n"+
"}\n"+
"//-->\n"+
"</script>\n"
    );
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
    throws ManifoldCFException, IOException
  {
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    int i;
    int k;

    // Paths tab
    if (tabName.equals(Messages.getString(locale,"HDFSRepositoryConnector.Paths")) && connectionSequenceNumber == actualSequenceNumber)
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
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.ConvertToURI") + "<br/>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.ConvertToURIExample")+ "</nobr></td>\n"+
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
          String pathOpName = seqPrefix+"specop"+pathDescription;
          
          String path = sn.getAttributeValue("path");
          String convertToURIString = sn.getAttributeValue("converttouri");

          boolean convertToURI = false;
          if (convertToURIString != null && convertToURIString.equals("true"))
            convertToURI = true;

          out.print(
"        <tr class=\""+(((k % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <input type=\"hidden\" name=\""+pathOpName+"\" value=\"\"/>\n"+
"            <input type=\"hidden\" name=\""+seqPrefix+"specpath"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(sn.getAttributeValue("path"))+"\"/>\n"+
"            <a name=\""+seqPrefix+"path_"+Integer.toString(k)+"\">\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"HDFSRepositoryConnector.Delete") + "\" onClick='Javascript:"+seqPrefix+"SpecOp(\""+pathOpName+"\",\"Delete\",\""+seqPrefix+"path_"+Integer.toString(k)+"\")' alt=\""+Messages.getAttributeString(locale,"HDFSRepositoryConnector.DeletePath")+Integer.toString(k)+"\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(path)+" \n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <input type=\"hidden\" name=\""+seqPrefix+"converttouri"+pathDescription+"\" value=\""+(convertToURI?"true":"false")+"\">\n"+
"            <nobr>\n"+
"              "+(convertToURI?Messages.getBodyString(locale,"HDFSRepositoryConnector.Yes"):Messages.getBodyString(locale,"HDFSRepositoryConnector.No"))+" \n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"boxcell\">\n"+
"            <input type=\"hidden\" name=\""+seqPrefix+"specchildcount"+pathDescription+"\" value=\""+Integer.toString(sn.getChildCount())+"\"/>\n"+
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
            String instanceOpName = seqPrefix + "specop" + instanceDescription;

            String nodeFlavor = excludeNode.getType();
            String nodeType = excludeNode.getAttributeValue("type");
            String nodeMatch = excludeNode.getAttributeValue("match");
            out.print(
"              <tr class=\"evenformrow\">\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"HDFSRepositoryConnector.InsertHere") + "\" onClick='Javascript:"+seqPrefix+"SpecOp(\""+seqPrefix+"specop"+instanceDescription+"\",\"Insert Here\",\""+seqPrefix+"match_"+Integer.toString(k)+"_"+Integer.toString(j+1)+"\")' alt=\""+Messages.getAttributeString(locale,"HDFSRepositoryConnector.InsertNewMatchForPath")+Integer.toString(k)+" before position #"+Integer.toString(j)+"\"/>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <select name=\""+seqPrefix+"specflavor"+instanceDescription+"\">\n"+
"                      <option value=\"include\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.include") + "</option>\n"+
"                      <option value=\"exclude\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.exclude") + "</option>\n"+
"                    </select>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <select name=\""+seqPrefix+"spectype"+instanceDescription+"\">\n"+
"                      <option value=\"file\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.File") + "</option>\n"+
"                      <option value=\"directory\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.Directory") + "</option>\n"+
"                    </select>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <input type=\"text\" size=\"10\" name=\""+seqPrefix+"specmatch"+instanceDescription+"\" value=\"\"/>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"              </tr>\n"+
"              <tr class=\"oddformrow\">\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <input type=\"hidden\" name=\""+instanceOpName+"\" value=\"\"/>\n"+
"                    <input type=\"hidden\" name=\""+seqPrefix+"specfl"+instanceDescription+"\" value=\""+nodeFlavor+"\"/>\n"+
"                    <input type=\"hidden\" name=\""+seqPrefix+"specty"+instanceDescription+"\" value=\""+nodeType+"\"/>\n"+
"                    <input type=\"hidden\" name=\""+seqPrefix+"specma"+instanceDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(nodeMatch)+"\"/>\n"+
"                    <a name=\""+seqPrefix+"match_"+Integer.toString(k)+"_"+Integer.toString(j)+"\">\n"+
"                      <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"HDFSRepositoryConnector.Delete") + "\" onClick='Javascript:"+seqPrefix+"SpecOp(\""+instanceOpName+"\",\"Delete\",\""+seqPrefix+"match_"+Integer.toString(k)+"_"+Integer.toString(j)+"\")' alt=\""+Messages.getAttributeString(locale,"HDFSRepositoryConnector.DeletePath")+Integer.toString(k)+", match spec #"+Integer.toString(j)+"\"/>\n"+
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
"              <tr class=\"formrow\"><td class=\"formcolumnmessage\" colspan=\"4\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.NoRulesDefined") + "</td></tr>\n"
            );
          }
          out.print(
"              <tr class=\"formrow\"><td class=\"lightseparator\" colspan=\"4\"><hr/></td></tr>\n"+
"              <tr class=\"formrow\">\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <a name=\""+seqPrefix+"match_"+Integer.toString(k)+"_"+Integer.toString(j)+"\">\n"+
"                    <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"HDFSRepositoryConnector.Add") + "\" onClick='Javascript:"+seqPrefix+"SpecOp(\""+pathOpName+"\",\"Add\",\""+seqPrefix+"match_"+Integer.toString(k)+"_"+Integer.toString(j+1)+"\")' alt=\""+Messages.getAttributeString(locale,"HDFSRepositoryConnector.AddNewMatchForPath")+Integer.toString(k)+"\"/>\n"+
"                  </a>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <select name=\""+seqPrefix+"specflavor"+pathDescription+"\">\n"+
"                      <option value=\"include\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.include") + "</option>\n"+
"                      <option value=\"exclude\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.exclude") + "</option>\n"+
"                    </select>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <select name=\""+seqPrefix+"spectype"+pathDescription+"\">\n"+
"                      <option value=\"file\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.File") + "</option>\n"+
"                      <option value=\"directory\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.Directory") + "</option>\n"+
"                    </select>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <input type=\"text\" size=\"10\" name=\""+seqPrefix+"specmatch"+pathDescription+"\" value=\"\"/>\n"+
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
"        <tr class=\"formrow\"><td class=\"formcolumnmessage\" colspan=\"4\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.NoDocumentsSpecified") + "</td></tr>\n"
        );
      }
      out.print(
"        <tr class=\"formrow\"><td class=\"lightseparator\" colspan=\"4\"><hr/></td></tr>\n"+
"        <tr class=\"formrow\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <a name=\""+seqPrefix+"path_"+Integer.toString(k)+"\">\n"+
"                <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"HDFSRepositoryConnector.Add") + "\" onClick='Javascript:"+seqPrefix+"SpecOp(\""+seqPrefix+"specop\",\"Add\",\""+seqPrefix+"path_"+Integer.toString(i+1)+"\")' alt=\"" + Messages.getAttributeString(locale,"HDFSRepositoryConnector.AddNewPath") + "\"/>\n"+
"                <input type=\"hidden\" name=\""+seqPrefix+"pathcount\" value=\""+Integer.toString(k)+"\"/>\n"+
"                <input type=\"hidden\" name=\""+seqPrefix+"specop\" value=\"\"/>\n"+
"              </a>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"text\" size=\"30\" name=\""+seqPrefix+"specpath\" value=\"\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input name=\""+seqPrefix+"converttouri\" type=\"checkbox\" value=\"true\"/>\n"+
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
          
          String path = sn.getAttributeValue("path");
          String convertToURIString = sn.getAttributeValue("converttouri");

          boolean convertToURI = false;
          if (convertToURIString != null && convertToURIString.equals("true"))
            convertToURI = true;

          out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specpath"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(path)+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"converttouri"+pathDescription+"\" value=\""+(convertToURI?"true":"false")+"\">\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"specchildcount"+pathDescription+"\" value=\""+Integer.toString(sn.getChildCount())+"\"/>\n"
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
"<input type=\"hidden\" name=\""+seqPrefix+"specfl"+instanceDescription+"\" value=\""+nodeFlavor+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"specty"+instanceDescription+"\" value=\""+nodeType+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"specma"+instanceDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(nodeMatch)+"\"/>\n"
            );
            j++;
          }
          k++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"pathcount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
    }
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
    throws ManifoldCFException
  {
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    String x = variableContext.getParameter(seqPrefix+"pathcount");
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
        String pathOpName = seqPrefix+"specop"+pathDescription;
        x = variableContext.getParameter(pathOpName);
        if (x != null && x.equals("Delete"))
        {
          // Skip to the next
          i++;
          continue;
        }
        // Path inserts won't happen until the very end
        String path = variableContext.getParameter(seqPrefix+"specpath"+pathDescription);
        String convertToURI = variableContext.getParameter(seqPrefix+"converttouri"+pathDescription);

        SpecificationNode node = new SpecificationNode("startpoint");
        node.setAttribute("path",path);
        if (convertToURI != null)
          node.setAttribute("converttouri",convertToURI);

        // Now, get the number of children
        String y = variableContext.getParameter(seqPrefix+"specchildcount"+pathDescription);
        int childCount = Integer.parseInt(y);
        int j = 0;
        int w = 0;
        while (j < childCount)
        {
          String instanceDescription = "_"+Integer.toString(i)+"_"+Integer.toString(j);
          // Look for an insert or a delete at this point
          String instanceOp = seqPrefix+"specop"+instanceDescription;
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
            flavor = variableContext.getParameter(seqPrefix+"specflavor"+instanceDescription);
            type = variableContext.getParameter(seqPrefix+"spectype"+instanceDescription);
            match = variableContext.getParameter(seqPrefix+"specmatch"+instanceDescription);
            sn = new SpecificationNode(flavor);
            sn.setAttribute("type",type);
            sn.setAttribute("match",match);
            node.addChild(w++,sn);
          }
          flavor = variableContext.getParameter(seqPrefix+"specfl"+instanceDescription);
          type = variableContext.getParameter(seqPrefix+"specty"+instanceDescription);
          match = variableContext.getParameter(seqPrefix+"specma"+instanceDescription);
          sn = new SpecificationNode(flavor);
          sn.setAttribute("type",type);
          sn.setAttribute("match",match);
          node.addChild(w++,sn);
          j++;
        }
        if (x != null && x.equals("Add"))
        {
          // Process adds to the end of the rules in-line
          String match = variableContext.getParameter(seqPrefix+"specmatch"+pathDescription);
          String type = variableContext.getParameter(seqPrefix+"spectype"+pathDescription);
          String flavor = variableContext.getParameter(seqPrefix+"specflavor"+pathDescription);
          SpecificationNode sn = new SpecificationNode(flavor);
          sn.setAttribute("type",type);
          sn.setAttribute("match",match);
          node.addChild(w,sn);
        }
        ds.addChild(k++,node);
        i++;
      }

      // See if there's a global add operation
      String op = variableContext.getParameter(seqPrefix+"specop");
      if (op != null && op.equals("Add"))
      {
        String path = variableContext.getParameter(seqPrefix+"specpath");
        String convertToURI = variableContext.getParameter(seqPrefix+"converttouri");

        SpecificationNode node = new SpecificationNode("startpoint");
        node.setAttribute("path",path);
        if (convertToURI != null)
          node.setAttribute("converttouri",convertToURI);
        
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
    
    return null;
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
    throws ManifoldCFException, IOException
  {
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\">" + Messages.getAttributeString(locale,"HDFSRepositoryConnector.Paths2") + "</td>\n"+    
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.RootPath") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.ConvertToURI") + "<br/>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.ConvertToURIExample")+ "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.Rules") + "</nobr></td>\n"+
"        </tr>\n"
    );
    
    int k = 0;
    for (int i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals("startpoint"))
      {
        String path = sn.getAttributeValue("path");
        String convertToURIString = sn.getAttributeValue("converttouri");
        boolean convertToURI = false;
        if (convertToURIString != null && convertToURIString.equals("true"))
          convertToURI = true;
        
        out.print(
"        <tr class=\""+(((k % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(path)+" \n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              "+(convertToURI?Messages.getBodyString(locale,"HDFSRepositoryConnector.Yes"):Messages.getBodyString(locale,"HDFSRepositoryConnector.No"))+" \n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"boxcell\">\n"+
"            <table class=\"formtable\">\n"+
"              <tr class=\"formheaderrow\">\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.IncludeExclude") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.FileDirectory") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"HDFSRepositoryConnector.Match") + "</nobr></td>\n"+
"              </tr>\n"
        );
        
        int l = 0;
        for (int j = 0; j < sn.getChildCount(); j++)
        {
          SpecificationNode excludeNode = sn.getChild(j);

          String nodeFlavor = excludeNode.getType();
          String nodeType = excludeNode.getAttributeValue("type");
          String nodeMatch = excludeNode.getAttributeValue("match");
          out.print(
"              <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
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
          l++;
        }

        if (l == 0)
        {
          out.print(
"              <tr><td class=\"formcolumnmessage\" colspan=\"3\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.NoRulesDefined") + "</td></tr>\n"
          );
        }

        out.print(
"            </table>\n"+
"           </td>\n"
        );

        out.print(
"        </tr>\n"
        );

        k++;
      }
      
    }

    if (k == 0)
    {
      out.print(
"        <tr><td class=\"formcolumnmessage\" colspan=\"3\">" + Messages.getBodyString(locale,"HDFSRepositoryConnector.NoDocumentsSpecified") + "</td></tr>\n"
      );
    }
    
    out.print(
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"
    );

    out.print(
"</table>\n"
    );
    
  }

  // Protected static methods

  /** Convert a path to an HDFS wget URI.  The URI is the URI that will be the unique key from
  * the search index, and will be presented to the user as part of the search results.
  *@param filePath is the document filePath.
  *@param repositoryPath is the document repositoryPath.
  *@return the document uri.
  */
  protected static String convertToWGETURI(String path)
    throws ManifoldCFException
  {
    //
    // Note well:  This MUST be a legal URI!!!
    try
    {
      StringBuffer sb = new StringBuffer();
      String[] tmp = path.split("/", 3);
      String scheme = "";
      String host = "";
      String other = "";
      if (tmp.length >= 1)
        scheme = tmp[0];
      else
        scheme = "hdfs";
      if (tmp.length >= 2)
        host = tmp[1];
      else
        host = "localhost:9000";
      if (tmp.length >= 3)
        other = "/" + tmp[2];
      else
        other = "/";
      return new URI(scheme + "://" + host + other).toURL().toString();
    }
    catch (java.net.MalformedURLException e)
    {
      throw new ManifoldCFException("Bad url: "+e.getMessage(),e);
    }
    catch (URISyntaxException e)
    {
      throw new ManifoldCFException("Bad url: "+e.getMessage(),e);
    }
  }

  /** This method finds the part of the path that should be converted to a URI.
  * Returns null if the path should not be converted.
  *@param spec is the document specification.
  *@param documentIdentifier is the document identifier.
  *@return the part of the path to be converted, or null.
  */
  protected static String findConvertPath(String nameNode, Specification spec, Path theFile)
  {
    String fullpath = theFile.toString();
    for (int j = 0; j < spec.getChildCount(); j++)
    {
      SpecificationNode sn = spec.getChild(j);
      if (sn.getType().equals("startpoint"))
      {
        String path = sn.getAttributeValue("path");
        String convertToURI = sn.getAttributeValue("converttouri");
        if (path.length() > 0 && convertToURI != null && convertToURI.equals("true"))
        {
          path = nameNode + path;
          if (!path.endsWith("/"))
            path += "/";
          if (fullpath.startsWith(path))
            return fullpath.substring(path.length());
        }
      }
    }
    return null;
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
  protected static boolean checkInclude(String nameNode, FileStatus fileStatus, String fileName, Specification documentSpecification)
    throws ManifoldCFException
  {
    if (Logging.connectors.isDebugEnabled())
    {
      Logging.connectors.debug("Checking whether to include file '"+fileName+"'");
    }

    String pathPart;
    String filePart;
    if (fileStatus.isDirectory())
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
  protected static boolean checkIngest(String nameNode, FileStatus fileStatus, Specification documentSpecification)
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
    Logging.connectors.warn("HDFS: IO exception: "+e.getMessage(),e);
    long currentTime = System.currentTimeMillis();
    throw new ServiceInterruption("IO exception: "+e.getMessage(), e, currentTime + 300000L, currentTime + 3 * 60 * 60000L,-1,false);
  }
  
  /**
   * @param e
   * @throws ManifoldCFException
   * @throws ServiceInterruption
   */
  private static void handleURISyntaxException(URISyntaxException e) throws ManifoldCFException, ServiceInterruption {
    // Permanent problem
    Logging.connectors.error("HDFS: Bad namenode specification: "+e.getMessage(), e);
    throw new ManifoldCFException("Bad namenode specification: "+e.getMessage(), e);
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
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
    } catch (IOException e) {
      handleIOException(e);
    }
  }

  protected static class GetSessionThread extends Thread {
    protected final String nameNode;
    protected final String user;
    protected Throwable exception = null;
    protected HDFSSession session;

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

  protected FileStatus[] getChildren(Path path)
    throws ManifoldCFException, ServiceInterruption {
    GetChildrenThread t = new GetChildrenThread(getSession(), path);
    try {
      t.start();
      t.finishUp();
      return t.getResult();
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
    return null;
  }

  protected class GetChildrenThread extends Thread {
    protected Throwable exception = null;
    protected FileStatus[] result = null;
    protected final HDFSSession session;
    protected final Path path;

    public GetChildrenThread(HDFSSession session, Path path) {
      super();
      this.session = session;
      this.path = path;
      setDaemon(true);
    }

    @Override
    public void run() {
      try {
        result = session.listStatus(path);
      } catch (Throwable e) {
        this.exception = e;
      }
    }

    public void finishUp() throws InterruptedException, IOException {
      join();
      Throwable thr = exception;
      if (thr != null) {
        if (thr instanceof RuntimeException) {
          throw (RuntimeException) thr;
        } else if (thr instanceof Error) {
          throw (Error) thr;
        } else if (thr instanceof IOException) {
          throw (IOException) thr;
        } else {
          throw new RuntimeException("Unhandled exception of type: "+thr.getClass().getName(),thr);
        }
      }
    }
    
    public FileStatus[] getResult() {
      return result;
    }
  }

  protected FileStatus getObject(Path path)
    throws ManifoldCFException, ServiceInterruption {
    GetObjectThread objt = new GetObjectThread(getSession(),path);
    try {
      objt.start();
      objt.finishUp();
      return objt.getResponse();
    } catch (InterruptedException e) {
      objt.interrupt();
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
    } catch (java.net.SocketTimeoutException e) {
      handleIOException(e);
    } catch (InterruptedIOException e) {
      objt.interrupt();
      handleIOException(e);
    } catch (IOException e) {
      handleIOException(e);
    }
    return null;
  }
  
  protected static class GetObjectThread extends Thread {
    protected final HDFSSession session;
    protected final Path nodeId;
    protected Throwable exception = null;
    protected FileStatus response = null;

    public GetObjectThread(HDFSSession session, Path nodeId) {
      super();
      setDaemon(true);
      this.session = session;
      this.nodeId = nodeId;
    }

    public void run() {
      try {
        response = session.getObject(nodeId);
      } catch (Throwable e) {
        this.exception = e;
      }
    }

    public void finishUp() throws InterruptedException, IOException {
      join();
      Throwable thr = exception;
      if (thr != null) {
        if (thr instanceof RuntimeException) {
          throw (RuntimeException) thr;
        } else if (thr instanceof Error) {
          throw (Error) thr;
        } else if (thr instanceof IOException) {
          throw (IOException) thr;
        } else {
          throw new RuntimeException("Unhandled exception of type: "+thr.getClass().getName(),thr);
        }
      }
    }

    public FileStatus getResponse() {
      return response;
    }

  }

  protected static class BackgroundStreamThread extends Thread
  {
    protected final HDFSSession session;
    protected final Path nodeId;
    
    protected boolean abortThread = false;
    protected Throwable responseException = null;
    protected InputStream sourceStream = null;
    protected XThreadInputStream threadStream = null;
    
    public BackgroundStreamThread(HDFSSession session, Path nodeId)
    {
      super();
      setDaemon(true);
      this.session = session;
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
