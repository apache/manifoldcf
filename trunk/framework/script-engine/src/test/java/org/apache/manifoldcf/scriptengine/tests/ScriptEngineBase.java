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
import java.io.*;

public class ScriptEngineBase
{
  protected ScriptParser scriptParser = new ScriptParser();
  
  @Before
  public void initializeCommands()
    throws Exception
  {
    scriptParser.addCommand("break",new BreakCommand());
    scriptParser.addCommand("print",new PrintCommand());
    scriptParser.addCommand("if",new IfCommand());
    scriptParser.addCommand("while",new WhileCommand());
    scriptParser.addCommand("set",new SetCommand());
    scriptParser.addCommand("insert",new InsertCommand());
    scriptParser.addCommand("remove",new RemoveCommand());
    scriptParser.addCommand("error",new ErrorCommand());
    scriptParser.addCommand("wait",new WaitCommand());
    
    scriptParser.addCommand("GET",new GETCommand());
    scriptParser.addCommand("PUT",new PUTCommand());
    scriptParser.addCommand("DELETE",new DELETECommand());
    scriptParser.addCommand("POST", new POSTCommand());
  }

  @Before
  public void initializeNewOperations()
    throws Exception
  {
    scriptParser.addNewOperation("configuration",new NewConfiguration());
    scriptParser.addNewOperation("configurationnode",new NewConfigurationNode());
    scriptParser.addNewOperation("url",new NewURL());
    scriptParser.addNewOperation("connectionname",new NewConnectionName());
    scriptParser.addNewOperation("array",new NewArray());
    scriptParser.addNewOperation("dictionary",new NewDictionary());
  }
  
  protected VariableReference evaluateExpression(String expression)
    throws Exception
  {
    Reader r = new StringReader(expression);
    TokenStream ts = new BasicTokenStream(r);
    return scriptParser.evaluateExpression(ts);
  }
  
  protected boolean executeStatements(String statements)
    throws Exception
  {
    Reader r = new StringReader(statements);
    TokenStream ts = new BasicTokenStream(r);
    return scriptParser.parseStatements(ts);
  }

}
