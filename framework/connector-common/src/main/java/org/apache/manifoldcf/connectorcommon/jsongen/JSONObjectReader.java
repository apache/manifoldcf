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
import java.util.*;

/** This class describes a JSON object reader. */
public class JSONObjectReader extends JSONReader
{
  protected final static int STATE_PREBRACE = 0;
  protected final static int STATE_PAIRBEGIN = 1;
  protected final static int STATE_PREEND = 2;
  protected final static int STATE_DONE = 3;
  
  protected int state = STATE_PREBRACE;
  protected final List<JSONReader> pairs = new ArrayList<JSONReader>();
  protected int readerIndex;

  public JSONObjectReader()
  {
  }
  
  public JSONObjectReader addNameValuePair(JSONNameValueReader pair)
  {
    pairs.add(pair);
    return this;
  }
  
  @Override
  public int read()
    throws IOException
  {
    switch (state)
    {
    case STATE_PREBRACE:
      if (pairs.size() == 0)
        state = STATE_PREEND;
      else
      {
        state = STATE_PAIRBEGIN;
        readerIndex = 0;
      }
      return '{';
    case STATE_PREEND:
      state = STATE_DONE;
      return '}';
    case STATE_DONE:
      return -1;
    case STATE_PAIRBEGIN:
      int x = pairs.get(readerIndex).read();
      if (x == -1)
      {
        readerIndex++;
        if (readerIndex == pairs.size())
        {
          state = STATE_DONE;
          return '}';
        }
        else
          return ',';
      }
      else
        return x;
    default:
      throw new IllegalStateException("Unknown state: "+state);
    }
  }

}


