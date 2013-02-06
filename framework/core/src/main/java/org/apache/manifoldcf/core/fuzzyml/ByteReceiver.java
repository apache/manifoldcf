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
package org.apache.manifoldcf.core.fuzzyml;

import org.apache.manifoldcf.core.interfaces.*;
import java.io.*;

/** This interface represents a receiver for bytes.
* Implementers of this interface will accept documents a byte at a time
*/
public abstract class ByteReceiver
{
  protected final byte[] byteBuffer;
  
  /** Constructor */
  public ByteReceiver(int chunkSize)
  {
    byteBuffer = new byte[chunkSize];
  }
  
  /** Receive a byte stream and process up to chunksize bytes,
  *@return true if end reached.
  */
  public boolean dealWithBytes(InputStream is)
    throws IOException, ManifoldCFException
  {
    int amt = is.read(byteBuffer);
    if (amt == -1)
      return true;
    for (int i = 0; i < amt; i++)
    {
      if (dealWithByte(byteBuffer[i]))
        return true;
    }
    return false;
  }
  
  /** Receive a byte.
  *@return true to stop further processing.
  */
  public abstract boolean dealWithByte(byte b)
    throws ManifoldCFException;
  
  /** Finish up all processing.
  */
  public abstract void finishUp()
    throws ManifoldCFException;

}
