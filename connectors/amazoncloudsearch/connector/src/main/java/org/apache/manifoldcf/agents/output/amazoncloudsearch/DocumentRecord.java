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
package org.apache.manifoldcf.agents.output.amazoncloudsearch;

import org.apache.manifoldcf.core.interfaces.*;

import java.io.*;

public class DocumentRecord {
  
  protected final String host;
  protected final String path;
  protected final String uid;
  protected final String uri;
  protected final String activity;
  protected final Long dataSize;
  protected final BinaryInput data;
  
  public DocumentRecord(String host, String path, String uid, String uri, String activity, Long dataSize, BinaryInput data)
  {
    this.host = host;
    this.path = path;
    this.uid = uid;
    this.uri = uri;
    this.activity = activity;
    this.dataSize = dataSize;
    this.data = data;
  }

  public String getHost()
  {
    return host;
  }
  
  public String getPath()
  {
    return path;
  }
  
  public String getUid()
  {
    return uid;
  }
  
  public String getUri()
  {
    return uri;
  }
  
  public String getActivity()
  {
    return activity;
  }
  
  public Long getDataSize()
  {
    return dataSize;
  }
  
  public long getStreamLength()
    throws ManifoldCFException
  {
    if (data != null)
      return data.getLength();
    return 0L;
  }
  
  public InputStream getDataStream()
    throws ManifoldCFException
  {
    if (data != null)
      return data.getStream();
    return null;
  }
  
  public void close()
    throws ManifoldCFException
  {
    if (data != null)
      data.discard();
  }
  
}
