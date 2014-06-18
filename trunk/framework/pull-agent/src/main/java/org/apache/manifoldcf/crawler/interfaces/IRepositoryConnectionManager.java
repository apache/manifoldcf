/* $Id: IRepositoryConnectionManager.java 996524 2010-09-13 13:38:01Z kwright $ */

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
package org.apache.manifoldcf.crawler.interfaces;

import org.apache.manifoldcf.core.interfaces.*;

/** Manager classes of this kind use the database to contain a human description of a repository connection.
*/
public interface IRepositoryConnectionManager
{
  public static final String _rcsid = "@(#)$Id: IRepositoryConnectionManager.java 996524 2010-09-13 13:38:01Z kwright $";

  /** Install the manager.
  */
  public void install()
    throws ManifoldCFException;

  /** Uninstall the manager.
  */
  public void deinstall()
    throws ManifoldCFException;

  /** Export configuration */
  public void exportConfiguration(java.io.OutputStream os)
    throws java.io.IOException, ManifoldCFException;

  /** Import configuration */
  public void importConfiguration(java.io.InputStream is)
    throws java.io.IOException, ManifoldCFException;

  /** Obtain a list of the repository connections, ordered by name.
  *@return an array of connection objects.
  */
  public IRepositoryConnection[] getAllConnections()
    throws ManifoldCFException;

  /** Load a repository connection by name.
  *@param name is the name of the repository connection.
  *@return the loaded connection object, or null if not found.
  */
  public IRepositoryConnection load(String name)
    throws ManifoldCFException;

  /** Load a set of repository connections.
  *@param names are the names of the repository connections.
  *@return the descriptors of the repository connections, with null
  * values for those not found.
  */
  public IRepositoryConnection[] loadMultiple(String[] names)
    throws ManifoldCFException;

  /** Create a new repository connection object.
  *@return the new object.
  */
  public IRepositoryConnection create()
    throws ManifoldCFException;

  /** Save a repository connection object.
  *@param object is the object to save.
  *@return true if the object is created, false otherwise.
  */
  public boolean save(IRepositoryConnection object)
    throws ManifoldCFException;

  /** Delete a repository connection.
  *@param name is the name of the connection to delete.  If the
  * name does not exist, no error is returned.
  */
  public void delete(String name)
    throws ManifoldCFException;

  /** Return true if the specified authority group name is referenced.
  *@param authorityGroup is the authority group name.
  *@return true if referenced, false otherwise.
  */
  public boolean isGroupReferenced(String authorityGroup)
    throws ManifoldCFException;

  /** Get a list of repository connections that share the same connector.
  *@param className is the class name of the connector.
  *@return the repository connections that use that connector.
  */
  public String[] findConnectionsForConnector(String className)
    throws ManifoldCFException;

  /** Check if underlying connector exists.
  *@param name is the name of the connection to check.
  *@return true if the underlying connector is registered.
  */
  public boolean checkConnectorExists(String name)
    throws ManifoldCFException;

  // Schema related

  /** Return the primary table name.
  *@return the table name.
  */
  public String getTableName();

  /** Return the name column.
  *@return the name column.
  */
  public String getConnectionNameColumn();


  // Reporting and analysis related

  /** Delete history rows related to a specific connection, upon user request.
  *@param connectionName is the connection whose history records should be removed.
  */
  public void cleanUpHistoryData(String connectionName)
    throws ManifoldCFException;
  
  /** Delete history rows older than a specified timestamp.
  *@param timeCutoff is the timestamp to delete older rows before.
  */
  public void cleanUpHistoryData(long timeCutoff)
    throws ManifoldCFException;

  // Activities the Connector Framework records

  /** Start a job */
  public static final String ACTIVITY_JOBSTART = "job start";
  /** Finish a job */
  public static final String ACTIVITY_JOBEND = "job end";
  /** Stop a job */
  public static final String ACTIVITY_JOBSTOP = "job stop";
  /** Continue a job */
  public static final String ACTIVITY_JOBCONTINUE = "job continue";
  /** Wait due to schedule */
  public static final String ACTIVITY_JOBWAIT = "job wait";
  /** Unwait due to schedule */
  public static final String ACTIVITY_JOBUNWAIT = "job unwait";
  
  /** The set of activity records. */
  public static final String[] activitySet = new String[]
  {
    ACTIVITY_JOBSTART,
    ACTIVITY_JOBSTOP,
    ACTIVITY_JOBCONTINUE,
    ACTIVITY_JOBWAIT,
    ACTIVITY_JOBUNWAIT,
    ACTIVITY_JOBEND
  };

  /** Record time-stamped information about the activity of the connection.  This information can originate from
  * either the connector or from the framework.  The reason it is here is that it is viewed as 'belonging' to an
  * individual connection, and is segregated accordingly.
  *@param connectionName is the connection to which the record belongs.  If the connection is deleted, the
  * corresponding records will also be deleted.  Cannot be null.
  *@param startTime is either null or the time since the start of epoch in milliseconds (Jan 1, 1970).  Every
  *       activity has an associated time; the startTime field records when the activity began.  A null value
  *       indicates that the start time and the finishing time are the same.
  *@param activityType is a string which is fully interpretable only in the context of the connector involved, which is
  *       used to categorize what kind of activity is being recorded.  For example, a web connector might record a
  *       "fetch document" activity, while the framework might record "ingest document", "job start", "job finish",
  *       "job abort", etc.  Cannot be null.
  *@param dataSize is the number of bytes of data involved in the activity, or null if not applicable.
  *@param entityIdentifier is a (possibly long) string which identifies the object involved in the history record.
  *       The interpretation of this field will differ from connector to connector.  May be null.
  *@param resultCode contains a terse description of the result of the activity.  The description is limited in
  *       size to 255 characters, and can be interpreted only in the context of the current connector.  May be null.
  *@param resultDescription is a (possibly long) human-readable string which adds detail, if required, to the result
  *       described in the resultCode field.  This field is not meant to be queried on.  May be null.
  *@param childIdentifiers is a set of child entity identifiers associated with this activity.  May be null.
  */
  public void recordHistory(String connectionName, Long startTime, String activityType, Long dataSize,
    String entityIdentifier, String resultCode, String resultDescription, String[] childIdentifiers)
    throws ManifoldCFException;

  /** Generate a report, listing the start time, elapsed time, result code and description, number of bytes, and entity identifier.
  * The records selected for this report are based on the filtering criteria object passed into this method.
  * The record order is based on the sorting criteria object passed into this method.
  * The resultset returned should have the following columns: "activity","starttime","elapsedtime","resultcode","resultdesc","bytes","identifier".
  *@param connectionName is the name of the connection.
  *@param criteria is the filtering criteria, which selects the records of interest.
  *@param sort is the sorting order, which can specify sort based on the result columns.
  *@param startRow is the first row to include (beginning with 0)
  *@param maxRowCount is the maximum number of rows to include.
  */
  public IResultSet genHistorySimple(String connectionName, FilterCriteria criteria, SortOrder sort, int startRow, int maxRowCount)
    throws ManifoldCFException;

  /** Count the number of rows specified by a given set of criteria.  This can be used to make decisions
  * as to whether a query based on those rows will complete in an acceptable amount of time.
  *@param connectionName is the name of the connection.
  *@param criteria is the filtering criteria, which selects the records of interest.
  *@return the number of rows included by the criteria.
  */
  public long countHistoryRows(String connectionName, FilterCriteria criteria)
    throws ManifoldCFException;

  /** Get the maximum number of rows a window-based report can work with.
  *@return the maximum rows.
  */
  public long getMaxRows()
    throws ManifoldCFException;
    
  /** Generate a report, listing the start time, activity count, and identifier bucket, given
  * a time slice (interval) size.
  * The records selected for this report are based on the filtering criteria object passed into this method.
  * The record order is based on the sorting criteria object passed into this method.
  * The identifier bucket description is specified by the bucket description object.
  * The resultset returned should have the following columns: "starttime","endtime","activitycount","idbucket".
  *@param connectionName is the name of the connection.
  *@param criteria is the filtering criteria, which selects the records of interest.
  *@param sort is the sorting order, which can specify sort based on the result columns.
  *@param idBucket is the description of the bucket based on processed entity identifiers.
  *@param interval is the time interval, in milliseconds, to locate.  There will be one row in the resultset
  *       for each distinct idBucket value, and the returned activity count will the maximum found over the
  *       specified interval size.
  *@param startRow is the first row to include (beginning with 0)
  *@param maxRowCount is the maximum number of rows to include.
  */
  public IResultSet genHistoryActivityCount(String connectionName, FilterCriteria criteria, SortOrder sort, BucketDescription idBucket,
    long interval, int startRow, int maxRowCount)
    throws ManifoldCFException;

  /** Generate a report, listing the start time, bytes processed, and identifier bucket, given
  * a time slice (interval) size.
  * The records selected for this report are based on the filtering criteria object passed into this method.
  * The record order is based on the sorting criteria object passed into this method.
  * The identifier bucket description is specified by the bucket description object.
  * The resultset returned should have the following columns: "starttime","endtime","bytecount","idbucket".
  *@param connectionName is the name of the connection.
  *@param criteria is the filtering criteria, which selects the records of interest.
  *@param sort is the sorting order, which can specify sort based on the result columns.
  *@param idBucket is the description of the bucket based on processed entity identifiers.
  *@param interval is the time interval, in milliseconds, to locate.  There will be one row in the resultset
  *       for each distinct idBucket value, and the returned activity count will the maximum found over the
  *       specified interval size.
  *@param startRow is the first row to include (beginning with 0)
  *@param maxRowCount is the maximum number of rows to include.
  */
  public IResultSet genHistoryByteCount(String connectionName, FilterCriteria criteria, SortOrder sort, BucketDescription idBucket,
    long interval, int startRow, int maxRowCount)
    throws ManifoldCFException;


  /** Generate a report, listing the result bucket and identifier bucket.
  * The records selected for this report are based on the filtering criteria object passed into this method.
  * The record order is based on the sorting criteria object passed into this method.
  * The result code bucket description is specified by a bucket description object.
  * The identifier bucket description is specified by a bucket description object.
  * The resultset returned should have the following columns: "eventcount","resultcodebucket","idbucket".
  *@param connectionName is the name of the connection.
  *@param criteria is the filtering criteria, which selects the records of interest.
  *@param sort is the sorting order, which can specify sort based on the result columns.
  *@param resultCodeBucket is the description of the bucket based on processed result codes.
  *@param idBucket is the description of the bucket based on processed entity identifiers.
  *@param startRow is the first row to include (beginning with 0)
  *@param maxRowCount is the maximum number of rows to include.
  */
  public IResultSet genHistoryResultCodes(String connectionName, FilterCriteria criteria, SortOrder sort,
    BucketDescription resultCodeBucket, BucketDescription idBucket, int startRow, int maxRowCount)
    throws ManifoldCFException;


}
