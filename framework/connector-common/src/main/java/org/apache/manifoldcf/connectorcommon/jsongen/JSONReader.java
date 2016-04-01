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
package org.apache.manifoldcf.connectorcommon.jsongen;

import java.io.*;

/** This base class describes a JSON reader. */
public abstract class JSONReader extends Reader
{

  @Override
  public int read(char[] cbuf, int off, int len)
    throws IOException
  {
    int amt = 0;
    while (true)
    {
      if (len == 0)
        return amt;
      int theChar = read();
      if (theChar == -1)
      {
        if (amt == 0)
          return -1;
        return amt;
      }
      cbuf[off++] = (char)theChar;
      len--;
      amt++;
    }
  }
  
  @Override
  public abstract int read()
    throws IOException;
  
  @Override
  public void close()
    throws IOException
  {
  }
  
}


