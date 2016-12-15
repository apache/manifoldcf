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
package org.apache.manifoldcf.crawler.bins;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.CacheKeyFactory;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import org.apache.manifoldcf.crawler.system.Logging;
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
public class BinManager extends org.apache.manifoldcf.core.database.BaseTable implements IBinManager
{
  public static final String _rcsid = "@(#)$Id$";

  // Field names
  public final static String connectorClassField = "connectorclass";
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
  @Override
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
        // HSQLDB does not like null primary keys!!
        map.put(connectorClassField,new ColumnDescription("VARCHAR(255)",false,true,null,null,false));
        map.put(binNameField,new ColumnDescription("VARCHAR(255)",false,true,null,null,false));
        map.put(binCounterField,new ColumnDescription("FLOAT",false,false,null,null,false));
        performCreate(map,null);
      }
      else
      {
        // Upgrade
        if (existing.get(connectorClassField) == null) {
          HashMap map = new HashMap();
          map.put(connectorClassField,new ColumnDescription("VARCHAR(255)",false,true,null,null,false));
          performAlter(map,null,null,null);
        }
      }

      // Index management goes here
      IndexDescription binIndex = new IndexDescription(true,new String[]{connectorClassField,binNameField});

      // Get rid of indexes that shouldn't be there
      Map indexes = getTableIndexes(null,null);
      Iterator iter = indexes.keySet().iterator();
      while (iter.hasNext())
      {
        String indexName = (String)iter.next();
        IndexDescription id = (IndexDescription)indexes.get(indexName);

        if (binIndex != null && id.equals(binIndex))
          binIndex = null;
        else if (indexName.indexOf("_pkey") == -1)
          // This index shouldn't be here; drop it
          performRemoveIndex(indexName);
      }

      // Add the ones we didn't find
      if (binIndex != null)
        performAddIndex(null,binIndex);

      break;
    }
  }

  /** Uninstall.
  */
  @Override
  public void deinstall()
    throws ManifoldCFException
  {
    performDrop(null);
  }

  /** Reset all bins */
  @Override
  public void reset()
    throws ManifoldCFException
  {
    performDelete("", null, null);
  }

  /** Get N bin values (and set next one).  If the record does not yet exist, create it with a starting value.
  * We expect this to happen within a transaction!!.
  *@param connectorClass is the class name of the connector
  *@param binName is the name of the bin (256 char max)
  *@param newBinValue is the value to use if there is no such bin yet.  This is the value that will be
  * returned; what will be stored will be that value + 1.
  *@param count is the number of values desired.
  *@return the counter values.
  */
  @Override
  public double[] getIncrementBinValues(String connectorClass, String binName, double newBinValue, int count)
    throws ManifoldCFException
  {
    double[] returnValues = new double[count];
    // SELECT FOR UPDATE/MODIFY is the most common path
    ArrayList params = new ArrayList();
    String query = buildConjunctionClause(params,new ClauseDescription[]{
      new UnitaryClause(connectorClassField,connectorClass),
      new UnitaryClause(binNameField,binName)});
    IResultSet result = performQuery("SELECT "+binCounterField+" FROM "+getTableName()+" WHERE "+query+" FOR UPDATE",params,null,null);
    if (result.getRowCount() > 0)
    {
      IResultRow row = result.getRow(0);
      Double value = (Double)row.getValue(binCounterField);
      double rval = value.doubleValue();
      if (rval < newBinValue)
        rval = newBinValue;
      // rval is the starting value; compute the entire array based on it.
      for (int i = 0; i < count; i++)
      {
        returnValues[i] = rval;
        rval += 1.0;
      }
      HashMap map = new HashMap();
      map.put(binCounterField,new Double(rval));
      performUpdate(map," WHERE "+query,params,null);
    }
    else
    {
      for (int i = 0; i < count; i++)
      {
        returnValues[i] = newBinValue;
        newBinValue += 1.0;
      }
      HashMap map = new HashMap();
      map.put(connectorClassField,connectorClass);
      map.put(binNameField,binName);
      map.put(binCounterField,new Double(newBinValue));
      performInsert(map,null);
    }
    return returnValues;
  }

  /** Get N bin values (and set next one).  If the record does not yet exist, create it with a starting value.
  * This method invokes its own retry-able transaction.
  *@param connectorClass is the class name of the connector
  *@param binName is the name of the bin (256 char max)
  *@param newBinValue is the value to use if there is no such bin yet.  This is the value that will be
  * returned; what will be stored will be that value + 1.
  *@param count is the number of values desired.
  *@return the counter values.
  */
  @Override
  public double[] getIncrementBinValuesInTransaction(String connectorClass, String binName, double newBinValue, int count)
    throws ManifoldCFException
  {
    while (true)
    {
      long sleepAmt = 0L;
      beginTransaction();
      try
      {
        return getIncrementBinValues(connectorClass, binName, newBinValue, count);
      }
      catch (Error e)
      {
        signalRollback();
        throw e;
      }
      catch (RuntimeException e)
      {
        signalRollback();
        throw e;
      }
      catch (ManifoldCFException e)
      {
        signalRollback();
        if (e.getErrorCode() == e.DATABASE_TRANSACTION_ABORT)
        {
          if (Logging.perf.isDebugEnabled())
            Logging.perf.debug("Aborted transaction obtaining docpriorities: "+e.getMessage());
          sleepAmt = getSleepAmt();
          continue;
        }
        throw e;
      }
      finally
      {
        endTransaction();
        sleepFor(sleepAmt);
      }
    }
  }

}
