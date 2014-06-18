/* $Id: IConnector.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.core.interfaces;

import java.io.*;
import java.util.*;

/** This interface represents the base methods every configurable connector needs.
* Connector instances are pooled, so that session setup does not need to be done repeatedly.  The
* pool is segregated by specific sets of configuration parameters.
*/
public interface IConnector
{

  /** Install the connector.
  * This method is called to initialize persistent storage for the connector, such as database tables etc.
  * It is called when the connector is registered.
  *@param threadContext is the current thread context.
  */
  public void install(IThreadContext threadContext)
    throws ManifoldCFException;

  /** Uninstall the connector.
  * This method is called to remove persistent storage for the connector, such as database tables etc.
  * It is called when the connector is deregistered.
  *@param threadContext is the current thread context.
  */
  public void deinstall(IThreadContext threadContext)
    throws ManifoldCFException;

  /** Connect.  The configuration parameters are included.
  *@param configParams are the configuration parameters for this connection.
  */
  public void connect(ConfigParams configParams);

  // All methods below this line will ONLY be called if a connect() call succeeded
  // on this instance!

  /** Test the connection.  Returns a string describing the connection integrity.
  *@return the connection's status as a displayable string.
  */
  public String check()
    throws ManifoldCFException;

  /** This method is periodically called for all connectors that are connected but not
  * in active use.
  */
  public void poll()
    throws ManifoldCFException;

  /** This method is called to assess whether to count this connector instance should
  * actually be counted as being connected.
  *@return true if the connector instance is actually connected.
  */
  public boolean isConnected();
  
  /** Close the connection.  Call this before discarding the repository connector.
  */
  public void disconnect()
    throws ManifoldCFException;

  /** Get configuration information.
  *@return the configuration information for this class.
  */
  public ConfigParams getConfiguration();

  /** Clear out any state information specific to a given thread.
  * This method is called when this object is returned to the connection pool.
  */
  public void clearThreadContext();

  /** Attach to a new thread.
  *@param threadContext is the new thread context.
  */
  public void setThreadContext(IThreadContext threadContext)
    throws ManifoldCFException;


  // UI support methods.
  //
  // These support methods are involved in setting up connection configuration information. The configuration methods cannot assume that the
  // current connector is connected.  That is why they receive a thread context argument.

  /** Output the configuration header section.
  * This method is called in the head section of the connector's configuration page.  Its purpose is to add the required tabs to the list, and to output any
  * javascript methods that might be needed by the configuration editing HTML.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale that the output should use.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters, List<String> tabsArray)
    throws ManifoldCFException, IOException;
  
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
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException;
  
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
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext, Locale locale, ConfigParams parameters)
    throws ManifoldCFException;
  
  /** View configuration.
  * This method is called in the body section of the authority connector's view configuration page.  Its purpose is to present the connection information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale that the output should use.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  */
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters)
    throws ManifoldCFException, IOException;

}