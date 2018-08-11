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

package org.apache.manifoldcf.crawler.connectors.email;

import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.util.URLEncoder;
import org.apache.manifoldcf.crawler.interfaces.IExistingVersions;
import org.apache.manifoldcf.crawler.interfaces.IProcessActivity;
import org.apache.manifoldcf.crawler.interfaces.ISeedingActivity;
import org.apache.manifoldcf.crawler.system.Logging;

import javax.mail.*;
import javax.mail.internet.MimeMessage;
import javax.mail.search.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
* This interface describes an instance of a connection between a repository and ManifoldCF's
* standard "pull" ingestion agent.
* <p/>
* Each instance of this interface is used in only one thread at a time. Connection Pooling
* on these kinds of objects is performed by the factory which instantiates repository connectors
* from symbolic names and config parameters, and is pooled by these parameters. That is, a pooled connector
* handle is used only if all the connection parameters for the handle match.
* <p/>
* Implementers of this interface should provide a default constructor which has this signature:
* <p/>
* xxx();
* <p/>
* Connectors are either configured or not. If configured, they will persist in a pool, and be
* reused multiple times. Certain methods of a connector may be called before the connector is
* configured. This includes basically all methods that permit inspection of the connector's
* capabilities. The complete list is:
* <p/>
* <p/>
* The purpose of the repository connector is to allow documents to be fetched from the repository.
* <p/>
* Each repository connector describes a set of documents that are known only to that connector.
* It therefore establishes a space of document identifiers. Each connector will only ever be
* asked to deal with identifiers that have in some way originated from the connector.
* <p/>
* Documents are fetched in three stages. First, the getDocuments() method is called in the connector
* implementation. This returns a set of document identifiers. The document identifiers are used to
* obtain the current document version strings in the second stage, using the getDocumentVersions() method.
* The last stage is processDocuments(), which queues up any additional documents needed, and also ingests.
* This method will not be called if the document version seems to indicate that no document change took
* place.
*/

public class EmailConnector extends org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector {

  protected final static long SESSION_EXPIRATION_MILLISECONDS = 300000L;
  
  // Local variables.
  protected long sessionExpiration = -1L;
  
  // Parameters for establishing a session
  
  protected String server = null;
  protected String portString = null;
  protected String username = null;
  protected String password = null;
  protected String protocol = null;
  protected Properties properties = null;
  protected String urlTemplate = null;
  protected String attachmentUrlTemplate = null;
  
  // Local session handle
  protected EmailSession session = null;

  private static Map<String,String> providerMap;
  static
  {
    providerMap = new HashMap<String,String>();
    providerMap.put(EmailConfig.PROTOCOL_POP3, EmailConfig.PROTOCOL_POP3_PROVIDER);
    providerMap.put(EmailConfig.PROTOCOL_POP3S, EmailConfig.PROTOCOL_POP3S_PROVIDER);
    providerMap.put(EmailConfig.PROTOCOL_IMAP, EmailConfig.PROTOCOL_IMAP_PROVIDER);
    providerMap.put(EmailConfig.PROTOCOL_IMAPS, EmailConfig.PROTOCOL_IMAPS_PROVIDER);
  }
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
    this.server = configParameters.getParameter(EmailConfig.SERVER_PARAM);
    this.portString = configParameters.getParameter(EmailConfig.PORT_PARAM);
    this.protocol = configParameters.getParameter(EmailConfig.PROTOCOL_PARAM);
    this.username = configParameters.getParameter(EmailConfig.USERNAME_PARAM);
    this.password = configParameters.getObfuscatedParameter(EmailConfig.PASSWORD_PARAM);
    this.urlTemplate = configParameters.getParameter(EmailConfig.URL_PARAM);
    this.attachmentUrlTemplate = configParameters.getParameter(EmailConfig.ATTACHMENT_URL_PARAM);
    this.properties = new Properties();
    int i = 0;
    while (i < configParameters.getChildCount()) //In post property set is added as a configuration node
    {
      ConfigNode cn = configParameters.getChild(i++);
      if (cn.getType().equals(EmailConfig.NODE_PROPERTIES)) {
        String findParameterName = cn.getAttributeValue(EmailConfig.ATTRIBUTE_NAME);
        String findParameterValue = cn.getAttributeValue(EmailConfig.ATTRIBUTE_VALUE);
        this.properties.setProperty(findParameterName, findParameterValue);
      }
    }
  }

  /**
  * Close the connection. Call this before discarding this instance of the
  * repository connector.
  */
  @Override
  public void disconnect()
    throws ManifoldCFException {
    this.attachmentUrlTemplate = null;
    this.urlTemplate = null;
    this.server = null;
    this.portString = null;
    this.protocol = null;
    this.username = null;
    this.password = null;
    this.properties = null;
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
    } catch (MessagingException e) {
      handleMessagingException(e,"checking the connection");
    }
  }

  ///////////////////////////////End of Basic Connector Methods////////////////////////////////////////

  //////////////////////////////Start of Repository Connector Method///////////////////////////////////

  @Override
  public int getConnectorModel() {
    return MODEL_ADD; //Change is not applicable in context of email
  }

  /**
  * Return the list of activities that this connector supports (i.e. writes into the log).
  *
  * @return the list.
  */
  @Override
  public String[] getActivitiesList() {
    return new String[]{EmailConfig.ACTIVITY_FETCH};
  }

  /**
  * Get the bin name strings for a document identifier. The bin name describes the queue to which the
  * document will be assigned for throttling purposes. Throttling controls the rate at which items in a
  * given queue are fetched; it does not say anything about the overall fetch rate, which may operate on
  * multiple queues or bins.
  * For example, if you implement a web crawler, a good choice of bin name would be the server name, since
  * that is likely to correspond to a real resource that will need real throttle protection.
  *
  * @param documentIdentifier is the document identifier.
  * @return the set of bin names. If an empty array is returned, it is equivalent to there being no request
  * rate throttling available for this identifier.
  */
  @Override
  public String[] getBinNames(String documentIdentifier) {
    return new String[]{server};
  }

  /**
  * Get the maximum number of documents to amalgamate together into one batch, for this connector.
  *
  * @return the maximum number. 0 indicates "unlimited".
  */
  @Override
  public int getMaxDocumentRequest() {
    return 10;
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
  *@param lastSeedVersion is the last seeding version string for this job, or null if the job has no previous seeding version string.
  *@param seedTime is the end of the time range of documents to consider, exclusive.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@return an updated seeding version string, to be stored with the job.
  */
  @Override
  public String addSeedDocuments(ISeedingActivity activities, Specification spec,
    String lastSeedVersion, long seedTime, int jobMode)
    throws ManifoldCFException, ServiceInterruption {

    long startTime;
    if (lastSeedVersion == null)
      startTime = 0L;
    else
    {
      // Unpack seed time from seed version string
      startTime = new Long(lastSeedVersion).longValue();
    }

    getSession();

    int i = 0;
    Map<String,String> findMap = new HashMap<String,String>();
    List<String> folderNames = new ArrayList<String>();
    while (i < spec.getChildCount()) {
      SpecificationNode sn = spec.getChild(i++);
      if (sn.getType().equals(EmailConfig.NODE_FOLDER)) {
        folderNames.add(sn.getAttributeValue(EmailConfig.ATTRIBUTE_NAME));
      } else if (sn.getType().equals(EmailConfig.NODE_FILTER)) {
        String findParameterName, findParameterValue;
        findParameterName = sn.getAttributeValue(EmailConfig.ATTRIBUTE_NAME);
        findParameterValue = sn.getAttributeValue(EmailConfig.ATTRIBUTE_VALUE);
        findMap.put(findParameterName, findParameterValue);

      }

    }
    
    for (String folderName : folderNames)
    {
      try {
        OpenFolderThread oft = new OpenFolderThread(session, folderName);
        oft.start();
        Folder folder = oft.finishUp();
        try
        {
          Message[] messages = findMessages(folder, startTime, seedTime, findMap);
          for (Message message : messages) {
            String emailID = ((MimeMessage) message).getMessageID();
            activities.addSeedDocument(createDocumentIdentifier(folderName,emailID));
          }
        }
        finally
        {
          CloseFolderThread cft = new CloseFolderThread(session, folder);
          cft.start();
          cft.finishUp();
        }
      } catch (InterruptedException e) {
        throw new ManifoldCFException(e.getMessage(),ManifoldCFException.INTERRUPTED);
      } catch (MessagingException e) {
        handleMessagingException(e, "finding emails");
      }
    }

    return new Long(seedTime).toString();
  }

  /*
  This method will return the list of messages which matches the given criteria
  */
  private Message[] findMessages(Folder folder, long startTime, long endTime, Map<String,String> findMap)
    throws MessagingException, InterruptedException {
    String findParameterName;
    String findParameterValue;
    
    SearchTerm searchTerm = null;
    
    Iterator<Map.Entry<String,String>> it = findMap.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String,String> pair = it.next();
      findParameterName = pair.getKey().toLowerCase(Locale.ROOT);
      findParameterValue = pair.getValue();
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("Email: Finding emails where '" + findParameterName +
            "' = '" + findParameterValue + "'");
      SearchTerm searchClause = null;
      Integer comparisonTerm = null;
      if (findParameterName.equals(EmailConfig.EMAIL_SUBJECT)) {
        searchClause = new SubjectTerm(findParameterValue);
      } else if (findParameterName.equals(EmailConfig.EMAIL_FROM)) {
        searchClause = new FromStringTerm(findParameterValue);
      } else if (findParameterName.equals(EmailConfig.EMAIL_TO)) {
        searchClause = new RecipientStringTerm(Message.RecipientType.TO, findParameterValue);
      } else if (findParameterName.equals(EmailConfig.EMAIL_BODY)) {
        searchClause = new BodyTerm(findParameterValue);
      } else if (findParameterName.equals(EmailConfig.EMAIL_START_DATE)) {
        comparisonTerm = ComparisonTerm.GT;
      } else if (findParameterName.equals(EmailConfig.EMAIL_END_DATE)) {
        comparisonTerm = ComparisonTerm.LT;
      }

      if (comparisonTerm != null) {
        SimpleDateFormat date = new SimpleDateFormat(EmailConfig.EMAIL_FILTERING_DATE_FORMAT, Locale.ROOT);
        try {
          searchClause = new ReceivedDateTerm(comparisonTerm, date.parse(findParameterValue));
        } catch (ParseException e) {
          Logging.connectors.warn("Email: Unknown date format: '" + findParameterValue + "'for filter parameter name: '" + findParameterName + "'");
        }
      }


      if (searchClause != null)
      {
        if (searchTerm == null)
          searchTerm = searchClause;
        else
          searchTerm = new AndTerm(searchTerm, searchClause);
      }
      else
      {
        Logging.connectors.warn("Email: Unknown filter parameter name: '"+findParameterName+"'");
      }
    }
    
    Message[] result;
    if (searchTerm == null)
    {
      GetMessagesThread gmt = new GetMessagesThread(session, folder);
      gmt.start();
      result = gmt.finishUp();
    }
    else
    {
      SearchMessagesThread smt = new SearchMessagesThread(session, folder, searchTerm);
      smt.start();
      result = smt.finishUp();
    }
    return result;
  }

  protected void getSession()
    throws ManifoldCFException, ServiceInterruption {
    if (session == null) {
      
      // Check that all the required parameters are there.
      if (urlTemplate == null)
        throw new ManifoldCFException("Missing url parameter");
      if (server == null)
        throw new ManifoldCFException("Missing server parameter");
      if (properties == null)
        throw new ManifoldCFException("Missing server properties");
      if (protocol == null)
        throw new ManifoldCFException("Missing protocol parameter");
      
      // Create a session.
      int port;
      if (portString != null && portString.length() > 0)
      {
        try
        {
          port = Integer.parseInt(portString);
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException("Port number has bad format: "+e.getMessage(),e);
        }
      }
      else
        port = -1;

      try {
        ConnectThread connectThread = new ConnectThread(server, port, username, password,
          providerMap.get(protocol), properties);
        connectThread.start();
        session = connectThread.finishUp();
      } catch (InterruptedException e) {
        throw new ManifoldCFException(e.getMessage(),ManifoldCFException.INTERRUPTED);
      } catch (MessagingException e) {
        handleMessagingException(e, "connecting");
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
      } catch (MessagingException e) {
        Logging.connectors.warn("Error while closing connection to server: " + e.getMessage(),e);
      } finally {
        session = null;
      }
    }
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

    List<String> requiredMetadata = new ArrayList<String>();
    boolean useEmailExtractor = false;
    for (int i = 0; i < spec.getChildCount(); i++) {
      SpecificationNode sn = spec.getChild(i);
      if (sn.getType().equals(EmailConfig.NODE_METADATA)) {
        String metadataAttribute = sn.getAttributeValue(EmailConfig.ATTRIBUTE_NAME);
        requiredMetadata.add(metadataAttribute);
      }
      if (sn.getType().equals(EmailConfig.NODE_EXTRACT_EMAIL)) {
        useEmailExtractor = true;
      }
    }
    
    // Keep a cached set of open folders
    Map<String,Folder> openFolders = new HashMap<String,Folder>();
    try {

      for (String documentIdentifier : documentIdentifiers) {
        final Integer attachmentIndex = extractAttachmentNumberFromDocumentIdentifier(documentIdentifier);
        if (attachmentIndex == null) {
          // It's an email
          String versionString = "_" + urlTemplate;   // NOT empty; we need to make ManifoldCF understand that this is a document that never will change.
          
          // Check if we need to index
          if (!activities.checkDocumentNeedsReindexing(documentIdentifier,versionString))
            continue;
          
          String compositeID = documentIdentifier;
          String version = versionString;
          String folderName = extractFolderNameFromDocumentIdentifier(compositeID);
          String id = extractEmailIDFromDocumentIdentifier(compositeID);
          
          String errorCode = null;
          String errorDesc = null;
          Long fileLengthLong = null;
          long startTime = System.currentTimeMillis();
          try {
            try {
              Folder folder = openFolders.get(folderName);
              if (folder == null)
              {
                getSession();
                OpenFolderThread oft = new OpenFolderThread(session, folderName);
                oft.start();
                folder = oft.finishUp();
                openFolders.put(folderName,folder);
              }
              
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("Email: Processing document identifier '"
                  + compositeID + "'");
              SearchTerm messageIDTerm = new MessageIDTerm(id);
                
              getSession();
              SearchMessagesThread smt = new SearchMessagesThread(session, folder, messageIDTerm);
              smt.start();
              Message[] message = smt.finishUp();

              String msgURL = makeDocumentURI(urlTemplate, folderName, id);

              Message msg = null;
              for (Message msg2 : message) {
                msg = msg2;
              }
              if (msg == null) {
                // email was not found
                activities.deleteDocument(documentIdentifier);
                continue;
              }
                
              if (!activities.checkURLIndexable(msgURL)) {
                errorCode = activities.EXCLUDED_URL;
                errorDesc = "Excluded because of URL ('"+msgURL+"')";
                activities.noDocument(documentIdentifier, version);
                continue;
              }
                
              long fileLength = msg.getSize();
              if (!activities.checkLengthIndexable(fileLength)) {
                errorCode = activities.EXCLUDED_LENGTH;
                errorDesc = "Excluded because of length ("+fileLength+")";
                activities.noDocument(documentIdentifier, version);
                continue;
              }
                
              Date sentDate = msg.getSentDate();
              if (!activities.checkDateIndexable(sentDate)) {
                errorCode = activities.EXCLUDED_DATE;
                errorDesc = "Excluded because of date ("+sentDate+")";
                activities.noDocument(documentIdentifier, version);
                continue;
              }
              
              String mimeType = "text/plain";
              if (!activities.checkMimeTypeIndexable(mimeType)) {
                errorCode = activities.EXCLUDED_MIMETYPE;
                errorDesc = "Excluded because of mime type ('"+mimeType+"')";
                activities.noDocument(documentIdentifier, version);
                continue;
              }
              
              RepositoryDocument rd = new RepositoryDocument();
              rd.setFileName(msg.getFileName());
              rd.setMimeType(mimeType);
              rd.setCreatedDate(sentDate);
              rd.setModifiedDate(sentDate);

              for (String metadata : requiredMetadata) {
                if (metadata.toLowerCase(Locale.ROOT).equals(EmailConfig.EMAIL_TO)) {
                  Address[] to = msg.getRecipients(Message.RecipientType.TO);
                  if (to != null) {
                    String[] toStr = new String[to.length];
                    int j = 0;
                    for (Address address : to) {
                      toStr[j] = useEmailExtractor ? extractEmailAddress(address.toString()) : address.toString();
                      j++;
                    }
                    rd.addField(EmailConfig.EMAIL_TO, toStr);
                  }
                } else if (metadata.toLowerCase(Locale.ROOT).equals(EmailConfig.EMAIL_FROM)) {
                  Address[] from = msg.getFrom();
                  String[] fromStr = new String[from.length];
                  int j = 0;
                  for (Address address : from) {
                    fromStr[j] = useEmailExtractor ? extractEmailAddress(address.toString()) : address.toString();
                    j++;
                  }
                  rd.addField(EmailConfig.EMAIL_FROM, fromStr);
                } else if (metadata.toLowerCase(Locale.ROOT).equals(EmailConfig.EMAIL_SUBJECT)) {
                  String subject = msg.getSubject();
                  rd.addField(EmailConfig.EMAIL_SUBJECT, subject);
                } else if (metadata.toLowerCase(Locale.ROOT).equals(EmailConfig.EMAIL_DATE)) {
                  rd.addField(EmailConfig.EMAIL_DATE, sentDate.toString());
                } else if (metadata.toLowerCase(Locale.ROOT).equals(EmailConfig.EMAIL_ATTACHMENT_ENCODING)) {
                  Object o = msg.getContent();
                  if (o != null) {
                    if (o instanceof Multipart) {
                      Multipart mp = (Multipart) o;
                      String[] encoding = new String[mp.getCount()];
                      for (int k = 0, n = mp.getCount(); k < n; k++) {
                        Part part = mp.getBodyPart(k);
                        if (isAttachment(part)) {
                          final String[] fileSplit = part.getFileName().split("\\?");
                          if (fileSplit.length > 1) {
                            encoding[k] = fileSplit[1];
                          } else {
                            encoding[k] = "";
                          }
                        }
                      }
                      rd.addField(EmailConfig.ENCODING_FIELD, encoding);
                    } else if (o instanceof String) {
                      rd.addField(EmailConfig.ENCODING_FIELD, "");
                    }
                  }
                } else if (metadata.toLowerCase(Locale.ROOT).equals(EmailConfig.EMAIL_ATTACHMENT_MIMETYPE)) {
                  Object o = msg.getContent();
                  if (o != null) {
                    if (o instanceof Multipart) {
                      Multipart mp = (Multipart) o;
                      String[] MIMEType = new String[mp.getCount()];
                      for (int k = 0, n = mp.getCount(); k < n; k++) {
                        Part part = mp.getBodyPart(k);
                        if (isAttachment(part)) {
                          MIMEType[k] = part.getContentType();

                        }
                      }
                      rd.addField(EmailConfig.MIMETYPE_FIELD, MIMEType);
                    } else if (o instanceof String) {
                      rd.addField(EmailConfig.MIMETYPE_FIELD, "");
                    }
                  }
                } else if (metadata.toLowerCase(Locale.ROOT).equals(EmailConfig.EMAIL_ATTACHMENTNAME)) {
                  Object o = msg.getContent();
                  if (o != null) {
                    if (o instanceof Multipart) {
                      Multipart mp = (Multipart) o;
                      String[] attachmentNames = new String[mp.getCount()];
                      for (int k = 0, n = mp.getCount(); k < n; k++) {
                        Part part = mp.getBodyPart(k);
                        if (isAttachment(part)) {
                          attachmentNames[k] = part.getFileName();
                        }
                      }
                      rd.addField(EmailConfig.ATTACHMENTNAME_FIELD, attachmentNames);
                    } else if (o instanceof String) {
                      rd.addField(EmailConfig.ATTACHMENTNAME_FIELD, "");
                    }
                  }
                }
              }

              //Content includes both body and attachments,
              //Body will be set as content and attachments will be indexed as separate documents.
              final EmailContent bodyContent = extractBodyContent(msg);
              if(bodyContent != null) {
                rd.setMimeType(bodyContent.getMimeType());
                InputStream is = new ByteArrayInputStream(bodyContent.getContent().getBytes(StandardCharsets.UTF_8));
                try {
                  rd.setBinary(is, fileLength);
                  activities.ingestDocumentWithException(documentIdentifier, version, msgURL, rd);
                  errorCode = "OK";
                  fileLengthLong = new Long(fileLength);
                } finally {
                  is.close();
                }
              }

              // If we're supposed to deal with attachments, this is the time to queue them up
              if (attachmentUrlTemplate != null) {
                if (msg.getContent() != null && msg.getContent() instanceof Multipart) {
                  final Multipart mp = (Multipart) msg.getContent();
                  final int numAttachments = mp.getCount();
                  for (int i = 0; i < numAttachments; i++) {
                    if (isAttachment(mp.getBodyPart(i))) {
                      activities.addDocumentReference(documentIdentifier + ":" + i);
                    }
                  }
                }
              }
              
            } catch (InterruptedException e) {
              throw new ManifoldCFException(e.getMessage(), ManifoldCFException.INTERRUPTED);
            } catch (MessagingException e) {
              errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
              errorDesc = e.getMessage();
              handleMessagingException(e, "processing email");
            } catch (IOException e) {
              errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
              errorDesc = e.getMessage();
              handleIOException(e, "processing email");
              throw new ManifoldCFException(e.getMessage(), e);
            }
          } catch (ManifoldCFException e) {
            if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
              errorCode = null;
            throw e;
          } finally {
            if (errorCode != null)
              activities.recordActivity(new Long(startTime),EmailConfig.ACTIVITY_FETCH,
                fileLengthLong,documentIdentifier,errorCode,errorDesc,null);
          }
        } else {
          // It's a specific attachment
          final int attachmentNumber = attachmentIndex;
          
          String versionString = "_" + attachmentUrlTemplate;   // NOT empty; we need to make ManifoldCF understand that this is a document that never will change.
          
          // Check if we need to index
          if (!activities.checkDocumentNeedsReindexing(documentIdentifier,versionString))
            continue;
          
          String compositeID = documentIdentifier;
          String version = versionString;
          String folderName = extractFolderNameFromDocumentIdentifier(compositeID);
          String id = extractEmailIDFromDocumentIdentifier(compositeID);
          
          String errorCode = null;
          String errorDesc = null;
          Long fileLengthLong = null;
          long startTime = System.currentTimeMillis();
          try {
            try {
              Folder folder = openFolders.get(folderName);
              if (folder == null)
              {
                getSession();
                OpenFolderThread oft = new OpenFolderThread(session, folderName);
                oft.start();
                folder = oft.finishUp();
                openFolders.put(folderName,folder);
              }
              
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("Email: Processing document identifier '"
                  + documentIdentifier + "'");
              SearchTerm messageIDTerm = new MessageIDTerm(id);
                
              getSession();
              SearchMessagesThread smt = new SearchMessagesThread(session, folder, messageIDTerm);
              smt.start();
              Message[] message = smt.finishUp();

              String msgURL = makeDocumentURI(attachmentUrlTemplate, folderName, id, attachmentNumber);

              Message msg = null;
              for (Message msg2 : message) {
                msg = msg2;
              }
              if (msg == null) {
                // email was not found
                activities.deleteDocument(documentIdentifier);
                continue;
              }
                
              if (!activities.checkURLIndexable(msgURL)) {
                errorCode = activities.EXCLUDED_URL;
                errorDesc = "Excluded because of URL ('"+msgURL+"')";
                activities.noDocument(documentIdentifier, version);
                continue;
              }

              final Date sentDate = msg.getSentDate();
              if (!activities.checkDateIndexable(sentDate)) {
                errorCode = activities.EXCLUDED_DATE;
                errorDesc = "Excluded because of date ("+sentDate+")";
                activities.noDocument(documentIdentifier, version);
                continue;
              }

              final Multipart mp = (Multipart) msg.getContent();
              if (mp.getCount() <= attachmentNumber) {
                activities.deleteDocument(documentIdentifier);
                continue;
              }
              final Part part = mp.getBodyPart(attachmentNumber);
                            
              final long fileLength = part.getSize();
              if (!activities.checkLengthIndexable(fileLength)) {
                errorCode = activities.EXCLUDED_LENGTH;
                errorDesc = "Excluded because of length ("+fileLength+")";
                activities.noDocument(documentIdentifier, version);
                continue;
              }
                
              final String origMimeType = part.getContentType();
              final String mimeType;
              //MSExchange puts crap after the mime type so it has to be munged.
              // Example: "application/msword; name=SampleDOCFile_100kb.doc"
              if (origMimeType == null || origMimeType.indexOf(";") == -1) {
                mimeType = origMimeType;
              } else {
                mimeType = origMimeType.substring(0, origMimeType.indexOf(";"));
              }
              if (!activities.checkMimeTypeIndexable(mimeType)) {
                errorCode = activities.EXCLUDED_MIMETYPE;
                errorDesc = "Excluded because of mime type ('"+mimeType+"')";
                activities.noDocument(documentIdentifier, version);
                continue;
              }

              RepositoryDocument rd = new RepositoryDocument();
              rd.setFileName(part.getFileName());
              rd.setMimeType(mimeType);
              rd.setCreatedDate(sentDate);
              rd.setModifiedDate(sentDate);

              for (String metadata : requiredMetadata) {
                if (metadata.toLowerCase(Locale.ROOT).equals(EmailConfig.EMAIL_TO)) {
                  Address[] to = msg.getRecipients(Message.RecipientType.TO);
                  if (to != null) {
                    String[] toStr = new String[to.length];
                    int j = 0;
                    for (Address address : to) {
                      toStr[j] = useEmailExtractor ? extractEmailAddress(address.toString()) : address.toString();
                      j++;
                    }
                    rd.addField(EmailConfig.EMAIL_TO, toStr);
                  }
                } else if (metadata.toLowerCase(Locale.ROOT).equals(EmailConfig.EMAIL_FROM)) {
                  Address[] from = msg.getFrom();
                  String[] fromStr = new String[from.length];
                  int j = 0;
                  for (Address address : from) {
                    fromStr[j] = useEmailExtractor ? extractEmailAddress(address.toString()) : address.toString();
                    j++;
                  }
                  rd.addField(EmailConfig.EMAIL_FROM, fromStr);
                } else if (metadata.toLowerCase(Locale.ROOT).equals(EmailConfig.EMAIL_SUBJECT)) {
                  String subject = msg.getSubject();
                  //Attachments may have a field named "subject". So, different field name is used not to clash.
                  rd.addField(EmailConfig.MAILSUBJECT_FIELD, subject);
                } else if (metadata.toLowerCase(Locale.ROOT).equals(EmailConfig.EMAIL_DATE)) {
                  rd.addField(EmailConfig.EMAIL_DATE, sentDate.toString());
                }
              }

              final InputStream is = part.getInputStream();
              try {
                rd.setBinary(is, fileLength);
                activities.ingestDocumentWithException(documentIdentifier, version, msgURL, rd);
                errorCode = "OK";
                fileLengthLong = new Long(fileLength);
              } finally {
                is.close();
              }

            } catch (InterruptedException e) {
              throw new ManifoldCFException(e.getMessage(), ManifoldCFException.INTERRUPTED);
            } catch (MessagingException e) {
              errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
              errorDesc = e.getMessage();
              handleMessagingException(e, "processing email attachment");
            } catch (IOException e) {
              errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
              errorDesc = e.getMessage();
              handleIOException(e, "processing email attachment");
              throw new ManifoldCFException(e.getMessage(), e);
            }
          } catch (ManifoldCFException e) {
            if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
              errorCode = null;
            throw e;
          } finally {
            if (errorCode != null)
              activities.recordActivity(new Long(startTime),EmailConfig.ACTIVITY_FETCH,
                fileLengthLong,documentIdentifier,errorCode,errorDesc,null);
          }

        }
      }
    }
    finally
    {
      for (Folder f : openFolders.values())
      {
        try
        {
          CloseFolderThread cft = new CloseFolderThread(session, f);
          cft.start();
          cft.finishUp();
        }
        catch (InterruptedException e)
        {
          throw new ManifoldCFException(e.getMessage(),ManifoldCFException.INTERRUPTED);
        }
        catch (MessagingException e)
        {
          handleMessagingException(e, "closing folders");
        }
      }
    }

  }

  private EmailContent getContent(Part part) throws MessagingException, IOException {
    if (part.isMimeType(EmailConfig.MIMETYPE_TEXT_PLAIN)) {
      return new EmailContent(part.getContent().toString());
    } else if(part.isMimeType(EmailConfig.MIMETYPE_HTML)) {
      return new EmailContent(part.getContent().toString(), EmailConfig.MIMETYPE_HTML);
    }

    if (part.isMimeType(EmailConfig.MIMETYPE_MULTIPART_ALTERNATIVE)) {
      // prefer html text over plain text
      Multipart mp = (Multipart) part.getContent();
      EmailContent emailContent = null;
      for (int i = 0; i < mp.getCount(); i++) {
        Part bodyPart = mp.getBodyPart(i);
        if (bodyPart.isMimeType(EmailConfig.MIMETYPE_TEXT_PLAIN)) {
          if (emailContent == null) {
            emailContent = getContent(bodyPart);
          }
          continue;
        } else if (bodyPart.isMimeType(EmailConfig.MIMETYPE_HTML)) {
          emailContent = getContent(bodyPart);
          if (emailContent != null) {
            return emailContent;
          }
        } else {
          return getContent(bodyPart);
        }
      }
      return emailContent;
    } else if (part.isMimeType(EmailConfig.MIMETYPE_MULTIPART_GENERIC)) {
      Multipart mp = (Multipart) part.getContent();
      for (int i = 0; i < mp.getCount(); i++) {
        EmailContent emailContent = getContent(mp.getBodyPart(i));
        if (emailContent != null) {
          return emailContent;
        }
      }
    }
    return null;
  }

  private EmailContent extractBodyContent(Message msg) throws MessagingException, IOException {
    EmailContent emailContent = null;
    Object o = msg.getContent();
    if (o instanceof Multipart) {
      Multipart mp = (Multipart) msg.getContent();
      for (int k = 0, n = mp.getCount(); k < n; k++) {
        Part part = mp.getBodyPart(k);
        String disposition = part.getDisposition();
        if (disposition == null) {
          EmailContent content = getContent(part);
          if (content != null) {
            emailContent = content;
          }
        }
      }
    } else if (o instanceof String) {
      emailContent = new EmailContent((String)o);
    }
    return emailContent;
  }

  /**
  * Checks whether a Part is an attachment or not
  * @param part Part to check
  * @return is attachment or not
  */
  private boolean isAttachment(Part part) throws MessagingException {
    String disposition = part.getDisposition();
    return ((disposition != null)
        && ((disposition.toLowerCase(Locale.ROOT).equals(Part.ATTACHMENT)
        || (disposition.toLowerCase(Locale.ROOT).equals(Part.INLINE)))));
  }

  /**
   * Extracts e-mail address within < and > characters if any.
   * If not, returns passed raw mail address.
   *
   * @param rawEmailAddress e-mail address to be extracted
   * @return Extracted e-mail address
   */
  private String extractEmailAddress(String rawEmailAddress) {
    Pattern pattern = Pattern.compile("<(.+?@.+?)>");
    Matcher matcher = pattern.matcher(rawEmailAddress);

    return matcher.find() ? matcher.group(1) : rawEmailAddress;
  }

  //////////////////////////////End of Repository Connector Methods///////////////////////////////////


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
    tabsArray.add(Messages.getString(locale, "EmailConnector.Server"));
    tabsArray.add(Messages.getString(locale, "EmailConnector.URL"));
    // Map the parameters
    Map<String, Object> paramMap = new HashMap<String, Object>();

    // Fill in the parameters from each tab
    fillInServerConfigurationMap(paramMap, out, parameters);
    fillInURLConfigurationMap(paramMap, out, parameters);

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
    fillInURLConfigurationMap(paramMap, out, parameters);
    Messages.outputResourceWithVelocity(out, locale, "Configuration_Server.html", paramMap);
    Messages.outputResourceWithVelocity(out, locale, "Configuration_URL.html", paramMap);
  }

  private static void fillInServerConfigurationMap(Map<String, Object> paramMap, IPasswordMapperActivity mapper, ConfigParams parameters) {
    int i = 0;
    String username = parameters.getParameter(EmailConfig.USERNAME_PARAM);
    String password = parameters.getObfuscatedParameter(EmailConfig.PASSWORD_PARAM);
    String protocol = parameters.getParameter(EmailConfig.PROTOCOL_PARAM);
    String server = parameters.getParameter(EmailConfig.SERVER_PARAM);
    String port = parameters.getParameter(EmailConfig.PORT_PARAM);
    List<Map<String, String>> list = new ArrayList<Map<String, String>>();
    while (i < parameters.getChildCount()) //In post property set is added as a configuration node
    {
      ConfigNode cn = parameters.getChild(i++);
      if (cn.getType().equals(EmailConfig.NODE_PROPERTIES)) {
        String findParameterName = cn.getAttributeValue(EmailConfig.ATTRIBUTE_NAME);
        String findParameterValue = cn.getAttributeValue(EmailConfig.ATTRIBUTE_VALUE);
        Map<String, String> row = new HashMap<String, String>();
        row.put("name", findParameterName);
        row.put("value", findParameterValue);
        list.add(row);
      }
    }

    if (username == null)
      username = StringUtils.EMPTY;
    if (password == null)
      password = StringUtils.EMPTY;
    else
      password = mapper.mapPasswordToKey(password);
    if (protocol == null)
      protocol = EmailConfig.PROTOCOL_DEFAULT_VALUE;
    if (server == null)
      server = StringUtils.EMPTY;
    if (port == null)
      port = EmailConfig.PORT_DEFAULT_VALUE;

    paramMap.put("USERNAME", username);
    paramMap.put("PASSWORD", password);
    paramMap.put("PROTOCOL", protocol);
    paramMap.put("SERVER", server);
    paramMap.put("PORT", port);
    paramMap.put("PROPERTIES", list);

  }

  private static void fillInURLConfigurationMap(Map<String, Object> paramMap, IPasswordMapperActivity mapper, ConfigParams parameters) {
    String urlTemplate = parameters.getParameter(EmailConfig.URL_PARAM);

    if (urlTemplate == null) {
      urlTemplate = "http://sampleserver/$(FOLDERNAME)?id=$(MESSAGEID)";
    }

    paramMap.put("URL", urlTemplate);
    
    String attachmentUrlTemplate = parameters.getParameter(EmailConfig.ATTACHMENT_URL_PARAM);
    
    if (attachmentUrlTemplate == null) {
      attachmentUrlTemplate = "http://sampleserver/$(FOLDERNAME)?id=$(MESSAGEID)&attach=$(ATTACHMENTNUMBER)";
    }
    
    paramMap.put("ATTACHMENTURL", attachmentUrlTemplate);
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

    String urlTemplate = variableContext.getParameter("url");
    if (urlTemplate != null)
      parameters.setParameter(EmailConfig.URL_PARAM, urlTemplate);

    String attachmentUrlTemplate = variableContext.getParameter("attachmenturl");
    if (attachmentUrlTemplate != null)
      parameters.setParameter(EmailConfig.ATTACHMENT_URL_PARAM, attachmentUrlTemplate);

    String userName = variableContext.getParameter("username");
    if (userName != null)
      parameters.setParameter(EmailConfig.USERNAME_PARAM, userName);

    String password = variableContext.getParameter("password");
    if (password != null)
      parameters.setObfuscatedParameter(EmailConfig.PASSWORD_PARAM, variableContext.mapKeyToPassword(password));

    String protocol = variableContext.getParameter("protocol");
    if (protocol != null)
      parameters.setParameter(EmailConfig.PROTOCOL_PARAM, protocol);

    String server = variableContext.getParameter("server");
    if (server != null)
      parameters.setParameter(EmailConfig.SERVER_PARAM, server);
    String port = variableContext.getParameter("port");
    if (port != null)
      parameters.setParameter(EmailConfig.PORT_PARAM, port);
    // Remove old find parameter document specification information
    removeNodes(parameters, EmailConfig.NODE_PROPERTIES);

    // Parse the number of records that were posted
    String findCountString = variableContext.getParameter("findcount");
    if (findCountString != null) {
      int findCount = Integer.parseInt(findCountString);

      // Loop throught them and add new server properties
      int i = 0;
      while (i < findCount) {
        String suffix = "_" + Integer.toString(i++);
        // Only add the name/value if the item was not deleted.
        String findParameterOp = variableContext.getParameter("findop" + suffix);
        if (findParameterOp == null || !findParameterOp.equals("Delete")) {
          String findParameterName = variableContext.getParameter("findname" + suffix);
          String findParameterValue = variableContext.getParameter("findvalue" + suffix);
          addFindParameterNode(parameters, findParameterName, findParameterValue);
        }
      }
    }

    // Now, look for a global "Add" operation
    String operation = variableContext.getParameter("findop");
    if (operation != null && operation.equals("Add")) {
      // Pick up the global parameter name and value
      String findParameterName = variableContext.getParameter("findname");
      String findParameterValue = variableContext.getParameter("findvalue");
      addFindParameterNode(parameters, findParameterName, findParameterValue);
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
    fillInURLConfigurationMap(paramMap, out, parameters);

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
    tabsArray.add(Messages.getString(locale, "EmailConnector.Metadata"));
    tabsArray.add(Messages.getString(locale, "EmailConnector.Filter"));
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
    outputFilterTab(out, locale, ds, tabName, connectionSequenceNumber, actualSequenceNumber);
    outputMetadataTab(out, locale, ds, tabName, connectionSequenceNumber, actualSequenceNumber);
  }

  /**
* Take care of "Metadata" tab.
*/
  protected void outputMetadataTab(IHTTPOutput out, Locale locale,
    Specification ds, String tabName, int connectionSequenceNumber, int actualSequenceNumber)
    throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("TabName", tabName);
    paramMap.put("SeqNum", Integer.toString(connectionSequenceNumber));
    paramMap.put("SelectedNum", Integer.toString(actualSequenceNumber));
    fillInMetadataTab(paramMap, ds);
    fillInMetadataAttributes(paramMap);
    Messages.outputResourceWithVelocity(out, locale, "Specification_Metadata.html", paramMap);
  }

  /**
  * Fill in Velocity context for Metadata tab.
  */
  protected static void fillInMetadataTab(Map<String, Object> paramMap,
    Specification ds) {
    Set<String> metadataSelections = new HashSet<String>();
    String extractEmailSelection = null;
    int i = 0;
    while (i < ds.getChildCount()) {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(EmailConfig.NODE_METADATA)) {
        String metadataName = sn.getAttributeValue(EmailConfig.ATTRIBUTE_NAME);
        metadataSelections.add(metadataName);
      } else if (sn.getType().equals(EmailConfig.NODE_EXTRACT_EMAIL)) {
        extractEmailSelection = sn.getAttributeValue(EmailConfig.ATTRIBUTE_NAME);
      }
    }
    paramMap.put("METADATASELECTIONS", metadataSelections);
    paramMap.put("EXTRACTEMAILSELECTION", extractEmailSelection);
  }

  /**
  * Fill in Velocity context with data to permit attribute selection.
  */
  protected void fillInMetadataAttributes(Map<String, Object> paramMap) {
    String[] matchNames = EmailConfig.BASIC_METADATA;
    paramMap.put("METADATAATTRIBUTES", matchNames);

    String extractEmailAttribute = EmailConfig.BASIC_EXTRACT_EMAIL;
    paramMap.put("EXTRACTEMAILATTRIBUTE", extractEmailAttribute);
  }

  protected void outputFilterTab(IHTTPOutput out, Locale locale,
    Specification ds, String tabName, int connectionSequenceNumber, int actualSequenceNumber)
    throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("TabName", tabName);
    paramMap.put("SeqNum", Integer.toString(connectionSequenceNumber));
    paramMap.put("SelectedNum", Integer.toString(actualSequenceNumber));
    fillInFilterTab(paramMap, ds);
    if (tabName.equals(Messages.getString(locale, "EmailConnector.Filter")))
      fillInSearchableAttributes(paramMap);
    Messages.outputResourceWithVelocity(out, locale, "Specification_Filter.html", paramMap);
  }

  private void fillInSearchableAttributes(Map<String, Object> paramMap)
  {
    String[] attributes = EmailConfig.BASIC_SEARCHABLE_ATTRIBUTES;
    paramMap.put("SEARCHABLEATTRIBUTES", attributes);
    try
    {
      String[] folderNames = getFolderNames();
      paramMap.put("FOLDERNAMES", folderNames);
      paramMap.put("EXCEPTION", "");
    }
    catch (ManifoldCFException e)
    {
      paramMap.put("EXCEPTION", e.getMessage());
    }
    catch (ServiceInterruption e)
    {
      paramMap.put("EXCEPTION", e.getMessage());
    }
  }

  protected static void fillInFilterTab(Map<String, Object> paramMap,
    Specification ds) {
    List<Map<String, String>> filterList = new ArrayList<Map<String, String>>();
    Set<String> folders = new HashSet<String>();
    int i = 0;
    while (i < ds.getChildCount()) {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(EmailConfig.NODE_FILTER)) {

        String findParameterName = sn.getAttributeValue(EmailConfig.ATTRIBUTE_NAME);
        String findParameterValue = sn.getAttributeValue(EmailConfig.ATTRIBUTE_VALUE);
        Map<String, String> row = new HashMap<String, String>();
        row.put("name", findParameterName);
        row.put("value", findParameterValue);
        filterList.add(row);
      }
      else if (sn.getType().equals(EmailConfig.NODE_FOLDER)) {
        String folderName = sn.getAttributeValue(EmailConfig.ATTRIBUTE_NAME);
        folders.add(folderName);
      }
    }
    paramMap.put("MATCHES", filterList);
    paramMap.put("FOLDERS", folders);
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

    String result = processFilterTab(variableContext, ds, connectionSequenceNumber);
    if (result != null)
      return result;
    result = processMetadataTab(variableContext, ds, connectionSequenceNumber);
    return result;
  }


  protected String processFilterTab(IPostParameters variableContext, Specification ds,
    int connectionSequenceNumber)
    throws ManifoldCFException {

    String seqPrefix = "s"+connectionSequenceNumber+"_";
      
    String findCountString = variableContext.getParameter(seqPrefix + "findcount");
    if (findCountString != null) {
      int findCount = Integer.parseInt(findCountString);
      
      // Remove old find parameter document specification information
      removeNodes(ds, EmailConfig.NODE_FILTER);

      int i = 0;
      while (i < findCount) {
        String suffix = "_" + Integer.toString(i++);
        // Only add the name/value if the item was not deleted.
        String findParameterOp = variableContext.getParameter(seqPrefix + "findop" + suffix);
        if (findParameterOp == null || !findParameterOp.equals("Delete")) {
          String findParameterName = variableContext.getParameter(seqPrefix + "findname" + suffix);
          String findParameterValue = variableContext.getParameter(seqPrefix + "findvalue" + suffix);
          addFindParameterNode(ds, findParameterName, findParameterValue);
        }
      }

      String operation = variableContext.getParameter(seqPrefix + "findop");
      if (operation != null && operation.equals("Add")) {
        String findParameterName = variableContext.getParameter(seqPrefix + "findname");
        String findParameterValue = variableContext.getParameter(seqPrefix + "findvalue");
        addFindParameterNode(ds, findParameterName, findParameterValue);
      }
    }
    
    String[] folders = variableContext.getParameterValues(seqPrefix + "folders");
    if (folders != null)
    {
      removeNodes(ds, EmailConfig.NODE_FOLDER);
      for (String folder : folders)
      {
        addFolderNode(ds, folder);
      }
    }
    return null;
  }


  protected String processMetadataTab(IPostParameters variableContext, Specification ds,
                                   int connectionSequenceNumber)
          throws ManifoldCFException {
    String result = processMetadataAttributes(variableContext, ds, connectionSequenceNumber);
    if (result != null)
      return result;

    result = processExtractEmail(variableContext, ds, connectionSequenceNumber);
    return result;

  }

  protected String processMetadataAttributes(IPostParameters variableContext, Specification ds,
    int connectionSequenceNumber)
    throws ManifoldCFException {
      
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    // Remove old included metadata nodes
    removeNodes(ds, EmailConfig.NODE_METADATA);

    // Get the posted metadata values
    String[] metadataNames = variableContext.getParameterValues(seqPrefix + "metadata");
    if (metadataNames != null) {
      // Add each metadata name as a node to the document specification
      int i = 0;
      while (i < metadataNames.length) {
        String metadataName = metadataNames[i++];
        addIncludedMetadataNode(ds, metadataName);
      }
    }

    return null;
  }

  protected String processExtractEmail(IPostParameters variableContext, Specification ds,
    int connectionSequenceNumber)
    throws ManifoldCFException {

    String seqPrefix = "s"+connectionSequenceNumber+"_";

    // Remove old included extract email nodes
    removeNodes(ds, EmailConfig.NODE_EXTRACT_EMAIL);

    // Get the posted extract email value
    String extractEmail = variableContext.getParameter(seqPrefix + "extractemail");
    if (extractEmail == null) {
      return null;
    }

    // Gather the extract email parameter to be the last one
    SpecificationNode sn = new SpecificationNode(EmailConfig.NODE_EXTRACT_EMAIL);
    sn.setAttribute(EmailConfig.ATTRIBUTE_NAME, extractEmail);
    // Add the new extract email parameter
    ds.addChild(ds.getChildCount(), sn);

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
    throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("SeqNum", Integer.toString(connectionSequenceNumber));
    fillInFilterTab(paramMap, ds);
    fillInMetadataTab(paramMap, ds);
    Messages.outputResourceWithVelocity(out, locale, "SpecificationView.html", paramMap);
  }

  ///////////////////////////////////////End of specification UI///////////////////////////////////////////////
  
  /** Get a sorted list of folder names */
  protected String[] getFolderNames()
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();
    try
    {
      ListFoldersThread lft = new ListFoldersThread(session);
      lft.start();
      return lft.finishUp();
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException(e.getMessage(),ManifoldCFException.INTERRUPTED);
    }
    catch (MessagingException e)
    {
      handleMessagingException(e,"getting folder list");
      return null;
    }
  }

  /** Create a document's URI given a template, a folder name, and a message ID */
  protected static String makeDocumentURI(String urlTemplate, String folderName, String id)
  {
      // First, URL encode folder name and id
      String encodedFolderName = URLEncoder.encode(folderName);
      String encodedId = URLEncoder.encode(id);
      // The template is already URL encoded, except for the substitution points
      Map<String,String> subsMap = new HashMap<String,String>();
      subsMap.put("FOLDERNAME", encodedFolderName);
      subsMap.put("MESSAGEID", encodedId);
      return substitute(urlTemplate, subsMap);
  }

  /** Create a document's URI given a template, a folder name, a message ID, and an attachment number */
  protected static String makeDocumentURI(String urlTemplate, String folderName, String id, int attachmentNumber)
  {
      // First, URL encode folder name and id
      String encodedFolderName = URLEncoder.encode(folderName);
      String encodedId = URLEncoder.encode(id);
      // The template is already URL encoded, except for the substitution points
      Map<String,String> subsMap = new HashMap<String,String>();
      subsMap.put("FOLDERNAME", encodedFolderName);
      subsMap.put("MESSAGEID", encodedId);
      subsMap.put("ATTACHMENTNUMBER", Integer.toString(attachmentNumber));
      return substitute(urlTemplate, subsMap);
  }

  protected static String substitute(String template, Map<String,String> map)
  {
    StringBuilder sb = new StringBuilder();
    int index = 0;
    while (true)
    {
      int newIndex = template.indexOf("$(",index);
      if (newIndex == -1)
      {
        sb.append(template.substring(index));
        break;
      }
      sb.append(template.substring(index, newIndex));
      int endIndex = template.indexOf(")",newIndex+2);
      String varName;
      if (endIndex == -1)
        varName = template.substring(newIndex + 2);
      else
        varName = template.substring(newIndex + 2, endIndex);
      String subsValue = map.get(varName);
      if (subsValue == null)
        subsValue = "";
      sb.append(subsValue);
      if (endIndex == -1)
        break;
      index = endIndex+1;
    }
    return sb.toString();
  }
  
  protected static void addFindParameterNode(ConfigParams parameters, String findParameterName, String findParameterValue) {
    ConfigNode cn = new ConfigNode(EmailConfig.NODE_PROPERTIES);
    cn.setAttribute(EmailConfig.ATTRIBUTE_NAME, findParameterName);
    cn.setAttribute(EmailConfig.ATTRIBUTE_VALUE, findParameterValue);
    // Add to the end
    parameters.addChild(parameters.getChildCount(), cn);
  }

  protected static void removeNodes(ConfigParams parameters,
                    String nodeTypeName) {
    int i = 0;
    while (i < parameters.getChildCount()) {
      ConfigNode cn = parameters.getChild(i);
      if (cn.getType().equals(nodeTypeName))
        parameters.removeChild(i);
      else
        i++;
    }
  }

  protected static void removeNodes(Specification ds,
                    String nodeTypeName) {
    int i = 0;
    while (i < ds.getChildCount()) {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals(nodeTypeName))
        ds.removeChild(i);
      else
        i++;
    }
  }

  protected static void addIncludedMetadataNode(Specification ds,
                          String metadataName) {
    // Build the proper node
    SpecificationNode sn = new SpecificationNode(EmailConfig.NODE_METADATA);
    sn.setAttribute(EmailConfig.ATTRIBUTE_NAME, metadataName);
    // Add to the end
    ds.addChild(ds.getChildCount(), sn);
  }

  protected static void addFindParameterNode(Specification ds, String findParameterName, String findParameterValue) {
    SpecificationNode sn = new SpecificationNode(EmailConfig.NODE_FILTER);
    sn.setAttribute(EmailConfig.ATTRIBUTE_NAME, findParameterName);
    sn.setAttribute(EmailConfig.ATTRIBUTE_VALUE, findParameterValue);
    // Add to the end
    ds.addChild(ds.getChildCount(), sn);
  }

  protected static void addFolderNode(Specification ds, String folderName)
  {
    SpecificationNode sn = new SpecificationNode(EmailConfig.NODE_FOLDER);
    sn.setAttribute(EmailConfig.ATTRIBUTE_NAME, folderName);
    ds.addChild(ds.getChildCount(), sn);
  }
  

  /** Create a document identifier from a folder name and an email ID */
  protected static String createDocumentIdentifier(String folderName, String emailID)
  {
    return makeSafeFolderName(folderName) + ":" + emailID;
  }
  
  /** Find an attachment number in a document identifier */
  protected static Integer extractAttachmentNumberFromDocumentIdentifier(String di)
  {
    int index1 = di.indexOf(":");
    if (index1 == -1)
      throw new RuntimeException("Bad document identifier: '"+di+"'");
    int index2 = di.indexOf(":", index1 + 1);
    if (index2 == -1)
      return null;
    return new Integer(di.substring(index2 + 1));
  }
  
  /** Find a folder name in a document identifier */
  protected static String extractFolderNameFromDocumentIdentifier(String di)
  {
    int index = di.indexOf(":");
    if (index == -1)
      throw new RuntimeException("Bad document identifier: '"+di+"'");
    return di.substring(0,index);
  }

  /** Find an email ID in a document identifier */
  protected static String extractEmailIDFromDocumentIdentifier(String di)
  {
    int index1 = di.indexOf(":");
    if (index1 == -1)
      throw new RuntimeException("Bad document identifier: '"+di+"'");
    int index2 = di.indexOf(":", index1 + 1);
    if (index2 == -1)
      return di.substring(index1+1);
    return di.substring(index1 + 1, index2);
  }
  
  /** Create a safe folder name (which doesn't contain colons) */
  protected static String makeSafeFolderName(String folderName)
  {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < folderName.length(); i++)
    {
      char x = folderName.charAt(i);
      if (x == '\\')
        sb.append('\\').append('\\');
      else if (x == ':')
        sb.append('\\').append('0');
      else
        sb.append(x);
    }
    return sb.toString();
  }
  
  /** Unpack a safe folder name */
  protected static String unpackSafeFolderName(String packedFolderName)
  {
    StringBuilder sb = new StringBuilder();
    int i = 0;
    while (i < packedFolderName.length())
    {
      char x = packedFolderName.charAt(i++);
      if (x == '\\')
      {
        if (i == packedFolderName.length())
          throw new RuntimeException("Illegal packed folder name: '"+packedFolderName+"'");
        x = packedFolderName.charAt(i++);
        if (x == '\\')
          sb.append('\\');
        else if (x == '0')
          sb.append(':');
        else
          throw new RuntimeException("Illegal packed folder name: '"+packedFolderName+"'");
      }
      else
        sb.append(x);
    }
    return sb.toString();
  }
  
  /** Handle Messaging exceptions in a consistent global manner */
  protected static void handleMessagingException(MessagingException e, String context)
    throws ManifoldCFException, ServiceInterruption
  {
    if (e.getMessage().indexOf("Connection dropped by server?") != -1) {
      final long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Email server is down, retrying: "+e.getMessage(),e,currentTime + 300000L,
        currentTime + 12 * 60 * 60000L,-1,true);
    } else {
      Logging.connectors.error("Email: Error "+context+": "+e.getMessage(),e);
      throw new ManifoldCFException("Error "+context+": "+e.getMessage(),e);
    }
  }
  
  /** Handle IO Exception */
  protected static void handleIOException(IOException e, String context)
    throws ManifoldCFException, ServiceInterruption
  {
    if (e instanceof java.net.SocketTimeoutException)
    {
      Logging.connectors.error("Email: Socket timeout "+context+": "+e.getMessage(),e);
      throw new ManifoldCFException("Socket timeout: "+e.getMessage(),e);
    }
    else if (e instanceof InterruptedIOException)
    {
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),ManifoldCFException.INTERRUPTED);
    }
    else
    {
      Logging.connectors.error("Email: IO error "+context+": "+e.getMessage(),e);
      throw new ManifoldCFException("IO error "+context+": "+e.getMessage(),e);
    }
  }

  /** Class to set up connection.
  */
  protected static class ConnectThread extends Thread
  {
    protected final String server;
    protected final int port;
    protected final String username;
    protected final String password;
    protected final String protocol;
    protected final Properties properties;
    
    // Local session handle
    protected EmailSession session = null;
    protected Throwable exception = null;
    
    public ConnectThread(String server, int port, String username, String password, String protocol, Properties properties)
    {
      this.server = server;
      this.port = port;
      this.username = username;
      this.password = password;
      this.protocol = protocol;
      this.properties = properties;
      setDaemon(true);
    }
    
    public void run()
    {
      try
      {
        session = new EmailSession(server, port, username, password, protocol, properties);
      }
      catch (Throwable e)
      {
        exception = e;
      }
    }
    
    public EmailSession finishUp()
      throws MessagingException, InterruptedException
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
          else if (exception instanceof MessagingException)
            throw (MessagingException)exception;
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
    protected final EmailSession session;
    
    protected Throwable exception = null;
    
    public CloseSessionThread(EmailSession session)
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
      throws MessagingException, InterruptedException
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
          else if (exception instanceof MessagingException)
            throw (MessagingException)exception;
          else
            throw new RuntimeException("Unknown exception type: "+exception.getClass().getName()+": "+exception.getMessage(),exception);
        }
      } catch (InterruptedException e) {
        this.interrupt();
        throw e;
      }
    }
  }

  /** Class to list all folders.
  */
  protected static class ListFoldersThread extends Thread
  {
    protected final EmailSession session;
    
    protected String[] rval = null;
    protected Throwable exception = null;
    
    public ListFoldersThread(EmailSession session)
    {
      this.session = session;
      setDaemon(true);
    }
    
    public void run()
    {
      try
      {
        rval = session.listFolders();
      }
      catch (Throwable e)
      {
        exception = e;
      }
    }
    
    public String[] finishUp()
      throws MessagingException, InterruptedException
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
          else if (exception instanceof MessagingException)
            throw (MessagingException)exception;
          else
            throw new RuntimeException("Unknown exception type: "+exception.getClass().getName()+": "+exception.getMessage(),exception);
        }
        return rval;
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
    protected final EmailSession session;
    
    protected Throwable exception = null;
    
    public CheckConnectionThread(EmailSession session)
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
      throws MessagingException, InterruptedException
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
          else if (exception instanceof MessagingException)
            throw (MessagingException)exception;
          else
            throw new RuntimeException("Unknown exception type: "+exception.getClass().getName()+": "+exception.getMessage(),exception);
        }
      } catch (InterruptedException e) {
        this.interrupt();
        throw e;
      }
    }
  }

  /** Class to open a folder.
  */
  protected static class OpenFolderThread extends Thread
  {
    protected final EmailSession session;
    protected final String folderName;
    
    // Local folder
    protected Folder folder = null;
    protected Throwable exception = null;
    
    public OpenFolderThread(EmailSession session, String folderName)
    {
      this.session = session;
      this.folderName = folderName;
      setDaemon(true);
    }
    
    public void run()
    {
      try
      {
        folder = session.openFolder(folderName);
      }
      catch (Throwable e)
      {
        exception = e;
      }
    }
    
    public Folder finishUp()
      throws MessagingException, InterruptedException
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
          else if (exception instanceof MessagingException)
            throw (MessagingException)exception;
          else
            throw new RuntimeException("Unknown exception type: "+exception.getClass().getName()+": "+exception.getMessage(),exception);
        }
        return folder;
      } catch (InterruptedException e) {
        this.interrupt();
        throw e;
      }
    }
  }
  
  /** Class to close a folder.
  */
  protected static class CloseFolderThread extends Thread
  {
    protected final EmailSession session;
    protected final Folder folder;
    
    // Local folder
    protected Throwable exception = null;
    
    public CloseFolderThread(EmailSession session, Folder folder)
    {
      this.session = session;
      this.folder = folder;
      setDaemon(true);
    }
    
    public void run()
    {
      try
      {
        session.closeFolder(folder);
      }
      catch (Throwable e)
      {
        exception = e;
      }
    }
    
    public void finishUp()
      throws MessagingException, InterruptedException
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
          else if (exception instanceof MessagingException)
            throw (MessagingException)exception;
          else
            throw new RuntimeException("Unknown exception type: "+exception.getClass().getName()+": "+exception.getMessage(),exception);
        }
      } catch (InterruptedException e) {
        this.interrupt();
        throw e;
      }
    }
  }

  /** Class to get all messages from a folder.
  */
  protected static class GetMessagesThread extends Thread
  {
    protected final EmailSession session;
    protected final Folder folder;
    
    // Local messages
    protected Message[] messages = null;
    protected Throwable exception = null;
    
    public GetMessagesThread(EmailSession session, Folder folder)
    {
      this.session = session;
      this.folder = folder;
      setDaemon(true);
    }
    
    public void run()
    {
      try
      {
        messages = session.getMessages(folder);
      }
      catch (Throwable e)
      {
        exception = e;
      }
    }
    
    public Message[] finishUp()
      throws MessagingException, InterruptedException
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
          else if (exception instanceof MessagingException)
            throw (MessagingException)exception;
          else
            throw new RuntimeException("Unknown exception type: "+exception.getClass().getName()+": "+exception.getMessage(),exception);
        }
        return messages;
      } catch (InterruptedException e) {
        this.interrupt();
        throw e;
      }
    }
  }

  /** Class to search for messages in a folder.
  */
  protected static class SearchMessagesThread extends Thread
  {
    protected final EmailSession session;
    protected final Folder folder;
    protected final SearchTerm searchTerm;
    
    // Local messages
    protected Message[] messages = null;
    protected Throwable exception = null;
    
    public SearchMessagesThread(EmailSession session, Folder folder, SearchTerm searchTerm)
    {
      this.session = session;
      this.folder = folder;
      this.searchTerm = searchTerm;
      setDaemon(true);
    }
    
    public void run()
    {
      try
      {
        messages = session.search(folder, searchTerm);
      }
      catch (Throwable e)
      {
        exception = e;
      }
    }
    
    public Message[] finishUp()
      throws MessagingException, InterruptedException
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
          else if (exception instanceof MessagingException)
            throw (MessagingException)exception;
          else
            throw new RuntimeException("Unknown exception type: "+exception.getClass().getName()+": "+exception.getMessage(),exception);
        }
        return messages;
      } catch (InterruptedException e) {
        this.interrupt();
        throw e;
      }
    }
  }

  private static class EmailContent {
    private final String content;
    private final String mimeType;

    public EmailContent(final String content) {
      this.content = content;
      this.mimeType = EmailConfig.MIMETYPE_TEXT_PLAIN;
    }

    public EmailContent(final String content, final String mimetype) {
      this.content = content;
      this.mimeType = mimetype;
    }

    public String getContent() {
      return content;
    }

    public String getMimeType() {
      return mimeType;
    }
  }

}
