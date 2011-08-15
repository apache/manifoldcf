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

/** Parse script and execute.
*/
public class ScriptParser
{
  protected TokenStream currentStream = null;
  
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
      skipExpression();
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
      skipExpression();
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
  
  public Variable evaluateExpression()
    throws ScriptException
  {
    // MHL
    return null;
  }
  
  public void skipExpression()
    throws ScriptException
  {
    // MHL
  }
  
  protected void syntaxError(String message)
    throws ScriptException
  {
    throw new ScriptException(message+" in file "+currentStream.getFileName()+" at position "+currentStream.getCharacterPosition());
  }
  
}
