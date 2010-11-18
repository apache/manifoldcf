/* $Id: BlockingDocuments.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import org.apache.manifoldcf.crawler.system.Logging;
import java.util.*;

/** An instance of this class keeps track of a pool of documents that the stuffer thread believes are ready to be reprioritized.
* The way it works is that when the stuffer sees documents that it can't use, but which it thinks are eligible from reprioritization,
* it hands them to this object.  The prioritization thread then takes documents out of this queue and prioritizes them, before it
* goes looking for other documents.  This process guarantees that the stuffer is not blocked by documents that need reprioritization.
*/
public class BlockingDocuments
{
  public static final String _rcsid = "@(#)$Id: BlockingDocuments.java 988245 2010-08-23 18:39:35Z kwright $";

  // It's a simple queue.  This is the set of DocumentDescription objects that have been placed here by the stuffer.
  // Since there is a chance that the same document will be entered more than once, it's stored as a hash.
  protected HashMap docsInNeed = new HashMap();

  /** Constructor */
  public BlockingDocuments()
  {
  }

  /** Add a document to the set */
  public synchronized void addBlockingDocument(DocumentDescription dd)
  {
    docsInNeed.put(dd.getID(),dd);
  }

  /** Pop a document from the set.
  *@return null if there are no remaining documents.
  */
  public synchronized DocumentDescription getBlockingDocument()
  {
    if (docsInNeed.size() == 0)
      return null;
    Iterator iter = docsInNeed.keySet().iterator();
    return (DocumentDescription)docsInNeed.remove((Long)iter.next());
  }

}
