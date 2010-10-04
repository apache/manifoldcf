/* $Id: UpdateDoc.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.jdbc;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import java.util.*;
import java.io.*;

public class UpdateDoc
{
        public static final String _rcsid = "@(#)$Id: UpdateDoc.java 988245 2010-08-23 18:39:35Z kwright $";

        private UpdateDoc()
        {
        }


        public static void main(String[] args)
        {
                if (args.length != 12)
                {
                        System.err.println("Usage: AddDoc <provider> <host> <databasename> <username> <password> <tablename> <idcolumn> <id> <versioncolumn> <version> <contentcolumn> <content_file>");
                        System.exit(1);
                }

                try
                {
                        JDBCConnection handle = new JDBCConnection(args[0],args[1],args[2],args[3],args[4]);

                        // Build query
                        StringBuffer sb = new StringBuffer();
                        ArrayList paramList = new ArrayList();
                        sb.append("UPDATE ").append(args[5]).append(" SET ");
                        if (args[8].length() > 0)
                        {
                                sb.append(args[8]).append("=?, ");
                                paramList.add(args[9]);
                        }
                        sb.append(args[10]).append("=?");
                        InputStream is = new FileInputStream(new File(args[11]));
                        try
                        {
                                paramList.add(new TempFileInput(is));
                        }
                        finally
                        {
                                is.close();
                        }
                        sb.append(" WHERE ").append(args[6]).append("=?");
                        paramList.add(args[7]);
 
                        handle.executeOperation(sb.toString(),paramList);

                        System.err.println("Successfully updated");
                }
                catch (IOException e)
                {
                        e.printStackTrace(System.err);
                        System.exit(2);
                }
                catch (ManifoldCFException e)
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
