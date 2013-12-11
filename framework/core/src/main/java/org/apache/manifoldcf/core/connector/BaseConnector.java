/* $Id: BaseConnector.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.core.connector;

import org.apache.manifoldcf.core.interfaces.*;
import java.io.*;
import java.util.*;

/** This base class underlies all connector implementations.
*/
public abstract class BaseConnector implements IConnector
{
  public static final String _rcsid = "@(#)$Id: BaseConnector.java 988245 2010-08-23 18:39:35Z kwright $";

  // Config params
  protected ConfigParams params = null;

  // Current thread context
  protected IThreadContext currentContext = null;

  /** Install the connector.
  * This method is called to initialize persistent storage for the connector, such as database tables etc.
  * It is called when the connector is registered.
  *@param threadContext is the current thread context.
  */
  @Override
  public void install(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // Base install does nothing
  }

  /** Uninstall the connector.
  * This method is called to remove persistent storage for the connector, such as database tables etc.
  * It is called when the connector is deregistered.
  *@param threadContext is the current thread context.
  */
  @Override
  public void deinstall(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // Base uninstall does nothing
  }

  /** Connect.  The configuration parameters are included.
  *@param configParams are the configuration parameters for this connection.
  */
  @Override
  public void connect(ConfigParams configParams)
  {
    params = configParams;
  }

  // All methods below this line will ONLY be called if a connect() call succeeded
  // on this instance!

  /** Test the connection.  Returns a string describing the connection integrity.
  *@return the connection's status as a displayable string.
  */
  @Override
  public String check()
    throws ManifoldCFException
  {
    // Base version returns "OK" status.
    return "Connection working";
  }

  /** This method is periodically called for all connectors that are connected but not
  * in active use.
  */
  @Override
  public void poll()
    throws ManifoldCFException
  {
    // Base version does nothing
  }

  /** This method is called to assess whether to count this connector instance should
  * actually be counted as being connected.
  *@return true if the connector instance is actually connected.
  */
  @Override
  public boolean isConnected()
  {
    // Consider it connected.
    return true;
  }

  /** Close the connection.  Call this before discarding the repository connector.
  */
  @Override
  public void disconnect()
    throws ManifoldCFException
  {
    params = null;
  }

  /** Clear out any state information specific to a given thread.
  * This method is called when this object is returned to the connection pool.
  */
  @Override
  public void clearThreadContext()
  {
    currentContext = null;
  }

  /** Attach to a new thread.
  *@param threadContext is the new thread context.
  */
  @Override
  public void setThreadContext(IThreadContext threadContext)
    throws ManifoldCFException
  {
    currentContext = threadContext;
  }

  /** Get configuration information.
  *@return the configuration information for this class.
  */
  @Override
  public ConfigParams getConfiguration()
  {
    return params;
  }

  /** Output the configuration header section.
  * This method is called in the head section of the connector's configuration page.  Its purpose is to add the required tabs to the list, and to output any
  * javascript methods that might be needed by the configuration editing HTML.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale that the output should use.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    outputConfigurationHeader(threadContext,out,parameters,tabsArray);
  }

  /** Output the configuration header section.
  * This method is called in the head section of the connector's configuration page.  Its purpose is to add the required tabs to the list, and to output any
  * javascript methods that might be needed by the configuration editing HTML.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, ConfigParams parameters, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    // Call the old method signature, for backwards compatibility
    ArrayList<Object> localTabsArray = new ArrayList<Object>();
    outputConfigurationHeader(threadContext,out,parameters,localTabsArray);
    for (Object o : localTabsArray)
    {
      tabsArray.add((String)o);
    }
  }
  
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, ConfigParams parameters, ArrayList<Object> tabsArray)
    throws ManifoldCFException, IOException
  {
  }
  
  /** Output the configuration body section.
  * This method is called in the body section of the authority connector's configuration page.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editconnection".
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale that the output should use.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabName is the current tab name.
  */
  @Override
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException
  {
    outputConfigurationBody(threadContext,out,parameters,tabName);
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
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException
  {
  }

  /** Process a configuration post.
  * This method is called at the start of the authority connector's configuration page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the configuration parameters accordingly.
  * The name of the posted form is "editconnection".
  *@param threadContext is the local thread context.
  *@param variableContext is the set of variables available from the post, including binary file post information.
  *@param locale is the locale that the output should use.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the connection (and cause a redirection to an error page).
  */
  @Override
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext, Locale locale, ConfigParams parameters)
    throws ManifoldCFException
  {
    return processConfigurationPost(threadContext,variableContext,parameters);
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
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext, ConfigParams parameters)
    throws ManifoldCFException
  {
    return null;
  }

  /** View configuration.
  * This method is called in the body section of the authority connector's view configuration page.  Its purpose is to present the connection information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale that the output should use.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  */
  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters)
    throws ManifoldCFException, IOException
  {
    viewConfiguration(threadContext,out,parameters);
  }

  /** View configuration.
  * This method is called in the body section of the connector's view configuration page.  Its purpose is to present the connection information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  */
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, ConfigParams parameters)
    throws ManifoldCFException, IOException
  {
  }

  // Useful pack/unpack methods.  These are typically used for version strings, which
  // appear in some kinds of connectors (but not others).
  
  /** Stuffer for packing a single string with an end delimiter */
  protected static void pack(StringBuilder output, String value, char delimiter)
  {
    int i = 0;
    while (i < value.length())
    {
      char x = value.charAt(i++);
      if (x == '\\' || x == delimiter)
        output.append('\\');
      output.append(x);
    }
    output.append(delimiter);
  }

  /** Unstuffer for the above. */
  protected static int unpack(StringBuilder sb, String value, int startPosition, char delimiter)
  {
    while (startPosition < value.length())
    {
      char x = value.charAt(startPosition++);
      if (x == '\\')
      {
        if (startPosition < value.length())
          x = value.charAt(startPosition++);
      }
      else if (x == delimiter)
        break;
      sb.append(x);
    }
    return startPosition;
  }

  /** Stuffer for packing lists of fixed length */
  protected static void packFixedList(StringBuilder output, String[] values, char delimiter)
  {
    int i = 0;
    while (i < values.length)
    {
      pack(output,values[i++],delimiter);
    }
  }

  /** Unstuffer for unpacking lists of fixed length */
  protected static int unpackFixedList(String[] output, String value, int startPosition, char delimiter)
  {
    StringBuilder sb = new StringBuilder();
    int i = 0;
    while (i < output.length)
    {
      sb.setLength(0);
      startPosition = unpack(sb,value,startPosition,delimiter);
      output[i++] = sb.toString();
    }
    return startPosition;
  }

  /** Stuffer for packing lists of variable length */
  protected static void packList(StringBuilder output, List<String> values, char delimiter)
  {
    pack(output,Integer.toString(values.size()),delimiter);
    int i = 0;
    while (i < values.size())
    {
      pack(output,values.get(i++).toString(),delimiter);
    }
  }

  /** Another stuffer for packing lists of variable length */
  protected static void packList(StringBuilder output, String[] values, char delimiter)
  {
    pack(output,Integer.toString(values.length),delimiter);
    int i = 0;
    while (i < values.length)
    {
      pack(output,values[i++],delimiter);
    }
  }

  /** Unstuffer for unpacking lists of variable length.
  *@param output is the array to write the unpacked result into.
  *@param value is the value to unpack.
  *@param startPosition is the place to start the unpack.
  *@param delimiter is the character to use between values.
  *@return the next position beyond the end of the list.
  */
  protected static int unpackList(List<String> output, String value, int startPosition, char delimiter)
  {
    StringBuilder sb = new StringBuilder();
    startPosition = unpack(sb,value,startPosition,delimiter);
    try
    {
      int count = Integer.parseInt(sb.toString());
      int i = 0;
      while (i < count)
      {
        sb.setLength(0);
        startPosition = unpack(sb,value,startPosition,delimiter);
        output.add(sb.toString());
        i++;
      }
    }
    catch (NumberFormatException e)
    {
    }
    return startPosition;
  }

}