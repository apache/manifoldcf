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

import org.apache.http.client.HttpClient;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.config.SocketConfig;
import org.apache.http.HttpStatus;
import org.apache.http.HttpException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.ParseException;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
import java.nio.charset.Charset;

/** Class to parse various syntactical parts of a script and execute them.
*/
public class ScriptParser
{
  /** The connection manager. */
  protected HttpClientConnectionManager connectionManager = null;
  
  /** The client instance */
  protected HttpClient httpClient = null;
  
  /** The lock for the httpclient factory */
  protected Integer httpClientLock = new Integer(0);
  
  /** The current variable context. */
  protected Map<String,ContextVariableReference> context = new HashMap<String,ContextVariableReference>();
  
  /** A table of commands that we know how to deal with. */
  protected Map<String,Command> commands = new HashMap<String,Command>();
  
  /** A table of "new" operations that we know how to deal with. */
  protected Map<String,NewOperation> newOperations = new HashMap<String,NewOperation>();

  public ScriptParser()
  {
  }
  
  /** Add a command.
  *@param commandName is the name of the command.
  *@param command is the command instance.
  */
  public void addCommand(String commandName, Command command)
  {
    commands.put(commandName,command);
  }

  /** Add a "new" operation.
  *@param operationName is the name of the operation.
  *@param operation is the operation to create.
  */
  public void addNewOperation(String operationName, NewOperation operation)
  {
    newOperations.put(operationName,operation);
  }
  
  /** Add a variable.
  */
  public void addVariable(String variableName, Variable v)
    throws ScriptException
  {
    ContextVariableReference cvr = new ContextVariableReference();
    cvr.setReference(v);
    context.put(variableName,cvr);
  }

  /** Execute script. */
  public void execute(TokenStream currentStream)
    throws ScriptException
  {
    if (parseStatements(currentStream))
      localError(currentStream,"Break command must be inside a loop");
    Token t = currentStream.peek();
    if (t != null)
      localError(currentStream,"Characters after end of script");
  }

  // Statement return codes
  protected static final int STATEMENT_NOTME = 0;
  protected static final int STATEMENT_ISME = 1;
  protected static final int STATEMENT_BREAK = 2;
  
  /** Parse and execute multiple statements.
  *@param currentStream is the token stream to parse.
  *@return true for a break signal.
  */
  public boolean parseStatements(TokenStream currentStream)
    throws ScriptException
  {
    boolean breakSignal = false;
    while (true)
    {
      if (breakSignal)
      {
        if (skipStatement(currentStream) == false)
          break;
      }
      else
      {
        int result = parseStatement(currentStream);
        if (result == STATEMENT_NOTME)
          break;
        if (result == STATEMENT_BREAK)
          breakSignal = true;
      }
    }
    return breakSignal;
  }
  
  /** Skip multiple statements.
  *@param currentStream is the token stream to parse.
  */
  public void skipStatements(TokenStream currentStream)
    throws ScriptException
  {
    while (true)
    {
      if (skipStatement(currentStream) == false)
        break;
    }
  }
  
  
  /** Parse a single statement.
  *@param currentStream is the current token stream.
  *@return a signal indicating either NOTME, ISME, or BREAK.
  */
  protected int parseStatement(TokenStream currentStream)
    throws ScriptException
  {
    int rval = STATEMENT_ISME;
    
    Token command = currentStream.peek();
    if (command == null)
      return STATEMENT_NOTME;
    String commandString = command.getToken();
    if (commandString == null)
      return STATEMENT_NOTME;
    
    // Let's see if we know about this command.
    Token t = currentStream.peek();
    if (t != null && t.getToken() != null)
    {
      Command c = commands.get(t.getToken());
      if (c != null)
      {
        // We do know about it.  So skip the command name and call the parse method.
        currentStream.skip();
        if (c.parseAndExecute(this,currentStream))
          rval = STATEMENT_BREAK;
        else
          rval = STATEMENT_ISME;
      }
      else
        return STATEMENT_NOTME;
    }
    else
      return STATEMENT_NOTME;
    
    Token semi = currentStream.peek();
    if (semi == null || semi.getPunctuation() == null || !semi.getPunctuation().equals(";"))
      syntaxError(currentStream,"Missing semicolon");
    currentStream.skip();
    return rval;
  }

  /** Skip a single statement.
  *@param currentStream is the current token stream.
  *@return true if a statement was detected, false otherwise.
  */
  protected boolean skipStatement(TokenStream currentStream)
    throws ScriptException
  {
    Token command = currentStream.peek();
    if (command == null)
      return false;
    String commandString = command.getToken();
    if (commandString == null)
      return false;
    
    // Let's see if we know about this command.
    Token t = currentStream.peek();
    if (t != null && t.getToken() != null)
    {
      Command c = commands.get(t.getToken());
      if (c != null)
      {
        // We do know about it.  So skip the command name and call the parse method.
        currentStream.skip();
        c.parseAndSkip(this,currentStream);
        // Fall through
      }
      else
        return false;
    }
    else
      return false;
    
    Token semi = currentStream.peek();
    if (semi == null || semi.getPunctuation() == null || !semi.getPunctuation().equals(";"))
      syntaxError(currentStream,"Missing semicolon");
    currentStream.skip();
    return true;
  }
  
  /** Evaluate an expression.
  *@param currentStream is the token stream to parse.
  *@return a VariableReference object if an expression was detected, null otherwise.
  */
  public VariableReference evaluateExpression(TokenStream currentStream)
    throws ScriptException
  {
    // Look for pipe operations
    VariableReference vr = evaluateExpression_1(currentStream);
    if (vr == null)
      return null;
    while (true)
    {
      Token t = currentStream.peek();
      if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("||"))
      {
        currentStream.skip();
        VariableReference v = evaluateExpression_1(currentStream);
        if (v == null)
          syntaxError(currentStream,"Missing expression after '||'");
        vr = resolveMustExist(currentStream,vr).doublePipe(v.resolve());
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("|"))
      {
        currentStream.skip();
        VariableReference v = evaluateExpression_1(currentStream);
        if (v == null)
          syntaxError(currentStream,"Missing expression after '|'");
        vr = resolveMustExist(currentStream,vr).pipe(v.resolve());
      }
      else
        break;
    }
    return vr;
  }
  
  /** Skip an expression.
  *@param currentStream is the token stream to parse.
  *@return true if an expression was detected, false otherwise.
  */
  public boolean skipExpression(TokenStream currentStream)
    throws ScriptException
  {
    // Look for pipe operations
    if (skipExpression_1(currentStream) == false)
      return false;
    while (true)
    {
      Token t = currentStream.peek();
      if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("||"))
      {
        currentStream.skip();
        if (skipExpression_1(currentStream) == false)
          syntaxError(currentStream,"Missing expression after '||'");
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("|"))
      {
        currentStream.skip();
        if (skipExpression_1(currentStream) == false)
          syntaxError(currentStream,"Missing expression after '|'");
      }
      else
        break;
    }
    return true;
  }
  
  protected VariableReference evaluateExpression_1(TokenStream currentStream)
    throws ScriptException
  {
    // Look for ampersand operations
    VariableReference vr = evaluateExpression_2(currentStream);
    if (vr == null)
      return null;
    while (true)
    {
      Token t = currentStream.peek();
      if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("&&"))
      {
        currentStream.skip();
        VariableReference v = evaluateExpression_2(currentStream);
        if (v == null)
          syntaxError(currentStream,"Missing expression after '&&'");
        vr = resolveMustExist(currentStream,vr).doubleAmpersand(v.resolve());
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("&"))
      {
        currentStream.skip();
        VariableReference v = evaluateExpression_2(currentStream);
        if (v == null)
          syntaxError(currentStream,"Missing expression after '&'");
        vr = resolveMustExist(currentStream,vr).ampersand(v.resolve());
      }
      else
        break;
    }
    return vr;
  }
  
  protected boolean skipExpression_1(TokenStream currentStream)
    throws ScriptException
  {
    // Look for ampersand operations
    if (skipExpression_2(currentStream) == false)
      return false;
    while (true)
    {
      Token t = currentStream.peek();
      if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("&&"))
      {
        currentStream.skip();
        if (skipExpression_2(currentStream) == false)
          syntaxError(currentStream,"Missing expression after '&&'");
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("&"))
      {
        currentStream.skip();
        if (skipExpression_2(currentStream) == false)
          syntaxError(currentStream,"Missing expression after '&'");
      }
      else
        break;
    }
    return true;
  }

  protected VariableReference evaluateExpression_2(TokenStream currentStream)
    throws ScriptException
  {
    // Look for exclamation operations
    Token t = currentStream.peek();
    if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("!"))
    {
      currentStream.skip();
      VariableReference v = evaluateExpression_2(currentStream);
      if (v == null)
        syntaxError(currentStream,"Missing expression after '!'");
      return resolveMustExist(currentStream,v).unaryExclamation();
    }
    return evaluateExpression_3(currentStream);
  }
  
  protected boolean skipExpression_2(TokenStream currentStream)
    throws ScriptException
  {
    // Look for exclamation operations
    Token t = currentStream.peek();
    if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("!"))
    {
      currentStream.skip();
      if (skipExpression_2(currentStream) == false)
        syntaxError(currentStream,"Missing expression after '!'");
      return true;
    }
    return skipExpression_3(currentStream);
  }
  
  protected VariableReference evaluateExpression_3(TokenStream currentStream)
    throws ScriptException
  {
    // Look for comparison operations
    VariableReference vr = evaluateExpression_4(currentStream);
    if (vr == null)
      return null;
    while (true)
    {
      Token t = currentStream.peek();
      if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("=="))
      {
        currentStream.skip();
        VariableReference v = evaluateExpression_4(currentStream);
        if (v == null)
          syntaxError(currentStream,"Missing expression after '=='");
        vr = resolveMustExist(currentStream,vr).doubleEquals(v.resolve());
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("!="))
      {
        currentStream.skip();
        VariableReference v = evaluateExpression_4(currentStream);
        if (v == null)
          syntaxError(currentStream,"Missing expression after '!='");
        vr = resolveMustExist(currentStream,vr).exclamationEquals(v.resolve());
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("<"))
      {
        currentStream.skip();
        VariableReference v = evaluateExpression_4(currentStream);
        if (v == null)
          syntaxError(currentStream,"Missing expression after '<'");
        vr = resolveMustExist(currentStream,vr).lesserAngle(v.resolve());
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals(">"))
      {
        currentStream.skip();
        VariableReference v = evaluateExpression_4(currentStream);
        if (v == null)
          syntaxError(currentStream,"Missing expression after '>'");
        vr = resolveMustExist(currentStream,vr).greaterAngle(v.resolve());
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("<="))
      {
        currentStream.skip();
        VariableReference v = evaluateExpression_4(currentStream);
        if (v == null)
          syntaxError(currentStream,"Missing expression after '<='");
        vr = resolveMustExist(currentStream,vr).lesserAngleEquals(v.resolve());
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals(">="))
      {
        currentStream.skip();
        VariableReference v = evaluateExpression_4(currentStream);
        if (v == null)
          syntaxError(currentStream,"Missing expression after '>='");
        vr = resolveMustExist(currentStream,vr).greaterAngleEquals(v.resolve());
      }
      else
        break;
    }
    return vr;
  }
  
  protected boolean skipExpression_3(TokenStream currentStream)
    throws ScriptException
  {
    // Look for comparison operations
    if (skipExpression_4(currentStream) == false)
      return false;
    while (true)
    {
      Token t = currentStream.peek();
      if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("=="))
      {
        currentStream.skip();
        if (skipExpression_4(currentStream) == false)
          syntaxError(currentStream,"Missing expression after '=='");
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("!="))
      {
        currentStream.skip();
        if (skipExpression_4(currentStream) == false)
          syntaxError(currentStream,"Missing expression after '!='");
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("<"))
      {
        currentStream.skip();
        if (skipExpression_4(currentStream) == false)
          syntaxError(currentStream,"Missing expression after '<'");
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals(">"))
      {
        currentStream.skip();
        if (skipExpression_4(currentStream) == false)
          syntaxError(currentStream,"Missing expression after '>'");
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("<="))
      {
        currentStream.skip();
        if (skipExpression_4(currentStream) == false)
          syntaxError(currentStream,"Missing expression after '<='");
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals(">="))
      {
        currentStream.skip();
        if (skipExpression_4(currentStream) == false)
          syntaxError(currentStream,"Missing expression after '>='");
      }
      else
        break;
    }
    return true;
  }
  
  protected VariableReference evaluateExpression_4(TokenStream currentStream)
    throws ScriptException
  {
    // Look for +/- operations
    VariableReference vr = evaluateExpression_5(currentStream);
    if (vr == null)
      return null;
    while (true)
    {
      Token t = currentStream.peek();
      if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("+"))
      {
        currentStream.skip();
        VariableReference v = evaluateExpression_5(currentStream);
        if (v == null)
          syntaxError(currentStream,"Missing expression after '+'");
        vr = resolveMustExist(currentStream,vr).plus(v.resolve());
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("-"))
      {
        currentStream.skip();
        VariableReference v = evaluateExpression_5(currentStream);
        if (v == null)
          syntaxError(currentStream,"Missing expression after '-'");
        vr = resolveMustExist(currentStream,vr).minus(v.resolve());
      }
      else
        break;
    }
    return vr;
  }

  protected boolean skipExpression_4(TokenStream currentStream)
    throws ScriptException
  {
    // Look for +/- operations
    if (skipExpression_5(currentStream) == false)
      return false;
    while (true)
    {
      Token t = currentStream.peek();
      if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("+"))
      {
        currentStream.skip();
        if (skipExpression_5(currentStream) == false)
          syntaxError(currentStream,"Missing expression after '+'");
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("-"))
      {
        currentStream.skip();
        if (skipExpression_5(currentStream) == false)
          syntaxError(currentStream,"Missing expression after '-'");
      }
      else
        break;
    }
    return true;
  }

  protected VariableReference evaluateExpression_5(TokenStream currentStream)
    throws ScriptException
  {
    // Look for *// operations
    VariableReference vr = evaluateExpression_6(currentStream);
    if (vr == null)
      return null;
    while (true)
    {
      Token t = currentStream.peek();
      if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("*"))
      {
        currentStream.skip();
        VariableReference v = evaluateExpression_6(currentStream);
        if (v == null)
          syntaxError(currentStream,"Missing expression after '*'");
        vr = resolveMustExist(currentStream,vr).asterisk(v.resolve());
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("/"))
      {
        currentStream.skip();
        VariableReference v = evaluateExpression_6(currentStream);
        if (v == null)
          syntaxError(currentStream,"Missing expression after '/'");
        vr = resolveMustExist(currentStream,vr).slash(v.resolve());
      }
      else
        break;
    }
    return vr;
  }

  protected boolean skipExpression_5(TokenStream currentStream)
    throws ScriptException
  {
    // Look for *// operations
    if (skipExpression_6(currentStream) == false)
      return false;
    while (true)
    {
      Token t = currentStream.peek();
      if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("*"))
      {
        currentStream.skip();
        if (skipExpression_6(currentStream) == false)
          syntaxError(currentStream,"Missing expression after '*'");
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("/"))
      {
        currentStream.skip();
        if (skipExpression_6(currentStream) == false)
          syntaxError(currentStream,"Missing expression after '/'");
      }
      else
        break;
    }
    return true;
  }

  protected VariableReference evaluateExpression_6(TokenStream currentStream)
    throws ScriptException
  {
    // Look for - operations
    Token t = currentStream.peek();
    if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("-"))
    {
      currentStream.skip();
      VariableReference v = evaluateExpression_6(currentStream);
      if (v == null)
        syntaxError(currentStream,"Missing expression after '-'");
      return resolveMustExist(currentStream,v).unaryMinus();
    }
    return parseVariableReference(currentStream);
  }
  
  protected boolean skipExpression_6(TokenStream currentStream)
    throws ScriptException
  {
    // Look for - operations
    Token t = currentStream.peek();
    if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("-"))
    {
      currentStream.skip();
      if (skipExpression_6(currentStream) == false)
        syntaxError(currentStream,"Missing expression after '-'");
      return true;
    }
    return skipVariableReference(currentStream);
  }

  protected VariableReference parseVariableReference(TokenStream currentStream)
    throws ScriptException
  {
    // variable_reference -> 'isnull' variable_reference
    // variable_reference -> variable_reference_0
    Token t = currentStream.peek();
    if (t != null && t.getToken() != null && t.getToken().equals("isnull"))
    {
      currentStream.skip();
      VariableReference reference = parseVariableReference(currentStream);
      if (reference == null)
        syntaxError(currentStream,"Missing variable reference");
      return new VariableBoolean(reference.isNull());
    }
    else
      return parseVariableReference_0(currentStream);
  }

  protected boolean skipVariableReference(TokenStream currentStream)
    throws ScriptException
  {
    // variable_reference -> 'isnull' variable_reference
    // variable_reference -> variable_reference_0
    Token t = currentStream.peek();
    if (t != null && t.getToken() != null && t.getToken().equals("isnull"))
    {
      currentStream.skip();
      if (skipVariableReference(currentStream) == false)
        syntaxError(currentStream,"Missing variable reference");
      return true;
    }
    else
      return skipVariableReference_0(currentStream);
  }
  
  protected VariableReference parseVariableReference_0(TokenStream currentStream)
    throws ScriptException
  {
    // variable_reference -> variable_reference '[' expression ']'
    // variable_reference -> variable_reference.property_name
    // variable_reference -> variable_reference_1
    
    VariableReference vr = parseVariableReference_1(currentStream);
    if (vr == null)
      return vr;
    while (true)
    {
      Token t = currentStream.peek();
      if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("["))
      {
	currentStream.skip();
	VariableReference expression = evaluateExpression(currentStream);
	if (expression == null)
	  syntaxError(currentStream,"Missing expression after '['");
	Variable v = resolveMustExist(currentStream,vr);
	t = currentStream.peek();
	if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals("]"))
	  syntaxError(currentStream,"Missing ']'");
	currentStream.skip();
        vr = v.getIndexed(expression.resolve());
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("."))
      {
	currentStream.skip();
	t = currentStream.peek();
	if (t == null || t.getToken() == null)
	  syntaxError(currentStream,"Need attribute name");
	Variable v = resolveMustExist(currentStream,vr);
	currentStream.skip();
        vr = v.getAttribute(t.getToken());
      }
      else
	break;
    }
      
    return vr;
  }
  
  protected VariableReference parseVariableReference_1(TokenStream currentStream)
    throws ScriptException
  {
    Token t = currentStream.peek();
    if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("("))
    {
      currentStream.skip();
      VariableReference rval = evaluateExpression(currentStream);
      if (rval == null)
        syntaxError(currentStream,"Missing expression after '('");
      t = currentStream.peek();
      if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals(")"))
        syntaxError(currentStream,"Missing ')'");
      currentStream.skip();
      return rval;
    }
    else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("["))
    {
      currentStream.skip();
      VariableArray va = new VariableArray();
      t = currentStream.peek();
      if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals("]"))
      {
        while (true)
        {
          VariableReference vr = evaluateExpression(currentStream);
          if (vr == null)
            syntaxError(currentStream,"Missing expression in array initializer");
          va.insertAt(vr.resolve(),null);
          t = currentStream.peek();
          if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("]"))
            break;
          if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals(","))
            syntaxError(currentStream,"Missing ','");
          currentStream.skip();
        }
      }
      currentStream.skip();
      return va;
    }
    else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("{"))
    {
      currentStream.skip();
      VariableConfiguration va = new VariableConfiguration();
      t = currentStream.peek();
      if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals("}"))
      {
        while (true)
        {
          VariableReference vr = evaluateExpression(currentStream);
          if (vr == null)
            syntaxError(currentStream,"Missing expression in configuration object initializer");
          va.insertAt(vr.resolve(),null);
          t = currentStream.peek();
          if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("}"))
            break;
          if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals(","))
            syntaxError(currentStream,"Missing ','");
          currentStream.skip();
        }
      }
      currentStream.skip();
      return va;
    }
    else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("<<"))
    {
      currentStream.skip();
      // Parse the node type
      VariableReference nodeTypeRef = evaluateExpression(currentStream);
      if (nodeTypeRef == null)
        syntaxError(currentStream,"Missing node type");
      Variable nodeType = resolveMustExist(currentStream,nodeTypeRef);
      t = currentStream.peek();
      if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals(":"))
        syntaxError(currentStream,"Missing ':'");
      currentStream.skip();
      
      VariableConfigurationNode va = new VariableConfigurationNode(nodeType.getStringValue());

      // Parse the node value
      VariableReference nodeValueRef = evaluateExpression(currentStream);
      if (nodeValueRef == null)
        syntaxError(currentStream,"Missing node value");
      Variable nodeValue = resolveMustExist(currentStream,nodeValueRef);
      t = currentStream.peek();
      if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals(":"))
        syntaxError(currentStream,"Missing ':'");
      currentStream.skip();
      
      va.getAttribute(Variable.ATTRIBUTE_VALUE).setReference(nodeValue);
      
      // Parse the attributes
      t = currentStream.peek();
      if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals(":"))
      {
        while (true)
        {
          VariableReference vr = evaluateExpression(currentStream);
          if (vr == null)
            syntaxError(currentStream,"Missing name expression in configurationnode attribute initializer");
          Variable attrName = resolveMustExist(currentStream,vr);
          t = currentStream.peek();
          if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals("="))
            syntaxError(currentStream,"Missing '='");
          currentStream.skip();
          vr = evaluateExpression(currentStream);
          if (vr == null)
            syntaxError(currentStream,"Missing value expression in configurationnode attribute initializer");
          va.getAttribute(attrName.getStringValue()).setReference(vr.resolve());
          t = currentStream.peek();
          if (t != null && t.getPunctuation() != null && t.getPunctuation().equals(":"))
            break;
          if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals(","))
            syntaxError(currentStream,"Missing ','");
          currentStream.skip();
        }
      }
      currentStream.skip();

      // Parse the children
      t = currentStream.peek();
      if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals(">>"))
      {
        while (true)
        {
          VariableReference vr = evaluateExpression(currentStream);
          if (vr == null)
            syntaxError(currentStream,"Missing expression in configurationnode object initializer");
          va.insertAt(vr.resolve(),null);
          t = currentStream.peek();
          if (t != null && t.getPunctuation() != null && t.getPunctuation().equals(">>"))
            break;
          if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals(","))
            syntaxError(currentStream,"Missing ','");
          currentStream.skip();
        }
      }
      currentStream.skip();
      return va;
    }

    return parseVariableReference_2(currentStream);
  }
  
  protected VariableReference parseVariableReference_2(TokenStream currentStream)
    throws ScriptException
  {
    Token t = currentStream.peek();
    if (t != null && t.getToken() != null)
    {
      currentStream.skip();
      String variableName = t.getToken();
      if (variableName.equals("true"))
        return new VariableBoolean(true);
      else if (variableName.equals("false"))
        return new VariableBoolean(false);
      else if (variableName.equals("null"))
        return new NullVariableReference();
      else if (variableName.equals("new"))
      {
        // Parse the new operation name
        t = currentStream.peek();
        if (t == null || t.getToken() == null)
          syntaxError(currentStream,"Missing 'new' operation name");
        String operationName = t.getToken();
        // Look up operation
        NewOperation newOperation = newOperations.get(operationName);
        if (newOperation == null)
          syntaxError(currentStream,"New operation type is unknown");
        currentStream.skip();
        return newOperation.parseAndCreate(this,currentStream);
      }
      else
      {
        // Look up variable reference in current context
        ContextVariableReference x = context.get(variableName);
        if (x == null)
        {
          x = new ContextVariableReference();
          context.put(variableName,x);
        }
        return x;
      }
    }
    else if (t != null && t.getString() != null)
    {
      currentStream.skip();
      return new VariableString(t.getString());
    }
    else if (t != null && t.getFloat() != null)
    {
      currentStream.skip();
      return new VariableFloat(new Double(t.getFloat()).doubleValue());
    }
    else if (t != null && t.getInteger() != null)
    {
      currentStream.skip();
      return new VariableInt(Integer.parseInt(t.getInteger()));
    }
    else
      return null;
  }
  
  protected boolean skipVariableReference_0(TokenStream currentStream)
    throws ScriptException
  {
    if (skipVariableReference_1(currentStream) == false)
      return false;
    while (true)
    {
      Token t = currentStream.peek();
      if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("["))
      {
	currentStream.skip();
	if (skipExpression(currentStream) == false)
	  syntaxError(currentStream,"Missing expression after '['");
	t = currentStream.peek();
	if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals("]"))
	  syntaxError(currentStream,"Missing ']'");
	currentStream.skip();
      }
      else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("."))
      {
	currentStream.skip();
	t = currentStream.peek();
	if (t == null || t.getToken() == null)
	  syntaxError(currentStream,"Need property name");
	currentStream.skip();
      }
      else
	break;
    }
      
    return true;
  }

  protected boolean skipVariableReference_1(TokenStream currentStream)
    throws ScriptException
  {
    Token t = currentStream.peek();
    if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("("))
    {
      currentStream.skip();
      if (skipExpression(currentStream) == false)
        syntaxError(currentStream,"Missing expression after '('");
      t = currentStream.peek();
      if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals(")"))
        syntaxError(currentStream,"Missing ')'");
      currentStream.skip();
      return true;
    }
    else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("["))
    {
      currentStream.skip();
      t = currentStream.peek();
      if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals("]"))
      {
        while (true)
        {
          if (skipExpression(currentStream) == false)
            syntaxError(currentStream,"Missing expression in array initializer");
          t = currentStream.peek();
          if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("]"))
            break;
          if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals(","))
            syntaxError(currentStream,"Missing ','");
          currentStream.skip();
        }
      }
      currentStream.skip();
      return true;
    }
    else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("{"))
    {
      currentStream.skip();
      t = currentStream.peek();
      if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals("}"))
      {
        while (true)
        {
          if (skipExpression(currentStream) == false)
            syntaxError(currentStream,"Missing expression in configuration object initializer");
          t = currentStream.peek();
          if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("}"))
            break;
          if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals(","))
            syntaxError(currentStream,"Missing ','");
          currentStream.skip();
        }
      }
      currentStream.skip();
      return true;
    }
    else if (t != null && t.getPunctuation() != null && t.getPunctuation().equals("<<"))
    {
      currentStream.skip();
      // Parse the node type
      if (skipExpression(currentStream) == false)
        syntaxError(currentStream,"Missing node type");
      t = currentStream.peek();
      if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals(":"))
        syntaxError(currentStream,"Missing ':'");
      currentStream.skip();
      
      // Parse the node value
      if (skipExpression(currentStream) == false)
        syntaxError(currentStream,"Missing node value");
      t = currentStream.peek();
      if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals(":"))
        syntaxError(currentStream,"Missing ':'");
      currentStream.skip();
      
      // Parse the attributes
      t = currentStream.peek();
      if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals(":"))
      {
        while (true)
        {
          if (skipExpression(currentStream) == false)
            syntaxError(currentStream,"Missing name expression in configurationnode attribute initializer");
          t = currentStream.peek();
          if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals("="))
            syntaxError(currentStream,"Missing '='");
          currentStream.skip();
          if (skipExpression(currentStream) == false)
            syntaxError(currentStream,"Missing value expression in configurationnode attribute initializer");
          t = currentStream.peek();
          if (t != null && t.getPunctuation() != null && t.getPunctuation().equals(":"))
            break;
          if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals(","))
            syntaxError(currentStream,"Missing ','");
          currentStream.skip();
        }
      }
      currentStream.skip();

      // Parse the children
      t = currentStream.peek();
      if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals(">>"))
      {
        while (true)
        {
          if (skipExpression(currentStream) == false)
            syntaxError(currentStream,"Missing expression in configurationnode object initializer");
          t = currentStream.peek();
          if (t != null && t.getPunctuation() != null && t.getPunctuation().equals(">>"))
            break;
          if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals(","))
            syntaxError(currentStream,"Missing ','");
          currentStream.skip();
        }
      }
      currentStream.skip();
      return true;
    }

    return skipVariableReference_2(currentStream);
  }

  protected boolean skipVariableReference_2(TokenStream currentStream)
    throws ScriptException
  {
    Token t = currentStream.peek();
    if (t != null && t.getToken() != null)
    {
      currentStream.skip();
      if (t.getToken().equals("new"))
      {
        // Parse the new operation name
        t = currentStream.peek();
        if (t == null || t.getToken() == null)
          syntaxError(currentStream,"Missing 'new' operation name");
        String operationName = t.getToken();
        // Look up operation
        NewOperation newOperation = newOperations.get(operationName);
        if (newOperation == null)
          syntaxError(currentStream,"New operation type is unknown");
        currentStream.skip();
        newOperation.parseAndSkip(this,currentStream);
      }
      return true;
    }
    else if (t != null && t.getString() != null)
    {
      currentStream.skip();
      return true;
    }
    else if (t != null && t.getFloat() != null)
    {
      currentStream.skip();
      return true;
    }
    else if (t != null && t.getInteger() != null)
    {
      currentStream.skip();
      return true;
    }

    return false;
  }
  
  public static void syntaxError(TokenStream currentStream, String message)
    throws ScriptException
  {
    localError(currentStream,"Syntax error: "+message);
  }
  
  public static Variable resolveMustExist(TokenStream currentStream, VariableReference vr)
    throws ScriptException
  {
    Variable v = vr.resolve();
    if (v == null)
      localError(currentStream,"Expression cannot be null");
    return v;
  }
  
  public static void localError(TokenStream currentStream, String message)
    throws ScriptException
  {
    Token t = currentStream.peek();
    if (t == null)
      throw new ScriptException(message+", at end of file");
    else
      t.throwException(message + ": "+t);
  }
  
  public static String convertToString(HttpResponse httpResponse)
    throws IOException
  {
    HttpEntity entity = httpResponse.getEntity();
    if (entity != null)
    {
      InputStream is = entity.getContent();
      try
      {
        Charset charSet;
        try
        {
          ContentType ct = ContentType.get(entity);
          if (ct == null)
            charSet = StandardCharsets.UTF_8;
          else
            charSet = ct.getCharset();
        }
        catch (ParseException e)
        {
          charSet = StandardCharsets.UTF_8;
        }
        char[] buffer = new char[65536];
        Reader r = new InputStreamReader(is,charSet);
        Writer w = new StringWriter();
        try
        {
          while (true)
          {
            int amt = r.read(buffer);
            if (amt == -1)
              break;
            w.write(buffer,0,amt);
          }
        }
        finally
        {
          w.flush();
        }
        return w.toString();
      }
      finally
      {
        is.close();
      }
    }
    return "";
  }
  
  public HttpClient getHttpClient()
  {
    synchronized (httpClientLock)
    {
      if (httpClient == null)
      {
        int socketTimeout = 900000;
        int connectionTimeout = 300000;

        PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager();
        poolingConnectionManager.setDefaultMaxPerRoute(1);
        poolingConnectionManager.setValidateAfterInactivity(60000);
        poolingConnectionManager.setDefaultSocketConfig(SocketConfig.custom()
          .setTcpNoDelay(true)
          .setSoTimeout(socketTimeout)
          .build());
        connectionManager = poolingConnectionManager;
        
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

        RequestConfig.Builder requestBuilder = RequestConfig.custom()
          .setCircularRedirectsAllowed(true)
          .setSocketTimeout(socketTimeout)
          .setExpectContinueEnabled(true)
          .setConnectTimeout(connectionTimeout)
          .setConnectionRequestTimeout(socketTimeout);

        httpClient = HttpClients.custom()
          .setConnectionManager(connectionManager)
          .setMaxConnTotal(1)
          .disableAutomaticRetries()
          .setDefaultRequestConfig(requestBuilder.build())
          .setDefaultCredentialsProvider(credentialsProvider)
          //.setSSLSocketFactory(myFactory)
          .setRequestExecutor(new HttpRequestExecutor(socketTimeout))
          .setRedirectStrategy(new LaxRedirectStrategy())
          .build();

      }
    }
    return httpClient;
  }
  
  public static void main(String[] argv)
  {
    // Usage: ScriptParser [<filename> [arg1 [arg2 ...]]
    
    ScriptParser sp = new ScriptParser();
    
    // Initialize script parser with the appropriate commands.
    sp.addCommand("break",new BreakCommand());
    sp.addCommand("print",new PrintCommand());
    sp.addCommand("if",new IfCommand());
    sp.addCommand("while",new WhileCommand());
    sp.addCommand("set",new SetCommand());
    sp.addCommand("insert",new InsertCommand());
    sp.addCommand("remove",new RemoveCommand());
    sp.addCommand("error",new ErrorCommand());
    sp.addCommand("wait",new WaitCommand());
    
    sp.addCommand("GET",new GETCommand());
    sp.addCommand("PUT",new PUTCommand());
    sp.addCommand("DELETE",new DELETECommand());
    sp.addCommand("POST", new POSTCommand());
    
    // Add the new operations we need
    sp.addNewOperation("configuration",new NewConfiguration());
    sp.addNewOperation("configurationnode",new NewConfigurationNode());
    sp.addNewOperation("url",new NewURL());
    sp.addNewOperation("connectionname",new NewConnectionName());
    sp.addNewOperation("array",new NewArray());
    sp.addNewOperation("dictionary",new NewDictionary());
    sp.addNewOperation("queryarg",new NewQueryArgument());
    
    try
    {
      Reader reader;
      if (argv.length >= 1)
      {
        File inputFile = new File(argv[0]);
        reader = new InputStreamReader(new FileInputStream(inputFile),StandardCharsets.UTF_8);
        VariableArray va = new VariableArray();
        int i = 0;
        while (i < argv.length - 1)
        {
          String arg = argv[i+1];
          va.insertAt(new VariableString(arg),null);
          i++;
        }
        sp.addVariable("__args__",va);
        TokenStream ts = new BasicTokenStream(reader);
        sp.execute(ts);
      }
      else
      {
        reader = new InputStreamReader(System.in, StandardCharsets.UTF_8);
      
        while (true)
        {
          TokenStream ts = new BasicTokenStream(reader);
          try
          {
            sp.execute(ts);
            break;
          }
          catch (ScriptException e)
          {
            System.out.println("Error: "+e.getMessage());
            if (ts.peek() == null)
              break;
          }
        }
      }
    }
    catch (Exception e)
    {
      e.printStackTrace(System.err);
      System.exit(2);
    }
  }
  
}
