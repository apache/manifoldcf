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
    sb.append("<< ");
    sb.append(new VariableString(configurationNode.getType()).getScriptValue());
    sb.append(" : ");
    String valueField = configurationNode.getValue();
    if (valueField == null)
      valueField = "";
    sb.append(new VariableString(valueField).getScriptValue());
    sb.append(" : ");
    boolean needComma = false;
    Iterator<String> iter = configurationNode.getAttributes();
    String[] attrs = new String[configurationNode.getAttributeCount()];
    int i = 0;
    while (iter.hasNext())
    {
      String attrName = iter.next();
      attrs[i++] = attrName;
    }
    java.util.Arrays.sort(attrs);
    i = 0;
    while (i < attrs.length)
    {
      String attrName = attrs[i++];
      String value = configurationNode.getAttributeValue(attrName);
      if (needComma)
        sb.append(", ");
      else
        needComma = true;
      sb.append(new VariableString(attrName).getScriptValue());
      sb.append("=");
      sb.append(new VariableString(value).getScriptValue());
    }
    sb.append(" : ");
    i = 0;
    while (i < configurationNode.getChildCount())
    {
      ConfigurationNode child = configurationNode.findChild(i);
      if (i > 0)
        sb.append(", ");
      sb.append(new VariableConfigurationNode(child).getScriptValue());
      i++;
    }
    sb.append(" >>");
    return sb.toString();
  }

  /** Check if the variable has a string value */
  @Override
  public boolean hasStringValue()
    throws ScriptException
  {
    return true;
  }

  /** Convert to a value */
  @Override
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
  @Override
  public VariableReference getAttribute(String attributeName)
    throws ScriptException
  {
    // We recognize the __size__ attribute
    if (attributeName.equals(ATTRIBUTE_SIZE))
      return new VariableInt(configurationNode.getChildCount());
    // Also, the __type__ attribute
    if (attributeName.equals(ATTRIBUTE_TYPE))
      return new VariableString(configurationNode.getType());
    // And the __value__ attribute
    if (attributeName.equals(ATTRIBUTE_VALUE))
      return new ValueReference();
    if (attributeName.equals(ATTRIBUTE_DICT))
    {
      VariableDict dict = new VariableDict();
      int i = 0;
      while (i < configurationNode.getChildCount())
      {
        ConfigurationNode child = configurationNode.findChild(i++);
        String type = child.getType();
        dict.getIndexed(new VariableString(type)).setReference(new VariableConfigurationNode(child));
      }
      return dict;
    }
    if (attributeName.equals(ATTRIBUTE_SCRIPT) ||
      attributeName.equals(ATTRIBUTE_STRING) ||
      attributeName.equals(ATTRIBUTE_INT) ||
      attributeName.equals(ATTRIBUTE_FLOAT) || 
      attributeName.equals(ATTRIBUTE_BOOLEAN))
      return super.getAttribute(attributeName);
    // All others are presumed to be attributes of the configuration node, which can be set or cleared.
    return new AttributeReference(attributeName);
  }
  
  /** Get an indexed property of the variable */
  @Override
  public VariableReference getIndexed(Variable index)
    throws ScriptException
  {
    if (index == null)
      throw new ScriptException(composeMessage("Subscript cannot be null"));
    int indexValue = index.getIntValue();
    if (indexValue >= 0 && indexValue < configurationNode.getChildCount())
      return new NodeReference(indexValue);
    throw new ScriptException(composeMessage("Subscript is out of bounds: "+indexValue));
  }

  /** Insert an object into this variable at a position. */
  @Override
  public void insertAt(Variable v, Variable index)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Can't insert a null object"));
    if (index == null)
      configurationNode.addChild(configurationNode.getChildCount(),v.getConfigurationNodeValue());
    else
    {
      int indexValue = index.getIntValue();
      if (indexValue < 0 || indexValue > configurationNode.getChildCount())
        throw new ScriptException(composeMessage("Insert out of bounds: "+indexValue));
      configurationNode.addChild(indexValue,v.getConfigurationNodeValue());
    }
  }

  /** Delete an object from this variable at a position. */
  @Override
  public void removeAt(Variable index)
    throws ScriptException
  {
    if (index == null)
      throw new ScriptException(composeMessage("Remove index cannot be null"));
    int indexValue = index.getIntValue();
    if (indexValue < 0 || indexValue >= configurationNode.getChildCount())
      throw new ScriptException(composeMessage("Remove index out of bounds: "+indexValue));
    configurationNode.removeChild(indexValue);
  }


  @Override
  public VariableReference plus(Variable v)
    throws ScriptException
  {
    if (v == null)
      throw new ScriptException(composeMessage("Can't add a null object"));
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

  /** Implement VariableReference to allow values to be set or cleared */
  protected class ValueReference implements VariableReference
  {
    public ValueReference()
    {
    }
    
    @Override
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
    
    @Override
    public Variable resolve()
      throws ScriptException
    {
      String value = configurationNode.getValue();
      if (value == null)
        value = "";
      return new VariableString(value);
    }
    
    @Override
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
    
    @Override
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

    @Override
    public Variable resolve()
      throws ScriptException
    {
      String attrValue = configurationNode.getAttributeValue(attributeName);
      if (attrValue == null)
        throw new ScriptException(composeMessage("No attribute named '"+attributeName+"'"));
      return new VariableString(attrValue);
    }

    @Override
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
    
    @Override
    public void setReference(Variable v)
      throws ScriptException
    {
      if (index < 0 || index >= configurationNode.getChildCount())
        throw new ScriptException(composeMessage("Index out of range: "+index));
      ConfigurationNode confNode = v.getConfigurationNodeValue();
      configurationNode.removeChild(index);
      configurationNode.addChild(index,confNode);
    }

    @Override
    public Variable resolve()
      throws ScriptException
    {
      if (index < 0 || index >= configurationNode.getChildCount())
        throw new ScriptException(composeMessage("Index out of range: "+index));
      return new VariableConfigurationNode(configurationNode.findChild(index));
    }

    /** Check if this reference is null */
    @Override
    public boolean isNull()
    {
      return index < 0 || index >= configurationNode.getChildCount();
    }

  }
}
