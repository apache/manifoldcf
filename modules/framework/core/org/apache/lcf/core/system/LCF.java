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
package org.apache.lcf.core.system;

import org.apache.lcf.core.interfaces.*;
import java.io.*;
import java.util.*;
import java.security.MessageDigest;

public class LCF
{
  public static final String _rcsid = "@(#)$Id$";

  
  // Shutdown hooks
  /** Temporary file collector */
  protected static FileTrack tracker;
  /** Database handle cleanup */
  protected static DatabaseShutdown dbShutdown;
  
  /** Array of cleanup hooks (for managing shutdown) */
  protected static ArrayList cleanupHooks; 
  /** Shutdown thread */
  protected static Thread shutdownThread;
  /** Static initializer for setting up shutdown thread etc. */
  static
  {
    cleanupHooks = new ArrayList();
    shutdownThread = new ShutdownThread(cleanupHooks);
    tracker = new FileTrack();
    dbShutdown = new DatabaseShutdown();
    try
    {
      Runtime.getRuntime().addShutdownHook(shutdownThread);
      // Register the file tracker for cleanup on shutdown
      addShutdownHook(tracker);
      // Register the database cleanup hook
      addShutdownHook(dbShutdown);
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

  // Local member variables
  protected static String masterDatabaseName = null;
  protected static String masterDatabaseUsername = null;
  protected static String masterDatabasePassword = null;
  protected static java.util.Properties localProperties = null;
  //protected static String configPath = null;
  protected static long propertyFilelastMod = -1L;
  protected static String propertyFilePath = null;

  protected static final String applicationName = "lcf";

  // System property names
  public static final String lcfConfigFileProperty = "org.apache.lcf.configfile";

  // System property/property file property names
  
  // Database access properties
  /** Database name property */
  public static final String masterDatabaseNameProperty = "org.apache.lcf.database.name";
  /** Database user name property */
  public static final String masterDatabaseUsernameProperty = "org.apache.lcf.database.username";
  /** Database password property */
  public static final String masterDatabasePasswordProperty = "org.apache.lcf.database.password";

  // Database connection pooling properties
  /** Maximum open database handles property */
  public static final String databaseHandleMaxcountProperty = "org.apache.lcf.database.maxhandles";
  /** Database handle timeout property */
  public static final String databaseHandleTimeoutProperty = "org.apache.lcf.database.handletimeout";

  // Log configuration properties
  /** Location of log configuration file */
  public static final String logConfigFileProperty = "org.apache.lcf.logconfigfile";
  
  // Implementation class properties
  /** Lock manager implementation class */
  public static final String lockManagerImplementation = "org.apache.lcf.lockmanagerclass";
  /** Database implementation class */
  public static final String databaseImplementation = "org.apache.lcf.databaseimplementationclass";
  
  // The following are system integration properties
  /** Script to invoke when configuration changes, if any */
  public static final String configSignalCommandProperty = "org.apache.lcf.configuration.change.command";
  /** File to look for to block access to UI during database maintenance */
  public static final String maintenanceFileSignalProperty = "org.apache.lcf.database.maintenanceflag";

  /** Initialize environment.
  */
  public static synchronized void initializeEnvironment()
  {
    if (localProperties != null)
      return;

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
        propertyFilePath = new File(configPath,"properties.ini").toString();
      }
      
      // Read .ini parameters
      localProperties = new java.util.Properties();
      checkProperties();

      String logConfigFile = getProperty(logConfigFileProperty);
      if (logConfigFile == null)
      {
	System.err.println("Couldn't find "+logConfigFileProperty+" property; using default");
        String configPath = (String)props.get("user.home") + "/"+applicationName;
        configPath = configPath.replace('\\', '/');
        logConfigFile = new File(configPath,"logging.ini").toString();
      }

      masterDatabaseName = getProperty(masterDatabaseNameProperty);
      if (masterDatabaseName == null)
        masterDatabaseName = "dbname";
      masterDatabaseUsername = getProperty(masterDatabaseUsernameProperty);
      if (masterDatabaseUsername == null)
        masterDatabaseUsername = "lcf";
      masterDatabasePassword = getProperty(masterDatabasePasswordProperty);
      if (masterDatabasePassword == null)
        masterDatabasePassword = "local_pg_passwd";

      Logging.initializeLoggingSystem(logConfigFile);

      // Set up local loggers
      Logging.initializeLoggers();
      Logging.setLogLevels();

    }
    catch (LCFException e)
    {
      e.printStackTrace();
    }


  }

  /** Reloads properties as needed.
  */
  public static final void checkProperties()
    throws LCFException
  {
    File f = new File(propertyFilePath);    // for re-read
    try
    {
      if (propertyFilelastMod != f.lastModified())
      {
        InputStream is = new FileInputStream(f);
        try
        {
          localProperties.load(is);
	  System.err.println("Property file successfully read");
          propertyFilelastMod = f.lastModified();
        }
        finally
        {
          is.close();
        }
      }
      else
	System.err.println("Property file not read because it didn't change");
    }
    catch (Exception e)
    {
      throw new LCFException("Could not read property file '"+f.toString()+"'",e);
    }
  }

  /** Read a property, either from the system properties, or from the local property file image.
  *@param s is the property name.
  *@return the property value, as a string.
  */
  public static final String getProperty(String s)
  {
    String rval = System.getProperty(s);
    if (rval == null)
      rval = localProperties.getProperty(s);
    return rval;
  }

  /** Attempt to make sure a path is a folder
  * @param path
  */
  public static void ensureFolder(String path)
    throws LCFException
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
      throw new LCFException("Can't make folder",e,LCFException.GENERAL_ERROR);
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
  *@param directoryPath is the File describing the directory to be removed.
  */
  protected static void recursiveDelete(File directoryPath)
  {
    File[] children = directoryPath.listFiles();
    if (children != null)
    {
      int i = 0;
      while (i < children.length)
      {
        File x = children[i++];
        if (x.isDirectory())
          recursiveDelete(x);
        else
          x.delete();
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
    StringBuffer rval = new StringBuffer();
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
    throws LCFException
  {
    return encrypt(input);
  }

  /** Start creating a hash
  */
  public static MessageDigest startHash()
    throws LCFException
  {
    try
    {
      return MessageDigest.getInstance("SHA");
    }
    catch (Exception e)
    {
      throw new LCFException("Couldn't encrypt: "+e.getMessage(),e,LCFException.GENERAL_ERROR);
    }
  }

  /** Add to hash
  */
  public static void addToHash(MessageDigest digest, String input)
    throws LCFException
  {
    try
    {
      byte[] inputBytes = input.getBytes("UTF-8");
      digest.update(inputBytes);
    }
    catch (Exception e)
    {
      throw new LCFException("Couldn't encrypt: "+e.getMessage(),e,LCFException.GENERAL_ERROR);
    }
  }

  /** Calculate final hash value
  */
  public static String getHashValue(MessageDigest digest)
    throws LCFException
  {
    try
    {
      byte[] encryptedBytes = digest.digest();
      StringBuffer rval = new StringBuffer();
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
      throw new LCFException("Couldn't encrypt: "+e.getMessage(),e,LCFException.GENERAL_ERROR);
    }
  }

  /** Perform standard one-way encryption of a string.
  *@param input is the string to encrypt.
  *@return the encrypted string.
  */
  public static String encrypt(String input)
    throws LCFException
  {
    MessageDigest hash = startHash();
    addToHash(hash,input);
    return getHashValue(hash);
  }

  /** Encode a string in a reversible obfuscation.
  *@param input is the input string.
  *@return the output string.
  */
  public static String obfuscate(String input)
    throws LCFException
  {
    try
    {
      if (input == null)
        return null;
      if (input.length() == 0)
        return input;
      // First, convert to binary
      byte[] array = input.getBytes("UTF-8");
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
      StringBuffer rval = new StringBuffer();
      i = 0;
      while (i < array.length)
      {
        int x = (int)array[i++];
        rval.append(writeNibble((x >> 4) & 0x0f));
        rval.append(writeNibble(x & 0x0f));
      }
      return rval.toString();
    }
    catch (java.io.UnsupportedEncodingException e)
    {
      throw new LCFException("UTF-8 not supported",e,LCFException.GENERAL_ERROR);
    }
  }

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
  public static String deobfuscate(String input)
    throws LCFException
  {
    try
    {
      if (input == null)
        return null;
      if (input.length() == 0)
        return input;

      if ((input.length() >> 1) * 2 != input.length())
        throw new LCFException("Decoding error",LCFException.GENERAL_ERROR);

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
      return new String(bytes,"UTF-8");
    }
    catch (java.io.UnsupportedEncodingException e)
    {
      throw new LCFException("UTF-8 unsupported",e,LCFException.GENERAL_ERROR);
    }
  }

  /** Read a hex nibble.
  *@param value is the character.
  *@return the value.
  */
  protected static int readNibble(char value)
    throws LCFException
  {
    if (value >= 'A' && value <= 'F')
      return (int)(value - 'A' + 10);
    else if (value >= '0' && value <= '9')
      return (int)(value - '0');
    else
      throw new LCFException("Bad hexadecimal value",LCFException.GENERAL_ERROR);
  }


  /** Install system database.
  *@param threadcontext is the thread context.
  *@param masterName is the master database name ("mysql" for mysql).
  *@param masterUsername is the master database user name.
  *@param masterPassword is the master database password.
  */
  public static void createSystemDatabase(IThreadContext threadcontext, String masterName,
    String masterUsername, String masterPassword)
    throws LCFException
  {
    String databaseName = getMasterDatabaseName();
    String databaseUsername = getMasterDatabaseUsername();
    String databasePassword = getMasterDatabasePassword();

    IDBInterface master = DBInterfaceFactory.make(threadcontext,masterName,masterUsername,masterPassword);
    master.createUserAndDatabase(databaseUsername,databasePassword,databaseName,null);
  }

  /** Drop system database.
  *@param threadcontext is the thread context.
  *@param masterName is the master database name ("mysql" for mysql).
  *@param masterUsername is the master database user name.
  *@param masterPassword is the master database password.
  */
  public static void dropSystemDatabase(IThreadContext threadcontext, String masterName,
    String masterUsername, String masterPassword)
    throws LCFException
  {
    IDBInterface master = DBInterfaceFactory.make(threadcontext,masterName,masterUsername,masterPassword);
    master.dropUserAndDatabase(getMasterDatabaseUsername(),getMasterDatabaseName(),null);
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
    throws LCFException
  {
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
      StringBuffer argBuffer = new StringBuffer();
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
            Reader r = new InputStreamReader(is);
            try
            {
              BufferedReader br = new BufferedReader(r);
              try
              {
                StringBuffer sb = new StringBuffer();
                while (true)
                {
                  String value = br.readLine();
                  if (value == null)
                    break;
                  sb.append(value).append("; ");
                }
                throw new LCFException("Shelled process '"+configChangeSignalCommand+"' failed with error "+Integer.toString(rval)+": "+sb.toString());
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
      throw new LCFException("Process wait interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
    }
    catch (InterruptedIOException e)
    {
      throw new LCFException("IO with subprocess interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new LCFException("IO exception signalling change: "+e.getMessage(),e);
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
    os.write(byteArray,0,byteArray.length);
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
    writeBytes(os,buffer);
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
      characters = stringValue.getBytes("utf-8");
    writeByteArray(os,characters);
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
    writeSdword(os,Float.floatToIntBits(floatValue));
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
    return new String(bytes,"utf-8");
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
  
  /** Perform system shutdown, using the registered shutdown hooks. */
  protected static void cleanUpSystem()
  {
    // It needs to call all registered shutdown hooks, in reverse order.
    // A failure of any one hook should cause the cleanup to continue, after a logging attempt is made.
    synchronized (cleanupHooks)
    {
      int i = cleanupHooks.size();
      while (i > 0)
      {
	i--;
	IShutdownHook hook = (IShutdownHook)cleanupHooks.get(i);
	try
	{
	  hook.doCleanup();
	}
	catch (LCFException e)
	{
	  Logging.root.warn("Error during system shutdown: "+e.getMessage(),e);
	}
      }
      cleanupHooks.clear();
    }
  }

  /** Class that tracks files that need to be cleaned up on exit */
  protected static class FileTrack implements IShutdownHook
  {
    /** Key and value are both File objects */
    protected HashMap filesToDelete = new HashMap();

    /** Constructor */
    public FileTrack()
    {
    }

    /** Add a file to track */
    public synchronized void addFile(File f)
    {
      filesToDelete.put(f,f);
    }

    /** Delete a file */
    public synchronized void deleteFile(File f)
    {
      f.delete();
      filesToDelete.remove(f);
    }

    /** Delete all remaining files */
    public void doCleanup()
      throws LCFException
    {
      synchronized (this)
      {
	Iterator iter = filesToDelete.keySet().iterator();
	while (iter.hasNext())
	{
	  File f = (File)iter.next();
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
        doCleanup();
      }
      finally
      {
        super.finalize();
      }
    }

  }

  /** Class that cleans up database handles on exit */
  protected static class DatabaseShutdown implements IShutdownHook
  {
    public DatabaseShutdown()
    {
    }
    
    public void doCleanup()
      throws LCFException
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
    }
  }
  
  /** Finisher thread, to be registered with the runtime */
  protected static class ShutdownThread extends Thread
  {
    protected ArrayList cleanupHooks;
    /** Constructor.
    */
    public ShutdownThread(ArrayList cleanupHooks)
    {
      super();
      setName("Shutdown thread");
    }

    public void run()
    {
      // This thread is run at shutdown time.
      cleanUpSystem();
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
      org.apache.lcf.core.database.ConnectionFactory.releaseAll();
    }
  }
}

