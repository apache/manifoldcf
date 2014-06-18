/* $Id: MultilineParser.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.ui.util;

import java.util.*;

/** Utility methods for converting multi-line inputs into arrays.
*/
public class MultilineParser
{
  public static final String _rcsid = "@(#)$Id: MultilineParser.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Read a string as a sequence of individual expressions, urls, etc.
  */
  public static ArrayList stringToArray(String input)
  {
    ArrayList list = new ArrayList();
    try
    {
      java.io.Reader str = new java.io.StringReader(input);
      try
      {
        java.io.BufferedReader is = new java.io.BufferedReader(str);
        try
        {
          while (true)
          {
            String nextString = is.readLine();
            if (nextString == null)
              break;
            if (nextString.length() == 0)
              continue;
            if (nextString.startsWith("#"))
              continue;
            list.add(nextString);
          }
        }
        finally
        {
          is.close();
        }
      }
      finally
      {
        str.close();
      }
    }
    catch (java.io.IOException e)
    {
      return null;
    }
    return list;
  }

}
