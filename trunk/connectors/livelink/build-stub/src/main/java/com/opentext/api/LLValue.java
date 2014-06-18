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

import java.util.*;

/** Stub classes to get connector to build.
*/
public class LLValue
{
  public static final int LL_TRUE = 1;
  public static final int LL_FALSE = 0;
  
  public LLValue()
  {
  }
  
  public long toLong(int index, String attributeName)
  {
    return 0;
  }
  
  public long toLong(String attributeName)
  {
    return 0;
  }

  public int toInteger(int index, String attributeName)
  {
    return 0;
  }
  
  public int toInteger(String attributeName)
  {
    return 0;
  }
  
  public int size()
  {
    return 0;
  }
  
  public LLValue toValue(int index)
  {
    return null;
  }
  
  public String toString(String attributeName)
  {
     return null;
  }
  
  public LLValue setAssocNotSet()
  {
    return null;
  }
  
  public LLValue setAssoc()
  {
    return null;
  }
  
  public int add(String attributeName, int intValue)
  {
    return 0;
  }

  public int add(String attributeName, String strValue)
  {
    return 0;
  }

  public int add(String attributeName, LLValue llValue)
  {
    return 0;
  }
  
  public boolean isTable()
  {
    return false;
  }
  
  public boolean isRecord()
  {
    return false;
  }
  
  public LLValueEnumeration enumerateValues()
  {
    return null;
  }
  
  public String toString(int index, String attributeName)
  {
    return null;
  }
  
  public Date toDate(String attributeName)
  {
    return null;
  }
  
}

