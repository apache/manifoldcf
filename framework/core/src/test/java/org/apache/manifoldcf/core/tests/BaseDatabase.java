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

/** This is a testing base class that is responsible for selecting the database via an abstract method which can be overridden. */
public abstract class BaseDatabase extends Base
{

  /** Method to add properties to properties.xml contents.
  * Override this method to add properties clauses to the property file.
  */
  @Override
  protected void writeProperties(StringBuilder output)
    throws Exception
  {
    super.writeProperties(output);
    writeDatabaseProperties(output);
    writeDatabaseMaxQueryTimeProperty(output);
    writeDatabaseMaxHandlesProperty(output);
    writeCrawlerThreadsProperty(output);
    writeExpireThreadsProperty(output);
    writeCleanupThreadsProperty(output);
    writeDeleteThreadsProperty(output);
    writeConnectorDebugProperty(output);
  }
  
  /** Method to add database-specific (as opposed to test-specific) parameters to
  * property file.
  */
  protected void writeDatabaseProperties(StringBuilder output)
    throws Exception
  {
    writeDatabaseImplementationProperty(output);
    writeDatabaseControlProperties(output);
  }
  
  /** Method to write the database implementation property */
  protected void writeDatabaseImplementationProperty(StringBuilder output)
    throws Exception
  {
    output.append(
      "  <property name=\"org.apache.manifoldcf.databaseimplementationclass\" value=\""+getDatabaseImplementationClass()+"\"/>\n"
    );
  }
  
  /** Method to get database implementation class */
  protected abstract String getDatabaseImplementationClass()
    throws Exception;

  /** Method to write the database control properties. */
  protected abstract void writeDatabaseControlProperties(StringBuilder output)
    throws Exception;
  
  /** Method to write the max query time. */
  protected void writeDatabaseMaxQueryTimeProperty(StringBuilder output)
    throws Exception
  {
    output.append(
      "  <property name=\"org.apache.manifoldcf.database.maxquerytime\" value=\""+getDatabaseMaxQueryTimeProperty()+"\"/>\n"
    );
  }
  
  /** Method to get max query time property. */
  protected int getDatabaseMaxQueryTimeProperty()
    throws Exception
  {
    return 30;
  }
  
  /** Method to write the max handles. */
  protected void writeDatabaseMaxHandlesProperty(StringBuilder output)
    throws Exception
  {
    output.append(
      "  <property name=\"org.apache.manifoldcf.database.maxhandles\" value=\"80\"/>\n"
    );
  }
  
  /** Method to write crawler threads property. */
  protected void writeCrawlerThreadsProperty(StringBuilder output)
    throws Exception
  {
    output.append(
      "  <property name=\"org.apache.manifoldcf.crawler.threads\" value=\"30\"/>\n"
    );
  }
  
  /** Method to write expire threads property. */
  protected void writeExpireThreadsProperty(StringBuilder output)
    throws Exception
  {
    output.append(
      "  <property name=\"org.apache.manifoldcf.crawler.expirethreads\" value=\"10\"/>\n"
    );
  }

  /** Method to write cleanup threads property. */
  protected void writeCleanupThreadsProperty(StringBuilder output)
    throws Exception
  {
    output.append(
      "  <property name=\"org.apache.manifoldcf.crawler.cleanupthreads\" value=\"10\"/>\n"
    );
  }

  /** Method to write delete threads property. */
  protected void writeDeleteThreadsProperty(StringBuilder output)
    throws Exception
  {
    output.append(
      "  <property name=\"org.apache.manifoldcf.crawler.deletethreads\" value=\"10\"/>\n"
    );
  }

  /** Method to write connector debug property. */
  protected void writeConnectorDebugProperty(StringBuilder output)
    throws Exception
  {
    // By default, leave debug off
  }

}
