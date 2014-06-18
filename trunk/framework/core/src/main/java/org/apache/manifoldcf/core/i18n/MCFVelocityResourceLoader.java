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
package org.apache.manifoldcf.core.i18n;

import java.io.*;

/** Our own Velocity resource loader, which uses our class resolution to find Velocity template resources.
*/
public class MCFVelocityResourceLoader extends org.apache.velocity.runtime.resource.loader.ResourceLoader
{
  protected Class classInstance;
  
  /** Constructor.
  */
  public MCFVelocityResourceLoader(Class classInstance)
  {
    this.classInstance = classInstance;
  }

  public long getLastModified(org.apache.velocity.runtime.resource.Resource resource)
  {
    return 0L;
  }
  
  public InputStream getResourceStream(String source)
    throws org.apache.velocity.exception.ResourceNotFoundException
  {
    InputStream rval = classInstance.getResourceAsStream(source);
    if (rval == null)
      throw new org.apache.velocity.exception.ResourceNotFoundException("Resource '"+source+"' not found.");
    return rval;
  }

  public void init(org.apache.commons.collections.ExtendedProperties configuration)
  {
    // Does nothing
  }

  public boolean isSourceModified(org.apache.velocity.runtime.resource.Resource resource)
  {
    // This obviously supports caching, which we don't need and may mess us up if the caching is cross-instance
    return true;
  }

}