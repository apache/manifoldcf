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
  
  /** Get a named attribute of the variable; e.g. xxx.yyy */
  public VariableReference getAttribute(String attributeName)
    throws ScriptException
  {
    // We recognize only the __size__ attribute
    if (attributeName.equals(ATTRIBUTE_SIZE))
      return new VariableReference(new VariableInt(configurationNode.getChildCount()));
    // Look for named attribute
    String attributeValue = configurationNode.getAttributeValue(attributeName);
    // MHL to allow attribute to be modified
    if (attributeValue != null)
      return new VariableReference(new VariableString(attributeValue));
    return super.getAttribute(attributeName);
  }
  
  /** Get an indexed property of the variable */
  public VariableReference getIndexed(int index)
    throws ScriptException
  {
    if (index < configurationNode.getChildCount())
      return new NodeReference(index, new VariableConfigurationNode(configurationNode.findChild(index)));
    return super.getIndexed(index);
  }
  
  public ConfigurationNode getConfigurationNode()
  {
    return configurationNode;
  }
  
  /** Extend VariableReference class so we capture attempts to set the reference, and actually overwrite the child when that is done */
  protected class NodeReference extends VariableReference
  {
    protected int index;
    
    public NodeReference(int index, VariableConfigurationNode node)
      throws ScriptException
    {
      super(node);
      this.index = index;
    }
    
    public void setReference(Variable v)
      throws ScriptException
    {
      if (!(v instanceof VariableConfigurationNode))
        throw new ScriptException("Cannot set child value to anything other than a ConfigurationNode object");
      super.setReference(v);
      configurationNode.removeChild(index);
      configurationNode.addChild(index,((VariableConfigurationNode)v).getConfigurationNode());
    }
    
  }
}
