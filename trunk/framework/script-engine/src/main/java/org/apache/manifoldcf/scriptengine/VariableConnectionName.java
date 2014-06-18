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

import org.apache.manifoldcf.core.util.URLEncoder;

/** Variable class representing a ManifoldCF API URL connection name segment.  In conjunction
* with the URL variable, this variable will properly character-stuff the connection name to make
* a valid URL.
*/
public class VariableConnectionName extends VariableBase
{
  protected String encodedConnectionName;
  protected String connectionName;
  
  public VariableConnectionName(String connectionName)
  {
    this.connectionName = connectionName;
    this.encodedConnectionName = encode(connectionName);
  }

  @Override
  public int hashCode()
  {
    return connectionName.hashCode();
  }
  
  @Override
  public boolean equals(Object o)
  {
    if (!(o instanceof VariableConnectionName))
      return false;
    return ((VariableConnectionName)o).connectionName.equals(connectionName);
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
    StringBuilder sb = new StringBuilder();
    sb.append("(new connectionname \"");
    int i = 0;
    while (i < connectionName.length())
    {
      char x = connectionName.charAt(i++);
      if (x == '\\' || x == '\"')
        sb.append('\\');
      sb.append(x);
    }
    sb.append("\")");
    return sb.toString();
  }
  
  /** Check if the variable has a URL path value */
  @Override
  public boolean hasURLPathValue()
    throws ScriptException
  {
    return true;
  }

  /** Get the variable's value as a URL path component */
  @Override
  public String getURLPathValue()
    throws ScriptException
  {
      return URLEncoder.encode(encodedConnectionName).replace("+","%20");
  }

  /** Check if the variable has a string value */
  @Override
  public boolean hasStringValue()
    throws ScriptException
  {
    return true;
  }

  /** Get the variable's value as a string */
  @Override
  public String getStringValue()
    throws ScriptException
  {
    return encodedConnectionName;
  }

  @Override
  public VariableReference doubleEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '==' operand cannot be null"));
    return new VariableBoolean(encodedConnectionName.equals(v.getStringValue()));
  }

  @Override
  public VariableReference exclamationEquals(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '!=' operand cannot be null"));
    return new VariableBoolean(!encodedConnectionName.equals(v.getStringValue()));
  }

  protected static String encode(String connectionName)
  {
    StringBuilder sb = new StringBuilder();
    int i = 0;
    while (i < connectionName.length())
    {
      char x = connectionName.charAt(i++);
      if (x == '/')
        sb.append('.').append('+');
      else if (x == '.')
        sb.append('.').append('.');
      else
        sb.append(x);
    }
    return sb.toString();
  }
  
}
