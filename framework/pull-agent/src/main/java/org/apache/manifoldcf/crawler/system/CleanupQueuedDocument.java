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

/** This class represents a document that will be placed on the document cleanup queue, and will be
* processed by a cleanup worker thread.
*/
public class CleanupQueuedDocument extends DeleteQueuedDocument
{
  public static final String _rcsid = "@(#)$Id$";

  /** Flag indicating whether we should delete the document from the index or not. */
  protected boolean deleteFromIndex;

  /** Constructor.
  *@param documentDescription is the document description.
  */
  public CleanupQueuedDocument(DocumentDescription documentDescription, boolean deleteFromIndex)
  {
    super(documentDescription);
    this.deleteFromIndex = deleteFromIndex;
  }

  /** Check if document should be removed from the index.
  *@return true if it should be removed.
  */
  public boolean shouldBeRemovedFromIndex()
  {
    return deleteFromIndex;
  }

}

