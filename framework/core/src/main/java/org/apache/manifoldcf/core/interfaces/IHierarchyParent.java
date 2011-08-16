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
package org.apache.manifoldcf.core.interfaces;

import java.io.*;
import java.util.*;

/** This interface describes the methods for support configuration parent/child
* relationships.
*/
public interface IHierarchyParent
{
  public static final String _rcsid = "@(#)$Id$";

  /** Clear children.
  */
  public void clearChildren();

   /** Get child count.
  *@return the count.
  */
  public int getChildCount();

  /** Get child n.
  *@param index is the child number.
  *@return the child node.
  */
  public ConfigurationNode findChild(int index);

  /** Remove child n.
  *@param index is the child to remove.
  */
  public void removeChild(int index);

  /** Add child at specified position.
  *@param index is the position to add the child.
  *@param child is the child to add.
  */
  public void addChild(int index, ConfigurationNode child);

}
