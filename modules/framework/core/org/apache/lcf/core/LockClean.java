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
package org.apache.lcf.core;
import java.io.*;
import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.core.system.*;

public class LockClean
{
        public static final String _rcsid = "@(#)$Id$";

        private LockClean()
        {
        }


        public static void main(String[] args)
        {
                if (args.length != 0)
                {
                        System.err.println("Usage: LockClean");
                        System.exit(1);
                }

                LCF.initializeEnvironment();
                String synchDir = LCF.getProperty(LCF.synchDirectoryProperty);
                if (synchDir != null)
                {
                        // Recursively clean up the contents of the synch directory.
                        File dir = new File(synchDir);
                        if (dir.isDirectory())
                        {
                                File[] files = dir.listFiles();
                                int i = 0;
                                while (i < files.length)
                                {
                                        if (files[i].isDirectory())
                                                removeDirectory(files[i]);
                                        else
                                                files[i].delete();
                                        i++;
                                }
                        }
                }
                System.err.println("Synchronization storage cleaned up");
        }


        protected static void removeDirectory(File directory)
        {
                File[] files = directory.listFiles();
                int i = 0;
                while (i < files.length)
                {
                        if (files[i].isDirectory())
                                removeDirectory(files[i]);
                        else
                                files[i].delete();
                        i++;
                }
                // Remove the directory itself
                directory.delete();
        }

                
}
