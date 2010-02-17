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
package org.apache.lcf.crawler.jobs;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import org.apache.lcf.crawler.interfaces.CacheKeyFactory;
import java.util.*;

/** This class manages the prerequisite event table.
* This table lists the prerequisite event rows that must NOT exist in order for a jobqueue entry to be queued for processing.  If a prerequisite event row does, in fact,
* exist, then queuing and processing do not take place until that row is cleared.
*/
public class PrereqEventManager extends org.apache.lcf.core.database.BaseTable
{
        public static final String _rcsid = "@(#)$Id$";

        // Field names
        public final static String ownerField = "owner";
        public final static String eventNameField = "eventname";

            // Count of events to call analyze for
        protected static final long ANALYZE_COUNT = 50000L;
        // Count of events to call reindex for
        protected static final long REINDEX_COUNT = 250000L;

        /** Counter for kicking off analyze */
        protected static Tracker tracker = new Tracker(ANALYZE_COUNT/2);
        /** Counter for kicking off reindex */
        protected static Tracker reindexTracker = new Tracker(REINDEX_COUNT/2);


        /** Constructor.
        *@param database is the database handle.
        */
        public PrereqEventManager(IDBInterface database)
                throws LCFException
        {
                super(database,"prereqevents");
        }

        /** Install or upgrade this table.
        */
        public void install(String ownerTableName, String ownerColumn)
                throws LCFException
        {
                beginTransaction();
                try
                {
                        Map existing = getTableSchema(null,null);
                        if (existing == null)
                        {
                                HashMap map = new HashMap();
                                map.put(ownerField,new ColumnDescription("BIGINT",false,false,ownerTableName,ownerColumn,false));
                                map.put(eventNameField,new ColumnDescription("VARCHAR(255)",false,false,null,null,false));
                                performCreate(map,null);
                            
                                // Indexes
                                ArrayList list = new ArrayList();
                                list.add(ownerField);
                                addTableIndex(false,list);
                        }
                        else
                        {
                                // No upgrade is possible since this table has just been introduced.
                        }
                }
                catch (LCFException e)
                {
                        signalRollback();
                        throw e;
                }
                catch (Error e)
                {
                        signalRollback();
                        throw e;
                }
                finally
                {
                        endTransaction();
                }
        }

        /** Uninstall.
        */
        public void deinstall()
                throws LCFException
        {
                beginTransaction();
                try
                {
                        performDrop(null);
                }
                catch (LCFException e)
                {
                        signalRollback();
                        throw e;
                }
                catch (Error e)
                {
                        signalRollback();
                        throw e;
                }
                finally
                {
                        endTransaction();
                }
        }

        /** Delete specified rows, based on jobqueue criteria. */
        public void deleteRows(String parentTableName, String joinField, String parentCriteria, ArrayList list)
                throws LCFException
        {
                StringBuffer sb = new StringBuffer();
                sb.append("WHERE EXISTS(SELECT 'x' FROM ").append(parentTableName).append(" WHERE ").append(joinField).append("=")
                        .append(getTableName()).append(".").append(ownerField);
                if (parentCriteria != null)
                        sb.append(" AND ").append(parentCriteria);
                sb.append(")");
                performDelete(sb.toString(),list,null);
                reindexTracker.noteEvent();
        }

        /** Delete specified rows, as directly specified without a join. */
        public void deleteRows(String ownerQueryPart, ArrayList list)
                throws LCFException
        {
                performDelete("WHERE "+ownerField+" IN("+ownerQueryPart+")",list,null);
                reindexTracker.noteEvent();
        }
        
        /** Delete rows pertaining to a single entry */
        public void deleteRows(Long recordID)
                throws LCFException
        {
                ArrayList list = new ArrayList();
                list.add(recordID);
                performDelete(" WHERE "+ownerField+"=?",list,null);
                reindexTracker.noteEvent();
        }
        
        /** Add rows pertaining to a single entry */
        public void addRows(Long recordID, String[] eventNames)
                throws LCFException
        {
                if (eventNames != null)
                {
                        int i = 0;
                        while (i < eventNames.length)
                        {
                                HashMap map = new HashMap();
                                map.put(ownerField,recordID);
                                map.put(eventNameField,eventNames[i++]);
                                performInsert(map,null);
                        }
                        tracker.noteEvent(i);
                }
        }
        
        /** Conditionally do analyze operation.
        */
        public void conditionallyMaintainTables()
                throws LCFException
        {
                if (tracker.checkAction())
                {
                        try
                        {
                                // Do the analyze
                                analyzeTable();
                        }
                        finally
                        {
                                tracker.noteAction(ANALYZE_COUNT);
                        }
                }
                if (reindexTracker.checkAction())
                {
                        try
                        {
                                // Do the reindex
                                reindexTable();
                        }
                        finally
                        {
                                reindexTracker.noteAction(REINDEX_COUNT);
                        }
                }
        }


        /** Analyze tracker class.
        */
        protected static class Tracker
        {
                // Number of records to track before kicking off the action.
                protected long recordCount;
                protected boolean busy = false;
                
                /** Constructor.
                */
                public Tracker(long initialValue)
                {
                        recordCount = initialValue;
                }

                /** Note an analyze.
                */
                public synchronized void noteAction(long repeatCount)
                {
                        recordCount = repeatCount;
                        busy = false;
                }

                public synchronized void noteEvent(int count)
                {
                        if (recordCount >= (long)count)
                                recordCount -= (long)count;
                        else
                                recordCount = 0L;
                }

                /** Note an insert */
                public synchronized void noteEvent()
                {
                        if (recordCount > 0L)
                                recordCount--;
                }
                
                /** See if action is required.
                */
                public synchronized boolean checkAction()
                {
                        if (busy)
                                return false;
                        busy = (recordCount == 0L);
                        return busy;
                }


        }

}
