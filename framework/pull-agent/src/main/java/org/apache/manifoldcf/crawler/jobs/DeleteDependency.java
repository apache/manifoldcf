/* $Id: DeleteDependency.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.jobs;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import java.util.*;

/** This class represents and describes a single delete dependency.
*/
public class DeleteDependency
{
  public static final String _rcsid = "@(#)$Id: DeleteDependency.java 988245 2010-08-23 18:39:35Z kwright $";

  // Data
  protected String linkType;
  protected String parentIDHash;
  protected String childIDHash;

  /** Constructor. */
  public DeleteDependency(String linkType, String parentIDHash, String childIDHash)
  {
    if (linkType == null)
      linkType = "";
    this.linkType = linkType;
    this.parentIDHash = parentIDHash;
    if (childIDHash == null)
      childIDHash = "";
    this.childIDHash = childIDHash;
  }

  /** Get linktype */
  public String getLinkType()
  {
    return linkType;
  }

  /** Get parent identifier */
  public String getParentIDHash()
  {
    return parentIDHash;
  }

  /** Get child identifier */
  public String getChildIDHash()
  {
    return childIDHash;
  }

  /** hash */
  public int hashCode()
  {
    return linkType.hashCode() + parentIDHash.hashCode() + childIDHash.hashCode();
  }

  /** Compare */
  public boolean equals(Object o)
  {
    if (!(o instanceof DeleteDependency))
      return false;
    DeleteDependency d = (DeleteDependency)o;
    return linkType.equals(d.linkType) && parentIDHash.equals(d.parentIDHash) && childIDHash.equals(d.childIDHash);
  }

}
