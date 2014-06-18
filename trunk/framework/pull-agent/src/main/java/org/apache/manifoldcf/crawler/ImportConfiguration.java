/* $Id: ImportConfiguration.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.system.*;

/**
 * This class provides a script hook to allow import of crawler configuration information from a file.
 */
public class ImportConfiguration extends BaseCrawlerInitializationCommand
{
  public static final String _rcsid = "@(#)$Id: ImportConfiguration.java 988245 2010-08-23 18:39:35Z kwright $";

  private final String importFilename;
  private final String passCode;

  public ImportConfiguration(String importFilename, String passCode)
  {
    this.importFilename = importFilename;
    this.passCode = passCode;
  }

  protected void doExecute(IThreadContext tc) throws ManifoldCFException
  {
    ManifoldCF.importConfiguration(tc,importFilename, passCode);
    Logging.root.info("Configuration imported");
  }

  public static void main(String[] args)
  {
    if (args.length != 1 && args.length != 2)
    {
      System.err.println("Usage: ImportConfiguration <filename> [<passcode>]");
      System.exit(1);
    }

    String importFilename = args[0];
    String passCode = (args.length == 2) ? args[1] : null;

    try
    {
      ImportConfiguration importConfiguration = new ImportConfiguration(importFilename, passCode);
      importConfiguration.execute();
      System.err.println("Configuration imported");
    }
    catch (Exception e)
    {
      e.printStackTrace();
      System.exit(2);
    }
  }
}
