/* $Id: CheckAll.java 953331 2010-06-10 14:22:50Z kwright $ */

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
package org.apache.acf.authorities;

import java.io.*;
import org.apache.acf.core.interfaces.*;
import org.apache.acf.authorities.interfaces.*;
import org.apache.acf.authorities.system.*;
import java.util.*;

/** This class is used during testing.
*/
public class CheckAll
{
  public static final String _rcsid = "@(#)$Id: CheckAll.java 953331 2010-06-10 14:22:50Z kwright $";

  private CheckAll()
  {
  }

  public static void main(String[] args)
  {
    if (args.length != 0)
    {
      System.err.println("Usage: CheckAll");
      System.exit(1);
    }

    try
    {
      LCF.initializeEnvironment();
      IThreadContext tc = ThreadContextFactory.make();
      // Now, get a list of the authority connections
      IAuthorityConnectionManager mgr = AuthorityConnectionManagerFactory.make(tc);
      IAuthorityConnection[] connections = mgr.getAllConnections();
      int i = 0;
      while (i < connections.length)
      {
        // Grab the connection and perform a check operation.
        // Check operations that return "connection working" are ignored.
        // Operations that return anything else stream stuff to standard error,
        // in a form which is parseable.  This will be an escaped form of the authority identifying string, followed by ":",
        // followed by the message and a newline
        IAuthorityConnection connection = connections[i++];
        String identifyingName = connection.getDescription();
        if (identifyingName == null || identifyingName.length() == 0)
          identifyingName = connection.getName();

        String className = connection.getClassName();
        int maxCount = connection.getMaxConnections();
        ConfigParams parameters = connection.getConfigParams();

        // Now, test the connection.
        String connectionStatus;
        try
        {
          IAuthorityConnector c = AuthorityConnectorFactory.grab(tc,className,parameters,maxCount);
          if (c != null)
          {
            try
            {
              connectionStatus = c.check();
            }
            finally
            {
              AuthorityConnectorFactory.release(c);
            }
          }
          else
            connectionStatus = "Connector not installed";
        }
        catch (LCFException e)
        {
          connectionStatus = "Threw exception: '"+e.getMessage()+"'";
        }

        if (connectionStatus.startsWith("Connection working"))
          continue;

        UTF8Stdout.println(encode(identifyingName)+":"+encode(connectionStatus));
      }
      System.err.println("Done getting authority status");
    }
    catch (Exception e)
    {
      System.err.print(e.getMessage());
      Logging.root.warn("Exception in CheckAll: "+e.getMessage(),e);
      System.exit(2);
    }
  }

  /** Encode a string so that it doesn't have control characters, newlines, or colons in it */
  protected static String encode(String input)
  {
    StringBuffer sb = new StringBuffer();
    int i = 0;
    while (i < input.length())
    {
      char x = input.charAt(i++);
      if (x == ':')
        sb.append('\\').append(x);
      else if (x < ' ' && x >= 0)
        sb.append(' ');
      else
        sb.append(x);
    }
    return sb.toString();
  }

}
