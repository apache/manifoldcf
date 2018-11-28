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
public class VariableBoolean extends VariableBase
{
  protected boolean value;
  
  public VariableBoolean(boolean value)
  {
    this.value = value;
  }

  @Override
  public int hashCode()
  {
    return new Boolean(value).hashCode();
  }
  
  @Override
  public boolean equals(Object o)
  {
    if (!(o instanceof VariableBoolean))
      return false;
    return ((VariableBoolean)o).value == value;
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
    if (value)
      return "true";
    return "false";
  }

  /** Check if the variable has a boolean value */
  @Override
  public boolean hasBooleanValue()
    throws ScriptException
  {
    return true;
  }

  /** Get the variable's value as a boolean */
  @Override
  public boolean getBooleanValue()
    throws ScriptException
  {
    return value;
  }

  @Override
  public VariableReference doubleEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '==' operand cannot be null"));
    return new VariableBoolean(value == v.getBooleanValue());
  }

  public VariableReference exclamationEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '!=' operand cannot be null"));
    return new VariableBoolean(value != v.getBooleanValue());
  }

  @Override
  public VariableReference doubleAmpersand(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '&&' operand cannot be null"));
    return new VariableBoolean(value && v.getBooleanValue());
  }
    
  @Override
  public VariableReference doublePipe(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '||' operand cannot be null"));
    return new VariableBoolean(value || v.getBooleanValue());
  }

  @Override
  public VariableReference ampersand(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '&' operand cannot be null"));
    return new VariableBoolean(value && v.getBooleanValue());
  }
    
  @Override
  public VariableReference pipe(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '|' operand cannot be null"));
    return new VariableBoolean(value || v.getBooleanValue());
  }

  @Override
  public VariableReference unaryExclamation()
    throws ScriptException
  {
    return new VariableBoolean(! value);
  }

}
