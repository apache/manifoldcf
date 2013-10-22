/* $Id: Encoder.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** Various useful encoding methods for working with html
*/
public class Encoder
{
  public static final String _rcsid = "@(#)$Id: Encoder.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Escape a string that's inside an html attribute and thence inside javascript.
  *@param value is the input.
  *@return the escaped string.
  */
  public static String attributeJavascriptEscape(String value)
  {
    StringBuilder rval = new StringBuilder();
    int i = 0;
    while (i < value.length())
    {
      char x = value.charAt(i++);
      // First level of encoding: javascript string
      if (x == '\\' || x == '"' || x == '\'')
      {
        rval.append("\\").append(x);
      }
      else if (x == '\n')
      {
        rval.append("\\n");
      }
      else if (x == '\r')
      {
        rval.append("\\r");
      }
      else if (x == '\t')
      {
        rval.append("\\t");
      }
      else
        rval.append(x);
    }
    return attributeEscape(rval.toString());
  }

  /** Escape a string that's in an html body (script) area and thence inside javascript.
  *@param value is the input.
  *@return the escaped string.
  */
  public static String bodyJavascriptEscape(String value)
  {
    StringBuilder rval = new StringBuilder();
    int i = 0;
    while (i < value.length())
    {
      char x = value.charAt(i++);
      // First level of encoding: javascript string
      if (x == '\\' || x == '"' || x == '\'')
      {
        rval.append("\\").append(x);
      }
      else if (x == '\n')
      {
        rval.append("\\n");
      }
      else if (x == '\r')
      {
        rval.append("\\r");
      }
      else if (x == '\t')
      {
        rval.append("\\t");
      }
      else
        rval.append(x);
    }
    // Body escaping does not seem to be necessary inside <script></script>
    // blocks, at least when <!-- and //--> surround it.
    //return bodyEscape(rval.toString());
    return rval.toString();
  }

  /** Escape a string that's inside an html attribute.
  *@param value is the input.
  *@return the escaped string.
  */
  public static String attributeEscape(String value)
  {
    StringBuilder rval = new StringBuilder();
    int i = 0;
    while (i < value.length())
    {
      char x = value.charAt(i++);
      if (x == '\'' || x == '"' || x == '<' || x == '>' || x == '&'|| (x < ' ' && x >= 0))
      {
        rval.append("&#").append(Integer.toString((int)x)).append(";");
      }
      else
        rval.append(x);
    }
    return rval.toString();
  }

  /** Escape a string that's inside an html body.
  *@param value is the input.
  *@return the escaped string.
  */
  public static String bodyEscape(String value)
  {
    StringBuilder rval = new StringBuilder();
    int i = 0;
    while (i < value.length())
    {
      char x = value.charAt(i++);
      if (x == '<' || x == '>' || x == '&' || (x < ' ' && x >= 0))
      {
        rval.append("&#").append(Integer.toString((int)x)).append(";");
      }
      else
        rval.append(x);
    }
    return rval.toString();
  }

}
