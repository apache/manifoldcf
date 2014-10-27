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
import java.util.*;

/** This class represents a variable-length buffer for bytes.
*/
public class ByteBuffer
{
  protected static final int arraySize = 65536;
  protected static final int arrayShift = 16;
  
  protected final List<byte[]> arrayOfArrays = new ArrayList<byte[]>();
  protected int totalBytes = 0;
  protected int currentIndex = -1;
  protected byte[] currentBuffer = null;
  
  /** Constructor */
  public ByteBuffer()
  {
  }
  
  /** Clear the buffer.
  */
  public void clear()
  {
    arrayOfArrays.clear();
    totalBytes = 0;
    currentIndex = -1;
    currentBuffer = null;
  }
  
  /** Get the current buffer length.
  */
  public int size()
  {
    return totalBytes;
  }
  
  /** Add a byte to the buffer at the end.
  */
  public void appendByte(byte b)
  {
    if (currentIndex == arraySize || currentIndex == -1)
    {
      currentBuffer = new byte[arraySize];
      arrayOfArrays.add(currentBuffer);
      currentIndex = 0;
    }
    currentBuffer[currentIndex++] = b;
    totalBytes++;
  }
  
  /** Read a byte from the buffer from the specified place.
  */
  public byte readByte(int position)
  {
    int arrayNumber = position >> 16;
    int offset = position & (arraySize-1);
    return arrayOfArrays.get(arrayNumber)[offset];
  }
  
}
