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

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.client.HttpClient;
import org.apache.http.HttpStatus;
import org.apache.http.HttpException;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** PUT command.  This performs a REST-style PUT operation, designed to work
* against the ManifoldCF API.  The syntax is: PUT resultvariable = sendvariable to urlvariable*/
public class PUTCommand implements Command
{
  /** Parse and execute.  Parsing begins right after the command name, and should stop before the trailing semicolon.
  *@param sp is the script parser to use to help in the parsing.
  *@param currentStream is the current token stream.
  *@return true to send a break signal, false otherwise.
  */
  public boolean parseAndExecute(ScriptParser sp, TokenStream currentStream)
    throws ScriptException
  {
    VariableReference result = sp.evaluateExpression(currentStream);
    if (result == null)
      sp.syntaxError(currentStream,"Missing result expression");
    Token t = currentStream.peek();
    if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals("="))
      sp.syntaxError(currentStream,"Missing '=' sign");
    currentStream.skip();
    VariableReference send = sp.evaluateExpression(currentStream);
    if (send == null)
      sp.syntaxError(currentStream,"Missing send expression");
    t = currentStream.peek();
    if (t == null || t.getToken() == null || !t.getToken().equals("to"))
      sp.syntaxError(currentStream,"Missing 'to'");
    currentStream.skip();
    VariableReference url = sp.evaluateExpression(currentStream);
    if (url == null)
      sp.syntaxError(currentStream,"Missing URL expression");
    
    // Perform the actual PUT.
    String urlString = sp.resolveMustExist(currentStream,url).getStringValue();
    Configuration configuration = sp.resolveMustExist(currentStream,send).getConfigurationValue();

    try
    {
      String json = configuration.toJSON();
      HttpClient client = sp.getHttpClient();
      HttpPut method = new HttpPut(urlString);
      try
      {
        method.setEntity(new StringEntity(json,ContentType.create("text/plain", StandardCharsets.UTF_8)));
        HttpResponse httpResponse = client.execute(method);
        int resultCode = httpResponse.getStatusLine().getStatusCode();
        String resultJSON = sp.convertToString(httpResponse);
        result.setReference(new VariableResult(resultCode,resultJSON));
      
        return false;
      }
      finally
      {
        //method.releaseConnection();
      }
    }
    catch (ManifoldCFException e)
    {
      throw new ScriptException(e.getMessage(),e);
    }
    catch (IOException e)
    {
      throw new ScriptException(e.getMessage(),e);
    }
  }
  
  /** Parse and skip.  Parsing begins right after the command name, and should stop before the trailing semicolon.
  *@param sp is the script parser to use to help in the parsing.
  *@param currentStream is the current token stream.
  */
  public void parseAndSkip(ScriptParser sp, TokenStream currentStream)
    throws ScriptException
  {
    if (sp.skipExpression(currentStream) == false)
      sp.syntaxError(currentStream,"Missing result expression");
    Token t = currentStream.peek();
    if (t == null || t.getPunctuation() == null || !t.getPunctuation().equals("="))
      sp.syntaxError(currentStream,"Missing '=' sign");
    currentStream.skip();
    if (sp.skipExpression(currentStream) == false)
      sp.syntaxError(currentStream,"Missing send expression");
    t = currentStream.peek();
    if (t == null || t.getToken() == null || !t.getToken().equals("to"))
      sp.syntaxError(currentStream,"Missing 'to'");
    currentStream.skip();
    if (sp.skipExpression(currentStream) == false)
      sp.syntaxError(currentStream,"Missing URL expression");
  }

}
