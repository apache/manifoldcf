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
package com.metacarta.core.interfaces;

import java.util.*;
import java.io.*;
import com.metacarta.core.common.XMLDoc;

/** This class represents a document specification, which is a generalized hierarchy of nodes that
* can be interpreted by a repository connector in an appropriate way.
*/
public class Specification
{
	public static final String _rcsid = "@(#)$Id$";

	// The children
	protected ArrayList children = new ArrayList();
	// Read-only flag
	protected boolean readOnly = false;
	
	/** Constructor.
	*/
	public Specification()
	{
	}

	/** Construct from XML.
	*@param xml is the input XML.
	*/
	public Specification(String xml)
		throws MetacartaException
	{
		fromXML(xml);
	}

	/** Make the specification read-only */
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
	
	/** Get as XML
	*@return the xml corresponding to this DocumentSpecification.
	*/
	public String toXML()
		throws MetacartaException
	{
		XMLDoc doc = new XMLDoc();
		// name of root node in definition
		Object top = doc.createElement(null,"specification");
		// Now, go through all children
		int i = 0;
		while (i < children.size())
		{
			SpecificationNode node = (SpecificationNode)children.get(i++);
			writeNode(doc,top,node);
		}

		return doc.getXML();
	}

	/** Write a specification node.
	*@param doc is the document.
	*@param parent is the parent.
	*@param node is the node.
	*/
	protected static void writeNode(XMLDoc doc, Object parent, SpecificationNode node)
		throws MetacartaException
	{
		// Get the type
		String type = node.getType();
		String value = node.getValue();
		Object o = doc.createElement(parent,type);
		Iterator iter = node.getAttributes();
		while (iter.hasNext())
		{
			String attribute = (String)iter.next();
			String attrValue = node.getAttributeValue(attribute);
			// Add to the element
			doc.setAttribute(o,attribute,attrValue);
		}

		if (value != null)
			doc.createText(o,value);
		// Now, do children
		int i = 0;
		while (i < node.getChildCount())
		{
			SpecificationNode child = node.getChild(i++);
			writeNode(doc,o,child);
		}
	}

	/** Read from XML.
	*@param xml is the input XML.
	*/
	public void fromXML(String xml)
		throws MetacartaException
	{
		if (readOnly)
			throw new IllegalStateException("Attempt to change read-only object");
		children.clear();
		XMLDoc doc = new XMLDoc(xml);
		ArrayList list = new ArrayList();
		doc.processPath(list, "*", null);

		if (list.size() != 1)
		{
			throw new MetacartaException("Bad xml - missing outer 'specification' node - there are "+Integer.toString(list.size())+" nodes");
		}
		Object parent = list.get(0);
		if (!doc.getNodeName(parent).equals("specification"))
			throw new MetacartaException("Bad xml - outer node is not 'specification'");

		list.clear();
		doc.processPath(list, "*", parent);

		// Outer level processing.
		int i = 0;
		while (i < list.size())
		{
			Object o = list.get(i++);
			SpecificationNode node = readNode(doc,o);
			children.add(node);
		}
	}

	/** Read a specification node from XML.
	*@param doc is the document.
	*@param object is the object.
	*@return the specification node.
	*/
	protected static SpecificationNode readNode(XMLDoc doc, Object object)
		throws MetacartaException
	{
		String type = doc.getNodeName(object);
		SpecificationNode rval = new SpecificationNode(type);
		String value = doc.getData(object);
		rval.setValue(value);
		// Do attributes
		ArrayList list = doc.getAttributes(object);
		int i = 0;
		while (i < list.size())
		{
			String attribute = (String)list.get(i++);
			String attrValue = doc.getValue(object,attribute);
			rval.setAttribute(attribute,attrValue);
		}
		// Now, do children
		list.clear();
		doc.processPath(list,"*",object);
		i = 0;
		while (i < list.size())
		{
			Object o = list.get(i);
			SpecificationNode node = readNode(doc,o);
			rval.addChild(i++,node);
		}
		return rval;
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

}
