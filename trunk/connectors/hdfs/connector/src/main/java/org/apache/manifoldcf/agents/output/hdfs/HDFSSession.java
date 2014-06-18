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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.manifoldcf.agents.output.hdfs;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.manifoldcf.core.common.*;

import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.HashMap;

/**
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

  public Map<String, String> getRepositoryInfo() {
    Map<String, String> info = new HashMap<String, String>();

    info.put("Name Node", nameNode);
    info.put("Config", config.toString());
    info.put("User", user);
    info.put("Canonical Service Name", fileSystem.getCanonicalServiceName());
    //info.put("Default Block Size", Long.toString(fileSystem.getDefaultBlockSize()));
    //info.put("Default Replication", Short.toString(fileSystem.getDefaultReplication()));
    //info.put("Home Directory", fileSystem.getHomeDirectory().toUri().toString());
    //info.put("Working Directory", fileSystem.getWorkingDirectory().toUri().toString());
    return info;
  }

  public void deleteFile(Path path)
    throws IOException {
    if (fileSystem.exists(path)) {
      fileSystem.delete(path, true);
    }
  }

  public void createFile(Path path, InputStream input)
    throws IOException {
    /*
      * make directory
      */
    if (!fileSystem.exists(path.getParent())) {
      fileSystem.mkdirs(path.getParent());
    }

    /*
      * delete old file
      */
    if (fileSystem.exists(path)) {
      fileSystem.delete(path, true);
    }

    FSDataOutputStream output = fileSystem.create(path);
    try {
      /*
       * write file
       */
      byte buf[] = new byte[65536];
      int len;
      while((len = input.read(buf)) != -1) {
        output.write(buf, 0, len);
      }
      output.flush();
    } finally {
      output.close();
    }

    // Do NOT close input; it's closed by the caller.
  }

  public URI getUri() {
    return fileSystem.getUri();
  }

  public void close() throws IOException {
    fileSystem.close();
  }
}
