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

/** This class represents a node in a configuration structure.
*/
public class ConfigNode
{
	public static final String _rcsid = "@(#)$Id$";

	// Member variables
	protected ArrayList children = new ArrayList();
	protected HashMap attributes = new HashMap();
	protected String type = null;
	protected String value = null;

	/** Constructor.
	*/
	public ConfigNode(String type)
	{
		this.type = type;
	}

	/** Duplicate.
	*@return the duplicate.
	*/
	public ConfigNode duplicate()
	{
		ConfigNode rval = new ConfigNode(type);
		rval.value = value;
		rval.attributes = (HashMap)attributes.clone();
		int i = 0;
		while (i < children.size())
		{
			ConfigNode node = (ConfigNode)children.get(i++);
			rval.children.add(node.duplicate());
		}
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
		return children.size();
	}

	/** Get child n.
	*@param index is the child number.
	*@return the child node.
	*/
	public ConfigNode getChild(int index)
	{
		return (ConfigNode)children.get(index);
	}

	/** Remove child n.
	*@param index is the child to remove.
	*/
	public void removeChild(int index)
	{
		children.remove(index);
	}

	/** Add child at specified position.
	*@param index is the position to add the child.
	*@param child is the child to add.
	*/
	public void addChild(int index, ConfigNode child)
	{
		children.add(index,child);
	}

	/** Set an attribute.
	*@param attribute is the name of the attribute.
	*@param value is the value of the attribute (null to remove it).
	*/
	public void setAttribute(String attribute, String value)
	{
		if (value == null)
			attributes.remove(attribute);
		else
			attributes.put(attribute,value);
	}

	/** Iterate over attributes.
	*@return the attribute iterator.
	*/
	public Iterator getAttributes()
	{
		return attributes.keySet().iterator();
	}

	/** Get an attribute value.
	*@param attribute is the name of the attribute.
	*@return the value.
	*/
	public String getAttributeValue(String attribute)
	{
		return (String)attributes.get(attribute);
	}

	/** Calculate a hashcode */
	public int hashCode()
	{
		int rval = type.hashCode();
		if (value != null)
			rval += value.hashCode();
		Iterator iter = attributes.keySet().iterator();
		// Make sure this is not sensitive to order!
		while (iter.hasNext())
		{
			String key = (String)iter.next();
			String attrValue = (String)attributes.get(key);
			rval += key.hashCode() + attrValue.hashCode();
		}
		// Do children
		int i = 0;
		while (i < children.size())
		{
			rval += children.get(i++).hashCode();
		}
		return rval;
	}

	/** Check if equals */
	public boolean equals(Object o)
	{
		if (!(o instanceof ConfigNode))
			return false;
		ConfigNode n = (ConfigNode)o;
		if (attributes.size() != n.attributes.size())
			return false;
		if (children.size() != n.children.size())
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
		Iterator iter = attributes.keySet().iterator();
		while (iter.hasNext())
		{
			String key = (String)iter.next();
			String attrValue = (String)attributes.get(key);
			String nAttrValue = (String)n.attributes.get(key);
			if (nAttrValue == null || !attrValue.equals(nAttrValue))
				return false;
		}
		int i = 0;
		while (i < children.size())
		{
			ConfigNode child = (ConfigNode)children.get(i);
			ConfigNode nChild = (ConfigNode)n.children.get(i);
			if (!child.equals(nChild))
				return false;
			i++;
		}
		return true;
	}

}
