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
import java.util.*;

/** Variable wrapper for ConfigurationNode object.
*/
public class VariableConfigurationNode extends VariableBase
{
  protected ConfigurationNode configurationNode;
  
  public VariableConfigurationNode(String name)
  {
    configurationNode = new ConfigurationNode(name);
  }
  
  public VariableConfigurationNode(ConfigurationNode node)
  {
    configurationNode = node;
  }
  
  /** Get the variable's value as a string */
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("< ");
    sb.append(new VariableString(configurationNode.getType()).toString());
    sb.append(" : ");
    String valueField = configurationNode.getValue();
    if (valueField == null)
      valueField = "";
    sb.append(new VariableString(valueField).toString());
    sb.append(" : ");
    boolean needComma = false;
    Iterator<String> iter = configurationNode.getAttributes();
    while (iter.hasNext())
    {
      String attrName = iter.next();
      String value = configurationNode.getAttributeValue(attrName);
      if (needComma)
        sb.append(", ");
      else
        needComma = true;
      sb.append(new VariableString(attrName).toString());
      sb.append("=");
      sb.append(new VariableString(value).toString());
    }
    sb.append(" : ");
    int i = 0;
    while (i < configurationNode.getChildCount())
    {
      ConfigurationNode child = configurationNode.findChild(i++);
      if (i > 0)
        sb.append(", ");
      sb.append(new VariableConfigurationNode(child).toString());
    }
    sb.append(" >");
    return sb.toString();
  }

  /** Convert to a value */
  public String getStringValue()
    throws ScriptException
  {
    if (configurationNode.getValue() == null)
      return "";
    return configurationNode.getValue();
  }
  
  /** Get the variable's value as a ConfigurationNode object */
  public ConfigurationNode getConfigurationNodeValue()
    throws ScriptException
  {
    return configurationNode;
  }

  /** Get a named attribute of the variable; e.g. xxx.yyy */
  public VariableReference getAttribute(String attributeName)
    throws ScriptException
  {
    // We recognize the __size__ attribute
    if (attributeName.equals(ATTRIBUTE_SIZE))
      return new VariableInt(configurationNode.getChildCount());
    // Also, the __name__ attribute
    if (attributeName.equals(ATTRIBUTE_NAME))
      return new VariableString(configurationNode.getType());
    // And the __value__ attribute
    if (attributeName.equals(ATTRIBUTE_VALUE))
      return new ValueReference();
    // All others are presumed to be attributes of the configuration node, which can be set or cleared.
    return new AttributeReference(attributeName);
  }
  
  /** Get an indexed property of the variable */
  public VariableReference getIndexed(int index)
    throws ScriptException
  {
    if (index < configurationNode.getChildCount())
      return new NodeReference(index);
    return super.getIndexed(index);
  }

  /** Insert an object into this variable at a position. */
  public void insertAt(Variable v, int index)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("Can't insert a null object");
    if (index > configurationNode.getChildCount())
      throw new ScriptException("Insert out of bounds");
    ConfigurationNode insertObject = v.getConfigurationNodeValue();
    configurationNode.addChild(index,insertObject);
  }

  /** Insert an object into this variable at end. */
  public void insert(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("Can't insert a null object");
    configurationNode.addChild(configurationNode.getChildCount(),v.getConfigurationNodeValue());
  }

  public VariableReference plus(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException("Can't add a null object");
    ConfigurationNode node = v.getConfigurationNodeValue();
    ConfigurationNode cn = new ConfigurationNode(configurationNode.getType());
    cn.setValue(configurationNode.getValue());
    Iterator<String> attIter = configurationNode.getAttributes();
    while (attIter.hasNext())
    {
      String attrName = attIter.next();
      String attrValue = configurationNode.getAttributeValue(attrName);
      cn.setAttribute(attrName,attrValue);
    }
    int i = 0;
    while (i < configurationNode.getChildCount())
    {
      ConfigurationNode child = configurationNode.findChild(i++);
      cn.addChild(cn.getChildCount(),child);
    }
    cn.addChild(cn.getChildCount(),node);
    return new VariableConfigurationNode(cn);
  }

  /** Delete an object from this variable at a position. */
  public void removeAt(int index)
    throws ScriptException
  {
    if (index >= configurationNode.getChildCount())
      throw new ScriptException("Remove out of bounds");
    configurationNode.removeChild(index);
  }

  /** Implement VariableReference to allow values to be set or cleared */
  protected class ValueReference implements VariableReference
  {
    public ValueReference()
    {
    }
    
    public void setReference(Variable v)
      throws ScriptException
    {
      if (v == null)
        configurationNode.setValue(null);
      else
      {
        String value = v.getStringValue();
        configurationNode.setValue(value);
      }
    }
    
    public Variable resolve()
      throws ScriptException
    {
      String value = configurationNode.getValue();
      if (value == null)
        value = "";
      return new VariableString(value);
    }
    
    public boolean isNull()
    {
      return false;
    }
  }
  
  /** Implement VariableReference to allow attributes to be set or cleared */
  protected class AttributeReference implements VariableReference
  {
    protected String attributeName;
    
    public AttributeReference(String attributeName)
    {
      this.attributeName = attributeName;
    }
    
    public void setReference(Variable v)
      throws ScriptException
    {
      if (v == null)
        configurationNode.setAttribute(attributeName,null);
      else
      {
        String value = v.getStringValue();
        configurationNode.setAttribute(attributeName,value);
      }
    }

    public Variable resolve()
      throws ScriptException
    {
      String attrValue = configurationNode.getAttributeValue(attributeName);
      if (attrValue == null)
        throw new ScriptException("ConfigurationNode has no attribute named '"+attributeName+"'");
      return new VariableString(attrValue);
    }

    public boolean isNull()
    {
      return (configurationNode.getAttributeValue(attributeName) == null);
    }
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
      if (index >= configurationNode.getChildCount())
        throw new ScriptException("Index out of range for ConfigurationNode children");
      ConfigurationNode confNode = v.getConfigurationNodeValue();
      configurationNode.removeChild(index);
      configurationNode.addChild(index,confNode);
    }

    public Variable resolve()
      throws ScriptException
    {
      if (index >= configurationNode.getChildCount())
        throw new ScriptException("Index out of range for ConfigurationNode children");
      return new VariableConfigurationNode(configurationNode.findChild(index));
    }

    /** Check if this reference is null */
    public boolean isNull()
    {
      return index >= configurationNode.getChildCount();
    }

  }
}
