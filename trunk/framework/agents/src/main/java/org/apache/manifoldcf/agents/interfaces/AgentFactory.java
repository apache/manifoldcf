/* $Id: AgentFactory.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.agents.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import java.lang.reflect.*;

/** This is the agent class factory.
*/
public class AgentFactory
{
  public static final String _rcsid = "@(#)$Id: AgentFactory.java 988245 2010-08-23 18:39:35Z kwright $";

  private AgentFactory()
  {
  }

  /** Make an agent, given a class name.
  *@param tc is the thread context.
  *@param className is the agent class name.
  *@return the agent.
  */
  public static IAgent make(String className)
    throws ManifoldCFException
  {
    try
    {
      Class theClass = Class.forName(className);
      Class[] argumentClasses = new Class[0];
      // Look for a constructor
      Constructor c = theClass.getConstructor(argumentClasses);
      Object[] arguments = new Object[0];
      Object o = c.newInstance(arguments);
      if (!(o instanceof IAgent))
        throw new ManifoldCFException("Class '"+className+"' does not implement IAgent.");
      return (IAgent)o;
    }
    catch (InvocationTargetException e)
    {
      Throwable z = e.getTargetException();
      if (z instanceof Error)
        throw (Error)z;
      else
        throw (ManifoldCFException)z;
    }
    catch (ClassNotFoundException e)
    {
      throw new ManifoldCFException("No class implementing IAgent called '"+
        className+"'.",
        e);
    }
    catch (NoSuchMethodException e)
    {
      throw new ManifoldCFException("No appropriate constructor for IAgent implementation '"+
        className+"'.  Need xxx().",
        e);
    }
    catch (SecurityException e)
    {
      throw new ManifoldCFException("Protected constructor for IAgent implementation '"+className+"'",
        e);
    }
    catch (IllegalAccessException e)
    {
      throw new ManifoldCFException("Unavailable constructor for IAgent implementation '"+className+"'",
        e);
    }
    catch (IllegalArgumentException e)
    {
      throw new ManifoldCFException("Shouldn't happen!!!",e);
    }
    catch (InstantiationException e)
    {
      throw new ManifoldCFException("InstantiationException for IAgent implementation '"+className+"'",
        e);
    }
    catch (ExceptionInInitializerError e)
    {
      throw new ManifoldCFException("ExceptionInInitializerError for IAgent implementation '"+className+"'",
        e);
    }
  }

}
