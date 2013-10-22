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
package org.apache.manifoldcf.rss_tests;

import org.apache.manifoldcf.core.interfaces.*;

import java.io.*;
import java.util.*;
import org.junit.*;
import java.sql.*;
import javax.naming.*;
import javax.sql.*;

/** This is a very basic sanity check */
public class FlakyDerbyInstance extends org.apache.manifoldcf.core.database.DBInterfaceDerby
{

  public FlakyDerbyInstance(IThreadContext tc, String databaseName, String userName, String password)
    throws ManifoldCFException
  {
    super(tc,databaseName,userName,password);
  }

  public FlakyDerbyInstance(IThreadContext tc, String databaseName)
    throws ManifoldCFException
  {
    super(tc,databaseName);
  }

  @Override
  protected IResultSet execute(Connection connection, String query, List params, boolean bResults, int maxResults,
    ResultSpecification spec, ILimitChecker returnLimit)
    throws ManifoldCFException
  {
    // MHL
    return super.execute(connection,query,params,bResults,maxResults,spec,returnLimit);
  }

}
