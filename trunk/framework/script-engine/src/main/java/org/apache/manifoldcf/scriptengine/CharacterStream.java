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

package org.apache.manifoldcf.scriptengine;

import java.io.*;

/** Convert a Reader into a sequence of characters, while keeping track of line
* endings.
*/
public class CharacterStream
{
  protected Reader reader;
  protected int currentCharacter = -1;
  protected int lineNumber = 0;
  protected int characterNumber = 0;
  
  public CharacterStream(Reader reader)
  {
    this.reader = reader;
  }
  
  public int peek()
    throws ScriptException
  {
    if (currentCharacter == -1)
      currentCharacter = readNextCharacter();
    return currentCharacter;
  }
  
  public void skip()
  {
    currentCharacter = -1;
  }
  
  public int getLineNumber()
  {
    return lineNumber;
  }
  
  public int getCharacterNumber()
  {
    return characterNumber;
  }
  
  // Protected methods
  
  protected int readNextCharacter()
    throws ScriptException
  {
    if (reader == null)
      return -1;
    
    try
    {
      int rval = reader.read();
      if (rval == -1)
      {
        reader.close();
        reader = null;
        return -1;
      }
      characterNumber++;
      if (((char)rval) == '\n')
      {
        lineNumber++;
        characterNumber = 0;
      }
      return rval;
    }
    catch (IOException e)
    {
      throw new ScriptException("I/O exception: "+e.getMessage(),e);
    }
  }
  
}
