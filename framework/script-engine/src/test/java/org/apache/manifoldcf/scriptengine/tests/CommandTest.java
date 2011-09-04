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
package org.apache.manifoldcf.scriptengine.tests;

import org.apache.manifoldcf.scriptengine.*;
import org.junit.*;
import static org.junit.Assert.*;

public class CommandTest extends ScriptEngineBase
{
  @Test
  public void setCommand()
    throws Exception
  {
    VariableReference vr;
    assertEquals(false,executeStatements("set x = 1;"));
    vr = evaluateExpression("x");
    assertNotNull(vr);
    assertEquals(1,vr.resolve().getIntValue());
  }

  @Test
  public void ifCommand()
    throws Exception
  {
    VariableReference vr;
    
    assertEquals(false,executeStatements("if 1==1 then set y = 1; else set y = 2; ;"));
    vr = evaluateExpression("y");
    assertNotNull(vr);
    assertEquals(1,vr.resolve().getIntValue());

    assertEquals(false,executeStatements("if 1==0 then set y = 1; else set y = 2; ;"));
    vr = evaluateExpression("y");
    assertNotNull(vr);
    assertEquals(2,vr.resolve().getIntValue());

  }

  @Test
  public void whileCommand()
    throws Exception
  {
    VariableReference vr;
    
    assertEquals(false,executeStatements("set counter = 10; while counter > 0 do set counter = counter - 1; ;"));

    vr = evaluateExpression("counter");
    assertNotNull(vr);
    assertEquals(0,vr.resolve().getIntValue());

    assertEquals(false,executeStatements("set flag = null; while ! isnull foo do set flag = 1; ;"));

    vr = evaluateExpression("flag");
    assertNotNull(vr);
    assertNull(vr.resolve());
  }

  @Test
  public void breakCommand()
    throws Exception
  {
    VariableReference vr;

    assertEquals(true,executeStatements("set x = null; break; set x = 1;"));
    
    vr = evaluateExpression("x");
    assertNotNull(vr);
    assertNull(vr.resolve());
  }

  @Test
  public void waitCommand()
    throws Exception
  {
    long now = System.currentTimeMillis();
    assertEquals(false,executeStatements("wait 1000;"));
    assertEquals(true,(now + 1000L) <= System.currentTimeMillis());
  }

  @Test
  public void errorCommand()
    throws Exception
  {
    boolean sawException = false;
    try
    {
      executeStatements("error '123456';");
    }
    catch (ScriptException e)
    {
      if (e.getMessage().indexOf("123456") != -1)
        sawException = true;
      else
        throw e;
    }
    assertEquals(true,sawException);
  }

  @Test
  public void printCommand()
    throws Exception
  {
    assertEquals(false,executeStatements("print 'hello';"));
  }
  
  // MHL for GET, PUT, DELETE and POST commands, which need a jetty instance to work against.
  
}