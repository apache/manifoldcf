/* $Id: AddUserToSite.java 921329 2010-03-10 12:44:20Z kwright $ */

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
package org.apache.acf.crawler.connectors.sharepoint;

import org.apache.acf.core.interfaces.*;

public class AddUserToSite
{
        public static final String _rcsid = "@(#)$Id: AddUserToSite.java 921329 2010-03-10 12:44:20Z kwright $";

        private AddUserToSite()
        {
        }


        public static void main(String[] args)
        {
                if (args.length != 12)
                {
                        System.err.println("Usage: AddUserToSite <protocol> <servername> <port> <location> <username> <password> <domain> <site_url> <user_alias> <display_name> <email> <group>");
                        System.exit(1);
                }

                try
                {
                        FPSEPublish handle = new FPSEPublish(args[0],args[1],new Integer(args[2]).intValue(),args[3],args[4],args[5],args[6]);
                        try
                        {
                                handle.addSiteUser(args[7],args[8],args[9],args[10],args[11]);
                        }
                        finally
                        {
                                handle.close();
                        }
                        System.err.println("Successfully added user to site");
                }
                catch (ACFException e)
                {
                        e.printStackTrace(System.err);
                        System.exit(2);
                }
        }

}
