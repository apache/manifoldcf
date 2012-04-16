/* $Id: ConfigParams.java 988245 2010-08-23 18:39:35Z kwright $ */

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

import org.apache.manifoldcf.core.interfaces.*;
import java.util.*;
import java.io.*;
import org.apache.manifoldcf.core.system.ManifoldCF;

/** This class represents a set of configuration parameters, with structure, which is a generalized hierarchy of nodes that
* can be interpreted by a repository or authority connector in an appropriate way.
*/
public class ConfigParams extends Configuration
{
  public static final String _rcsid = "@(#)$Id: ConfigParams.java 988245 2010-08-23 18:39:35Z kwright $";

  /** The parameter type node */
  protected final static String PARAMETER_TYPE = "_PARAMETER_";
  protected final static String ATTR_NAME = "name";

  // The parameter map (which stores name/value pairs also listed in the children)
  protected Map<String,String> params = new HashMap<String,String>();

  /** Constructor.
  */
  public ConfigParams()
  {
    super("configuration");
  }

  /** Constructor.
  *@param map is the initialized (mutable) map describing the name/value configuration parameters.
  * This method of setting up a ConfigParams object will go away when the parameters are all in XML.
  */
  public ConfigParams(Map<String,String> map)
  {
    super("configuration");
    Iterator<String> iter = map.keySet().iterator();
    while (iter.hasNext())
    {
      String key = iter.next();
      String value = map.get(key);
      ConfigNode cn = new ConfigNode(PARAMETER_TYPE);
      cn.setAttribute(ATTR_NAME,key);
      cn.setValue(value);
      addChild(getChildCount(),cn);
    }
  }

  /** Construct from XML.
  *@param xml is the input XML.
  */
  public ConfigParams(String xml)
    throws ManifoldCFException
  {
    super("configuration");
    fromXML(xml);
  }

  /** Construct from XML.
  *@param xmlstream is the input XML stream.  Does NOT close the stream.
  */
  public ConfigParams(InputStream xmlstream)
    throws ManifoldCFException
  {
    super("configuration");
    fromXML(xmlstream);
  }

  /** Create a new object of the appropriate class.
  */
  protected Configuration createNew()
  {
    return new ConfigParams();
  }

  /** Create a new child node of the appropriate type and class.
  */
  protected ConfigurationNode createNewNode(String type)
  {
    return new ConfigNode(type);
  }
  
  /** Note the removal of all outer nodes.
  */
  protected void clearOuterNodes()
  {
    params.clear();
  }
  
  /** Note the addition of a new outer node.
  *@param node is the node that was just read.
  */
  protected void addOuterNode(ConfigurationNode node)
  {
    if (node.getType().equals(PARAMETER_TYPE))
    {
      String name = node.getAttributeValue(ATTR_NAME);
      String value = node.getValue();
      if (name != null && value != null)
        params.put(name,value);
    }
  }
  
  /** Note the removal of an outer node.
  *@param node is the node that was just removed.
  */
  protected void removeOuterNode(ConfigurationNode node)
  {
    if (node.getType().equals(PARAMETER_TYPE))
    {
      String name = node.getAttributeValue(ATTR_NAME);
      if (name != null)
        params.remove(name);
    }
  }

  /** Get a parameter value.
  *@param key is the name of the parameter.
  *@return the value.
  */
  public String getParameter(String key)
  {
    return params.get(key);
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
      return ManifoldCF.deobfuscate(rval);
    }
    catch (ManifoldCFException e)
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
    if (value != null)
    {
      ConfigNode cn = new ConfigNode(PARAMETER_TYPE);
      cn.setAttribute(ATTR_NAME,key);
      cn.setValue(value);
      addChild(getChildCount(),cn);
    }
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
        value = ManifoldCF.obfuscate(value);
      }
      catch (ManifoldCFException e)
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
    return (ConfigParams)createDuplicate(false);
  }

  /** Get child node.
  *@param index is the node number.
  *@return the node.
  */
  public ConfigNode getChild(int index)
  {
    return (ConfigNode)findChild(index);
  }
  
}
