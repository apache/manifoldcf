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

package org.apache.manifoldcf.scriptengine;

import java.util.*;

/** Parse script and execute.
*/
public class ScriptParser
{
  protected TokenStream currentStream = null;
  protected Map<String,VariableReference> context = new HashMap<String,VariableReference>();
  
  public ScriptParser()
  {
  }
  
  /** Parse multiple statements.
  */
  public boolean parseStatements()
    throws ScriptException
  {
    while (true)
    {
      if (parseStatement() == false)
        break;
    }
    return true;
  }
  
  /** Skip multiple statements.
  */
  public boolean skipStatements()
    throws ScriptException
  {
    while (true)
    {
      if (skipStatement() == false)
        break;
    }
    return true;
  }
  
  /** Parse a single statement.
  */
  public boolean parseStatement()
    throws ScriptException
  {
    Token command = currentStream.peek();
    if (command == null)
      return false;
    String commandString = command.getString();
    if (commandString == null)
      return false;
    if (commandString.equals("POST"))
    {
      currentStream.skip();
      // MHL
    }
    else if (commandString.equals("GET"))
    {
      currentStream.skip();
      // MHL
    }
    else if (commandString.equals("PUT"))
    {
      currentStream.skip();
      // MHL
    }
    else if (commandString.equals("DELETE"))
    {
      currentStream.skip();
      // MHL
    }
    else if (commandString.equals("if"))
    {
      currentStream.skip();
      Variable ifCondition = evaluateExpression();
      if (ifCondition == null)
        syntaxError("Missing if expression");
      Token t = currentStream.peek();
      if (t == null || t.getString() == null || !t.getString().equals("then"))
        syntaxError("Missing 'then' in if statement");
      currentStream.skip();
      if (ifCondition.getBooleanValue())
      {
        parseStatements();
        t = currentStream.peek();
        if (t != null &&t.getString().equals("else"))
        {
          currentStream.skip();
          // Skip statements
          skipStatements();
        }
      }
      else
      {
        skipStatements();
        t = currentStream.peek();
        if (t != null && t.getString().equals("else"))
        {
          currentStream.skip();
          // Parse statements
          parseStatements();
        }
      }
    }
    else if (commandString.equals("while"))
    {
      currentStream.skip();
      int expressionPosition = currentStream.getCharacterPosition();
      while (true)
      {
        currentStream.setCharacterPosition(expressionPosition);
        Variable whileCondition = evaluateExpression();
        if (whileCondition == null)
          syntaxError("Missing while expression");
        Token t = currentStream.peek();
        if (t == null || t.getString() == null || !t.getString().equals("do"))
          syntaxError("Missing 'do' in while statement");
        currentStream.skip();
        if (whileCondition.getBooleanValue())
        {
          parseStatements();
        }
        else
        {
          skipStatements();
          break;
        }
      }
    }
    else
      return false;
    Token semi = currentStream.peek();
    if (semi == null || semi.getPunctuation() == null || !semi.getPunctuation().equals(";"))
      syntaxError("Missing semicolon");
    currentStream.skip();
    return true;
  }

  /** Skip a single statement.
  */
  public boolean skipStatement()
    throws ScriptException
  {
    Token command = currentStream.peek();
    if (command == null)
      return false;
    String commandString = command.getString();
    if (commandString == null)
      return false;
    if (commandString.equals("POST") ||
      commandString.equals("GET") ||
      commandString.equals("PUT") ||
      commandString.equals("DELETE"))
    {
      currentStream.skip();
      while (true)
      {
        Token t = currentStream.peek();
        if (t == null || t.getPunctuation().equals(";"))
          break;
      }
    }
    else if (commandString.equals("if"))
    {
      currentStream.skip();
      if (skipExpression() == false)
        syntaxError("Missing if expression");
      Token t = currentStream.peek();
      if (t == null || t.getString() == null || !t.getString().equals("then"))
        syntaxError("Missing 'then' in if statement");
      currentStream.skip();
      skipStatements();
      t = currentStream.peek();
      if (t != null &&t.getString().equals("else"))
      {
        currentStream.skip();
        // Skip statements
        skipStatements();
      }
    }
    else if (commandString.equals("while"))
    {
      currentStream.skip();
      if (skipExpression() == false)
	syntaxError("Missing while expression");
      Token t = currentStream.peek();
      if (t == null || t.getString() == null || !t.getString().equals("do"))
        syntaxError("Missing 'do' in if statement");
      currentStream.skip();
      skipStatements();
    }
    else
      return false;
    Token semi = currentStream.peek();
    if (semi == null || semi.getPunctuation() == null || !semi.getPunctuation().equals(";"))
      syntaxError("Missing semicolon");
    currentStream.skip();
    return true;
  }
  
  protected Variable evaluateExpression()
    throws ScriptException
  {
    // MHL
    return null;
  }
  
  protected boolean skipExpression()
    throws ScriptException
  {
    // MHL
    return false;
  }
  
  protected VariableReference parseVariableReference()
    throws ScriptException
  {
    // variable_reference -> variable_reference '[' expression ']'
    // variable_reference -> variable_reference.property_name
    // variable_reference -> variable_reference_1
    
    VariableReference vr = parseVariableReference_1();
    if (vr == null)
      return vr;
    while (true)
    {
      Token t = currentStream.peek();
      if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("["))
      {
	currentStream.skip();
	Variable expression = evaluateExpression();
	if (expression == null)
	  syntaxError("Missing expression after '['");
	int indexValue = expression.getIntValue();
	Variable v = vr.resolve();
	if (v == null)
	  syntaxError("Null reference");
	vr = v.getIndexed(indexValue);
	t = currentStream.peek();
	if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals("]"))
	  syntaxError("Missing ']'");
	currentStream.skip();
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("."))
      {
	currentStream.skip();
	t = currentStream.peek();
	if (t == null || t.getString() == null)
	  syntaxError("Need attribute name");
	Variable v = vr.resolve();
	if (v == null)
	  syntaxError("Null attribute reference");
	vr = v.getAttribute(t.getString());
	currentStream.skip();
      }
      else
	break;
    }
      
    return vr;
  }
  
  protected VariableReference parseVariableReference_1()
    throws ScriptException
  {
    Token t = currentStream.peek();
    if (t == null || t.getString() == null)
      return null;
    currentStream.skip();
    String variableName = t.getString();
    // Look up variable reference in current context
    VariableReference x = context.get(variableName);
    if (x == null)
    {
      x = new VariableReference();
      context.put(variableName,x);
    }
    return x;
  }
  
  protected boolean skipVariableReference()
    throws ScriptException
  {
    if (skipVariableReference_1() == false)
      return false;
    while (true)
    {
      Token t = currentStream.peek();
      if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("["))
      {
	currentStream.skip();
	if (skipExpression() == false)
	  syntaxError("Missing expression after '['");
	t = currentStream.peek();
	if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals("]"))
	  syntaxError("Missing ']'");
	currentStream.skip();
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("."))
      {
	currentStream.skip();
	t = currentStream.peek();
	if (t == null || t.getString() == null)
	  syntaxError("Need property name");
	currentStream.skip();
      }
      else
	break;
    }
      
    return true;
  }
  
  protected boolean skipVariableReference_1()
    throws ScriptException
  {
    Token t = currentStream.peek();
    if (t == null || t.getString() == null)
      return false;
    currentStream.skip();
    return true;
  }
  
  protected void syntaxError(String message)
    throws ScriptException
  {
    throw new ScriptException(message+" in file "+currentStream.getFileName()+" at position "+currentStream.getCharacterPosition());
  }
  
}
