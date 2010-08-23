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
package org.apache.acf.crawler.connectors.meridio;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.crawler.connectors.meridio.meridiowrapper.MeridioTestWrapper;

import org.apache.acf.crawler.connectors.meridio.DMDataSet.*;
import com.meridio.www.MeridioDMWS.*;
import com.meridio.www.MeridioDMWS.holders.*;

import org.apache.acf.crawler.connectors.meridio.RMDataSet.*;
import com.meridio.www.MeridioRMWS.*;

import java.io.*;

public class AddRecMultiple
{
        public static final String _rcsid = "@(#)$Id$";

        private AddRecMultiple()
        {
        }


        public static void main(String[] args)
        {
                if (args.length < 9 || args.length > 10)
                {
                        System.err.println("Usage: AddRecMultiple <docurl> <recurl> <username> <password> <folder> <filebasepath> <titlebasename> <levels> <docsperlevel> [<category>]");
                        System.err.println(" E.g.: AddRecMultiple ... ... ... ... /LargeDocs /root/largefiles lf 3 10 ACFSearchable");
                        System.exit(1);
                }

                try
                {
                        MeridioTestWrapper handle = new MeridioTestWrapper(args[0],args[1],args[2],args[3]);
                        
                        try
                        {
                                String meridioFolder = args[4];
                                String localBasePath = args[5];
                                String titleBasePrefix = args[6];
                                int levelCount = Integer.parseInt(args[7]);
                                int levelMax = Integer.parseInt(args[8]);
                                String category;
                                if (args.length > 8 && args[9] != null && args[9].length() > 0)
                                        category = args[9];
                                else
                                        category = null;
                        
                                File localBaseDir = new File(localBasePath);
                                File[] filesWithinDir = localBaseDir.listFiles();

                                int categoryID = 0;
                                if (category != null)
                                {
                                        categoryID = handle.findCategory(category);
                                        if (categoryID == 0)
                                                throw new Exception("Unknown category '"+category+"'");
                                }
                                int[] levelValues = new int[levelCount];
                                
                                // Loop over files first
                                int i = 0;
                                while (i < filesWithinDir.length)
                                {
                                        File theFile = filesWithinDir[i++];
                                        String fileName = theFile.getName();
                                        int j = fileName.indexOf(".");
                                        String filePiece;
                                        if (j != -1)
                                                filePiece = fileName.substring(0,j);
                                        else
                                                filePiece = fileName;

                                        // Reset values
                                        reset(levelValues);
                                        // Iterate through the target documents produced by this file
                                        while (true)
                                        {
                                                String title = titleBasePrefix + filePiece + getSuffix(levelValues);
                                                if (categoryID != 0)
                                                {
                                                        DOCUMENTS d = new DOCUMENTS();
                                                        d.setNewDocCategoryId(categoryID); 
                
                                                        handle.addRecordToFolder(meridioFolder,localBasePath,fileName,title,d,new DOCUMENT_CUSTOMPROPS[0]);
                                                }
                                                else
                                                {
                                                        handle.addRecordToFolder(meridioFolder,localBasePath,fileName,title);
                                                }
                                                if (increment(levelValues,levelMax))
                                                        break;
                                        }
                                }
                        }
                        finally
                        {
                                handle.logout();
                        }
                        System.err.println("Successfully added records");
                }
                catch (Exception e)
                {
                        e.printStackTrace(System.err);
                        System.exit(2);
                }
        }

        protected static void reset(int[] levelValues)
        {
                int i = 0;
                while (i < levelValues.length)
                {
                        levelValues[i++] = 0;
                }
        }
        
        protected static String getSuffix(int[] levelValues)
        {
                StringBuffer sb = new StringBuffer();
                int i = 0;
                while (i < levelValues.length)
                {
                        sb.append("-");
                        sb.append(levelValues[i++]);
                }
                return sb.toString();
        }

        protected static boolean increment(int[] levelValues, int levelMax)
        {
                int i = 0;
                while (i < levelValues.length)
                {
                        levelValues[i]++;
                        if (levelValues[i] < levelMax)
                                return false;
                        levelValues[i] = 0;
                        i++;
                }
                return true;
        }
}
