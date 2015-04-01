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

/** This interface represents a variable within the ManifoldCF script engine.
* A variable may have a value, and may have various named properties, as described below.
* 
*/
public interface Variable
{
  // Special attributes
  
  /** Conversion to string */
  public static String ATTRIBUTE_STRING = "__string__";
  /** Conversion to int */
  public static String ATTRIBUTE_INT = "__int__";
  /** Conversion to float */
  public static String ATTRIBUTE_FLOAT = "__float__";
  /** Conversion to boolean */
  public static String ATTRIBUTE_BOOLEAN = "__boolean__";
  /** Script value */
  public static String ATTRIBUTE_SCRIPT = "__script__";
  /** Name attribute */
  public static String ATTRIBUTE_TYPE = "__type__";
  /** Value attribute */
  public static String ATTRIBUTE_VALUE = "__value__";
  /** Size attribute */
  public static String ATTRIBUTE_SIZE = "__size__";
  /** Dict attribute */
  public static String ATTRIBUTE_DICT = "__dict__";
  /** OK status attribute */
  public static String ATTRIBUTE_OKSTATUS = "__OK__";
  /** NOTFOUND status attribute */
  public static String ATTRIBUTE_NOTFOUNDSTATUS = "__NOTFOUND__";
  /** CREATED status attribute */
  public static String ATTRIBUTE_CREATEDSTATUS = "__CREATED__";
  /** UNAUTHORIZED status attribute */
  public static String ATTRIBUTE_UNAUTHORIZEDSTATUS = "__UNAUTHORIZED__";
  
  /** Check if the variable has a string value */
  public boolean hasStringValue()
    throws ScriptException;
    
  /** Get the variable's value as a string */
  public String getStringValue()
    throws ScriptException;

  /** Check if the variable has a script value */
  public boolean hasScriptValue()
    throws ScriptException;

  /** Get the variable's script value */
  public String getScriptValue()
    throws ScriptException;

  /** Check if the variable has a Configuration value */
  public boolean hasConfigurationValue()
    throws ScriptException;

  /** Get the variable's value as a Configuration object */
  public Configuration getConfigurationValue()
    throws ScriptException;
  
  /** Check if the variable has a ConfigurationNode value */
  public boolean hasConfigurationNodeValue()
    throws ScriptException;

  /** Get the variable's value as a ConfigurationNode object */
  public ConfigurationNode getConfigurationNodeValue()
    throws ScriptException;

  /** Check if the variable has a boolean value */
  public boolean hasBooleanValue()
    throws ScriptException;

  /** Get the variable's value as a boolean */
  public boolean getBooleanValue()
    throws ScriptException;
  
  /** Check if the variable has an int value */
  public boolean hasIntValue()
    throws ScriptException;

  /** Get the variable's value as an integer */
  public int getIntValue()
    throws ScriptException;
  
  /** Check if the variable has a double value */
  public boolean hasDoubleValue()
    throws ScriptException;

  /** Get the variable's value as a double */
  public double getDoubleValue()
    throws ScriptException;
    
  /** Check if the variable has a query argument value */
  public boolean hasQueryArgumentValue()
    throws ScriptException;

  /** Get the variable's value as a properly-encoded query argument */
  public String getQueryArgumentValue()
    throws ScriptException;
  
  /** Check if the variable has a URL path value */
  public boolean hasURLPathValue()
    throws ScriptException;
    
  /** Get the variable's value as a URL path component */
  public String getURLPathValue()
    throws ScriptException;
    
  // Arithmetic and comparison operators
  
  public VariableReference plus(Variable v)
    throws ScriptException;
    
  public VariableReference minus(Variable v)
    throws ScriptException;
    
  public VariableReference asterisk(Variable v)
    throws ScriptException;
    
  public VariableReference slash(Variable v)
    throws ScriptException;
    
  public VariableReference unaryMinus()
    throws ScriptException;
  
  public VariableReference greaterAngle(Variable v)
    throws ScriptException;
    
  public VariableReference lesserAngle(Variable v)
    throws ScriptException;
    
  public VariableReference doubleEquals(Variable v)
    throws ScriptException;
    
  public VariableReference greaterAngleEquals(Variable v)
    throws ScriptException;
    
  public VariableReference lesserAngleEquals(Variable v)
    throws ScriptException;
  
  public VariableReference exclamationEquals(Variable v)
    throws ScriptException;
  
  public VariableReference ampersand(Variable v)
    throws ScriptException;
    
  public VariableReference pipe(Variable v)
    throws ScriptException;

  public VariableReference doubleAmpersand(Variable v)
    throws ScriptException;
    
  public VariableReference doublePipe(Variable v)
    throws ScriptException;

  public VariableReference unaryExclamation()
    throws ScriptException;
    
  // The following operations allow manipulation of a Configuration structure

  /** Get a named attribute of the variable; e.g. xxx.yyy */
  public VariableReference getAttribute(String attributeName)
    throws ScriptException;
  
  /** Insert an object into this variable at a position.  Use null to insert at end. */
  public void insertAt(Variable v, Variable index)
    throws ScriptException;
    
  /** Delete an object from this variable at a position. */
  public void removeAt(Variable index)
    throws ScriptException;
    
  // The following operations correspond to xxx[index]
  
  /** Get an indexed property of the variable */
  public VariableReference getIndexed(Variable index)
    throws ScriptException;
  
}
