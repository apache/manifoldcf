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

/** An instance of this interface describes how to authorize various components
* of the ManifoldCF system.
*/
public interface IAuth
{
  
  // User capabilities
  
  /** View connections */
  public final static int CAPABILITY_VIEW_CONNECTIONS = 1;
  /** View jobs */
  public final static int CAPABILITY_VIEW_JOBS = 2;
  /** View reports */
  public final static int CAPABILITY_VIEW_REPORTS = 3;
  /** Edit connections */
  public final static int CAPABILITY_EDIT_CONNECTIONS = 4;
  /** Edit jobs */
  public final static int CAPABILITY_EDIT_JOBS = 5;
  /** Run jobs */
  public final static int CAPABILITY_RUN_JOBS = 6;
  
  /** Verify UI login */
  public boolean verifyUILogin(final String userId, final String password)
    throws ManifoldCFException;

  /** Verify API login */
  public boolean verifyAPILogin(final String userId, final String password)
    throws ManifoldCFException;
	
  /** Check user capability */
  public boolean checkCapability(final String userId, final int capability)
    throws ManifoldCFException;
  
}