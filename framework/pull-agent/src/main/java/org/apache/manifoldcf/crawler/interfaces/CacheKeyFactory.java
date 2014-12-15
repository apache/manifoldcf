/* $Id: CacheKeyFactory.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class just represents a central place where cache keys are assembled.
* All methods are static.
*/
public class CacheKeyFactory extends org.apache.manifoldcf.agents.interfaces.CacheKeyFactory
{
  public static final String _rcsid = "@(#)$Id: CacheKeyFactory.java 988245 2010-08-23 18:39:35Z kwright $";

  protected CacheKeyFactory()
  {
  }

  /** Construct a key which represents the general list of authority connectors.
  *@return the cache key.
  */
  public static String makeAuthorityConnectionsKey()
  {
    return "AUTHORITYCONNECTIONS";
  }

  /** Construct a key which represents an individual authority connection.
  *@param connectionName is the name of the connection.
  *@return the cache key.
  */
  public static String makeAuthorityConnectionKey(String connectionName)
  {
    return "AUTHORITYCONNECTION_"+connectionName;
  }

  /** Construct a key which represents the general list of repository connectors.
  *@return the cache key.
  */
  public static String makeRepositoryConnectionsKey()
  {
    return "REPOSITORYCONNECTIONS";
  }

  /** Construct a key which represents an individual repository connection.
  *@param connectionName is the name of the connector.
  *@return the cache key.
  */
  public static String makeRepositoryConnectionKey(String connectionName)
  {
    return "REPOSITORYCONNECTION_"+connectionName;
  }

  /** Construct a key which represents the general list of notification connectors.
  *@return the cache key.
  */
  public static String makeNotificationConnectionsKey()
  {
    return "NOTIFICATIONCONNECTIONS";
  }

  /** Construct a key which represents an individual notification connection.
  *@param connectionName is the name of the connector.
  *@return the cache key.
  */
  public static String makeNotificationConnectionKey(String connectionName)
  {
    return "NOTIFICATIONCONNECTION_"+connectionName;
  }


  /** Construct a key which represents the general list of jobs - for queries
  * that depend on the fixed kind of job data, not the dynamic data (e.g. status)
  *@return the cache key.
  */
  public static String makeJobsKey()
  {
    return "JOBS";
  }

  /** Construct a key which represents the fixed kind of data for an individual job.
  *@param jobID is the job identifier.
  *@return the cache key.
  */
  public static String makeJobIDKey(String jobID)
  {
    return "JOB_"+jobID;
  }

  /** Construct a key which represents the collective statuses of all jobs.
  *@return the cache key.
  */
  public static String makeJobStatusKey()
  {
    return "JOBSTATUSES";
  }

}
