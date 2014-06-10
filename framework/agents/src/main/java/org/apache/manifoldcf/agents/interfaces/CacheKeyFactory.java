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
package org.apache.manifoldcf.agents.interfaces;

/** This class just represents a central place where cache keys are assembled.
* All methods are static.
*/
public class CacheKeyFactory extends org.apache.manifoldcf.core.interfaces.CacheKeyFactory
{
  public static final String _rcsid = "@(#)$Id: CacheKeyFactory.java 988245 2010-08-23 18:39:35Z kwright $";

  protected CacheKeyFactory()
  {
  }

  /** Construct a key which represents the general list of output connectors.
  *@return the cache key.
  */
  public static String makeOutputConnectionsKey()
  {
    return "OUTPUTCONNECTIONS";
  }

  /** Construct a key which represents an individual output connection.
  *@param connectionName is the name of the connector.
  *@return the cache key.
  */
  public static String makeOutputConnectionKey(String connectionName)
  {
    return "OUTPUTCONNECTION_"+connectionName;
  }

  /** Construct a key which represents the general list of transformation connectors.
  *@return the cache key.
  */
  public static String makeTransformationConnectionsKey()
  {
    return "TRANSCONNECTIONS";
  }

  /** Construct a key which represents an individual transformation connection.
  *@param connectionName is the name of the connector.
  *@return the cache key.
  */
  public static String makeTransformationConnectionKey(String connectionName)
  {
    return "TRANSCONNECTION_"+connectionName;
  }

}
