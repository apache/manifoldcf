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

/** An instance of this interface authorizes a user to perform various capabilities within MCF,
* within the local thread scope.
*/
public interface IAuthorizer
{
  
  // User capabilities
  /** View connections */
  public final static int CAPABILITY_VIEW_CONNECTIONS = IAuth.CAPABILITY_VIEW_CONNECTIONS;
  /** View jobs */
  public final static int CAPABILITY_VIEW_JOBS = IAuth.CAPABILITY_VIEW_JOBS;
  /** View reports */
  public final static int CAPABILITY_VIEW_REPORTS = IAuth.CAPABILITY_VIEW_REPORTS;
  /** Edit connections */
  public final static int CAPABILITY_EDIT_CONNECTIONS = IAuth.CAPABILITY_EDIT_CONNECTIONS;
  /** Edit jobs */
  public final static int CAPABILITY_EDIT_JOBS = IAuth.CAPABILITY_EDIT_JOBS;
  /** Run jobs */
  public final static int CAPABILITY_RUN_JOBS = IAuth.CAPABILITY_RUN_JOBS;
  
  /** Check user capability */
  public boolean checkAllowed(final IThreadContext threadContext, final int capability)
    throws ManifoldCFException;
  
}