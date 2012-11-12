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
package org.apache.manifoldcf.core.common;

import java.io.*;

/** Cross-thread input stream.  Use this class when you have a helper thread
* reading from a socket, and you need the ability to read safely from a ManifoldCF
* worker thread.
*/
public class XThreadInputStream extends InputStream
{
  private byte[] buffer = new byte[65536];
  private int startPoint = 0;
  private int byteCount = 0;
  private boolean streamEnd = false;
  private InputStream sourceStream;
  
  /** Constructor */
  public XThreadInputStream(InputStream sourceStream)
  {
    this.sourceStream = sourceStream;
  }
  
  /** This method is called from the helper thread side, to keep the queue
  * stuffed.  It exits when the stream is empty, or when interrupted.
  */
  public void stuffQueue()
    throws IOException, InterruptedException
  {
    while (true)
    {
      int maxToRead;
      int readStartPoint;
      synchronized (this)
      {
        if (streamEnd)
          return;
        // Calculate amount to read
        maxToRead = buffer.length - byteCount;
        if (maxToRead == 0)
        {
          wait();
          continue;
        }
        readStartPoint = (startPoint + byteCount) & (buffer.length-1);
      }
      
      // See how to break up the reads into pieces.  We only do one piece right now.
      if (readStartPoint + maxToRead >= buffer.length)
        maxToRead = buffer.length - readStartPoint;
      int amt = sourceStream.read(buffer, readStartPoint, maxToRead);
      synchronized (this)
      {
        if (amt == -1)
          streamEnd = true;
        else
          byteCount += amt;
        notifyAll();
      }
    }
  }
  
  /** Read a byte.
  */
  @Override
  public int read()
    throws IOException
  {
    byte[] b = new byte[1];
    int amt = read(b,0,1);
    if (amt == -1)
      return amt;
    return ((int)b[0]) & 0xffff;
  }
    
  /** Read lots of bytes.
  */
  @Override
  public int read(byte[] b)
    throws IOException
  {
    return read(b,0,b.length);
  }

  /** Read lots of specific bytes.
  */
  @Override
  public int read(byte[] b, int off, int len)
    throws IOException
  {
    int totalAmt = 0;
    while (true)
    {
      if (len == 0)
        return 0;
      int copyLen;
      synchronized (this)
      {
        if (streamEnd)
        {
          if (totalAmt != 0)
            return totalAmt;
          return -1;
        }
        copyLen = byteCount;
        if (copyLen > len)
          copyLen = len;
        int remLen = buffer.length - startPoint;
        if (copyLen > remLen)
          copyLen = remLen;
      }
      System.arraycopy(buffer, startPoint, b, off, copyLen);
      totalAmt += copyLen;
      len -= copyLen;
      synchronized (this)
      {
        startPoint += copyLen;
        startPoint &= (buffer.length - 1);
        byteCount -= copyLen;
      }
    }
  }
  
  /** Skip
  */
  @Override
  public long skip(long n)
    throws IOException
  {
    // Do nothing
    return 0L;
  }

  /** Get available.
  */
  @Override
  public int available()
    throws IOException
  {
    // Not supported
    return 0;
  }

  /** Mark.
  */
  @Override
  public void mark(int readLimit)
  {
    // Do nothing
  }

  /** Reset.
  */
  @Override
  public void reset()
    throws IOException
  {
    // Do nothing
  }

  /** Check if mark is supported.
  */
  @Override
  public boolean markSupported()
  {
    // Not supported
    return false;
  }

  /** Close.
  */
  @Override
  public void close()
    throws IOException
  {
    // MHL
  }

}

