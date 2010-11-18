/* $Id: SetDocPermission.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.filenet;


public class SetDocPermission
{
        public static final String _rcsid = "@(#)$Id: SetDocPermission.java 988245 2010-08-23 18:39:35Z kwright $";

        private SetDocPermission()
        {
        }


        public static void main(String[] args)
        {
                if (args.length < 7)
                {
                        System.err.println("Usage: AddDoc <wsiurl> <user> <password> <fndomain> <objectstore> <fnid> <username_1> ... <username_n>");
                        System.exit(1);
                }
                
                try
                {
                        FilenetAddRemove handle = new FilenetAddRemove(args[1],args[2],args[3],args[4],args[0]);
                        String[] userIDs = new String[args.length - 6];
                        int i = 0;
                        while (i < userIDs.length)
                        {
                                userIDs[i] = args[6+i];
                                i++;
                        }
                        
                        handle.setDocumentPermissions(args[5],userIDs);
                        System.err.println("Successfully modified");
                }
                catch (Exception e)
                {
                        e.printStackTrace(System.err);
                        System.exit(2);
                }

        }

}
