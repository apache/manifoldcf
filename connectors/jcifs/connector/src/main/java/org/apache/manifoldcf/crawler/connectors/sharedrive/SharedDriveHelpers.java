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
package org.apache.manifoldcf.crawler.connectors.sharedrive;

import org.apache.manifoldcf.core.interfaces.ManifoldCFException;

import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;

import jcifs.smb.ACE;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileFilter;

/** This class contains test code that is useful for performing test operations
using JCifs from the appliance.  Basic operations are: addDocument, deleteDocument,
  addFolderUser, and deleteFolderUser.
*/
public class SharedDriveHelpers
{
  public static final String _rcsid = "@(#)$Id: SharedDriveHelpers.java 988245 2010-08-23 18:39:35Z kwright $";

  private NtlmPasswordAuthentication pa;
  private SmbFile smbconnection;

  /** Construct the helper and initialize the connection.
  *@param serverName is the DNS name of the server.
  *@param userName is the name to use to log in.
  *@param password is the password.
  */
  public SharedDriveHelpers(String serverName, String userName, String password)
    throws ManifoldCFException
  {
    try
    {
      // make the smb connection to the server
      // use NtlmPasswordAuthentication so that we can reuse credential for DFS support
      pa = new NtlmPasswordAuthentication(userName + ":" + password);
      smbconnection = new SmbFile("smb://" + serverName + "/",pa);
    }
    catch (MalformedURLException e)
    {
      throw new ManifoldCFException("Unable to access SMB/CIFS share: "+serverName, e, ManifoldCFException.SETUP_ERROR);
    }
  }

  /** Close the connection.
  */
  public void close()
    throws ManifoldCFException
  {
    // Just let stuff go
    pa = null;
    smbconnection = null;
  }

  /** See if a document exists.
  *@param targetPath is the document's path, beginning with the share name and
  *       separated by "/" characters.
  *@return the target path if the document is found, or "" if it is not.
  */
  public String lookupDocument(String targetPath)
    throws ManifoldCFException
  {
    try
    {
      String identifier = mapToIdentifier(targetPath);
      SmbFile file = new SmbFile(identifier,pa);
      if (file.exists())
        return targetPath;
      return "";
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO exception: "+e.getMessage(),e);
    }
  }

  /** Add a document.
  *@param targetPath is the target path, beginning with the share name and separated
  *       by "/" characters.
  *@param sourceFile is the local source file name to copy to the target.
  *@return the target path.
  */
  public String addDocument(String targetPath, String sourceFile)
    throws ManifoldCFException
  {
    try
    {
      String identifier = mapToIdentifier(targetPath);
      SmbFile file = new SmbFile(identifier,pa);
      // Open source file for read
      InputStream is = new FileInputStream(sourceFile);
      try
      {
        // Open smbfile for write
        if (!file.exists())
        {
          file.createNewFile();
          file = new SmbFile(identifier,pa);
        }
        OutputStream os = file.getOutputStream();
        try
        {
          byte[] bytes = new byte[65536];
          while (true)
          {
            int amt = is.read(bytes,0,bytes.length);
            if (amt == -1)
              break;
            if (amt > 0)
              os.write(bytes,0,amt);
          }
        }
        finally
        {
          os.close();
        }
      }
      finally
      {
        is.close();
      }
      return targetPath;
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO exception: "+e.getMessage(),e);
    }
  }

  /** Delete a document.
  *@param targetPath is the file path to delete, beginning with the share name and
  *       separated by "/" characters.
  */
  public void deleteDocument(String targetPath)
    throws ManifoldCFException
  {
    try
    {
      String identifier = mapToIdentifier(targetPath);
      SmbFile file = new SmbFile(identifier,pa);
      file.delete();
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO exception: "+e.getMessage(),e);
    }
  }

  /** Add user ACL to folder.
  *@param targetPath is the folder path to add the acl to, beginning with the share
  *       name and separated by "/" characters.
  *@param userName is the user to add.
  */
  public void addUserToFolder(String targetPath, String userName)
    throws ManifoldCFException
  {
    // MHL
  }

  /** Remove user ACL from folder.
  *@param targetPath is the folder path to add the acl to, beginning with the share
  *       name and separated by "/" characters.
  *@param userName is the user to remove.
  */
  public void removeUserFromFolder(String targetPath, String userName)
    throws ManifoldCFException
  {
    // MHL
  }

  /** Map a "path" specification to a full identifier.
  */
  protected String mapToIdentifier(String path)
    throws IOException
  {
    String smburi = smbconnection.getCanonicalPath();
    String uri = smburi + path + "/";
    return new SmbFile(uri,pa).getCanonicalPath();
  }

}
