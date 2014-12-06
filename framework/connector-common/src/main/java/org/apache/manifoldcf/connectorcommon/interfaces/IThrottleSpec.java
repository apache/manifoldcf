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
package org.apache.manifoldcf.connectorcommon.interfaces;


/** An IThrottleSpec object describes what throttling criteria to apply
* per bin.
*/
public interface IThrottleSpec
{
  public static final String _rcsid = "@(#)$Id$";

  /** Given a bin name, find the max open connections to use for that bin.
  *@return Integer.MAX_VALUE if no limit found.
  */
  public int getMaxOpenConnections(String binName);

  /** Look up minimum milliseconds per byte for a bin.
  *@return 0.0 if no limit found.
  */
  public double getMinimumMillisecondsPerByte(String binName);

  /** Look up minimum milliseconds for a fetch for a bin.
  *@return 0 if no limit found.
  */
  public long getMinimumMillisecondsPerFetch(String binName);

}
