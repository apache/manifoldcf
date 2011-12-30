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
package org.apache.manifoldcf.core.tests;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.ManifoldCF;

import java.io.*;
import java.util.*;
import org.junit.*;

/** This is a testing base class that is responsible for setting up/tearing down the core Derby database. */
public class BaseHSQLDB extends Base
{
  /** Method to add properties to properties.xml contents.
  * Override this method to add properties clauses to the property file.
  */
  protected void writeProperties(StringBuilder output)
    throws Exception
  {
    super.writeProperties(output);
    String currentPathString = currentPath.getAbsolutePath();
    output.append(
      "  <property name=\"org.apache.manifoldcf.databaseimplementationclass\" value=\"org.apache.manifoldcf.core.database.DBInterfaceHSQLDB\"/>\n" +
      "  <property name=\"org.apache.manifoldcf.hsqldbdatabasepath\" value=\""+currentPathString.replaceAll("\\\\","/")+"\"/>\n" +
      "  <property name=\"org.apache.manifoldcf.database.maxquerytime\" value=\"30\"/>\n" +
      "  <property name=\"org.apache.manifoldcf.crawler.threads\" value=\"30\"/>\n" +
      "  <property name=\"org.apache.manifoldcf.crawler.expirethreads\" value=\"10\"/>\n" +
      "  <property name=\"org.apache.manifoldcf.crawler.cleanupthreads\" value=\"10\"/>\n" +
      "  <property name=\"org.apache.manifoldcf.crawler.deletethreads\" value=\"10\"/>\n" +
      "  <property name=\"org.apache.manifoldcf.database.maxhandles\" value=\"80\"/>\n"
    );
  }
  
}
