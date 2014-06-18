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
public class LAPI_ATTRIBUTES
{
  public final static int CATEGORY_TYPE_LIBRARY = 0;

  public LAPI_ATTRIBUTES(LLConnect session)
  {
  }
  
  public int AttrGetValues(LLValue catVersion, String attrName, int something, LLValue somethingelse, LLValue result)
  {
    return 0;
  }
  
  public int AttrListNames(LLValue catVersion, LLValue something, LLValue rvalue)
  {
    return 0;
  }
  
}

