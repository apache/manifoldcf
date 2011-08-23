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

/** Variable wrapper for Configuration object.
*/
public class VariableConfiguration extends VariableBase
{
  protected Configuration configuration;
  
  public VariableConfiguration(String type)
  {
    configuration = new Configuration(type);
  }
  
  public VariableConfiguration(String type, String json)
    throws ScriptException
  {
    configuration = new Configuration(type);
    try
    {
      configuration.fromJSON(json);
    }
    catch (ManifoldCFException e)
    {
      throw new ScriptException(e.getMessage(),e);
    }
  }
  
  /** Get a string from this */
  public String getStringValue()
    throws ScriptException
  {
    return configuration.toString();
  }
  
  /** Get the variable's value as a JSON string */
  public String getJSONValue()
    throws ScriptException
  {
    try
    {
      return configuration.toJSON();
    }
    catch (ManifoldCFException e)
    {
      throw new ScriptException(e.getMessage(),e);
    }
  }

  /** Get a named attribute of the variable; e.g. xxx.yyy */
  public VariableReference getAttribute(String attributeName)
    throws ScriptException
  {
    // We recognize only the __size__ attribute
    if (attributeName.equals(ATTRIBUTE_SIZE))
      return new VariableInt(configuration.getChildCount());
    return super.getAttribute(attributeName);
  }
  
  /** Get an indexed property of the variable */
  public VariableReference getIndexed(int index)
    throws ScriptException
  {
    if (index < configuration.getChildCount())
      return new NodeReference(index);
    return super.getIndexed(index);
  }
  
  /** Extend VariableReference class so we capture attempts to set the reference, and actually overwrite the child when that is done */
  protected class NodeReference implements VariableReference
  {
    protected int index;
    
    public NodeReference(int index)
    {
      this.index = index;
    }
    
    public void setReference(Variable v)
      throws ScriptException
    {
      if (!(v instanceof VariableConfigurationNode))
        throw new ScriptException("Cannot set Configuration child value to anything other than a ConfigurationNode object");
      if (index >= configuration.getChildCount())
        throw new ScriptException("Index out of range for Configuration children");
      configuration.removeChild(index);
      configuration.addChild(index,((VariableConfigurationNode)v).getConfigurationNode());
    }
    
    public Variable resolve()
      throws ScriptException
    {
      if (index >= configuration.getChildCount())
        throw new ScriptException("Index out of range for Configuration children");
      return new VariableConfigurationNode(configuration.findChild(index));
    }
    
    /** Check if this reference is null */
    public boolean isNull()
    {
      return index >= configuration.getChildCount();
    }

  }
}
