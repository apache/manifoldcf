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

/** Convert a script into a stream of tokens.
*/
public class TokenStream
{
  protected String fileName;
  protected String code;
  protected int characterPosition = 0;
  protected Token currentToken = null;
  
  public TokenStream(String fileName, String code)
  {
    this.fileName = fileName;
    this.code = code;
  }
  
  public int getCharacterPosition()
  {
    return characterPosition;
  }
  
  public void setCharacterPosition(int characterPosition)
  {
    this.characterPosition = characterPosition;
    currentToken = null;
  }
  
  public Token peek()
  {
    if (currentToken == null)
      currentToken = parseNextToken();
    return currentToken;
  }
  
  public void skip()
  {
    currentToken = null;
  }
  
  public String getFileName()
  {
    return fileName;
  }

  // Protected methods
  
  protected Token parseNextToken()
  {
    // Skip to the start of the next token (or exit if no token start can be found)
    while (true)
    {
      // Skip white space
      while (characterPosition < code.length())
      {
        char x = code.charAt(characterPosition);
        if (x > ' ')
          break;
        characterPosition++;
      }
      
      // End of text?
      if (characterPosition == code.length())
        return null;
      
      // Is it a comment?
      char y = code.charAt(characterPosition);
      if (y == '#')
      {
        // Skip to the end of the line, or the end of the text.
        while (characterPosition < code.length())
        {
          char x = code.charAt(characterPosition++);
          if (x == '\n')
            break;
        }
        continue;
      }
      break;
    }

    // At the start of the next token.
    // Figure out what kind of token it is.  Currently we only care about punctuation
    // and strings.  Furthermore, no distinction is made between quoted and unquoted
    // strings.
    char z = code.charAt(characterPosition);
    if (isLegalTokenCharacter(z))
    {
      // Legal token character
      StringBuilder tokenBuffer = new StringBuilder();
      int quoteMark = -1;
      while (characterPosition < code.length())
      {
        char x = code.charAt(characterPosition);
        if (quoteMark == -1)
        {
          // Not yet in a quote
          if (x <= ' ' || x == '#')
            break;
          if (x == '\'' || x == '"')
          {
            characterPosition++;
            quoteMark = (int)x;
          }
          else if (isLegalTokenCharacter(x))
          {
            characterPosition++;
            tokenBuffer.append(x);
          }
          else
            break;
        }
        else
        {
          characterPosition++;
          // In a quote.
          if (((int)x) == quoteMark)
            // End of the quotation
            break;
          if (x == '\\')
          {
            // Escape character
            if (characterPosition < code.length())
              tokenBuffer.append(code.charAt(characterPosition++));
          }
          else
            tokenBuffer.append(x);
        }
      }
      return new Token(Token.TOKEN_STRING,tokenBuffer.toString());
    }
    else
    {
      // Set a punctuation token
      characterPosition++;
      return new Token(Token.TOKEN_PUNCTUATION,new StringBuilder(z).toString());
    }
  }
  
  protected static boolean isLegalTokenCharacter(char x)
  {
    if (x >= '0' && x <= '9')
      return true;
    if (x >= 'a' && x <= 'z')
      return true;
    if (x >= 'A' && x <='Z')
      return true;
    if (x == '_' || x == '$' || x == '@')
      return true;
    return false;
  }
  
}
