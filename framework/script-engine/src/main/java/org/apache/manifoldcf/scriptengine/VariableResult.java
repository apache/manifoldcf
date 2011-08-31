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

/** Variable class representing the result of an http operation.
* This consists of two parts: a result code, and a VariableConfiguration object.
*/
public class VariableResult extends VariableBase
{
  protected int resultCode;
  protected VariableConfiguration result;
  
  public VariableResult(int resultCode, String json)
    throws ScriptException
  {
    this.resultCode = resultCode;
    this.result = new VariableConfiguration(json);
  }
  
  /** Get the variable's script value */
  public String getScriptValue()
    throws ScriptException
  {
    return "("+Integer.toString(resultCode)+") "+result.toString();
  }

  /** Get the variable's value as an integer */
  public int getIntValue()
    throws ScriptException
  {
    return resultCode;
  }
  
  /** Get a named attribute of the variable; e.g. xxx.yyy */
  public VariableReference getAttribute(String attributeName)
    throws ScriptException
  {
    if (attributeName.equals(ATTRIBUTE_OKSTATUS))
      return new VariableBoolean(resultCode == 200);
    else if (attributeName.equals(ATTRIBUTE_CREATEDSTATUS))
      return new VariableBoolean(resultCode == 201);
    else if (attributeName.equals(ATTRIBUTE_NOTFOUNDSTATUS))
      return new VariableBoolean(resultCode == 404);
    else if (attributeName.equals(ATTRIBUTE_VALUE))
      return result;
    else
      return super.getAttribute(attributeName);
  }

}
