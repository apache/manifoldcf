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
package org.apache.acf.crawler.connectors.memex;

import org.apache.acf.core.interfaces.*;
import java.util.*;

public class ModifyRecord
{
        public static final String _rcsid = "@(#)$Id$";

        private ModifyRecord()
        {
        }


        public static void main(String[] args)
        {
                if (args.length < 5 && (args.length & 1) != 1)
                {
                        System.err.println("Usage: ModifyRecord <servername> <port> <username> <password> <id> <name_1> <value_1> ... <name_N> <value_N>");
                        System.exit(1);
                }

                try
                {
                        MemexSupport handle = new MemexSupport(args[2],args[3],args[0],args[1]);
                        try
                        {
                                Hashtable fields = setupFields(args,5);
                                handle.modifyRecord(args[4],fields);
                        }
                        finally
                        {
                                handle.close();
                        }
                        System.err.println("Successfully modified");
                }
                catch (ACFException e)
                {
                        e.printStackTrace(System.err);
                        System.exit(2);
                }
        }

        protected static Hashtable setupFields(String[] args, int startingIndex)
        {
                Hashtable rval = new Hashtable();
                while (startingIndex < args.length)
                {
                        String name = args[startingIndex];
                        String value = args[startingIndex+1];
                        startingIndex += 2;
                        rval.put(name,value);
                }
                return rval;
        }
}
