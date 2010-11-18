/* $Id: DocumentSpecification.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.interfaces;

import org.apache.manifoldcf.core.interfaces.*;

/** This class represents a document specification, which is a generalized hierarchy of nodes that
* can be interpreted by a repository connector in an appropriate way.
*/
public class DocumentSpecification extends Specification
{
  public static final String _rcsid = "@(#)$Id: DocumentSpecification.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Constructor.
  */
  public DocumentSpecification()
  {
    super();
  }

  /** Construct from XML.
  *@param xml is the input XML.
  */
  public DocumentSpecification(String xml)
    throws ManifoldCFException
  {
    super(xml);
  }

  /** Duplicate.
  *@return an exact duplicate
  */
  public DocumentSpecification duplicate(boolean readOnly)
  {
    if (readOnly && this.readOnly)
      return this;
    DocumentSpecification rval = new DocumentSpecification();
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
