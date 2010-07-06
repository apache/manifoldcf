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

import org.apache.lcf.core.interfaces.*;
import java.util.*;
import java.io.*;
import org.apache.lcf.core.system.LCF;
import org.apache.lcf.core.common.XMLDoc;

/** This class represents XML configuration information, in its most basic incarnation.
*/
public abstract class Configuration
{
  public static final String _rcsid = "@(#)$Id$";

  // The children
  protected ArrayList children = new ArrayList();
  // Read-only flag
  protected boolean readOnly = false;

  /** Constructor.
  */
  public Configuration()
  {
  }

  /** Construct from XML.
  *@param xml is the input XML.
  */
  public Configuration(String xml)
    throws LCFException
  {
    fromXML(xml);
  }

  /** Construct from XML.
  *@param xmlstream is the input XML stream.  Does NOT close the stream.
  */
  public Configuration(InputStream xmlstream)
    throws LCFException
  {
    fromXML(xmlstream);
  }

  /** Return the root node type.
  *@return the node type name.
  */
  protected abstract String getRootNodeLabel();
  
  /** Create a new object of the appropriate class.
  *@return the newly-created configuration object.
  */
  protected abstract Configuration createNew();
  
  /** Create a new child node of the appropriate type and class.
  *@return the newly-created node.
  */
  protected ConfigurationNode createNewNode(String type)
  {
    return new ConfigurationNode(type);
  }
  
  /** Note the removal of all outer nodes.
  */
  protected void clearOuterNodes()
  {
  }
  
  /** Note the addition of a new outer node.
  *@param node is the node that was just read.
  */
  protected void addOuterNode(ConfigurationNode node)
  {
  }
  
  /** Note the removal of an outer node.
  *@param node is the node that was just removed.
  */
  protected void removeOuterNode(ConfigurationNode node)
  {
  }
  
  /** Create a duplicate.
  */
  protected Configuration createDuplicate(boolean readOnly)
  {
    if (readOnly && this.readOnly)
      return this;
    // Create a new object.
    Configuration rval = createNew();
    rval.readOnly = readOnly;
    // Copy the children.
    if (children != null)
    {
      int i = 0;
      while (i < children.size())
      {
        ConfigurationNode child = (ConfigurationNode)children.get(i++);
        // Duplicate the child
        ConfigurationNode newChild = child.createDuplicate(readOnly);
        rval.addChild(rval.getChildCount(),newChild);
      }
    }
    return rval;
  }
  
  /** Make the configuration read-only */
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

  /** Get as XML
  *@return the xml corresponding to these ConfigParams.
  */
  public String toXML()
    throws LCFException
  {
    XMLDoc doc = new XMLDoc();
    // name of root node in definition
    Object top = doc.createElement(null,getRootNodeLabel());
    // Now, go through all children
    int i = 0;
    while (i < children.size())
    {
      ConfigurationNode node = (ConfigurationNode)children.get(i++);
      writeNode(doc,top,node);
    }

    return doc.getXML();
  }

  /** Write a specification node.
  *@param doc is the document.
  *@param parent is the parent.
  *@param node is the node.
  */
  protected static void writeNode(XMLDoc doc, Object parent, ConfigurationNode node)
    throws LCFException
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
      ConfigurationNode child = node.findChild(i++);
      writeNode(doc,o,child);
    }
  }

  /** Read from XML.
  *@param xml is the input XML.
  */
  public void fromXML(String xml)
    throws LCFException
  {
    XMLDoc doc = new XMLDoc(xml);
    initializeFromDoc(doc);
  }
  
  /** Read from an XML binary stream.
  *@param xmlstream is the input XML stream.  Does NOT close the stream.
  */
  public void fromXML(InputStream xmlstream)
    throws LCFException
  {
    XMLDoc doc = new XMLDoc(xmlstream);
    initializeFromDoc(doc);
  }

  protected void initializeFromDoc(XMLDoc doc)
    throws LCFException
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    clearChildren();
    ArrayList list = new ArrayList();
    doc.processPath(list, "*", null);

    if (list.size() != 1)
    {
      throw new LCFException("Bad xml - missing outer '"+getRootNodeLabel()+"' node - there are "+Integer.toString(list.size())+" nodes");
    }
    Object parent = list.get(0);
    if (!doc.getNodeName(parent).equals(getRootNodeLabel()))
      throw new LCFException("Bad xml - outer node is not '"+getRootNodeLabel()+"'");

    list.clear();
    doc.processPath(list, "*", parent);

    // Outer level processing.
    int i = 0;
    while (i < list.size())
    {
      Object o = list.get(i++);
      ConfigurationNode node = readNode(doc,o);
      addChild(getChildCount(),node);
    }
  }

  /** Read a configuration node from XML.
  *@param doc is the document.
  *@param object is the object.
  *@return the specification node.
  */
  protected ConfigurationNode readNode(XMLDoc doc, Object object)
    throws LCFException
  {
    String type = doc.getNodeName(object);
    ConfigurationNode rval = createNewNode(type);
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
      ConfigurationNode node = readNode(doc,o);
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
  public ConfigurationNode findChild(int index)
  {
    return (ConfigurationNode)children.get(index);
  }

  /** Remove child n.
  *@param index is the child to remove.
  */
  public void removeChild(int index)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    ConfigurationNode node = (ConfigurationNode)children.remove(index);
    removeOuterNode(node);
  }

  /** Add child at specified position.
  *@param index is the position to add the child.
  *@param child is the child to add.
  */
  public void addChild(int index, ConfigurationNode child)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    children.add(index,child);
    addOuterNode(child);
  }

  /** Clear children.
  */
  public void clearChildren()
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    children.clear();
    clearOuterNodes();
  }

  /** Calculate a hash code */
  public int hashCode()
  {
    int rval = 0;
    int i = 0;
    while (i < children.size())
    {
      rval += children.get(i++).hashCode();
    }
    return rval;
  }

  /** Do a comparison */
  public boolean equals(Object o)
  {
    if (!(o instanceof Configuration))
      return false;
    Configuration p = (Configuration)o;
    if (children.size() != p.children.size())
      return false;
    int i = 0;
    while (i < children.size())
    {
      if (!children.get(i).equals(p.children.get(i)))
        return false;
      i++;
    }
    return true;
  }

}
