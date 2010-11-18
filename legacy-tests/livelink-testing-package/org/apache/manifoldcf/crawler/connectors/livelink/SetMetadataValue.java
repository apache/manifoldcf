/* $Id: SetMetadataValue.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.livelink;

import org.apache.manifoldcf.core.interfaces.*;

public class SetMetadataValue
{
        public static final String _rcsid = "@(#)$Id: SetMetadataValue.java 988245 2010-08-23 18:39:35Z kwright $";

        private SetMetadataValue()
        {
        }


        public static void main(String[] args)
        {
                if (args.length != 8)
                {
                        System.err.println("Usage: SetMetadataValue <servername> <port> <username> <password> <livelinkpath> <categorypath> <attrname> <attrvalue>");
                        System.exit(1);
                }

                try
                {
                        LiveLinkSupport handle = new LiveLinkSupport(args[0],new Integer(args[1]).intValue(),args[2],args[3]);
                        try
                        {
                                handle.setMetadataValue(args[4],args[5],args[6],args[7]);
                        }
                        finally
                        {
                                handle.close();
                        }
                        System.err.println("Successfully set metadata");
                }
                catch (ManifoldCFException e)
                {
                        e.printStackTrace(System.err);
                        System.exit(2);
                }
        }

}
