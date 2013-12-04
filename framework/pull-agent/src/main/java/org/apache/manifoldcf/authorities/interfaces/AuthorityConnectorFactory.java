/* $Id: AuthorityConnectorFactory.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.authorities.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.ManifoldCF;
import java.util.*;
import java.io.*;
import java.lang.reflect.*;

/** This class manages a pool of authority connectors.
*/
public class AuthorityConnectorFactory extends ConnectorFactory<IAuthorityConnector>
{
  // Static factory
  protected final static AuthorityConnectorFactory thisFactory = new AuthorityConnectorFactory();
  
  protected AuthorityConnectorFactory()
  {
  }

  @Override
  protected boolean isInstalled(IThreadContext tc, String className)
    throws ManifoldCFException
  {
    IAuthorityConnectorManager connMgr = AuthorityConnectorManagerFactory.make(tc);
    return connMgr.isInstalled(className);
  }

  /** Get the default response from a connector.  Called if the connection attempt fails.
  */
  public AuthorizationResponse getThisDefaultAuthorizationResponse(IThreadContext threadContext, String className, String userName)
    throws ManifoldCFException
  {
    IAuthorityConnector connector = getThisConnector(threadContext,className);
    if (connector == null)
      return null;
    return connector.getDefaultAuthorizationResponse(userName);
  }

  /** Install connector.
  *@param className is the class name.
  */
  public static void install(IThreadContext threadContext, String className)
    throws ManifoldCFException
  {
    thisFactory.installThis(threadContext,className);
  }

  /** Uninstall connector.
  *@param className is the class name.
  */
  public static void deinstall(IThreadContext threadContext, String className)
    throws ManifoldCFException
  {
    thisFactory.deinstallThis(threadContext,className);
  }

  /** Get the default response from a connector.  Called if the connection attempt fails.
  */
  public static AuthorizationResponse getDefaultAuthorizationResponse(IThreadContext threadContext, String className, String userName)
    throws ManifoldCFException
  {
    return thisFactory.getThisDefaultAuthorizationResponse(threadContext,className,userName);
  }

  /** Output the configuration header section.
  */
  public static void outputConfigurationHeader(IThreadContext threadContext, String className, IHTTPOutput out, Locale locale, ConfigParams parameters, ArrayList tabsArray)
    throws ManifoldCFException, IOException
  {
    thisFactory.outputThisConfigurationHeader(threadContext,className,out,locale,parameters,tabsArray);
  }

  /** Output the configuration body section.
  */
  public static void outputConfigurationBody(IThreadContext threadContext, String className, IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException
  {
    thisFactory.outputThisConfigurationBody(threadContext,className,out,locale,parameters,tabName);
  }

  /** Process configuration post data for a connector.
  */
  public static String processConfigurationPost(IThreadContext threadContext, String className, IPostParameters variableContext, Locale locale, ConfigParams configParams)
    throws ManifoldCFException
  {
    return thisFactory.processThisConfigurationPost(threadContext,className,variableContext,locale,configParams);
  }
  
  /** View connector configuration.
  */
  public static void viewConfiguration(IThreadContext threadContext, String className, IHTTPOutput out, Locale locale, ConfigParams configParams)
    throws ManifoldCFException, IOException
  {
    thisFactory.viewThisConfiguration(threadContext,className,out,locale,configParams);
  }

  /** Get a repository connector instance, but do NOT check if class is installed first!
  *@param className is the class name.
  *@return the instance.
  */
  public static IAuthorityConnector getConnectorNoCheck(String className)
    throws ManifoldCFException
  {
    return thisFactory.getThisConnectorNoCheck(className);
  }

  /** Get multiple authority connectors, all at once.  Do this in a particular order
  * so that any connector exhaustion will not cause a deadlock.
  */
  public static IAuthorityConnector[] grabMultiple(IThreadContext threadContext,
    String[] orderingKeys, String[] classNames, ConfigParams[] configInfos, int[] maxPoolSizes)
    throws ManifoldCFException
  {
    return thisFactory.grabThisMultiple(threadContext,IAuthorityConnector.class,orderingKeys,classNames,configInfos,maxPoolSizes);
  }

  /** Get an authority connector.
  * The connector is specified by its class and its parameters.
  *@param threadContext is the current thread context.
  *@param className is the name of the class to get a connector for.
  *@param configInfo are the name/value pairs constituting configuration info
  * for this class.
  */
  public static IAuthorityConnector grab(IThreadContext threadContext,
    String className, ConfigParams configInfo, int maxPoolSize)
    throws ManifoldCFException
  {
    return thisFactory.grabThis(threadContext,className,configInfo,maxPoolSize);
  }

  /** Release multiple authority connectors.
  */
  public static void releaseMultiple(IAuthorityConnector[] connectors)
    throws ManifoldCFException
  {
    thisFactory.releaseThisMultiple(connectors);
  }

  /** Release an authority connector.
  *@param connector is the connector to release.
  */
  public static void release(IAuthorityConnector connector)
    throws ManifoldCFException
  {
    thisFactory.releaseThis(connector);
  }

  /** Idle notification for inactive authority connector handles.
  * This method polls all inactive handles.
  */
  public static void pollAllConnectors(IThreadContext threadContext)
    throws ManifoldCFException
  {
    thisFactory.pollThisAllConnectors(threadContext);
  }

  /** Flush only those connector handles that are currently unused.
  */
  public static void flushUnusedConnectors(IThreadContext threadContext)
    throws ManifoldCFException
  {
    thisFactory.flushThisUnusedConnectors(threadContext);
  }

  /** Clean up all open authority connector handles.
  * This method is called when the connector pool needs to be flushed,
  * to free resources.
  *@param threadContext is the local thread context.
  */
  public static void closeAllConnectors(IThreadContext threadContext)
    throws ManifoldCFException
  {
    thisFactory.closeThisAllConnectors(threadContext);
  }

}

