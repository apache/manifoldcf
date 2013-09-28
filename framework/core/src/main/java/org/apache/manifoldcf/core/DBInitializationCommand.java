/* $Id: Obfuscate.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import org.apache.manifoldcf.core.interfaces.ThreadContextFactory;
import org.apache.manifoldcf.core.system.ManifoldCF;

/**
 * Parent class for all database initialization related commands. This class provides methods to
 * obtain username and password for the database. 
 *
 * @author Jettro Coenradie
 */
public abstract class DBInitializationCommand implements InitializationCommand
{
  private final String userName;
  private final String password;

  /**
   * The userName and password for the database on which the command needs to be performed
   *
   * @param userName String containing the mandatory database username
   * @param password String containing the mandatory database password
   */
  public DBInitializationCommand(String userName, String password)
  {
    this.userName = userName;
    this.password = password;
  }

  public void execute() throws ManifoldCFException
  {
    IThreadContext tc = ThreadContextFactory.make();
    ManifoldCF.initializeEnvironment(tc);
    doExecute(tc);
  }

  protected abstract void doExecute(IThreadContext tc) throws ManifoldCFException;

  protected String getPassword()
  {
    return password;
  }

  protected String getUserName()
  {
    return userName;
  }

}
