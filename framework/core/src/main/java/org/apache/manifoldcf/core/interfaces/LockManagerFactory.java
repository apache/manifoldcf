/* $Id: LockManagerFactory.java 988245 2010-08-23 18:39:35Z kwright $ */

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

public class LockManagerFactory
{
  public static final String _rcsid = "@(#)$Id: LockManagerFactory.java 988245 2010-08-23 18:39:35Z kwright $";

  private final static String lockManager = "_LockManager_";

  private LockManagerFactory()
  {
  }

  /** Instantiate a lock manager.
  * This should be thread specific (so that locks can nest properly in the same
  * thread).
  */
  public static ILockManager make(IThreadContext context)
    throws ManifoldCFException
  {
    Object x = context.get(lockManager);
    if (x == null || !(x instanceof ILockManager))
    {
      String implementationClass = ManifoldCF.getStringProperty(ManifoldCF.lockManagerImplementation,
        "org.apache.manifoldcf.core.lockmanager.LockManager");
      try
      {
        Class c = Class.forName(implementationClass);
        x = c.newInstance();
        if (!(x instanceof ILockManager))
          throw new ManifoldCFException("Lock manager class "+implementationClass+" does not implement ILockManager",ManifoldCFException.SETUP_ERROR);
        context.save(lockManager,x);
      }
      catch (ClassNotFoundException e)
      {
        throw new ManifoldCFException("Lock manager class "+implementationClass+" could not be found: "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
      }
      catch (ExceptionInInitializerError e)
      {
        throw new ManifoldCFException("Lock manager class "+implementationClass+" could not be instantiated: "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
      }
      catch (LinkageError e)
      {
        throw new ManifoldCFException("Lock manager class "+implementationClass+" could not be linked: "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
      }
      catch (InstantiationException e)
      {
        throw new ManifoldCFException("Lock manager class "+implementationClass+" could not be instantiated: "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
      }
      catch (IllegalAccessException e)
      {
        throw new ManifoldCFException("Lock manager class "+implementationClass+" had no public default initializer: "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
      }
    }
    return (ILockManager)x;
  }

  public static String getProperty(IThreadContext tc, String s)
    throws ManifoldCFException
  {
    return make(tc).getSharedConfiguration().getProperty(s);
  }
  
  public static String getStringProperty(IThreadContext tc, String s, String defaultValue)
    throws ManifoldCFException
  {
    return make(tc).getSharedConfiguration().getStringProperty(s, defaultValue);
  }
  
  public static String getPossiblyObfuscatedStringProperty(IThreadContext tc, String s, String defaultValue)
    throws ManifoldCFException
  {
    return make(tc).getSharedConfiguration().getPossiblyObfuscatedStringProperty(s, defaultValue);
  }
  
  public static int getIntProperty(IThreadContext tc, String s, int defaultValue)
    throws ManifoldCFException
  {
    return make(tc).getSharedConfiguration().getIntProperty(s, defaultValue);
  }

  public static long getLongProperty(IThreadContext tc, String s, long defaultValue)
    throws ManifoldCFException
  {
    return make(tc).getSharedConfiguration().getLongProperty(s, defaultValue);
  }
  
  public static double getDoubleProperty(IThreadContext tc, String s, double defaultValue)
    throws ManifoldCFException
  {
    return make(tc).getSharedConfiguration().getDoubleProperty(s, defaultValue);
  }
  
  public static boolean getBooleanProperty(IThreadContext tc, String s, boolean defaultValue)
    throws ManifoldCFException
  {
    return make(tc).getSharedConfiguration().getBooleanProperty(s, defaultValue);
  }
  
}

