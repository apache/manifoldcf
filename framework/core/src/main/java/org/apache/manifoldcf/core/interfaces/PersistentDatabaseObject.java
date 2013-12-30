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

/** Objects derived from this class can function as database parameters or as results.  In
* both cases, they must be managed specially because they are potentially backed by disk files,
* and the data within is treated as a stream (of something) rather than a scalar piece of data.
*/
public abstract class PersistentDatabaseObject
{
  public static final String _rcsid = "@(#)$Id$";

  /** Construct from nothing.
  */
  public PersistentDatabaseObject()
  {
  }

  /** Close any open streams, but do NOT remove the backing object.
  * Thus the stream can be reopened in the future. */
  public abstract void doneWithStream()
    throws ManifoldCFException;
  
  /** Discard this object permanently */
  public abstract void discard()
    throws ManifoldCFException;

}
