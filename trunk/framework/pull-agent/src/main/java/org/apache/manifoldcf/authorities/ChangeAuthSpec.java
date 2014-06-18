/* $Id: ChangeAuthSpec.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.authorities;

import java.io.*;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import org.apache.manifoldcf.authorities.system.*;
import java.util.*;

/** This class is used during testing.
*/
public class ChangeAuthSpec
{
        public static final String _rcsid = "@(#)$Id: ChangeAuthSpec.java 988245 2010-08-23 18:39:35Z kwright $";

        private ChangeAuthSpec()
        {
        }

        public static void main(String[] args)
        {
                if (args.length != 2)
                {
                        System.err.println("Usage: ChangeAuthSpec <connection_name> <connection_xml>");
                        System.exit(1);
                }

                String connectionName = args[0];
                String connectionXML = args[1];


                try
                {
                        IThreadContext tc = ThreadContextFactory.make();
                        ManifoldCF.initializeEnvironment(tc);
                        IAuthorityConnectionManager connManager = AuthorityConnectionManagerFactory.make(tc);
                        IAuthorityConnection conn = connManager.load(connectionName);
                        if (conn == null)
                        {
                                System.err.println("No such connection: '"+connectionName+"'");
                                System.exit(3);
                        }
                        conn.getConfigParams().fromXML(connectionXML);

                        // Now, save
                        connManager.save(conn);
                        System.out.println("Authority specification has been changed");
                }
                catch (Exception e)
                {
                        e.printStackTrace();
                        System.exit(2);
                }
        }
                
}
