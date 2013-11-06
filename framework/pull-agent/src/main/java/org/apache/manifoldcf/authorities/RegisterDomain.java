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

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import org.apache.manifoldcf.authorities.system.*;

public class RegisterDomain extends BaseDomainsInitializationCommand
{
  public static final String _rcsid = "@(#)$Id$";

  private final String domainName;
  private final String description;

  public RegisterDomain(String domainName, String description)
  {
    this.domainName = domainName;
    this.description = description;
  }

  protected void doExecute(IAuthorizationDomainManager mgr) throws ManifoldCFException
  {
    mgr.registerDomain(description,domainName);
    Logging.root.info("Successfully registered authorization domain '"+domainName+"'");
  }

  public static void main(String[] args)
  {
    if (args.length != 2)
    {
      System.err.println("Usage: RegisterDomain <domainname> <description>");
      System.exit(1);
    }

    String domainName = args[0];
    String description = args[1];

    try
    {
      RegisterDomain registerDomain = new RegisterDomain(domainName,description);
      registerDomain.execute();
      System.err.println("Successfully registered authorization domain '"+domainName+"'");
    }
    catch (ManifoldCFException e)
    {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
