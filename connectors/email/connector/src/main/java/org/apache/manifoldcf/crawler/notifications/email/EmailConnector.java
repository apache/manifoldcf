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

package org.apache.manifoldcf.crawler.notifications.email;

import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.util.URLEncoder;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;

import java.io.*;
import java.util.*;
import javax.mail.*;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.search.*;

/**
*/
public class EmailConnector extends org.apache.manifoldcf.crawler.notifications.BaseNotificationConnector {

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

  protected void getSession()
    throws ManifoldCFException, ServiceInterruption {
    if (session == null) {
      
      // Check that all the required parameters are there.
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

  ///////////////////////////////End of Basic Connector Methods////////////////////////////////////////

  //////////////////////////////Start of Notification Connector Method///////////////////////////////////

  //////////////////////////////End of Notification Connector Methods///////////////////////////////////


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
    // Map the parameters
    Map<String, Object> paramMap = new HashMap<String, Object>();

    // Fill in the parameters from each tab
    fillInServerConfigurationMap(paramMap, out, parameters);

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
    Messages.outputResourceWithVelocity(out, locale, "Configuration_Server.html", paramMap);
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
    // MHL
    //tabsArray.add(Messages.getString(locale, "EmailConnector.Metadata"));
    //tabsArray.add(Messages.getString(locale, "EmailConnector.Filter"));
    //Messages.outputResourceWithVelocity(out, locale, "SpecificationHeader.js", paramMap);
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
    // MHL
    //outputFilterTab(out, locale, ds, tabName, connectionSequenceNumber, actualSequenceNumber);
    //outputMetadataTab(out, locale, ds, tabName, connectionSequenceNumber, actualSequenceNumber);
  }

  /**
* Take care of "Metadata" tab.
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
*/

  /**
  * Fill in Velocity context for Metadata tab.
  protected static void fillInMetadataTab(Map<String, Object> paramMap,
    Specification ds) {
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
  */

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

    //String result = processFilterTab(variableContext, ds, connectionSequenceNumber);
    //if (result != null)
    //  return result;
    //result = processMetadataTab(variableContext, ds, connectionSequenceNumber);
    //return result;
    return null;
  }

  /*
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
  */

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
    // MHL
    //fillInFilterTab(paramMap, ds);
    //fillInMetadataTab(paramMap, ds);
    //Messages.outputResourceWithVelocity(out, locale, "SpecificationView.html", paramMap);
  }

  ///////////////////////////////////////End of specification UI///////////////////////////////////////////////
  
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

}