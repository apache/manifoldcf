/* $Id: TestBase.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.core.tests;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.ManifoldCF;

import java.io.*;
import java.util.*;
import org.junit.*;

/** This is a testing base class that is responsible for setting up/tearing down the core Postgresql database. */
public class BasePostgresql extends BaseDatabase
{
  protected final static String SUPER_USER_NAME = "postgres";
  protected final static String SUPER_USER_PASSWORD = "postgres";

  /** Method to get database implementation class */
  @Override
  protected String getDatabaseImplementationClass()
    throws Exception
  {
    return "org.apache.manifoldcf.core.database.DBInterfacePostgreSQL";
  }

  /** Method to set database properties */
  @Override
  protected void writeDatabaseControlProperties(StringBuilder output)
    throws Exception
  {
    output.append(
      "  <property name=\"org.apache.manifoldcf.database.name\" value=\"testdb\"/>\n" +
      "  <property name=\"org.apache.manifoldcf.database.username\" value=\"testuser\"/>\n"
    );
  }

  /** Method to get max query time property. */
  @Override
  protected int getDatabaseMaxQueryTimeProperty()
    throws Exception
  {
    return 15;
  }

  /** Method to get database superuser name.
  */
  @Override
  protected String getDatabaseSuperuserName()
    throws Exception
  {
    return SUPER_USER_NAME;
  }
  
  /** Method to get database superuser password.
  */
  @Override
  protected String getDatabaseSuperuserPassword()
    throws Exception
  {
    return SUPER_USER_PASSWORD;
  }

}
