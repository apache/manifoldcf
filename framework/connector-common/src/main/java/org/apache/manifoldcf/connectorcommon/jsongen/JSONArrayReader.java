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

/** This class describes a JSON array reader. */
public class JSONArrayReader extends JSONReader
{
  protected final static int STATE_PREBRACKET = 0;
  protected final static int STATE_ELEMENT = 1;
  protected final static int STATE_PREEND = 2;
  protected final static int STATE_DONE = 3;
  
  protected int state = STATE_PREBRACKET;
  protected final List<JSONReader> elements = new ArrayList<JSONReader>();
  protected int elementIndex;
  
  public JSONArrayReader()
  {
  }
  
  public JSONArrayReader(JSONReader[] elements)
  {
    for (JSONReader element : elements)
    {
      addArrayElement(element);
    }
  }
  
  public JSONArrayReader addArrayElement(JSONReader element)
  {
    elements.add(element);
    return this;
  }

  @Override
  public int read()
    throws IOException
  {
    int newState;
    switch (state)
    {
    case STATE_PREBRACKET:
      if (elements.size() == 0)
        state = STATE_PREEND;
      else
      {
        state = STATE_ELEMENT;
        elementIndex = 0;
      }
      return '[';
    case STATE_PREEND:
      state = STATE_DONE;
      return ']';
    case STATE_DONE:
      return -1;
    case STATE_ELEMENT:
      int x = elements.get(elementIndex).read();
      if (x == -1)
      {
        elementIndex++;
        if (elementIndex == elements.size())
        {
          state = STATE_DONE;
          return ']';
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


