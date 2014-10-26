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

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;

/** This class represents the parse state of the BOM (byte order mark) parser.
* The byte order mark parser looks for a byte order mark at the start of a byte sequence,
* and based on whether it finds it or not, and what it finds, selects a preliminary character encoding.
* Once a preliminary character encoding is determined, an EncodingAccepter is notified,
* and further bytes are sent to a provided ByteReceiver.
*/
public class BOMEncodingDetector extends SingleByteReceiver implements EncodingDetector
{
  protected String encoding = null;
  protected final ByteReceiver overflowByteReceiver;
  
  protected ByteBuffer replayBuffer = new ByteBuffer();
  
  protected final static int BOM_NOTHINGYET = 0;
  protected final static int BOM_SEEN_EF = 1;
  protected final static int BOM_SEEN_FF = 2;
  protected final static int BOM_SEEN_FE = 3;
  protected final static int BOM_SEEN_ZERO = 4;
  protected final static int BOM_SEEN_EFBB = 5;
  protected final static int BOM_SEEN_FFFE = 6;
  protected final static int BOM_SEEN_0000 = 7;
  protected final static int BOM_SEEN_FFFE00 = 8;
  protected final static int BOM_SEEN_0000FE = 9;
  
  protected int currentState = BOM_NOTHINGYET;
  
  /** Constructor.
  *@param overflowByteReceiver Pass in the receiver of all overflow bytes.
  * If no receiver is passed in, the detector will stop as soon as the
  * BOM is either seen, or not seen.
  */
  public BOMEncodingDetector(ByteReceiver overflowByteReceiver)
  {
    super(8);
    this.overflowByteReceiver = overflowByteReceiver;
  }
  
  /** Set initial encoding.
  */
  @Override
  public void setEncoding(String encoding)
  {
    this.encoding = encoding;
  }

  /** Retrieve final encoding determination.
  */
  @Override
  public String getEncoding()
  {
    return encoding;
  }
  
  /** Receive a byte.
  */
  @Override
  public boolean dealWithByte(byte b)
    throws ManifoldCFException
  {
    replayBuffer.appendByte(b);
    int theByte = 0xff & (int)b;
    switch (currentState)
    {

    case BOM_NOTHINGYET:
      if (theByte == 0xef)
        currentState = BOM_SEEN_EF;
      else if (theByte == 0xff)
        currentState = BOM_SEEN_FF;
      else if (theByte == 0xfe)
        currentState = BOM_SEEN_FE;
      else if (theByte == 0x00)
        currentState = BOM_SEEN_ZERO;
      else
        return replay();
      break;

    case BOM_SEEN_EF:
      if (theByte == 0xbb)
        currentState = BOM_SEEN_EFBB;
      else
        return replay();
      break;

    case BOM_SEEN_FF:
      if (theByte == 0xfe)
      {
        // Either UTF-16LE or UTF-32LE
        mark();
        currentState = BOM_SEEN_FFFE;
      }
      else
        return replay();
      break;

    case BOM_SEEN_FE:
      if (theByte == 0xff)
      {
        // UTF-16BE detected
        mark();
        return establishEncoding("UTF-16BE");
      }
      else
        return replay();
      
    case BOM_SEEN_ZERO:
      if (theByte == 0x00)
        currentState = BOM_SEEN_0000;
      else
        return replay();
      break;
      
    case BOM_SEEN_EFBB:
      if (theByte == 0xbf)
      {
        // Encoding detected as utf-8
        mark();
        return establishEncoding(StandardCharsets.UTF_8.name());
      }
      else
        return replay();

    case BOM_SEEN_FFFE:
      if (theByte == 0x00)
      {
        currentState = BOM_SEEN_FFFE00;
      }
      else
      {
        // Encoding detected as UTF-16LE.  Do NOT re-mark, we need this
        // character for later.
        return establishEncoding(StandardCharsets.UTF_16LE.name());
      }
      break;

    case BOM_SEEN_0000:
      if (theByte == 0xfe)
        currentState = BOM_SEEN_0000FE;
      else
        return replay();
      break;

    case BOM_SEEN_FFFE00:
      if (theByte == 0x00)
      {
        mark();
        return establishEncoding("UTF-32LE");
      }
      else
      {
        // Leave mark alone.
        return establishEncoding(StandardCharsets.UTF_16LE.name());
      }

    case BOM_SEEN_0000FE:
      if (theByte == 0xff)
      {
        mark();
        return establishEncoding("UTF-32BE");
      }
      else
        return replay();
      
    default:
      throw new ManifoldCFException("Unknown state: "+currentState);
    }
    
    return false;
  }

  /** Establish the provided encoding, and send the rest to the child, if any.
  */
  protected boolean establishEncoding(String encoding)
    throws ManifoldCFException
  {
    setEncoding(encoding);
    return true;
  }
  
  /** Set a "mark".
  */
  protected void mark()
  {
    replayBuffer.clear();
  }
  
  /** Establish NO encoding, and replay from the current saved point to the child, if any.
  */
  protected boolean replay()
    throws ManifoldCFException
  {
    return true;
  }
  
  /** Send stream from current point onward with the current encoding.
  */
  protected boolean playFromCurrentPoint()
    throws ManifoldCFException
  {
    mark();
    return true;
  }
  
  /** Deal with the remainder of the input.
  * This is called only when dealWithByte() returns true.
  *@param buffer is the buffer of characters that should come first.
  *@param offset is the offset within the buffer of the first character.
  *@param len is the number of characters in the buffer.
  *@param inputStream is the stream that should come after the characters in the buffer.
  *@return true to abort, false if the end of the stream has been reached.
  */
  @Override
  protected boolean dealWithRemainder(byte[] buffer, int offset, int len, InputStream inputStream)
    throws IOException, ManifoldCFException
  {
    if (overflowByteReceiver == null)
      return super.dealWithRemainder(buffer,offset,len,inputStream);
    // Create a wrapped input stream with all the missing bytes
    while (len > 0)
    {
      replayBuffer.appendByte(buffer[offset++]);
      len--;
    }
    return overflowByteReceiver.dealWithBytes(new PrefixedInputStream(replayBuffer,inputStream));
  }

}
