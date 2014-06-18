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
package com.opentext.api;

/** Stub classes to get connector to build.
*/
public class LAPI_DOCUMENTS
{
  public static final int PERM_SEE = 2;
  public static final int PERM_SEECONTENTS = 36865;
  public static final int RIGHT_GROUP = -4;
  public static final int RIGHT_SYSTEM = -2;
  public static final int RIGHT_WORLD = -1;
  public static final int RIGHT_OWNER = -3;
  public static final int PROJECTSUBTYPE = 202;
  public static final int CATEGORYSUBTYPE = 131;
  public static final int DOCUMENTSUBTYPE = 144;
  public static final int COMPOUNDDOCUMENTSUBTYPE = 136;
  public static final int FOLDERSUBTYPE = 0;
	
  public LAPI_DOCUMENTS(LLConnect session)
  {
  }
  
  public int ListObjectCategoryIDs(LLValue object, LLValue catID)
  {
    return 0;
  }
  
  public int GetObjectInfo(int vol, int id, LLValue objinfo)
  {
    return 0;
  }

  public int GetVersionInfo(int vol, int id, int revNumber, LLValue objinfo)
  {
    return 0;
  }
  
  public int FetchVersion(int vol, int id, int revNumber, java.io.OutputStream output)
  {
    return 0;
  }

  public int GetObjectRights(int vol, int objID, LLValue objinfo)
  {
    return 0;
  }
  
  public int GetObjectAttributesEx(LLValue objID, LLValue catID, LLValue returnval)
  {
    return 0;
  }
  
  public int FetchCategoryVersion(LLValue catID, LLValue catVersion)
  {
    return 0;
  }
  
  public int ListObjects(int vol, int objID, String something, String filterString, int perms, LLValue childrenDocs)
  {
    return 0;
  }
  
  public int AccessCategoryWS(LLValue entinfo)
  {
    return 0;
  }
  
  public int AccessEnterpriseWS(LLValue entinfo)
  {
    return 0;
  }
  
}

