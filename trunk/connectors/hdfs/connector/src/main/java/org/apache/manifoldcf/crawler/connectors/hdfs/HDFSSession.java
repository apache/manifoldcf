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

  private final FileSystem fileSystem;
  private final String nameNode;
  private final Configuration config;
  private final String user;
  
  public HDFSSession(String nameNode, String user) throws URISyntaxException, IOException, InterruptedException {
    this.nameNode = nameNode;
    this.user = user;
    // Switch class loaders so that scheme registration works properly
    ClassLoader ocl = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
      config = new Configuration();
      config.set("fs.defaultFS", nameNode);
      fileSystem = FileSystem.get(new URI(nameNode), config, user);
    } finally {
      Thread.currentThread().setContextClassLoader(ocl);
    }
  }

  public static void runMe()
  {
  }
  
  public Map<String, String> getRepositoryInfo() {
    Map<String, String> info = new HashMap<String, String>();

    info.put("Name Node", nameNode);
    info.put("Config", config.toString());
    info.put("User", user);
    // Commented much of this out because each timeout is too long if there's no connection
    info.put("Canonical Service Name", fileSystem.getCanonicalServiceName());
    //info.put("Default Block Size", Long.toString(fileSystem.getDefaultBlockSize()));
    //info.put("Default Replication", Short.toString(fileSystem.getDefaultReplication()));
    //info.put("Home Directory", fileSystem.getHomeDirectory().toUri().toString());
    //info.put("Working Directory", fileSystem.getWorkingDirectory().toUri().toString());
    return info;
  }

  public FileStatus[] listStatus(Path path)
    throws IOException {
    try {
      return fileSystem.listStatus(path);
    } catch (FileNotFoundException e) {
      return null;
    }
  }
  
  public URI getUri() {
    return fileSystem.getUri();
  }

  public FileStatus getObject(Path path) throws IOException {
    try {
      return fileSystem.getFileStatus(path);
    } catch(FileNotFoundException e) {
      return null;
    }
  }

  public FSDataInputStream getFSDataInputStream(Path path) throws IOException {
    try {
      return fileSystem.open(path);
    } catch (FileNotFoundException e) {
      return null;
    }
  }
  
  public void close() throws IOException {
    fileSystem.close();
  }
}
