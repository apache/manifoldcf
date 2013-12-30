/* $Id: LockClean.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.core;
import java.io.*;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.*;

/**
 * ManifoldCF makes use of a synchronization directory to store data about the current state of the synchronization between
 * the repository connection and the output connection. This class is used to clear this directory.
 */
public class LockClean implements InitializationCommand
{
  public static final String _rcsid = "@(#)$Id: LockClean.java 988245 2010-08-23 18:39:35Z kwright $";

  public LockClean()
  {
  }

  /**
   * {@inheritDoc}
   */
  public void execute() throws ManifoldCFException
  {
    ManifoldCF.initializeEnvironment(ThreadContextFactory.make());
    File synchDir = org.apache.manifoldcf.core.lockmanager.FileLockManager.getSynchDirectoryProperty();
    if (synchDir != null)
    {
      // Recursively clean up the contents of the synch directory. But don't remove the directory itself
      if (synchDir.isDirectory())
      {
        removeContentsOfDirectory(synchDir);
      }
    }
    Logging.root.info("Synchronization storage cleaned up");
  }

  /**
   * Removes the contents of the directory but not the directory itself.
   *
   * @param directory File representing the directory to remove
   */
  private void removeContentsOfDirectory(File directory)
  {
    File[] files = directory.listFiles();
    int i = 0;
    while (i < files.length)
    {
      if (files[i].isDirectory())
      {
        removeDirectory(files[i]);
      }
      else
      {
        files[i].delete();
      }
      i++;
    }
  }

  /**
   * Removes the contents of the directory as well as the directory itself.
   *
   * @param directory File representing the directory to completely remove
   */
  private void removeDirectory(File directory)
  {
    removeContentsOfDirectory(directory);
    // Remove the directory itself
    directory.delete();
  }

  /**
   * Useful when running this class standalone. You should not provide any arguments
   *
   * @param args String[] containing the arguments
   */
  public static void main(String[] args)
  {
    if (args.length != 0)
    {
      System.err.println("Usage: LockClean");
      System.exit(1);
    }

    LockClean lockClean = new LockClean();

    try
    {
      lockClean.execute();
      System.err.println("Synchronization storage cleaned up");
    }
    catch (ManifoldCFException e)
    {
      e.printStackTrace(System.err);
      System.exit(2);
    }
  }
}
