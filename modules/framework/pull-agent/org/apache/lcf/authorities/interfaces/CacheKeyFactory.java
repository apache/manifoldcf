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
package org.apache.lcf.authorities.interfaces;

/** This class just represents a central place where cache keys are assembled.
* All methods are static.
*/
public class CacheKeyFactory extends org.apache.lcf.core.interfaces.CacheKeyFactory
{

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

}
