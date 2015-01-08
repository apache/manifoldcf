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
package org.apache.manifoldcf.agents.incrementalingest;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.agents.system.Logging;
import org.apache.manifoldcf.agents.system.ManifoldCF;
import java.util.*;
import java.io.*;

/** This class accepts a RepositoryDocument in its constructor, and then
* allows multiple copies to me made, as part of a split in the pipeline.
* It must be closed in order to release all temporary resources.
*/
public class RepositoryDocumentFactory
{
  
  // The objects we need to track are:
  // (1) The binary stream
  // (2) All metadata values that are Readers
  // Everything else can be pulled out of the original RepositoryDocument
  
  protected final RepositoryDocument original;

  // The binary stream file and stream (if any)
  protected BinaryInput binaryTracker;
  
  // Readers (organized by metadata)
  protected final Map<String,CharacterInput[]> metadataReaders = new HashMap<String,CharacterInput[]>();
  
  /** Constructor.
  * Pass a RepositoryDocument.  This constructor reads all streams and stores them in
  * a temporary local location. 
  * If there is an error reading the streams, an IOException will be thrown.  Otherwise,
  * a ManifoldCFException will be thrown instead.
  *@param document is the repository document to read.
  */
  public RepositoryDocumentFactory(RepositoryDocument document)
    throws ManifoldCFException, IOException
  {
    this.original = document;
    try
    {
      this.binaryTracker = new TempFileInput(document.getBinaryStream());
      // Copy all reader streams
      Iterator<String> iter = document.getFields();
      while (iter.hasNext())
      {
        String fieldName = iter.next();
        Object[] objects = document.getField(fieldName);
        if (objects instanceof Reader[])
        {
          CharacterInput[] newValues = new CharacterInput[objects.length];
          metadataReaders.put(fieldName,newValues);
          // Populate newValues
          for (int i = 0; i < newValues.length; i++)
          {
            newValues[i] = new TempFileCharacterInput((Reader)objects[i]);
          }
        }
      }
    }
    catch (Throwable e)
    {
      // Clean up everything we've done so far.
      if (this.binaryTracker != null)
        this.binaryTracker.discard();
      for (String key : metadataReaders.keySet())
      {
        CharacterInput[] rt = metadataReaders.get(key);
        for (CharacterInput r : rt)
        {
          if (r != null)
            r.discard();
        }
      }
      if (e instanceof IOException)
        throw (IOException)e;
      else if (e instanceof RuntimeException)
        throw (RuntimeException)e;
      else if (e instanceof Error)
        throw (Error)e;
      else
        throw new RuntimeException("Unknown exception type: "+e.getClass().getName()+": "+e.getMessage(),e);
    }
  }
  
  /** Create a new RepositoryDocument object from the saved local resources.
  * As a side effect, this method also releases any resources held on behalf of the previously
  * created RepositoryDocument.
  *@return a repository document object.
  */
  public RepositoryDocument createDocument()
    throws ManifoldCFException
  {
    RepositoryDocument rd = new RepositoryDocument();
    
    // Copy scalar values
    rd.setCreatedDate(original.getCreatedDate());
    rd.setModifiedDate(original.getModifiedDate());
    rd.setIndexingDate(original.getIndexingDate());
    rd.setMimeType(original.getMimeType());
    rd.setFileName(original.getFileName());
    
    Iterator<String> securityTypes = original.securityTypesIterator();
    while (securityTypes.hasNext())
    {
      String securityType = securityTypes.next();
      rd.setSecurityACL(securityType,original.getSecurityACL(securityType));
      rd.setSecurityDenyACL(securityType,original.getSecurityDenyACL(securityType));
    }
    
    // Copy binary
    binaryTracker.doneWithStream();
    rd.setBinary(binaryTracker.getStream(),original.getBinaryLength());
    // Copy metadata fields (including minting new Readers where needed)
    Iterator<String> iter = original.getFields();
    while (iter.hasNext())
    {
      String fieldName = iter.next();
      Object[] objects = original.getField(fieldName);
      if (objects instanceof Reader[])
      {
        CharacterInput[] rts = metadataReaders.get(fieldName);
        Reader[] newReaders = new Reader[rts.length];
        for (int i = 0; i < rts.length; i++)
        {
          rts[i].doneWithStream();
          newReaders[i] = rts[i].getStream();
        }
        rd.addField(fieldName,newReaders);
      }
      else if (objects instanceof Date[])
      {
        rd.addField(fieldName,(Date[])objects);
      }
      else if (objects instanceof String[])
      {
        rd.addField(fieldName,(String[])objects);
      }
      else
        throw new RuntimeException("Unknown kind of metadata: "+objects.getClass().getName());
    }

    return rd;
  }
  
  /** Close this object and release its resources.
  */
  public void close()
    throws ManifoldCFException
  {
    binaryTracker.discard();
    for (String key : metadataReaders.keySet())
    {
      CharacterInput[] rt = metadataReaders.get(key);
      for (CharacterInput r : rt)
      {
        r.discard();
      }
    }
  }
  
}
