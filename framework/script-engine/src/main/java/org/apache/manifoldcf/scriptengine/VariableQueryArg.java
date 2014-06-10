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

import org.apache.manifoldcf.core.util.URLEncoder;

/** Variable class representing a ManifoldCF query argument, with a name
* and a value.
*/
public class VariableQueryArg extends VariableBase
{
  protected final String name;
  protected final String value;
  
  public VariableQueryArg(String name, String value)
  {
    if (value == null)
      value = "";
    this.name = name;
    this.value = value;
  }
  
  @Override
  public int hashCode()
  {
    return name.hashCode() + value.hashCode();
  }
  
  @Override
  public boolean equals(Object o)
  {
    if (!(o instanceof VariableQueryArg))
      return false;
    VariableQueryArg other = (VariableQueryArg)o;
    return other.name.equals(name) && other.value.equals(value);
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

  /** Check if the variable has a query arg value */
  @Override
  public boolean hasQueryArgumentValue()
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
    sb.append("new queryarg ").append(escapeValue(name)).append("=").append(escapeValue(value));
    return sb.toString();
  }
  
  protected static String escapeValue(String input)
  {
    StringBuilder sb = new StringBuilder("\"");
    int i = 0;
    while (i < input.length())
    {
      char x = input.charAt(i++);
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
      return URLEncoder.encode(name).replace("+","%20") + "=" +
        URLEncoder.encode(value).replace("+","%20");
  }

  @Override
  public String getQueryArgumentValue()
    throws ScriptException
  {
    return getStringValue();
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
