/* $Id: DefineAuthorityConnection.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import java.util.*;

/** This class is used during testing.
*/
public class DefineAuthorityConnection
{
  public static final String _rcsid = "@(#)$Id: DefineAuthorityConnection.java 988245 2010-08-23 18:39:35Z kwright $";

  private DefineAuthorityConnection()
  {
  }


  public static void main(String[] args)
  {
    if (args.length < 4)
    {
      System.err.println("Usage: DefineAuthorityConnection <connection_name> <description> <connector_class> <pool_max> <param1>=<value1> ...");
      System.exit(1);
    }

    String connectionName = args[0];
    String description = args[1];
    String connectorClass = args[2];
    String poolMax = args[3];


    try
    {
      IThreadContext tc = ThreadContextFactory.make();
      ManifoldCF.initializeEnvironment(tc);
      IAuthorityConnectionManager mgr = AuthorityConnectionManagerFactory.make(tc);
      IAuthorityConnection conn = mgr.create();
      conn.setName(connectionName);
      conn.setDescription(description);
      conn.setClassName(connectorClass);
      conn.setMaxConnections(new Integer(poolMax).intValue());
      ConfigParams x = conn.getConfigParams();
      int i = 4;
      while (i < args.length)
      {
        String arg = args[i++];
        // Parse
        int pos = arg.indexOf("=");
        if (pos == -1)
          throw new ManifoldCFException("Argument missing =");
        String name = arg.substring(0,pos);
        String value = arg.substring(pos+1);
        if (name.endsWith("assword"))
          x.setObfuscatedParameter(name,value);
        else
          x.setParameter(name,value);
      }

      // Now, save
      mgr.save(conn);

    }
    catch (Exception e)
    {
      e.printStackTrace(System.err);
      System.exit(2);
    }
  }


}
