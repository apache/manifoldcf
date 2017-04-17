/* $Id: ManifoldCF.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.core.system;

import org.apache.manifoldcf.core.interfaces.*;
import java.lang.reflect.*;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class ManifoldCF
{
  public static final String _rcsid = "@(#)$Id: ManifoldCF.java 988245 2010-08-23 18:39:35Z kwright $";

  // Configuration XML node names and attribute names
  public static final String NODE_LIBDIR = "libdir";
  public static final String ATTRIBUTE_PATH = "path";
  
  // This is the unique process identifier, which has to be unique and repeatable within a cluster
  
  /** Process ID (no more than 16 characters) */
  protected static String processID = null;
  
  // "Working directory"
  
  /** This is the working directory file object. */
  protected static File workingDirectory = null;
  
  // Class loader
  
  /** The object that manages ManifoldCF plugin class loading.  This is initialized when the initialize method is called. */
  protected static ManifoldCFResourceLoader resourceLoader = null;

  // Shutdown hooks
  /** Temporary file collector */
  protected static FileTrack tracker = null;
  /** Database handle cleanup */
  protected static DatabaseShutdown dbShutdown = null;
  
  /** Array of cleanup hooks (for managing shutdown) */
  protected final static List<IShutdownHook> cleanupHooks = new ArrayList<IShutdownHook>(); 
  
  /** Array of polling hooks (for managing polling) */
  protected final static List<IPollingHook> pollingHooks = new ArrayList<IPollingHook>();
  
  /** Shutdown thread */
  protected static Thread shutdownThread;
  /** Static initializer for setting up shutdown thread */
  static
  {
    shutdownThread = new ShutdownThread();
    try
    {
      Runtime.getRuntime().addShutdownHook(shutdownThread);
    }
    catch (Exception e)
    {
      // Guess we can't do it - dump a trace and continue
      e.printStackTrace();
    }
    catch (Error e)
    {
      e.printStackTrace();
    }
  }
  
  // Flag indicating whether system initialized or not, and synchronizer to protect that flag.
  protected static int initializeLevel = 0;
  protected static boolean alreadyClosed = false;
  protected static boolean alreadyShutdown = false;
  protected static Integer initializeFlagLock = new Integer(0);

  // Local member variables
  protected static String mcfVersion = null;
  protected static String masterDatabaseName = null;
  protected static String masterDatabaseUsername = null;
  protected static String masterDatabasePassword = null;
  protected static ManifoldCFConfiguration localConfiguration = null;
  protected static long propertyFilelastMod = -1L;
  protected static String propertyFilePath = null;

  protected static final String applicationName = "lcf";

  // System property names
  public static final String lcfConfigFileProperty = "org.apache.manifoldcf.configfile";

  // System property/config file property names
  
  // Version property
  /** The current ManifoldCF version string */
  public static final String versionProperty = "org.apache.manifoldcf.versionstring";
  
  // Process ID property
  /** Process ID - cannot exceed 16 characters */
  public static final String processIDProperty = "org.apache.manifoldcf.processid";
  
  // Database access properties
  /** Database name property */
  public static final String masterDatabaseNameProperty = "org.apache.manifoldcf.database.name";
  /** Database user name property */
  public static final String masterDatabaseUsernameProperty = "org.apache.manifoldcf.database.username";
  /** Database password property */
  public static final String masterDatabasePasswordProperty = "org.apache.manifoldcf.database.password";

  // Database connection pooling properties
  /** Maximum open database handles property */
  public static final String databaseHandleMaxcountProperty = "org.apache.manifoldcf.database.maxhandles";
  /** Database handle timeout property */
  public static final String databaseHandleTimeoutProperty = "org.apache.manifoldcf.database.handletimeout";
  /** Connection tracking debug property */
  public static final String databaseConnectionTrackingProperty = "org.apache.manifoldcf.database.connectiontracking";

  // Database performance monitoring properties
  /** Elapsed time a query can take before a warning is output to the log, in seconds */
  public static final String databaseQueryMaxTimeProperty = "org.apache.manifoldcf.database.maxquerytime";
  
  // Log configuration properties
  /** Location of log configuration file */
  public static final String logConfigFileProperty = "org.apache.manifoldcf.logconfigfile";
  
  // File resources property
  /** Location of file resources */
  public static final String fileResourcesProperty = "org.apache.manifoldcf.fileresources";
  
  // Implementation class properties
  /** Lock manager implementation class */
  public static final String lockManagerImplementation = "org.apache.manifoldcf.lockmanagerclass";
  /** Database implementation class */
  public static final String databaseImplementation = "org.apache.manifoldcf.databaseimplementationclass";
  /** Auth implementation class */
  public static final String authImplementation = "org.apache.manifoldcf.authimplementationclass";
  
  // The following are system integration properties
  /** Script to invoke when configuration changes, if any */
  public static final String configSignalCommandProperty = "org.apache.manifoldcf.configuration.change.command";
  /** File to look for to block access to UI during database maintenance */
  public static final String maintenanceFileSignalProperty = "org.apache.manifoldcf.database.maintenanceflag";

  /** Encryption salt property */
  public static final String saltProperty = "org.apache.manifoldcf.salt";

  /** Reset environment, minting a thread context for convenience and backwards
  * compatibility.
  */
  @Deprecated
  public static void resetEnvironment()
  {
    resetEnvironment(ThreadContextFactory.make());
  }
  
  /** Reset environment.
  */
  public static void resetEnvironment(IThreadContext threadContext)
  {
    synchronized (initializeFlagLock)
    {
      if (initializeLevel > 0)
      {
        // Clean up the system doing the same thing the shutdown thread would have if the process was killed
        cleanUpEnvironment(threadContext);
        processID = null;
        mcfVersion = null;
        masterDatabaseName = null;
        masterDatabaseUsername = null;
        masterDatabasePassword = null;
        localConfiguration = null;
        propertyFilelastMod = -1L;
        propertyFilePath = null;
        alreadyClosed = false;
        alreadyShutdown = false;
        initializeLevel = 0;
      }
    }
  }
  
  /** Initialize environment, minting a thread context for backwards compatibility.
  */
  @Deprecated
  public static void initializeEnvironment()
    throws ManifoldCFException
  {
    initializeEnvironment(ThreadContextFactory.make());
  }
  
  /** Initialize environment.
  */
  public static void initializeEnvironment(IThreadContext threadContext)
    throws ManifoldCFException
  {
    synchronized (initializeFlagLock)
    {
      if (initializeLevel == 0)
      {
        try
        {
          
          // Get system properties
          java.util.Properties props = System.getProperties();
          // First, look for a define that might indicate where to look
        
          propertyFilePath = (String)props.get(lcfConfigFileProperty);
          if (propertyFilePath == null)
          {
            System.err.println("Couldn't find "+lcfConfigFileProperty+" property; using default");
            String configPath = (String)props.get("user.home") + "/"+applicationName;
            configPath = configPath.replace('\\', '/');
            propertyFilePath = new File(configPath,"properties.xml").toString();
          }

          // Initialize working directory.  We cannot use the actual system cwd, because different ManifoldCF processes will have different ones.
          // So, instead, we use the location of the property file itself, and call that the "working directory".
          workingDirectory = new File(propertyFilePath).getAbsoluteFile().getParentFile();

          // Initialize resource loader.
          resourceLoader = new ManifoldCFResourceLoader(Thread.currentThread().getContextClassLoader());
          
          // Read configuration!
          localConfiguration = new OverrideableManifoldCFConfiguration();
          checkProperties();

          // Process ID is always local
          processID = getStringProperty(processIDProperty,"");
          if (processID.length() > 16)
            throw new ManifoldCFException("Process ID cannot exceed 16 characters!");

          // Log file is always local
          File logConfigFile = getFileProperty(logConfigFileProperty);
          if (logConfigFile == null)
          {
            System.err.println("Couldn't find "+logConfigFileProperty+" property; using default");
            String configPath = (String)props.get("user.home") + "/"+applicationName;
            configPath = configPath.replace('\\', '/');
            logConfigFile = new File(configPath,"logging.xml");
          }

          // Make sure that the registered entry points for polling and cleanup are cleared, just in case.
          // This prevents classloader-style registration, which is actually not a good one for MCF architecture.
          synchronized (cleanupHooks)
          {
            cleanupHooks.clear();
          }
          synchronized (pollingHooks)
          {
            pollingHooks.clear();
          }
          
          Logging.initializeLoggingSystem(logConfigFile);

          // Set up local loggers
          Logging.initializeLoggers();
          Logging.setLogLevels(threadContext);

          mcfVersion = LockManagerFactory.getStringProperty(threadContext,versionProperty,"unknown version");
          masterDatabaseName = LockManagerFactory.getStringProperty(threadContext,masterDatabaseNameProperty,"dbname");
          masterDatabaseUsername = LockManagerFactory.getStringProperty(threadContext,masterDatabaseUsernameProperty,"manifoldcf");
          masterDatabasePassword = LockManagerFactory.getPossiblyObfuscatedStringProperty(threadContext,masterDatabasePasswordProperty,"local_pg_passwd");

          // Register the connector services
          registerConnectorServices();

          // Put the cache manager in the polling loop
          addPollingHook(new CachePoll());

          // Register the file tracker for cleanup on shutdown
          tracker = new FileTrack();
          addShutdownHook(tracker);
          // Register the database cleanup hook
          addShutdownHook(new DatabaseShutdown());

          // Open the database.  Done once per JVM.
          DBInterfaceFactory.make(threadContext,masterDatabaseName,masterDatabaseUsername,masterDatabasePassword).openDatabase();
        }
        catch (ManifoldCFException e)
        {
          throw new ManifoldCFException("Initialization failed: "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
        }
      }
      initializeLevel++;
    }

  }

  /** Register connector services provided in connectors and connector-commons
  */
  protected static void registerConnectorServices()
    throws ManifoldCFException
  {
    try
    {
      Class connectorServicesManifoldCF = findClass("org.apache.manifoldcf.connectorcommon.system.ManifoldCF");
      Method m = connectorServicesManifoldCF.getMethod("registerConnectorServices",new Class[0]);
      m.invoke(new Object[0]);
    }
    catch (ClassNotFoundException e)
    {
      Logging.root.warn("Could not find connectorcommon main class: "+e.getMessage(),e);
    }
    catch (NoSuchMethodException e)
    {
      Logging.root.warn("ManifoldCF.registerConnectorServices not found: "+e.getMessage(),e);
    }
    catch (IllegalAccessException e)
    {
      Logging.root.warn("Connectorcommon main class had illegal access: "+e.getMessage(),e);
    }
    catch (InvocationTargetException e)
    {
      Throwable z = e.getTargetException();
      if (z instanceof Error)
        throw (Error)z;
      else if (z instanceof RuntimeException)
        throw (RuntimeException)z;
      else
        throw new RuntimeException("Unknown exception type: "+z.getClass().getName()+": "+z.getMessage(),z);
    }
  }
  
  /** For local properties (not shared!!), this class allows them to be overridden directly from the command line.
  */
  protected static class OverrideableManifoldCFConfiguration extends ManifoldCFConfiguration
  {
    public OverrideableManifoldCFConfiguration()
    {
      super();
    }
    
    @Override
    public String getProperty(String s)
    {
      String rval = System.getProperty(s);
      if (rval == null)
        rval = super.getProperty(s);
      return rval;
    }
    
  }

  /** Get process ID */
  public static final String getProcessID()
  {
    return processID;
  }
  
  /** Get current properties.  Makes no attempt to reread or interpret them.
  */
  public static final ManifoldCFConfiguration getConfiguration()
  {
    return localConfiguration;
  }
  
  /** Reloads properties as needed.
  */
  public static final void checkProperties()
    throws ManifoldCFException
  {
    File f = new File(propertyFilePath);    // for re-read
    try
    {
      if (propertyFilelastMod != f.lastModified())
      {
        InputStream is = new FileInputStream(f);
        try
        {
          localConfiguration.fromXML(is);
	  System.err.println("Configuration file successfully read");
          propertyFilelastMod = f.lastModified();
        }
        finally
        {
          is.close();
        }
      }
      else
      {
	System.err.println("Configuration file not read because it didn't change");
        return;
      }
    }
    catch (Exception e)
    {
      throw new ManifoldCFException("Could not read configuration file '"+f.toString()+"'",e);
    }
    
    // For convenience, post-process all "lib" nodes.
    ArrayList libDirs = new ArrayList();
    int i = 0;
    while (i < localConfiguration.getChildCount())
    {
      ConfigurationNode cn = localConfiguration.findChild(i++);
      if (cn.getType().equals(NODE_LIBDIR))
      {
        String path = cn.getAttributeValue(ATTRIBUTE_PATH);
        if (path == null)
          throw new ManifoldCFException("Node type '"+NODE_LIBDIR+"' requires a '"+ATTRIBUTE_PATH+" attribute");
        // What exactly should I do with this classpath information?  The classloader can be dynamically updated, but if I do that will everything work?
        // I'm going to presume the answer is "yes" for now...
        libDirs.add(resolvePath(path));
      }
    }
    // Apply libdirs to the resource loader.
    resourceLoader.setClassPath(libDirs);
  }

  /** Resolve a file path, possibly relative to ManifoldCF's concept of its "working directory".
  *@param path is the path, to be calculated relative to the ManifoldCF "working directory".
  *@return the resolved file.
  */
  public static File resolvePath(String path)
  {
    File r = new File(path);
    return r.isAbsolute() ? r : new File(workingDirectory, path);
  }

  /** Read a (string) property, either from the system properties, or from the local configuration file.
  *@param s is the property name.
  *@return the property value, as a string.
  */
  public static String getProperty(String s)
  {
    return localConfiguration.getProperty(s);
  }

  /** Read a File property, either from the system properties, or from the local configuration file.
  * Relative file references are resolved according to the "working directory" for ManifoldCF.
  */
  public static File getFileProperty(String s)
  {
    String value = getProperty(s);
    if (value == null)
      return null;
    return resolvePath(value);
  }

  /** Read a (string) property, either from the system properties, or from the local configuration file.
  *@param s is the property name.
  *@param defaultValue is the default value for the property.
  *@return the property value, as a string.
  */
  public static String getStringProperty(String s, String defaultValue)
  {
    return localConfiguration.getStringProperty(s, defaultValue);
  }

  /** Read a boolean property
  */
  public static boolean getBooleanProperty(String s, boolean defaultValue)
    throws ManifoldCFException
  {
    return localConfiguration.getBooleanProperty(s, defaultValue);
  }
  
  /** Read an integer property, either from the system properties, or from the local configuration file.
  */
  public static int getIntProperty(String s, int defaultValue)
    throws ManifoldCFException
  {
    return localConfiguration.getIntProperty(s, defaultValue);
  }

  /** Read a long property, either from the system properties, or from the local configuration file.
  */
  public static long getLongProperty(String s, long defaultValue)
    throws ManifoldCFException
  {
    return localConfiguration.getLongProperty(s, defaultValue);
  }

  /** Read a float property, either from the system properties, or from the local configuration file.
  */
  public static double getDoubleProperty(String s, double defaultValue)
    throws ManifoldCFException
  {
    return localConfiguration.getDoubleProperty(s, defaultValue);
  }
  
  /** Attempt to make sure a path is a folder
  * @param path
  */
  public static void ensureFolder(String path)
    throws ManifoldCFException
  {
    try
    {
      File f = new File(path);
      if (!f.isDirectory())
      {
        f.mkdirs();
      }
    }
    catch (Exception e)
    {
      throw new ManifoldCFException("Can't make folder",e,ManifoldCFException.GENERAL_ERROR);
    }
  }

  /** Delete a folder path.
  *@param path is the folder path.
  */
  public static void deleteFolder(String path)
  {
    File directoryPath = new File(path);
    recursiveDelete(directoryPath);
  }

  /** Recursive delete: for cleaning up company folder.
  *@param directoryPath is the File describing the directory or file to be removed.
  */
  public static void recursiveDelete(File directoryPath)
  {
    if (!directoryPath.exists())
      return;
    if (directoryPath.isDirectory())
    {
      File[] children = directoryPath.listFiles();
      if (children != null)
      {
        int i = 0;
        while (i < children.length)
        {
          File x = children[i++];
          recursiveDelete(x);
        }
      }
    }
    directoryPath.delete();
  }

  /** Discover if a path is a folder
  * @param path spec, 'unix' form mostly
  */
  public static boolean isFolder(String path)
  {
    File f = new File(path);
    return f.isDirectory();
  }

  /** Convert a string into a safe, unique filename.
  *@param value is the string.
  *@return the file name.
  */
  public static String safeFileName(String value)
  {
    StringBuilder rval = new StringBuilder();
    int i = 0;
    while (i < value.length())
    {
      char x = value.charAt(i++);
      if (x == '/' || x == '"' || x == '\\' || x == '|' || (x >= 0 && x < ' ') ||
        x == '+' || x == ',' || x == ':' || x == ';' || x == '<' || x == '>' ||
        x == '=' || x == '[' || x == ']' || x == '&')
      {
        // Stuff the character
        rval.append("&").append(Integer.toString((int)x)).append("!");
      }
      else
        rval.append(x);
    }
    return rval.toString();
  }

  /** Get the mcf version.
  *@return the version string
  */
  public static String getMcfVersion()
  {
    return mcfVersion;
  }
  
  /** Get the master database name.
  *@return the master database name
  */
  public static String getMasterDatabaseName()
  {
    return masterDatabaseName;
  }

  /** Get the master database username.
  *@return the master database username.
  */
  public static String getMasterDatabaseUsername()
  {
    return masterDatabaseUsername;
  }

  /** Get the master database password.
  *@return the master database password.
  */
  public static String getMasterDatabasePassword()
  {
    return masterDatabasePassword;
  }

  /** Find a child database name given a company database instance and the child
  * database identifier.
  *@param companyDatabase is the company database.
  *@param childDBIdentifier is the identifier.
  *@return the child database name.
  */
  public static String getChildDatabaseName(IDBInterface companyDatabase, String childDBIdentifier)
  {
    return companyDatabase.getDatabaseName()+"_"+childDBIdentifier;
  }

  /** Perform standard hashing of a string
  *  @param input is the string to hash.
  *  @return the encrypted string.
  *   */
  public static String hash(String input)
    throws ManifoldCFException
  {
    MessageDigest hash = startHash();
    addToHash(hash,input);
    return getHashValue(hash);
  }

  /** Start creating a hash
  */
  public static MessageDigest startHash()
    throws ManifoldCFException
  {
    try
    {
      return MessageDigest.getInstance("SHA");
    }
    catch (Exception e)
    {
      throw new ManifoldCFException("Couldn't encrypt: "+e.getMessage(),e,ManifoldCFException.GENERAL_ERROR);
    }
  }

  /** Add to hash
  */
  public static void addToHash(MessageDigest digest, String input)
    throws ManifoldCFException
  {
    try
    {
      byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
      digest.update(inputBytes);
    }
    catch (Exception e)
    {
      throw new ManifoldCFException("Couldn't encrypt: "+e.getMessage(),e,ManifoldCFException.GENERAL_ERROR);
    }
  }

  /** Calculate final hash value
  */
  public static String getHashValue(MessageDigest digest)
    throws ManifoldCFException
  {
    try
    {
      byte[] encryptedBytes = digest.digest();
      StringBuilder rval = new StringBuilder();
      int i = 0;
      while (i < encryptedBytes.length)
      {
        byte x = encryptedBytes[i++];
        rval.append(writeNibble((((int)x) >> 4) & 15));
        rval.append(writeNibble(((int)x) & 15));
      }
      return rval.toString();
    }
    catch (Exception e)
    {
      throw new ManifoldCFException("Couldn't encrypt: "+e.getMessage(),e,ManifoldCFException.GENERAL_ERROR);
    }
  }

  protected static final int IV_LENGTH = 16;

  protected static String getSaltValue(IThreadContext threadContext)
    throws ManifoldCFException
  {
    final String saltValue = LockManagerFactory.getProperty(threadContext, saltProperty);

    if (saltValue == null || saltValue.length() == 0)
      throw new ManifoldCFException("Missing required SALT value");

    return saltValue;
  }
  
  protected static Cipher getCipher(IThreadContext threadContext, final int mode, final String passCode, final byte[] iv)
    throws ManifoldCFException
  {
    return getCipher(getSaltValue(threadContext), mode, passCode, iv);
  }
  
  protected static Cipher getCipher(String saltValue, final int mode, final String passCode, final byte[] iv)
    throws ManifoldCFException
  {
    try
    {
      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      KeySpec keySpec = new PBEKeySpec(passCode.toCharArray(), saltValue.getBytes(StandardCharsets.UTF_8), 1024, 128);
      SecretKey secretKey = factory.generateSecret(keySpec);

      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      SecretKeySpec key = new SecretKeySpec(secretKey.getEncoded(), "AES");
      IvParameterSpec parameterSpec = new IvParameterSpec(iv);
      cipher.init(mode, key, parameterSpec);
      return cipher;
    }
    catch (GeneralSecurityException gse)
    {
      throw new ManifoldCFException("Could not build a cipher: " + gse.getMessage(),gse);
    }
  }
  
  protected static byte[] getSecureRandom()
  {
    SecureRandom random = new SecureRandom();
    byte[] iv = new byte[IV_LENGTH];
    random.nextBytes(iv);
    return iv;
  }
  
  private static String OBFUSCATION_PASSCODE = "NowIsTheTime";
  private static String OBFUSCATION_SALT = "Salty";
  
  /** Encode a string in a reversible obfuscation.
  *@param input is the input string.
  *@return the output string.
  */
  public static String obfuscate(String input)
    throws ManifoldCFException
  {
    return encrypt(OBFUSCATION_SALT, OBFUSCATION_PASSCODE, input);
  }
  
  /** Decode a string encoded using the obfuscation
  * technique.
  *@param input is the input string.
  *@return the decoded string.
  */
  public static String deobfuscate(String input)
    throws ManifoldCFException
  {
    return decrypt(OBFUSCATION_SALT, OBFUSCATION_PASSCODE, input);
  }
  
  /** Encrypt a string in a reversible encryption.
  *@param saltValue is the salt value.
  *@param passCode is the pass code.
  *@param input is the input string.
  *@return the output string.
  */
  public static String encrypt(String saltValue, String passCode, String input)
    throws ManifoldCFException
  {
    if (input == null)
      return null;
    if (input.length() == 0)
      return input;

    try
    {
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      
      // Write IV as a prefix:
      byte[] iv = getSecureRandom();
      os.write(iv);
      os.flush();
            
      Cipher cipher = getCipher(saltValue, Cipher.ENCRYPT_MODE, passCode, iv);
      CipherOutputStream cos = new CipherOutputStream(os, cipher);
      Writer w = new OutputStreamWriter(cos,java.nio.charset.StandardCharsets.UTF_8);
      w.write(input);
      w.flush();
      // These two shouldn't be necessary, but they are.
      cos.flush();
      cos.close();
      byte[] bytes = os.toByteArray();
      return new org.apache.manifoldcf.core.common.Base64().encodeByteArray(bytes);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }
        
  /** Decrypt a string.
  *@param saltValue is the salt value.
  *@param passCode is the pass code.
  *@param input is the input string.
  *@return the decoded string.
  */
  public static String decrypt(String saltValue, String passCode, String input)
    throws ManifoldCFException
  {
    if (input == null)
      return null;
    if (input.length() == 0)
      return input;

    try
    {
      ByteArrayInputStream is = new ByteArrayInputStream(new org.apache.manifoldcf.core.common.Base64().decodeString(input));
      
      byte[] iv = new byte[IV_LENGTH];
      int pointer = 0;
      while (pointer < iv.length)
      {
        int amt = is.read(iv,pointer,iv.length-pointer);
        if (amt == -1)
          throw new ManifoldCFException("String can't be decrypted: too short");
        pointer += amt;
      }

      Cipher cipher = getCipher(saltValue, Cipher.DECRYPT_MODE, passCode, iv);
      CipherInputStream cis = new CipherInputStream(is, cipher);
      InputStreamReader reader = new InputStreamReader(cis,java.nio.charset.StandardCharsets.UTF_8);
      StringBuilder sb = new StringBuilder();
      char[] buffer = new char[65536];
      while (true)
      {
        int amt = reader.read(buffer,0,buffer.length);
        if (amt == -1)
          break;
        sb.append(buffer,0,amt);
      }
      return sb.toString();
    }
    catch (IOException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }
  
  /** Encode a string in a reversible obfuscation.
  *@param input is the input string.
  *@return the output string.
  */
  /*
  public static String obfuscate(String input)
    throws ManifoldCFException
  {
      if (input == null)
        return null;
      if (input.length() == 0)
        return input;
      // First, convert to binary
      byte[] array = input.getBytes(StandardCharsets.UTF_8);
      // Shift and xor
      // We shift by some number not a multiple of 4.
      // The resulting hexadecimal is then not a simple shift.
      int i = 0;
      int carryover = (((int)array[array.length-1]) & 0x1f);
      while (i < array.length)
      {
        int x = (int)array[i];
        int newCarryover = x & 0x1f;
        x = ((x >> 5) & 0x7) + (carryover << 3);
        carryover = newCarryover;
        array[i++] = (byte)(x ^ 0x59);
      }
      // Now, convert to hex
      StringBuilder rval = new StringBuilder();
      i = 0;
      while (i < array.length)
      {
        int x = (int)array[i++];
        rval.append(writeNibble((x >> 4) & 0x0f));
        rval.append(writeNibble(x & 0x0f));
      }
      return rval.toString();
  }
*/
  /** Write a hex nibble.
  *@param value is the value to write.
  *@return the character.
  */

  protected static char writeNibble(int value)
  {
    if (value >= 10)
      return (char)(value-10+'A');
    else
      return (char)(value+'0');
  }


  /** Decode a string encoded using the obfuscation
  * technique.
  *@param input is the input string.
  *@return the decoded string.
  */
  /*
  public static String deobfuscate(String input)
    throws ManifoldCFException
  {
      if (input == null)
        return null;
      if (input.length() == 0)
        return input;

      if ((input.length() >> 1) * 2 != input.length())
        throw new ManifoldCFException("Decoding error",ManifoldCFException.GENERAL_ERROR);

      byte[] bytes = new byte[input.length() >> 1];
      int i = 0;
      int j = 0;
      while (i < input.length())
      {
        int x0 = readNibble(input.charAt(i++));
        int x1 = readNibble(input.charAt(i++));
        int x = (x0 << 4) + x1;
        bytes[j++] = (byte)x;
      }

      // Process the array in reverse order
      int carryover = ((((int)bytes[0]) ^ 0x59) >> 3) & 0x1f;
      i = bytes.length;
      while (i > 0)
      {
        i--;
        int x = ((int)bytes[i]) ^ 0x59;
        int newCarryover = (x >> 3) & 0x1f;
        x = (x << 5) + carryover;
        bytes[i] = (byte)x;
        carryover = newCarryover;
      }

      // Convert from utf-8 to a string
      return new String(bytes,StandardCharsets.UTF_8);
  }
  */
  
  /** Read a hex nibble.
  *@param value is the character.
  *@return the value.
  */
  protected static int readNibble(char value)
    throws ManifoldCFException
  {
    if (value >= 'A' && value <= 'F')
      return (int)(value - 'A' + 10);
    else if (value >= '0' && value <= '9')
      return (int)(value - '0');
    else
      throw new ManifoldCFException("Bad hexadecimal value",ManifoldCFException.GENERAL_ERROR);
  }


  /** Install system database.
  *@param threadcontext is the thread context.
  *@param masterUsername is the master database user name.
  *@param masterPassword is the master database password.
  */
  public static void createSystemDatabase(IThreadContext threadcontext, String masterUsername, String masterPassword)
    throws ManifoldCFException
  {
    String databaseName = getMasterDatabaseName();
    String databaseUsername = getMasterDatabaseUsername();
    String databasePassword = getMasterDatabasePassword();

    IDBInterface master = DBInterfaceFactory.make(threadcontext,databaseName,databaseUsername,databasePassword);
    master.createUserAndDatabase(masterUsername,masterPassword,null);
  }

  /** Drop system database.
  *@param threadcontext is the thread context.
  *@param masterUsername is the master database user name.
  *@param masterPassword is the master database password.
  */
  public static void dropSystemDatabase(IThreadContext threadcontext, String masterUsername, String masterPassword)
    throws ManifoldCFException
  {
    String databaseName = getMasterDatabaseName();
    String databaseUsername = getMasterDatabaseUsername();
    String databasePassword = getMasterDatabasePassword();

    IDBInterface master = DBInterfaceFactory.make(threadcontext,databaseName,databaseUsername,databasePassword);
    master.dropUserAndDatabase(masterUsername,masterPassword,null);
  }

  /** Create temporary directory.
  */
  public static File createTempDir(String prefix, String suffix)
    throws ManifoldCFException
  {
    String tempDirLocation = System.getProperty("java.io.tmpdir");
    if (tempDirLocation == null)
      throw new ManifoldCFException("Can't find temporary directory!");
    File tempDir = new File(tempDirLocation);
    // Start with current timestamp, and generate a hash, then look for collision
    long currentFileID = System.currentTimeMillis();
    long currentFileHash = (currentFileID << 5) ^ (currentFileID >> 3);
    int raceConditionRepeat = 0;
    while (raceConditionRepeat < 1000)
    {
      File tempCertDir = new File(tempDir,prefix+currentFileHash+suffix);
      if (tempCertDir.mkdir())
      {
        return tempCertDir;
      }
      if (tempCertDir.exists())
      {
        currentFileHash++;
        continue;
      }
      // Doesn't exist but couldn't create either.  COULD be a race condition; we'll only know if we retry
      // lots and nothing changes.
      raceConditionRepeat++;
      Thread.yield();
    }
    throw new ManifoldCFException("Temporary directory appears to be unwritable");
  }

  /** Add a file to the tracking system. */
  public static void addFile(File f)
  {
    tracker.addFile(f);
  }

  /** Use the tracking system to delete a file.  You MUST use this to
  * delete any file that was added to the tracking system with addFile(). */
  public static void deleteFile(File f)
  {
    tracker.deleteFile(f);
  }

  /** Check if maintenance is underway.
  */
  public static boolean checkMaintenanceUnderway()
  {
    // File check is always local; this whole bit of logic needs to be rethought though.
    String fileToCheck = getProperty(maintenanceFileSignalProperty);
    if (fileToCheck != null && fileToCheck.length() > 0)
    {
      File f = new File(fileToCheck);
      return f.exists();
    }
    return false;
  }

  /** Note configuration change.
  */
  public static void noteConfigurationChange()
    throws ManifoldCFException
  {
    // Always a local file.  This needs to be rethought how it should operate in a clustered world.
    String configChangeSignalCommand = getProperty(configSignalCommandProperty);
    if (configChangeSignalCommand == null || configChangeSignalCommand.length() == 0)
      return;

    // Do stuff to the file to note change.  This involves
    // shelling out to the os and involving whatever is desired.

    // We should try to convert the command into arguments.
    ArrayList list = new ArrayList();
    int currentIndex = 0;
    while (currentIndex < configChangeSignalCommand.length())
    {
      // Suck up the leading whitespace
      while (currentIndex < configChangeSignalCommand.length())
      {
        char x = configChangeSignalCommand.charAt(currentIndex);
        if (x < 0 || x > ' ')
          break;
        currentIndex++;
      }
      StringBuilder argBuffer = new StringBuilder();
      boolean isQuoted = false;
      while (currentIndex < configChangeSignalCommand.length())
      {
        char x = configChangeSignalCommand.charAt(currentIndex);
        if (isQuoted)
        {
          if (x == '"')
          {
            currentIndex++;
            isQuoted = false;
          }
          else if (x == '\\')
          {
            currentIndex++;
            if (currentIndex < configChangeSignalCommand.length())
            {
              x = configChangeSignalCommand.charAt(currentIndex);
              argBuffer.append(x);
            }
            else
              break;
          }
          else
          {
            currentIndex++;
            argBuffer.append(x);
          }
        }
        else
        {
          if (x == '"')
          {
            currentIndex++;
            isQuoted = true;
          }
          else if (x == '\\')
          {
            currentIndex++;
            if (currentIndex < configChangeSignalCommand.length())
            {
              x = configChangeSignalCommand.charAt(currentIndex);
              argBuffer.append(x);
            }
            else
              break;
          }
          else if (x >= 0 && x <= ' ')
            break;
          else
          {
            currentIndex++;
            argBuffer.append(x);
          }
        }
      }
      list.add(argBuffer.toString());
    }

    // Set up for command invocation
    String[] commandArray = new String[list.size()];
    int i = 0;
    while (i < commandArray.length)
    {
      commandArray[i] = (String)list.get(i);
      i++;
    }

    if (commandArray.length == 0)
      return;

    String[] env = new String[0];
    File dir = new File("/");

    try
    {
      // Do the exec.
      Process p = Runtime.getRuntime().exec(commandArray,env,dir);
      try
      {
        // To make this truly "safe", we really ought to spin up a thread to handle both the standard error and the standard output streams - otherwise
        // we run the risk of getting blocked here.  In practice, there's enough buffering in the OS to handle what we need right now.
        int rval = p.waitFor();
        if (rval != 0)
        {
          InputStream is = p.getErrorStream();
          try
          {
            Reader r = new InputStreamReader(is, StandardCharsets.UTF_8);
            try
            {
              BufferedReader br = new BufferedReader(r);
              try
              {
                StringBuilder sb = new StringBuilder();
                while (true)
                {
                  String value = br.readLine();
                  if (value == null)
                    break;
                  sb.append(value).append("; ");
                }
                throw new ManifoldCFException("Shelled process '"+configChangeSignalCommand+"' failed with error "+Integer.toString(rval)+": "+sb.toString());
              }
              finally
              {
                br.close();
              }
            }
            finally
            {
              r.close();
            }
          }
          finally
          {
            is.close();
          }
        }
      }
      finally
      {
        p.destroy();
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException("Process wait interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (InterruptedIOException e)
    {
      throw new ManifoldCFException("IO with subprocess interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO exception signalling change: "+e.getMessage(),e);
    }
  }

  /** Use this method to sleep instead of Thread.sleep().  Thread.sleep() doesn't seem to work well when the system
  * time is reset.
  */
  public static void sleep(long milliseconds)
    throws InterruptedException
  {
    // Unfortunately we need to create an object for every time that we sleep
    Integer x = new Integer(0);
    synchronized (x)
    {
      x.wait(milliseconds);
    }
  }

  /** Write a bunch of bytes to the output stream */
  public static void writeBytes(OutputStream os, byte[] byteArray)
    throws IOException
  {
    os.write(byteArray, 0, byteArray.length);
  }

  /** Write a byte to an output stream */
  public static void writeByte(OutputStream os, int byteValue)
    throws IOException
  {
    writeBytes(os,new byte[]{(byte)byteValue});
  }

  /** Write a word to an output stream */
  public static void writeWord(OutputStream os, int wordValue)
    throws IOException
  {
    byte[] buffer = new byte[2];
    buffer[0] = (byte)(wordValue & 0xff);
    buffer[1] = (byte)((wordValue >>> 8) & 0xff);
    writeBytes(os,buffer);
  }

  /** Write a dword to an output stream */
  public static void writeDword(OutputStream os, int dwordValue)
    throws IOException
  {
    if (dwordValue < 0)
      throw new IllegalArgumentException("Attempt to use an unsigned operator to write a signed value");
    writeSdword(os,dwordValue);
  }

  /** Write a signed dword to an output stream */
  public static void writeSdword(OutputStream os, int dwordValue)
    throws IOException
  {
    byte[] buffer = new byte[4];
    buffer[0] = (byte)(dwordValue & 0xff);
    buffer[1] = (byte)((dwordValue >>> 8) & 0xff);
    buffer[2] = (byte)((dwordValue >>> 16) & 0xff);
    buffer[3] = (byte)((dwordValue >>> 24) & 0xff);
    writeBytes(os, buffer);
  }

  /** Write a Long to an output stream */
  public static void writeLong(OutputStream os, Long longValue)
    throws IOException
  {
    if (longValue == null)
      writeByte(os,1);
    else
    {
      writeByte(os,0);
      long value = longValue.longValue();
      byte[] buffer = new byte[8];
      buffer[0] = (byte)(value & 0xff);
      buffer[1] = (byte)(Long.rotateRight(value,8) & 0xff);
      buffer[2] = (byte)(Long.rotateRight(value,16) & 0xff);
      buffer[3] = (byte)(Long.rotateRight(value,24) & 0xff);
      buffer[4] = (byte)(Long.rotateRight(value,32) & 0xff);
      buffer[5] = (byte)(Long.rotateRight(value,40) & 0xff);
      buffer[6] = (byte)(Long.rotateRight(value,48) & 0xff);
      buffer[7] = (byte)(Long.rotateRight(value,56) & 0xff);
      writeBytes(os,buffer);
    }
  }

  /** Write a String to an output stream */
  public static void writeString(OutputStream os, String stringValue)
    throws IOException
  {
    byte[] characters;
    if (stringValue == null)
      characters = null;
    else
      characters = stringValue.getBytes(StandardCharsets.UTF_8);
    writeByteArray(os, characters);
  }

  /** Write a byte array to an output stream */
  public static void writeByteArray(OutputStream os, byte[] byteArray)
    throws IOException
  {
    if (byteArray == null)
      writeSdword(os,-1);
    else
    {
      writeSdword(os,byteArray.length);
      writeBytes(os,byteArray);
    }
  }

  /** Write a float value to an output stream */
  public static void writefloat(OutputStream os, float floatValue)
    throws IOException
  {
    writeSdword(os, Float.floatToIntBits(floatValue));
  }

  /** Read  bytes from the input stream into specified array. */
  public static void readBytes(InputStream is, byte[] byteArray)
    throws IOException
  {
    int amtSoFar = 0;
    while (amtSoFar < byteArray.length)
    {
      int amt = is.read(byteArray,amtSoFar,byteArray.length-amtSoFar);
      if (amt == -1)
        throw new IOException("Unexpected EOF");
      amtSoFar += amt;
    }
  }

  /** Read a byte from an input stream */
  public static int readByte(InputStream is)
    throws IOException
  {
    byte[] inputArray = new byte[1];
    readBytes(is,inputArray);
    return ((int)inputArray[0]) & 0xff;
  }

  /** Read a word from an input stream */
  public static int readWord(InputStream is)
    throws IOException
  {
    byte[] inputArray = new byte[2];
    readBytes(is,inputArray);
    return (((int)inputArray[0]) & 0xff) +
      ((((int)inputArray[1]) & 0xff) << 8);
  }

  /** Read a dword from an input stream */
  public static int readDword(InputStream is)
    throws IOException
  {
    byte[] inputArray = new byte[4];
    readBytes(is,inputArray);
    return (((int)inputArray[0]) & 0xff) +
      ((((int)inputArray[1]) & 0xff) << 8) +
      ((((int)inputArray[2]) & 0xff) << 16) +
      ((((int)inputArray[3]) & 0xff) << 24);
  }

  /** Read a signed dword from an input stream */
  public static int readSdword(InputStream is)
    throws IOException
  {
    byte[] inputArray = new byte[4];
    readBytes(is,inputArray);
    return (((int)inputArray[0]) & 0xff) +
      ((((int)inputArray[1]) & 0xff) << 8) +
      ((((int)inputArray[2]) & 0xff) << 16) +
      (((int)inputArray[3]) << 24);
  }

  /** Read a Long from an input stream */
  public static Long readLong(InputStream is)
    throws IOException
  {
    int value = readByte(is);
    if (value == 1)
      return null;
    byte[] inputArray = new byte[8];
    readBytes(is,inputArray);
    return new Long((long)(((int)inputArray[0]) & 0xff) +
      Long.rotateLeft(((int)inputArray[1]) & 0xff, 8) +
      Long.rotateLeft(((int)inputArray[2]) & 0xff, 16) +
      Long.rotateLeft(((int)inputArray[3]) & 0xff, 24) +
      Long.rotateLeft(((int)inputArray[4]) & 0xff, 32) +
      Long.rotateLeft(((int)inputArray[5]) & 0xff, 40) +
      Long.rotateLeft(((int)inputArray[6]) & 0xff, 48) +
      Long.rotateLeft(((int)inputArray[7]) & 0xff, 56));

  }

  /** Read a String from an input stream */
  public static String readString(InputStream is)
    throws IOException
  {
    byte[] bytes = readByteArray(is);
    if (bytes == null)
      return null;
    return new String(bytes,StandardCharsets.UTF_8);
  }

  /** Read a byte array from an input stream */
  public static byte[] readByteArray(InputStream is)
    throws IOException
  {
    int length = readSdword(is);
    if (length == -1)
      return null;
    byte[] byteArray = new byte[length];
    readBytes(is,byteArray);
    return byteArray;
  }

  /** Read a float value from an input stream */
  public static float readfloat(InputStream os)
    throws IOException
  {
    return Float.intBitsToFloat(readSdword(os));
  }

  /** Add a cleanup hook to the list.  These hooks will be evaluated in the
  * reverse order than the order in which they were added.
  *@param hook is the shutdown hook that needs to be added to the sequence.
  */
  public static void addShutdownHook(IShutdownHook hook)
  {
    synchronized (cleanupHooks)
    {
      cleanupHooks.add(hook);
    }
  }

  /** Add a polling hook to the list.  These hooks will be evaluated in the
  * order they were added.
  *@param hook is the polling hook that needs to be added to the sequence.
  */
  public static void addPollingHook(IPollingHook hook)
  {
    synchronized (pollingHooks)
    {
      pollingHooks.add(hook);
    }
  }
  
  /** Poll all the registered polling services.
  */
  public static void pollAll(IThreadContext threadContext)
    throws ManifoldCFException
  {
    synchronized (pollingHooks)
    {
      for (IPollingHook hook : pollingHooks)
      {
        hook.doPoll(threadContext);
      }
    }
  }
  
  /** Create a new resource loader based on the default one.  This is used by
  * connectors wishing to make their own resource loaders for isolation purposes.
  */
  public static ManifoldCFResourceLoader createResourceLoader()
    throws ManifoldCFException
  {
    return new ManifoldCFResourceLoader(resourceLoader.getClassLoader());
  }
  
  /** Locate a class in the configuration-determined class path.  This method
  * is designed for loading plugin classes, and their downstream dependents.
  */
  public static Class findClass(String cname)
    throws ClassNotFoundException,ManifoldCFException
  {
    return resourceLoader.findClass(cname);
  }
  
  /** Perform system shutdown, minting thread context for backwards compatibility */
  @Deprecated
  public static void cleanUpEnvironment()
  {
    cleanUpEnvironment(ThreadContextFactory.make());
  }
  
  /** Perform system shutdown, using the registered shutdown hooks. */
  public static void cleanUpEnvironment(IThreadContext threadContext)
  {
    synchronized (initializeFlagLock)
    {
      initializeLevel--;
      // It needs to call all registered shutdown hooks, in reverse order.
      // A failure of any one hook should cause the cleanup to continue, after a logging attempt is made.
      if (initializeLevel == 0 && !alreadyShutdown)
      {
        synchronized (cleanupHooks)
        {
          int i = cleanupHooks.size();
          while (i > 0)
          {
            i--;
            IShutdownHook hook = cleanupHooks.get(i);
            try
            {
              hook.doCleanup(threadContext);
            }
            catch (ManifoldCFException e)
            {
              Logging.root.warn("Error during system shutdown: "+e.getMessage(),e);
            }
          }
          cleanupHooks.clear();
        }
        synchronized (pollingHooks)
        {
          pollingHooks.clear();
        }
        alreadyShutdown = true;
      }
    }
  }

  /** Class that tracks files that need to be cleaned up on exit */
  protected static class FileTrack implements IShutdownHook
  {
    /** Set of File objects */
    protected Set<File> filesToDelete = new HashSet<File>();

    /** Constructor */
    public FileTrack()
    {
    }

    /** Add a file to track */
    public void addFile(File f)
    {
      synchronized (this)
      {
        filesToDelete.add(f);
      }
    }

    /** Delete a file */
    public void deleteFile(File f)
    {
      // Because we never reuse file names, it is OK to delete twice.
      // So the delete() can be outside the synchronizer.
      recursiveDelete(f);
      synchronized (this)
      {
        filesToDelete.remove(f);
      }
    }

    /** Delete all remaining files */
    @Override
    public void doCleanup(IThreadContext threadContext)
      throws ManifoldCFException
    {
      synchronized (this)
      {
	Iterator<File> iter = filesToDelete.iterator();
	while (iter.hasNext())
	{
	  File f = iter.next();
	  f.delete();
	}
	filesToDelete.clear();
      }
    }

    /** Finalizer, which is designed to catch class unloading that tomcat 5.5 does.
    */
    protected void finalize()
      throws Throwable
    {
      try
      {
        doCleanup(ThreadContextFactory.make());
      }
      finally
      {
        super.finalize();
      }
    }

  }

  
  /** Class that cleans up expired cache objects on polling.
  */
  protected static class CachePoll implements IPollingHook
  {
    public CachePoll()
    {
    }
    
    @Override
    public void doPoll(IThreadContext threadContext)
      throws ManifoldCFException
    {
      ICacheManager cacheManager = CacheManagerFactory.make(threadContext);
      cacheManager.expireObjects(System.currentTimeMillis());
    }
  }
  
  /** Class that cleans up database handles on exit */
  protected static class DatabaseShutdown implements IShutdownHook
  {
    public DatabaseShutdown()
    {
    }
    
    @Override
    public void doCleanup(IThreadContext threadContext)
      throws ManifoldCFException
    {
      // Clean up the database handles
      Thread t = new DatabaseConnectionReleaseThread();
      t.start();
      try
      {
        // Wait 15 seconds for database cleanup to finish.  If we haven't managed to close database connections by then, we give up and just exit.
        t.join(15000L);
      }
      catch (InterruptedException e)
      {
      }
      closeDatabase();
    }
    
    protected void closeDatabase()
      throws ManifoldCFException
    {
      synchronized (initializeFlagLock)
      {
        if (initializeLevel == 0 && !alreadyClosed)
        {
          IThreadContext threadcontext = ThreadContextFactory.make();
          
          String databaseName = getMasterDatabaseName();
          String databaseUsername = getMasterDatabaseUsername();
          String databasePassword = getMasterDatabasePassword();

          DBInterfaceFactory.make(threadcontext,databaseName,databaseUsername,databasePassword).closeDatabase();
          alreadyClosed = true;
        }
      }
    }
    
    /** Finalizer, which is designed to catch class unloading that tomcat 5.5 does.
    */
    protected void finalize()
      throws Throwable
    {
      try
      {
        // The database handle cleanup is handled inside the finalizers for the pools that hold onto connections.
        closeDatabase();
      }
      finally
      {
        super.finalize();
      }
    }

  }
  
  /** Finisher thread, to be registered with the runtime */
  protected static class ShutdownThread extends Thread
  {
    /** Constructor.
    */
    public ShutdownThread()
    {
      super();
      setName("Shutdown thread");
    }

    public void run()
    {
      // This thread is run at shutdown time.
      cleanUpEnvironment(ThreadContextFactory.make());
    }
  }

  /** The thread that actually releases database connections
  */
  protected static class DatabaseConnectionReleaseThread extends Thread
  {
    /** Constructor. */
    public DatabaseConnectionReleaseThread()
    {
      super();
      setName("Database connection release thread");
      // May be abandoned if it takes too long
      setDaemon(true);
    }

    public void run()
    {
      // Clean up the database handles
      org.apache.manifoldcf.core.database.ConnectionFactory.releaseAll();
    }
  }
}

