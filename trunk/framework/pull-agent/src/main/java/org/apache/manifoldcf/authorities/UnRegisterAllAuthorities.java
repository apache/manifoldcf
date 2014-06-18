/* $Id: UnRegisterAllAuthorities.java 988245 2010-08-23 18:39:35Z kwright $ */

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

public class UnRegisterAllAuthorities extends BaseAuthoritiesInitializationCommand
{
  public static final String _rcsid = "@(#)$Id: UnRegisterAllAuthorities.java 988245 2010-08-23 18:39:35Z kwright $";

  private UnRegisterAllAuthorities()
  {
  }

  protected void doExecute(IAuthorityConnectorManager mgr) throws ManifoldCFException
  {
    IResultSet classNames = mgr.getConnectors();
    int i = 0;
    while (i < classNames.getRowCount())
    {
      IResultRow row = classNames.getRow(i++);
      mgr.unregisterConnector((String)row.getValue("classname"));
    }
    Logging.root.info("Successfully unregistered all connectors");
  }


  public static void main(String[] args)
  {
    if (args.length > 0)
    {
      System.err.println("Usage: UnRegisterAllAuthorities");
      System.exit(1);
    }


    try
    {
      UnRegisterAllAuthorities unRegisterAllAuthorities = new UnRegisterAllAuthorities();
      unRegisterAllAuthorities.execute();
      System.err.println("Successfully unregistered all connectors");
    }
    catch (ManifoldCFException e)
    {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
