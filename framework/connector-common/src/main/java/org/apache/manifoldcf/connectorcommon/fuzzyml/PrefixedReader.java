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
import java.nio.*;

/** This class represents a Reader that begins with characters
* passed to it through the constructor, and then continues with the characters
* from a wrapped Reader.  It's basically used when
* we've read past where we want to be in a Reader and need to back up.
*/
public class PrefixedReader extends Reader
{
  protected final CharacterBuffer chars;
  protected final Reader remainderStream;
  
  protected int charPosition = 0;
  protected int charMax;
  
  /** Constructor */
  public PrefixedReader(CharacterBuffer chars, Reader remainderStream)
  {
    this.chars = chars;
    this.remainderStream = remainderStream;
    charMax = chars.size();
  }

  @Override
  public int read(CharBuffer target)
    throws IOException
  {
    if (charPosition < charMax)
      return super.read(target);
    return remainderStream.read(target);
  }
  
  @Override
  public int read()
    throws IOException
  {
    if (charPosition < charMax)
      return super.read();
    return remainderStream.read();
  }
  
  @Override
  public int read(char[] cbuf)
    throws IOException
  {
    if (charPosition < charMax)
      return super.read(cbuf);
    return remainderStream.read(cbuf);
  }
  
  @Override
  public int read(char[] cbuf, int off, int len)
    throws IOException
  {
    int amt = 0;
    while (charPosition < charMax)
    {
      cbuf[off++] = chars.readChar(charPosition++);
      len--;
      amt++;
    }
    if (len > 0)
    {
      int rem = remainderStream.read(cbuf,off,len);
      if (rem == -1)
      {
        if (amt > 0)
          return amt;
        return rem;
      }
      amt += rem;
    }
    return amt;
  }
  
  @Override
  public long skip(long n)
    throws IOException
  {
    if (charPosition < charMax)
      return super.skip(n);
    return remainderStream.skip(n);
  }
  
  @Override
  public void close()
    throws IOException
  {
    // Since the wrapped reader will be closed by someone else,
    // we NEVER close it through the wrapper.
  }
  
}
