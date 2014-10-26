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

/** Output stream, which writes to XThreadInputStream.
* Use this when an API method needs to write to an output stream, but
* you want an input stream in the other thread receiving the data.
*/
public class XThreadOutputStream extends OutputStream {

  protected final XThreadInputStream inputStream;
    
  byte[] byteBuffer = new byte[1];

  public XThreadOutputStream(XThreadInputStream inputStream) {
    this.inputStream = inputStream;
  }
  
  @Override
  public void write(byte[] buffer)
    throws IOException {
    try {
      inputStream.stuffQueue(buffer,0,buffer.length);
    } catch (InterruptedException e) {
      throw new InterruptedIOException(e.getMessage());
    }
  }

  @Override
  public void write(int c)
    throws IOException {
    byteBuffer[0] = (byte)c;
    try {
      inputStream.stuffQueue(byteBuffer,0,1);
    } catch (InterruptedException e) {
      throw new InterruptedIOException(e.getMessage());
    }
  }

  @Override
  public void write(byte[] buffer, int pos, int amt)
    throws IOException {
    try {
      inputStream.stuffQueue(buffer,pos,amt);
    } catch (InterruptedException e) {
      throw new InterruptedIOException(e.getMessage());
    }
  }
    
  @Override
  public void close()
    throws IOException {
    inputStream.doneStuffingQueue();
    super.close();
  }
}

