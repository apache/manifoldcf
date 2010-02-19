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
package org.apache.lcf.core.interfaces;

import java.io.*;
import java.util.*;

/** This class represents a node in a specification structure.
*/
public class SpecificationNode
{
  public static final String _rcsid = "@(#)$Id$";

  // Member variables
  protected ArrayList children = null;
  protected HashMap attributes = null;
  protected String type = null;
  protected String value = null;

  // Readonly flag
  protected boolean readOnly = false;

  /** Constructor.
  */
  public SpecificationNode(String type)
  {
    this.type = type;
  }

  /** Make this specification node (and its children) read-only
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
        SpecificationNode child = (SpecificationNode)children.get(i++);
        child.makeReadOnly();
      }
    }
    readOnly = true;
  }

  /** Duplicate.
  *@return the duplicate.
  */
  public SpecificationNode duplicate(boolean readOnly)
  {
    if (readOnly && this.readOnly)
      return this;
    SpecificationNode rval = new SpecificationNode(type);
    rval.value = value;
    if (attributes != null)
      rval.attributes = (HashMap)attributes.clone();
    if (children != null)
    {
      rval.children = new ArrayList();
      int i = 0;
      while (i < children.size())
      {
        SpecificationNode node = (SpecificationNode)children.get(i++);
        rval.children.add(node.duplicate(readOnly));
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
  public SpecificationNode getChild(int index)
  {
    return (SpecificationNode)children.get(index);
  }

  /** Remove child n.
  *@param index is the child to remove.
  */
  public void removeChild(int index)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    children.remove(index);
  }

  /** Add child at specified position.
  *@param index is the position to add the child.
  *@param child is the child to add.
  */
  public void addChild(int index, SpecificationNode child)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    if (children == null)
      children = new ArrayList();
    children.add(index,child);
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
        attributes.remove(attribute);
    }
    else
    {
      if (attributes == null)
        attributes = new HashMap();
      attributes.put(attribute,value);
    }
  }

  /** Iterate over attributes.
  *@return the attribute iterator.
  */
  public Iterator getAttributes()
  {
    if (attributes == null)
      return new HashMap().keySet().iterator();
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
    return (String)attributes.get(attribute);
  }

}
