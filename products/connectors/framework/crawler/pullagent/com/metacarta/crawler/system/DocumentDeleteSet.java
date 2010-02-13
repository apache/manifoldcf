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
package com.metacarta.crawler.system;

import com.metacarta.core.interfaces.*;
import com.metacarta.agents.interfaces.*;
import com.metacarta.crawler.interfaces.*;
import java.util.*;

/** This class is what's actually queued for delete threads.  It represents an array of DocumentDescription objects,
* of an appropriate size to be a decent chunk.  It will be processed by a single delete worker thread, in bulk.
*/
public class DocumentDeleteSet
{
        public static final String _rcsid = "@(#)$Id$";

        /** This is the array of documents to delete. */
        protected DeleteQueuedDocument[] documents;

        /** Constructor.
        *@param documents is the arraylist representing the documents for this chunk.
        */
        public DocumentDeleteSet(DeleteQueuedDocument[] documents)
        {
                this.documents = documents;
        }

        /** Get the number of documents.
        *@return the number.
        */
        public int getCount()
        {
                return documents.length;
        }

        /** Get the nth document.
        *@param index is the document number.
        *@return the document.
        */
        public DeleteQueuedDocument getDocument(int index)
        {
                return documents[index];
        }

}
