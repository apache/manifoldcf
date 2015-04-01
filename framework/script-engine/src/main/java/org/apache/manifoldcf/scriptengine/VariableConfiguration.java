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

  public VariableConfiguration(Configuration c)
  {
    configuration = c;
  }
  
  public VariableConfiguration(String json)
    throws ScriptException
  {
    configuration = new Configuration();
    if (json != null && json.length() > 0)
    {
      try
      {
        configuration.fromJSON(json);
      }
      catch (ManifoldCFException e)
      {
        throw new ScriptException("Invalid json: '"+json+"': "+e.getMessage(),e);
      }
    }
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
    sb.append("{ ");
    int i = 0;
    while (i < configuration.getChildCount())
    {
      if (i > 0)
        sb.append(", ");
      ConfigurationNode child = configuration.findChild(i++);
      sb.append(new VariableConfigurationNode(child).getScriptValue());
    }
    sb.append(" }");
    return sb.toString();
  }
  
  /** Check if the variable has a Configuration value */
  @Override
  public boolean hasConfigurationValue()
    throws ScriptException
  {
    return true;
  }

  /** Get the variable's value as a Configuration object */
  @Override
  public Configuration getConfigurationValue()
    throws ScriptException
  {
    return configuration;
  }
  
  /** Get a named attribute of the variable; e.g. xxx.yyy */
  @Override
  public VariableReference getAttribute(String attributeName)
    throws ScriptException
  {
    // We recognize only the __size__ attribute
    if (attributeName.equals(ATTRIBUTE_SIZE))
      return new VariableInt(configuration.getChildCount());
    if (attributeName.equals(ATTRIBUTE_DICT))
    {
      VariableDict dict = new VariableDict();
      int i = 0;
      while (i < configuration.getChildCount())
      {
        ConfigurationNode child = configuration.findChild(i++);
        String type = child.getType();
        dict.getIndexed(new VariableString(type)).setReference(new VariableConfigurationNode(child));
      }
      return dict;
    }
    return super.getAttribute(attributeName);
  }
  
  /** Get an indexed property of the variable */
  @Override
  public VariableReference getIndexed(Variable index)
    throws ScriptException
  {
    if (index == null)
      throw new ScriptException(composeMessage("Subscript cannot be null"));
    int indexValue = index.getIntValue();
    if (indexValue >= 0 && indexValue < configuration.getChildCount())
      return new NodeReference(indexValue);
    throw new ScriptException(composeMessage("Subscript is out of bounds: "+indexValue));
  }
  
  /** Insert an object into this variable at a position. */
  @Override
  public void insertAt(Variable v, Variable index)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Can't insert a null object into a configuration"));
    if (index == null)
      configuration.addChild(configuration.getChildCount(),v.getConfigurationNodeValue());
    else
    {
      int indexValue = index.getIntValue();
      if (indexValue < 0 || indexValue > configuration.getChildCount())
        throw new ScriptException(composeMessage("Configuration insert out of bounds: "+indexValue));
      configuration.addChild(indexValue,v.getConfigurationNodeValue());
    }
  }

  /** Delete an object from this variable at a position. */
  @Override
  public void removeAt(Variable index)
    throws ScriptException
  {
    if (index == null)
      throw new ScriptException(composeMessage("Configuration remove index cannot be null"));
    int indexValue = index.getIntValue();
    if (indexValue < 0 || indexValue >= configuration.getChildCount())
      throw new ScriptException(composeMessage("Configuration remove index out of bounds"));
    configuration.removeChild(indexValue);
  }

  @Override
  public VariableReference plus(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Binary '+' can't add a null object"));
    ConfigurationNode node = v.getConfigurationNodeValue();
    Configuration c = new Configuration();
    int i = 0;
    while (i < configuration.getChildCount())
    {
      ConfigurationNode child = configuration.findChild(i++);
      c.addChild(c.getChildCount(),child);
    }
    c.addChild(c.getChildCount(),node);
    return new VariableConfiguration(c);
  }


  /** Extend VariableReference class so we capture attempts to set the reference, and actually overwrite the child when that is done */
  protected class NodeReference implements VariableReference
  {
    protected int index;
    
    public NodeReference(int index)
    {
      this.index = index;
    }
    
    @Override
    public void setReference(Variable v)
      throws ScriptException
    {
      if (index < 0 || index >= configuration.getChildCount())
        throw new ScriptException(composeMessage("Index out of range for Configuration children: "+index));
      ConfigurationNode confNode = v.getConfigurationNodeValue();
      configuration.removeChild(index);
      configuration.addChild(index,confNode);
    }
    
    @Override
    public Variable resolve()
      throws ScriptException
    {
      if (index < 0 || index >= configuration.getChildCount())
        throw new ScriptException(composeMessage("Index out of range for Configuration children: "+index));
      return new VariableConfigurationNode(configuration.findChild(index));
    }
    
    /** Check if this reference is null */
    @Override
    public boolean isNull()
    {
      return index < 0 || index >= configuration.getChildCount();
    }

  }
}
