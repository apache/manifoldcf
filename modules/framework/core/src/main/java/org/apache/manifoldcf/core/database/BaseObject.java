/* $Id: BaseObject.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.core.database;

import org.apache.manifoldcf.core.interfaces.*;
import java.util.*;

/** This is the base paper object, which can represents all the fields of a database row -
* plus anything else that is added.  This works together with BaseTable, if an
* instance representation is required.
*/
public class BaseObject
{
  public static final String _rcsid = "@(#)$Id: BaseObject.java 988245 2010-08-23 18:39:35Z kwright $";

  protected HashMap fields = new HashMap();

  /** Construct an empty one.
  */
  public BaseObject()
  {
  }

  /** Clear the current object.
  */
  public void clear()
  {
    fields.clear();
  }

  /** Get the list of fields that currently have
  * values.
  *@return an iterator of the current non-null fields.
  */
  public Iterator listFields()
  {
    return fields.keySet().iterator();
  }

  /** Get a field by name.
  *@param fieldName is the name of the field.
  *@return the field value, or null if it is not set.
  */
  public Object getValue(String fieldName)
  {
    return fields.get(fieldName);
  }

  /** Set a field by name.
  *@param fieldName is the name of the field.
  *@param fieldValue is the value, or null if the field should be removed.
  */
  public void setValue(String fieldName, Object fieldValue)
  {
    if (fieldValue == null)
      fields.remove(fieldName);
    else
      fields.put(fieldName,fieldValue);
  }

}
