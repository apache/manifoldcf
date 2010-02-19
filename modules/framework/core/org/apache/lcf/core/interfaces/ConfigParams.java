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

/** This class represents a set of configuration parameters, with structure, which is a generalized hierarchy of nodes that
* can be interpreted by a repository or authority connector in an appropriate way.
*/
public class ConfigParams
{
        public static final String _rcsid = "@(#)$Id$";

        /** The parameter type node */
        protected final static String PARAMETER_TYPE = "_PARAMETER_";
        protected final static String ATTR_NAME = "name";


        // The children
        protected ArrayList children = new ArrayList();
        // The parameter map (which stores name/value pairs also listed in the children)
        protected HashMap params = new HashMap();

        /** Constructor.
        */
        public ConfigParams()
        {
        }

        /** Constructor.
        *@param map is the initialized (mutable) map describing the name/value configuration parameters.
        * This method of setting up a ConfigParams object will go away when the parameters are all in XML.
        */
        public ConfigParams(Map map)
        {
                Iterator iter = map.keySet().iterator();
                while (iter.hasNext())
                {
                        String key = (String)iter.next();
                        String value = (String)map.get(key);
                        ConfigNode cn = new ConfigNode(PARAMETER_TYPE);
                        cn.setAttribute(ATTR_NAME,key);
                        cn.setValue(value);
                        children.add(cn);
                        params.put(key,value);
                }
        }

        /** Construct from XML.
        *@param xml is the input XML.
        */
        public ConfigParams(String xml)
                throws LCFException
        {
                fromXML(xml);
        }

        /** Get as XML
        *@return the xml corresponding to these ConfigParams.
        */
        public String toXML()
                throws LCFException
        {
                XMLDoc doc = new XMLDoc();
                // name of root node in definition
                Object top = doc.createElement(null,"configuration");
                // Now, go through all children
                int i = 0;
                while (i < children.size())
                {
                        ConfigNode node = (ConfigNode)children.get(i++);
                        writeNode(doc,top,node);
                }

                return doc.getXML();
        }

        /** Write a specification node.
        *@param doc is the document.
        *@param parent is the parent.
        *@param node is the node.
        */
        protected static void writeNode(XMLDoc doc, Object parent, ConfigNode node)
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
                        ConfigNode child = node.getChild(i++);
                        writeNode(doc,o,child);
                }
        }

        /** Read from XML.
        *@param xml is the input XML.
        */
        public void fromXML(String xml)
                throws LCFException
        {
                children.clear();
                params.clear();
                XMLDoc doc = new XMLDoc(xml);
                ArrayList list = new ArrayList();
                doc.processPath(list, "*", null);

                if (list.size() != 1)
                {
                        throw new LCFException("Bad xml - missing outer 'configuration' node - there are "+Integer.toString(list.size())+" nodes");
                }
                Object parent = list.get(0);
                if (!doc.getNodeName(parent).equals("configuration"))
                        throw new LCFException("Bad xml - outer node is not 'configuration'");

                list.clear();
                doc.processPath(list, "*", parent);

                // Outer level processing.
                int i = 0;
                while (i < list.size())
                {
                        Object o = list.get(i++);
                        ConfigNode node = readNode(doc,o);
                        children.add(node);
                        // Populate the params too.
                        if (node.getType().equals(PARAMETER_TYPE))
                        {
                                String name = node.getAttributeValue(ATTR_NAME);
                                String value = node.getValue();
                                if (name != null && value != null)
                                        params.put(name,value);
                        }
                }
        }

        /** Read a specification node from XML.
        *@param doc is the document.
        *@param object is the object.
        *@return the specification node.
        */
        protected static ConfigNode readNode(XMLDoc doc, Object object)
                throws LCFException
        {
                String type = doc.getNodeName(object);
                ConfigNode rval = new ConfigNode(type);
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
                        ConfigNode node = readNode(doc,o);
                        rval.addChild(i++,node);
                }
                return rval;
        }

        /** Get a parameter value.
        *@param key is the name of the parameter.
        *@return the value.
        */
        public String getParameter(String key)
        {
                return (String)params.get(key);
        }

        /** Get an obfuscated parameter value.
        *@param key is the name of the parameter.
        *@return the unobfuscated value.
        */
        public String getObfuscatedParameter(String key)
        {
                String rval = getParameter(key);
                if (rval == null)
                        return rval;
                try
                {
                        return LCF.deobfuscate(rval);
                }
                catch (LCFException e)
                {
                        // Ignore this exception, and return an empty string.
                        return "";
                }
        }

        /** Set a parameter value.
        *@param key is the name of the parameter.
        *@param value is the new value, or null if we should
        * delete the value.
        */
        public void setParameter(String key, String value)
        {
                // See if we've got it
                if (params.get(key) != null)
                {
                        // Linear scan.  This is ugly, but this method is deprecated and it will go away shortly.
                        int i = 0;
                        while (i < children.size())
                        {
                                ConfigNode node = (ConfigNode)children.get(i);
                                if (node.getType().equals(PARAMETER_TYPE))
                                {
                                        String name = node.getAttributeValue(ATTR_NAME);
                                        if (name.equals(key))
                                        {
                                                removeChild(i);
                                                break;
                                        }
                                }
                                i++;
                        }
                }
                ConfigNode cn = new ConfigNode(PARAMETER_TYPE);
                cn.setAttribute(ATTR_NAME,key);
                cn.setValue(value);
                children.add(cn);
                params.put(key,value);
        }

        /** Set an obfuscated parameter.
        *@param key is the name of the parameter.
        *@param value is the unobfuscated new value, or null if delete request.
        */
        public void setObfuscatedParameter(String key, String value)
        {
                if (value != null)
                {
                        try
                        {
                                value = LCF.obfuscate(value);
                        }
                        catch (LCFException e)
                        {
                                // Ignore this exception, and set "" to be the value
                                value = "";
                        }
                }
                setParameter(key,value);
        }

        /** List parameters.
        */
        public Iterator listParameters()
        {
                return params.keySet().iterator();
        }

        /** Duplicate.
        *@return an exact duplicate
        */
        public ConfigParams duplicate()
        {
                ConfigParams rval = new ConfigParams();
                int i = 0;
                while (i < children.size())
                {
                        ConfigNode node = (ConfigNode)children.get(i++);
                        rval.children.add(node.duplicate());
                        if (node.getType().equals(PARAMETER_TYPE))
                        {
                                String name = node.getAttributeValue(ATTR_NAME);
                                String value = node.getValue();
                                if (value != null)
                                        rval.params.put(name,value);
                        }
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
        public ConfigNode getChild(int index)
        {
                return (ConfigNode)children.get(index);
        }

        /** Remove child n.
        *@param index is the child to remove.
        */
        public void removeChild(int index)
        {
                ConfigNode node = (ConfigNode)children.remove(index);
                if (node.getType().equals(PARAMETER_TYPE))
                {
                        String name = node.getAttributeValue(ATTR_NAME);
                        if (name != null)
                                params.remove(name);
                }
        }

        /** Add child at specified position.
        *@param index is the position to add the child.
        *@param child is the child to add.
        */
        public void addChild(int index, ConfigNode child)
        {
                children.add(index,child);
                if (child.getType().equals(PARAMETER_TYPE))
                {
                        String name = child.getAttributeValue(ATTR_NAME);
                        String value = child.getValue();
                        if (name != null && value != null)
                                params.put(name,value);
                }
        }

        /** Clear children.
        */
        public void clearChildren()
        {
                children.clear();
                params.clear();
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
                if (!(o instanceof ConfigParams))
                        return false;
                ConfigParams p = (ConfigParams)o;
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
