/* $Id: AddDoc.java 921329 2010-03-10 12:44:20Z kwright $ */

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
package org.apache.acf.crawler.connectors.jdbc;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.agents.interfaces.*;
import java.util.*;
import java.io.*;

public class AddDoc
{
        public static final String _rcsid = "@(#)$Id: AddDoc.java 921329 2010-03-10 12:44:20Z kwright $";

        private AddDoc()
        {
        }


        public static void main(String[] args)
        {
                if (args.length != 14)
                {
                        System.err.println("Usage: AddDoc <provider> <host> <databasename> <username> <password> <tablename> <idcolumn> <id> <urlcolumn> <url> <versioncolumn> <version> <contentcolumn> <content_file>");
                        System.exit(1);
                }

                try
                {
                        JDBCConnection handle = new JDBCConnection(args[0],args[1],args[2],args[3],args[4]);

                        // Build query
                        StringBuffer sb = new StringBuffer();
                        ArrayList paramList = new ArrayList();
                        sb.append("INSERT INTO ").append(args[5]).append("(").append(args[6]).append(",");
                        if (args[8].length() > 0)
                                sb.append(args[8]).append(",");
                        if (args[10].length() > 0)
                                sb.append(args[10]).append(",");
                        sb.append(args[12]).append(") VALUES (?,");
                        paramList.add(args[7]);
                        if (args[8].length() > 0)
                        {
                                sb.append("?,");
                                paramList.add(args[9]);
                        }
                        if (args[10].length() > 0)
                        {
                                sb.append("?,");
                                paramList.add(args[11]);
                        }
                        sb.append("?)");
                        InputStream is = new FileInputStream(new File(args[13]));
                        try
                        {
                                BinaryInput bi = new TempFileInput(is);
                                paramList.add(bi);

                        }
                        finally
                        {
                                is.close();
                        }
 
                        handle.executeOperation(sb.toString(),paramList);

                        System.err.println("Successfully added");
                }
                catch (IOException e)
                {
                        e.printStackTrace(System.err);
                        System.exit(2);
                }
                catch (ACFException e)
                {
                        e.printStackTrace(System.err);
                        System.exit(2);
                }
                catch (ServiceInterruption e)
                {
                        e.printStackTrace(System.err);
                        System.exit(2);
                }
        }

}
