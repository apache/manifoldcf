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

/** An instance of this class represents a version string, in combination with the configuration parameters
* and specification that produced it.  Some clients will use the version string (e.g. the database), while others
* may find it more convenient to use the parameters or the specification.  However:
* (1) It is ALWAYS wrong to use data from configuration or specification that is NOT represented in some
*     way in the version string, either by exact representation, or by some proxy value;
* (2) Configuration and Specification are guaranteed to be the identical ones which were used during creation
*     of the version string;
* (3) Configuration and Specification are provided as CONVENIENCES; they are not to be considered primary
*    data for these objects.
*/
public class VersionContext
{
  public static final String _rcsid = "@(#)$Id$";

  // Member variables
  protected final String versionString;
  protected final ConfigParams params;
  protected final Specification specification;

  /** Constructor.
  */
  public VersionContext(String versionString, ConfigParams params, Specification specification)
  {
    this.versionString = versionString;
    this.params = params;
    this.specification = specification;
  }

  /** Retrieve the version String */
  public String getVersionString()
  {
    return versionString;
  }
  
  /** Retrieve the configuration parameters */
  public ConfigParams getParams()
  {
    return params;
  }
  
  /** Retrieve the specification */
  public Specification getSpecification()
  {
    return specification;
  }
  
}
