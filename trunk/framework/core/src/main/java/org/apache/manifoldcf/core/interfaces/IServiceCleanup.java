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


/** The IServiceCleanup interface describes functionality needed to clean up after
* a service that has ended, as determined by an ILockManager instance.  It is always
* throttled in a manner where only one thread in
* the entire cluster will be cleaning up after any specific service.
*/
public interface IServiceCleanup
{
  public static final String _rcsid = "@(#)$Id$";

  /** Clean up after the specified service.  This method will block any startup of the specified
  * service for as long as it runs.
  *@param serviceName is the name of the service.
  */
  public void cleanUpService(String serviceName)
    throws ManifoldCFException;

  /** Clean up after ALL services of the type on the cluster.
  */
  public void cleanUpAllServices()
    throws ManifoldCFException;
  
  /** Perform cluster initialization - that is, whatever is needed presuming that the
  * cluster has been down for an indeterminate period of time, but is otherwise in a clean
  * state.
  */
  public void clusterInit()
    throws ManifoldCFException;

}
