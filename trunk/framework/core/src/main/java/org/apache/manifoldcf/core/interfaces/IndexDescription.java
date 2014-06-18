/* $Id: IndexDescription.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** Describe a database index.
*/
public class IndexDescription
{
  public static final String _rcsid = "@(#)$Id: IndexDescription.java 988245 2010-08-23 18:39:35Z kwright $";

  protected boolean isUnique;
  protected String[] columnNames;

  /** Create an index description.
  * Use this object in addTableIndex()
  *@param isUnique is true if the index is unique.
  *@param columnNames are the column names desired for this index.
  */
  public IndexDescription(boolean isUnique, String[] columnNames)
  {
    this.isUnique = isUnique;
    this.columnNames = columnNames;
  }

  public boolean getIsUnique()
  {
    return isUnique;
  }

  public String[] getColumnNames()
  {
    return columnNames;
  }

  public boolean equals(Object o)
  {
    if (!(o instanceof IndexDescription))
      return false;
    IndexDescription id = (IndexDescription)o;
    if (id.isUnique != isUnique || id.columnNames.length != columnNames.length)
      return false;

    int i = 0;
    while (i < columnNames.length)
    {
      if (!columnNames[i].equals(id.columnNames[i]))
        return false;
      i++;
    }
    return true;
  }
}

