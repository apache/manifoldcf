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

package org.apache.manifoldcf.scriptengine;

import java.net.*;
import java.io.*;

/** Variable class representing a ManifoldCF API URL.  As the URL is glued together, the
* individual path pieces are appropriately encoded.
*/
public class VariableURL extends VariableBase
{
  protected String encodedURL;
  
  public VariableURL(String baseURLValue)
  {
    this.encodedURL = baseURLValue;
    if (encodedURL.endsWith("/"))
      this.encodedURL = this.encodedURL.substring(0,this.encodedURL.length()-1);
  }
  
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("\"");
    int i = 0;
    while (i < encodedURL.length())
    {
      char x = encodedURL.charAt(i++);
      if (x == '\\' || x == '\"')
        sb.append('\\');
      sb.append(x);
    }
    sb.append("\"");
    return sb.toString();
  }
  
  /** Get the variable's value as a string */
  public String getStringValue()
    throws ScriptException
  {
    return encodedURL;
  }

  public VariableReference plus(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("+ operand cannot be null");
    try
    {
      return new VariableURL(encodedURL + "/" + URLEncoder.encode(v.getStringValue(),"utf-8").replace("+","%20"));
    }
    catch (UnsupportedEncodingException e)
    {
      throw new ScriptException(e.getMessage(),e);
    }
  }
  
  public VariableReference doubleEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("== operand cannot be null");
    return new VariableBoolean(encodedURL.equals(v.getStringValue()));
  }

  public VariableReference exclamationEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("!= operand cannot be null");
    return new VariableBoolean(!encodedURL.equals(v.getStringValue()));
  }

}
