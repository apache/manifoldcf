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

/** This class represents an input stream that begins with bytes
* passed to it through the constructor, and then continues with the bytes
* from a wrapped input stream.  It's basically used when
* we've read past where we want to be in an input stream and need to back up.
*/
public class PrefixedInputStream extends InputStream
{
  protected final ByteBuffer bytes;
  protected final InputStream remainderStream;
  
  protected int bytePosition = 0;
  protected int byteMax;
  
  /** Constructor */
  public PrefixedInputStream(ByteBuffer bytes, InputStream remainderStream)
  {
    this.bytes = bytes;
    this.remainderStream = remainderStream;
    byteMax = bytes.size();
  }

  @Override
  public int read()
    throws IOException
  {
    if (bytePosition < byteMax)
      return 0xff & (int)bytes.readByte(bytePosition++);
    return remainderStream.read();
  }
  
  @Override
  public int read(byte[] b, int off, int len)
    throws IOException
  {
    // Use the superclass method that goes through read, if we're within the
    // buffer.
    if (bytePosition < byteMax)
      return super.read(b,off,len);
    // Outside of the local buffer, vector right through to the remainder stream.
    return remainderStream.read(b,off,len);
  }
  
  @Override
  public long skip(long n)
    throws IOException
  {
    if (bytePosition < byteMax)
      return super.skip(n);
    return remainderStream.skip(n);
  }
  
  @Override
  public void close()
    throws IOException
  {
    // Since the wrapped input stream will be closed by someone else,
    // we NEVER close it through the wrapper.
  }
  
}
