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
public class VariableInt extends VariableBase
{
  protected int value;
  
  public VariableInt(int value)
  {
    this.value = value;
  }

  @Override
  public int hashCode()
  {
    return new Integer(value).hashCode();
  }
  
  @Override
  public boolean equals(Object o)
  {
    if (!(o instanceof VariableInt))
      return false;
    return ((VariableInt)o).value == value;
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
    
  /** Get the variable's script value */
  @Override
  public String getScriptValue()
    throws ScriptException
  {
    return Integer.toString(value);
  }

  /** Get the variable's value as a string */
  @Override
  public String getStringValue()
    throws ScriptException
  {
    return Integer.toString(value);
  }

  /** Get the variable's value as an integer */
  @Override
  public int getIntValue()
    throws ScriptException
  {
    return value;
  }
  
  /** Get the variable's value as a double */
  @Override
  public double getDoubleValue()
    throws ScriptException
  {
    return (double)value;
  }

  @Override
  public VariableReference plus(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '+' operand cannot be null"));
    return new VariableInt(value + v.getIntValue());
  }
    
  @Override
  public VariableReference minus(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '-' operand cannot be null"));
    return new VariableInt(value - v.getIntValue());
  }

  
  @Override
  public VariableReference asterisk(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '*' operand cannot be null"));
    return new VariableInt(value * v.getIntValue());
  }
    
  @Override
  public VariableReference slash(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '/' operand cannot be null"));
    return new VariableInt(value / v.getIntValue());
  }
    
  @Override
  public VariableReference unaryMinus()
    throws ScriptException
  {
    return new VariableInt(-value);
  }
  
  @Override
  public VariableReference greaterAngle(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '>' operand cannot be null"));
    return new VariableBoolean(value > v.getIntValue());
  }
  
  @Override
  public VariableReference lesserAngle(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '<' operand cannot be null"));
    return new VariableBoolean(value < v.getIntValue());
  }
    
  @Override
  public VariableReference doubleEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '==' operand cannot be null"));
    return new VariableBoolean(value == v.getIntValue());
  }
    
  @Override
  public VariableReference greaterAngleEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '>=' operand cannot be null"));
    return new VariableBoolean(value >= v.getIntValue());
  }
    
  @Override
  public VariableReference lesserAngleEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '<=' operand cannot be null"));
    return new VariableBoolean(value <= v.getIntValue());
  }
  
  @Override
  public VariableReference exclamationEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '!=' operand cannot be null"));
    return new VariableBoolean(value != v.getIntValue());
  }
  
  @Override
  public VariableReference ampersand(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '&' operand cannot be null"));
    return new VariableInt(value & v.getIntValue());
  }
    
  @Override
  public VariableReference pipe(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '|' operand cannot be null"));
    return new VariableInt(value | v.getIntValue());
  }

  @Override
  public VariableReference unaryExclamation()
    throws ScriptException
  {
    return new VariableInt(value ^ value);
  }

}
