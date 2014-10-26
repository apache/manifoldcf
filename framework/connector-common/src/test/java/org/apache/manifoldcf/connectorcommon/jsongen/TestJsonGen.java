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
import org.junit.*;
import static org.junit.Assert.*;

public class TestJsonGen
{
  
  @Test
  public void testArrayFormation()
    throws IOException
  {
    JSONArrayReader jr = new JSONArrayReader();
    jr.addArrayElement(new JSONStringReader("hello"))
      .addArrayElement(new JSONStringReader("world"));
    compare("[\"hello\",\"world\"]",jr);
    compare("[]",new JSONArrayReader());
  }

  @Test
  public void testObjectFormation()
    throws IOException
  {
    JSONObjectReader jr = new JSONObjectReader();
    jr.addNameValuePair(new JSONNameValueReader(new JSONStringReader("hi"),new JSONIntegerReader(1)))
      .addNameValuePair(new JSONNameValueReader(new JSONStringReader("there"),new JSONDoubleReader(1.0)));
    compare("{\"hi\":1,\"there\":1.0}",jr);
    compare("{}",new JSONObjectReader());
  }
  
  @Test
  public void testStringEscaping()
    throws IOException
  {
    compare("\"t1\\u000da\"",new JSONStringReader("t1\ra"));
    compare("\"t2\\u0009\\u0022\\u005c\"",new JSONStringReader("t2\t\"\\"));
  }
  
  protected void compare(String value, Reader reader)
    throws IOException
  {
    StringBuilder sb = new StringBuilder();
    while (true)
    {
      int character = reader.read();
      if (character == -1)
        break;
      sb.append((char)character);
    }
    assertEquals(value,sb.toString());
  }

}
