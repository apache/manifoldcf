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

  public int hashCode()
  {
    return new Boolean(value).hashCode();
  }
  
  public boolean equals(Object o)
  {
    if (!(o instanceof VariableBoolean))
      return false;
    return ((VariableBoolean)o).value == value;
  }

  /** Get the variable's script value */
  public String getScriptValue()
    throws ScriptException
  {
    if (value)
      return "true";
    return "false";
  }

  /** Get the variable's value as a boolean */
  public boolean getBooleanValue()
    throws ScriptException
  {
    return value;
  }

  public VariableReference doubleEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("== operand cannot be null");
    return new VariableBoolean(value == v.getBooleanValue());
  }

  public VariableReference exclamationEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("!= operand cannot be null");
    return new VariableBoolean(value != v.getBooleanValue());
  }

  public VariableReference doubleAmpersand(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("&& operand cannot be null");
    return new VariableBoolean(value && v.getBooleanValue());
  }
    
  public VariableReference doublePipe(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("|| operand cannot be null");
    return new VariableBoolean(value || v.getBooleanValue());
  }

  public VariableReference ampersand(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("& operand cannot be null");
    return new VariableBoolean(value && v.getBooleanValue());
  }
    
  public VariableReference pipe(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("| operand cannot be null");
    return new VariableBoolean(value || v.getBooleanValue());
  }

  public VariableReference unaryExclamation()
    throws ScriptException
  {
    return new VariableBoolean(! value);
  }

}
