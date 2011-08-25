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
  
  public VariableConfiguration()
  {
    configuration = new Configuration();
  }
  
  public VariableConfiguration(String json)
    throws ScriptException
  {
    configuration = new Configuration();
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
  public String toString()
  {
    return configuration.toString();
  }
  
  /** Get the variable's value as a Configuration object */
  public Configuration getConfigurationValue()
    throws ScriptException
  {
    return configuration;
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
  
  /** Insert an object into this variable at a position. */
  public void insertAt(Variable v, int index)
    throws ScriptException
  {
    if (index > configuration.getChildCount())
      throw new ScriptException("Insert out of bounds");
    if (v == null)
      throw new ScriptException("Can't insert a null object");
    configuration.addChild(index,v.getConfigurationNodeValue());
  }

  /** Insert an object into this variable at end. */
  public void insert(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("Can't insert a null object");
    configuration.addChild(configuration.getChildCount(),v.getConfigurationNodeValue());
  }

  public VariableReference plus(Variable v)
    throws ScriptException
  {
    insert(v);
    return this;
  }

  /** Delete an object from this variable at a position. */
  public void removeAt(int index)
    throws ScriptException
  {
    if (index >= configuration.getChildCount())
      throw new ScriptException("Remove out of bounds");
    configuration.removeChild(index);
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
      if (index >= configuration.getChildCount())
        throw new ScriptException("Index out of range for Configuration children");
      ConfigurationNode confNode = v.getConfigurationNodeValue();
      configuration.removeChild(index);
      configuration.addChild(index,confNode);
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
