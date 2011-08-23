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

/** Variable class representing an integer.
*/
public class VariableString extends VariableBase
{
  protected String value;
  
  public VariableString(String value)
  {
    this.value = value;
  }
  
  /** Get the variable's value as a string */
  public String getStringValue()
    throws ScriptException
  {
    return value;
  }

  /** Get the variable's value as an integer */
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
  public double getDoubleValue()
    throws ScriptException
  {
    return new Double(value).doubleValue();
  }

  public VariableReference plus(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("+ operand cannot be null");
    return new VariableString(value + v.getStringValue());
  }
  
  public VariableReference doubleEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("== operand cannot be null");
    return new VariableBoolean(value.equals(v.getStringValue()));
  }

  public VariableReference exclamationEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("!= operand cannot be null");
    return new VariableBoolean(!value.equals(v.getStringValue()));
  }

}
