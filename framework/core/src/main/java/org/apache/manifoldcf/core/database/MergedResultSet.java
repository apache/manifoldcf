/* $Id: MergedResultSet.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class merges several resultsets together to make what appears to be
* a single one.  This is very useful when queries are broken up due to restrictions
* in the length of an IN clause, but the results may need to be merged at the end.
*/
public class MergedResultSet implements IResultSet
{
  public static final String _rcsid = "@(#)$Id: MergedResultSet.java 988245 2010-08-23 18:39:35Z kwright $";

  // This is the array list of resultsets
  protected ArrayList resultSets = new ArrayList();
  protected int totalRowCount = 0;

  public MergedResultSet()
  {
  }

  public void addResultSet(IResultSet set)
  {
    resultSets.add(set);
    totalRowCount += set.getRowCount();
  }

  /** Get a specific row in the resultset.
  *@param rowNumber is the number of the row.
  *@return the immutable row description, or null if there is no such row.
  */
  public IResultRow getRow(int rowNumber)
  {
    // linear walk!  Inefficient - fix later.
    int j = 0;
    while (j < resultSets.size())
    {
      IResultSet set = (IResultSet)resultSets.get(j++);
      if (set.getRowCount() > rowNumber)
      {
        // row is in here
        return set.getRow(rowNumber);
      }
      rowNumber -= set.getRowCount();
    }
    return null;
  }

  /** Get the number of rows in this resultset.
  *@return the number of rows the resultset contains.
  */
  public int getRowCount()
  {
    return totalRowCount;
  }

  /** Get an array of all the rows.
  * This method is NOT preferred because it requires a new
  * array object to be constructed.
  *@return the array.
  */
  public IResultRow[] getRows()
  {
    IResultRow[] rval = new IResultRow[totalRowCount];
    int i = 0;
    int j = 0;
    while (j < resultSets.size())
    {
      IResultSet set = (IResultSet)resultSets.get(j++);
      int k = 0;
      while (k < set.getRowCount())
      {
        rval[i++] = set.getRow(k++);
      }
    }
    return rval;
  }
}


