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

/** This interface represents a variable within the ManifoldCF script engine.
* A variable may have a value, and may have various named properties, as described below.
* 
*/
public interface Variable
{
  /** Get the variable's value as a string */
  public String getStringValue()
    throws ScriptException;
  
  /** Get the variable's value as a boolean */
  public boolean getBooleanValue()
    throws ScriptException;
  
  /** Get the variable's value as an integer */
  public int getIntValue()
    throws ScriptException;
  
  /** Get the variable's value as a double */
  public double getDoubleValue()
    throws ScriptException;
  
  /** Check whether this variable constitutes an "OK" status.
  */
  public boolean isOKStatus()
    throws ScriptException;

  /** Check whether this variable constitutes a "NOT FOUND" status.
  */
  public boolean isNOTFOUNDStatus()
    throws ScriptException;

  /** Check whether this variable constitutes a "CREATED" status.
  */
  public boolean isCREATEDStatus()
    throws ScriptException;

  // The following operation corresponds to the "." operation
  
  /** Get a named property of the variable */
  public VariableReference getProperty(String propertyName)
    throws ScriptException;
  
  // The following two operations correspond to xxx[index]
  
  /** Get an indexed property of the variable */
  public VariableReference getIndexed(int index)
    throws ScriptException;
    
}
