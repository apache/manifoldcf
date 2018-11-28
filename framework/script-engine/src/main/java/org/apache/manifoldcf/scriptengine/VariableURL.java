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
  protected String encodedArgs;
  
  public VariableURL(String baseURLValue)
  {
    this(baseURLValue,null);
  }
  
  public VariableURL(String baseURLValue, String encodedArgsValue)
  {
    this.encodedURL = baseURLValue;
    if (encodedURL.endsWith("/"))
      this.encodedURL = this.encodedURL.substring(0,this.encodedURL.length()-1);
    this.encodedArgs = encodedArgsValue;
  }
  
  @Override
  public int hashCode()
  {
    return encodedURL.hashCode() + encodedArgs.hashCode();
  }
  
  @Override
  public boolean equals(Object o)
  {
    if (!(o instanceof VariableURL))
      return false;
    VariableURL other = (VariableURL)o;
    if (!other.encodedURL.equals(encodedURL))
      return false;
    if (other.encodedArgs != null || encodedArgs != null)
    {
      if (other.encodedArgs == encodedArgs)
        return true;
      if (other.encodedArgs == null || encodedArgs == null)
        return false;
      return other.encodedArgs.equals(encodedArgs);
    }
    return true;
  }

  /** Check if the variable has a string value */
  @Override
  public boolean hasStringValue()
    throws ScriptException
  {
    return true;
  }

  /** Check if the variable has a script value */
  @Override
  public boolean hasScriptValue()
    throws ScriptException
  {
    return true;
  }

  /** Get the variable's script value */
  @Override
  public String getScriptValue()
    throws ScriptException
  {
    StringBuilder sb = new StringBuilder();
    String value = getStringValue();
    sb.append("\"");
    int i = 0;
    while (i < value.length())
    {
      char x = value.charAt(i++);
      if (x == '\\' || x == '\"')
        sb.append('\\');
      sb.append(x);
    }
    sb.append("\"");
    return sb.toString();
  }
  
  /** Get the variable's value as a string */
  @Override
  public String getStringValue()
    throws ScriptException
  {
    if (encodedArgs != null)
      return encodedURL + "?" + encodedArgs;
    else
      return encodedURL;
  }

  @Override
  public VariableReference plus(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '+' operand cannot be null"));
    String urlSide = encodedURL;
    if (v.hasURLPathValue())
      urlSide += "/" + v.getURLPathValue();
    String argSide = encodedArgs;
    if (v.hasQueryArgumentValue())
    {
      if (argSide == null)
        argSide = v.getQueryArgumentValue();
      else
        argSide += "&" + v.getQueryArgumentValue();
    }
    return new VariableURL(urlSide,argSide);
  }
  
  @Override
  public VariableReference doubleEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '==' operand cannot be null"));
    return new VariableBoolean(getStringValue().equals(v.getStringValue()));
  }

  @Override
  public VariableReference exclamationEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '!=' operand cannot be null"));
    return new VariableBoolean(!getStringValue().equals(v.getStringValue()));
  }

}
