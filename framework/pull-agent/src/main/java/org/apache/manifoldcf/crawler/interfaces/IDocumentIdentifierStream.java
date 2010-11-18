/* $Id: IDocumentIdentifierStream.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** Implement this interface to return a stream of local document identifiers.  These will be the result
* of an initial query, and should be unchanged by subsequent changes to the underlying data store.
*
* The semantics of calling this interface are much like using a stream: identifiers are pulled off
* one-by-one, and the stream will be explicitly closed when no longer needed.  The purpose for the
* existence of this stream is also similar to other I/O streams: to avoid overusing main memory for
* buffers.
*/
public interface IDocumentIdentifierStream
{
  public static final String _rcsid = "@(#)$Id: IDocumentIdentifierStream.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Get the next local document identifier.
  *@return the next document identifier, or null if there are no more.
  */
  public String getNextIdentifier()
    throws ManifoldCFException, ServiceInterruption;

  /** Close the stream.
  */
  public void close()
    throws ManifoldCFException;

}
