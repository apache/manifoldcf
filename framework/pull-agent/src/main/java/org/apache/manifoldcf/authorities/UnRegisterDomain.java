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
package org.apache.manifoldcf.authorities;

import java.io.*;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import org.apache.manifoldcf.authorities.system.*;

public class UnRegisterDomain extends BaseDomainsInitializationCommand
{
  public static final String _rcsid = "@(#)$Id$";

  private final String domainName;

  public UnRegisterDomain(String domainName)
  {
    this.domainName = domainName;
  }

  protected void doExecute(IAuthorizationDomainManager mgr) throws ManifoldCFException
  {
    mgr.unregisterDomain(domainName);
    Logging.root.info("Successfully unregistered domain '"+domainName+"'");
  }

  public static void main(String[] args)
  {
    if (args.length != 1)
    {
      System.err.println("Usage: UnRegisterDomain <domainname>");
      System.exit(1);
    }

    String domainName = args[0];

    try
    {
      UnRegisterDomain unRegisterDomain = new UnRegisterDomain(domainName);
      unRegisterDomain.execute();
      System.err.println("Successfully unregistered domain '"+domainName+"'");
    }
    catch (ManifoldCFException e)
    {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
