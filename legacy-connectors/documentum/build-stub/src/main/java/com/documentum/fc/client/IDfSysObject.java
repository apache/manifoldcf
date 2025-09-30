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
package com.documentum.fc.client;

import com.documentum.fc.common.*;

/** Stub interface to allow the connector to build fully.
*/
public interface IDfSysObject extends IDfPersistentObject
{
  public IDfVersionPolicy getVersionPolicy()
    throws DfException;
  public String getFile(String path)
    throws DfException;
  public String getTypeName()
    throws DfException;
  public int getFolderIdCount()
    throws DfException;
  public IDfId getFolderId(int i)
    throws DfException;
  public int getPageCount()
    throws DfException;
  public long getContentSize()
    throws DfException;
  public int getPermit()
    throws DfException;
  public boolean isHidden()
    throws DfException;
  public String getACLName()
    throws DfException;
  public String getACLDomain()
    throws DfException;
  public String getContentType()
    throws DfException;
  public String getObjectName()
    throws DfException;
}
