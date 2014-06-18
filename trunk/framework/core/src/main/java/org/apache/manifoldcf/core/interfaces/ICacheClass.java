/* $Id: ICacheClass.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This interface represents an object class.  It provides methods that permit
* the cache manager to learn about the LRU properties of a cached object.
*/
public interface ICacheClass
{
  public static final String _rcsid = "@(#)$Id: ICacheClass.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Get the name of the object class.
  * This determines the set of objects that are treated in the same
  * LRU pool.
  *@return the class name.
  */
  public String getClassName();

  /** Get the maximum LRU count of the object class.
  *@return the maximum number of the objects of the particular class
  * allowed.
  */
  public int getMaxLRUCount();
}

