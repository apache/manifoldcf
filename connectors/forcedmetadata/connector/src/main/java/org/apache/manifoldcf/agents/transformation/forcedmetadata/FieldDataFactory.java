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
package org.apache.manifoldcf.agents.transformation.forcedmetadata;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;

import java.io.*;
import java.util.*;

/** This class provides unique Reader and other field instances, when requested, based
* on an input RepositoryDocument.  It does this by pulling the values of the field into
* a CharacterInput implementation, thus making a temporary file copy.  So it is imperative
* that this object is closed when it is no longer needed.
*/
public class FieldDataFactory {

  protected final RepositoryDocument sourceDocument;

  // Readers (organized by metadata)
  protected final Map<String,CharacterInput[]> metadataReaders = new HashMap<String,CharacterInput[]>();

  public FieldDataFactory(RepositoryDocument sourceDocument) {
    this.sourceDocument = sourceDocument;
  }

  public void close()
    throws ManifoldCFException {
    for (String key : metadataReaders.keySet()) {
      CharacterInput[] rt = metadataReaders.get(key);
      for (CharacterInput r : rt) {
        r.discard();
      }
    }
  }

  public Object[] getField(String fieldName)
    throws IOException, ManifoldCFException {
    CharacterInput[] inputs = metadataReaders.get(fieldName);
    if (inputs == null) {
      // Either never seen the field before, or it's not a Reader
      Object[] fieldValues = sourceDocument.getField(fieldName);
      if (fieldValues == null)
        return fieldValues;
      if (fieldValues instanceof Reader[]) {
        // Create a copy
        CharacterInput[] newValues = new CharacterInput[fieldValues.length];
        try {
          // Populate newValues
          for (int i = 0; i < newValues.length; i++) {
            newValues[i] = new TempFileCharacterInput((Reader)fieldValues[i]);
          }
          metadataReaders.put(fieldName,newValues);
          inputs = newValues;
        } catch (Throwable e) {
          for (CharacterInput r : newValues)
          {
            if (r != null)
              r.discard();
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
      } else {
        return fieldValues;
      }
    }

    Reader[] newReaders = new Reader[inputs.length];
    for (int i = 0; i < inputs.length; i++)
    {
      inputs[i].doneWithStream();
      newReaders[i] = inputs[i].getStream();
    }
    return newReaders;
  }

  public String[] getFieldAsStrings(String fieldName)
    throws IOException, ManifoldCFException {
    CharacterInput[] cilist = metadataReaders.get(fieldName);
    if (cilist == null)
      return sourceDocument.getFieldAsStrings(fieldName);

    // We've created a local array of CharacterInputs from this field.  We'll need to convert these
    // to strings.
    char[] buffer = new char[65536];
    String[] rval = new String[cilist.length];
    for (int i = 0; i < rval.length; i++) {
      CharacterInput ci = cilist[i];
      ci.doneWithStream();
      Reader r = ci.getStream();
      // Read into a buffer
      StringBuilder newValue = new StringBuilder();
      while (true)
      {
        int amt = r.read(buffer);
        if (amt == -1)
          break;
        newValue.append(buffer,0,amt);
      }
      rval[i] = newValue.toString();
    }
    sourceDocument.addField(fieldName,rval);
    metadataReaders.remove(fieldName);
    for (CharacterInput ci : cilist) {
      ci.discard();
    }
    return rval;
  }
}

