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
package org.apache.manifoldcf.crawler.jobs;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.CacheKeyFactory;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import java.util.*;

/** This class manages the docbins table.
* A row in this table represents a document bin.  The count that is kept is the
* number of documents in this particular bin that have been assigned a document priority.
* 
* <br><br>
* <b>docbins</b>
* <table border="1" cellpadding="3" cellspacing="0">
* <tr class="TableHeadingColor">
* <th>Field</th><th>Type</th><th>Description&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
* <tr><td>binname</td><td>VARCHAR(255)</td><td>Primary Key</td></tr>
* <tr><td>bincounter</td><td>BIGINT</td><td></td></tr>
* </table>
* <br><br>
* 
*/
public class BinManager extends org.apache.manifoldcf.core.database.BaseTable
{
  public static final String _rcsid = "@(#)$Id$";

  // Field names
  public final static String binNameField = "binname";
  public final static String binCounterField = "bincounter";
  
  /** Constructor.
  *@param database is the database handle.
  */
  public BinManager(IDBInterface database)
    throws ManifoldCFException
  {
    super(database,"docbins");
  }

  /** Install or upgrade this table.
  */
  public void install()
    throws ManifoldCFException
  {
    // Standard practice: outer loop for installs
    while (true)
    {
      Map existing = getTableSchema(null,null);
      if (existing == null)
      {
        HashMap map = new HashMap();
        map.put(binNameField,new ColumnDescription("VARCHAR(255)",true,true,null,null,false));
        map.put(binCounterField,new ColumnDescription("BIGINT",false,false,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Upgrade goes here if needed
      }

      // Index management goes here

      break;
    }
  }

  /** Uninstall.
  */
  public void deinstall()
    throws ManifoldCFException
  {
    performDrop(null);
  }

  /** Reset all bins */
  public void reset()
    throws ManifoldCFException
  {
    performDelete("", null, null);
  }

  /** Get a bin value (and set next one).  If the record does not yet exist, create it with a starting value.
  * We expect this to happen within a transaction!! 
  *@param binName is the name of the bin (256 char max)
  *@param newBinValue is the value to use if there is no such bin yet.  This is the value that will be
  * returned; what will be stored will be that value + 1.
  *@return the counter value.
  */
  public long getIncrementBinValue(String binName, long newBinValue)
    throws ManifoldCFException
  {
    // SELECT FOR UPDATE/MODIFY is the most common path
    ArrayList params = new ArrayList();
    String query = buildConjunctionClause(params,new ClauseDescription[]{
      new UnitaryClause(binNameField,binName)});
    IResultSet result = performQuery("SELECT "+binCounterField+" FROM "+getTableName()+" WHERE "+query+" FOR UPDATE",params,null,null);
    if (result.getRowCount() > 0)
    {
      IResultRow row = result.getRow(0);
      Long value = (Long)row.getValue(binCounterField);
      long rval = value.longValue();
      HashMap map = new HashMap();
      map.put(binCounterField,new Long(rval+1L));
      performUpdate(map," WHERE "+query,params,null);
      return rval;
    }
    else
    {
      HashMap map = new HashMap();
      map.put(binNameField,binName);
      map.put(binCounterField,new Long(newBinValue+1L));
      performInsert(map,null);
      return newBinValue;
    }
  }

}
