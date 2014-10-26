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
package org.apache.manifoldcf.connectorcommon.common;

import java.io.*;

/** Cross-thread input stream.  Use this class when you have a helper thread
* reading from a socket, and you need the ability to read safely from a ManifoldCF
* worker thread.
*/
public class XThreadInputStream extends InputStream
{
  private final byte[] buffer = new byte[65536];
  private int startPoint = 0;
  private int byteCount = 0;
  private boolean streamEnd = false;
  private IOException failureException = null;
  private boolean abort = false;

  private final InputStream sourceStream;
	
  /** Constructor, from a given input stream. */
  public XThreadInputStream(InputStream sourceStream)
  {
    this.sourceStream = sourceStream;
  }
  
  /** Constructor, from another source. */
  public XThreadInputStream()
  {
    this.sourceStream = null;
  }
  
  /** Call this method to abort the stuffQueue() method.
  */
  public void abort()
  {
    synchronized (this)
    {
      abort = true;
      notifyAll();
    }
  }
  
  /** This method is called from the helper thread side, to stuff bytes onto
  * the queue when there is no input stream.
  * It exits only when interrupted or done.
  */
  public void stuffQueue(byte[] byteBuffer, int offset, int amount)
    throws InterruptedException
  {
    while (amount > 0)
    {
      int maxToRead;
      int readStartPoint;
      synchronized (this)
      {
        if (abort || streamEnd)
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
      if (readStartPoint + maxToRead >= buffer.length)
        maxToRead = buffer.length - readStartPoint;
      // Now, copy to buffer
      int amt;
      if (amount > maxToRead)
        amt = maxToRead;
      else
        amt = amount;
      System.arraycopy(byteBuffer,offset,buffer,readStartPoint,amt);
      offset += amt;
      amount -= amt;
      synchronized (this)
      {
        byteCount += amt;
        notifyAll();
      }
    }
  }
  
  /** Call this method when there is no more data to write.
  */
  public void doneStuffingQueue()
  {
    synchronized (this)
    {
      streamEnd = true;
      notifyAll();
    }
  }
  
  /** This method is called from the helper thread side, to keep the queue
  * stuffed from the input stream.
  * It exits when the stream is empty, or when interrupted.
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
        if (streamEnd || abort)
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
      
      int amt = -1;
      IOException exception = null;
      try
      {
        amt = sourceStream.read(buffer, readStartPoint, maxToRead);
      }
      catch (IOException e)
      {
        exception = e;
      }
      
      synchronized (this)
      {
        if (exception != null)
          failureException = exception;
        else if (amt == -1)
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
    try
    {
      int totalAmt = 0;
      while (true)
      {
        if (len == 0)
          return totalAmt;
        int copyLen;
        synchronized (this)
        {
          copyLen = byteCount;
          if (copyLen > len)
            copyLen = len;
          int remLen = buffer.length - startPoint;
          if (copyLen > remLen)
            copyLen = remLen;
          if (copyLen == 0)
          {
            if (streamEnd)
            {
              if (totalAmt != 0)
                return totalAmt;
              return -1;
            }
            if (failureException != null)
              throw failureException;
            wait();
            continue;
          }
        }
        System.arraycopy(buffer, startPoint, b, off, copyLen);
        totalAmt += copyLen;
        off += copyLen;
        len -= copyLen;
        synchronized (this)
        {
          startPoint += copyLen;
          startPoint &= (buffer.length - 1);
          byteCount -= copyLen;
          notifyAll();
        }
      }
    }
    catch (InterruptedException e)
    {
      throw new InterruptedIOException(e.getMessage());
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
    // Do nothing; stream close is handled by the caller on the stuffer side
  }

}

