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
package org.apache.manifoldcf.crawler.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import java.util.*;

/** This interface describes functionality designed to allow retrieval of existing
* version information from previous crawls.  It is part of the IRepositoryConnector API.
*/
public interface IExistingVersions
{
  public static final String _rcsid = "@(#)$Id$";

  /** Retrieve the primary existing version string given a document identifier.
  *@param documentIdentifier is the document identifier.
  *@return the document version string, or null if the document was never previously indexed.
  */
  public String getIndexedVersionString(String documentIdentifier)
    throws ManifoldCFException;

  /** Retrieve a component existing version string given a document identifier.
  *@param documentIdentifier is the document identifier.
  *@param componentIdentifier is the component identifier, if any.
  *@return the document version string, or null of the document component was never previously indexed.
  */
  public String getIndexedVersionString(String documentIdentifier, String componentIdentifier)
    throws ManifoldCFException;
  
}
