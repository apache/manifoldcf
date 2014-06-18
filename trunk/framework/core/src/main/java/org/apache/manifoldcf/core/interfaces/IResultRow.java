/* $Id: IResultRow.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This interface allows immutable access to a resultset row.
*/
public interface IResultRow
{
  public static final String _rcsid = "@(#)$Id: IResultRow.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Obtain the number of columns in the row.
  *@return the number of columns that row contains.
  */
  public int getColumnCount();

  /** Obtain the set of columns for a row.
  @return an iterator that will list all the (String) column names stored in that row.
  */
  public Iterator<String> getColumns();

  /** Get the row value for a column.
  *@param columnName is the name of the column.
  *@return the value, or null if not present.
  */
  public Object getValue(String columnName);

}
