/* $Id: OutputConnectorFactory.java 988245 2010-08-23 18:39:35Z kwright $ */

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

import org.apache.manifoldcf.core.system.ManifoldCF;

import java.util.*;
import java.io.*;
import java.lang.reflect.*;

/** This is the base factory class for all IConnector objects.
*/
public abstract class ConnectorFactory<T extends IConnector>
{
  public static final String _rcsid = "@(#)$Id: OutputConnectorFactory.java 988245 2010-08-23 18:39:35Z kwright $";

  protected ConnectorFactory()
  {
  }

  /** Override this method to hook into a connector manager.
  */
  protected abstract boolean isInstalled(IThreadContext tc, String className)
    throws ManifoldCFException;
  
  /** Install connector.
  *@param className is the class name.
  */
  protected void installThis(IThreadContext threadContext, String className)
    throws ManifoldCFException
  {
    T connector = getThisConnectorNoCheck(className);
    connector.install(threadContext);
  }

  /** Uninstall connector.
  *@param className is the class name.
  */
  protected void deinstallThis(IThreadContext threadContext, String className)
    throws ManifoldCFException
  {
    T connector = getThisConnectorNoCheck(className);
    connector.deinstall(threadContext);
  }

  /** Output the configuration header section.
  */
  protected void outputThisConfigurationHeader(IThreadContext threadContext, String className,
    IHTTPOutput out, Locale locale, ConfigParams parameters, ArrayList tabsArray)
    throws ManifoldCFException, IOException
  {
    T connector = getThisConnector(threadContext, className);
    if (connector == null)
      return;
    connector.outputConfigurationHeader(threadContext,out,locale,parameters,tabsArray);
  }

  /** Output the configuration body section.
  */
  protected void outputThisConfigurationBody(IThreadContext threadContext, String className,
    IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException
  {
    T connector = getThisConnector(threadContext, className);
    if (connector == null)
      return;
    connector.outputConfigurationBody(threadContext,out,locale,parameters,tabName);
  }

  /** Process configuration post data for a connector.
  */
  protected String processThisConfigurationPost(IThreadContext threadContext, String className,
    IPostParameters variableContext, Locale locale, ConfigParams configParams)
    throws ManifoldCFException
  {
    T connector = getThisConnector(threadContext, className);
    if (connector == null)
      return null;
    return connector.processConfigurationPost(threadContext,variableContext,locale,configParams);
  }
  
  /** View connector configuration.
  */
  protected void viewThisConfiguration(IThreadContext threadContext, String className,
    IHTTPOutput out, Locale locale, ConfigParams configParams)
    throws ManifoldCFException, IOException
  {
    T connector = getThisConnector(threadContext, className);
    // We want to be able to view connections even if they have unregistered connectors.
    if (connector == null)
      return;
    connector.viewConfiguration(threadContext,out,locale,configParams);
  }

  /** Get a connector instance, without checking for installed connector.
  *@param className is the class name.
  *@return the instance.
  */
  protected T getThisConnectorNoCheck(String className)
    throws ManifoldCFException
  {
    T rval = getThisConnectorRaw(className);
    if (rval == null)
      throw new ManifoldCFException("No connector class '"+className+"' was found.");
    return rval;
  }

  /** Get a connector instance.
  *@param className is the class name.
  *@return the instance.
  */
  protected T getThisConnector(IThreadContext threadContext, String className)
    throws ManifoldCFException
  {
    if (!isInstalled(threadContext,className))
      return null;

    return getThisConnectorRaw(className);
  }
  
  /** Instantiate a connector, but return null if the class is not found.
  */
  protected T getThisConnectorRaw(String className)
    throws ManifoldCFException
  {
    try
    {
      Class theClass = ManifoldCF.findClass(className);
      Class[] argumentClasses = new Class[0];
      // Look for a constructor
      Constructor c = theClass.getConstructor(argumentClasses);
      Object[] arguments = new Object[0];
      Object o = c.newInstance(arguments);
      try
      {
        return (T)o;
      }
      catch (ClassCastException e)
      {
        throw new ManifoldCFException("Class '"+className+"' does not implement IConnector.");
      }
    }
    catch (InvocationTargetException e)
    {
      Throwable z = e.getTargetException();
      if (z instanceof Error)
        throw (Error)z;
      else if (z instanceof RuntimeException)
        throw (RuntimeException)z;
      else if (z instanceof ManifoldCFException)
        throw (ManifoldCFException)z;
      else
        throw new RuntimeException("Unknown exception type: "+z.getClass().getName()+": "+z.getMessage(),z);
    }
    catch (ClassNotFoundException e)
    {
      return null;
    }
    catch (NoSuchMethodException e)
    {
      throw new ManifoldCFException("No appropriate constructor for IConnector implementation '"+
        className+"'.  Need xxx().",
        e);
    }
    catch (SecurityException e)
    {
      throw new ManifoldCFException("Protected constructor for IConnector implementation '"+className+"'",
        e);
    }
    catch (IllegalAccessException e)
    {
      throw new ManifoldCFException("Unavailable constructor for IConnector implementation '"+className+"'",
        e);
    }
    catch (IllegalArgumentException e)
    {
      throw new ManifoldCFException("Shouldn't happen!!!",e);
    }
    catch (InstantiationException e)
    {
      throw new ManifoldCFException("InstantiationException for IConnector implementation '"+className+"'",
        e);
    }
    catch (ExceptionInInitializerError e)
    {
      throw new ManifoldCFException("ExceptionInInitializerError for IConnector implementation '"+className+"'",
        e);
    }

  }

}
