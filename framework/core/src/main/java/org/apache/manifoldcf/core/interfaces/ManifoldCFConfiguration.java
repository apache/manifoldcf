/* $Id: ManifoldCFConfiguration.java 988245 2010-08-23 18:39:35Z kwright $ */

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

import java.util.*;
import java.io.*;

/** This class represents the configuration data read from the main ManifoldCF configuration
* XML file.
*/
public class ManifoldCFConfiguration extends Configuration
{
  public static final String _rcsid = "@(#)$Id: ManifoldCFConfiguration.java 988245 2010-08-23 18:39:35Z kwright $";

  // Configuration XML node names and attribute names
  public static final String NODE_PROPERTY = "property";
  public static final String ATTRIBUTE_NAME = "name";
  public static final String ATTRIBUTE_VALUE = "value";

  protected final Map<String,String> localProperties = new HashMap<String,String>();

  /** Constructor.
  */
  public ManifoldCFConfiguration()
  {
    super("configuration");
  }

  /** Construct from XML.
  *@param xmlStream is the input XML stream.
  */
  public ManifoldCFConfiguration(InputStream xmlStream)
    throws ManifoldCFException
  {
    super("configuration");
    fromXML(xmlStream);
    parseProperties();
  }

  public String getProperty(String s)
  {
    return localProperties.get(s);
  }
  
  /** Read a (string) property, either from the system properties, or from the local configuration file.
  *@param s is the property name.
  *@param defaultValue is the default value for the property.
  *@return the property value, as a string.
  */
  public String getStringProperty(String s, String defaultValue)
  {
    String rval = getProperty(s);
    if (rval == null)
      rval = defaultValue;
    return rval;
  }

  /** Read a possibly obfuscated string property, either from the system properties, or from the local configuration file.
  *@param s is the property name.
  *@param defaultValue is the default value for the property.
  *@return the property value, as a string.
  */
  public String getPossiblyObfuscatedStringProperty(String s, String defaultValue)
    throws ManifoldCFException
  {
    String obfuscatedPropertyName = s + ".obfuscated";
    String rval = getProperty(obfuscatedPropertyName);
    if (rval != null)
      return org.apache.manifoldcf.core.system.ManifoldCF.deobfuscate(rval);
    rval = getProperty(s);
    if (rval == null)
      rval = defaultValue;
    return rval;
  }

  /** Read a boolean property
  */
  public boolean getBooleanProperty(String s, boolean defaultValue)
    throws ManifoldCFException
  {
    String value = getProperty(s);
    if (value == null)
      return defaultValue;
    if (value.equals("true") || value.equals("yes"))
      return true;
    if (value.equals("false") || value.equals("no"))
      return false;
    throw new ManifoldCFException("Illegal property value for boolean property '"+s+"': '"+value+"'");
  }
  
  /** Read an integer property, either from the system properties, or from the local configuration file.
  */
  public int getIntProperty(String s, int defaultValue)
    throws ManifoldCFException
  {
    String value = getProperty(s);
    if (value == null)
      return defaultValue;
    try
    {
      return Integer.parseInt(value);
    }
    catch (NumberFormatException e)
    {
      throw new ManifoldCFException("Illegal property value for integer property '"+s+"': '"+value+"': "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
    }
  }

  /** Read a long property, either from the system properties, or from the local configuration file.
  */
  public long getLongProperty(String s, long defaultValue)
    throws ManifoldCFException
  {
    String value = getProperty(s);
    if (value == null)
      return defaultValue;
    try
    {
      return Long.parseLong(value);
    }
    catch (NumberFormatException e)
    {
      throw new ManifoldCFException("Illegal property value for long property '"+s+"': '"+value+"': "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
    }
  }

  /** Read a float property, either from the system properties, or from the local configuration file.
  */
  public double getDoubleProperty(String s, double defaultValue)
    throws ManifoldCFException
  {
    String value = getProperty(s);
    if (value == null)
      return defaultValue;
    try
    {
      return Double.parseDouble(value);
    }
    catch (NumberFormatException e)
    {
      throw new ManifoldCFException("Illegal property value for double property '"+s+"': '"+value+"': "+e.getMessage(),e,ManifoldCFException.SETUP_ERROR);
    }
  }

  protected void parseProperties()
    throws ManifoldCFException
  {
    // For convenience, post-process all "property" nodes so that we have a semblance of the earlier name/value pairs available, by default.
    // e.g. <property name= value=/>
    localProperties.clear();
    for (int i = 0; i < getChildCount(); i++)
    {
      ConfigurationNode cn = findChild(i);
      if (cn.getType().equals(NODE_PROPERTY))
      {
        String name = cn.getAttributeValue(ATTRIBUTE_NAME);
        String value = cn.getAttributeValue(ATTRIBUTE_VALUE);
        if (name == null)
          throw new ManifoldCFException("Node type '"+NODE_PROPERTY+"' requires a '"+ATTRIBUTE_NAME+"' attribute");
        localProperties.put(name,value);
      }
    }
  }
  
  /** Read from an input stream.
  */
  @Override
  public void fromXML(InputStream is)
    throws ManifoldCFException
  {
    super.fromXML(is);
    parseProperties();
  }
  
  /** Create a new object of the appropriate class.
  */
  @Override
  protected Configuration createNew()
  {
    return new ManifoldCFConfiguration();
  }
  
}
