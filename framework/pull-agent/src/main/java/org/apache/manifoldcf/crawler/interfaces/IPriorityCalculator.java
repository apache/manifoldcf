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
import org.apache.manifoldcf.agents.interfaces.*;

/** This interface represents an object that calculates a document priority
* value, for inclusion in the jobqueue table.  One of these objects is passed in
* lieu of a document priority for every document being added to the table.
*/
public interface IPriorityCalculator
{
  public static final String _rcsid = "@(#)$Id$";

  /** Compute the document priority.  This MUST be called from within a
  * a retry-able database transaction!!
  *@return the document priority.
  */
  public double getDocumentPriority()
    throws ManifoldCFException;
  
}
