/* $Id: DBCreate.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.core;

import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.system.ManifoldCF;
import org.apache.manifoldcf.core.system.Logging;

/**
 * Create the database using the name as specified through the {@see ManifoldCF}. The username and password for the
 * configured database need to be specified during construction.
 */
public class DBCreate extends DBInitializationCommand
{
  public static final String _rcsid = "@(#)$Id: DBCreate.java 988245 2010-08-23 18:39:35Z kwright $";

  /**
   * The userName and password for the database to be created
   *
   * @param userName String containing the mandatory database username
   * @param password String containing the mandatory database password
   */
  public DBCreate(String userName, String password)
  {
    super(userName, password);
  }


  /**
   * {@inheritDoc}
   */
  public void doExecute(IThreadContext tc) throws ManifoldCFException
  {
    ManifoldCF.createSystemDatabase(tc, getUserName(), getPassword());
    Logging.root.info("ManifoldCF database created");
  }

  /**
   * Useful when running this class standalone. Provide two arguments, the first is the username, the second
   * the password. The password is optional, an empty string is used as the default password.
   *
   * @param args String[] containing the arguments
   */
  public static void main(String[] args)
  {
    if (args.length != 1 && args.length != 2)
    {
      System.err.println("Usage: DBCreate <dbuser> [<dbpassword>]");
      System.exit(1);
    }

    String userName = args[0];
    String password = "";
    if (args.length == 2)
    {
      password = args[1];
    }

    DBCreate dbCreate = new DBCreate(userName, password);

    try
    {
      dbCreate.execute();
      System.err.println("ManifoldCF database created");
    }
    catch (ManifoldCFException e)
    {
      e.printStackTrace();
      System.exit(1);
    }
  }


}
