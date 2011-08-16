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

/** Base class for variables.
* Basically, everything is illegal until overridden.
*/
public class VariableBase implements Variable
{
  public VariableBase()
  {
  }
  
  /** Get the variable's value as a string */
  public String getStringValue()
    throws ScriptException
  {
    throw new ScriptException("Cannot convert variable to string");
  }
  
  /** Get the variable's value as a boolean */
  public boolean getBooleanValue()
    throws ScriptException
  {
    throw new ScriptException("Cannot convert variable to boolean");
  }
  
  /** Get the variable's value as an integer */
  public int getIntValue()
    throws ScriptException
  {
    throw new ScriptException("Cannot convert variable to int");
  }
  
  /** Get the variable's value as a double */
  public double getDoubleValue()
    throws ScriptException
  {
    throw new ScriptException("Cannot convert variable to float");
  }
  
  // The following operations allow manipulation of a Configuration structure

  /** Get a named attribute of the variable; e.g. xxx.yyy */
  public VariableReference getAttribute(String attributeName)
    throws ScriptException
  {
    throw new ScriptException("Variable has no attribute called '"+attributeName+"'");
  }
  
  // The following two operations correspond to <xxx> and xxx[index]
  
  /** Get an indexed property of the variable */
  public VariableReference getIndexed(int index)
    throws ScriptException
  {
    throw new ScriptException("Variable has no member number "+Integer.toString(index));
  }

}
