/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.lcf.core.common;

import org.apache.lcf.core.interfaces.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.MalformedURLException;
import java.util.*;
import java.io.*;

/** An instance of this class is capable of minting URLClassLoader objects on
* demand, for the purpose of loading plugins.
*/
public class LCFResourceLoader
{
  public static final String _rcsid = "@(#)$Id$";

  /** The current 'working directory' */
  protected String instanceDir;
  /** The parent class loader */
  protected ClassLoader parent;
  /** The class loader we're caching */
  protected ClassLoader classLoader = null;
  /** The current 'classpath' - a list of File objects */
  protected ArrayList currentClasspath = new ArrayList();
  
  /** Construct a resource manager.
  *@param instanceDir is the current "working path" of the instance.
  *@param parent is the parent class loader.
  */
  public LCFResourceLoader(String instanceDir, ClassLoader parent)
    throws LCFException
  {
    this.instanceDir = makeLegalDir(instanceDir);
    this.parent = parent;
  }

  /** Set the classpath to a given list of libdirs.
  */
  public synchronized void setClassPath(ArrayList libdirList)
    throws LCFException
  {
    if (currentClasspath.size() > 0)
    {
      currentClasspath.clear();
      classLoader = null;
    }
    int i = 0;
    while (i < libdirList.size())
    {
      String path = (String)libdirList.get(i++);
      addToClassPath(path,null);
    }
  }
  
  /** Clear the class-search path.
  */
  public synchronized void clearClassPath()
  {
    if (currentClasspath.size() == 0)
      return;
    currentClasspath.clear();
    classLoader = null;
  }
  
  /** Add to the class-search path.
  *@param path is the path to a jar or class root, relative to the "working path" of this loader.
  */
  public synchronized void addToClassPath(String path)
    throws LCFException
  {
    final File file = resolvePath(new File(instanceDir), path);
    if (file.canRead())
    {
      addDirsToClassPath(new File[]{file.getParentFile()},
        new FileFilter[]{new FileFilter() {
          public boolean accept(final File pathname)
          {
            return pathname.equals(file);
          }
        } } );
    }
    else
      throw new LCFException("Path '"+path+"' does not exist or is not readable");
  }
  
  /** Add to the class-search path.
  *@param dir is the directory to add.
  *@param filter is the file filter to use on that directory.
  */
  public synchronized void addToClassPath(String dir, FileFilter filter)
    throws LCFException
  {
    File base = resolvePath(new File(instanceDir), dir);
    addDirsToClassPath(new File[]{base}, new FileFilter[]{filter});
  }

  /** Get the specified class using the proper classloader.
  *@param cname is the fully-qualified class name.
  */
  public synchronized Class findClass(String cname)
    throws ClassNotFoundException,LCFException
  {
    if (classLoader == null)
    {
      // Mint a class loader on demand
      if (currentClasspath.size() == 0)
        classLoader = parent;
      else
      {
        URL[] elements = new URL[currentClasspath.size()];
        
        for (int j = 0; j < currentClasspath.size(); j++)
        {
          try
          {
            URL element = ((File)currentClasspath.get(j)).toURI().normalize().toURL();
            elements[j] = element;
          }
          catch (MalformedURLException e)
          {
            // Should never happen, but...
            throw new LCFException(e.getMessage(),e);
          }
        }
        classLoader = URLClassLoader.newInstance(elements, parent);
      }
    }
    
    // If we ever get this far, we have a classloader at least...
    return Class.forName(cname,true,classLoader);
  }

  /** Add fully-resolved directories (with filters) to the current class path.
  *@param baseList is the list of library directories.
  *@param filterList is the corresponding list of filters.
  */
  protected void addDirsToClassPath(File[] baseList, FileFilter[] filterList)
    throws LCFException
  {
    int i = 0;
    while (i < baseList.length)
    {
      File base = baseList[i];
      FileFilter filter;
      if (filterList != null)
        filter = filterList[i];
      else
        filter = null;
      
      if (base.canRead() && base.isDirectory())
      {
        File[] files = base.listFiles(filter);
        
        if (files != null && files.length > 0)
        {
          int j = 0;
          while (j < files.length)
          {
            File file = files[j++];
            currentClasspath.add(file);
            // Invalidate the current classloader
            classLoader = null;
          }
        }
      }
      else
        throw new LCFException("Supposed directory '"+base.toString()+"' is either not readable, or is not a directory");
      i++;
    }
  }
  
  
  /** Ensures a path is always interpreted as a directory */
  protected static String makeLegalDir(String path)
  {
    return (path != null && (!(path.endsWith("/") || path.endsWith("\\"))))?path+File.separator: path;
  }

  /** Resolve a path.
  *@param base is the "working directory".
  *@param path is the path, to be calculated relative to the base.
  */
  protected static File resolvePath(File base,String path)
  {
    File r = new File(path);
    return r.isAbsolute() ? r : new File(base, path);
  }

}