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
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;

import java.io.*;
import java.util.*;
import javax.mail.*;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.search.*;

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

  /**
  * Queue "seed" documents. Seed documents are the starting places for crawling activity. Documents
  * are seeded when this method calls appropriate methods in the passed in ISeedingActivity object.
  * <p/>
  * This method can choose to find repository changes that happen only during the specified time interval.
  * The seeds recorded by this method will be viewed by the framework based on what the
  * getConnectorModel() method returns.
  * <p/>
  * It is not a big problem if the connector chooses to create more seeds than are
  * strictly necessary; it is merely a question of overall work required.
  * <p/>
  * The times passed to this method may be interpreted for greatest efficiency. The time ranges
  * any given job uses with this connector will not overlap, but will proceed starting at 0 and going
  * to the "current time", each time the job is run. For continuous crawling jobs, this method will
  * be called once, when the job starts, and at various periodic intervals as the job executes.
  * <p/>
  * When a job's specification is changed, the framework automatically resets the seeding start time to 0. The
  * seeding start time may also be set to 0 on each job run, depending on the connector model returned by
  * getConnectorModel().
  * <p/>
  * Note that it is always ok to send MORE documents rather than less to this method.
  *
  * @param activities is the interface this method should use to perform whatever framework actions are desired.
  * @param spec is a document specification (that comes from the job).
  * @param startTime is the beginning of the time range to consider, inclusive.
  * @param endTime is the end of the time range to consider, exclusive.
  * @param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  */
  @Override
  public void addSeedDocuments(ISeedingActivity activities,
    DocumentSpecification spec, long startTime, long endTime, int jobMode)
    throws ManifoldCFException, ServiceInterruption {

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
          Message[] messages = findMessages(folder, startTime, endTime, findMap);
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
      findParameterName = pair.getKey().toLowerCase();
      findParameterValue = pair.getValue();
      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("Email: Finding emails where '" + findParameterName +
            "' = '" + findParameterValue + "'");
      SearchTerm searchClause = null;
      if (findParameterName.equals(EmailConfig.EMAIL_SUBJECT)) {
        searchClause = new SubjectTerm(findParameterValue);
      } else if (findParameterName.equals(EmailConfig.EMAIL_FROM)) {
        searchClause = new FromStringTerm(findParameterValue);
      } else if (findParameterName.equals(EmailConfig.EMAIL_TO)) {
        searchClause = new RecipientStringTerm(Message.RecipientType.TO, findParameterValue);
      } else if (findParameterName.equals(EmailConfig.EMAIL_BODY)) {
        searchClause = new BodyTerm(findParameterValue);
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

  /**
  * Get document versions given an array of document identifiers.
  * This method is called for EVERY document that is considered. It is therefore important to perform
  * as little work as possible here.
  * The connector will be connected before this method can be called.
  *
  * @param documentIdentifiers is the array of local document identifiers, as understood by this connector.
  * @param oldVersions is the corresponding array of version strings that have been saved for the document identifiers.
  * A null value indicates that this is a first-time fetch, while an empty string indicates that the previous document
  * had an empty version string.
  * @param activities is the interface this method should use to perform whatever framework actions are desired.
  * @param spec is the current document specification for the current job. If there is a dependency on this
  * specification, then the version string should include the pertinent data, so that reingestion will occur
  * when the specification changes. This is primarily useful for metadata.
  * @param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  * @param usesDefaultAuthority will be true only if the authority in use for these documents is the default one.
  * @return the corresponding version strings, with null in the places where the document no longer exists.
  * Empty version strings indicate that there is no versioning ability for the corresponding document, and the document
  * will always be processed.
  */
  @Override
  public String[] getDocumentVersions(String[] documentIdentifiers, String[] oldVersions, IVersionActivity activities,
    DocumentSpecification spec, int jobMode, boolean usesDefaultAuthority)
    throws ManifoldCFException, ServiceInterruption {

    String[] result = new String[documentIdentifiers.length];
    for (int i = 0; i < documentIdentifiers.length; i++)
    {
      result[i] = "_" + urlTemplate;   // NOT empty; we need to make ManifoldCF understand that this is a document that never will change.
    }
    return result;

  }

  /**
  * Process a set of documents.
  * This is the method that should cause each document to be fetched, processed, and the results either added
  * to the queue of documents for the current job, and/or entered into the incremental ingestion manager.
  * The document specification allows this class to filter what is done based on the job.
  * The connector will be connected before this method can be called.
  *
  * @param documentIdentifiers is the set of document identifiers to process.
  * @param versions is the corresponding document versions to process, as returned by getDocumentVersions() above.
  * The implementation may choose to ignore this parameter and always process the current version.
  * @param activities is the interface this method should use to queue up new document references
  * and ingest documents.
  * @param spec is the document specification.
  * @param scanOnly is an array corresponding to the document identifiers. It is set to true to indicate when the processing
  * should only find other references, and should not actually call the ingestion methods.
  * @param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  */
  @Override
  public void processDocuments(String[] documentIdentifiers, String[] versions, IProcessActivity activities,
    DocumentSpecification spec, boolean[] scanOnly, int jobMode)
    throws ManifoldCFException, ServiceInterruption {
    getSession();
    int i = 0;
    List<String> requiredMetadata = new ArrayList<String>();
    while (i < spec.getChildCount()) {
      SpecificationNode sn = spec.getChild(i++);
      if (sn.getType().equals(EmailConfig.NODE_METADATA)) {
        String metadataAttribute = sn.getAttributeValue(EmailConfig.ATTRIBUTE_NAME);
        requiredMetadata.add(metadataAttribute);
      }
    }
    
    // Keep a cached set of open folders
    Map<String,Folder> openFolders = new HashMap<String,Folder>();
    try {
      i = 0;
      while (i < documentIdentifiers.length) {
        String compositeID = documentIdentifiers[i];
        String version = versions[i];
        String folderName = extractFolderNameFromDocumentIdentifier(compositeID);
        String id = extractEmailIDFromDocumentIdentifier(compositeID);
        try {
          Folder folder = openFolders.get(folderName);
          if (folder == null)
          {
            OpenFolderThread oft = new OpenFolderThread(session, folderName);
            oft.start();
            folder = oft.finishUp();
            openFolders.put(folderName,folder);
          }
          
          long startTime = System.currentTimeMillis();
          InputStream is = null;
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("Email: Processing document identifier '"
              + compositeID + "'");
          SearchTerm messageIDTerm = new MessageIDTerm(id);
          
          SearchMessagesThread smt = new SearchMessagesThread(session, folder, messageIDTerm);
          smt.start();
          Message[] message = smt.finishUp();

          for (Message msg : message) {
            RepositoryDocument rd = new RepositoryDocument();
            Date setDate = msg.getSentDate();
            rd.setFileName(msg.getFileName());
            is = msg.getInputStream();
            rd.setBinary(is, msg.getSize());
            String subject = StringUtils.EMPTY;
            for (String metadata : requiredMetadata) {
              if (metadata.toLowerCase().equals(EmailConfig.EMAIL_TO)) {
                Address[] to = msg.getRecipients(Message.RecipientType.TO);
                String[] toStr = new String[to.length];
                int j = 0;
                for (Address address : to) {
                  toStr[j] = address.toString();
                }
                rd.addField(EmailConfig.EMAIL_TO, toStr);
              } else if (metadata.toLowerCase().equals(EmailConfig.EMAIL_FROM)) {
                Address[] from = msg.getFrom();
                String[] fromStr = new String[from.length];
                int j = 0;
                for (Address address : from) {
                  fromStr[j] = address.toString();
                }
                rd.addField(EmailConfig.EMAIL_TO, fromStr);

              } else if (metadata.toLowerCase().equals(EmailConfig.EMAIL_SUBJECT)) {
                subject = msg.getSubject();
                rd.addField(EmailConfig.EMAIL_SUBJECT, subject);
              } else if (metadata.toLowerCase().equals(EmailConfig.EMAIL_BODY)) {
                Multipart mp = (Multipart) msg.getContent();
                for (int j = 0, n = mp.getCount(); i < n; i++) {
                  Part part = mp.getBodyPart(i);
                  String disposition = part.getDisposition();
                  if ((disposition == null)) {
                    MimeBodyPart mbp = (MimeBodyPart) part;
                    if (mbp.isMimeType(EmailConfig.MIMETYPE_TEXT_PLAIN)) {
                      rd.addField(EmailConfig.EMAIL_BODY, mbp.getContent().toString());
                    } else if (mbp.isMimeType(EmailConfig.MIMETYPE_HTML)) {
                      rd.addField(EmailConfig.EMAIL_BODY, mbp.getContent().toString()); //handle html accordingly. Returns content with html tags
                    }
                  }
                }
              } else if (metadata.toLowerCase().equals(EmailConfig.EMAIL_DATE)) {
                Date sentDate = msg.getSentDate();
                rd.addField(EmailConfig.EMAIL_DATE, sentDate.toString());
              } else if (metadata.toLowerCase().equals(EmailConfig.EMAIL_ATTACHMENT_ENCODING)) {
                Multipart mp = (Multipart) msg.getContent();
                if (mp != null) {
                  String[] encoding = new String[mp.getCount()];
                  for (int k = 0, n = mp.getCount(); i < n; i++) {
                    Part part = mp.getBodyPart(i);
                    String disposition = part.getDisposition();
                    if ((disposition != null) &&
                        ((disposition.equals(Part.ATTACHMENT) ||
                            (disposition.equals(Part.INLINE))))) {
                      encoding[k] = part.getFileName().split("\\?")[1];

                    }
                  }
                  rd.addField(EmailConfig.ENCODING_FIELD, encoding);
                }
              } else if (metadata.toLowerCase().equals(EmailConfig.EMAIL_ATTACHMENT_MIMETYPE)) {
                Multipart mp = (Multipart) msg.getContent();
                String[] MIMEType = new String[mp.getCount()];
                for (int k = 0, n = mp.getCount(); i < n; i++) {
                  Part part = mp.getBodyPart(i);
                  String disposition = part.getDisposition();
                  if ((disposition != null) &&
                      ((disposition.equals(Part.ATTACHMENT) ||
                          (disposition.equals(Part.INLINE))))) {
                    MIMEType[k] = part.getContentType();

                  }
                }
                rd.addField(EmailConfig.MIMETYPE_FIELD, MIMEType);
              }
            }
            String documentURI = makeDocumentURI(urlTemplate, folderName, id);
            activities.ingestDocument(id, version, documentURI, rd);

          }
        } catch (InterruptedException e) {
          throw new ManifoldCFException(e.getMessage(), ManifoldCFException.INTERRUPTED);
        } catch (MessagingException e) {
          handleMessagingException(e, "processing email");
        } catch (IOException e) {
          handleIOException(e, "processing email");
          throw new ManifoldCFException(e.getMessage(), e);
        }
        
        i++;
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

    if (urlTemplate == null)
      urlTemplate = "http://sampleserver/$(FOLDERNAME)?id=$(MESSAGEID)";

    paramMap.put("URL", urlTemplate);
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

  /**
  * Output the specification header section.
  * This method is called in the head section of a job page which has selected a repository connection of the
  * current type. Its purpose is to add the required tabs to the list, and to output any javascript methods
  * that might be needed by the job editing HTML.
  * The connector will be connected before this method can be called.
  *
  * @param out is the output to which any HTML should be sent.
  * @param locale is the desired locale.
  * @param ds is the current document specification for this job.
  * @param tabsArray is an array of tab names. Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale,
    DocumentSpecification ds, List<String> tabsArray)
    throws ManifoldCFException, IOException {
    // Add the tabs
    tabsArray.add(Messages.getString(locale, "EmailConnector.Metadata"));
    tabsArray.add(Messages.getString(locale, "EmailConnector.Filter"));
    Messages.outputResourceWithVelocity(out, locale, "SpecificationHeader.js", null);
  }

  /**
  * Output the specification body section.
  * This method is called in the body section of a job page which has selected a repository connection of the
  * current type. Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate
  * <html>, <body>, and <form> tags. The name of the form is always "editjob".
  * The connector will be connected before this method can be called.
  *
  * @param out is the output to which any HTML should be sent.
  * @param locale is the desired locale.
  * @param ds is the current document specification for this job.
  * @param tabName is the current tab name.
  */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale,
    DocumentSpecification ds, String tabName)
    throws ManifoldCFException, IOException {
    outputFilterTab(out, locale, ds, tabName);
    outputMetadataTab(out, locale, ds, tabName);
  }

  /**
* Take care of "Metadata" tab.
*/
  protected void outputMetadataTab(IHTTPOutput out, Locale locale,
                   DocumentSpecification ds, String tabName)
      throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("TabName", tabName);
    fillInMetadataTab(paramMap, ds);
    fillInMetadataAttributes(paramMap);
    Messages.outputResourceWithVelocity(out, locale, "Specification_Metadata.html", paramMap);
  }

  /**
  * Fill in Velocity context for Metadata tab.
  */
  protected static void fillInMetadataTab(Map<String, Object> paramMap,
    DocumentSpecification ds) {
    Set<String> metadataSelections = new HashSet<String>();
    int i = 0;
    while (i < ds.getChildCount()) {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(EmailConfig.NODE_METADATA)) {
        String metadataName = sn.getAttributeValue(EmailConfig.ATTRIBUTE_NAME);
        metadataSelections.add(metadataName);
      }
    }
    paramMap.put("METADATASELECTIONS", metadataSelections);
  }

  /**
  * Fill in Velocity context with data to permit attribute selection.
  */
  protected void fillInMetadataAttributes(Map<String, Object> paramMap) {
    String[] matchNames = EmailConfig.BASIC_METADATA;
    paramMap.put("METADATAATTRIBUTES", matchNames);
  }

  protected void outputFilterTab(IHTTPOutput out, Locale locale,
    DocumentSpecification ds, String tabName)
    throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("TabName", tabName);
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
    DocumentSpecification ds) {
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

  /**
  * Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form
  * data for a connection has been posted. Its purpose is to gather form information and modify the
  * document specification accordingly. The name of the posted form is always "editjob".
  * The connector will be connected before this method can be called.
  *
  * @param variableContext contains the post data, including binary file-upload information.
  * @param ds is the current document specification for this job.
  * @return null if all is well, or a string error message if there is an error that should prevent saving of
  * the job (and cause a redirection to an error page).
  */
  @Override
  public String processSpecificationPost(IPostParameters variableContext, DocumentSpecification ds)
      throws ManifoldCFException {

    String result = processFilterTab(variableContext, ds);
    if (result != null)
      return result;
    result = processMetadataTab(variableContext, ds);
    return result;
  }


  protected String processFilterTab(IPostParameters variableContext, DocumentSpecification ds)
      throws ManifoldCFException {

    String findCountString = variableContext.getParameter("findcount");
    if (findCountString != null) {
      int findCount = Integer.parseInt(findCountString);
      
      // Remove old find parameter document specification information
      removeNodes(ds, EmailConfig.NODE_FILTER);

      int i = 0;
      while (i < findCount) {
        String suffix = "_" + Integer.toString(i++);
        // Only add the name/value if the item was not deleted.
        String findParameterOp = variableContext.getParameter("findop" + suffix);
        if (findParameterOp == null || !findParameterOp.equals("Delete")) {
          String findParameterName = variableContext.getParameter("findname" + suffix);
          String findParameterValue = variableContext.getParameter("findvalue" + suffix);
          addFindParameterNode(ds, findParameterName, findParameterValue);
        }
      }

      String operation = variableContext.getParameter("findop");
      if (operation != null && operation.equals("Add")) {
        String findParameterName = variableContext.getParameter("findname");
        String findParameterValue = variableContext.getParameter("findvalue");
        addFindParameterNode(ds, findParameterName, findParameterValue);
      }
    }
    
    String[] folders = variableContext.getParameterValues("folders");
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


  protected String processMetadataTab(IPostParameters variableContext, DocumentSpecification ds)
      throws ManifoldCFException {
    // Remove old included metadata nodes
    removeNodes(ds, EmailConfig.NODE_METADATA);

    // Get the posted metadata values
    String[] metadataNames = variableContext.getParameterValues("metadata");
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

  /**
  * View specification.
  * This method is called in the body section of a job's view page. Its purpose is to present the document
  * specification information to the user. The coder can presume that the HTML that is output from
  * this configuration will be within appropriate <html> and <body> tags.
  * The connector will be connected before this method can be called.
  *
  * @param out is the output to which any HTML should be sent.
  * @param locale is the desired locale.
  * @param ds is the current document specification for this job.
*/
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, DocumentSpecification ds)
      throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();
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
    try {
      // First, URL encode folder name and id
      String encodedFolderName = java.net.URLEncoder.encode(folderName, "utf-8");
      String encodedId = java.net.URLEncoder.encode(id, "utf-8");
      // The template is already URL encoded, except for the substitution points
      Map<String,String> subsMap = new HashMap<String,String>();
      subsMap.put("FOLDERNAME", encodedFolderName);
      subsMap.put("MESSAGEID", encodedId);
      return substitute(urlTemplate, subsMap);
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("No utf-8 encoder found: "+e.getMessage(), e);
    }
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

  protected static void removeNodes(DocumentSpecification ds,
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

  protected static void addIncludedMetadataNode(DocumentSpecification ds,
                          String metadataName) {
    // Build the proper node
    SpecificationNode sn = new SpecificationNode(EmailConfig.NODE_METADATA);
    sn.setAttribute(EmailConfig.ATTRIBUTE_NAME, metadataName);
    // Add to the end
    ds.addChild(ds.getChildCount(), sn);
  }

  protected static void addFindParameterNode(DocumentSpecification ds, String findParameterName, String findParameterValue) {
    SpecificationNode sn = new SpecificationNode(EmailConfig.NODE_FILTER);
    sn.setAttribute(EmailConfig.ATTRIBUTE_NAME, findParameterName);
    sn.setAttribute(EmailConfig.ATTRIBUTE_VALUE, findParameterValue);
    // Add to the end
    ds.addChild(ds.getChildCount(), sn);
  }

  protected static void addFolderNode(DocumentSpecification ds, String folderName)
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
    int index = di.indexOf(":");
    if (index == -1)
      throw new RuntimeException("Bad document identifier: '"+di+"'");
    return di.substring(index+1);
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
    Logging.connectors.error("Email: Error "+context+": "+e.getMessage(),e);
    throw new ManifoldCFException("Error "+context+": "+e.getMessage(),e);
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

}