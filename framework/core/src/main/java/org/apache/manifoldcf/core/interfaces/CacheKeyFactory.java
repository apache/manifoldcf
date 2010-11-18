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
package org.apache.manifoldcf.core.interfaces;

/** This class just represents a central place where cache keys are assembled.
* All methods are static.
*/
public class CacheKeyFactory
{
  public static final String _rcsid = "@(#)$Id: CacheKeyFactory.java 988245 2010-08-23 18:39:35Z kwright $";

  private static final String DATABASE = "DB-";
  private static final String DASH = "-";
  private static final String TABLE = "TBL-";


  protected CacheKeyFactory()
  {
  }

  /** Construct a key that is database specific.
  * Typically, this
  * method is not called directly; it gets invoked by other key construction
  * methods.
  * Overall, it will be called last in a compositional sequence that looks
  * conceptually like this:
  * makeDatabaseKey(makeTableKey(makeThingsTableTypeKey(typeX),"Things"),databaseName);
  *@param keyName is the input keyname
  *@param databaseName is the database name
  *@return the qualified key name
  */
  public static String makeDatabaseKey(String keyName, String databaseName)
  {
    String rval;
    if (keyName == null)
      rval = DATABASE+databaseName;
    else
      rval = DATABASE+databaseName+DASH+keyName;
    return rval;
  }

  /** Construct a key that is database specific, and applies to queries
  * made against a specific table name.
  */
  public static String makeTableKey(String keyName, String tableName, String databaseName)
  {
    String rval;
    if (keyName == null)
      rval = TABLE+tableName;
    else
      rval = TABLE+tableName+DASH+keyName;
    return makeDatabaseKey(rval,databaseName);
  }


  /** Construct a general database cache key.
  *@param databaseName is the database name.
  *@return the cache key.
  */
  public static String makeDatabaseKey(String databaseName)
  {
    return makeDatabaseKey("GENERAL",databaseName);
  }
}
