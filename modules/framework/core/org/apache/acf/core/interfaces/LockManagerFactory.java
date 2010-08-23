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
package org.apache.acf.core.interfaces;

import org.apache.acf.core.system.ACF;

public class LockManagerFactory
{
  public static final String _rcsid = "@(#)$Id$";

  private final static String lockManager = "_LockManager_";

  private LockManagerFactory()
  {
  }

  /** Instantiate a lock manager.
  * This should be thread specific (so that locks can nest properly in the same
  * thread).
  */
  public static ILockManager make(IThreadContext context)
    throws ACFException
  {
    Object x = context.get(lockManager);
    if (x == null || !(x instanceof ILockManager))
    {
      String implementationClass = ACF.getProperty(ACF.lockManagerImplementation);
      if (implementationClass == null)
        implementationClass = "org.apache.acf.core.lockmanager.LockManager";
      try
      {
        Class c = Class.forName(implementationClass);
        x = c.newInstance();
        if (!(x instanceof ILockManager))
          throw new ACFException("Lock manager class "+implementationClass+" does not implement ILockManager",ACFException.SETUP_ERROR);
        context.save(lockManager,x);
      }
      catch (ClassNotFoundException e)
      {
        throw new ACFException("Lock manager class "+implementationClass+" could not be found: "+e.getMessage(),e,ACFException.SETUP_ERROR);
      }
      catch (ExceptionInInitializerError e)
      {
        throw new ACFException("Lock manager class "+implementationClass+" could not be instantiated: "+e.getMessage(),e,ACFException.SETUP_ERROR);
      }
      catch (LinkageError e)
      {
        throw new ACFException("Lock manager class "+implementationClass+" could not be linked: "+e.getMessage(),e,ACFException.SETUP_ERROR);
      }
      catch (InstantiationException e)
      {
        throw new ACFException("Lock manager class "+implementationClass+" could not be instantiated: "+e.getMessage(),e,ACFException.SETUP_ERROR);
      }
      catch (IllegalAccessException e)
      {
        throw new ACFException("Lock manager class "+implementationClass+" had no public default initializer: "+e.getMessage(),e,ACFException.SETUP_ERROR);
      }
    }
    return (ILockManager)x;
  }

}

