/* $Id: ConfigurationNode.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.core.interfaces;

import java.io.*;
import java.util.*;

/** This class represents a node in a configuration structure.
*/
public class ConfigurationNode implements IHierarchyParent
{
  public static final String _rcsid = "@(#)$Id: ConfigurationNode.java 988245 2010-08-23 18:39:35Z kwright $";

  // Member variables
  protected List<ConfigurationNode> children = null;
  protected Map<String,String> attributes = null;
  protected String type = null;
  protected String value = null;

  // Readonly flag
  protected boolean readOnly = false;

  /** Constructor.
  */
  public ConfigurationNode(String type)
  {
    this.type = type;
  }

  /** Duplication constructor.
  */
  public ConfigurationNode(ConfigurationNode source)
  {
    this.type = source.type;
    this.value = source.value;
    this.readOnly = source.readOnly;
    if (source.attributes != null)
    {
      Iterator<String> iter = source.attributes.keySet().iterator();
      while (iter.hasNext())
      {
        String attribute = iter.next();
        String attrValue = source.attributes.get(attribute);
        if (this.attributes == null)
          this.attributes = new HashMap<String,String>();
        this.attributes.put(attribute,attrValue);
      }
    }
    int i = 0;
    while (i < source.getChildCount())
    {
      ConfigurationNode child = source.findChild(i++);
      this.addChild(this.getChildCount(),createNewNode(child));
    }
  }
  
  /** Make a new blank node identical in type and class to the current node.
  *@return the new node.
  */
  protected ConfigurationNode createNewNode()
  {
    return new ConfigurationNode(type);
  }
  
  /** Make a new node that is a copy of the specified node.
  */
  protected ConfigurationNode createNewNode(ConfigurationNode source)
  {
    return new ConfigurationNode(source);
  }
  
  /** Make this node (and its children) read-only
  */
  public void makeReadOnly()
  {
    if (readOnly)
      return;
    if (children != null)
    {
      int i = 0;
      while (i < children.size())
      {
        ConfigurationNode child = (ConfigurationNode)children.get(i++);
        child.makeReadOnly();
      }
    }
    readOnly = true;
  }

  /** Create a duplicate of the current node.
  *@return the duplicate
  */
  protected ConfigurationNode createDuplicate(boolean readOnly)
  {
    if (readOnly && this.readOnly)
      return this;
    ConfigurationNode rval = createNewNode();
    rval.value = value;
    if (attributes != null)
      rval.attributes = cloneAttributes(attributes);
    if (children != null)
    {
      rval.children = new ArrayList<ConfigurationNode>();
      int i = 0;
      while (i < children.size())
      {
        ConfigurationNode node = children.get(i++);
        rval.children.add(node.createDuplicate(readOnly));
      }
    }
    rval.readOnly = readOnly;
    return rval;
  }

  /** Get type.
  *@return the node type.
  */
  public String getType()
  {
    return type;
  }

  /** Set value.
  *@param value is the value to set.
  */
  public void setValue(String value)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    this.value = value;
  }

  /** Get value.
  *@return the value.
  */
  public String getValue()
  {
    return value;
  }

  /** Get child count.
  *@return the count.
  */
  public int getChildCount()
  {
    if (children == null)
      return 0;
    return children.size();
  }

  /** Get child n.
  *@param index is the child number.
  *@return the child node.
  */
  public ConfigurationNode findChild(int index)
  {
    return children.get(index);
  }

  /** Remove child n.
  *@param index is the child to remove.
  */
  public void removeChild(int index)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    if (children != null)
    {
      children.remove(index);
      if (children.size() == 0)
        children = null;
    }
  }

  /** Add child at specified position.
  *@param index is the position to add the child.
  *@param child is the child to add.
  */
  public void addChild(int index, ConfigurationNode child)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");

    if (children == null)
      children = new ArrayList<ConfigurationNode>();
    children.add(index,child);
  }

  /** Clear children.
  */
  public void clearChildren()
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    children.clear();
  }

  /** Set an attribute.
  *@param attribute is the name of the attribute.
  *@param value is the value of the attribute (null to remove it).
  */
  public void setAttribute(String attribute, String value)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    if (value == null)
    {
      if (attributes != null)
      {
        attributes.remove(attribute);
        if (attributes.size() == 0)
          attributes = null;
      }
    }
    else
    {
      if (attributes == null)
        attributes = new HashMap();
      attributes.put(attribute,value);
    }
  }

  /** Get the attribute count.
  *@return the attribute count.
  */
  public int getAttributeCount()
  {
    if (attributes == null)
      return 0;
    else
      return attributes.size();
  }
  
  /** Iterate over attributes.
  *@return the attribute iterator.
  */
  public Iterator<String> getAttributes()
  {
    if (attributes == null)
      return new HashMap<String,String>().keySet().iterator();
    return attributes.keySet().iterator();
  }

  /** Get an attribute value.
  *@param attribute is the name of the attribute.
  *@return the value.
  */
  public String getAttributeValue(String attribute)
  {
    if (attributes == null)
      return null;
    return attributes.get(attribute);
  }

  /** Calculate a hashcode */
  public int hashCode()
  {
    int rval = type.hashCode();
    if (value != null)
      rval += value.hashCode();
    if (attributes != null)
    {
      Iterator<String> iter = attributes.keySet().iterator();
      // Make sure this is not sensitive to order!
      while (iter.hasNext())
      {
        String key = iter.next();
        String attrValue = attributes.get(key);
        rval += key.hashCode() + attrValue.hashCode();
      }
    }
    if (children != null)
    {
      // Do children
      int i = 0;
      while (i < children.size())
      {
        rval += children.get(i++).hashCode();
      }
    }
    return rval;
  }

  /** Check if equals */
  public boolean equals(Object o)
  {
    if (!(o instanceof ConfigurationNode))
      return false;
    ConfigurationNode n = (ConfigurationNode)o;
    if (((attributes==null)?0:attributes.size()) != ((n.attributes==null)?0:n.attributes.size()))
      return false;
    if (((children==null)?0:children.size()) != ((n.children==null)?0:n.children.size()))
      return false;
    if (!type.equals(n.type))
      return false;
    if (value == null || n.value == null)
    {
      if (value != n.value)
        return false;
    }
    else
    {
      if (!value.equals(n.value))
        return false;
    }
    if (attributes != null && n.attributes != null)
    {
      Iterator<String> iter = attributes.keySet().iterator();
      while (iter.hasNext())
      {
        String key = iter.next();
        String attrValue = attributes.get(key);
        String nAttrValue = n.attributes.get(key);
        if (nAttrValue == null || !attrValue.equals(nAttrValue))
          return false;
      }
    }
    if (children != null && n.children != null)
    {
      int i = 0;
      while (i < children.size())
      {
        ConfigurationNode child = children.get(i);
        ConfigurationNode nChild = n.children.get(i);
        if (!child.equals(nChild))
          return false;
        i++;
      }
    }
    return true;
  }
  
  /** Construct a human-readable string */
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    sb.append(type);
    if (value != null)
      sb.append(":").append(value);
    if (attributes != null)
    {
      Iterator<String> iter = attributes.keySet().iterator();
      while (iter.hasNext())
      {
        sb.append(" ");
        String key = iter.next();
        String attrValue = attributes.get(key);
        sb.append(key).append("='").append(attrValue).append("'");
      }
    }
    sb.append(" [");
    if (children != null)
    {
      int i = 0;
      while (i < children.size())
      {
        if (i > 0)
          sb.append(", ");
        ConfigurationNode cn = children.get(i++);
        sb.append(cn.toString());
      }
    }
    sb.append("])");
    return sb.toString();
  }

  protected static Map<String,String> cloneAttributes(Map<String,String> attributes)
  {
    Map<String,String> rval = new HashMap<String,String>();
    Iterator<Map.Entry<String,String>> iter = attributes.entrySet().iterator();
    while (iter.hasNext())
    {
      Map.Entry<String,String> entry = iter.next();
      rval.put(entry.getKey(),entry.getValue());
    }
    return rval;
  }
}
