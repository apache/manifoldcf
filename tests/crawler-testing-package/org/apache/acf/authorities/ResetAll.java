/* $Id: ResetAll.java 921329 2010-03-10 12:44:20Z kwright $ */

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
package org.apache.acf.authorities;

import java.io.*;
import org.apache.acf.core.interfaces.*;
import org.apache.acf.authorities.interfaces.*;
import org.apache.acf.authorities.system.*;
import java.util.*;

/** This class is used during testing.
*/
public class ResetAll
{
        public static final String _rcsid = "@(#)$Id: ResetAll.java 921329 2010-03-10 12:44:20Z kwright $";

        private ResetAll()
        {
        }

        public static void main(String[] args)
        {
                if (args.length != 0)
                {
                        System.err.println("Usage: ResetAll");
                        System.exit(1);
                }

                try
                {
                        ACF.initializeEnvironment();
                        IThreadContext tc = ThreadContextFactory.make();
                        // Now, get a list of the authority connections
                        IAuthorityConnectionManager mgr = AuthorityConnectionManagerFactory.make(tc);
                        IAuthorityConnection[] connections = mgr.getAllConnections();
                        int i = 0;
                        while (i < connections.length)
                        {
                                mgr.delete(connections[i++].getName());
                        }
                        System.err.println("Reset complete");
                }
                catch (Exception e)
                {
                        e.printStackTrace();
                        System.exit(2);
                }
        }
                
}
