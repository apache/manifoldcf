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

/** This class describes a JSON name/value object reader. */
public class JSONNameValueReader extends JSONReader
{
  protected final static int STATE_NAME = 0;
  protected final static int STATE_VALUE = 1;
  protected final static int STATE_DONE = 2;
  
  protected final JSONReader name;
  protected final JSONReader value;
  
  protected int state = STATE_NAME;
  
  public JSONNameValueReader(JSONStringReader name, JSONReader value)
  {
    this.name = name;
    this.value = value;
  }

  @Override
  public int read()
    throws IOException
  {
    int x;
    switch (state)
    {
    case STATE_NAME:
      x = name.read();
      if (x == -1)
      {
        state = STATE_VALUE;
        return ':';
      }
      return x;
    case STATE_VALUE:
      x = value.read();
      if (x == -1)
      {
        state = STATE_DONE;
        return -1;
      }
      return x;
    case STATE_DONE:
      return -1;
    default:
      throw new IllegalStateException("Unknown state: "+state);
    }
  }

}


