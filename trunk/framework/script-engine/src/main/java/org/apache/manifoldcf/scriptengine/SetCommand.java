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

/** Set command. */
public class SetCommand implements Command
{
  /** Parse and execute.  Parsing begins right after the command name, and should stop before the trailing semicolon.
  *@param sp is the script parser to use to help in the parsing.
  *@param currentStream is the current token stream.
  *@return true to send a break signal, false otherwise.
  */
  public boolean parseAndExecute(ScriptParser sp, TokenStream currentStream)
    throws ScriptException
  {
    VariableReference lhs = sp.evaluateExpression(currentStream);
    if (lhs == null)
      sp.syntaxError(currentStream,"Missing lhs expression in set command");
    Token t = currentStream.peek();
    if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals("="))
      sp.syntaxError(currentStream,"Missing '=' in set command");
    currentStream.skip();
    VariableReference rhs = sp.evaluateExpression(currentStream);
    if (rhs == null)
      sp.syntaxError(currentStream,"Missing rhs expression in set command");
    lhs.setReference(rhs.resolve());
    return false;
  }
  
  /** Parse and skip.  Parsing begins right after the command name, and should stop before the trailing semicolon.
  *@param sp is the script parser to use to help in the parsing.
  *@param currentStream is the current token stream.
  */
  public void parseAndSkip(ScriptParser sp, TokenStream currentStream)
    throws ScriptException
  {
    if (sp.skipExpression(currentStream) == false)
      sp.syntaxError(currentStream,"Missing lhs expression in set command");
    Token t = currentStream.peek();
    if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals("="))
      sp.syntaxError(currentStream,"Missing '=' in set command");
    currentStream.skip();
    if (sp.skipExpression(currentStream) == false)
      sp.syntaxError(currentStream,"Missing rhs expression in set command");
  }

}
