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

/** An IConnectionThrottler object is meant to be embedded in an InputStream.  It is not
* thread-local, and does not require access to a thread context.  It thus also does not
* throw ManifoldCFExceptions.  It is thus suitable for use in background threads, etc.
* These objects are typically created by IFetchThrottler objects - they are not meant
* to be created directly.
*/
public interface IStreamThrottler
{
  public static final String _rcsid = "@(#)$Id$";

  /** Obtain permission to read a block of bytes.  This method may wait until it is OK to proceed.
  * The throttle group, bin names, etc are already known
  * to this specific interface object, so it is unnecessary to include them here.
  *@param byteCount is the number of bytes to get permissions to read.
  *@return true if the wait took place as planned, or false if the system is being shut down.
  */
  public boolean obtainReadPermission(int byteCount)
    throws InterruptedException;

  /** Obtain permission to read a block of bytes.  This method may wait until it is OK to proceed.
  * The throttle group, bin names, etc are already known
  * to this specific interface object, so it is unnecessary to include them here.
  *@param byteCount is the number of bytes to get permissions to read.
  *@return true if the wait took place as planned, or false if the system is being shut down.
  */
  public boolean obtainReadPermission(int byteCount, IBreakCheck breakCheck)
    throws InterruptedException, BreakException;
  
  /** Note the completion of the read of a block of bytes.  Call this after
  * obtainReadPermission() was successfully called, and bytes were successfully read.
  *@param origByteCount is the originally requested number of bytes to get permissions to read.
  *@param actualByteCount is the number of bytes actually read.
  */
  public void releaseReadPermission(int origByteCount, int actualByteCount);
  
  /** Note the stream being closed.
  */
  public void closeStream();
}
