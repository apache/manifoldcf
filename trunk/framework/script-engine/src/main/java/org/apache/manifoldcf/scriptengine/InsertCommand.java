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

/** Insert command.  This command inserts an object into an array, either at the specified position, or at the end. */
public class InsertCommand implements Command
{
  /** Parse and execute.  Parsing begins right after the command name, and should stop before the trailing semicolon.
  *@param sp is the script parser to use to help in the parsing.
  *@param currentStream is the current token stream.
  *@return true to send a break signal, false otherwise.
  */
  public boolean parseAndExecute(ScriptParser sp, TokenStream currentStream)
    throws ScriptException
  {
    // Insert xxx into yyy [at zzz]
    VariableReference objectRef = sp.evaluateExpression(currentStream);
    if (objectRef == null)
      sp.syntaxError(currentStream,"Missing object expression");
    Variable object = objectRef.resolve();
    Token t = currentStream.peek();
    if (t == null || t.getToken() == null || !t.getToken().equals("into"))
      sp.syntaxError(currentStream,"Missing 'into'");
    currentStream.skip();
    VariableReference targetRef = sp.evaluateExpression(currentStream);
    if (targetRef == null)
      sp.syntaxError(currentStream,"Missing target expression");
    Variable target = sp.resolveMustExist(currentStream,targetRef);
    t = currentStream.peek();
    if (t != null && t.getToken() != null && t.getToken().equals("at"))
    {
      currentStream.skip();
      VariableReference indexRef = sp.evaluateExpression(currentStream);
      if (indexRef == null)
        sp.syntaxError(currentStream,"Missing expression after 'at'");
      target.insertAt(object,indexRef.resolve());
    }
    else
    {
      target.insertAt(object,null);
    }
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
      sp.syntaxError(currentStream,"Missing object expression");
    Token t = currentStream.peek();
    if (t == null || t.getToken() == null || !t.getToken().equals("into"))
      sp.syntaxError(currentStream,"Missing 'into'");
    currentStream.skip();
    if (sp.skipExpression(currentStream) == false)
      sp.syntaxError(currentStream,"Missing target expression");
    t = currentStream.peek();
    if (t != null && t.getToken() != null && t.getToken().equals("at"))
    {
      currentStream.skip();
      if (sp.skipExpression(currentStream) == false)
        sp.syntaxError(currentStream,"Missing expression after 'at'");
    }
  }

}
