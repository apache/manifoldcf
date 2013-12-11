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
import org.apache.manifoldcf.crawler.interfaces.*;
import java.io.*;
import java.util.*;

/** Class which handles reset for cleanup thread pool (of which there's
* typically only one member).  The reset action here
* is to move the status of documents from "???" back to "PURGATORY".
*/
public class DocCleanupResetManager extends ResetManager
{
  public static final String _rcsid = "@(#)$Id$";

  protected final DocumentCleanupQueue ddq;

  /** Constructor. */
  public DocCleanupResetManager(DocumentCleanupQueue ddq, String processID)
  {
    super(processID);
    this.ddq = ddq;
  }

  /** Reset */
  @Override
  protected void performResetLogic(IThreadContext tc, String processID)
    throws ManifoldCFException
  {
    IJobManager jobManager = JobManagerFactory.make(tc);
    jobManager.resetDocCleanupWorkerStatus(processID);
    ddq.clear();
  }
  
  /** Do the wakeup logic.
  */
  @Override
  protected void performWakeupLogic()
  {
    ddq.reset();
  }

}
