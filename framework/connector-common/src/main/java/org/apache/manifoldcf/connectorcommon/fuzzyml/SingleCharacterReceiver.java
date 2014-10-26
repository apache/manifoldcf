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
package org.apache.manifoldcf.connectorcommon.fuzzyml;

import org.apache.manifoldcf.core.interfaces.*;
import java.io.*;

/** This interface represents a receiver for a sequence of individual characters.
*/
public abstract class SingleCharacterReceiver extends CharacterReceiver
{
  protected final char[] charBuffer;
  
  /** Constructor.
  */
  public SingleCharacterReceiver(int chunkSize)
  {
    charBuffer = new char[chunkSize];
  }
  
  /** Receive a stream of characters.
  *@return true if abort signalled, false if end of stream.
  */
  @Override
  public final boolean dealWithCharacters(Reader reader)
    throws IOException, ManifoldCFException
  {
    while (true)
    {
      int amt = reader.read(charBuffer);
      if (amt == -1)
        return false;
      for (int i = 0; i < amt; i++)
      {
        if (dealWithCharacter(charBuffer[i]))
        {
          return dealWithRemainder(charBuffer, i+1, amt-(i+1), reader);
        }
      }
    }
  }
  
  /** Receive a byte.
  * @return true if done.
  */
  public abstract boolean dealWithCharacter(char c)
    throws IOException, ManifoldCFException;
  
  /** Deal with the remainder of the input.
  * This is called only when dealWithCharacter() returns true.
  *@param buffer is the buffer of characters that should come first.
  *@param offset is the offset within the buffer of the first character.
  *@param len is the number of characters in the buffer.
  *@param inputStream is the stream that should come after the characters in the buffer.
  *@return true to abort, false if the end of the stream has been reached.
  */
  protected boolean dealWithRemainder(char[] buffer, int offset, int len, Reader reader)
    throws IOException, ManifoldCFException
  {
    return true;
  }

}
