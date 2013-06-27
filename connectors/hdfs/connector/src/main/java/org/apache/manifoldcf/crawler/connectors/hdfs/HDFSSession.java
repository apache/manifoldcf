/* $Id: DropboxSession.java 1490621 2013-06-07 12:55:04Z kwright $ */

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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.manifoldcf.crawler.connectors.hdfs;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.manifoldcf.core.common.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.HashMap;

/**
 *
 * @author andrew
 */
public class HDFSSession {

  private FileSystem fileSystem;
  private String nameNode;
  private Configuration config;
  private String user;
  
  public HDFSSession(String nameNode, Configuration config, String user) throws URISyntaxException, IOException, InterruptedException {
    this.nameNode = nameNode;
    this.config = config;
    this.user = user;
    fileSystem = FileSystem.get(new URI(nameNode), config, user);
  }

  public Map<String, String> getRepositoryInfo() {
    Map<String, String> info = new HashMap<String, String>();

    info.put("Name Node", nameNode);
    info.put("Config", config.toString());
    info.put("User", user);
    info.put("Canonical Service Name", fileSystem.getCanonicalServiceName());
    info.put("Default Block Size", Long.toString(fileSystem.getDefaultBlockSize()));
    info.put("Default Replication", Short.toString(fileSystem.getDefaultReplication()));
    info.put("Home Directory", fileSystem.getHomeDirectory().toUri().toString());
    info.put("Working Directory", fileSystem.getWorkingDirectory().toUri().toString());
    return info;
  }

  public void getSeeds(XThreadStringBuffer idBuffer, String path)
    throws IOException, InterruptedException {

    /*
     * need to add root dir so that single files such as /file1 will still get read
     */
    idBuffer.add(path);
    
    /*
     * gets a list of the contents of the entire folder: subfolders + files
     */
    FileStatus[] fileStatuses = fileSystem.listStatus(new Path(path));
    for (FileStatus fileStatus : fileStatuses) {
      /*
       * only add the directories as seeds, we'll add the files later
       */
      if (fileStatus.isDir()) {
        idBuffer.add(fileStatus.getPath().toUri().toString());
      }
    }
  }
  
  public FileSystem getFileSystem() {
	  return fileSystem;
  }
  
  public FileStatus getObject(String id) throws IOException {
    try {
      return fileSystem.getFileStatus(new Path(id));
    } catch(FileNotFoundException e) {
      return null;
    }
  }

  public FSDataInputStream getFSDataInputStream(String id) throws IOException {
    return fileSystem.open(new Path(id));
  }
  
  public void close() throws IOException {
    fileSystem.close();
  }
}
