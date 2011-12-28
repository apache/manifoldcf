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

package org.apache.manifoldcf.core.system;

import org.apache.manifoldcf.core.interfaces.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.MalformedURLException;
import java.util.*;
import java.io.*;

/** An instance of this class is capable of minting URLClassLoader objects on
* demand, for the purpose of loading plugins.
*/
public class ManifoldCFResourceLoader
{
  public static final String _rcsid = "@(#)$Id: ManifoldCFResourceLoader.java 988245 2010-08-23 18:39:35Z kwright $";

  /** The parent class loader */
  protected ClassLoader parent;
  /** The class loader we're caching */
  protected ClassLoader classLoader = null;
  /** The current 'classpath' - a list of File objects */
  protected ArrayList currentClasspath = new ArrayList();
  
  /** Construct a resource manager.
  *@param parent is the parent class loader.
  */
  public ManifoldCFResourceLoader(ClassLoader parent)
    throws ManifoldCFException
  {
    this.parent = parent;
  }

  /** Set the classpath to a given list of libdirs.
  *@param libdirList is an arraylist of File objects, each representing a directory.
  */
  public synchronized void setClassPath(ArrayList libdirList)
    throws ManifoldCFException
  {
    if (currentClasspath.size() > 0)
    {
      currentClasspath.clear();
      classLoader = null;
    }
    int i = 0;
    while (i < libdirList.size())
    {
      File dir = (File)libdirList.get(i++);
      addToClassPath(dir,null);
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
  *@param file is the jar or class root.
  */
  public synchronized void addToClassPath(final File file)
    throws ManifoldCFException
  {
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
      throw new ManifoldCFException("Path '"+file.toString()+"' does not exist or is not readable");
  }
  
  /** Add to the class-search path.
  *@param dir is the directory to add.
  *@param filter is the file filter to use on that directory.
  */
  public synchronized void addToClassPath(File dir, FileFilter filter)
    throws ManifoldCFException
  {
    addDirsToClassPath(new File[]{dir}, new FileFilter[]{filter});
  }

  /** Get the class loader representing this resource loader.
  */
  public synchronized ClassLoader getClassLoader()
    throws ManifoldCFException
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
            throw new ManifoldCFException(e.getMessage(),e);
          }
        }
        classLoader = URLClassLoader.newInstance(elements, parent);
      }
    }
    return classLoader;
  }
  
  /** Get the specified class using the proper classloader.
  *@param cname is the fully-qualified class name.
  */
  public Class findClass(String cname)
    throws ClassNotFoundException,ManifoldCFException
  {
    // If we ever get this far, we have a classloader at least...
    return Class.forName(cname,true,getClassLoader());
  }

  /** Add fully-resolved directories (with filters) to the current class path.
  *@param baseList is the list of library directories.
  *@param filterList is the corresponding list of filters.
  */
  protected void addDirsToClassPath(File[] baseList, FileFilter[] filterList)
    throws ManifoldCFException
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
        throw new ManifoldCFException("Supposed directory '"+base.toString()+"' is either not a directory, or is unreadable.");
      i++;
    }
  }
  

}
