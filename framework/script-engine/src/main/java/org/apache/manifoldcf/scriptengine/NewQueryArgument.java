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

/** Class defining the creation of a new Query Argument object.
*/
public class NewQueryArgument implements NewOperation
{
  /** Parse and execute.  Parsing begins right after the "new" keyword and the operation name token.
  *@param sp is the script parser to use to help in the parsing.
  *@param currentStream is the current token stream.
  *@return the variable reference that got created.  Should never be null.
  */
  @Override
  public VariableReference parseAndCreate(ScriptParser sp, TokenStream currentStream)
    throws ScriptException
  {
    VariableReference name = sp.evaluateExpression(currentStream);
    if (name == null)
      sp.syntaxError(currentStream,"Need name expression");
    String nameString = sp.resolveMustExist(currentStream,name).getStringValue();
    String valueString = null;
    Token equal = currentStream.peek();
    if (equal != null && equal.getPunctuation() != null && equal.getPunctuation().equals("="))
    {
      currentStream.skip();
      VariableReference value = sp.evaluateExpression(currentStream);
      if (value == null)
        sp.syntaxError(currentStream,"Need value expression");
      valueString = sp.resolveMustExist(currentStream,value).getStringValue();
    }
    return new VariableQueryArg(nameString,valueString);
  }
  
  /** Parse and skip.  Parsing begins right after the "new" keyword and the operation name token.
  *@param sp is the script parser to use to help in the parsing.
  *@param currentStream is the current token stream.
  */
  @Override
  public void parseAndSkip(ScriptParser sp, TokenStream currentStream)
    throws ScriptException
  {
    sp.skipExpression(currentStream);
    Token equal = currentStream.peek();
    if (equal != null && equal.getPunctuation() != null && equal.getPunctuation().equals("="))
    {
      currentStream.skip();
      sp.skipExpression(currentStream);
    }
  }
  
}
