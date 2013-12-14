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
package org.apache.manifoldcf.core.interfaces;

import java.util.*;

/** An IConnectionThrottler object is not thread-local.  It gates connection
* creation.
*/
public interface IConnectionThrottler
{
  public static final String _rcsid = "@(#)$Id$";

  /** Get permission to use a connection, which is described by the passed array of bin names.
  * This method may block until a connection slot is available.
  * The connection can be used multiple times until the releaseConnectionPermission() method is called.
  * This persistence feature is meant to allow connections to be pooled locally by the caller.
  *@return the fetch throttler to use when performing fetches from the corresponding connection, or null if the system is being shut down.
  */
  public IFetchThrottler obtainConnectionPermission()
    throws InterruptedException;
  
  /** Determine whether to release a pooled connection.  This method returns the number of bins
  * where the outstanding connection exceeds current quotas, indicating whether at least one with the specified
  * characteristics should be released.
  * NOTE WELL: This method cannot judge which is the best connection to be released to meet
  * quotas.  The caller needs to do that based on the highest number of bins matched.
  *@return the number of bins that are over quota, or zero if none of them are.  Returns Integer.MAX_VALUE if shutting down.
  */
  public int overConnectionQuotaCount();
  
  /** Release permission to use one connection. This presumes that obtainConnectionPermission()
  * was called earlier by someone and was successful.
  */
  public void releaseConnectionPermission();
  
}
