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
package org.apache.manifoldcf.crawler.system;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;

/** This class calculates a document priority given all the required inputs.
* It is not thread safe, but calls classes that are (e.g. QueueTracker).
*/
public class PriorityCalculator implements IPriorityCalculator
{
  public static final String _rcsid = "@(#)$Id$";

  protected final QueueTracker queueTracker;
  protected final IRepositoryConnection connection;
  protected final String[] documentBins;
  protected final IBinManager binManager;
  
  /** Constructor. */
  public PriorityCalculator(QueueTracker queueTracker, IRepositoryConnection connection, String[] documentBins, IBinManager binManager)
  {
    this.queueTracker = queueTracker;
    this.connection = connection;
    this.documentBins = documentBins;
    this.binManager = binManager;
  }

  /** Compute the document priority given an actual bincounter value.
  *@return the document priority.
  */
  @Override
  public double getDocumentPriority()
    throws ManifoldCFException
  {
    // MHL to use database bin tables
    return queueTracker.calculatePriority(documentBins,connection);
  }

}

