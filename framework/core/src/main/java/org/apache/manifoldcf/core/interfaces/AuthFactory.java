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
package org.apache.manifoldcf.core.interfaces;

import org.apache.manifoldcf.core.system.ManifoldCF;
import java.lang.reflect.*;

/** This is the factory class for an IAuth.
*/
public class AuthFactory
{
  public static final String _rcsid = "@(#)$Id$";

  private final static String authName = "_Auth_";

  private AuthFactory()
  {
  }

  public static IAuth make(IThreadContext context)
    throws ManifoldCFException
  {
    Object x = context.get(authName);
    if (x == null || !(x instanceof IAuth))
    {
      String implementationClass = LockManagerFactory.getStringProperty(context, ManifoldCF.authImplementation,
        "org.apache.manifoldcf.core.auth.DefaultAuthenticator");
      try
      {
        Class c = Class.forName(implementationClass);
        Constructor constructor = c.getConstructor(new Class[]{IThreadContext.class});
        x = constructor.newInstance(new Object[]{context});
        if (!(x instanceof IAuth))
          throw new ManifoldCFException("Auth implementation class "+implementationClass+" does not implement IAuth",ManifoldCFException.SETUP_ERROR);
        context.save(authName,x);
      }
      catch (ClassNotFoundException e)
      {
        throw new ManifoldCFException("Auth implementation class "+implementationClass+" could not be found: "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
      }
      catch (ExceptionInInitializerError e)
      {
        throw new ManifoldCFException("Auth implementation class "+implementationClass+" could not be instantiated: "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
      }
      catch (LinkageError e)
      {
        throw new ManifoldCFException("Auth implementation class "+implementationClass+" could not be linked: "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
      }
      catch (InstantiationException e)
      {
        throw new ManifoldCFException("Auth implementation class "+implementationClass+" could not be instantiated: "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
      }
      catch (InvocationTargetException e)
      {
        throw new ManifoldCFException("Auth implementation class "+implementationClass+" could not be instantiated: "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
      }
      catch (NoSuchMethodException e)
      {
        throw new ManifoldCFException("Auth implementation class "+implementationClass+" had no constructor taking (IThreadContext): "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
      }
      catch (IllegalAccessException e)
      {
        throw new ManifoldCFException("Auth implementation class "+implementationClass+" had no public constructor taking (IThreadContext): "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
      }
    }
    return (IAuth)x;

  }

}
