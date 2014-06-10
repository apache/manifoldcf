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
package org.apache.manifoldcf.agents;

import java.io.*;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.agents.system.ManifoldCF;
import java.util.*;

/** This class is a command that is used to delete a transformation connection.
*/
public class DeleteTransformationConnection
{
        public static final String _rcsid = "@(#)$Id$";

        private DeleteTransformationConnection()
        {
        }


        public static void main(String[] args)
        {
                if (args.length != 1)
                {
                        System.err.println("Usage: DeleteTransformationConnection <connection_name>");
                        System.exit(1);
                }

                String connectionName = args[0];
                try
                {
                        IThreadContext tc = ThreadContextFactory.make();
                        ManifoldCF.initializeEnvironment(tc);
                        ITransformationConnectionManager mgr = TransformationConnectionManagerFactory.make(tc);
                        mgr.delete(connectionName);

                }
                catch (Exception e)
                {
                        e.printStackTrace();
                        System.exit(2);
                }
        }



                
}
