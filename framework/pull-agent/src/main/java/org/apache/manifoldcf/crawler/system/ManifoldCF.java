/* $Id: ManifoldCF.java 996524 2010-09-13 13:38:01Z kwright $ */

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
package org.apache.manifoldcf.crawler.system;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;

import java.io.*;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.*;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class ManifoldCF extends org.apache.manifoldcf.agents.system.ManifoldCF
{
  public static final String _rcsid = "@(#)$Id: ManifoldCF.java 996524 2010-09-13 13:38:01Z kwright $";

  // Initialization flag.
  protected static boolean crawlerInitialized = false;

  // Properties
  protected static final String workerThreadCountProperty = "org.apache.manifoldcf.crawler.threads";
  protected static final String deleteThreadCountProperty = "org.apache.manifoldcf.crawler.deletethreads";
  protected static final String cleanupThreadCountProperty = "org.apache.manifoldcf.crawler.cleanupthreads";
  protected static final String expireThreadCountProperty = "org.apache.manifoldcf.crawler.expirethreads";
  protected static final String lowWaterFactorProperty = "org.apache.manifoldcf.crawler.lowwaterfactor";
  protected static final String stuffAmtFactorProperty = "org.apache.manifoldcf.crawler.stuffamountfactor";
  protected static final String connectorsConfigurationFileProperty = "org.apache.manifoldcf.connectorsconfigurationfile";
  protected static final String databaseSuperuserNameProperty = "org.apache.manifoldcf.dbsuperusername";
  protected static final String databaseSuperuserPasswordProperty = "org.apache.manifoldcf.dbsuperuserpassword";

  
  /** Initialize environment.
  */
  public static void initializeEnvironment(IThreadContext tc)
    throws ManifoldCFException
  {
    synchronized (initializeFlagLock)
    {
      org.apache.manifoldcf.agents.system.ManifoldCF.initializeEnvironment(tc);
      org.apache.manifoldcf.authorities.system.ManifoldCF.localInitialize(tc);
      org.apache.manifoldcf.crawler.system.ManifoldCF.localInitialize(tc);
    }
  }

  public static void cleanUpEnvironment(IThreadContext tc)
  {
    synchronized (initializeFlagLock)
    {
      org.apache.manifoldcf.authorities.system.ManifoldCF.localCleanup(tc);
      org.apache.manifoldcf.crawler.system.ManifoldCF.localCleanup(tc);
      org.apache.manifoldcf.agents.system.ManifoldCF.cleanUpEnvironment(tc);
    }
  }
  
  public static void localInitialize(IThreadContext tc)
    throws ManifoldCFException
  {
    synchronized (initializeFlagLock)
    {
      
      if (crawlerInitialized)
        return;
      
      Logging.initializeLoggers();
      Logging.setLogLevels(tc);
      crawlerInitialized = true;
    }
  }
  
  public static void localCleanup(IThreadContext tc)
  {
    try
    {
      RepositoryConnectorPoolFactory.make(tc).closeAllConnectors();
    }
    catch (ManifoldCFException e)
    {
      if (Logging.root != null)
        Logging.root.warn("Exception tossed on repository connector pool cleanup: "+e.getMessage(),e);
    }
    try
    {
      NotificationConnectorPoolFactory.make(tc).closeAllConnectors();
    }
    catch (ManifoldCFException e)
    {
      if (Logging.root != null)
        Logging.root.warn("Exception tossed on notification connector pool cleanup: "+e.getMessage(),e);
    }
  }
  
  /** Create system database using superuser properties from properties.xml.
  */
  public static void createSystemDatabase(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // Get the specified superuser name and password, in case this isn't Derby we're using
    String superuserName = LockManagerFactory.getStringProperty(threadContext, databaseSuperuserNameProperty, "");
    String superuserPassword = LockManagerFactory.getPossiblyObfuscatedStringProperty(threadContext, databaseSuperuserPasswordProperty, "");
    createSystemDatabase(threadContext,superuserName,superuserPassword);
  }
  
  /** Register this agent */
  public static void registerThisAgent(IThreadContext tc)
    throws ManifoldCFException
  {
    // Register 
    IAgentManager agentMgr = AgentManagerFactory.make(tc);
    agentMgr.registerAgent("org.apache.manifoldcf.crawler.system.CrawlerAgent");
  }

  /** Register or re-register all connectors, based on a connectors.xml file.
  */
  public static void reregisterAllConnectors(IThreadContext tc)
    throws ManifoldCFException
  {
    // Read connectors configuration file (to figure out what we need to register)
    File connectorConfigFile = getFileProperty(connectorsConfigurationFileProperty);
    Connectors c = readConnectorDeclarations(connectorConfigFile);
    
    // Unregister all the connectors we don't want.
    unregisterAllConnectors(tc,c);

    // Register (or update) all connectors specified by connectors.xml
    registerConnectors(tc,c);
  }
  
  /** Read connectors configuration file.
  */
  public static Connectors readConnectorDeclarations(File connectorConfigFile)
    throws ManifoldCFException
  {
    Connectors c = null;
    if (connectorConfigFile != null)
    {
      try
      {
        // Open the file, read it, and attempt to do the connector registrations
        InputStream is = new FileInputStream(connectorConfigFile);
        try
        {
          c = new Connectors(is);
        }
        finally
        {
          is.close();
        }
      }
      catch (FileNotFoundException e)
      {
        throw new ManifoldCFException("Couldn't find connector configuration file: "+e.getMessage(),e);
      }
      catch (IOException e)
      {
        throw new ManifoldCFException("Error reading connector configuration file: "+e.getMessage(),e);
      }
    }
    return c;
  }

  /** Unregister all connectors.
  */
  public static void unregisterAllConnectors(IThreadContext tc)
    throws ManifoldCFException
  {
    unregisterAllConnectors(tc,null);
  }

  // Connectors configuration file
  protected static final String NODE_AUTHORIZATIONDOMAIN = "authorizationdomain";
  protected static final String NODE_OUTPUTCONNECTOR = "outputconnector";
  protected static final String NODE_TRANSFORMATIONCONNECTOR = "transformationconnector";
  protected static final String NODE_MAPPINGCONNECTOR = "mappingconnector";
  protected static final String NODE_AUTHORITYCONNECTOR = "authorityconnector";
  protected static final String NODE_NOTIFICATIONCONNECTOR = "notificationconnector";
  protected static final String NODE_REPOSITORYCONNECTOR = "repositoryconnector";
  protected static final String ATTRIBUTE_NAME = "name";
  protected static final String ATTRIBUTE_CLASS = "class";
  protected static final String ATTRIBUTE_DOMAIN = "domain";
  
  /** Unregister all connectors which don't match a specified connector list.
  */
  public static void unregisterAllConnectors(IThreadContext tc, Connectors c)
    throws ManifoldCFException
  {
    // Create a map of class name and description, so we can compare what we can find
    // against what we want.
    Map<String,String> desiredOutputConnectors = new HashMap<String,String>();
    Map<String,String> desiredTransformationConnectors = new HashMap<String,String>();
    Map<String,String> desiredMappingConnectors = new HashMap<String,String>();
    Map<String,String> desiredAuthorityConnectors = new HashMap<String,String>();
    Map<String,String> desiredNotificationConnectors = new HashMap<String,String>();
    Map<String,String> desiredRepositoryConnectors = new HashMap<String,String>();

    Map<String,String> desiredDomains = new HashMap<String,String>();

    if (c != null)
    {
      for (int i = 0; i < c.getChildCount(); i++)
      {
        ConfigurationNode cn = c.findChild(i);
        if (cn.getType().equals(NODE_AUTHORIZATIONDOMAIN))
        {
          String domainName = cn.getAttributeValue(ATTRIBUTE_DOMAIN);
          String name = cn.getAttributeValue(ATTRIBUTE_NAME);
          desiredDomains.put(domainName,name);
        }
        else if (cn.getType().equals(NODE_OUTPUTCONNECTOR))
        {
          String name = cn.getAttributeValue(ATTRIBUTE_NAME);
          String className = cn.getAttributeValue(ATTRIBUTE_CLASS);
          desiredOutputConnectors.put(className,name);
        }
        else if (cn.getType().equals(NODE_TRANSFORMATIONCONNECTOR))
        {
          String name = cn.getAttributeValue(ATTRIBUTE_NAME);
          String className = cn.getAttributeValue(ATTRIBUTE_CLASS);
          desiredTransformationConnectors.put(className,name);
        }
        else if (cn.getType().equals(NODE_MAPPINGCONNECTOR))
        {
          String name = cn.getAttributeValue(ATTRIBUTE_NAME);
          String className = cn.getAttributeValue(ATTRIBUTE_CLASS);
          desiredMappingConnectors.put(className,name);
        }
        else if (cn.getType().equals(NODE_AUTHORITYCONNECTOR))
        {
          String name = cn.getAttributeValue(ATTRIBUTE_NAME);
          String className = cn.getAttributeValue(ATTRIBUTE_CLASS);
          desiredAuthorityConnectors.put(className,name);
        }
        else if (cn.getType().equals(NODE_NOTIFICATIONCONNECTOR))
        {
          String name = cn.getAttributeValue(ATTRIBUTE_NAME);
          String className = cn.getAttributeValue(ATTRIBUTE_CLASS);
          desiredNotificationConnectors.put(className,name);
        }
        else if (cn.getType().equals(NODE_REPOSITORYCONNECTOR))
        {
          String name = cn.getAttributeValue(ATTRIBUTE_NAME);
          String className = cn.getAttributeValue(ATTRIBUTE_CLASS);
          desiredRepositoryConnectors.put(className,name);
        }
      }
    }

    // Grab a database handle, so we can use transactions later.
    IDBInterface database = DBInterfaceFactory.make(tc,
      ManifoldCF.getMasterDatabaseName(),
      ManifoldCF.getMasterDatabaseUsername(),
      ManifoldCF.getMasterDatabasePassword());

    // Domains...
    {
      IAuthorizationDomainManager mgr = AuthorizationDomainManagerFactory.make(tc);
      IResultSet domains = mgr.getDomains();
      for (int i = 0; i < domains.getRowCount(); i++)
      {
        IResultRow row = domains.getRow(i);
        String domainName = (String)row.getValue("domainname");
        String description = (String)row.getValue("description");
        if (desiredDomains.get(domainName) == null || !desiredDomains.get(domainName).equals(description))
        {
          mgr.unregisterDomain(domainName);
        }
      }
      System.err.println("Successfully unregistered all domains");
    }
    
    // Output connectors...
    {
      IOutputConnectorManager mgr = OutputConnectorManagerFactory.make(tc);
      IOutputConnectionManager connManager = OutputConnectionManagerFactory.make(tc);
      IResultSet classNames = mgr.getConnectors();
      int i = 0;
      while (i < classNames.getRowCount())
      {
        IResultRow row = classNames.getRow(i++);
        String className = (String)row.getValue("classname");
        String description = (String)row.getValue("description");
        if (desiredOutputConnectors.get(className) == null || !desiredOutputConnectors.get(className).equals(description))
        {
          // Deregistration should be done in a transaction
          database.beginTransaction();
          try
          {
            // Find the connection names that come with this class
            String[] connectionNames = connManager.findConnectionsForConnector(className);
            // For all connection names, notify all agents of the deregistration
            AgentManagerFactory.noteOutputConnectorDeregistration(tc,connectionNames);
            // Now that all jobs have been placed into an appropriate state, actually do the deregistration itself.
            mgr.unregisterConnector(className);
          }
          catch (ManifoldCFException e)
          {
            database.signalRollback();
            throw e;
          }
          catch (Error e)
          {
            database.signalRollback();
            throw e;
          }
          finally
          {
            database.endTransaction();
          }
        }
      }
      System.err.println("Successfully unregistered all output connectors");
    }

    // Output connectors...
    {
      ITransformationConnectorManager mgr = TransformationConnectorManagerFactory.make(tc);
      ITransformationConnectionManager connManager = TransformationConnectionManagerFactory.make(tc);
      IResultSet classNames = mgr.getConnectors();
      int i = 0;
      while (i < classNames.getRowCount())
      {
        IResultRow row = classNames.getRow(i++);
        String className = (String)row.getValue("classname");
        String description = (String)row.getValue("description");
        if (desiredTransformationConnectors.get(className) == null || !desiredTransformationConnectors.get(className).equals(description))
        {
          // Deregistration should be done in a transaction
          database.beginTransaction();
          try
          {
            // Find the connection names that come with this class
            String[] connectionNames = connManager.findConnectionsForConnector(className);
            // For all connection names, notify all agents of the deregistration
            AgentManagerFactory.noteTransformationConnectorDeregistration(tc,connectionNames);
            // Now that all jobs have been placed into an appropriate state, actually do the deregistration itself.
            mgr.unregisterConnector(className);
          }
          catch (ManifoldCFException e)
          {
            database.signalRollback();
            throw e;
          }
          catch (Error e)
          {
            database.signalRollback();
            throw e;
          }
          finally
          {
            database.endTransaction();
          }
        }
      }
      System.err.println("Successfully unregistered all transformation connectors");
    }

    // Mapping connectors...
    {
      IMappingConnectorManager mgr = MappingConnectorManagerFactory.make(tc);
      IResultSet classNames = mgr.getConnectors();
      int i = 0;
      while (i < classNames.getRowCount())
      {
        IResultRow row = classNames.getRow(i++);
        String className = (String)row.getValue("classname");
        String description = (String)row.getValue("description");
        if (desiredMappingConnectors.get(className) == null || !desiredMappingConnectors.get(className).equals(description))
        {
          mgr.unregisterConnector(className);
        }
      }
      System.err.println("Successfully unregistered all mapping connectors");
    }

    // Authority connectors...
    {
      IAuthorityConnectorManager mgr = AuthorityConnectorManagerFactory.make(tc);
      IResultSet classNames = mgr.getConnectors();
      int i = 0;
      while (i < classNames.getRowCount())
      {
        IResultRow row = classNames.getRow(i++);
        String className = (String)row.getValue("classname");
        String description = (String)row.getValue("description");
        if (desiredAuthorityConnectors.get(className) == null || !desiredAuthorityConnectors.get(className).equals(description))
        {
          mgr.unregisterConnector(className);
        }
      }
      System.err.println("Successfully unregistered all authority connectors");
    }
      
    // Notification connectors...
    {
      INotificationConnectorManager mgr = NotificationConnectorManagerFactory.make(tc);
      IJobManager jobManager = JobManagerFactory.make(tc);
      INotificationConnectionManager connManager = NotificationConnectionManagerFactory.make(tc);
      IResultSet classNames = mgr.getConnectors();
      int i = 0;
      while (i < classNames.getRowCount())
      {
        IResultRow row = classNames.getRow(i++);
        String className = (String)row.getValue("classname");
        String description = (String)row.getValue("description");
        if (desiredNotificationConnectors.get(className) == null || !desiredNotificationConnectors.get(className).equals(description))
        {
          // Deregistration should be done in a transaction
          database.beginTransaction();
          try
          {
            // Find the connection names that come with this class
            String[] connectionNames = connManager.findConnectionsForConnector(className);
            // For each connection name, modify the jobs to note that the connector is no longer installed
            jobManager.noteNotificationConnectorDeregistration(connectionNames);
            // Now that all jobs have been placed into an appropriate state, actually do the deregistration itself.
            mgr.unregisterConnector(className);
          }
          catch (ManifoldCFException e)
          {
            database.signalRollback();
            throw e;
          }
          catch (Error e)
          {
            database.signalRollback();
            throw e;
          }
          finally
          {
            database.endTransaction();
          }
        }
      }
    }
    
    // Repository connectors...
    {
      IConnectorManager mgr = ConnectorManagerFactory.make(tc);
      IJobManager jobManager = JobManagerFactory.make(tc);
      IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(tc);
      IResultSet classNames = mgr.getConnectors();
      int i = 0;
      while (i < classNames.getRowCount())
      {
        IResultRow row = classNames.getRow(i++);
        String className = (String)row.getValue("classname");
        String description = (String)row.getValue("description");
        if (desiredRepositoryConnectors.get(className) == null || !desiredRepositoryConnectors.get(className).equals(description))
        {
          // Deregistration should be done in a transaction
          database.beginTransaction();
          try
          {
            // Find the connection names that come with this class
            String[] connectionNames = connManager.findConnectionsForConnector(className);
            // For each connection name, modify the jobs to note that the connector is no longer installed
            jobManager.noteConnectorDeregistration(connectionNames);
            // Now that all jobs have been placed into an appropriate state, actually do the deregistration itself.
            mgr.unregisterConnector(className);
          }
          catch (ManifoldCFException e)
          {
            database.signalRollback();
            throw e;
          }
          catch (Error e)
          {
            database.signalRollback();
            throw e;
          }
          finally
          {
            database.endTransaction();
          }
        }
      }
      System.err.println("Successfully unregistered all repository connectors");
    }
  }


  /** Register all connectors as specified by a Connectors structure, usually read from the connectors.xml file.
  */
  public static void registerConnectors(IThreadContext tc, Connectors c)
    throws ManifoldCFException
  {
    if (c != null)
    {
      // Grab a database handle, so we can use transactions later.
      IDBInterface database = DBInterfaceFactory.make(tc,
        ManifoldCF.getMasterDatabaseName(),
        ManifoldCF.getMasterDatabaseUsername(),
        ManifoldCF.getMasterDatabasePassword());
        
      // Other code will go here to discover and register various connectors that exist in the classpath
      int i = 0;
      while (i < c.getChildCount())
      {
        ConfigurationNode cn = c.findChild(i++);
        if (cn.getType().equals(NODE_AUTHORIZATIONDOMAIN))
        {
          String domainName = cn.getAttributeValue(ATTRIBUTE_DOMAIN);
          String name = cn.getAttributeValue(ATTRIBUTE_NAME);
          IAuthorizationDomainManager mgr = AuthorizationDomainManagerFactory.make(tc);
          mgr.registerDomain(name,domainName);
        }
        else if (cn.getType().equals(NODE_OUTPUTCONNECTOR))
        {
          String name = cn.getAttributeValue(ATTRIBUTE_NAME);
          String className = cn.getAttributeValue(ATTRIBUTE_CLASS);
          IOutputConnectorManager mgr = OutputConnectorManagerFactory.make(tc);
          IOutputConnectionManager connManager = OutputConnectionManagerFactory.make(tc);
          // Registration should be done in a transaction
          database.beginTransaction();
          try
          {
            // First, register connector
            mgr.registerConnector(name,className);
            // Then, signal to all jobs that might depend on this connector that they can switch state
            // Find the connection names that come with this class
            String[] connectionNames = connManager.findConnectionsForConnector(className);
            // For all connection names, notify all agents of the registration
            AgentManagerFactory.noteOutputConnectorRegistration(tc,connectionNames);
          }
          catch (ManifoldCFException e)
          {
            database.signalRollback();
            throw e;
          }
          catch (Error e)
          {
            database.signalRollback();
            throw e;
          }
          finally
          {
            database.endTransaction();
          }
          System.err.println("Successfully registered output connector '"+className+"'");
        }
        else if (cn.getType().equals(NODE_TRANSFORMATIONCONNECTOR))
        {
          String name = cn.getAttributeValue(ATTRIBUTE_NAME);
          String className = cn.getAttributeValue(ATTRIBUTE_CLASS);
          ITransformationConnectorManager mgr = TransformationConnectorManagerFactory.make(tc);
          ITransformationConnectionManager connManager = TransformationConnectionManagerFactory.make(tc);
          // Registration should be done in a transaction
          database.beginTransaction();
          try
          {
            // First, register connector
            mgr.registerConnector(name,className);
            // Then, signal to all jobs that might depend on this connector that they can switch state
            // Find the connection names that come with this class
            String[] connectionNames = connManager.findConnectionsForConnector(className);
            // For all connection names, notify all agents of the registration
            AgentManagerFactory.noteTransformationConnectorRegistration(tc,connectionNames);
          }
          catch (ManifoldCFException e)
          {
            database.signalRollback();
            throw e;
          }
          catch (Error e)
          {
            database.signalRollback();
            throw e;
          }
          finally
          {
            database.endTransaction();
          }
          System.err.println("Successfully registered transformation connector '"+className+"'");
        }
        else if (cn.getType().equals(NODE_AUTHORITYCONNECTOR))
        {
          String name = cn.getAttributeValue(ATTRIBUTE_NAME);
          String className = cn.getAttributeValue(ATTRIBUTE_CLASS);
          IAuthorityConnectorManager mgr = AuthorityConnectorManagerFactory.make(tc);
          mgr.registerConnector(name,className);
          System.err.println("Successfully registered authority connector '"+className+"'");
        }
        else if (cn.getType().equals(NODE_MAPPINGCONNECTOR))
        {
          String name = cn.getAttributeValue(ATTRIBUTE_NAME);
          String className = cn.getAttributeValue(ATTRIBUTE_CLASS);
          IMappingConnectorManager mgr = MappingConnectorManagerFactory.make(tc);
          mgr.registerConnector(name,className);
          System.err.println("Successfully registered mapping connector '"+className+"'");
        }
        else if (cn.getType().equals(NODE_NOTIFICATIONCONNECTOR))
        {
          String name = cn.getAttributeValue(ATTRIBUTE_NAME);
          String className = cn.getAttributeValue(ATTRIBUTE_CLASS);
          INotificationConnectorManager mgr = NotificationConnectorManagerFactory.make(tc);
          IJobManager jobManager = JobManagerFactory.make(tc);
          INotificationConnectionManager connManager = NotificationConnectionManagerFactory.make(tc);
          // Deregistration should be done in a transaction
          database.beginTransaction();
          try
          {
            // First, register connector
            mgr.registerConnector(name,className);
            // Then, signal to all jobs that might depend on this connector that they can switch state
            // Find the connection names that come with this class
            String[] connectionNames = connManager.findConnectionsForConnector(className);
            // For each connection name, modify the jobs to note that the connector is now installed
            jobManager.noteNotificationConnectorRegistration(connectionNames);
          }
          catch (ManifoldCFException e)
          {
            database.signalRollback();
            throw e;
          }
          catch (Error e)
          {
            database.signalRollback();
            throw e;
          }
          finally
          {
            database.endTransaction();
          }
          System.err.println("Successfully registered notification connector '"+className+"'");
        }
        else if (cn.getType().equals(NODE_REPOSITORYCONNECTOR))
        {
          String name = cn.getAttributeValue(ATTRIBUTE_NAME);
          String className = cn.getAttributeValue(ATTRIBUTE_CLASS);
          IConnectorManager mgr = ConnectorManagerFactory.make(tc);
          IJobManager jobManager = JobManagerFactory.make(tc);
          IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(tc);
          // Deregistration should be done in a transaction
          database.beginTransaction();
          try
          {
            // First, register connector
            mgr.registerConnector(name,className);
            // Then, signal to all jobs that might depend on this connector that they can switch state
            // Find the connection names that come with this class
            String[] connectionNames = connManager.findConnectionsForConnector(className);
            // For each connection name, modify the jobs to note that the connector is now installed
            jobManager.noteConnectorRegistration(connectionNames);
          }
          catch (ManifoldCFException e)
          {
            database.signalRollback();
            throw e;
          }
          catch (Error e)
          {
            database.signalRollback();
            throw e;
          }
          finally
          {
            database.endTransaction();
          }
          System.err.println("Successfully registered repository connector '"+className+"'");
        }
        else
          throw new ManifoldCFException("Unrecognized connectors node type '"+cn.getType()+"'");
      }
    }
  }

  /** Install all the crawler system tables.
  *@param threadcontext is the thread context.
  */
  public static void installSystemTables(IThreadContext threadcontext)
    throws ManifoldCFException
  {
    IConnectorManager repConnMgr = ConnectorManagerFactory.make(threadcontext);
    IRepositoryConnectionManager repCon = RepositoryConnectionManagerFactory.make(threadcontext);
    INotificationConnectorManager notConnMgr = NotificationConnectorManagerFactory.make(threadcontext);
    INotificationConnectionManager notCon = NotificationConnectionManagerFactory.make(threadcontext);
    IJobManager jobManager = JobManagerFactory.make(threadcontext);
    IBinManager binManager = BinManagerFactory.make(threadcontext);
    org.apache.manifoldcf.authorities.system.ManifoldCF.installSystemTables(threadcontext);
    repConnMgr.install();
    repCon.install();
    notConnMgr.install();
    notCon.install();
    jobManager.install();
    binManager.install();
  }

  /** Uninstall all the crawler system tables.
  *@param threadcontext is the thread context.
  */
  public static void deinstallSystemTables(IThreadContext threadcontext)
    throws ManifoldCFException
  {
    IConnectorManager repConnMgr = ConnectorManagerFactory.make(threadcontext);
    IRepositoryConnectionManager repCon = RepositoryConnectionManagerFactory.make(threadcontext);
    INotificationConnectorManager notConnMgr = NotificationConnectorManagerFactory.make(threadcontext);
    INotificationConnectionManager notCon = NotificationConnectionManagerFactory.make(threadcontext);
    IJobManager jobManager = JobManagerFactory.make(threadcontext);
    IBinManager binManager = BinManagerFactory.make(threadcontext);
    binManager.deinstall();
    jobManager.deinstall();
    notCon.deinstall();
    notConnMgr.deinstall();
    repCon.deinstall();
    repConnMgr.deinstall();
    org.apache.manifoldcf.authorities.system.ManifoldCF.deinstallSystemTables(threadcontext);
  }

  /** Atomically export the crawler configuration */
  public static void exportConfiguration(IThreadContext threadContext, String exportFilename, String passCode)
    throws ManifoldCFException
  {
    // The basic idea here is that we open a zip stream, into which we dump all the pertinent information in a transactionally-consistent manner.
    // First, we need a database handle...
    IDBInterface database = DBInterfaceFactory.make(threadContext,
      ManifoldCF.getMasterDatabaseName(),
      ManifoldCF.getMasterDatabaseUsername(),
      ManifoldCF.getMasterDatabasePassword());
    // Also create the following managers, which will handle the actual details of writing configuration data
    IOutputConnectionManager outputManager = OutputConnectionManagerFactory.make(threadContext);
    ITransformationConnectionManager transManager = TransformationConnectionManagerFactory.make(threadContext);
    IAuthorityGroupManager groupManager = AuthorityGroupManagerFactory.make(threadContext);
    IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(threadContext);
    INotificationConnectionManager notificationConnManager = NotificationConnectionManagerFactory.make(threadContext);
    IMappingConnectionManager mappingManager = MappingConnectionManagerFactory.make(threadContext);
    IAuthorityConnectionManager authManager = AuthorityConnectionManagerFactory.make(threadContext);
    IJobManager jobManager = JobManagerFactory.make(threadContext);

    File outputFile = new File(exportFilename);

    // Create a zip output stream, which is what we will use as a mechanism for handling the output data
    try
    {
      
      OutputStream os = new FileOutputStream(outputFile);
      
      try
      {
        
        java.util.zip.ZipOutputStream zos = null;
        CipherOutputStream cos = null;
        
        // Check whether we need to encrypt the file content:
        if (passCode != null && passCode.length() > 0)
        {
          
          // Write IV as a prefix:
          byte[] iv = getSecureRandom();
          os.write(iv);
          os.flush();
          
          Cipher cipher = getCipher(threadContext, Cipher.ENCRYPT_MODE, passCode, iv);
          cos = new CipherOutputStream(os, cipher);
          zos = new java.util.zip.ZipOutputStream(cos);
        }
        else
          zos = new java.util.zip.ZipOutputStream(os);
 
        try
        {
          // Now, work within a transaction.
          database.beginTransaction();
          try
          {
            // At the outermost level, I've decided that the best structure is to have a zipentry for each
            // manager.  Each manager must manage its own data as a binary blob, including any format versioning information,
            // This guarantees flexibility for the future.

            // The zipentries must be written in an order that permits their proper restoration.  The "lowest level" is thus
            // written first, which yields the order: authority connections, repository connections, jobs
            java.util.zip.ZipEntry transEntry = new java.util.zip.ZipEntry("transformations");
            zos.putNextEntry(transEntry);
            transManager.exportConfiguration(zos);
            zos.closeEntry();

            java.util.zip.ZipEntry outputEntry = new java.util.zip.ZipEntry("outputs");
            zos.putNextEntry(outputEntry);
            outputManager.exportConfiguration(zos);
            zos.closeEntry();

            java.util.zip.ZipEntry groupEntry = new java.util.zip.ZipEntry("groups");
            zos.putNextEntry(groupEntry);
            groupManager.exportConfiguration(zos);
            zos.closeEntry();

            java.util.zip.ZipEntry mappingEntry = new java.util.zip.ZipEntry("mappings");
            zos.putNextEntry(mappingEntry);
            mappingManager.exportConfiguration(zos);
            zos.closeEntry();

            java.util.zip.ZipEntry authEntry = new java.util.zip.ZipEntry("authorities");
            zos.putNextEntry(authEntry);
            authManager.exportConfiguration(zos);
            zos.closeEntry();

            java.util.zip.ZipEntry connEntry = new java.util.zip.ZipEntry("connections");
            zos.putNextEntry(connEntry);
            connManager.exportConfiguration(zos);
            zos.closeEntry();

            java.util.zip.ZipEntry notConnEntry = new java.util.zip.ZipEntry("notifications");
            zos.putNextEntry(notConnEntry);
            notificationConnManager.exportConfiguration(zos);
            zos.closeEntry();

            java.util.zip.ZipEntry jobsEntry = new java.util.zip.ZipEntry("jobs");
            zos.putNextEntry(jobsEntry);
            jobManager.exportConfiguration(zos);
            zos.closeEntry();

            // All done
          }
          catch (ManifoldCFException e)
          {
            database.signalRollback();
            throw e;
          }
          catch (Error e)
          {
            database.signalRollback();
            throw e;
          }
          finally
          {
            database.endTransaction();
          }
        }
        finally
        {
          zos.close();
          if (cos != null) {
            cos.close();
          }
        }
      }
      finally
      {
        os.close();
      }
    }
    catch (java.io.IOException e)
    {
      // On error, delete any file we created
      outputFile.delete();
      // Convert I/O error into lcf exception
      throw new ManifoldCFException("Error creating configuration file: "+e.getMessage(),e);
    }
  }
  

  /** Atomically import a crawler configuration */
  public static void importConfiguration(IThreadContext threadContext, String importFilename, String passCode)
    throws ManifoldCFException
  {
    // First, we need a database handle...
    IDBInterface database = DBInterfaceFactory.make(threadContext,
      ManifoldCF.getMasterDatabaseName(),
      ManifoldCF.getMasterDatabaseUsername(),
      ManifoldCF.getMasterDatabasePassword());
    // Also create the following managers, which will handle the actual details of reading configuration data
    IOutputConnectionManager outputManager = OutputConnectionManagerFactory.make(threadContext);
    ITransformationConnectionManager transManager = TransformationConnectionManagerFactory.make(threadContext);
    IAuthorityGroupManager groupManager = AuthorityGroupManagerFactory.make(threadContext);
    IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(threadContext);
    INotificationConnectionManager notificationConnManager = NotificationConnectionManagerFactory.make(threadContext);
    IMappingConnectionManager mappingManager = MappingConnectionManagerFactory.make(threadContext);
    IAuthorityConnectionManager authManager = AuthorityConnectionManagerFactory.make(threadContext);
    IJobManager jobManager = JobManagerFactory.make(threadContext);

    File inputFile = new File(importFilename);

    // Create a zip input stream, which is what we will use as a mechanism for handling the input data
    try
    {
      InputStream is = new FileInputStream(inputFile);
      try
      {
        java.util.zip.ZipInputStream zis = null;
        CipherInputStream cis = null;
        
        // Check whether we need to decrypt the file content:
        if (passCode != null && passCode.length() > 0)
        {
          
          byte[] iv = new byte[IV_LENGTH];
          is.read(iv);

          Cipher cipher = getCipher(threadContext, Cipher.DECRYPT_MODE, passCode, iv);
          cis = new CipherInputStream(is, cipher);
          zis = new java.util.zip.ZipInputStream(cis);
        }
        else
          zis = new java.util.zip.ZipInputStream(is);

        try
        {
          // Now, work within a transaction.
          database.beginTransaction();
          try
          {
            // Process the entries in the order in which they were recorded.
            int entries = 0;
            while (true)
            {
              java.util.zip.ZipEntry z = zis.getNextEntry();
              // Stop if there are no more entries
              if (z == null)
                break;
              entries++;
              // Get the name of the entry
              String name = z.getName();
              if (name.equals("transformations"))
                transManager.importConfiguration(zis);
              else if (name.equals("outputs"))
                outputManager.importConfiguration(zis);
              else if (name.equals("groups"))
                groupManager.importConfiguration(zis);
              else if (name.equals("mappings"))
                mappingManager.importConfiguration(zis);
              else if (name.equals("authorities"))
                authManager.importConfiguration(zis);
              else if (name.equals("connections"))
                connManager.importConfiguration(zis);
              else if (name.equals("notifications"))
                notificationConnManager.importConfiguration(zis);
              else if (name.equals("jobs"))
                jobManager.importConfiguration(zis);
              else
                throw new ManifoldCFException("Configuration file has an entry named '"+name+"' that I do not recognize");
              zis.closeEntry();

            }
            if (entries == 0 && passCode != null && passCode.length() > 0)
              throw new ManifoldCFException("Cannot read configuration file. Please check your passcode and/or SALT value.");
            // All done!!
          }
          catch (ManifoldCFException e)
          {
            database.signalRollback();
            throw e;
          }
          catch (Error e)
          {
            database.signalRollback();
            throw e;
          }
          finally
          {
            database.endTransaction();
          }
        }
        finally
        {
          zis.close();
          if (cis != null) {
            cis.close();
          }
        }
      }
      finally
      {
        is.close();
      }
    }
    catch (java.io.IOException e)
    {
      // Convert I/O error into lcf exception
      throw new ManifoldCFException("Error reading configuration file: "+e.getMessage(),e);
    }
  }


  /** Get the maximum number of worker threads.
  */
  public static int getMaxWorkerThreads(IThreadContext threadContext)
    throws ManifoldCFException
  {
    return LockManagerFactory.getIntProperty(threadContext,workerThreadCountProperty,100);
  }

  /** Get the maximum number of delete threads.
  */
  public static int getMaxDeleteThreads(IThreadContext threadContext)
    throws ManifoldCFException
  {
    return LockManagerFactory.getIntProperty(threadContext,deleteThreadCountProperty,10);
  }

  /** Get the maximum number of expire threads.
  */
  public static int getMaxExpireThreads(IThreadContext threadContext)
    throws ManifoldCFException
  {
    return LockManagerFactory.getIntProperty(threadContext,expireThreadCountProperty,10);
  }

  /** Get the maximum number of cleanup threads.
  */
  public static int getMaxCleanupThreads(IThreadContext threadContext)
    throws ManifoldCFException
  {
    return LockManagerFactory.getIntProperty(threadContext,cleanupThreadCountProperty,10);
  }
  
  /** Requeue documents due to carrydown.
  */
  public static void requeueDocumentsDueToCarrydown(IJobManager jobManager,
    DocumentDescription[] requeueCandidates,
    IRepositoryConnector connector, IRepositoryConnection connection, IReprioritizationTracker rt, long currentTime)
    throws ManifoldCFException
  {
    // A list of document descriptions from finishDocuments() above represents those documents that may need to be requeued, for the
    // reason that carrydown information for those documents has changed.  In order to requeue, we need to calculate document priorities, however.
    IPriorityCalculator[] docPriorities = new IPriorityCalculator[requeueCandidates.length];
    String[][] binNames = new String[requeueCandidates.length][];
    int q = 0;
    while (q < requeueCandidates.length)
    {
      DocumentDescription dd = requeueCandidates[q];
      String[] bins = calculateBins(connector,dd.getDocumentIdentifier());
      binNames[q] = bins;
      docPriorities[q] = new PriorityCalculator(rt,connection,bins,dd.getDocumentIdentifier());
      q++;
    }

    // Now, requeue the documents with the new priorities
    jobManager.carrydownChangeDocumentMultiple(requeueCandidates,docPriorities);
  }

  /** Stuff colons so we can't have conflicts. */
  public static String colonStuff(String input)
  {
    StringBuilder sb = new StringBuilder();
    int i = 0;
    while (i < input.length())
    {
      char x  = input.charAt(i++);
      if (x == ':' || x == '\\')
        sb.append('\\');
      sb.append(x);
    }
    return sb.toString();
  }

  /** Create a global string */
  public static String createGlobalString(String simpleString)
  {
    return ":" + simpleString;
  }

  /** Create a connection-specific string */
  public static String createConnectionSpecificString(String connectionName, String simpleString)
  {
    return "C "+colonStuff(connectionName) + ":" + simpleString;
  }

  /** Create a job-specific string */
  public static String createJobSpecificString(Long jobID, String simpleString)
  {
    return "J "+jobID.toString() + ":" + simpleString;
  }

  /** Given a connector object and a document identifier, calculate its bins.
  */
  public static String[] calculateBins(IRepositoryConnector connector, String documentIdentifier)
  {
    // Get the bins for the document identifier
    return connector.getBinNames(documentIdentifier);
  }

  /** Reset all (active) document priorities.  This operation may occur due to various externally-triggered
  * events, such a job abort, pause, resume, wait, or unwait.
  */
  public static void resetAllDocumentPriorities(IThreadContext threadContext, String processID)
    throws ManifoldCFException
  {
    // The reprioritization cycle is as follows now:
    // (1) We reset the reprioritization tracker, which causes all bins to be be reset, and locks reprioritization so that it is blocked;
    // (2) We clear all document priorities;
    // (3) We unlock reprioritization, so that it may proceed.
    IJobManager jobManager = JobManagerFactory.make(threadContext);
    IReprioritizationTracker rt = ReprioritizationTrackerFactory.make(threadContext);

    String reproID = IDFactory.make(threadContext);

    rt.startReprioritization(processID,reproID);

    jobManager.clearAllDocumentPriorities();

    rt.doneReprioritization(reproID);

  /*
    ILockManager lockManager = LockManagerFactory.make(threadContext);
    IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(threadContext);
    IReprioritizationTracker rt = ReprioritizationTrackerFactory.make(threadContext);

    String reproID = IDFactory.make(threadContext);

    rt.startReprioritization(System.currentTimeMillis(),processID,reproID);
    // Reprioritize all documents in the jobqueue, 1000 at a time

    Map<String,IRepositoryConnection> connectionMap = new HashMap<String,IRepositoryConnection>();
    Map<Long,IJobDescription> jobDescriptionMap = new HashMap<Long,IJobDescription>();
    
    // Do the 'not yet processed' documents only.  Documents that are queued for reprocessing will be assigned
    // new priorities.  Already processed documents won't.  This guarantees that our bins are appropriate for current thread
    // activity.
    // In order for this to be the correct functionality, ALL reseeding and requeuing operations MUST reset the associated document
    // priorities.
    // ???? Should only start the process of reprioritization, not complete it.
    while (true)
    {
      long startTime = System.currentTimeMillis();

      Long currentTimeValue = rt.checkReprioritizationInProgress();
      if (currentTimeValue == null)
      {
        // Some other process or thread superceded us.
        return;
      }
      long updateTime = currentTimeValue.longValue();
      
      DocumentDescription[] docs = jobManager.getNextNotYetProcessedReprioritizationDocuments(10000);
      if (docs.length == 0)
        break;

      // Calculate new priorities for all these documents
      writeDocumentPriorities(threadContext,docs,connectionMap,jobDescriptionMap);

      Logging.threads.debug("Reprioritized "+Integer.toString(docs.length)+" not-yet-processed documents in "+new Long(System.currentTimeMillis()-startTime)+" ms");
    }
    
    rt.doneReprioritization(reproID);
    */
  }
  
  /** Write a set of document priorities, based on the current queue tracker.
  */
  public static void writeDocumentPriorities(IThreadContext threadContext, DocumentDescription[] descs,
    Map<String,IRepositoryConnection> connectionMap, Map<Long,IJobDescription> jobDescriptionMap)
    throws ManifoldCFException
  {
    IRepositoryConnectorPool repositoryConnectorPool = RepositoryConnectorPoolFactory.make(threadContext);
    IRepositoryConnectionManager mgr = RepositoryConnectionManagerFactory.make(threadContext);
    IJobManager jobManager = JobManagerFactory.make(threadContext);
    IReprioritizationTracker rt = ReprioritizationTrackerFactory.make(threadContext);
    
    if (Logging.scheduling.isDebugEnabled())
      Logging.scheduling.debug("Reprioritizing "+Integer.toString(descs.length)+" documents");


    IPriorityCalculator[] priorities = new IPriorityCalculator[descs.length];

    rt.clearPreloadRequests();
    
    // Compute the list of connector instances we will need.
    // This has a side effect of fetching all job descriptions too.
    Set<String> connectionNames = new HashSet<String>();
    for (int i = 0; i < descs.length; i++)
    {
      DocumentDescription dd = descs[i];
      IJobDescription job = jobDescriptionMap.get(dd.getJobID());
      if (job == null)
      {
        job = jobManager.load(dd.getJobID(),true);
        jobDescriptionMap.put(dd.getJobID(),job);
      }
      connectionNames.add(job.getConnectionName());
    }
    String[] orderingKeys = new String[connectionNames.size()];
    IRepositoryConnection[] connections = new IRepositoryConnection[connectionNames.size()];
    int z = 0;
    for (String connectionName : connectionNames)
    {
      orderingKeys[z] = connectionName;
      IRepositoryConnection connection = connectionMap.get(connectionName);
      if (connection == null)
      {
        connection = mgr.load(connectionName);
        connectionMap.put(connectionName,connection);
      }
      connections[z] = connection;
      z++;
    }

    // Now, grab the connector instances we need
    IRepositoryConnector[] connectors = repositoryConnectorPool.grabMultiple(orderingKeys,connections);
    try
    {
      // Map from connection name to connector instance
      Map<String,IRepositoryConnector> connectorMap = new HashMap<String,IRepositoryConnector>();
      for (z = 0; z < orderingKeys.length; z++)
      {
        connectorMap.put(orderingKeys[z],connectors[z]);
      }
      // Go through the documents and calculate the priorities
      double minimumDepth = rt.getMinimumDepth();
      for (int i = 0; i < descs.length; i++)
      {
        DocumentDescription dd = descs[i];
        IJobDescription job = jobDescriptionMap.get(dd.getJobID());
        String connectionName = job.getConnectionName();
        IRepositoryConnector connector = connectorMap.get(connectionName);
        IRepositoryConnection connection = connectionMap.get(connectionName);
        String[] binNames;
        if (connector == null)
          binNames = new String[]{""};
        else
          // Get the bins for the document identifier
          binNames = connector.getBinNames(descs[i].getDocumentIdentifier());
        PriorityCalculator p = new PriorityCalculator(rt,minimumDepth,connection,binNames,descs[i].getDocumentIdentifier());
        priorities[i] = p;
        p.makePreloadRequest();
      }
    }
    finally
    {
      // Release all the connector instances we grabbed
      repositoryConnectorPool.releaseMultiple(connections,connectors);
    }
    
    rt.preloadBinValues();
    
    // Now, write all the priorities we can.
    jobManager.writeDocumentPriorities(descs,priorities);

    rt.clearPreloadedValues();
  }

  /** Get the activities list for a given repository connection.
  */
  public static String[] getActivitiesList(IThreadContext threadContext, String connectionName)
    throws ManifoldCFException
  {
    IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(threadContext);
    IRepositoryConnection thisConnection = connectionManager.load(connectionName);
    if (thisConnection == null)
      return null;
    String[] outputActivityList = OutputConnectionManagerFactory.getAllOutputActivities(threadContext);
    String[] transformationActivityList = TransformationConnectionManagerFactory.getAllTransformationActivities(threadContext);
    String[] connectorActivityList = RepositoryConnectorFactory.getActivitiesList(threadContext,thisConnection.getClassName());
    String[] globalActivityList = IRepositoryConnectionManager.activitySet;
    String[] activityList = new String[transformationActivityList.length + outputActivityList.length + ((connectorActivityList==null)?0:connectorActivityList.length) + globalActivityList.length];
    int k2 = 0;
    if (transformationActivityList != null)
    {
      for (String transformationActivity: transformationActivityList)
      {
        activityList[k2++] = transformationActivity;
      }
    }
    if (outputActivityList != null)
    {
      for (String outputActivity : outputActivityList)
      {
        activityList[k2++] = outputActivity;
      }
    }
    if (connectorActivityList != null)
    {
      for (String connectorActivity : connectorActivityList)
      {
        activityList[k2++] = connectorActivity;
      }
    }
    for (String globalActivity : globalActivityList)
    {
      activityList[k2++] = globalActivity;
    }
    java.util.Arrays.sort(activityList);
    return activityList;
  }
  
  // ========================== API support ===========================
  
  protected static final String API_JOBNODE = "job";
  protected static final String API_JOBSTATUSNODE = "jobstatus";
  protected static final String API_AUTHORIZATIONDOMAINNODE = "authorizationdomain";
  protected static final String API_AUTHORITYGROUPNODE = "authoritygroup";
  protected static final String API_REPOSITORYCONNECTORNODE = "repositoryconnector";
  protected static final String API_NOTIFICATIONCONNECTORNODE = "notificationconnector";
  protected static final String API_OUTPUTCONNECTORNODE = "outputconnector";
  protected static final String API_TRANSFORMATIONCONNECTORNODE = "transformationconnector";
  protected static final String API_AUTHORITYCONNECTORNODE = "authorityconnector";
  protected static final String API_MAPPINGCONNECTORNODE = "mappingconnector";
  protected static final String API_REPOSITORYCONNECTIONNODE = "repositoryconnection";
  protected static final String API_NOTIFICATIONCONNECTIONNODE = "notificationconnection";
  protected static final String API_OUTPUTCONNECTIONNODE = "outputconnection";
  protected static final String API_TRANSFORMATIONCONNECTIONNODE = "transformationconnection";
  protected static final String API_AUTHORITYCONNECTIONNODE = "authorityconnection";
  protected static final String API_MAPPINGCONNECTIONNODE = "mappingconnection";
  protected static final String API_CHECKRESULTNODE = "check_result";
  protected static final String API_JOBIDNODE = "job_id";
  protected static final String API_CONNECTIONNAMENODE = "connection_name";
  protected final static String API_ROWNODE = "row";
  protected final static String API_COLUMNNODE = "column";
  protected final static String API_NAMENODE = "name";
  protected final static String API_VALUENODE = "value";
  protected final static String API_ACTIVITYNODE = "activity";
  
  // Connector nodes
  protected static final String CONNECTORNODE_DESCRIPTION = "description";
  protected static final String CONNECTORNODE_CLASSNAME = "class_name";
  
  // Authorization domain nodes
  protected static final String AUTHORIZATIONDOMAINNODE_DESCRIPTION = "description";
  protected static final String AUTHORIZATIONDOMAINNODE_DOMAINNAME = "domain_name";
  
  /** Decode path element.
  * Path elements in the API world cannot have "/" characters, or they become impossible to parse.  This method undoes
  * escaping that prevents "/" from appearing.
  */
  public static String decodeAPIPathElement(String startingPathElement)
    throws ManifoldCFException
  {
    StringBuilder sb = new StringBuilder();
    int i = 0;
    while (i < startingPathElement.length())
    {
      char x = startingPathElement.charAt(i++);
      if (x == '.')
      {
        if (i == startingPathElement.length())
          throw new ManifoldCFException("Element decoding failed; illegal '.' character in '"+startingPathElement+"'");
        
        x = startingPathElement.charAt(i++);
        if (x == '.')
          sb.append(x);
        else if (x == '+')
          sb.append('/');
        else
          throw new ManifoldCFException("Element decoding failed; illegal post-'.' character in '"+startingPathElement+"'");
      }
      else
        sb.append(x);
    }
    return sb.toString();
  }

  // Read (GET) functions
  
  // Read result codes
  public static final int READRESULT_NOTFOUND = 0;
  public static final int READRESULT_FOUND = 1;
  public static final int READRESULT_NOTALLOWED = 2;
  
  /** Read jobs */
  protected static int apiReadJobs(IThreadContext tc, Configuration output, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_JOBS))
      return READRESULT_NOTALLOWED;

    try
    {
      IJobManager jobManager = JobManagerFactory.make(tc);
      IJobDescription[] jobs = jobManager.getAllJobs();
      int i = 0;
      while (i < jobs.length)
      {
        ConfigurationNode jobNode = new ConfigurationNode(API_JOBNODE);
        formatJobDescription(jobNode,jobs[i++]);
        output.addChild(output.getChildCount(),jobNode);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }
  
  /** Read a job */
  protected static int apiReadJob(IThreadContext tc, Configuration output, Long jobID, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_JOBS))
      return READRESULT_NOTALLOWED;

    try
    {
      IJobManager jobManager = JobManagerFactory.make(tc);
      IJobDescription job = jobManager.load(jobID);
      if (job != null)
      {
        // Fill the return object with job information
        ConfigurationNode jobNode = new ConfigurationNode(API_JOBNODE);
        formatJobDescription(jobNode,job);
        output.addChild(output.getChildCount(),jobNode);
      }
      else
      {
        createErrorNode(output,"Job does not exist.");
        return READRESULT_NOTFOUND;
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }
  
  /** Read an output connection status */
  protected static int apiReadOutputConnectionStatus(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IOutputConnectorPool outputConnectorPool = OutputConnectorPoolFactory.make(tc);
      IOutputConnectionManager connectionManager = OutputConnectionManagerFactory.make(tc);
      IOutputConnection connection = connectionManager.load(connectionName);
      if (connection == null)
      {
        createErrorNode(output,"Connection '"+connectionName+"' does not exist");
        return READRESULT_NOTFOUND;
      }
          
      String results;
      // Grab a connection handle, and call the test method
      IOutputConnector connector = outputConnectorPool.grab(connection);
      try
      {
        results = connector.check();
      }
      catch (ManifoldCFException e)
      {
        results = e.getMessage();
      }
      finally
      {
        outputConnectorPool.release(connection,connector);
      }
          
      ConfigurationNode response = new ConfigurationNode(API_CHECKRESULTNODE);
      response.setValue(results);
      output.addChild(output.getChildCount(),response);
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** Read a transformation connection status */
  protected static int apiReadTransformationConnectionStatus(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;
    
    try
    {
      ITransformationConnectorPool transformationConnectorPool = TransformationConnectorPoolFactory.make(tc);
      ITransformationConnectionManager connectionManager = TransformationConnectionManagerFactory.make(tc);
      ITransformationConnection connection = connectionManager.load(connectionName);
      if (connection == null)
      {
        createErrorNode(output,"Connection '"+connectionName+"' does not exist");
        return READRESULT_NOTFOUND;
      }
          
      String results;
      // Grab a connection handle, and call the test method
      ITransformationConnector connector = transformationConnectorPool.grab(connection);
      try
      {
        results = connector.check();
      }
      catch (ManifoldCFException e)
      {
        results = e.getMessage();
      }
      finally
      {
        transformationConnectorPool.release(connection,connector);
      }
          
      ConfigurationNode response = new ConfigurationNode(API_CHECKRESULTNODE);
      response.setValue(results);
      output.addChild(output.getChildCount(),response);
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** Read an authority connection status */
  protected static int apiReadAuthorityConnectionStatus(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IAuthorityConnectorPool authorityConnectorPool = AuthorityConnectorPoolFactory.make(tc);
      IAuthorityConnectionManager connectionManager = AuthorityConnectionManagerFactory.make(tc);
      IAuthorityConnection connection = connectionManager.load(connectionName);
      if (connection == null)
      {
        createErrorNode(output,"Connection '"+connectionName+"' does not exist");
        return READRESULT_NOTFOUND;
      }
          
      String results;
      // Grab a connection handle, and call the test method
      IAuthorityConnector connector = authorityConnectorPool.grab(connection);
      try
      {
        results = connector.check();
      }
      catch (ManifoldCFException e)
      {
        results = e.getMessage();
      }
      finally
      {
        authorityConnectorPool.release(connection,connector);
      }
          
      ConfigurationNode response = new ConfigurationNode(API_CHECKRESULTNODE);
      response.setValue(results);
      output.addChild(output.getChildCount(),response);
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** Read a mapping connection status */
  protected static int apiReadMappingConnectionStatus(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IMappingConnectorPool mappingConnectorPool = MappingConnectorPoolFactory.make(tc);
      IMappingConnectionManager connectionManager = MappingConnectionManagerFactory.make(tc);
      IMappingConnection connection = connectionManager.load(connectionName);
      if (connection == null)
      {
        createErrorNode(output,"Connection '"+connectionName+"' does not exist");
        return READRESULT_NOTFOUND;
      }
          
      String results;
      // Grab a connection handle, and call the test method
      IMappingConnector connector = mappingConnectorPool.grab(connection);
      try
      {
        results = connector.check();
      }
      catch (ManifoldCFException e)
      {
        results = e.getMessage();
      }
      finally
      {
        mappingConnectorPool.release(connection,connector);
      }
          
      ConfigurationNode response = new ConfigurationNode(API_CHECKRESULTNODE);
      response.setValue(results);
      output.addChild(output.getChildCount(),response);
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }
  
  /** Read a repository connection status */
  protected static int apiReadRepositoryConnectionStatus(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IRepositoryConnectorPool repositoryConnectorPool = RepositoryConnectorPoolFactory.make(tc);
      IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(tc);
      IRepositoryConnection connection = connectionManager.load(connectionName);
      if (connection == null)
      {
        createErrorNode(output,"Connection '"+connectionName+"' does not exist");
        return READRESULT_NOTFOUND;
      }
          
      String results;
      // Grab a connection handle, and call the test method
      IRepositoryConnector connector = repositoryConnectorPool.grab(connection);
      try
      {
        results = connector.check();
      }
      catch (ManifoldCFException e)
      {
        results = e.getMessage();
      }
      finally
      {
        repositoryConnectorPool.release(connection,connector);
      }
          
      ConfigurationNode response = new ConfigurationNode(API_CHECKRESULTNODE);
      response.setValue(results);
      output.addChild(output.getChildCount(),response);
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }
  
  /** Read a notification connection status */
  protected static int apiReadNotificationConnectionStatus(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      INotificationConnectorPool notificationConnectorPool = NotificationConnectorPoolFactory.make(tc);
      INotificationConnectionManager connectionManager = NotificationConnectionManagerFactory.make(tc);
      INotificationConnection connection = connectionManager.load(connectionName);
      if (connection == null)
      {
        createErrorNode(output,"Connection '"+connectionName+"' does not exist");
        return READRESULT_NOTFOUND;
      }
          
      String results;
      // Grab a connection handle, and call the test method
      INotificationConnector connector = notificationConnectorPool.grab(connection);
      try
      {
        results = connector.check();
      }
      catch (ManifoldCFException e)
      {
        results = e.getMessage();
      }
      finally
      {
        notificationConnectorPool.release(connection,connector);
      }
          
      ConfigurationNode response = new ConfigurationNode(API_CHECKRESULTNODE);
      response.setValue(results);
      output.addChild(output.getChildCount(),response);
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }
  
  /** Read an output connection's info */
  protected static int apiReadOutputConnectionInfo(IThreadContext tc, Configuration output, String connectionName, String command, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IOutputConnectorPool outputConnectorPool = OutputConnectorPoolFactory.make(tc);
      IOutputConnectionManager connectionManager = OutputConnectionManagerFactory.make(tc);
      IOutputConnection connection = connectionManager.load(connectionName);
      if (connection == null)
      {
        createErrorNode(output,"Connection '"+connectionName+"' does not exist");
        return READRESULT_NOTFOUND;
      }

      // Grab a connection handle, and call the test method
      IOutputConnector connector = outputConnectorPool.grab(connection);
      try
      {
        return connector.requestInfo(output,command)?READRESULT_FOUND:READRESULT_NOTFOUND;
      }
      finally
      {
        outputConnectorPool.release(connection,connector);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** Read a transformation connection's info */
  protected static int apiReadTransformationConnectionInfo(IThreadContext tc, Configuration output, String connectionName, String command, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      ITransformationConnectorPool transformationConnectorPool = TransformationConnectorPoolFactory.make(tc);
      ITransformationConnectionManager connectionManager = TransformationConnectionManagerFactory.make(tc);
      ITransformationConnection connection = connectionManager.load(connectionName);
      if (connection == null)
      {
        createErrorNode(output,"Connection '"+connectionName+"' does not exist");
        return READRESULT_NOTFOUND;
      }

      // Grab a connection handle, and call the test method
      ITransformationConnector connector = transformationConnectorPool.grab(connection);
      try
      {
        return connector.requestInfo(output,command)?READRESULT_FOUND:READRESULT_NOTFOUND;
      }
      finally
      {
        transformationConnectorPool.release(connection,connector);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }
  
  /** Read a repository connection's info */
  protected static int apiReadRepositoryConnectionInfo(IThreadContext tc, Configuration output, String connectionName, String command, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IRepositoryConnectorPool repositoryConnectorPool = RepositoryConnectorPoolFactory.make(tc);
      IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(tc);
      IRepositoryConnection connection = connectionManager.load(connectionName);
      if (connection == null)
      {
        createErrorNode(output,"Connection '"+connectionName+"' does not exist");
        return READRESULT_NOTFOUND;
      }

      // Grab a connection handle, and call the test method
      IRepositoryConnector connector = repositoryConnectorPool.grab(connection);
      try
      {
        return connector.requestInfo(output,command)?READRESULT_FOUND:READRESULT_NOTFOUND;
      }
      finally
      {
        repositoryConnectorPool.release(connection,connector);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** Read a notification connection's info */
  protected static int apiReadNotificationConnectionInfo(IThreadContext tc, Configuration output, String connectionName, String command, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      INotificationConnectorPool notificationConnectorPool = NotificationConnectorPoolFactory.make(tc);
      INotificationConnectionManager connectionManager = NotificationConnectionManagerFactory.make(tc);
      INotificationConnection connection = connectionManager.load(connectionName);
      if (connection == null)
      {
        createErrorNode(output,"Connection '"+connectionName+"' does not exist");
        return READRESULT_NOTFOUND;
      }

      // Grab a connection handle, and call the test method
      INotificationConnector connector = notificationConnectorPool.grab(connection);
      try
      {
        return connector.requestInfo(output,command)?READRESULT_FOUND:READRESULT_NOTFOUND;
      }
      finally
      {
        notificationConnectorPool.release(connection,connector);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** Get api job statuses */
  protected static int apiReadJobStatuses(IThreadContext tc, Configuration output, Map<String,List<String>> queryParameters, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_REPORTS))
      return READRESULT_NOTALLOWED;

    if (queryParameters == null)
      queryParameters = new HashMap<String,List<String>>();
    int maxCount;
    List<String> maxCountList = queryParameters.get("maxcount");
    if (maxCountList == null || maxCountList.size() == 0)
      maxCount = Integer.MAX_VALUE;
    else if (maxCountList.size() > 1)
      throw new ManifoldCFException("Multiple values for maxcount parameter");
    else
      maxCount = new Integer(maxCountList.get(0)).intValue();
      
    try
    {
      IJobManager jobManager = JobManagerFactory.make(tc);
      JobStatus[] jobStatuses = jobManager.getAllStatus(true,maxCount);
      int i = 0;
      while (i < jobStatuses.length)
      {
        ConfigurationNode jobStatusNode = new ConfigurationNode(API_JOBSTATUSNODE);
        formatJobStatus(jobStatusNode,jobStatuses[i++]);
        output.addChild(output.getChildCount(),jobStatusNode);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** Get api job statuses */
  protected static int apiReadJobStatusesNoCounts(IThreadContext tc, Configuration output, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_REPORTS))
      return READRESULT_NOTALLOWED;

    try
    {
      IJobManager jobManager = JobManagerFactory.make(tc);
      JobStatus[] jobStatuses = jobManager.getAllStatus(false);
      int i = 0;
      while (i < jobStatuses.length)
      {
        ConfigurationNode jobStatusNode = new ConfigurationNode(API_JOBSTATUSNODE);
        formatJobStatus(jobStatusNode,jobStatuses[i++]);
        output.addChild(output.getChildCount(),jobStatusNode);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }
  
  /** Get api job status */
  protected static int apiReadJobStatus(IThreadContext tc, Configuration output, Long jobID, Map<String,List<String>> queryParameters, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_REPORTS))
      return READRESULT_NOTALLOWED;

    if (queryParameters == null)
      queryParameters = new HashMap<String,List<String>>();
    int maxCount;
    List<String> maxCountList = queryParameters.get("maxcount");
    if (maxCountList == null || maxCountList.size() == 0)
      maxCount = Integer.MAX_VALUE;
    else if (maxCountList.size() > 1)
      throw new ManifoldCFException("Multiple values for maxcount parameter");
    else
      maxCount = new Integer(maxCountList.get(0)).intValue();

    try
    {
      IJobManager jobManager = JobManagerFactory.make(tc);
      JobStatus status = jobManager.getStatus(jobID,true,maxCount);
      if (status != null)
      {
        ConfigurationNode jobStatusNode = new ConfigurationNode(API_JOBSTATUSNODE);
        formatJobStatus(jobStatusNode,status);
        output.addChild(output.getChildCount(),jobStatusNode);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** Get api job status with no counts */
  protected static int apiReadJobStatusNoCounts(IThreadContext tc, Configuration output, Long jobID, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_REPORTS))
      return READRESULT_NOTALLOWED;

    try
    {
      IJobManager jobManager = JobManagerFactory.make(tc);
      JobStatus status = jobManager.getStatus(jobID,false);
      if (status != null)
      {
        ConfigurationNode jobStatusNode = new ConfigurationNode(API_JOBSTATUSNODE);
        formatJobStatus(jobStatusNode,status);
        output.addChild(output.getChildCount(),jobStatusNode);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }
  
  /** Get authority groups */
  protected static int apiReadAuthorityGroups(IThreadContext tc, Configuration output, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IAuthorityGroupManager groupManager = AuthorityGroupManagerFactory.make(tc);
      IAuthorityGroup[] groups = groupManager.getAllGroups();
      int i = 0;
      while (i < groups.length)
      {
        ConfigurationNode groupNode = new ConfigurationNode(API_AUTHORITYGROUPNODE);
        formatAuthorityGroup(groupNode,groups[i++]);
        output.addChild(output.getChildCount(),groupNode);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }
  
  /** Read authority group */
  protected static int apiReadAuthorityGroup(IThreadContext tc, Configuration output, String groupName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IAuthorityGroupManager groupManager = AuthorityGroupManagerFactory.make(tc);
      IAuthorityGroup group = groupManager.load(groupName);
      if (group != null)
      {
        // Fill the return object with job information
        ConfigurationNode groupNode = new ConfigurationNode(API_AUTHORITYGROUPNODE);
        formatAuthorityGroup(groupNode,group);
        output.addChild(output.getChildCount(),groupNode);
      }
      else
      {
        createErrorNode(output,"Authority group '"+groupName+"' does not exist.");
        return READRESULT_NOTFOUND;
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** Get output connections */
  protected static int apiReadOutputConnections(IThreadContext tc, Configuration output, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IOutputConnectionManager connManager = OutputConnectionManagerFactory.make(tc);
      IOutputConnection[] connections = connManager.getAllConnections();
      int i = 0;
      while (i < connections.length)
      {
        ConfigurationNode connectionNode = new ConfigurationNode(API_OUTPUTCONNECTIONNODE);
        formatOutputConnection(connectionNode,connections[i++]);
        output.addChild(output.getChildCount(),connectionNode);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }
  
  /** Read output connection */
  protected static int apiReadOutputConnection(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IOutputConnectionManager connectionManager = OutputConnectionManagerFactory.make(tc);
      IOutputConnection connection = connectionManager.load(connectionName);
      if (connection != null)
      {
        // Fill the return object with job information
        ConfigurationNode connectionNode = new ConfigurationNode(API_OUTPUTCONNECTIONNODE);
        formatOutputConnection(connectionNode,connection);
        output.addChild(output.getChildCount(),connectionNode);
      }
      else
      {
        createErrorNode(output,"Connection '"+connectionName+"' does not exist.");
        return READRESULT_NOTFOUND;
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

    /** Get transformation connections */
  protected static int apiReadTransformationConnections(IThreadContext tc, Configuration output, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      ITransformationConnectionManager connManager = TransformationConnectionManagerFactory.make(tc);
      ITransformationConnection[] connections = connManager.getAllConnections();
      int i = 0;
      while (i < connections.length)
      {
        ConfigurationNode connectionNode = new ConfigurationNode(API_TRANSFORMATIONCONNECTIONNODE);
        formatTransformationConnection(connectionNode,connections[i++]);
        output.addChild(output.getChildCount(),connectionNode);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }
  
  /** Read transformation connection */
  protected static int apiReadTransformationConnection(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      ITransformationConnectionManager connectionManager = TransformationConnectionManagerFactory.make(tc);
      ITransformationConnection connection = connectionManager.load(connectionName);
      if (connection != null)
      {
        // Fill the return object with job information
        ConfigurationNode connectionNode = new ConfigurationNode(API_TRANSFORMATIONCONNECTIONNODE);
        formatTransformationConnection(connectionNode,connection);
        output.addChild(output.getChildCount(),connectionNode);
      }
      else
      {
        createErrorNode(output,"Connection '"+connectionName+"' does not exist.");
        return READRESULT_NOTFOUND;
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** Get authority connections */
  protected static int apiReadAuthorityConnections(IThreadContext tc, Configuration output, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IAuthorityConnectionManager connManager = AuthorityConnectionManagerFactory.make(tc);
      IAuthorityConnection[] connections = connManager.getAllConnections();
      int i = 0;
      while (i < connections.length)
      {
        ConfigurationNode connectionNode = new ConfigurationNode(API_AUTHORITYCONNECTIONNODE);
        formatAuthorityConnection(connectionNode,connections[i++]);
        output.addChild(output.getChildCount(),connectionNode);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** Get mapping connections */
  protected static int apiReadMappingConnections(IThreadContext tc, Configuration output, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IMappingConnectionManager connManager = MappingConnectionManagerFactory.make(tc);
      IMappingConnection[] connections = connManager.getAllConnections();
      int i = 0;
      while (i < connections.length)
      {
        ConfigurationNode connectionNode = new ConfigurationNode(API_MAPPINGCONNECTIONNODE);
        formatMappingConnection(connectionNode,connections[i++]);
        output.addChild(output.getChildCount(),connectionNode);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** Read authority connection */
  protected static int apiReadAuthorityConnection(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IAuthorityConnectionManager connectionManager = AuthorityConnectionManagerFactory.make(tc);
      IAuthorityConnection connection = connectionManager.load(connectionName);
      if (connection != null)
      {
        // Fill the return object with job information
        ConfigurationNode connectionNode = new ConfigurationNode(API_AUTHORITYCONNECTIONNODE);
        formatAuthorityConnection(connectionNode,connection);
        output.addChild(output.getChildCount(),connectionNode);
      }
      else
      {
        createErrorNode(output,"Authority connection '"+connectionName+"' does not exist.");
        return READRESULT_NOTFOUND;
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** Read mapping connection */
  protected static int apiReadMappingConnection(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IMappingConnectionManager connectionManager = MappingConnectionManagerFactory.make(tc);
      IMappingConnection connection = connectionManager.load(connectionName);
      if (connection != null)
      {
        // Fill the return object with job information
        ConfigurationNode connectionNode = new ConfigurationNode(API_MAPPINGCONNECTIONNODE);
        formatMappingConnection(connectionNode,connection);
        output.addChild(output.getChildCount(),connectionNode);
      }
      else
      {
        createErrorNode(output,"Mapping connection '"+connectionName+"' does not exist.");
        return READRESULT_NOTFOUND;
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** Get repository connections */
  protected static int apiReadRepositoryConnections(IThreadContext tc, Configuration output, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(tc);
      IRepositoryConnection[] connections = connManager.getAllConnections();
      int i = 0;
      while (i < connections.length)
      {
        ConfigurationNode connectionNode = new ConfigurationNode(API_REPOSITORYCONNECTIONNODE);
        formatRepositoryConnection(connectionNode,connections[i++]);
        output.addChild(output.getChildCount(),connectionNode);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }
  
  /** Read repository connection */
  protected static int apiReadRepositoryConnection(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(tc);
      IRepositoryConnection connection = connectionManager.load(connectionName);
      if (connection != null)
      {
        // Fill the return object with job information
        ConfigurationNode connectionNode = new ConfigurationNode(API_REPOSITORYCONNECTIONNODE);
        formatRepositoryConnection(connectionNode,connection);
        output.addChild(output.getChildCount(),connectionNode);
      }
      else
      {
        createErrorNode(output,"Repository connection '"+connectionName+"' does not exist");
        return READRESULT_NOTFOUND;
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** Get notification connections */
  protected static int apiReadNotificationConnections(IThreadContext tc, Configuration output, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      INotificationConnectionManager connManager = NotificationConnectionManagerFactory.make(tc);
      INotificationConnection[] connections = connManager.getAllConnections();
      int i = 0;
      while (i < connections.length)
      {
        ConfigurationNode connectionNode = new ConfigurationNode(API_NOTIFICATIONCONNECTIONNODE);
        formatNotificationConnection(connectionNode,connections[i++]);
        output.addChild(output.getChildCount(),connectionNode);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }
  
  /** Read notification connection */
  protected static int apiReadNotificationConnection(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      INotificationConnectionManager connectionManager = NotificationConnectionManagerFactory.make(tc);
      INotificationConnection connection = connectionManager.load(connectionName);
      if (connection != null)
      {
        // Fill the return object with job information
        ConfigurationNode connectionNode = new ConfigurationNode(API_NOTIFICATIONCONNECTIONNODE);
        formatNotificationConnection(connectionNode,connection);
        output.addChild(output.getChildCount(),connectionNode);
      }
      else
      {
        createErrorNode(output,"Notification connection '"+connectionName+"' does not exist");
        return READRESULT_NOTFOUND;
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** List output connectors */
  protected static int apiReadOutputConnectors(IThreadContext tc, Configuration output, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    // List registered output connectors
    try
    {
      IOutputConnectorManager manager = OutputConnectorManagerFactory.make(tc);
      IResultSet resultSet = manager.getConnectors();
      int j = 0;
      while (j < resultSet.getRowCount())
      {
        IResultRow row = resultSet.getRow(j++);
        ConfigurationNode child = new ConfigurationNode(API_OUTPUTCONNECTORNODE);
        String description = (String)row.getValue("description");
        String className = (String)row.getValue("classname");
        ConfigurationNode node;
        if (description != null)
        {
          node = new ConfigurationNode(CONNECTORNODE_DESCRIPTION);
          node.setValue(description);
          child.addChild(child.getChildCount(),node);
        }
        node = new ConfigurationNode(CONNECTORNODE_CLASSNAME);
        node.setValue(className);
        child.addChild(child.getChildCount(),node);

        output.addChild(output.getChildCount(),child);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** List transformation connectors */
  protected static int apiReadTransformationConnectors(IThreadContext tc, Configuration output, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    // List registered transformation connectors
    try
    {
      ITransformationConnectorManager manager = TransformationConnectorManagerFactory.make(tc);
      IResultSet resultSet = manager.getConnectors();
      int j = 0;
      while (j < resultSet.getRowCount())
      {
        IResultRow row = resultSet.getRow(j++);
        ConfigurationNode child = new ConfigurationNode(API_TRANSFORMATIONCONNECTORNODE);
        String description = (String)row.getValue("description");
        String className = (String)row.getValue("classname");
        ConfigurationNode node;
        if (description != null)
        {
          node = new ConfigurationNode(CONNECTORNODE_DESCRIPTION);
          node.setValue(description);
          child.addChild(child.getChildCount(),node);
        }
        node = new ConfigurationNode(CONNECTORNODE_CLASSNAME);
        node.setValue(className);
        child.addChild(child.getChildCount(),node);

        output.addChild(output.getChildCount(),child);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** List authority connectors */
  protected static int apiReadAuthorityConnectors(IThreadContext tc, Configuration output, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    // List registered authority connectors
    try
    {
      IAuthorityConnectorManager manager = AuthorityConnectorManagerFactory.make(tc);
      IResultSet resultSet = manager.getConnectors();
      int j = 0;
      while (j < resultSet.getRowCount())
      {
        IResultRow row = resultSet.getRow(j++);
        ConfigurationNode child = new ConfigurationNode(API_AUTHORITYCONNECTORNODE);
        String description = (String)row.getValue("description");
        String className = (String)row.getValue("classname");
        ConfigurationNode node;
        if (description != null)
        {
          node = new ConfigurationNode(CONNECTORNODE_DESCRIPTION);
          node.setValue(description);
          child.addChild(child.getChildCount(),node);
        }
        node = new ConfigurationNode(CONNECTORNODE_CLASSNAME);
        node.setValue(className);
        child.addChild(child.getChildCount(),node);

        output.addChild(output.getChildCount(),child);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** List mapping connectors */
  protected static int apiReadMappingConnectors(IThreadContext tc, Configuration output, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    // List registered authority connectors
    try
    {
      IMappingConnectorManager manager = MappingConnectorManagerFactory.make(tc);
      IResultSet resultSet = manager.getConnectors();
      int j = 0;
      while (j < resultSet.getRowCount())
      {
        IResultRow row = resultSet.getRow(j++);
        ConfigurationNode child = new ConfigurationNode(API_MAPPINGCONNECTORNODE);
        String description = (String)row.getValue("description");
        String className = (String)row.getValue("classname");
        ConfigurationNode node;
        if (description != null)
        {
          node = new ConfigurationNode(CONNECTORNODE_DESCRIPTION);
          node.setValue(description);
          child.addChild(child.getChildCount(),node);
        }
        node = new ConfigurationNode(CONNECTORNODE_CLASSNAME);
        node.setValue(className);
        child.addChild(child.getChildCount(),node);

        output.addChild(output.getChildCount(),child);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** List authorization domains */
  protected static int apiReadAuthorizationDomains(IThreadContext tc, Configuration output, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    // List registered authorization domains
    try
    {
      IAuthorizationDomainManager manager = AuthorizationDomainManagerFactory.make(tc);
      IResultSet resultSet = manager.getDomains();
      int j = 0;
      while (j < resultSet.getRowCount())
      {
        IResultRow row = resultSet.getRow(j++);
        ConfigurationNode child = new ConfigurationNode(API_AUTHORIZATIONDOMAINNODE);
        String description = (String)row.getValue("description");
        String domainName = (String)row.getValue("domainname");
        ConfigurationNode node;
        if (description != null)
        {
          node = new ConfigurationNode(AUTHORIZATIONDOMAINNODE_DESCRIPTION);
          node.setValue(description);
          child.addChild(child.getChildCount(),node);
        }
        node = new ConfigurationNode(AUTHORIZATIONDOMAINNODE_DOMAINNAME);
        node.setValue(domainName);
        child.addChild(child.getChildCount(),node);

        output.addChild(output.getChildCount(),child);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;

  }
  
  /** List repository connectors */
  protected static int apiReadRepositoryConnectors(IThreadContext tc, Configuration output, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    // List registered repository connectors
    try
    {
      IConnectorManager manager = ConnectorManagerFactory.make(tc);
      IResultSet resultSet = manager.getConnectors();
      int j = 0;
      while (j < resultSet.getRowCount())
      {
        IResultRow row = resultSet.getRow(j++);
        ConfigurationNode child = new ConfigurationNode(API_REPOSITORYCONNECTORNODE);
        String description = (String)row.getValue("description");
        String className = (String)row.getValue("classname");
        ConfigurationNode node;
        if (description != null)
        {
          node = new ConfigurationNode(CONNECTORNODE_DESCRIPTION);
          node.setValue(description);
          child.addChild(child.getChildCount(),node);
        }
        node = new ConfigurationNode(CONNECTORNODE_CLASSNAME);
        node.setValue(className);
        child.addChild(child.getChildCount(),node);

        output.addChild(output.getChildCount(),child);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }

  /** List notification connectors */
  protected static int apiReadNotificationConnectors(IThreadContext tc, Configuration output, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    // List registered notification connectors
    try
    {
      IConnectorManager manager = ConnectorManagerFactory.make(tc);
      IResultSet resultSet = manager.getConnectors();
      int j = 0;
      while (j < resultSet.getRowCount())
      {
        IResultRow row = resultSet.getRow(j++);
        ConfigurationNode child = new ConfigurationNode(API_NOTIFICATIONCONNECTORNODE);
        String description = (String)row.getValue("description");
        String className = (String)row.getValue("classname");
        ConfigurationNode node;
        if (description != null)
        {
          node = new ConfigurationNode(CONNECTORNODE_DESCRIPTION);
          node.setValue(description);
          child.addChild(child.getChildCount(),node);
        }
        node = new ConfigurationNode(CONNECTORNODE_CLASSNAME);
        node.setValue(className);
        child.addChild(child.getChildCount(),node);

        output.addChild(output.getChildCount(),child);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }
      
  protected final static Map<String,Integer> docState;
  static
  {
    docState = new HashMap<String,Integer>();
    docState.put("neverprocessed",new Integer(IJobManager.DOCSTATE_NEVERPROCESSED));
    docState.put("previouslyprocessed",new Integer(IJobManager.DOCSTATE_PREVIOUSLYPROCESSED));
    docState.put("outofscope",new Integer(IJobManager.DOCSTATE_OUTOFSCOPE));
  }

  protected final static Map<String,Integer> docStatus;
  static
  {
    docStatus = new HashMap<String,Integer>();
    docStatus.put("inactive",new Integer(IJobManager.DOCSTATUS_INACTIVE));
    docStatus.put("processing",new Integer(IJobManager.DOCSTATUS_PROCESSING));
    docStatus.put("expiring",new Integer(IJobManager.DOCSTATUS_EXPIRING));
    docStatus.put("deleting",new Integer(IJobManager.DOCSTATUS_DELETING));
    docStatus.put("readyforprocessing",new Integer(IJobManager.DOCSTATUS_READYFORPROCESSING));
    docStatus.put("readyforexpiration",new Integer(IJobManager.DOCSTATUS_READYFOREXPIRATION));
    docStatus.put("waitingforprocessing",new Integer(IJobManager.DOCSTATUS_WAITINGFORPROCESSING));
    docStatus.put("waitingforexpiration",new Integer(IJobManager.DOCSTATUS_WAITINGFOREXPIRATION));
    docStatus.put("waitingforever",new Integer(IJobManager.DOCSTATUS_WAITINGFOREVER));
    docStatus.put("hopcountexceeded",new Integer(IJobManager.DOCSTATUS_HOPCOUNTEXCEEDED));
  }

  /** Queue reports */
  protected static int apiReadRepositoryConnectionQueue(IThreadContext tc, Configuration output,
    String connectionName, Map<String,List<String>> queryParameters, IAuthorizer authorizer) throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_REPORTS))
      return READRESULT_NOTALLOWED;

    if (queryParameters == null)
      queryParameters = new HashMap<String,List<String>>();

    // Jobs (specified by id)
    Long[] jobs;
    List<String> jobList = queryParameters.get("job");
    if (jobList == null)
      jobs = new Long[0];
    else
    {
      jobs = new Long[jobList.size()];
      for (int i = 0; i < jobs.length; i++)
      {
        jobs[i] = new Long(jobList.get(i));
      }
    }

    // Now time
    long now;
    List<String> nowList = queryParameters.get("now");
    if (nowList == null || nowList.size() == 0)
      now = System.currentTimeMillis();
    else if (nowList.size() > 1)
      throw new ManifoldCFException("Multiple values for now parameter");
    else
      now = new Long(nowList.get(0)).longValue();
      
    // Identifier match
    RegExpCriteria idMatch;
    List<String> idMatchList = queryParameters.get("idmatch");
    List<String> idMatchInsensitiveList = queryParameters.get("idmatch_insensitive");
    if (idMatchList != null && idMatchInsensitiveList != null)
      throw new ManifoldCFException("Either use idmatch or idmatch_insensitive, not both.");
    boolean isInsensitiveIdMatch;
    if (idMatchInsensitiveList != null)
    {
      idMatchList = idMatchInsensitiveList;
      isInsensitiveIdMatch = true;
    }
    else
      isInsensitiveIdMatch = false;
      
    if (idMatchList == null || idMatchList.size() == 0)
      idMatch = null;
    else if (idMatchList.size() > 1)
      throw new ManifoldCFException("Multiple id match regexps specified.");
    else
      idMatch = new RegExpCriteria(idMatchList.get(0),isInsensitiveIdMatch);

    List<String> stateMatchList = queryParameters.get("statematch");
    int[] matchStates;
    if (stateMatchList == null)
      matchStates = new int[0];
    else
    {
      matchStates = new int[stateMatchList.size()];
      for (int i = 0; i < matchStates.length; i++)
      {
        Integer value = docState.get(stateMatchList.get(i));
        if (value == null)
          throw new ManifoldCFException("Unrecognized state value: '"+stateMatchList.get(i)+"'");
        matchStates[i] = value.intValue();
      }
    }
      
    List<String> statusMatchList = queryParameters.get("statusmatch");
    int[] matchStatuses;
    if (statusMatchList == null)
      matchStatuses = new int[0];
    else
    {
      matchStatuses = new int[statusMatchList.size()];
      for (int i = 0; i < matchStatuses.length; i++)
      {
        Integer value = docStatus.get(statusMatchList.get(i));
        if (value == null)
          throw new ManifoldCFException("Unrecognized status value: '"+statusMatchList.get(i)+"'");
        matchStatuses[i] = value.intValue();
      }
    }
      
    StatusFilterCriteria filterCriteria = new StatusFilterCriteria(jobs,now,idMatch,matchStates,matchStatuses);
      
    // Look for sort order parameters...
    SortOrder sortOrder = new SortOrder();
    List<String> sortColumnsList = queryParameters.get("sortcolumn");
    List<String> sortColumnsDirList = queryParameters.get("sortcolumn_direction");
    if (sortColumnsList != null || sortColumnsDirList != null)
    {
      if (sortColumnsList == null || sortColumnsDirList == null || sortColumnsList.size() != sortColumnsDirList.size())
        throw new ManifoldCFException("sortcolumn and sortcolumn_direction must have the same cardinality.");
      for (int i = 0; i < sortColumnsList.size(); i++)
      {
        String column = sortColumnsList.get(i);
        String dir = sortColumnsDirList.get(i);
        int dirInt;
        if (dir.equals("ascending"))
          dirInt = SortOrder.SORT_ASCENDING;
        else if (dir.equals("descending"))
          dirInt = SortOrder.SORT_DESCENDING;
        else
          throw new ManifoldCFException("sortcolumn_direction must be 'ascending' or 'descending'.");
        sortOrder.addCriteria(column,dirInt);
      }
    }
      
    // Start row and row count
    int startRow;
    List<String> startRowList = queryParameters.get("startrow");
    if (startRowList == null || startRowList.size() == 0)
      startRow = 0;
    else if (startRowList.size() > 1)
      throw new ManifoldCFException("Multiple start rows specified.");
    else
      startRow = new Integer(startRowList.get(0)).intValue();
      
    int rowCount;
    List<String> rowCountList = queryParameters.get("rowcount");
    if (rowCountList == null || rowCountList.size() == 0)
      rowCount = 20;
    else if (rowCountList.size() > 1)
      throw new ManifoldCFException("Multiple row counts specified.");
    else
      rowCount = new Integer(rowCountList.get(0)).intValue();

    List<String> reportTypeList = queryParameters.get("report");
    String reportType;
    if (reportTypeList == null || reportTypeList.size() == 0)
      reportType = "document";
    else if (reportTypeList.size() > 1)
      throw new ManifoldCFException("Multiple report types specified.");
    else
      reportType = reportTypeList.get(0);

    IJobManager jobManager = JobManagerFactory.make(tc);
      
    IResultSet result;
    String[] resultColumns;
      
    if (reportType.equals("document"))
    {
      try
      {
        result = jobManager.genDocumentStatus(connectionName,filterCriteria,sortOrder,startRow,rowCount);
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
        return READRESULT_FOUND;
      }
      resultColumns = new String[]{"identifier","job","state","status","scheduled","action","retrycount","retrylimit"};
    }
    else if (reportType.equals("status"))
    {
      BucketDescription idBucket;
      List<String> idBucketList = queryParameters.get("idbucket");
      List<String> idBucketInsensitiveList = queryParameters.get("idbucket_insensitive");
      if (idBucketList != null && idBucketInsensitiveList != null)
        throw new ManifoldCFException("Either use idbucket or idbucket_insensitive, not both.");
      boolean isInsensitiveIdBucket;
      if (idBucketInsensitiveList != null)
      {
        idBucketList = idBucketInsensitiveList;
        isInsensitiveIdBucket = true;
      }
      else
        isInsensitiveIdBucket = false;
      if (idBucketList == null || idBucketList.size() == 0)
        idBucket = new BucketDescription("()",false);
      else if (idBucketList.size() > 1)
        throw new ManifoldCFException("Multiple idbucket regexps specified.");
      else
        idBucket = new BucketDescription(idBucketList.get(0),isInsensitiveIdBucket);
        
      try
      {
        result = jobManager.genQueueStatus(connectionName,filterCriteria,sortOrder,idBucket,startRow,rowCount);
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
        return READRESULT_FOUND;
      }
      resultColumns = new String[]{"idbucket","inactive","processing","expiring","deleting",
        "processready","expireready","processwaiting","expirewaiting","waitingforever","hopcountexceeded"};
    }
    else
      throw new ManifoldCFException("Unknown report type '"+reportType+"'.");

    createResultsetNode(output,result,resultColumns);
    return READRESULT_FOUND;
  }
  
  /** Get jobs for connection */
  protected static int apiReadRepositoryConnectionJobs(IThreadContext tc, Configuration output,
    String connectionName, IAuthorizer authorizer) throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_JOBS))
      return READRESULT_NOTALLOWED;

    try
    {
      IJobManager jobManager = JobManagerFactory.make(tc);
      IJobDescription[] jobs = jobManager.findJobsForConnection(connectionName);
      if (jobs == null)
      {
        createErrorNode(output,"Unknown connection '"+connectionName+"'");
        return READRESULT_NOTFOUND;
      }
      int i = 0;
      while (i < jobs.length)
      {
        ConfigurationNode jobNode = new ConfigurationNode(API_JOBNODE);
        formatJobDescription(jobNode,jobs[i++]);
        output.addChild(output.getChildCount(),jobNode);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }
  
  /** History reports */
  protected static int apiReadRepositoryConnectionHistory(IThreadContext tc, Configuration output,
    String connectionName, Map<String,List<String>> queryParameters, IAuthorizer authorizer) throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_REPORTS))
      return READRESULT_NOTALLOWED;

    if (queryParameters == null)
      queryParameters = new HashMap<String,List<String>>();
      
    // Look for filter criteria parameters...
      
    // Start time
    List<String> startTimeList = queryParameters.get("starttime");
    Long startTime;
    if (startTimeList == null || startTimeList.size() == 0)
      startTime = null;
    else if (startTimeList.size() > 1)
      throw new ManifoldCFException("Multiple start times specified.");
    else
      startTime = new Long(startTimeList.get(0));

    // End time
    List<String> endTimeList = queryParameters.get("endtime");
    Long endTime;
    if (endTimeList == null || endTimeList.size() == 0)
      endTime = null;
    else if (endTimeList.size() > 1)
      throw new ManifoldCFException("Multiple end times specified.");
    else
      endTime = new Long(endTimeList.get(0));
      
    // Activities
    List<String> activityList = queryParameters.get("activity");
    String[] activities;
    if (activityList == null)
      activities = new String[0];
    else
      activities = activityList.toArray(new String[0]);
      
    // Entity match
    RegExpCriteria entityMatch;
    List<String> entityMatchList = queryParameters.get("entitymatch");
    List<String> entityMatchInsensitiveList = queryParameters.get("entitymatch_insensitive");
    if (entityMatchList != null && entityMatchInsensitiveList != null)
      throw new ManifoldCFException("Either use entitymatch or entitymatch_insensitive, not both.");
    boolean isInsensitiveEntityMatch;
    if (entityMatchInsensitiveList != null)
    {
      entityMatchList = entityMatchInsensitiveList;
      isInsensitiveEntityMatch = true;
    }
    else
      isInsensitiveEntityMatch = false;
      
    if (entityMatchList == null || entityMatchList.size() == 0)
      entityMatch = null;
    else if (entityMatchList.size() > 1)
      throw new ManifoldCFException("Multiple entity match regexps specified.");
    else
      entityMatch = new RegExpCriteria(entityMatchList.get(0),isInsensitiveEntityMatch);
      
    // Result code match
    RegExpCriteria resultCodeMatch;
    List<String> resultCodeMatchList = queryParameters.get("resultcodematch");
    List<String> resultCodeMatchInsensitiveList = queryParameters.get("resultcodematch_insensitive");
    if (resultCodeMatchList != null && resultCodeMatchInsensitiveList != null)
      throw new ManifoldCFException("Either use resultcodematch or resultcodematch_insensitive, not both.");
    boolean isInsensitiveResultCodeMatch;
    if (entityMatchInsensitiveList != null)
    {
      resultCodeMatchList = resultCodeMatchInsensitiveList;
      isInsensitiveResultCodeMatch = true;
    }
    else
      isInsensitiveResultCodeMatch = false;
      
    if (resultCodeMatchList == null || resultCodeMatchList.size() == 0)
      resultCodeMatch = null;
    else if (resultCodeMatchList.size() > 1)
      throw new ManifoldCFException("Multiple resultcode match regexps specified.");
    else
      resultCodeMatch = new RegExpCriteria(resultCodeMatchList.get(0),isInsensitiveResultCodeMatch);
      
    // Filter criteria
    FilterCriteria filterCriteria = new FilterCriteria(activities,startTime,endTime,entityMatch,resultCodeMatch);
      
    // Look for sort order parameters...
    SortOrder sortOrder = new SortOrder();
    List<String> sortColumnsList = queryParameters.get("sortcolumn");
    List<String> sortColumnsDirList = queryParameters.get("sortcolumn_direction");
    if (sortColumnsList != null || sortColumnsDirList != null)
    {
      if (sortColumnsList == null || sortColumnsDirList == null || sortColumnsList.size() != sortColumnsDirList.size())
        throw new ManifoldCFException("sortcolumn and sortcolumn_direction must have the same cardinality.");
      for (int i = 0; i < sortColumnsList.size(); i++)
      {
        String column = sortColumnsList.get(i);
        String dir = sortColumnsDirList.get(i);
        int dirInt;
        if (dir.equals("ascending"))
          dirInt = SortOrder.SORT_ASCENDING;
        else if (dir.equals("descending"))
          dirInt = SortOrder.SORT_DESCENDING;
        else
          throw new ManifoldCFException("sortcolumn_direction must be 'ascending' or 'descending'.");
        sortOrder.addCriteria(column,dirInt);
      }
    }
      
    // Start row and row count
    int startRow;
    List<String> startRowList = queryParameters.get("startrow");
    if (startRowList == null || startRowList.size() == 0)
      startRow = 0;
    else if (startRowList.size() > 1)
      throw new ManifoldCFException("Multiple start rows specified.");
    else
      startRow = new Integer(startRowList.get(0)).intValue();
      
    int rowCount;
    List<String> rowCountList = queryParameters.get("rowcount");
    if (rowCountList == null || rowCountList.size() == 0)
      rowCount = 20;
    else if (rowCountList.size() > 1)
      throw new ManifoldCFException("Multiple row counts specified.");
    else
      rowCount = new Integer(rowCountList.get(0)).intValue();

    List<String> reportTypeList = queryParameters.get("report");
    String reportType;
    if (reportTypeList == null || reportTypeList.size() == 0)
      reportType = "simple";
    else if (reportTypeList.size() > 1)
      throw new ManifoldCFException("Multiple report types specified.");
    else
      reportType = reportTypeList.get(0);

    IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(tc);
      
    IResultSet result;
    String[] resultColumns;
      
    if (reportType.equals("simple"))
    {
      try
      {
        result = connectionManager.genHistorySimple(connectionName,filterCriteria,sortOrder,startRow,rowCount);
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
        return READRESULT_FOUND;
      }
      resultColumns = new String[]{"starttime","resultcode","resultdesc","identifier","activity","bytes","elapsedtime"};
    }
    else if (reportType.equals("maxactivity"))
    {
      long maxInterval = connectionManager.getMaxRows();
      long actualRows = connectionManager.countHistoryRows(connectionName,filterCriteria);
      if (actualRows > maxInterval)
        throw new ManifoldCFException("Too many history rows specified for maxactivity report - actual is "+actualRows+", max is "+maxInterval+".");
        
      BucketDescription idBucket;
      List<String> idBucketList = queryParameters.get("idbucket");
      List<String> idBucketInsensitiveList = queryParameters.get("idbucket_insensitive");
      if (idBucketList != null && idBucketInsensitiveList != null)
        throw new ManifoldCFException("Either use idbucket or idbucket_insensitive, not both.");
      boolean isInsensitiveIdBucket;
      if (idBucketInsensitiveList != null)
      {
        idBucketList = idBucketInsensitiveList;
        isInsensitiveIdBucket = true;
      }
      else
        isInsensitiveIdBucket = false;
      if (idBucketList == null || idBucketList.size() == 0)
        idBucket = new BucketDescription("()",false);
      else if (idBucketList.size() > 1)
        throw new ManifoldCFException("Multiple idbucket regexps specified.");
      else
        idBucket = new BucketDescription(idBucketList.get(0),isInsensitiveIdBucket);

      long interval;
      List<String> intervalList = queryParameters.get("interval");
      if (intervalList == null || intervalList.size() == 0)
        interval = 300000L;
      else if (intervalList.size() > 1)
        throw new ManifoldCFException("Multiple intervals specified.");
      else
        interval = new Long(intervalList.get(0)).longValue();
        
      try
      {
        result = connectionManager.genHistoryActivityCount(connectionName,filterCriteria,sortOrder,idBucket,interval,startRow,rowCount);
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
        return READRESULT_FOUND;
      }
      resultColumns = new String[]{"starttime","endtime","activitycount","idbucket"};
    }
    else if (reportType.equals("maxbandwidth"))
    {
      long maxInterval = connectionManager.getMaxRows();
      long actualRows = connectionManager.countHistoryRows(connectionName,filterCriteria);
      if (actualRows > maxInterval)
        throw new ManifoldCFException("Too many history rows specified for maxbandwidth report - actual is "+actualRows+", max is "+maxInterval+".");
        
      BucketDescription idBucket;
      List<String> idBucketList = queryParameters.get("idbucket");
      List<String> idBucketInsensitiveList = queryParameters.get("idbucket_insensitive");
      if (idBucketList != null && idBucketInsensitiveList != null)
        throw new ManifoldCFException("Either use idbucket or idbucket_insensitive, not both.");
      boolean isInsensitiveIdBucket;
      if (idBucketInsensitiveList != null)
      {
        idBucketList = idBucketInsensitiveList;
        isInsensitiveIdBucket = true;
      }
      else
        isInsensitiveIdBucket = false;
      if (idBucketList == null || idBucketList.size() == 0)
        idBucket = new BucketDescription("()",false);
      else if (idBucketList.size() > 1)
        throw new ManifoldCFException("Multiple idbucket regexps specified.");
      else
        idBucket = new BucketDescription(idBucketList.get(0),isInsensitiveIdBucket);
        
      long interval;
      List<String> intervalList = queryParameters.get("interval");
      if (intervalList == null || intervalList.size() == 0)
        interval = 300000L;
      else if (intervalList.size() > 1)
        throw new ManifoldCFException("Multiple intervals specified.");
      else
        interval = new Long(intervalList.get(0)).longValue();

      try
      {
        result = connectionManager.genHistoryByteCount(connectionName,filterCriteria,sortOrder,idBucket,interval,startRow,rowCount);
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
        return READRESULT_FOUND;
      }
      resultColumns = new String[]{"starttime","endtime","bytecount","idbucket"};
    }
    else if (reportType.equals("result"))
    {
      BucketDescription idBucket;
      List<String> idBucketList = queryParameters.get("idbucket");
      List<String> idBucketInsensitiveList = queryParameters.get("idbucket_insensitive");
      if (idBucketList != null && idBucketInsensitiveList != null)
        throw new ManifoldCFException("Either use idbucket or idbucket_insensitive, not both.");
      boolean isInsensitiveIdBucket;
      if (idBucketInsensitiveList != null)
      {
        idBucketList = idBucketInsensitiveList;
        isInsensitiveIdBucket = true;
      }
      else
        isInsensitiveIdBucket = false;
      if (idBucketList == null || idBucketList.size() == 0)
        idBucket = new BucketDescription("()",false);
      else if (idBucketList.size() > 1)
        throw new ManifoldCFException("Multiple idbucket regexps specified.");
      else
        idBucket = new BucketDescription(idBucketList.get(0),isInsensitiveIdBucket);

      BucketDescription resultCodeBucket;
      List<String> resultCodeBucketList = queryParameters.get("resultcodebucket");
      List<String> resultCodeBucketInsensitiveList = queryParameters.get("resultcodebucket_insensitive");
      if (resultCodeBucketList != null && resultCodeBucketInsensitiveList != null)
        throw new ManifoldCFException("Either use resultcodebucket or resultcodebucket_insensitive, not both.");
      boolean isInsensitiveResultCodeBucket;
      if (resultCodeBucketInsensitiveList != null)
      {
        resultCodeBucketList = resultCodeBucketInsensitiveList;
        isInsensitiveResultCodeBucket = true;
      }
      else
        isInsensitiveResultCodeBucket = false;
      if (resultCodeBucketList == null || resultCodeBucketList.size() == 0)
        resultCodeBucket = new BucketDescription("(.*)",false);
      else if (resultCodeBucketList.size() > 1)
        throw new ManifoldCFException("Multiple resultcodebucket regexps specified.");
      else
        resultCodeBucket = new BucketDescription(resultCodeBucketList.get(0),isInsensitiveResultCodeBucket);

      try
      {
        result = connectionManager.genHistoryResultCodes(connectionName,filterCriteria,sortOrder,resultCodeBucket,idBucket,startRow,rowCount);
      }
      catch (ManifoldCFException e)
      {
        createErrorNode(output,e);
        return READRESULT_FOUND;
      }
      resultColumns = new String[]{"idbucket","resultcodebucket","eventcount"};
    }
    else
      throw new ManifoldCFException("Unknown report type '"+reportType+"'.");

    createResultsetNode(output,result,resultColumns);
    return READRESULT_FOUND;
  }
  
  /** Add a resultset node to the output. */
  protected static void createResultsetNode(Configuration output, IResultSet result, String[] resultColumns)
    throws ManifoldCFException
  {
    // Go through result set and add results to output
    for (int i = 0; i < result.getRowCount(); i++)
    {
      IResultRow row = result.getRow(i);
      ConfigurationNode rowValue = new ConfigurationNode(API_ROWNODE);
      for (String columnName : resultColumns)
      {
        ConfigurationNode columnValue = new ConfigurationNode(API_COLUMNNODE);
        Object value = row.getValue(columnName);
        String valueToUse;
        if (value == null)
          valueToUse = "";
        else
          valueToUse = value.toString();
        ConfigurationNode nameNode = new ConfigurationNode(API_NAMENODE);
        nameNode.setValue(columnName);
        columnValue.addChild(columnValue.getChildCount(),nameNode);
        ConfigurationNode valueNode = new ConfigurationNode(API_VALUENODE);
        valueNode.setValue(valueToUse);
        columnValue.addChild(columnValue.getChildCount(),valueNode);
        rowValue.addChild(rowValue.getChildCount(),columnValue);
      }
      output.addChild(output.getChildCount(),rowValue);
    }
  }
  
  /** Read the activity list for a given connection name. */
  protected static int apiReadRepositoryConnectionActivities(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_VIEW_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      String[] activities = getActivitiesList(tc,connectionName);
      if (activities == null)
      {
        createErrorNode(output,"Connection '"+connectionName+"' does not exist.");
        return READRESULT_NOTFOUND;
      }
      for (String activity : activities)
      {
        ConfigurationNode node = new ConfigurationNode(API_ACTIVITYNODE);
        node.setValue(activity);
        output.addChild(output.getChildCount(),node);
      }
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return READRESULT_FOUND;
  }
  
  /** Execute specified read command.
  *@param tc is the thread context.
  *@param output is the output object, to be filled in.
  *@param path is the object path.
  *@return read status - either found, not found, or bad args
  */
  public static int executeReadCommand(IThreadContext tc, Configuration output, String path,
    Map<String,List<String>> queryParameters, IAuthorizer authorizer) throws ManifoldCFException
  {
    if (path.equals("jobs"))
    {
      return apiReadJobs(tc,output,authorizer);
    }
    else if (path.startsWith("jobs/"))
    {
      Long jobID = new Long(path.substring("jobs/".length()));
      return apiReadJob(tc,output,jobID,authorizer);
    }
    else if (path.startsWith("repositoryconnectionactivities/"))
    {
      int firstSeparator = "repositoryconnectionactivities/".length();
      String connectionName = decodeAPIPathElement(path.substring(firstSeparator));
      return apiReadRepositoryConnectionActivities(tc,output,connectionName,authorizer);
    }
    else if (path.startsWith("repositoryconnectionhistory/"))
    {
      int firstSeparator = "repositoryconnectionhistory/".length();
      String connectionName = decodeAPIPathElement(path.substring(firstSeparator));
      return apiReadRepositoryConnectionHistory(tc,output,connectionName,queryParameters,authorizer);
    }
    else if (path.startsWith("repositoryconnectionqueue/"))
    {
      int firstSeparator = "repositoryconnectionqueue/".length();
      String connectionName = decodeAPIPathElement(path.substring(firstSeparator));
      return apiReadRepositoryConnectionQueue(tc,output,connectionName,queryParameters,authorizer);
    }
    else if (path.startsWith("repositoryconnectionjobs/"))
    {
      int firstSeparator = "repositoryconnectionjobs/".length();
      String connectionName = decodeAPIPathElement(path.substring(firstSeparator));
      return apiReadRepositoryConnectionJobs(tc,output,connectionName,authorizer);
    }
    else if (path.startsWith("status/"))
    {
      int firstSeparator = "status/".length();
      int secondSeparator = path.indexOf("/",firstSeparator);
      if (secondSeparator == -1)
      {
        createErrorNode(output,"Need connection name.");
        return READRESULT_NOTFOUND;
      }
      
      String connectionType = path.substring(firstSeparator,secondSeparator);
      String connectionName = decodeAPIPathElement(path.substring(secondSeparator+1));
      
      if (connectionType.equals("outputconnections"))
      {
        return apiReadOutputConnectionStatus(tc,output,connectionName,authorizer);
      }
      else if (connectionType.equals("transformationconnections"))
      {
        return apiReadTransformationConnectionStatus(tc,output,connectionName,authorizer);
      }
      else if (connectionType.equals("mappingconnections"))
      {
        return apiReadMappingConnectionStatus(tc,output,connectionName,authorizer);
      }
      else if (connectionType.equals("authorityconnections"))
      {
        return apiReadAuthorityConnectionStatus(tc,output,connectionName,authorizer);
      }
      else if (connectionType.equals("repositoryconnections"))
      {
        return apiReadRepositoryConnectionStatus(tc,output,connectionName,authorizer);
      }
      else if (connectionType.equals("notificationconnections"))
      {
        return apiReadNotificationConnectionStatus(tc,output,connectionName,authorizer);
      }
      else
      {
        createErrorNode(output,"Unknown connection type '"+connectionType+"'.");
        return READRESULT_NOTFOUND;
      }
    }
    else if (path.startsWith("info/"))
    {
      int firstSeparator = "info/".length();
      int secondSeparator = path.indexOf("/",firstSeparator);
      if (secondSeparator == -1)
      {
        createErrorNode(output,"Need connection type and connection name.");
        return READRESULT_NOTFOUND;
      }

      int thirdSeparator = path.indexOf("/",secondSeparator+1);
      if (thirdSeparator == -1)
      {
        createErrorNode(output,"Need connection name.");
        return READRESULT_NOTFOUND;
      }

      String connectionType = path.substring(firstSeparator,secondSeparator);
      String connectionName = decodeAPIPathElement(path.substring(secondSeparator+1,thirdSeparator));
      String command = path.substring(thirdSeparator+1);
      
      if (connectionType.equals("outputconnections"))
      {
        return apiReadOutputConnectionInfo(tc,output,connectionName,command,authorizer);
      }
      else if (connectionType.equals("transformationconnections"))
      {
        return apiReadTransformationConnectionInfo(tc,output,connectionName,command,authorizer);
      }
      else if (connectionType.equals("repositoryconnections"))
      {
        return apiReadRepositoryConnectionInfo(tc,output,connectionName,command,authorizer);
      }
      else if (connectionType.equals("notificationconnections"))
      {
        return apiReadNotificationConnectionInfo(tc,output,connectionName,command,authorizer);
      }
      else
      {
        createErrorNode(output,"Unknown connection type '"+connectionType+"'.");
        return READRESULT_NOTFOUND;
      }
    }
    else if (path.equals("jobstatuses"))
    {
      return apiReadJobStatuses(tc,output,queryParameters,authorizer);
    }
    else if (path.startsWith("jobstatuses/"))
    {
      Long jobID = new Long(path.substring("jobstatuses/".length()));
      return apiReadJobStatus(tc,output,jobID,queryParameters,authorizer);
    }
    else if (path.equals("jobstatusesnocounts"))
    {
      return apiReadJobStatusesNoCounts(tc,output,authorizer);
    }
    else if (path.startsWith("jobstatusesnocounts/"))
    {
      Long jobID = new Long(path.substring("jobstatusesnocounts/".length()));
      return apiReadJobStatusNoCounts(tc,output,jobID,authorizer);
    }
    else if (path.equals("authoritygroups"))
    {
      return apiReadAuthorityGroups(tc,output,authorizer);
    }
    else if (path.startsWith("authoritygroups/"))
    {
      String groupName = decodeAPIPathElement(path.substring("authoritygroups/".length()));
      return apiReadAuthorityGroup(tc,output,groupName,authorizer);
    }
    else if (path.equals("outputconnections"))
    {
      return apiReadOutputConnections(tc,output,authorizer);
    }
    else if (path.startsWith("outputconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("outputconnections/".length()));
      return apiReadOutputConnection(tc,output,connectionName,authorizer);
    }
    else if (path.equals("transformationconnections"))
    {
      return apiReadTransformationConnections(tc,output,authorizer);
    }
    else if (path.startsWith("transformationconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("transformationconnections/".length()));
      return apiReadTransformationConnection(tc,output,connectionName,authorizer);
    }
    else if (path.equals("mappingconnections"))
    {
      return apiReadMappingConnections(tc,output,authorizer);
    }
    else if (path.startsWith("mappingconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("mappingconnections/".length()));
      return apiReadMappingConnection(tc,output,connectionName,authorizer);
    }
    else if (path.equals("authorityconnections"))
    {
      return apiReadAuthorityConnections(tc,output,authorizer);
    }
    else if (path.startsWith("authorityconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("authorityconnections/".length()));
      return apiReadAuthorityConnection(tc,output,connectionName,authorizer);
    }
    else if (path.equals("repositoryconnections"))
    {
      return apiReadRepositoryConnections(tc,output,authorizer);
    }
    else if (path.startsWith("repositoryconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("repositoryconnections/".length()));
      return apiReadRepositoryConnection(tc,output,connectionName,authorizer);
    }
    else if (path.equals("notificationconnections"))
    {
      return apiReadNotificationConnections(tc,output,authorizer);
    }
    else if (path.startsWith("notificationconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("notificationconnections/".length()));
      return apiReadNotificationConnection(tc,output,connectionName,authorizer);
    }
    else if (path.equals("outputconnectors"))
    {
      return apiReadOutputConnectors(tc,output,authorizer);
    }
    else if (path.equals("transformationconnectors"))
    {
      return apiReadTransformationConnectors(tc,output,authorizer);
    }
    else if (path.equals("mappingconnectors"))
    {
      return apiReadMappingConnectors(tc,output,authorizer);
    }
    else if (path.equals("authorityconnectors"))
    {
      return apiReadAuthorityConnectors(tc,output,authorizer);
    }
    else if (path.equals("repositoryconnectors"))
    {
      return apiReadRepositoryConnectors(tc,output,authorizer);
    }
    else if (path.equals("notificationconnectors"))
    {
      return apiReadNotificationConnectors(tc,output,authorizer);
    }
    else if (path.equals("authorizationdomains"))
    {
      return apiReadAuthorizationDomains(tc,output,authorizer);
    }
    else
    {
      createErrorNode(output,"Unrecognized resource.");
      return READRESULT_NOTFOUND;
    }
  }
  
  // Post result codes
  public static final int POSTRESULT_NOTFOUND = 0;
  public static final int POSTRESULT_FOUND = 1;
  public static final int POSTRESULT_CREATED = 2;
  public static final int POSTRESULT_NOTALLOWED = 3;
  
  /** Post job.
  */
  protected static int apiPostJob(IThreadContext tc, Configuration output, Configuration input, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_JOBS))
      return READRESULT_NOTALLOWED;

    ConfigurationNode jobNode = findConfigurationNode(input,API_JOBNODE);
    if (jobNode == null)
      throw new ManifoldCFException("Input must have '"+API_JOBNODE+"' field");

    // Turn the configuration node into a JobDescription
    org.apache.manifoldcf.crawler.jobs.JobDescription job = new org.apache.manifoldcf.crawler.jobs.JobDescription();
    processJobDescription(job,jobNode);
      
    if (job.getID() != null)
      throw new ManifoldCFException("Input job cannot supply an ID field for create");
      
    try
    {
      Long jobID = new Long(IDFactory.make(tc));
      job.setID(jobID);
      job.setIsNew(true);
        
      // Save the job.
      IJobManager jobManager = JobManagerFactory.make(tc);
      jobManager.save(job);

      ConfigurationNode idNode = new ConfigurationNode(API_JOBIDNODE);
      idNode.setValue(jobID.toString());
      output.addChild(output.getChildCount(),idNode);
        
      return POSTRESULT_CREATED;
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return POSTRESULT_FOUND;
  }
  
  /** Execute specified post command.
  *@param tc is the thread context.
  *@param output is the output object, to be filled in.
  *@param path is the object path.
  *@param input is the input object.
  *@return write result - either "not found", "found", or "created".
  */
  public static int executePostCommand(IThreadContext tc, Configuration output, String path, Configuration input, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (path.equals("jobs"))
    {
      return apiPostJob(tc,output,input,authorizer);
    }
    else
    {
      createErrorNode(output,"Unrecognized resource.");
      return POSTRESULT_NOTFOUND;
    }
  }

  // Write result codes
  public static final int WRITERESULT_NOTFOUND = 0;
  public static final int WRITERESULT_FOUND = 1;
  public static final int WRITERESULT_CREATED = 2;
  public static final int WRITERESULT_NOTALLOWED = 3;
  
  /** Start a job.
  */
  protected static int apiWriteStartJob(IThreadContext tc, Configuration output, Long jobID, boolean requestMinimum, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_RUN_JOBS))
      return READRESULT_NOTALLOWED;

    try
    {
      IJobManager jobManager = JobManagerFactory.make(tc);
      jobManager.manualStart(jobID,requestMinimum);
      return WRITERESULT_CREATED;
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return WRITERESULT_FOUND;
  }
  
  /** Abort a job.
  */
  protected static int apiWriteAbortJob(IThreadContext tc, Configuration output, Long jobID, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_RUN_JOBS))
      return READRESULT_NOTALLOWED;
    
    try
    {
      IJobManager jobManager = JobManagerFactory.make(tc);
      jobManager.manualAbort(jobID);
      return WRITERESULT_CREATED;
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return WRITERESULT_FOUND;
  }
  
  /** Restart a job.
  */
  protected static int apiWriteRestartJob(IThreadContext tc, Configuration output, Long jobID, boolean requestMinimum, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_RUN_JOBS))
      return READRESULT_NOTALLOWED;

    try
    {
      IJobManager jobManager = JobManagerFactory.make(tc);
      jobManager.manualAbortRestart(jobID,requestMinimum);
      return WRITERESULT_CREATED;
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return WRITERESULT_FOUND;
  }

  /** Pause a job.
  */
  protected static int apiWritePauseJob(IThreadContext tc, Configuration output, Long jobID, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_RUN_JOBS))
      return READRESULT_NOTALLOWED;

    try
    {
      IJobManager jobManager = JobManagerFactory.make(tc);
      jobManager.pauseJob(jobID);
      return WRITERESULT_CREATED;
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return WRITERESULT_FOUND;
  }

  /** Resume a job.
  */
  protected static int apiWriteResumeJob(IThreadContext tc, Configuration output, Long jobID, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_RUN_JOBS))
      return READRESULT_NOTALLOWED;

    try
    {
      IJobManager jobManager = JobManagerFactory.make(tc);
      jobManager.restartJob(jobID);
      return WRITERESULT_CREATED;
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return WRITERESULT_FOUND;
  }

  /** Reset incremental seeding for a job.
  */
  protected static int apiWriteReseedJob(IThreadContext tc, Configuration output, Long jobID, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_RUN_JOBS))
      return READRESULT_NOTALLOWED;

    try
    {
      IJobManager jobManager = JobManagerFactory.make(tc);
      jobManager.clearJobSeedingState(jobID);
      return WRITERESULT_CREATED;
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return WRITERESULT_FOUND;
  }
  
  /** Write job.
  */
  protected static int apiWriteJob(IThreadContext tc, Configuration output, Configuration input, Long jobID, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_JOBS))
      return READRESULT_NOTALLOWED;

    ConfigurationNode jobNode = findConfigurationNode(input,API_JOBNODE);
    if (jobNode == null)
      throw new ManifoldCFException("Input must have '"+API_JOBNODE+"' field");

    // Turn the configuration node into a JobDescription
    org.apache.manifoldcf.crawler.jobs.JobDescription job = new org.apache.manifoldcf.crawler.jobs.JobDescription();
    processJobDescription(job,jobNode);
      
    try
    {
      if (job.getID() == null)
      {
        job.setID(jobID);
      }
      else
      {
        if (!job.getID().equals(jobID))
          throw new ManifoldCFException("Job identifier must agree within object and within path");
      }
        
      job.setIsNew(false);
        
      // Save the job.
      IJobManager jobManager = JobManagerFactory.make(tc);
      jobManager.save(job);
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return WRITERESULT_FOUND;
  }

  /** Write authority group.
  */
  protected static int apiWriteAuthorityGroup(IThreadContext tc, Configuration output, Configuration input, String groupName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    ConfigurationNode groupNode = findConfigurationNode(input,API_AUTHORITYGROUPNODE);
    if (groupNode == null)
      throw new ManifoldCFException("Input argument must have '"+API_AUTHORITYGROUPNODE+"' field");
      
    // Turn the configuration node into an AuthorityGroup
    org.apache.manifoldcf.authorities.authgroups.AuthorityGroup authorityGroup = new org.apache.manifoldcf.authorities.authgroups.AuthorityGroup();
    processAuthorityGroup(authorityGroup,groupNode);
      
    if (authorityGroup.getName() == null)
      authorityGroup.setName(groupName);
    else
    {
      if (!authorityGroup.getName().equals(groupName))
        throw new ManifoldCFException("Authority group name in path and in object must agree");
    }
      
    try
    {
      // Save the connection.
      IAuthorityGroupManager groupManager = AuthorityGroupManagerFactory.make(tc);
      if (groupManager.save(authorityGroup))
        return WRITERESULT_CREATED;
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return WRITERESULT_FOUND;
  }

  /** Write output connection.
  */
  protected static int apiWriteOutputConnection(IThreadContext tc, Configuration output, Configuration input, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    ConfigurationNode connectionNode = findConfigurationNode(input,API_OUTPUTCONNECTIONNODE);
    if (connectionNode == null)
      throw new ManifoldCFException("Input argument must have '"+API_OUTPUTCONNECTIONNODE+"' field");
      
    // Turn the configuration node into an OutputConnection
    org.apache.manifoldcf.agents.outputconnection.OutputConnection outputConnection = new org.apache.manifoldcf.agents.outputconnection.OutputConnection();
    processOutputConnection(outputConnection,connectionNode);
      
    if (outputConnection.getName() == null)
      outputConnection.setName(connectionName);
    else
    {
      if (!outputConnection.getName().equals(connectionName))
        throw new ManifoldCFException("Connection name in path and in object must agree");
    }
      
    try
    {
      // Save the connection.
      IOutputConnectionManager connectionManager = OutputConnectionManagerFactory.make(tc);
      if (connectionManager.save(outputConnection))
        return WRITERESULT_CREATED;
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return WRITERESULT_FOUND;
  }

  /** Write transformation connection.
  */
  protected static int apiWriteTransformationConnection(IThreadContext tc, Configuration output, Configuration input, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_CONNECTIONS))
      return READRESULT_NOTALLOWED;
    
    ConfigurationNode connectionNode = findConfigurationNode(input,API_TRANSFORMATIONCONNECTIONNODE);
    if (connectionNode == null)
      throw new ManifoldCFException("Input argument must have '"+API_TRANSFORMATIONCONNECTIONNODE+"' field");
      
    // Turn the configuration node into a TransformationConnection
    org.apache.manifoldcf.agents.transformationconnection.TransformationConnection transformationConnection = new org.apache.manifoldcf.agents.transformationconnection.TransformationConnection();
    processTransformationConnection(transformationConnection,connectionNode);
      
    if (transformationConnection.getName() == null)
      transformationConnection.setName(connectionName);
    else
    {
      if (!transformationConnection.getName().equals(connectionName))
        throw new ManifoldCFException("Connection name in path and in object must agree");
    }
      
    try
    {
      // Save the connection.
      ITransformationConnectionManager connectionManager = TransformationConnectionManagerFactory.make(tc);
      if (connectionManager.save(transformationConnection))
        return WRITERESULT_CREATED;
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return WRITERESULT_FOUND;
  }
  
  /** Write authority connection.
  */
  protected static int apiWriteAuthorityConnection(IThreadContext tc, Configuration output, Configuration input, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    ConfigurationNode connectionNode = findConfigurationNode(input,API_AUTHORITYCONNECTIONNODE);
    if (connectionNode == null)
      throw new ManifoldCFException("Input argument must have '"+API_AUTHORITYCONNECTIONNODE+"' field");
      
    // Turn the configuration node into an OutputConnection
    org.apache.manifoldcf.authorities.authority.AuthorityConnection authorityConnection = new org.apache.manifoldcf.authorities.authority.AuthorityConnection();
    processAuthorityConnection(authorityConnection,connectionNode);
      
    if (authorityConnection.getName() == null)
      authorityConnection.setName(connectionName);
    else
    {
      if (!authorityConnection.getName().equals(connectionName))
        throw new ManifoldCFException("Connection name in path and in object must agree");
    }
      
    try
    {
      // Save the connection.
      IAuthorityConnectionManager connectionManager = AuthorityConnectionManagerFactory.make(tc);
      if (connectionManager.save(authorityConnection))
        return WRITERESULT_CREATED;
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return WRITERESULT_FOUND;
  }
  
  /** Write mapping connection.
  */
  protected static int apiWriteMappingConnection(IThreadContext tc, Configuration output, Configuration input, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    ConfigurationNode connectionNode = findConfigurationNode(input,API_MAPPINGCONNECTIONNODE);
    if (connectionNode == null)
      throw new ManifoldCFException("Input argument must have '"+API_MAPPINGCONNECTIONNODE+"' field");
      
    // Turn the configuration node into an OutputConnection
    org.apache.manifoldcf.authorities.mapping.MappingConnection mappingConnection = new org.apache.manifoldcf.authorities.mapping.MappingConnection();
    processMappingConnection(mappingConnection,connectionNode);
      
    if (mappingConnection.getName() == null)
      mappingConnection.setName(connectionName);
    else
    {
      if (!mappingConnection.getName().equals(connectionName))
        throw new ManifoldCFException("Connection name in path and in object must agree");
    }
      
    try
    {
      // Save the connection.
      IMappingConnectionManager connectionManager = MappingConnectionManagerFactory.make(tc);
      if (connectionManager.save(mappingConnection))
        return WRITERESULT_CREATED;
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return WRITERESULT_FOUND;
  }

  /** Write repository connection.
  */
  protected static int apiWriteRepositoryConnection(IThreadContext tc, Configuration output, Configuration input, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    ConfigurationNode connectionNode = findConfigurationNode(input,API_REPOSITORYCONNECTIONNODE);
    if (connectionNode == null)
      throw new ManifoldCFException("Input argument must have '"+API_REPOSITORYCONNECTIONNODE+"' field");
      
    // Turn the configuration node into an OutputConnection
    org.apache.manifoldcf.crawler.repository.RepositoryConnection repositoryConnection = new org.apache.manifoldcf.crawler.repository.RepositoryConnection();
    processRepositoryConnection(repositoryConnection,connectionNode);
      
    if (repositoryConnection.getName() == null)
      repositoryConnection.setName(connectionName);
    else
    {
      if (!repositoryConnection.getName().equals(connectionName))
        throw new ManifoldCFException("Connection name in path and in object must agree");
    }

    try
    {
      // Save the connection.
      IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(tc);
      if (connectionManager.save(repositoryConnection))
        return WRITERESULT_CREATED;
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return WRITERESULT_FOUND;
  }

  /** Clear repository connection history.
  */
  protected static int apiWriteClearHistoryRepositoryConnection(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(tc);
      connectionManager.cleanUpHistoryData(connectionName);
      return WRITERESULT_CREATED;
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return WRITERESULT_FOUND;
  }

  /** Write notification connection.
  */
  protected static int apiWriteNotificationConnection(IThreadContext tc, Configuration output, Configuration input, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    ConfigurationNode connectionNode = findConfigurationNode(input,API_NOTIFICATIONCONNECTIONNODE);
    if (connectionNode == null)
      throw new ManifoldCFException("Input argument must have '"+API_NOTIFICATIONCONNECTIONNODE+"' field");
      
    // Turn the configuration node into an NotificationConnection
    org.apache.manifoldcf.crawler.notification.NotificationConnection notificationConnection = new org.apache.manifoldcf.crawler.notification.NotificationConnection();
    processNotificationConnection(notificationConnection,connectionNode);
      
    if (notificationConnection.getName() == null)
      notificationConnection.setName(connectionName);
    else
    {
      if (!notificationConnection.getName().equals(connectionName))
        throw new ManifoldCFException("Connection name in path and in object must agree");
    }

    try
    {
      // Save the connection.
      INotificationConnectionManager connectionManager = NotificationConnectionManagerFactory.make(tc);
      if (connectionManager.save(notificationConnection))
        return WRITERESULT_CREATED;
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return WRITERESULT_FOUND;
  }

  /** Reset output connection (reset version of all recorded documents).
  */
  protected static int apiWriteClearVersionsOutputConnection(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      signalOutputConnectionRedo(tc,connectionName);
      return WRITERESULT_CREATED;
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return WRITERESULT_FOUND;
  }

  /** Clear output connection (remove all recorded documents).
  */
  protected static int apiWriteClearOutputConnection(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      signalOutputConnectionRemoved(tc,connectionName);
      return WRITERESULT_CREATED;
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return WRITERESULT_FOUND;
  }
  
  /** Execute specified write command.
  *@param tc is the thread context.
  *@param output is the output object, to be filled in.
  *@param path is the object path.
  *@param input is the input object.
  *@return write result - either "not found", "found", or "created".
  */
  public static int executeWriteCommand(IThreadContext tc, Configuration output, String path, Configuration input, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (path.startsWith("start/"))
    {
      Long jobID = new Long(path.substring("start/".length()));
      return apiWriteStartJob(tc,output,jobID,false,authorizer);
    }
    else if (path.startsWith("startminimal/"))
    {
      Long jobID = new Long(path.substring("startminimal/".length()));
      return apiWriteStartJob(tc,output,jobID,true,authorizer);
    }
    else if (path.startsWith("abort/"))
    {
      Long jobID = new Long(path.substring("abort/".length()));
      return apiWriteAbortJob(tc,output,jobID,authorizer);
    }
    else if (path.startsWith("restart/"))
    {
      Long jobID = new Long(path.substring("restart/".length()));
      return apiWriteRestartJob(tc,output,jobID,false,authorizer);
    }
    else if (path.startsWith("restartminimal/"))
    {
      Long jobID = new Long(path.substring("restartminimal/".length()));
      return apiWriteRestartJob(tc,output,jobID,true,authorizer);
    }
    else if (path.startsWith("pause/"))
    {
      Long jobID = new Long(path.substring("pause/".length()));
      return apiWritePauseJob(tc,output,jobID,authorizer);
    }
    else if (path.startsWith("resume/"))
    {
      Long jobID = new Long(path.substring("resume/".length()));
      return apiWriteResumeJob(tc,output,jobID,authorizer);
    }
    else if (path.startsWith("reseed/"))
    {
      Long jobID = new Long(path.substring("reseed/".length()));
      return apiWriteReseedJob(tc,output,jobID,authorizer);
    }
    else if (path.startsWith("jobs/"))
    {
      Long jobID = new Long(path.substring("jobs/".length()));
      return apiWriteJob(tc,output,input,jobID,authorizer);
    }
    else if (path.startsWith("authoritygroups/"))
    {
      String groupName = decodeAPIPathElement(path.substring("authoritygroups/".length()));
      return apiWriteAuthorityGroup(tc,output,input,groupName,authorizer);
    }
    else if (path.startsWith("outputconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("outputconnections/".length()));
      return apiWriteOutputConnection(tc,output,input,connectionName,authorizer);
    }
    else if (path.startsWith("transformationconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("transformationconnections/".length()));
      return apiWriteTransformationConnection(tc,output,input,connectionName,authorizer);
    }
    else if (path.startsWith("mappingconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("mappingconnections/".length()));
      return apiWriteMappingConnection(tc,output,input,connectionName,authorizer);
    }
    else if (path.startsWith("authorityconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("authorityconnections/".length()));
      return apiWriteAuthorityConnection(tc,output,input,connectionName,authorizer);
    }
    else if (path.startsWith("repositoryconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("repositoryconnections/".length()));
      return apiWriteRepositoryConnection(tc,output,input,connectionName,authorizer);
    }
    else if (path.startsWith("notificationconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("notificationconnections/".length()));
      return apiWriteNotificationConnection(tc,output,input,connectionName,authorizer);
    }
    else if (path.startsWith("clearhistory/"))
    {
      int firstSeparator = "clearhistory/".length();
      String connectionName = decodeAPIPathElement(path.substring(firstSeparator));
      return apiWriteClearHistoryRepositoryConnection(tc,output,connectionName,authorizer);
    }
    else if (path.startsWith("reset/"))
    {
      // This form is deprecated
      int firstSeparator = "reset/".length();
      int secondSeparator = path.indexOf("/",firstSeparator);
      if (secondSeparator == -1)
      {
        createErrorNode(output,"Need connection name.");
        return WRITERESULT_NOTFOUND;
      }
      
      String connectionType = path.substring(firstSeparator,secondSeparator);
      String connectionName = decodeAPIPathElement(path.substring(secondSeparator+1));
      
      if (connectionType.equals("outputconnections"))
      {
        return apiWriteClearVersionsOutputConnection(tc,output,connectionName,authorizer);
      }
      else
      {
        createErrorNode(output,"Unknown connection type '"+connectionType+"'.");
        return WRITERESULT_NOTFOUND;
      }
    }
    else if (path.startsWith("clearversions/"))
    {
      int firstSeparator = "clearversions/".length();
      String connectionName = decodeAPIPathElement(path.substring(firstSeparator));
      return apiWriteClearVersionsOutputConnection(tc,output,connectionName,authorizer);
    }
    else if (path.startsWith("clearrecords/"))
    {
      int firstSeparator = "clearrecords/".length();
      String connectionName = decodeAPIPathElement(path.substring(firstSeparator));
      return apiWriteClearOutputConnection(tc,output,connectionName,authorizer);
    }
    else
    {
      createErrorNode(output,"Unrecognized resource.");
      return WRITERESULT_NOTFOUND;
    }
  }
  
  // Delete result codes
  public static final int DELETERESULT_NOTFOUND = 0;
  public static final int DELETERESULT_FOUND = 1;
  public static final int DELETERESULT_NOTALLOWED = 2;
  
  /** Delete a job.
  */
  protected static int apiDeleteJob(IThreadContext tc, Configuration output, Long jobID, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_JOBS))
      return READRESULT_NOTALLOWED;

    try
    {
      IJobManager jobManager = JobManagerFactory.make(tc);
      jobManager.deleteJob(jobID);
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return DELETERESULT_FOUND;
  }
  
  /** Delete authority group.
  */
  protected static int apiDeleteAuthorityGroup(IThreadContext tc, Configuration output, String groupName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IAuthorityGroupManager groupManager = AuthorityGroupManagerFactory.make(tc);
      groupManager.delete(groupName);
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return DELETERESULT_FOUND;
  }

  /** Delete output connection.
  */
  protected static int apiDeleteOutputConnection(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IOutputConnectionManager connectionManager = OutputConnectionManagerFactory.make(tc);
      connectionManager.delete(connectionName);
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return DELETERESULT_FOUND;
  }

  /** Delete authority connection.
  */
  protected static int apiDeleteAuthorityConnection(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IAuthorityConnectionManager connectionManager = AuthorityConnectionManagerFactory.make(tc);
      connectionManager.delete(connectionName);
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return DELETERESULT_FOUND;
  }
  
  /** Delete mapping connection.
  */
  protected static int apiDeleteMappingConnection(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IMappingConnectionManager connectionManager = MappingConnectionManagerFactory.make(tc);
      connectionManager.delete(connectionName);
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return DELETERESULT_FOUND;
  }

  /** Delete transformation connection.
  */
  protected static int apiDeleteTransformationConnection(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      ITransformationConnectionManager connectionManager = TransformationConnectionManagerFactory.make(tc);
      connectionManager.delete(connectionName);
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return DELETERESULT_FOUND;
  }

  /** Delete repository connection.
  */
  protected static int apiDeleteRepositoryConnection(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      IRepositoryConnectionManager connectionManager = RepositoryConnectionManagerFactory.make(tc);
      connectionManager.delete(connectionName);
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return DELETERESULT_FOUND;
  }

  /** Delete notification connection.
  */
  protected static int apiDeleteNotificationConnection(IThreadContext tc, Configuration output, String connectionName, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (!authorizer.checkAllowed(tc, IAuthorizer.CAPABILITY_EDIT_CONNECTIONS))
      return READRESULT_NOTALLOWED;

    try
    {
      INotificationConnectionManager connectionManager = NotificationConnectionManagerFactory.make(tc);
      connectionManager.delete(connectionName);
    }
    catch (ManifoldCFException e)
    {
      createErrorNode(output,e);
    }
    return DELETERESULT_FOUND;
  }
  
  /** Execute specified delete command.
  *@param tc is the thread context.
  *@param output is the output object, to be filled in.
  *@param path is the object path.
  *@return delete result code
  */
  public static int executeDeleteCommand(IThreadContext tc, Configuration output, String path, IAuthorizer authorizer)
    throws ManifoldCFException
  {
    if (path.startsWith("jobs/"))
    {
      Long jobID = new Long(path.substring("jobs/".length()));
      return apiDeleteJob(tc,output,jobID,authorizer);
    }
    else if (path.startsWith("authoritygroups/"))
    {
      String groupName = decodeAPIPathElement(path.substring("authoritygroups/".length()));
      return apiDeleteAuthorityGroup(tc,output,groupName,authorizer);
    }
    else if (path.startsWith("outputconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("outputconnections/".length()));
      return apiDeleteOutputConnection(tc,output,connectionName,authorizer);
    }
    else if (path.startsWith("mappingconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("mappingconnections/".length()));
      return apiDeleteAuthorityConnection(tc,output,connectionName,authorizer);
    }
    else if (path.startsWith("authorityconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("authorityconnections/".length()));
      return apiDeleteAuthorityConnection(tc,output,connectionName,authorizer);
    }
    else if (path.startsWith("transformationconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("transformationconnections/".length()));
      return apiDeleteTransformationConnection(tc,output,connectionName,authorizer);
    }
    else if (path.startsWith("repositoryconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("repositoryconnections/".length()));
      return apiDeleteRepositoryConnection(tc,output,connectionName,authorizer);
    }
    else if (path.startsWith("notificationconnections/"))
    {
      String connectionName = decodeAPIPathElement(path.substring("notificationconnections/".length()));
      return apiDeleteNotificationConnection(tc,output,connectionName,authorizer);
    }
    else
    {
      createErrorNode(output,"Unrecognized resource.");
      return DELETERESULT_NOTFOUND;
    }
  }
  
  // The following chunk of code is responsible for formatting a job description into a set of nodes, and for reading back a formatted job description.
  // This is needed to support the job-related API methods, above.
  
  // Job node types
  protected static final String JOBNODE_ID = "id";
  protected static final String JOBNODE_DESCRIPTION = "description";
  protected static final String JOBNODE_CONNECTIONNAME = "repository_connection";
  protected static final String JOBNODE_DOCUMENTSPECIFICATION = "document_specification";
  protected static final String JOBNODE_STARTMODE = "start_mode";
  protected static final String JOBNODE_RUNMODE = "run_mode";
  protected static final String JOBNODE_HOPCOUNTMODE = "hopcount_mode";
  protected static final String JOBNODE_PRIORITY = "priority";
  protected static final String JOBNODE_RECRAWLINTERVAL = "recrawl_interval";
  protected static final String JOBNODE_MAXRECRAWLINTERVAL = "max_recrawl_interval";
  protected static final String JOBNODE_EXPIRATIONINTERVAL = "expiration_interval";
  protected static final String JOBNODE_RESEEDINTERVAL = "reseed_interval";
  protected static final String JOBNODE_HOPCOUNT = "hopcount";
  protected static final String JOBNODE_SCHEDULE = "schedule";
  protected static final String JOBNODE_LINKTYPE = "link_type";
  protected static final String JOBNODE_COUNT = "count";
  protected static final String JOBNODE_REQUESTMINIMUM = "requestminimum";
  protected static final String JOBNODE_TIMEZONE = "timezone";
  protected static final String JOBNODE_DURATION = "duration";
  protected static final String JOBNODE_DAYOFWEEK = "dayofweek";
  protected static final String JOBNODE_MONTHOFYEAR = "monthofyear";
  protected static final String JOBNODE_DAYOFMONTH = "dayofmonth";
  protected static final String JOBNODE_YEAR = "year";
  protected static final String JOBNODE_HOUROFDAY = "hourofday";
  protected static final String JOBNODE_MINUTESOFHOUR = "minutesofhour";
  protected static final String JOBNODE_ENUMVALUE = "value";
  protected static final String JOBNODE_PARAMNAME = "paramname";
  protected static final String JOBNODE_PARAMVALUE = "paramvalue";
  protected static final String JOBNODE_PIPELINESTAGE = "pipelinestage";
  protected static final String JOBNODE_STAGEID = "stage_id";
  protected static final String JOBNODE_STAGEPREREQUISITE = "stage_prerequisite";
  protected static final String JOBNODE_STAGEISOUTPUT = "stage_isoutput";
  protected static final String JOBNODE_STAGECONNECTIONNAME = "stage_connectionname";
  protected static final String JOBNODE_STAGEDESCRIPTION = "stage_description";
  protected static final String JOBNODE_STAGESPECIFICATION = "stage_specification";
  protected static final String JOBNODE_NOTIFICATIONSTAGE = "notificationstage";

  /** Convert a node into a job description.
  *@param jobDescription is the job to be filled in.
  *@param jobNode is the configuration node corresponding to the whole job itself.
  */
  protected static void processJobDescription(org.apache.manifoldcf.crawler.jobs.JobDescription jobDescription, ConfigurationNode jobNode)
    throws ManifoldCFException
  {
    // Walk through the node's children
    Map<String,PipelineStage> pipelineStages = new HashMap<String,PipelineStage>();
    for (int i = 0; i < jobNode.getChildCount(); i++)
    {
      ConfigurationNode child = jobNode.findChild(i);
      String childType = child.getType();
      if (childType.equals(JOBNODE_ID))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Job id node requires a value");
        jobDescription.setID(new Long(child.getValue()));
      }
      else if (childType.equals(JOBNODE_DESCRIPTION))
      {
        jobDescription.setDescription(child.getValue());
      }
      else if (childType.equals(JOBNODE_CONNECTIONNAME))
      {
        jobDescription.setConnectionName(child.getValue());
      }
      else if (childType.equals(JOBNODE_PIPELINESTAGE))
      {
        String stageID = null;
        String stagePrerequisite = null;
        String stageIsOutput = null;
        String stageConnectionName = null;
        String stageDescription = null;
        ConfigurationNode stageSpecification = null;
        for (int q = 0; q < child.getChildCount(); q++)
        {
          ConfigurationNode cn = child.findChild(q);
          if (cn.getType().equals(JOBNODE_STAGEID))
            stageID = cn.getValue();
          else if (cn.getType().equals(JOBNODE_STAGEPREREQUISITE))
            stagePrerequisite = cn.getValue();
          else if (cn.getType().equals(JOBNODE_STAGEISOUTPUT))
            stageIsOutput = cn.getValue();
          else if (cn.getType().equals(JOBNODE_STAGECONNECTIONNAME))
            stageConnectionName = cn.getValue();
          else if (cn.getType().equals(JOBNODE_STAGEDESCRIPTION))
            stageDescription = cn.getValue();
          else if (cn.getType().equals(JOBNODE_STAGESPECIFICATION))
          {
            stageSpecification = cn;
          }
          else
            throw new ManifoldCFException("Found an unexpected node type: '"+cn.getType()+"'");
        }
        if (stageID == null)
          throw new ManifoldCFException("Missing required field: '"+JOBNODE_STAGEID+"'");
        if (stageIsOutput == null)
          throw new ManifoldCFException("Missing required field: '"+JOBNODE_STAGEISOUTPUT+"'");
        if (stageConnectionName == null)
          throw new ManifoldCFException("Missing required field: '"+JOBNODE_STAGECONNECTIONNAME+"'");
        pipelineStages.put(stageID,new PipelineStage(stagePrerequisite,stageIsOutput.equals("true"),
          stageConnectionName,stageDescription,stageSpecification));
      }
      else if (childType.equals(JOBNODE_NOTIFICATIONSTAGE))
      {
        String stageConnectionName = null;
        String stageDescription = null;
        ConfigurationNode stageSpecification = null;
        for (int q = 0; q < child.getChildCount(); q++)
        {
          ConfigurationNode cn = child.findChild(q);
          if (cn.getType().equals(JOBNODE_STAGECONNECTIONNAME))
            stageConnectionName = cn.getValue();
          else if (cn.getType().equals(JOBNODE_STAGEDESCRIPTION))
            stageDescription = cn.getValue();
          else if (cn.getType().equals(JOBNODE_STAGESPECIFICATION))
          {
            stageSpecification = cn;
          }
          else
            throw new ManifoldCFException("Found an unexpected node type: '"+cn.getType()+"'");
        }
        if (stageConnectionName == null)
          throw new ManifoldCFException("Missing required field: '"+JOBNODE_STAGECONNECTIONNAME+"'");
        Specification os = jobDescription.addNotification(stageConnectionName,stageDescription);
        os.clearChildren();
        if (stageSpecification != null)
        {
          for (int j = 0; j < stageSpecification.getChildCount(); j++)
          {
            ConfigurationNode cn = stageSpecification.findChild(j);
            os.addChild(os.getChildCount(),new SpecificationNode(cn));
          }
        }

      }
      else if (childType.equals(JOBNODE_DOCUMENTSPECIFICATION))
      {
        // Get the job's document specification, clear out the children, and copy new ones from the child.
        Specification ds = jobDescription.getSpecification();
        ds.clearChildren();
        for (int j = 0; j < child.getChildCount(); j++)
        {
          ConfigurationNode cn = child.findChild(j);
          ds.addChild(ds.getChildCount(),new SpecificationNode(cn));
        }
      }
      else if (childType.equals(JOBNODE_STARTMODE))
      {
        jobDescription.setStartMethod(mapToStartMode(child.getValue()));
      }
      else if (childType.equals(JOBNODE_RUNMODE))
      {
        jobDescription.setType(mapToRunMode(child.getValue()));
      }
      else if (childType.equals(JOBNODE_HOPCOUNTMODE))
      {
        jobDescription.setHopcountMode(mapToHopcountMode(child.getValue()));
      }
      else if (childType.equals(JOBNODE_PRIORITY))
      {
        try
        {
          jobDescription.setPriority(Integer.parseInt(child.getValue()));
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException(e.getMessage(),e);
        }
      }
      else if (childType.equals(JOBNODE_RECRAWLINTERVAL))
      {
        jobDescription.setInterval(interpretInterval(child.getValue()));
      }
      else if (childType.equals(JOBNODE_MAXRECRAWLINTERVAL))
      {
        jobDescription.setMaxInterval(interpretInterval(child.getValue()));
      }
      else if (childType.equals(JOBNODE_EXPIRATIONINTERVAL))
      {
        jobDescription.setExpiration(interpretInterval(child.getValue()));
      }
      else if (childType.equals(JOBNODE_RESEEDINTERVAL))
      {
        jobDescription.setReseedInterval(interpretInterval(child.getValue()));
      }
      else if (childType.equals(JOBNODE_HOPCOUNT))
      {
        // Read the hopcount values
        String linkType = null;
        String hopCount = null;
        
        int q = 0;
        while (q < child.getChildCount())
        {
          ConfigurationNode cn = child.findChild(q++);
          if (cn.getType().equals(JOBNODE_LINKTYPE))
            linkType = cn.getValue();
          else if (cn.getType().equals(JOBNODE_COUNT))
            hopCount = cn.getValue();
          else
            throw new ManifoldCFException("Found an unexpected node type: '"+cn.getType()+"'");
        }
        if (linkType == null)
          throw new ManifoldCFException("Missing required field: '"+JOBNODE_LINKTYPE+"'");
        if (hopCount == null)
          throw new ManifoldCFException("Missing required field: '"+JOBNODE_COUNT+"'");
        jobDescription.addHopCountFilter(linkType,new Long(hopCount));
      }
      else if (childType.equals(JOBNODE_SCHEDULE))
      {
        // Create a schedule record.
        String timezone = null;
        Long duration = null;
        boolean requestMinimum = false;
        EnumeratedValues dayOfWeek = null;
        EnumeratedValues monthOfYear = null;
        EnumeratedValues dayOfMonth = null;
        EnumeratedValues year = null;
        EnumeratedValues hourOfDay = null;
        EnumeratedValues minutesOfHour = null;
            
        // Now, walk through children of the schedule node.
        int q = 0;
        while (q < child.getChildCount())
        {
          ConfigurationNode scheduleField = child.findChild(q++);
          String fieldType = scheduleField.getType();
          if (fieldType.equals(JOBNODE_REQUESTMINIMUM))
          {
            requestMinimum = scheduleField.getValue().equals("true");
          }
          else if (fieldType.equals(JOBNODE_TIMEZONE))
          {
            timezone = scheduleField.getValue();
          }
          else if (fieldType.equals(JOBNODE_DURATION))
          {
            duration = new Long(scheduleField.getValue());
          }
          else if (fieldType.equals(JOBNODE_DAYOFWEEK))
          {
            dayOfWeek = processEnumeratedValues(scheduleField);
          }
          else if (fieldType.equals(JOBNODE_MONTHOFYEAR))
          {
            monthOfYear = processEnumeratedValues(scheduleField);
          }
          else if (fieldType.equals(JOBNODE_YEAR))
          {
            year = processEnumeratedValues(scheduleField);
          }
          else if (fieldType.equals(JOBNODE_DAYOFMONTH))
          {
            dayOfMonth = processEnumeratedValues(scheduleField);
          }
          else if (fieldType.equals(JOBNODE_HOUROFDAY))
          {
            hourOfDay = processEnumeratedValues(scheduleField);
          }
          else if (fieldType.equals(JOBNODE_MINUTESOFHOUR))
          {
            minutesOfHour = processEnumeratedValues(scheduleField);
          }
          else
            throw new ManifoldCFException("Unrecognized field in schedule record: '"+fieldType+"'");
        }
        ScheduleRecord sr = new ScheduleRecord(dayOfWeek,monthOfYear,dayOfMonth,year,hourOfDay,minutesOfHour,timezone,duration,requestMinimum);
        // Add the schedule record to the job.
        jobDescription.addScheduleRecord(sr);
      }
      else
        throw new ManifoldCFException("Unrecognized job field: '"+childType+"'");
    }
    
    // Do pipeline stages.  These must be ordered so that the prerequisites are always done first.
    List<String> orderedStageNames = new ArrayList<String>();
    Set<String> keysSeen = new HashSet<String>();
    for (String stageName : pipelineStages.keySet())
    {
      PipelineStage ps = pipelineStages.get(stageName);
      if (keysSeen.contains(stageName))
        continue;
      // Look at the prerequisite; insert them beforehand if they aren't already there
      addStage(stageName,orderedStageNames,keysSeen,pipelineStages);
    }
    
    // Now, add stages to job in  order, and map to ordinals
    int k = 0;
    for (String stageName : orderedStageNames)
    {
      PipelineStage ps = pipelineStages.get(stageName);
      ps.ordinal = k++;
      int prerequisite = (ps.prerequisite == null)?-1:pipelineStages.get(ps.prerequisite).ordinal;
      Specification os = jobDescription.addPipelineStage(prerequisite,ps.isOutput,ps.connectionName,ps.description);
      os.clearChildren();
      if (ps.specification != null)
      {
        for (int j = 0; j < ps.specification.getChildCount(); j++)
        {
          ConfigurationNode cn = ps.specification.findChild(j);
          os.addChild(os.getChildCount(),new SpecificationNode(cn));
        }
      }
    }
  }

  protected static void addStage(String stageName, List<String> orderedStageNames, Set<String> keysSeen,
    Map<String,PipelineStage> pipelineStages)
    throws ManifoldCFException
  {
    if (keysSeen.contains(stageName))
      return;
    PipelineStage ps = pipelineStages.get(stageName);
    if (ps == null)
      throw new ManifoldCFException("Stage reference error: '"+stageName+"' is unknown");
    if (ps.prerequisite != null)
      addStage(ps.prerequisite,orderedStageNames,keysSeen,pipelineStages);
    // All prerequisites added!
    orderedStageNames.add(stageName);
    keysSeen.add(stageName);
  }
  
  protected static class PipelineStage
  {
    public final String prerequisite;
    public final boolean isOutput;
    public final String connectionName;
    public final String description;
    public final ConfigurationNode specification;
    public int ordinal;
    
    public PipelineStage(String prerequisite, boolean isOutput, String connectionName, String description, ConfigurationNode specification)
    {
      this.prerequisite = prerequisite;
      this.isOutput = isOutput;
      this.connectionName = connectionName;
      this.description = description;
      this.specification = specification;
    }
    
  }

  /** Convert a job description into a ConfigurationNode.
  *@param jobNode is the node to be filled in.
  *@param job is the job description.
  */
  protected static void formatJobDescription(ConfigurationNode jobNode, IJobDescription job)
  {
    // For each field of the job, add an appropriate child node, with value.
    ConfigurationNode child;
    
    // id
    if (job.getID() != null)
    {
      child = new ConfigurationNode(JOBNODE_ID);
      child.setValue(job.getID().toString());
      jobNode.addChild(jobNode.getChildCount(),child);
    }
    
    // description
    if (job.getDescription() != null)
    {
      child = new ConfigurationNode(JOBNODE_DESCRIPTION);
      child.setValue(job.getDescription());
      jobNode.addChild(jobNode.getChildCount(),child);
    }
    
    // connection
    if (job.getConnectionName() != null)
    {
      child = new ConfigurationNode(JOBNODE_CONNECTIONNAME);
      child.setValue(job.getConnectionName());
      jobNode.addChild(jobNode.getChildCount(),child);
    }

    // Document specification
    Specification ds = job.getSpecification();
    child = new ConfigurationNode(JOBNODE_DOCUMENTSPECIFICATION);
    for (int j = 0; j < ds.getChildCount(); j++)
    {
      ConfigurationNode cn = ds.getChild(j);
      child.addChild(child.getChildCount(),cn);
    }
    jobNode.addChild(jobNode.getChildCount(),child);

    // Pipeline stages
    for (int j = 0; j < job.countPipelineStages(); j++)
    {
      child = new ConfigurationNode(JOBNODE_PIPELINESTAGE);
      ConfigurationNode stage;
      stage = new ConfigurationNode(JOBNODE_STAGEID);
      stage.setValue(Integer.toString(j));
      child.addChild(child.getChildCount(),stage);
      if (job.getPipelineStagePrerequisite(j) != -1)
      {
        stage = new ConfigurationNode(JOBNODE_STAGEPREREQUISITE);
        stage.setValue(Integer.toString(job.getPipelineStagePrerequisite(j)));
        child.addChild(child.getChildCount(),stage);
      }
      stage = new ConfigurationNode(JOBNODE_STAGEISOUTPUT);
      stage.setValue(job.getPipelineStageIsOutputConnection(j)?"true":"false");
      child.addChild(child.getChildCount(),stage);
      stage = new ConfigurationNode(JOBNODE_STAGECONNECTIONNAME);
      stage.setValue(job.getPipelineStageConnectionName(j));
      child.addChild(child.getChildCount(),stage);
      String description = job.getPipelineStageDescription(j);
      if (description != null)
      {
        stage = new ConfigurationNode(JOBNODE_STAGEDESCRIPTION);
        stage.setValue(description);
        child.addChild(child.getChildCount(),stage);
      }
      Specification spec = job.getPipelineStageSpecification(j);
      stage = new ConfigurationNode(JOBNODE_STAGESPECIFICATION);
      for (int k = 0; k < spec.getChildCount(); k++)
      {
        ConfigurationNode cn = spec.getChild(k);
        stage.addChild(stage.getChildCount(),cn);
      }
      child.addChild(child.getChildCount(),stage);
      jobNode.addChild(jobNode.getChildCount(),child);
    }

    for (int j = 0; j < job.countNotifications(); j++)
    {
      child = new ConfigurationNode(JOBNODE_NOTIFICATIONSTAGE);
      ConfigurationNode stage;
      stage = new ConfigurationNode(JOBNODE_STAGECONNECTIONNAME);
      stage.setValue(job.getNotificationConnectionName(j));
      child.addChild(child.getChildCount(),stage);
      String description = job.getNotificationDescription(j);
      if (description != null)
      {
        stage = new ConfigurationNode(JOBNODE_STAGEDESCRIPTION);
        stage.setValue(description);
        child.addChild(child.getChildCount(),stage);
      }
      Specification spec = job.getNotificationSpecification(j);
      stage = new ConfigurationNode(JOBNODE_STAGESPECIFICATION);
      for (int k = 0; k < spec.getChildCount(); k++)
      {
        ConfigurationNode cn = spec.getChild(k);
        stage.addChild(stage.getChildCount(),cn);
      }
      child.addChild(child.getChildCount(),stage);
      jobNode.addChild(jobNode.getChildCount(),child);
    }

    // Start mode
    child = new ConfigurationNode(JOBNODE_STARTMODE);
    child.setValue(startModeMap(job.getStartMethod()));
    jobNode.addChild(jobNode.getChildCount(),child);

    // Run mode
    child = new ConfigurationNode(JOBNODE_RUNMODE);
    child.setValue(runModeMap(job.getType()));
    jobNode.addChild(jobNode.getChildCount(),child);

    // Hopcount mode
    child = new ConfigurationNode(JOBNODE_HOPCOUNTMODE);
    child.setValue(hopcountModeMap(job.getHopcountMode()));
    jobNode.addChild(jobNode.getChildCount(),child);

    // Priority
    child = new ConfigurationNode(JOBNODE_PRIORITY);
    child.setValue(Integer.toString(job.getPriority()));
    jobNode.addChild(jobNode.getChildCount(),child);

    // Recrawl interval
    child = new ConfigurationNode(JOBNODE_RECRAWLINTERVAL);
    child.setValue((job.getInterval()==null)?"infinite":job.getInterval().toString());
    jobNode.addChild(jobNode.getChildCount(),child);

    // Max recrawl interval
    child = new ConfigurationNode(JOBNODE_MAXRECRAWLINTERVAL);
    child.setValue((job.getMaxInterval()==null)?"infinite":job.getMaxInterval().toString());
    jobNode.addChild(jobNode.getChildCount(),child);

    child = new ConfigurationNode(JOBNODE_EXPIRATIONINTERVAL);
    child.setValue((job.getExpiration()==null)?"infinite":job.getExpiration().toString());
    jobNode.addChild(jobNode.getChildCount(),child);

    child = new ConfigurationNode(JOBNODE_RESEEDINTERVAL);
    child.setValue((job.getReseedInterval()==null)?"infinite":job.getReseedInterval().toString());
    jobNode.addChild(jobNode.getChildCount(),child);

    // Hopcount records
    Map filters = job.getHopCountFilters();
    Iterator iter = filters.keySet().iterator();
    while (iter.hasNext())
    {
      String linkType = (String)iter.next();
      Long hopCount = (Long)filters.get(linkType);
      child = new ConfigurationNode(JOBNODE_HOPCOUNT);
      ConfigurationNode cn;
      cn = new ConfigurationNode(JOBNODE_LINKTYPE);
      cn.setValue(linkType);
      child.addChild(child.getChildCount(),cn);
      cn = new ConfigurationNode(JOBNODE_COUNT);
      cn.setValue(hopCount.toString());
      child.addChild(child.getChildCount(),cn);
      jobNode.addChild(jobNode.getChildCount(),child);
    }
    
    // Schedule records
    for (int j = 0; j < job.getScheduleRecordCount(); j++)
    {
      ScheduleRecord sr = job.getScheduleRecord(j);
      child = new ConfigurationNode(JOBNODE_SCHEDULE);
      ConfigurationNode recordChild;
      
      // requestminimum
      recordChild = new ConfigurationNode(JOBNODE_REQUESTMINIMUM);
      recordChild.setValue(sr.getRequestMinimum()?"true":"false");
      child.addChild(child.getChildCount(),recordChild);
      
      // timezone
      if (sr.getTimezone() != null)
      {
        recordChild = new ConfigurationNode(JOBNODE_TIMEZONE);
        recordChild.setValue(sr.getTimezone());
        child.addChild(child.getChildCount(),recordChild);
      }

      // duration
      if (sr.getDuration() != null)
      {
        recordChild = new ConfigurationNode(JOBNODE_DURATION);
        recordChild.setValue(sr.getDuration().toString());
        child.addChild(child.getChildCount(),recordChild);
      }
      
      // Schedule specification values
      
      // day of week
      if (sr.getDayOfWeek() != null)
        formatEnumeratedValues(child,JOBNODE_DAYOFWEEK,sr.getDayOfWeek());
      if (sr.getMonthOfYear() != null)
        formatEnumeratedValues(child,JOBNODE_MONTHOFYEAR,sr.getMonthOfYear());
      if (sr.getDayOfMonth() != null)
        formatEnumeratedValues(child,JOBNODE_DAYOFMONTH,sr.getDayOfMonth());
      if (sr.getYear() != null)
        formatEnumeratedValues(child,JOBNODE_YEAR,sr.getYear());
      if (sr.getHourOfDay() != null)
        formatEnumeratedValues(child,JOBNODE_HOUROFDAY,sr.getHourOfDay());
      if (sr.getMinutesOfHour() != null)
        formatEnumeratedValues(child,JOBNODE_MINUTESOFHOUR,sr.getMinutesOfHour());
      
      jobNode.addChild(jobNode.getChildCount(),child);
    }
  }

  protected static void formatEnumeratedValues(ConfigurationNode recordNode, String childType, EnumeratedValues value)
  {
    ConfigurationNode child = new ConfigurationNode(childType);
    Iterator iter = value.getValues();
    while (iter.hasNext())
    {
      Integer theValue = (Integer)iter.next();
      ConfigurationNode valueNode = new ConfigurationNode(JOBNODE_ENUMVALUE);
      valueNode.setValue(theValue.toString());
      child.addChild(child.getChildCount(),valueNode);
    }
    recordNode.addChild(recordNode.getChildCount(),child);
  }
  
  protected static EnumeratedValues processEnumeratedValues(ConfigurationNode fieldNode)
    throws ManifoldCFException
  {
    ArrayList values = new ArrayList();
    int i = 0;
    while (i < fieldNode.getChildCount())
    {
      ConfigurationNode cn = fieldNode.findChild(i++);
      if (cn.getType().equals(JOBNODE_ENUMVALUE))
      {
        try
        {
          values.add(new Integer(cn.getValue()));
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException("Error processing enumerated value node: "+e.getMessage(),e);
        }
      }
      else
        throw new ManifoldCFException("Error processing enumerated value nodes: Unrecognized node type '"+cn.getType()+"'");
    }
    return new EnumeratedValues(values);
  }
  
  protected static String presentInterval(Long interval)
  {
    if (interval == null)
      return "infinite";
    return interval.toString();
  }

  protected static Long interpretInterval(String interval)
    throws ManifoldCFException
  {
    if (interval == null || interval.equals("infinite"))
      return null;
    else
      return new Long(interval);
  }
  
  protected static String startModeMap(int startMethod)
  {
    switch (startMethod)
    {
    case IJobDescription.START_WINDOWBEGIN:
      return "schedule window start";
    case IJobDescription.START_WINDOWINSIDE:
      return "schedule window anytime";
    case IJobDescription.START_DISABLE:
      return "manual";
    default:
      return "unknown";
    }
  }

  protected static int mapToStartMode(String startMethod)
    throws ManifoldCFException
  {
    if (startMethod.equals("schedule window start"))
      return IJobDescription.START_WINDOWBEGIN;
    else if (startMethod.equals("schedule window anytime"))
      return IJobDescription.START_WINDOWINSIDE;
    else if (startMethod.equals("manual"))
      return IJobDescription.START_DISABLE;
    else
      throw new ManifoldCFException("Unrecognized start method: '"+startMethod+"'");
  }
  
  protected static String runModeMap(int type)
  {
    switch (type)
    {
    case IJobDescription.TYPE_CONTINUOUS:
      return "continuous";
    case IJobDescription.TYPE_SPECIFIED:
      return "scan once";
    default:
      return "unknown";
    }
  }

  protected static int mapToRunMode(String mode)
    throws ManifoldCFException
  {
    if (mode.equals("continuous"))
      return IJobDescription.TYPE_CONTINUOUS;
    else if (mode.equals("scan once"))
      return IJobDescription.TYPE_SPECIFIED;
    else
      throw new ManifoldCFException("Unrecognized run method: '"+mode+"'");
  }
  
  protected static String hopcountModeMap(int mode)
  {
    switch (mode)
    {
    case IJobDescription.HOPCOUNT_ACCURATE:
      return "accurate";
    case IJobDescription.HOPCOUNT_NODELETE:
      return "no delete";
    case IJobDescription.HOPCOUNT_NEVERDELETE:
      return "never delete";
    default:
      return "unknown";
    }
  }

  protected static int mapToHopcountMode(String mode)
    throws ManifoldCFException
  {
    if (mode.equals("accurate"))
      return IJobDescription.HOPCOUNT_ACCURATE;
    else if (mode.equals("no delete"))
      return IJobDescription.HOPCOUNT_NODELETE;
    else if (mode.equals("never delete"))
      return IJobDescription.HOPCOUNT_NEVERDELETE;
    else
      throw new ManifoldCFException("Unrecognized hopcount method: '"+mode+"'");
  }
  
  // End of job API support code.
  
  // The following chunk of code supports job statuses in the API.  Only a formatting method is required, since we never "save" a status.

  // Node types used to handle job statuses.
  protected static final String JOBSTATUSNODE_JOBID = "job_id";
  protected static final String JOBSTATUSNODE_STATUS = "status";
  protected static final String JOBSTATUSNODE_ERRORTEXT = "errortext";
  protected static final String JOBSTATUSNODE_STARTTIME = "start_time";
  protected static final String JOBSTATUSNODE_ENDTIME = "end_time";
  protected static final String JOBSTATUSNODE_DOCUMENTSINQUEUE = "documents_in_queue";
  protected static final String JOBSTATUSNODE_DOCUMENTSOUTSTANDING = "documents_outstanding";
  protected static final String JOBSTATUSNODE_DOCUMENTSPROCESSED = "documents_processed";
  protected static final String JOBSTATUSNODE_QUEUEEXACT = "queue_exact";
  protected static final String JOBSTATUSNODE_OUTSTANDINGEXACT = "outstanding_exact";
  protected static final String JOBSTATUSNODE_PROCESSEDEXACT = "processed_exact";
  
  /** Format a job status.
  */
  protected static void formatJobStatus(ConfigurationNode jobStatusNode, JobStatus jobStatus)
  {
    // For each field of the job, add an appropriate child node, with value.
    ConfigurationNode child;
    int j;
    
    // id
    child = new ConfigurationNode(JOBSTATUSNODE_JOBID);
    child.setValue(jobStatus.getJobID().toString());
    jobStatusNode.addChild(jobStatusNode.getChildCount(),child);

    // status
    child = new ConfigurationNode(JOBSTATUSNODE_STATUS);
    child.setValue(statusMap(jobStatus.getStatus()));
    jobStatusNode.addChild(jobStatusNode.getChildCount(),child);

    // error text
    if (jobStatus.getErrorText() != null)
    {
      child = new ConfigurationNode(JOBSTATUSNODE_ERRORTEXT);
      child.setValue(jobStatus.getErrorText());
      jobStatusNode.addChild(jobStatusNode.getChildCount(),child);
    }
    
    // start time
    if (jobStatus.getStartTime() != -1L)
    {
      child = new ConfigurationNode(JOBSTATUSNODE_STARTTIME);
      child.setValue(new Long(jobStatus.getStartTime()).toString());
      jobStatusNode.addChild(jobStatusNode.getChildCount(),child);
    }
    
    // end time
    if (jobStatus.getEndTime() != -1L)
    {
      child = new ConfigurationNode(JOBSTATUSNODE_ENDTIME);
      child.setValue(new Long(jobStatus.getEndTime()).toString());
      jobStatusNode.addChild(jobStatusNode.getChildCount(),child);
    }

    // documents in queue
    child = new ConfigurationNode(JOBSTATUSNODE_DOCUMENTSINQUEUE);
    child.setValue(new Long(jobStatus.getDocumentsInQueue()).toString());
    jobStatusNode.addChild(jobStatusNode.getChildCount(),child);

    // documents outstanding
    child = new ConfigurationNode(JOBSTATUSNODE_DOCUMENTSOUTSTANDING);
    child.setValue(new Long(jobStatus.getDocumentsOutstanding()).toString());
    jobStatusNode.addChild(jobStatusNode.getChildCount(),child);

    // documents processed
    child = new ConfigurationNode(JOBSTATUSNODE_DOCUMENTSPROCESSED);
    child.setValue(new Long(jobStatus.getDocumentsProcessed()).toString());
    jobStatusNode.addChild(jobStatusNode.getChildCount(),child);

    // Exact flags
    child = new ConfigurationNode(JOBSTATUSNODE_QUEUEEXACT);
    child.setValue(new Boolean(jobStatus.getQueueCountExact()).toString());
    jobStatusNode.addChild(jobStatusNode.getChildCount(),child);

    child = new ConfigurationNode(JOBSTATUSNODE_OUTSTANDINGEXACT);
    child.setValue(new Boolean(jobStatus.getOutstandingCountExact()).toString());
    jobStatusNode.addChild(jobStatusNode.getChildCount(),child);

    child = new ConfigurationNode(JOBSTATUSNODE_PROCESSEDEXACT);
    child.setValue(new Boolean(jobStatus.getProcessedCountExact()).toString());
    jobStatusNode.addChild(jobStatusNode.getChildCount(),child);

  }

  protected static String statusMap(int status)
  {
    switch (status)
    {
    case JobStatus.JOBSTATUS_NOTYETRUN:
      return "not yet run";
    case JobStatus.JOBSTATUS_RUNNING:
      return "running";
    case JobStatus.JOBSTATUS_STOPPING:
      return "stopping";
    case JobStatus.JOBSTATUS_RESUMING:
      return "resuming";
    case JobStatus.JOBSTATUS_PAUSED:
      return "paused";
    case JobStatus.JOBSTATUS_COMPLETED:
      return "done";
    case JobStatus.JOBSTATUS_WINDOWWAIT:
      return "waiting";
    case JobStatus.JOBSTATUS_STARTING:
      return "starting up";
    case JobStatus.JOBSTATUS_DESTRUCTING:
      return "cleaning up";
    case JobStatus.JOBSTATUS_ERROR:
      return "error";
    case JobStatus.JOBSTATUS_ABORTING:
      return "aborting";
    case JobStatus.JOBSTATUS_RESTARTING:
      return "restarting";
    case JobStatus.JOBSTATUS_RUNNING_UNINSTALLED:
      return "running no connector";
    case JobStatus.JOBSTATUS_JOBENDCLEANUP:
      return "terminating";
    case JobStatus.JOBSTATUS_JOBENDNOTIFICATION:
      return "notifying";
    default:
      return "unknown";
    }
  }

  // End of jobstatus API support.
  
  // Authority group API
  
  protected static final String AUTHGROUPNODE_ISNEW = "isnew";
  protected static final String AUTHGROUPNODE_NAME = "name";
  protected static final String AUTHGROUPNODE_DESCRIPTION = "description";
  
  // Output connection API support.
  
  /** Convert input hierarchy into an AuthorityGroup object.
  */
  protected static void processAuthorityGroup(org.apache.manifoldcf.authorities.authgroups.AuthorityGroup group, ConfigurationNode groupNode)
    throws ManifoldCFException
  {
    // Walk through the node's children
    int i = 0;
    while (i < groupNode.getChildCount())
    {
      ConfigurationNode child = groupNode.findChild(i++);
      String childType = child.getType();
      if (childType.equals(AUTHGROUPNODE_ISNEW))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Authority group isnew node requires a value");
        group.setIsNew(child.getValue().equals("true"));
      }
      else if (childType.equals(AUTHGROUPNODE_NAME))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Authority group name node requires a value");
        group.setName(child.getValue());
      }
      else if (childType.equals(AUTHGROUPNODE_DESCRIPTION))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Authority group description node requires a value");
        group.setDescription(child.getValue());
      }
      else
        throw new ManifoldCFException("Unrecognized authority group field: '"+childType+"'");
    }

  }
  
  /** Format an authority group.
  */
  protected static void formatAuthorityGroup(ConfigurationNode groupNode, IAuthorityGroup group)
  {
    ConfigurationNode child;
    int j;

    child = new ConfigurationNode(AUTHGROUPNODE_ISNEW);
    child.setValue(group.getIsNew()?"true":"false");
    groupNode.addChild(groupNode.getChildCount(),child);

    child = new ConfigurationNode(AUTHGROUPNODE_NAME);
    child.setValue(group.getName());
    groupNode.addChild(groupNode.getChildCount(),child);

    if (group.getDescription() != null)
    {
      child = new ConfigurationNode(AUTHGROUPNODE_DESCRIPTION);
      child.setValue(group.getDescription());
      groupNode.addChild(groupNode.getChildCount(),child);
    }
    
  }

  // Connection API
  
  protected static final String CONNECTIONNODE_ISNEW = "isnew";
  protected static final String CONNECTIONNODE_NAME = "name";
  protected static final String CONNECTIONNODE_CLASSNAME = "class_name";
  protected static final String CONNECTIONNODE_MAXCONNECTIONS = "max_connections";
  protected static final String CONNECTIONNODE_DESCRIPTION = "description";
  protected static final String CONNECTIONNODE_PREREQUISITE = "prerequisite";
  protected static final String CONNECTIONNODE_CONFIGURATION = "configuration";
  protected static final String CONNECTIONNODE_ACLAUTHORITY = "acl_authority";
  protected static final String CONNECTIONNODE_THROTTLE = "throttle";
  protected static final String CONNECTIONNODE_MATCH = "match";
  protected static final String CONNECTIONNODE_MATCHDESCRIPTION = "match_description";
  protected static final String CONNECTIONNODE_RATE = "rate";
  protected static final String CONNECTIONNODE_AUTHDOMAIN = "authdomain";
  protected static final String CONNECTIONNODE_AUTHGROUP = "authgroup";
  
  // Output connection API support.
  
  /** Convert input hierarchy into an OutputConnection object.
  */
  protected static void processOutputConnection(org.apache.manifoldcf.agents.outputconnection.OutputConnection connection, ConfigurationNode connectionNode)
    throws ManifoldCFException
  {
    // Walk through the node's children
    int i = 0;
    while (i < connectionNode.getChildCount())
    {
      ConfigurationNode child = connectionNode.findChild(i++);
      String childType = child.getType();
      if (childType.equals(CONNECTIONNODE_ISNEW))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection isnew node requires a value");
        connection.setIsNew(child.getValue().equals("true"));
      }
      else if (childType.equals(CONNECTIONNODE_NAME))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection name node requires a value");
        connection.setName(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_CLASSNAME))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection classname node requires a value");
        connection.setClassName(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_MAXCONNECTIONS))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection maxconnections node requires a value");
        try
        {
          connection.setMaxConnections(Integer.parseInt(child.getValue()));
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException("Error parsing max connections: "+e.getMessage(),e);
        }
      }
      else if (childType.equals(CONNECTIONNODE_DESCRIPTION))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection description node requires a value");
        connection.setDescription(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_CONFIGURATION))
      {
        // Get the connection's configuration, clear out the children, and copy new ones from the child.
        ConfigParams cp = connection.getConfigParams();
        cp.clearChildren();
        int j = 0;
        while (j < child.getChildCount())
        {
          ConfigurationNode cn = child.findChild(j++);
          cp.addChild(cp.getChildCount(),new ConfigNode(cn));
        }
      }
      else
        throw new ManifoldCFException("Unrecognized output connection field: '"+childType+"'");
    }
    if (connection.getClassName() == null)
      throw new ManifoldCFException("Missing connection field: '"+CONNECTIONNODE_CLASSNAME+"'");

  }
  
  /** Format an output connection.
  */
  protected static void formatOutputConnection(ConfigurationNode connectionNode, IOutputConnection connection)
  {
    ConfigurationNode child;
    int j;

    child = new ConfigurationNode(CONNECTIONNODE_ISNEW);
    child.setValue(connection.getIsNew()?"true":"false");
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_NAME);
    child.setValue(connection.getName());
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_CLASSNAME);
    child.setValue(connection.getClassName());
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_MAXCONNECTIONS);
    child.setValue(Integer.toString(connection.getMaxConnections()));
    connectionNode.addChild(connectionNode.getChildCount(),child);

    if (connection.getDescription() != null)
    {
      child = new ConfigurationNode(CONNECTIONNODE_DESCRIPTION);
      child.setValue(connection.getDescription());
      connectionNode.addChild(connectionNode.getChildCount(),child);
    }
    
    ConfigParams cp = connection.getConfigParams();
    child = new ConfigurationNode(CONNECTIONNODE_CONFIGURATION);
    j = 0;
    while (j < cp.getChildCount())
    {
      ConfigurationNode cn = cp.findChild(j++);
      child.addChild(child.getChildCount(),cn);
    }
    connectionNode.addChild(connectionNode.getChildCount(),child);

  }

  // Transformation connection API support
  
    /** Convert input hierarchy into a TransformationConnection object.
  */
  protected static void processTransformationConnection(org.apache.manifoldcf.agents.transformationconnection.TransformationConnection connection, ConfigurationNode connectionNode)
    throws ManifoldCFException
  {
    // Walk through the node's children
    int i = 0;
    while (i < connectionNode.getChildCount())
    {
      ConfigurationNode child = connectionNode.findChild(i++);
      String childType = child.getType();
      if (childType.equals(CONNECTIONNODE_ISNEW))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection isnew node requires a value");
        connection.setIsNew(child.getValue().equals("true"));
      }
      else if (childType.equals(CONNECTIONNODE_NAME))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection name node requires a value");
        connection.setName(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_CLASSNAME))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection classname node requires a value");
        connection.setClassName(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_MAXCONNECTIONS))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection maxconnections node requires a value");
        try
        {
          connection.setMaxConnections(Integer.parseInt(child.getValue()));
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException("Error parsing max connections: "+e.getMessage(),e);
        }
      }
      else if (childType.equals(CONNECTIONNODE_DESCRIPTION))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection description node requires a value");
        connection.setDescription(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_CONFIGURATION))
      {
        // Get the connection's configuration, clear out the children, and copy new ones from the child.
        ConfigParams cp = connection.getConfigParams();
        cp.clearChildren();
        int j = 0;
        while (j < child.getChildCount())
        {
          ConfigurationNode cn = child.findChild(j++);
          cp.addChild(cp.getChildCount(),new ConfigNode(cn));
        }
      }
      else
        throw new ManifoldCFException("Unrecognized output connection field: '"+childType+"'");
    }
    if (connection.getClassName() == null)
      throw new ManifoldCFException("Missing connection field: '"+CONNECTIONNODE_CLASSNAME+"'");

  }

  /** Format a transformation connection.
  */
  protected static void formatTransformationConnection(ConfigurationNode connectionNode, ITransformationConnection connection)
  {
    ConfigurationNode child;
    int j;

    child = new ConfigurationNode(CONNECTIONNODE_ISNEW);
    child.setValue(connection.getIsNew()?"true":"false");
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_NAME);
    child.setValue(connection.getName());
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_CLASSNAME);
    child.setValue(connection.getClassName());
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_MAXCONNECTIONS);
    child.setValue(Integer.toString(connection.getMaxConnections()));
    connectionNode.addChild(connectionNode.getChildCount(),child);

    if (connection.getDescription() != null)
    {
      child = new ConfigurationNode(CONNECTIONNODE_DESCRIPTION);
      child.setValue(connection.getDescription());
      connectionNode.addChild(connectionNode.getChildCount(),child);
    }
    
    ConfigParams cp = connection.getConfigParams();
    child = new ConfigurationNode(CONNECTIONNODE_CONFIGURATION);
    j = 0;
    while (j < cp.getChildCount())
    {
      ConfigurationNode cn = cp.findChild(j++);
      child.addChild(child.getChildCount(),cn);
    }
    connectionNode.addChild(connectionNode.getChildCount(),child);

  }

  // Authority connection API support
  
  /** Convert input hierarchy into an AuthorityConnection object.
  */
  protected static void processAuthorityConnection(org.apache.manifoldcf.authorities.authority.AuthorityConnection connection, ConfigurationNode connectionNode)
    throws ManifoldCFException
  {
    // Walk through the node's children
    int i = 0;
    while (i < connectionNode.getChildCount())
    {
      ConfigurationNode child = connectionNode.findChild(i++);
      String childType = child.getType();
      if (childType.equals(CONNECTIONNODE_ISNEW))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection isnew node requires a value");
        connection.setIsNew(child.getValue().equals("true"));
      }
      else if (childType.equals(CONNECTIONNODE_NAME))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection name node requires a value");
        connection.setName(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_CLASSNAME))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection classname node requires a value");
        connection.setClassName(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_MAXCONNECTIONS))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection maxconnections node requires a value");
        try
        {
          connection.setMaxConnections(Integer.parseInt(child.getValue()));
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException("Error parsing max connections: "+e.getMessage(),e);
        }
      }
      else if (childType.equals(CONNECTIONNODE_PREREQUISITE))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection prerequisite node requires a value");
        connection.setPrerequisiteMapping(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_AUTHDOMAIN))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection authdomain node requires a value");
        connection.setAuthDomain(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_AUTHGROUP))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection authgroup node requires a value");
        connection.setAuthGroup(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_DESCRIPTION))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection description node requires a value");
        connection.setDescription(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_CONFIGURATION))
      {
        // Get the connection's configuration, clear out the children, and copy new ones from the child.
        ConfigParams cp = connection.getConfigParams();
        cp.clearChildren();
        int j = 0;
        while (j < child.getChildCount())
        {
          ConfigurationNode cn = child.findChild(j++);
          cp.addChild(cp.getChildCount(),new ConfigNode(cn));
        }
      }
      else
        throw new ManifoldCFException("Unrecognized authority connection field: '"+childType+"'");
    }
    if (connection.getClassName() == null)
      throw new ManifoldCFException("Missing connection field: '"+CONNECTIONNODE_CLASSNAME+"'");

  }


  /** Format an authority connection.
  */
  protected static void formatAuthorityConnection(ConfigurationNode connectionNode, IAuthorityConnection connection)
  {
    ConfigurationNode child;
    int j;
    
    child = new ConfigurationNode(CONNECTIONNODE_ISNEW);
    child.setValue(connection.getIsNew()?"true":"false");
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_NAME);
    child.setValue(connection.getName());
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_CLASSNAME);
    child.setValue(connection.getClassName());
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_MAXCONNECTIONS);
    child.setValue(Integer.toString(connection.getMaxConnections()));
    connectionNode.addChild(connectionNode.getChildCount(),child);

    if (connection.getPrerequisiteMapping() != null)
    {
      child = new ConfigurationNode(CONNECTIONNODE_PREREQUISITE);
      child.setValue(connection.getPrerequisiteMapping());
      connectionNode.addChild(connectionNode.getChildCount(),child);
    }

    if (connection.getDescription() != null)
    {
      child = new ConfigurationNode(CONNECTIONNODE_DESCRIPTION);
      child.setValue(connection.getDescription());
      connectionNode.addChild(connectionNode.getChildCount(),child);
    }
    
    if (connection.getAuthDomain() != null)
    {
      child = new ConfigurationNode(CONNECTIONNODE_AUTHDOMAIN);
      child.setValue(connection.getAuthDomain());
      connectionNode.addChild(connectionNode.getChildCount(),child);
    }
    
    child = new ConfigurationNode(CONNECTIONNODE_AUTHGROUP);
    child.setValue(connection.getAuthGroup());
    connectionNode.addChild(connectionNode.getChildCount(),child);

    ConfigParams cp = connection.getConfigParams();
    child = new ConfigurationNode(CONNECTIONNODE_CONFIGURATION);
    j = 0;
    while (j < cp.getChildCount())
    {
      ConfigurationNode cn = cp.findChild(j++);
      child.addChild(child.getChildCount(),cn);
    }
    connectionNode.addChild(connectionNode.getChildCount(),child);

  }

  // Mapping connection API methods
  
  /** Convert input hierarchy into an MappingConnection object.
  */
  protected static void processMappingConnection(org.apache.manifoldcf.authorities.mapping.MappingConnection connection, ConfigurationNode connectionNode)
    throws ManifoldCFException
  {
    // Walk through the node's children
    int i = 0;
    while (i < connectionNode.getChildCount())
    {
      ConfigurationNode child = connectionNode.findChild(i++);
      String childType = child.getType();
      if (childType.equals(CONNECTIONNODE_ISNEW))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection isnew node requires a value");
        connection.setIsNew(child.getValue().equals("true"));
      }
      else if (childType.equals(CONNECTIONNODE_NAME))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection name node requires a value");
        connection.setName(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_CLASSNAME))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection classname node requires a value");
        connection.setClassName(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_MAXCONNECTIONS))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection maxconnections node requires a value");
        try
        {
          connection.setMaxConnections(Integer.parseInt(child.getValue()));
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException("Error parsing max connections: "+e.getMessage(),e);
        }
      }
      else if (childType.equals(CONNECTIONNODE_PREREQUISITE))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection prerequisite node requires a value");
        connection.setPrerequisiteMapping(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_DESCRIPTION))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection description node requires a value");
        connection.setDescription(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_CONFIGURATION))
      {
        // Get the connection's configuration, clear out the children, and copy new ones from the child.
        ConfigParams cp = connection.getConfigParams();
        cp.clearChildren();
        int j = 0;
        while (j < child.getChildCount())
        {
          ConfigurationNode cn = child.findChild(j++);
          cp.addChild(cp.getChildCount(),new ConfigNode(cn));
        }
      }
      else
        throw new ManifoldCFException("Unrecognized mapping connection field: '"+childType+"'");
    }
    if (connection.getClassName() == null)
      throw new ManifoldCFException("Missing connection field: '"+CONNECTIONNODE_CLASSNAME+"'");

  }

  /** Format a mapping connection.
  */
  protected static void formatMappingConnection(ConfigurationNode connectionNode, IMappingConnection connection)
  {
    ConfigurationNode child;
    int j;
    
    child = new ConfigurationNode(CONNECTIONNODE_ISNEW);
    child.setValue(connection.getIsNew()?"true":"false");
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_NAME);
    child.setValue(connection.getName());
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_CLASSNAME);
    child.setValue(connection.getClassName());
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_MAXCONNECTIONS);
    child.setValue(Integer.toString(connection.getMaxConnections()));
    connectionNode.addChild(connectionNode.getChildCount(),child);

    if (connection.getPrerequisiteMapping() != null)
    {
      child = new ConfigurationNode(CONNECTIONNODE_PREREQUISITE);
      child.setValue(connection.getPrerequisiteMapping());
      connectionNode.addChild(connectionNode.getChildCount(),child);
    }
    
    if (connection.getDescription() != null)
    {
      child = new ConfigurationNode(CONNECTIONNODE_DESCRIPTION);
      child.setValue(connection.getDescription());
      connectionNode.addChild(connectionNode.getChildCount(),child);
    }
    
    ConfigParams cp = connection.getConfigParams();
    child = new ConfigurationNode(CONNECTIONNODE_CONFIGURATION);
    j = 0;
    while (j < cp.getChildCount())
    {
      ConfigurationNode cn = cp.findChild(j++);
      child.addChild(child.getChildCount(),cn);
    }
    connectionNode.addChild(connectionNode.getChildCount(),child);

  }

  // Repository connection API support methods
  
  /** Convert input hierarchy into a RepositoryConnection object.
  */
  protected static void processRepositoryConnection(org.apache.manifoldcf.crawler.repository.RepositoryConnection connection, ConfigurationNode connectionNode)
    throws ManifoldCFException
  {
    // Walk through the node's children
    int i = 0;
    while (i < connectionNode.getChildCount())
    {
      ConfigurationNode child = connectionNode.findChild(i++);
      String childType = child.getType();
      if (childType.equals(CONNECTIONNODE_ISNEW))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection isnew node requires a value");
        connection.setIsNew(child.getValue().equals("true"));
      }
      else if (childType.equals(CONNECTIONNODE_NAME))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection name node requires a value");
        connection.setName(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_CLASSNAME))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection classname node requires a value");
        connection.setClassName(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_MAXCONNECTIONS))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection maxconnections node requires a value");
        try
        {
          connection.setMaxConnections(Integer.parseInt(child.getValue()));
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException("Error parsing max connections: "+e.getMessage(),e);
        }
      }
      else if (childType.equals(CONNECTIONNODE_DESCRIPTION))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection description node requires a value");
        connection.setDescription(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_CONFIGURATION))
      {
        // Get the connection's configuration, clear out the children, and copy new ones from the child.
        ConfigParams cp = connection.getConfigParams();
        cp.clearChildren();
        int j = 0;
        while (j < child.getChildCount())
        {
          ConfigurationNode cn = child.findChild(j++);
          cp.addChild(cp.getChildCount(),new ConfigNode(cn));
        }
      }
      else if (childType.equals(CONNECTIONNODE_ACLAUTHORITY))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection aclauthority node requires a value");
        connection.setACLAuthority(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_THROTTLE))
      {
        String match = null;
        String description = null;
        Float rate = null;
            
        int q = 0;
        while (q < child.getChildCount())
        {
          ConfigurationNode throttleField = child.findChild(q++);
          String fieldType = throttleField.getType();
          if (fieldType.equals(CONNECTIONNODE_MATCH))
          {
            match = throttleField.getValue();
          }
          else if (fieldType.equals(CONNECTIONNODE_MATCHDESCRIPTION))
          {
            description = throttleField.getValue();
          }
          else if (fieldType.equals(CONNECTIONNODE_RATE))
          {
            rate = new Float(throttleField.getValue());
          }
          else
            throw new ManifoldCFException("Unrecognized throttle field: '"+fieldType+"'");
        }
        if (match == null)
          throw new ManifoldCFException("Missing throttle field: '"+CONNECTIONNODE_MATCH+"'");
        if (rate == null)
          throw new ManifoldCFException("Missing throttle field: '"+CONNECTIONNODE_RATE+"'");
        connection.addThrottleValue(match,description,rate.floatValue());
      }
      else
        throw new ManifoldCFException("Unrecognized repository connection field: '"+childType+"'");
    }
    if (connection.getClassName() == null)
      throw new ManifoldCFException("Missing connection field: '"+CONNECTIONNODE_CLASSNAME+"'");

  }
  
  /** Format a repository connection.
  */
  protected static void formatRepositoryConnection(ConfigurationNode connectionNode, IRepositoryConnection connection)
  {
    ConfigurationNode child;
    int j;

    child = new ConfigurationNode(CONNECTIONNODE_ISNEW);
    child.setValue(connection.getIsNew()?"true":"false");
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_NAME);
    child.setValue(connection.getName());
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_CLASSNAME);
    child.setValue(connection.getClassName());
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_MAXCONNECTIONS);
    child.setValue(Integer.toString(connection.getMaxConnections()));
    connectionNode.addChild(connectionNode.getChildCount(),child);

    if (connection.getDescription() != null)
    {
      child = new ConfigurationNode(CONNECTIONNODE_DESCRIPTION);
      child.setValue(connection.getDescription());
      connectionNode.addChild(connectionNode.getChildCount(),child);
    }
    
    ConfigParams cp = connection.getConfigParams();
    child = new ConfigurationNode(CONNECTIONNODE_CONFIGURATION);
    j = 0;
    while (j < cp.getChildCount())
    {
      ConfigurationNode cn = cp.findChild(j++);
      child.addChild(child.getChildCount(),cn);
    }
    connectionNode.addChild(connectionNode.getChildCount(),child);

    if (connection.getACLAuthority() != null)
    {
      child = new ConfigurationNode(CONNECTIONNODE_ACLAUTHORITY);
      child.setValue(connection.getACLAuthority());
      connectionNode.addChild(connectionNode.getChildCount(),child);
    }
    
    String[] throttles = connection.getThrottles();
    j = 0;
    while (j < throttles.length)
    {
      String match = throttles[j++];
      String description = connection.getThrottleDescription(match);
      float rate = connection.getThrottleValue(match);
      child = new ConfigurationNode(CONNECTIONNODE_THROTTLE);
      ConfigurationNode throttleChildNode;
      
      throttleChildNode = new ConfigurationNode(CONNECTIONNODE_MATCH);
      throttleChildNode.setValue(match);
      child.addChild(child.getChildCount(),throttleChildNode);
      
      if (description != null)
      {
        throttleChildNode = new ConfigurationNode(CONNECTIONNODE_MATCHDESCRIPTION);
        throttleChildNode.setValue(description);
        child.addChild(child.getChildCount(),throttleChildNode);
      }

      throttleChildNode = new ConfigurationNode(CONNECTIONNODE_RATE);
      throttleChildNode.setValue(new Float(rate).toString());
      child.addChild(child.getChildCount(),throttleChildNode);

      connectionNode.addChild(connectionNode.getChildCount(),child);
    }
    
  }

  // Notification connection node handling
  
  /** Convert input hierarchy into a NotificationConnection object.
  */
  protected static void processNotificationConnection(org.apache.manifoldcf.crawler.notification.NotificationConnection connection, ConfigurationNode connectionNode)
    throws ManifoldCFException
  {
    // Walk through the node's children
    int i = 0;
    while (i < connectionNode.getChildCount())
    {
      ConfigurationNode child = connectionNode.findChild(i++);
      String childType = child.getType();
      if (childType.equals(CONNECTIONNODE_ISNEW))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection isnew node requires a value");
        connection.setIsNew(child.getValue().equals("true"));
      }
      else if (childType.equals(CONNECTIONNODE_NAME))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection name node requires a value");
        connection.setName(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_CLASSNAME))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection classname node requires a value");
        connection.setClassName(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_MAXCONNECTIONS))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection maxconnections node requires a value");
        try
        {
          connection.setMaxConnections(Integer.parseInt(child.getValue()));
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException("Error parsing max connections: "+e.getMessage(),e);
        }
      }
      else if (childType.equals(CONNECTIONNODE_DESCRIPTION))
      {
        if (child.getValue() == null)
          throw new ManifoldCFException("Connection description node requires a value");
        connection.setDescription(child.getValue());
      }
      else if (childType.equals(CONNECTIONNODE_CONFIGURATION))
      {
        // Get the connection's configuration, clear out the children, and copy new ones from the child.
        ConfigParams cp = connection.getConfigParams();
        cp.clearChildren();
        int j = 0;
        while (j < child.getChildCount())
        {
          ConfigurationNode cn = child.findChild(j++);
          cp.addChild(cp.getChildCount(),new ConfigNode(cn));
        }
      }
      else
        throw new ManifoldCFException("Unrecognized notification connection field: '"+childType+"'");
    }
    if (connection.getClassName() == null)
      throw new ManifoldCFException("Missing connection field: '"+CONNECTIONNODE_CLASSNAME+"'");

  }

  /** Format a notification connection.
  */
  protected static void formatNotificationConnection(ConfigurationNode connectionNode, INotificationConnection connection)
  {
    ConfigurationNode child;
    int j;

    child = new ConfigurationNode(CONNECTIONNODE_ISNEW);
    child.setValue(connection.getIsNew()?"true":"false");
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_NAME);
    child.setValue(connection.getName());
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_CLASSNAME);
    child.setValue(connection.getClassName());
    connectionNode.addChild(connectionNode.getChildCount(),child);

    child = new ConfigurationNode(CONNECTIONNODE_MAXCONNECTIONS);
    child.setValue(Integer.toString(connection.getMaxConnections()));
    connectionNode.addChild(connectionNode.getChildCount(),child);

    if (connection.getDescription() != null)
    {
      child = new ConfigurationNode(CONNECTIONNODE_DESCRIPTION);
      child.setValue(connection.getDescription());
      connectionNode.addChild(connectionNode.getChildCount(),child);
    }
    
    ConfigParams cp = connection.getConfigParams();
    child = new ConfigurationNode(CONNECTIONNODE_CONFIGURATION);
    j = 0;
    while (j < cp.getChildCount())
    {
      ConfigurationNode cn = cp.findChild(j++);
      child.addChild(child.getChildCount(),cn);
    }
    connectionNode.addChild(connectionNode.getChildCount(),child);

  }

  // End of connection API code

}

