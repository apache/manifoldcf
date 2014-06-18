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

public class Token
{
  public static final int TOKEN_PUNCTUATION = 0;
  public static final int TOKEN_STRING = 1;
  public static final int TOKEN_INTEGER = 2;
  public static final int TOKEN_FLOAT = 3;
  public static final int TOKEN_TOKEN = 4;
  
  protected int tokenType;
  protected String tokenValue;
  protected int lineNumber;
  protected int characterPosition;
  
  public Token(int type, String value, int lineNumber, int characterPosition)
  {
    this.tokenType = type;
    this.tokenValue = value;
    this.lineNumber = lineNumber;
    this.characterPosition = characterPosition;
  }
  
  public void throwException(String message)
    throws ScriptException
  {
    throw new ScriptException(message+" at line "+lineNumber+" position "+characterPosition);
  }
  
  public String getPunctuation()
  {
    if (tokenType == TOKEN_PUNCTUATION)
      return tokenValue;
    return null;
  }
  
  public String getToken()
  {
    if (tokenType == TOKEN_TOKEN)
      return tokenValue;
    return null;
  }
  
  public String getString()
  {
    if (tokenType == TOKEN_STRING)
      return tokenValue;
    return null;
  }
  
  public String getFloat()
  {
    if (tokenType == TOKEN_FLOAT)
      return tokenValue;
    return null;
  }
  
  public String getInteger()
  {
    if (tokenType == TOKEN_INTEGER)
      return tokenValue;
    return null;
  }
  
  public String toString()
  {
    return "Type: "+Integer.toString(tokenType)+" Value: '"+tokenValue+"' Line: "+Integer.toString(lineNumber)+" Char: "+Integer.toString(characterPosition);
  }
  
}