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

import org.apache.manifoldcf.core.interfaces.*;

/** Base class for variables.
* Basically, everything is illegal until overridden.
*/
public class VariableBase implements Variable, VariableReference
{
  public VariableBase()
  {
  }
  
  /** Check if the variable has a string value */
  @Override
  public boolean hasStringValue()
    throws ScriptException
  {
    return false;
  }

  /** Check if the variable has a script value */
  @Override
  public boolean hasScriptValue()
    throws ScriptException
  {
    return false;
  }

  /** Check if the variable has a Configuration value */
  @Override
  public boolean hasConfigurationValue()
    throws ScriptException
  {
    return false;
  }
  
  /** Check if the variable has a ConfigurationNode value */
  @Override
  public boolean hasConfigurationNodeValue()
    throws ScriptException
  {
    return false;
  }

  /** Check if the variable has a boolean value */
  @Override
  public boolean hasBooleanValue()
    throws ScriptException
  {
    return false;
  }
  
  /** Check if the variable has an int value */
  @Override
  public boolean hasIntValue()
    throws ScriptException
  {
    return false;
  }
  
  /** Check if the variable has a double value */
  @Override
  public boolean hasDoubleValue()
    throws ScriptException
  {
    return false;
  }
    
  /** Check if the variable has a query argument value */
  @Override
  public boolean hasQueryArgumentValue()
    throws ScriptException
  {
    return false;
  }

  /** Check if the variable has a URL path value */
  @Override
  public boolean hasURLPathValue()
    throws ScriptException
  {
    return false;
  }
    

  /** Get the variable's script value */
  @Override
  public String getScriptValue()
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Variable has no script value"));
  }

  /** Get the variable's value as a string */
  @Override
  public String getStringValue()
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Cannot convert variable to string"));
  }
  
  /** Get the variable's value as a Configuration object */
  @Override
  public Configuration getConfigurationValue()
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Cannot convert variable to Configuration object"));
  }
    
  /** Get the variable's value as a ConfigurationNode object */
  @Override
  public ConfigurationNode getConfigurationNodeValue()
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Cannot convert variable to ConfigurationNode object"));
  }

  /** Get the variable's value as a boolean */
  @Override
  public boolean getBooleanValue()
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Cannot convert variable to boolean"));
  }
  
  /** Get the variable's value as an integer */
  @Override
  public int getIntValue()
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Cannot convert variable to int"));
  }
  
  /** Get the variable's value as a double */
  @Override
  public double getDoubleValue()
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Cannot convert variable to float"));
  }

  /** Get the variable's value as a properly-encoded query argument */
  @Override
  public String getQueryArgumentValue()
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Cannot convert variable to query argument"));
  }

  /** Get the variable's value as a URL path component */
  @Override
  public String getURLPathValue()
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Cannot convert variable to URL path component"));
  }
    
  // Operations
  
  @Override
  public VariableReference plus(Variable v)
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Binary '+' operator illegal for this type"));
  }
    
  @Override
  public VariableReference minus(Variable v)
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Binary '-' operator illegal for this type"));
  }
    
  @Override
  public VariableReference asterisk(Variable v)
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Binary '*' operator illegal for this type"));
  }
    
  @Override
  public VariableReference slash(Variable v)
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Binary '/' operator illegal for this type"));
  }
    
  @Override
  public VariableReference unaryMinus()
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Unary '-' operator illegal for this type"));
  }
  
  @Override
  public VariableReference greaterAngle(Variable v)
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Binary '>' operator illegal for this type"));
  }
    
  @Override
  public VariableReference lesserAngle(Variable v)
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Binary '<' operator illegal for this type"));
  }
    
  @Override
  public VariableReference doubleEquals(Variable v)
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Binary '==' operator illegal for this type"));
  }
    
  @Override
  public VariableReference greaterAngleEquals(Variable v)
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Binary '>=' operator illegal for this type"));
  }
    
  @Override
  public VariableReference lesserAngleEquals(Variable v)
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Binary '<=' operator illegal for this type"));
  }
  
  @Override
  public VariableReference exclamationEquals(Variable v)
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Binary '!=' operator illegal for this type"));
  }
  
  @Override
  public VariableReference ampersand(Variable v)
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Binary '&' operator illegal for this type"));
  }
    
  @Override
  public VariableReference pipe(Variable v)
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Binary '|' operator illegal for this type"));
  }

  @Override
  public VariableReference doubleAmpersand(Variable v)
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Binary '&&' operator illegal for this type"));
  }
    
  @Override
  public VariableReference doublePipe(Variable v)
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Binary '||' operator illegal for this type"));
  }
  
  @Override
  public VariableReference unaryExclamation()
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Unary '!' operator illegal for this type"));
  }

  // The following operations allow manipulation of a Configuration structure

  /** Get a named attribute of the variable; e.g. xxx.yyy */
  @Override
  public VariableReference getAttribute(String attributeName)
    throws ScriptException
  {
    if (attributeName.equals(ATTRIBUTE_STRING))
      return new VariableString(getStringValue());
    else if (attributeName.equals(ATTRIBUTE_INT))
      return new VariableInt(getIntValue());
    else if (attributeName.equals(ATTRIBUTE_FLOAT))
      return new VariableFloat(getDoubleValue());
    else if (attributeName.equals(ATTRIBUTE_BOOLEAN))
      return new VariableBoolean(getBooleanValue());
    else if (attributeName.equals(ATTRIBUTE_SCRIPT))
      return new VariableString(getScriptValue());
    else
      throw new ScriptException(composeMessage("Variable has no attribute called '"+attributeName+"'"));
  }
  
  /** Insert an object into this variable at a position. */
  @Override
  public void insertAt(Variable v, Variable index)
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Variable does not support 'insert' operation"));
  }

  /** Delete an object from this variable at a position. */
  @Override
  public void removeAt(Variable index)
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Variable does not support 'remove' operation"));
  }
    

  // The following two operations correspond to <xxx> and xxx[index]
  
  /** Get an indexed property of the variable */
  @Override
  public VariableReference getIndexed(Variable index)
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Variable does not support subscripts"));
  }

  // As a variable reference, refer to self
  
  /** Set the reference */
  @Override
  public void setReference(Variable object)
    throws ScriptException
  {
    throw new ScriptException(composeMessage("Cannot set reference"));
  }
  
  /** Resolve the reference */
  @Override
  public Variable resolve()
    throws ScriptException
  {
    return this;
  }

  /** Check if this reference is null */
  @Override
  public boolean isNull()
  {
    return false;
  }

  // Protected methods
  
  /** Compose a message which includes the current class name, so we can see what type of variable it is. */
  protected String composeMessage(String input)
  {
    return "Variable of type '"+getClass().getName()+"': "+input;
  }
}
