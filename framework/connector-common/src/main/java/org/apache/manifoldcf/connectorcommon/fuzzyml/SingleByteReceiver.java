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

/** This class represents a receiver for a series of single bytes.
*/
public abstract class SingleByteReceiver extends ByteReceiver
{
  protected final byte[] byteBuffer;
  
  /** Constructor */
  public SingleByteReceiver(int chunkSize)
  {
    byteBuffer = new byte[chunkSize];
  }
  
  /** Read a byte stream and process bytes.
  *@return true if abort signalled, false if end of stream reached.
  */
  @Override
  public final boolean dealWithBytes(InputStream inputStream)
    throws IOException, ManifoldCFException
  {
    while (true)
    {
      int amt = inputStream.read(byteBuffer);
      if (amt == -1)
        return false;
      for (int i = 0; i < amt; i++)
      {
        if (dealWithByte(byteBuffer[i]))
        {
          return dealWithRemainder(byteBuffer,i+1,amt-(i+1),inputStream);
        }
      }
    }
  }
  
  /** Receive a byte.
  *@return true to stop further processing.
  */
  public abstract boolean dealWithByte(byte b)
    throws IOException, ManifoldCFException;

  /** Deal with the remainder of the input.
  * This is called only when dealWithByte() returns true.
  *@param buffer is the buffer of characters that should come first.
  *@param offset is the offset within the buffer of the first character.
  *@param len is the number of characters in the buffer.
  *@param inputStream is the stream that should come after the characters in the buffer.
  *@return true to abort, false if the end of the stream has been reached.
  */
  protected boolean dealWithRemainder(byte[] buffer, int offset, int len, InputStream inputStream)
    throws IOException, ManifoldCFException
  {
    return true;
  }
  
}
