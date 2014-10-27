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
package org.apache.manifoldcf.connectorcommon.jsongen;

import java.io.*;

/** This class describes a JSON string reader. */
public class JSONStringReader extends JSONReader
{
  // Strings need to be escaped, therefore we have our own.
  /*
   All Unicode characters may be placed within the
   quotation marks except for the characters that must be escaped:
   quotation mark, reverse solidus, and the control characters (U+0000
   through U+001F).

   Any character may be escaped.  If the character is in the Basic
   Multilingual Plane (U+0000 through U+FFFF), then it may be
   represented as a six-character sequence: a reverse solidus, followed
   by the lowercase letter u, followed by four hexadecimal digits that
   encode the character's code point.  The hexadecimal letters A though
   F can be upper or lowercase.  So, for example, a string containing
   only a single reverse solidus character may be represented as
   "\u005C".

   Alternatively, there are two-character sequence escape
   representations of some popular characters.  So, for example, a
   string containing only a single reverse solidus character may be
   represented more compactly as "\\".
  */
  
  protected final static int STATE_PREQUOTE = 0;
  protected final static int STATE_U = 1;
  protected final static int STATE_1ST = 2;
  protected final static int STATE_2ND = 3;
  protected final static int STATE_3RD = 4;
  protected final static int STATE_4TH = 5;
  protected final static int STATE_NEXTCHAR = 6;
  protected final static int STATE_DONE = 7;

  protected final Reader inputReader;
  
  protected int state = STATE_PREQUOTE;
  protected String escapedChar;

  public JSONStringReader(String value)
  {
    inputReader = new StringReader(value);
  }
  
  public JSONStringReader(Reader value)
  {
    inputReader = value;
  }

  @Override
  public int read()
    throws IOException
  {
    int x;
    switch (state)
    {
    case STATE_PREQUOTE:
      state = STATE_NEXTCHAR;
      return '"';
    case STATE_NEXTCHAR:
      x = inputReader.read();
      if (x == -1)
      {
        state = STATE_DONE;
        return '"';
      }
      else
      {
        if (x < ' ' || x == '"' || x == '\\')
        {
          escapedChar = "000" + Integer.toHexString(x);
          escapedChar = escapedChar.substring(escapedChar.length()-4);
          state = STATE_U;
          return '\\';
        }
        return x;
      }
    case STATE_U:
      state = STATE_1ST;
      return 'u';
    case STATE_1ST:
      state = STATE_2ND;
      return escapedChar.charAt(0);
    case STATE_2ND:
      state = STATE_3RD;
      return escapedChar.charAt(1);
    case STATE_3RD:
      state = STATE_4TH;
      return escapedChar.charAt(2);
    case STATE_4TH:
      state = STATE_NEXTCHAR;
      return escapedChar.charAt(3);
    case STATE_DONE:
      return -1;
    default:
      throw new IllegalStateException("Unknown state: "+state);
    }
  }
}


