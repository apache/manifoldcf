/* $Id: Specification.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class represents a specification, which is a generalized hierarchy of nodes that
* can be interpreted by an appropriate connector in an appropriate way.
*/
public class Specification extends Configuration
{
  public static final String _rcsid = "@(#)$Id: Specification.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Constructor.
  */
  public Specification()
  {
    super("specification");
  }

  /** Construct from XML.
  *@param xml is the input XML.
  */
  public Specification(String xml)
    throws ManifoldCFException
  {
    super("specification");
    fromXML(xml);
  }

  /** Create a new object of the appropriate class.
  */
  protected Configuration createNew()
  {
    return new Specification();
  }
  
  /** Create a new child node of the appropriate type and class.
  */
  protected ConfigurationNode createNewNode(String type)
  {
    return new SpecificationNode(type);
  }

  /** Get child n.
  *@param index is the child number.
  *@return the child node.
  */
  public SpecificationNode getChild(int index)
  {
    return (SpecificationNode)findChild(index);
  }

  /** Duplicate.
  *@return an exact duplicate
  */
  public Specification duplicate(boolean readOnly)
  {
    if (readOnly && this.readOnly)
      return this;
    Specification rval = new Specification();
    int i = 0;
    while (i < children.size())
    {
      SpecificationNode node = (SpecificationNode)children.get(i++);
      rval.children.add(node.duplicate(readOnly));
    }
    rval.readOnly = readOnly;
    return rval;
  }

}
