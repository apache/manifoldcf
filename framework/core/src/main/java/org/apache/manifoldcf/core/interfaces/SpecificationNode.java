/* $Id: SpecificationNode.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class represents a node in a specification structure.  Its existence apart from
* ConfigurationNode is largely a relic.
*/
public class SpecificationNode extends ConfigurationNode
{
  public static final String _rcsid = "@(#)$Id: SpecificationNode.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Constructor.
  */
  public SpecificationNode(String type)
  {
    super(type);
  }

  /** Copy constructor.
  */
  public SpecificationNode(ConfigurationNode source)
  {
    super(source);
  }
  
  /** Create a new node of this same type and class.
  */
  protected ConfigurationNode createNewNode()
  {
    return new SpecificationNode(type);
  }

  /** Make a new node that is a copy of the specified node.
  */
  protected ConfigurationNode createNewNode(ConfigurationNode source)
  {
    return new SpecificationNode(source);
  }
  
  /** Duplicate.
  *@return the duplicate.
  */
  public SpecificationNode duplicate(boolean readOnly)
  {
    return (SpecificationNode)createDuplicate(readOnly);
  }

  /** Get child n.
  *@param index is the child number.
  *@return the child node.
  */
  public SpecificationNode getChild(int index)
  {
    return (SpecificationNode)findChild(index);
  }

}
