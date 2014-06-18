/* $Id: FileInfo.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.common.filenet;

import java.util.*;

public class FileInfo implements java.io.Serializable
{
  public static final String _rcsid = "@(#)$Id: FileInfo.java 988245 2010-08-23 18:39:35Z kwright $";

  protected String docClass;
  protected HashMap metadataValues = new HashMap();
  protected HashMap aclValues = new HashMap();
  protected HashMap denyAclValues = new HashMap();

  public FileInfo(String docClass)
  {
    this.docClass = docClass;
  }

  public void addMetadataValue(String metadataName, String metadataValue)
  {
    metadataValues.put(metadataName,metadataValue);
  }

  public void addAclValue(String aclValue)
  {
    aclValues.put(aclValue,aclValue);
  }

  public void addDenyAclValue(String aclValue)
  {
    denyAclValues.put(aclValue,aclValue);
  }

  public String getDocClass()
  {
    return docClass;
  }

  public int getMetadataCount()
  {
    return metadataValues.size();
  }

  public Iterator getMetadataIterator()
  {
    return metadataValues.keySet().iterator();
  }

  public String getMetadataValue(String metadataName)
  {
    return (String)metadataValues.get(metadataName);
  }

  public int getAclCount()
  {
    return aclValues.size();
  }

  public Iterator getAclIterator()
  {
    return aclValues.keySet().iterator();
  }

  public int getDenyAclCount()
  {
    return denyAclValues.size();
  }

  public Iterator getDenyAclIterator()
  {
    return denyAclValues.keySet().iterator();
  }
}
