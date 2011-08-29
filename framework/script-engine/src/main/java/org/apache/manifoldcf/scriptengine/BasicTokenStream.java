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

import java.io.*;

/** Convert a sequence of characters into a stream of tokens.
*/
public class BasicTokenStream implements TokenStream
{
  protected CharacterStream characterStream;
  protected Token currentToken = null;
  
  public BasicTokenStream(Reader reader)
  {
    this.characterStream = new CharacterStream(reader);
  }
  
  public Token peek()
    throws ScriptException
  {
    if (currentToken == null)
      currentToken = parseNextToken();
    return currentToken;
  }
  
  public void skip()
  {
    currentToken = null;
  }
  
  // Protected methods
  
  protected Token parseNextToken()
    throws ScriptException
  {
    char y;
    int lineNumber;
    int characterNumber;

    // Skip to the start of the next token (or exit if no token start can be found)
    while (true)
    {
      
      // Skip white space
      while (true)
      {
        // Remember current position so we can properly characterize the token.
        lineNumber = characterStream.getLineNumber();
        characterNumber = characterStream.getCharacterNumber();
        int x = characterStream.peek();
        if (x == -1)
          return null;
        y = (char)x;
        if (y > ' ')
          break;
        characterStream.skip();
      }

      // Is it a comment?
      if (y == '#')
      {
        // Skip to the end of the line, or the end of the text.
        while (true)
        {
          int x = characterStream.peek();
          if (x == -1)
            return null;
          characterStream.skip();
          y = (char)x;
          if (y == '\n')
            break;
        }
        continue;
      }
      break;
    }

    // At the start of the next token.
    if (isQuoteCharacter(y))
    {
      // Legal token character
      StringBuilder tokenBuffer = new StringBuilder();
      char quoteMark = y;
      characterStream.skip();
      while (true)
      {
        int x = characterStream.peek();
        if (x == -1)
          break;
        y = (char)x;
        characterStream.skip();
        if (y == quoteMark)
          // End of the quotation
          break;
        if (y == '\\')
        {
          // Escape character
          x = characterStream.peek();
          if (x != -1)
          {
            tokenBuffer.append((char)x);
            characterStream.skip();
          }
        }
        else
          tokenBuffer.append(y);
      }
      return new Token(Token.TOKEN_STRING,tokenBuffer.toString(),lineNumber,characterNumber);
    }
    else if (isTokenCharacter(y))
    {
      // Legal token character
      StringBuilder tokenBuffer = new StringBuilder();
      while (true)
      {
        int x = characterStream.peek();
        if (x == -1)
          break;
        y = (char)x;
        if (isTokenCharacter(y) || isNumberCharacter(y))
        {
          // Tokens can include numbers, just so long they aren't the first character
          characterStream.skip();
          tokenBuffer.append(y);
        }
        else
          break;
      }
      return new Token(Token.TOKEN_TOKEN,tokenBuffer.toString(),lineNumber,characterNumber);
    }
    else if (isNumberCharacter(y))
    {
      StringBuilder tokenBuffer = new StringBuilder();
      boolean seenDot = false;
      while (true)
      {
        int x = characterStream.peek();
        if (x == -1)
          break;
        y = (char)x;
        if (isNumberCharacter(y))
        {
          tokenBuffer.append(y);
          characterStream.skip();
        }
        else if (y == '.' && seenDot == false)
        {
          tokenBuffer.append(y);
          seenDot = true;
          characterStream.skip();
        }
        else
          break;
      }
      if (seenDot)
        return new Token(Token.TOKEN_FLOAT,tokenBuffer.toString(),lineNumber,characterNumber);
      else
        return new Token(Token.TOKEN_INTEGER,tokenBuffer.toString(),lineNumber,characterNumber);
    }
    else
    {
      // Set a punctuation token
      characterStream.skip();
      if (y == '=' || y == '!' || y == '>' || y == '<' || y == '&' || y == '|')
      {
        int x = characterStream.peek();
        if (x != -1)
        {
          char q = (char)x;
          if (y == '=' && q == '=' ||
            y == '!' && q == '=' ||
            y == '>' && q == '=' ||
            y == '<' && q == '=' ||
            y == '&' && q == '&' ||
            y == '|' && q == '|' ||
            y == '>' && q == '>' ||
            y == '<' && q == '<')
          {
            characterStream.skip();
            return new Token(Token.TOKEN_PUNCTUATION,new StringBuilder().append(y).append(q).toString(),
              lineNumber,characterNumber);
          }
        }
      }
      return new Token(Token.TOKEN_PUNCTUATION,new StringBuilder().append(y).toString(),
        lineNumber,characterNumber);
    }
  }
  
  protected static boolean isQuoteCharacter(char x)
  {
    return (x == '\'' || x == '"');
  }
  
  protected static boolean isNumberCharacter(char x)
  {
    return (x >= '0' && x <= '9');
  }
  
  protected static boolean isTokenCharacter(char x)
  {
    if (x >= 'a' && x <= 'z')
      return true;
    if (x >= 'A' && x <='Z')
      return true;
    if (x == '_' || x == '$' || x == '@')
      return true;
    return false;
  }
  
}
