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
package org.apache.manifoldcf.crawler;

import java.io.File;

import org.apache.manifoldcf.crawler.system.ManifoldCF;
import org.apache.manifoldcf.core.interfaces.*;

public class InitializeAndRegister
{
    public static final String _rcsid = "@(#)$Id$";

    private InitializeAndRegister()
    {
    }
    
    protected void doExecute(IThreadContext tc) throws ManifoldCFException 
    {
      // Do the basic initialization of the database and its schema
      ManifoldCF.createSystemDatabase(tc);
      
      ManifoldCF.installTables(tc);

      org.apache.manifoldcf.crawler.system.ManifoldCF.registerThisAgent(tc);
      
      ManifoldCF.reregisterAllConnectors(tc);
    }
    
    public static void main(String[] args)
    {
      if (args.length > 0)
      {
        System.err.println("Usage: InitializeAndRegister");
        System.exit(1);
      }

      try
      {
        IThreadContext tc = ThreadContextFactory.make();
        ManifoldCF.initializeEnvironment(tc);
      
        InitializeAndRegister register = new InitializeAndRegister();
        register.doExecute(tc);
        
        System.err.println("Successfully initialized database and registered all connectors");
      }
      catch (ManifoldCFException e)
      {
        e.printStackTrace();
        System.exit(1);
      }
    }
}
