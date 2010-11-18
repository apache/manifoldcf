/* $Id: ScheduleList.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import java.util.*;

/** This class describes an ordered set of schedule records.  They are ordered only for
* UI nicety, not any functional reason.
*/
public class ScheduleList
{
  public static final String _rcsid = "@(#)$Id: ScheduleList.java 988245 2010-08-23 18:39:35Z kwright $";

  // This is where the records are kept.
  protected ArrayList list = new ArrayList();

  /** Constructor.
  */
  public ScheduleList()
  {
  }

  /** Clear it.
  */
  public void clear()
  {
    list.clear();
  }

  /** Duplicate this list.
  *@return the duplicate.
  */
  public ScheduleList duplicate()
  {
    ScheduleList rval = new ScheduleList();
    int i = 0;
    while (i < list.size())
    {
      rval.list.add(list.get(i));
      i++;
    }
    return rval;
  }

  /** Add a record.
  *@param sr is the record to add to the end.
  */
  public void addRecord(ScheduleRecord sr)
  {
    list.add(sr);
  }

  /** Get the number of records.
  *@return the record count.
  */
  public int getRecordCount()
  {
    return list.size();
  }

  /** Get the specified record.
  *@param index is the record number.
  *@return the record.
  */
  public ScheduleRecord getRecord(int index)
  {
    return (ScheduleRecord)list.get(index);
  }

  /** Delete a record.
  *@param index is the record number.
  */
  public void deleteRecord(int index)
  {
    list.remove(index);
  }

}
