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

public class ExpressionTest extends ScriptEngineBase
{

  @Test
  public void basicIntOperations()
    throws Exception
  {
    VariableReference vr;
    
    // Binary +
    vr = evaluateExpression("1+2");
    assertNotNull(vr);
    assertEquals(3,vr.resolve().getIntValue());
    
    // Binary -
    vr = evaluateExpression("1-2");
    assertNotNull(vr);
    assertEquals(-1,vr.resolve().getIntValue());

    // Binary *
    vr = evaluateExpression("2*3");
    assertNotNull(vr);
    assertEquals(6,vr.resolve().getIntValue());

    // Binary /
    vr = evaluateExpression("3/2");
    assertNotNull(vr);
    assertEquals(1,vr.resolve().getIntValue());

    // Unary -
    vr = evaluateExpression("-2");
    assertNotNull(vr);
    assertEquals(-2,vr.resolve().getIntValue());

    // Binary &
    vr = evaluateExpression("1&5");
    assertNotNull(vr);
    assertEquals(1,vr.resolve().getIntValue());

    // Binary |
    vr = evaluateExpression("1|2");
    assertNotNull(vr);
    assertEquals(3,vr.resolve().getIntValue());

    // Unary !
    vr = evaluateExpression("!2");
    assertNotNull(vr);
    assertEquals(2^2,vr.resolve().getIntValue());

    // >
    vr = evaluateExpression("1>1");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());
    vr = evaluateExpression("2>1");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // >=
    vr = evaluateExpression("0>=1");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());
    vr = evaluateExpression("1>=1");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // <
    vr = evaluateExpression("2<2");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());
    vr = evaluateExpression("1<2");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // <=
    vr = evaluateExpression("2<=1");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());
    vr = evaluateExpression("1<=1");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // ==
    vr = evaluateExpression("1==2");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());
    vr = evaluateExpression("1==1");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // !=
    vr = evaluateExpression("1!=1");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());
    vr = evaluateExpression("1!=2");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // Attributes
    vr = evaluateExpression("2 .__int__");
    assertNotNull(vr);
    assertEquals(2,vr.resolve().getIntValue());
    vr = evaluateExpression("2 .__string__");
    assertNotNull(vr);
    assertEquals("2",vr.resolve().getStringValue());
    vr = evaluateExpression("2 .__float__");
    assertNotNull(vr);
    assertEquals(2.0,vr.resolve().getDoubleValue(),0.0);
    vr = evaluateExpression("2 .__script__");
    assertNotNull(vr);
    assertEquals("2",vr.resolve().getStringValue());

    // Various values
    assertEquals(2,new VariableInt(2).getIntValue());
    assertEquals("2",new VariableInt(2).getStringValue());
    assertEquals(2.0,new VariableInt(2).getDoubleValue(),0.0);

  }

  @Test
  public void basicFloatOperations()
    throws Exception
  {
    VariableReference vr;

    // Binary +
    vr = evaluateExpression("1.0+2.0");
    assertNotNull(vr);
    assertEquals(3.0,vr.resolve().getDoubleValue(),0.0);
    
    // Binary -
    vr = evaluateExpression("1.0-2.0");
    assertNotNull(vr);
    assertEquals(-1.0,vr.resolve().getDoubleValue(),0.0);

    // Binary *
    vr = evaluateExpression("2.0*3.0");
    assertNotNull(vr);
    assertEquals(6.0,vr.resolve().getDoubleValue(),0.0);

    // Binary /
    vr = evaluateExpression("3.0/2.0");
    assertNotNull(vr);
    assertEquals(1.5,vr.resolve().getDoubleValue(),0.0);

    // Unary -
    vr = evaluateExpression("-2.0");
    assertNotNull(vr);
    assertEquals(-2.0,vr.resolve().getDoubleValue(),0.0);

    // >
    vr = evaluateExpression("1.0>1.0");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());
    vr = evaluateExpression("2.0>1.0");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // >=
    vr = evaluateExpression("0.0>=1.0");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());
    vr = evaluateExpression("1.0>=1.0");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // <
    vr = evaluateExpression("2.0<2.0");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());
    vr = evaluateExpression("1.0<2.0");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // <=
    vr = evaluateExpression("2.0<=1.0");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());
    vr = evaluateExpression("1.0<=1.0");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // ==
    vr = evaluateExpression("1.0==2.0");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());
    vr = evaluateExpression("1.0==1.0");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // !=
    vr = evaluateExpression("1.0!=1.0");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());
    vr = evaluateExpression("1.0!=2.0");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // Attributes
    vr = evaluateExpression("2.0 .__int__");
    assertNotNull(vr);
    assertEquals(2,vr.resolve().getIntValue());
    vr = evaluateExpression("2.0 .__string__");
    assertNotNull(vr);
    assertEquals("2.0",vr.resolve().getStringValue());
    vr = evaluateExpression("2.0 .__float__");
    assertNotNull(vr);
    assertEquals(2.0,vr.resolve().getDoubleValue(),0.0);
    vr = evaluateExpression("2.0 .__script__");
    assertNotNull(vr);
    assertEquals("2.0",vr.resolve().getStringValue());

    // Various values
    assertEquals(2,new VariableFloat(2.0).getIntValue());
    assertEquals("2.0",new VariableFloat(2.0).getStringValue());
    assertEquals(2.0,new VariableFloat(2.0).getDoubleValue(),0.0);

  }

  @Test
  public void basicBooleanOperations()
    throws Exception
  {
    VariableReference vr;
    
    // Binary &
    vr = evaluateExpression("true&false");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());

    // Binary |
    vr = evaluateExpression("true|false");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // Binary &&
    vr = evaluateExpression("true&&false");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());

    // Binary ||
    vr = evaluateExpression("true||false");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // Unary !
    vr = evaluateExpression("!true");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());

    // ==
    vr = evaluateExpression("true==false");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());
    vr = evaluateExpression("true==true");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // !=
    vr = evaluateExpression("true!=true");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());
    vr = evaluateExpression("true!=false");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // Attributes
    vr = evaluateExpression("true .__script__");
    assertNotNull(vr);
    assertEquals("true",vr.resolve().getStringValue());

  }

  @Test
  public void basicStringOperations()
    throws Exception
  {
    VariableReference vr;
    
    // Binary +
    vr = evaluateExpression("'1'+'2'");
    assertNotNull(vr);
    assertEquals("12",vr.resolve().getStringValue());
    
    // ==
    vr = evaluateExpression("'1'=='2'");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());
    vr = evaluateExpression("'1'=='1'");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // !=
    vr = evaluateExpression("'1'!='1'");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());
    vr = evaluateExpression("'1'!='2'");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // Attributes
    vr = evaluateExpression("'2'.__int__");
    assertNotNull(vr);
    assertEquals(2,vr.resolve().getIntValue());
    vr = evaluateExpression("'2'.__string__");
    assertNotNull(vr);
    assertEquals("2",vr.resolve().getStringValue());
    vr = evaluateExpression("'2'.__float__");
    assertNotNull(vr);
    assertEquals(2.0,vr.resolve().getDoubleValue(),0.0);
    vr = evaluateExpression("'2'.__script__");
    assertNotNull(vr);
    assertEquals("\"2\"",vr.resolve().getStringValue());

    // Various values
    assertEquals(2,new VariableString("2").getIntValue());
    assertEquals("2",new VariableString("2").getStringValue());
    assertEquals(2.0,new VariableString("2").getDoubleValue(),0.0);

  }

  @Test
  public void basicURLOperations()
    throws Exception
  {
    VariableReference vr;
    
    // Binary +
    vr = evaluateExpression("(new url 'http://localhost:8345/mcf-api-service/json') + 'jobs' + '123'");
    assertNotNull(vr);
    assertEquals("http://localhost:8345/mcf-api-service/json/jobs/123",vr.resolve().getStringValue());
    
    // ==
    vr = evaluateExpression("(new url 'hello')==(new url 'there')");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());
    vr = evaluateExpression("(new url 'hello')==(new url 'hello')");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // !=
    vr = evaluateExpression("(new url 'hello')!=(new url 'hello')");
    assertNotNull(vr);
    assertEquals(false,vr.resolve().getBooleanValue());
    vr = evaluateExpression("(new url 'hello')!=(new url 'there')");
    assertNotNull(vr);
    assertEquals(true,vr.resolve().getBooleanValue());

    // Attributes
    vr = evaluateExpression("((new url 'abc') + 'def ghi').__string__");
    assertNotNull(vr);
    assertEquals("abc/def%20ghi",vr.resolve().getStringValue());
    vr = evaluateExpression("(new url 'abc').__script__");
    assertNotNull(vr);
    assertEquals("\"abc\"",vr.resolve().getStringValue());

    // Various values
    assertEquals("abc",new VariableURL("abc").getStringValue());

    // Connectionname objecfs
    vr = evaluateExpression("((new url 'hello') + (new connectionname 'there/guys.')).__string__");
    assertNotNull(vr);
    assertEquals("hello/there.%2Bguys..",vr.resolve().getStringValue());

  }

  @Test
  public void basicArrayOperations()
    throws Exception
  {
    VariableReference vr;
    
    // initializer and subscript
    assertEquals(false,executeStatements("set x = [1,'2',3];")); 

    vr = evaluateExpression("x[2]");
    assertNotNull(vr);
    assertEquals(3,vr.resolve().getIntValue());

    // Attributes
    vr = evaluateExpression("x.__script__");
    assertNotNull(vr);
    assertEquals("[ 1, \"2\", 3 ]",vr.resolve().getStringValue());
    vr = evaluateExpression("x.__size__");
    assertNotNull(vr);
    assertEquals(3,vr.resolve().getIntValue());

    // Do inserts
    assertEquals(false,executeStatements("insert 'tail' into x;"));
    vr = evaluateExpression("x[3]");
    assertNotNull(vr);
    assertEquals("tail",vr.resolve().getStringValue());
    assertEquals(false,executeStatements("insert 'head' into x at 0;"));
    vr = evaluateExpression("x[1]");
    assertNotNull(vr);
    assertEquals(1,vr.resolve().getIntValue());
    vr = evaluateExpression("x.__size__");
    assertNotNull(vr);
    assertEquals(5,vr.resolve().getIntValue());
    
    // Do deletes
    assertEquals(false,executeStatements("remove 1 from x;"));
    vr = evaluateExpression("x[1]");
    assertNotNull(vr);
    assertEquals("2",vr.resolve().getStringValue());
    vr = evaluateExpression("x[0]");
    assertNotNull(vr);
    assertEquals("head",vr.resolve().getStringValue());

  }

  @Test
  public void basicDictOperations()
    throws Exception
  {
    VariableReference vr;
    
    // Set up
    assertEquals(false,executeStatements("set x = new dictionary; set x['a'] = 1; set x['b'] = 2;"));
    vr = evaluateExpression("x['a']");
    assertNotNull(vr);
    assertEquals(1,vr.resolve().getIntValue());

    // Attributes
    vr = evaluateExpression("x.__size__");
    assertNotNull(vr);
    assertEquals(2,vr.resolve().getIntValue());

  }

  @Test
  public void basicConfigurationnodeOperations()
    throws Exception
  {
    VariableReference vr;
    
    // Set up
    assertEquals(false,executeStatements("set x = << 'type' : 'value' : 'attr1' = 'av1', 'attr2' = 'av2' : << 'child1' : 'cv1' : : >>, << 'child2' : 'cv2' : : >> >>;")); 

    // Subscripts
    vr = evaluateExpression("x[0].__type__");
    assertNotNull(vr);
    assertEquals("child1",vr.resolve().getStringValue());
    vr = evaluateExpression("x[0].__value__");
    assertNotNull(vr);
    assertEquals("cv1",vr.resolve().getStringValue());

    // Size
    vr = evaluateExpression("x.__size__");
    assertNotNull(vr);
    assertEquals(2,vr.resolve().getIntValue());
    
    // Dict
    vr = evaluateExpression("x.__dict__['child1'].__value__");
    assertNotNull(vr);
    assertEquals("cv1",vr.resolve().getStringValue());

    // User attributes
    vr = evaluateExpression("x.attr1");
    assertNotNull(vr);
    assertEquals("av1",vr.resolve().getStringValue());
    vr = evaluateExpression("x.attr2");
    assertNotNull(vr);
    assertEquals("av2",vr.resolve().getStringValue());

    // Do inserts
    assertEquals(false,executeStatements("insert << 'child3' : 'cv3' : : >> into x;"));
    vr = evaluateExpression("x[2].__type__");
    assertNotNull(vr);
    assertEquals("child3",vr.resolve().getStringValue());
    assertEquals(false,executeStatements("insert << 'childn1' : 'cvn1' : : >> into x at 0;"));
    vr = evaluateExpression("x[1].__type__");
    assertNotNull(vr);
    assertEquals("child1",vr.resolve().getStringValue());
    vr = evaluateExpression("x.__size__");
    assertNotNull(vr);
    assertEquals(4,vr.resolve().getIntValue());
    
    // Do deletes
    assertEquals(false,executeStatements("remove 1 from x;"));
    vr = evaluateExpression("x[1].__type__");
    assertNotNull(vr);
    assertEquals("child2",vr.resolve().getStringValue());
    vr = evaluateExpression("x[0].__type__");
    assertNotNull(vr);
    assertEquals("childn1",vr.resolve().getStringValue());

    // Script value
    vr = evaluateExpression("x.__script__");
    assertNotNull(vr);
    assertEquals("<< \"type\" : \"value\" : \"attr1\"=\"av1\", \"attr2\"=\"av2\" : << \"childn1\" : \"cvn1\" :  :  >>, << \"child2\" : \"cv2\" :  :  >>, << \"child3\" : \"cv3\" :  :  >> >>",vr.resolve().getStringValue());
  }

  @Test
  public void basicConfigurationOperations()
    throws Exception
  {
    VariableReference vr;
    
    // Set up
    assertEquals(false,executeStatements("set x = {<< 'child1' : 'cv1' : : >>, << 'child2' : 'cv2' : : >>};")); 

    // Subscripts
    vr = evaluateExpression("x[0].__type__");
    assertNotNull(vr);
    assertEquals("child1",vr.resolve().getStringValue());
    vr = evaluateExpression("x[0].__value__");
    assertNotNull(vr);
    assertEquals("cv1",vr.resolve().getStringValue());

    // Size
    vr = evaluateExpression("x.__size__");
    assertNotNull(vr);
    assertEquals(2,vr.resolve().getIntValue());
    
    // Dict
    vr = evaluateExpression("x.__dict__['child1'].__value__");
    assertNotNull(vr);
    assertEquals("cv1",vr.resolve().getStringValue());

    // Do inserts
    assertEquals(false,executeStatements("insert << 'child3' : 'cv3' : : >> into x;"));
    vr = evaluateExpression("x[2].__type__");
    assertNotNull(vr);
    assertEquals("child3",vr.resolve().getStringValue());
    assertEquals(false,executeStatements("insert << 'childn1' : 'cvn1' : : >> into x at 0;"));
    vr = evaluateExpression("x[1].__type__");
    assertNotNull(vr);
    assertEquals("child1",vr.resolve().getStringValue());
    vr = evaluateExpression("x.__size__");
    assertNotNull(vr);
    assertEquals(4,vr.resolve().getIntValue());
    
    // Do deletes
    assertEquals(false,executeStatements("remove 1 from x;"));
    vr = evaluateExpression("x[1].__type__");
    assertNotNull(vr);
    assertEquals("child2",vr.resolve().getStringValue());
    vr = evaluateExpression("x[0].__type__");
    assertNotNull(vr);
    assertEquals("childn1",vr.resolve().getStringValue());

    // Script value
    vr = evaluateExpression("x.__script__");
    assertNotNull(vr);
    assertEquals("{ << \"childn1\" : \"cvn1\" :  :  >>, << \"child2\" : \"cv2\" :  :  >>, << \"child3\" : \"cv3\" :  :  >> }",vr.resolve().getStringValue());
  }

}
