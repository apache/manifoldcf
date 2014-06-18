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

/** Variable class representing an integer.
*/
public class VariableString extends VariableBase
{
  protected String value;

  public VariableString(String value)
  {
    this.value = value;
  }

  @Override
  public int hashCode()
  {
    return value.hashCode();
  }
  
  @Override
  public boolean equals(Object o)
  {
    if (!(o instanceof VariableString))
      return false;
    return ((VariableString)o).value.equals(value);
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

  /** Check if the variable has an int value */
  @Override
  public boolean hasIntValue()
    throws ScriptException
  {
    return true;
  }
  
  /** Check if the variable has a double value */
  @Override
  public boolean hasDoubleValue()
    throws ScriptException
  {
    return true;
  }

  /** Check if the variable has a URL path value */
  @Override
  public boolean hasURLPathValue()
    throws ScriptException
  {
    return true;
  }

  /** Get the variable's value as a URL path component */
  @Override
  public String getURLPathValue()
    throws ScriptException
  {
      return URLEncoder.encode(value).replace("+","%20");
  }

  /** Get the variable's script value */
  @Override
  public String getScriptValue()
    throws ScriptException
  {
    StringBuilder sb = new StringBuilder();
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
    return value;
  }

  /** Get the variable's value as an integer */
  @Override
  public int getIntValue()
    throws ScriptException
  {
    try
    {
      return Integer.parseInt(value);
    }
    catch (NumberFormatException e)
    {
      throw new ScriptException(e.getMessage(),e);
    }
  }
  
  /** Get the variable's value as a double */
  @Override
  public double getDoubleValue()
    throws ScriptException
  {
    return new Double(value).doubleValue();
  }

  @Override
  public VariableReference plus(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '+' operand cannot be null"));
    return new VariableString(value + v.getStringValue());
  }
  
  @Override
  public VariableReference doubleEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '==' operand cannot be null"));
    return new VariableBoolean(value.equals(v.getStringValue()));
  }

  @Override
  public VariableReference exclamationEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '!=' operand cannot be null"));
    return new VariableBoolean(!value.equals(v.getStringValue()));
  }

}
