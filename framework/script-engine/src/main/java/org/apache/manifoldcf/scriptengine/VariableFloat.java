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
public class VariableFloat extends VariableBase
{
  protected double value;
  
  public VariableFloat(double value)
  {
    this.value = value;
  }
  
  /** Get a displayable string for the value */
  public String toString()
  {
    return new Double(value).toString();
  }
  
  /** Get the variable's value as a string */
  public String getStringValue()
    throws ScriptException
  {
    return new Double(value).toString();
  }

  /** Get the variable's value as an integer */
  public int getIntValue()
    throws ScriptException
  {
    return (int)value;
  }
  
  /** Get the variable's value as a double */
  public double getDoubleValue()
    throws ScriptException
  {
    return value;
  }

  public VariableReference plus(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("+ operand cannot be null");
    return new VariableFloat(value + v.getDoubleValue());
  }
    
  public VariableReference minus(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("- operand cannot be null");
    return new VariableFloat(value - v.getDoubleValue());
  }
    
  public VariableReference asterisk(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("* operand cannot be null");
    return new VariableFloat(value * v.getDoubleValue());
  }
    
  public VariableReference slash(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("/ operand cannot be null");
    return new VariableFloat(value / v.getDoubleValue());
  }
    
  public VariableReference unaryMinus()
    throws ScriptException
  {
    return new VariableFloat(-value);
  }
  
  public VariableReference greaterAngle(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("> operand cannot be null");
    return new VariableBoolean(value > v.getDoubleValue());
  }
  
  public VariableReference lesserAngle(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("< operand cannot be null");
    return new VariableBoolean(value < v.getDoubleValue());
  }
    
  public VariableReference doubleEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("== operand cannot be null");
    return new VariableBoolean(value == v.getDoubleValue());
  }
    
  public VariableReference greaterAngleEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(">= operand cannot be null");
    return new VariableBoolean(value >= v.getDoubleValue());
  }
    
  public VariableReference lesserAngleEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("<= operand cannot be null");
    return new VariableBoolean(value <= v.getDoubleValue());
  }
  
  public VariableReference exclamationEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("!= operand cannot be null");
    return new VariableBoolean(value != v.getDoubleValue());
  }

}
