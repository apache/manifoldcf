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
package org.apache.manifoldcf.crawler.connectors.filesystem.tests;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Helper class for local file manipulation */
public class FileHelper
{

  protected static void createDirectory(File f)
    throws Exception
  {
    if (f.mkdirs() == false)
      throw new Exception("Failed to create directory "+f.toString());
  }
  
  protected static void removeDirectory(File f)
    throws Exception
  {
    File[] files = f.listFiles();
    if (files != null)
    {
      int i = 0;
      while (i < files.length)
      {
        File subfile = files[i++];
        if (subfile.isDirectory())
          removeDirectory(subfile);
        else
          subfile.delete();
      }
    }
    f.delete();
  }
  
  protected static void createFile(File f, String contents)
    throws Exception
  {
    OutputStream os = new FileOutputStream(f);
    try
    {
      Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8);
      try
      {
        w.write(contents);
      }
      finally
      {
        w.flush();
      }
    }
    finally
    {
      os.close();
    }
  }
  
  protected static void removeFile(File f)
    throws Exception
  {
    if (f.delete() == false)
      throw new Exception("Failed to delete file "+f.toString());
  }
  
  protected static void changeFile(File f, String newContents)
    throws Exception
  {
    removeFile(f);
    createFile(f,newContents);
  }
  
}
