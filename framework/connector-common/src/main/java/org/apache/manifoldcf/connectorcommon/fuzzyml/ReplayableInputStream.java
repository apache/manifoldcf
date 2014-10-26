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

/** This class represents an input stream wraps another, and that can be reset and
* played over.  It accomplishes this by buffering all bytes that go past, and
* then plays back from that buffer after the reset.
* The caller, at some point, is expected to signal that no further restarts will
* occur, which ends all attempts at buffering.
*/
public class ReplayableInputStream extends InputStream
{
  protected final ByteBuffer bytes = new ByteBuffer();
  protected final InputStream wrappedStream;
  
  protected int bytePosition = 0;
  protected boolean doBuffering = true;
	
  /** Constructor */
  public ReplayableInputStream(InputStream wrappedStream)
  {
    this.wrappedStream = wrappedStream;
  }

  public void restart(boolean lastRestart)
  {
    if (doBuffering == false)
      throw new IllegalStateException("Can't restart a stream after it has been restarted the last time");
    doBuffering = !lastRestart;
    bytePosition = 0;
  }
  
  @Override
  public int read()
    throws IOException
  {
    if (bytePosition < bytes.size())
      return 0xff & (int)bytes.readByte(bytePosition++);
    int theByte = wrappedStream.read();
    if (theByte == -1)
      return theByte;
    if (doBuffering)
    {
      bytes.appendByte((byte)theByte);
      bytePosition++;
    }
    return theByte;
  }
  
  @Override
  public int read(byte[] b, int off, int len)
    throws IOException
  {
    // Use the superclass method that goes through read, if we're within the
    // buffer, or we're supposed to be buffering.
    if (bytePosition < bytes.size() || doBuffering)
      return super.read(b,off,len);
    // Outside of the local buffer, vector right through to the remainder stream.
    return wrappedStream.read(b,off,len);
  }
  
  @Override
  public long skip(long n)
    throws IOException
  {
    if (bytePosition < bytes.size())
    {
      // I hope this reads n bytes, one at a time; that's what the javadoc seems
      // to indicate.
      return super.skip(n);
    }
    return wrappedStream.skip(n);
  }
  
  @Override
  public void close()
    throws IOException
  {
    // Since the wrapped input stream will be closed by someone else,
    // we NEVER close it through the wrapper.
  }
  
}
