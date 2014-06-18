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
package org.apache.manifoldcf.authorities.interfaces;

import org.apache.manifoldcf.core.interfaces.*;

/** This interface describes the authorization domain registry.  Authorization domains are registered here, so that
* they can be made available when an authority connection is created.
*/
public interface IAuthorizationDomainManager
{
  /** Install.
  */
  public void install()
    throws ManifoldCFException;

  /** Uninstall.
  */
  public void deinstall()
    throws ManifoldCFException;

  /** Register a new domain.
  *@param description is the description to use in the UI.
  *@param domainName is the internal domain name used by the authority service.
  */
  public void registerDomain(String description, String domainName)
    throws ManifoldCFException;

  /** Unregister a domain.
  * This may fail if any authority connections refer to the domain.
  *@param domainName is the internal domain name to unregister.
  */
  public void unregisterDomain(String domainName)
    throws ManifoldCFException;

  /** Get ordered list of domains.
  *@return a resultset with the columns "description" and "domainname".
  * These will be ordered by description.
  */
  public IResultSet getDomains()
    throws ManifoldCFException;

  /** Get a description given a domain name.
  *@param domainName is the domain name.
  *@return the description, or null if the domain is not registered.
  */
  public String getDescription(String domainName)
    throws ManifoldCFException;

}
