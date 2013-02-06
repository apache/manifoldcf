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
package org.apache.manifoldcf.core.fuzzyml;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.Logging;
import java.util.*;

/** This class represents the basic, outermost parse state. */
public class BasicParseState
{
  protected static final int BASICPARSESTATE_NORMAL = 0;
  protected static final int BASICPARSESTATE_SAWLEFTBRACKET = 1;
  protected static final int BASICPARSESTATE_SAWEXCLAMATION = 2;
  protected static final int BASICPARSESTATE_SAWDASH = 3;
  protected static final int BASICPARSESTATE_IN_COMMENT = 4;
  protected static final int BASICPARSESTATE_SAWCOMMENTDASH = 5;
  protected static final int BASICPARSESTATE_SAWSECONDCOMMENTDASH = 6;
  protected static final int BASICPARSESTATE_IN_TAG_NAME = 7;
  protected static final int BASICPARSESTATE_IN_ATTR_NAME = 8;
  protected static final int BASICPARSESTATE_IN_ATTR_VALUE = 9;
  protected static final int BASICPARSESTATE_IN_TAG_SAW_SLASH = 10;
  protected static final int BASICPARSESTATE_IN_END_TAG_NAME = 11;
  protected static final int BASICPARSESTATE_IN_ATTR_LOOKING_FOR_VALUE = 12;
  protected static final int BASICPARSESTATE_IN_SINGLE_QUOTES_ATTR_VALUE = 13;
  protected static final int BASICPARSESTATE_IN_DOUBLE_QUOTES_ATTR_VALUE = 14;
  protected static final int BASICPARSESTATE_IN_UNQUOTED_ATTR_VALUE = 15;


  protected int currentState = BASICPARSESTATE_NORMAL;

  protected StringBuilder currentTagNameBuffer = null;
  protected StringBuilder currentAttrNameBuffer = null;
  protected StringBuilder currentValueBuffer = null;

  protected String currentTagName = null;
  protected String currentAttrName = null;
  protected Map<String,String> currentAttrMap = null;

  protected static final Map<String,String> mapLookup = new HashMap<String,String>();
  static
  {
    mapLookup.put("amp","&");
    mapLookup.put("lt","<");
    mapLookup.put("gt",">");
    mapLookup.put("quot","\"");
  }

  public BasicParseState()
  {
  }

  /** Deal with a character.  No exceptions are allowed, since those would represent syntax errors, and we don't want those to cause difficulty. */
  public void dealWithCharacter(char thisChar)
    throws ManifoldCFException
  {
    // At this level we want basic lexical analysis - that is, we deal with identifying tags and comments, that's it.
    char thisCharLower = Character.toLowerCase(thisChar);
    switch (currentState)
    {
    case BASICPARSESTATE_NORMAL:
      if (thisChar == '<')
        currentState = BASICPARSESTATE_SAWLEFTBRACKET;
      else
        noteNormalCharacter(thisChar);
      break;
    case BASICPARSESTATE_SAWLEFTBRACKET:
      if (thisChar == '!')
        currentState = BASICPARSESTATE_SAWEXCLAMATION;
      else if (thisChar == '/')
      {
        currentState = BASICPARSESTATE_IN_END_TAG_NAME;
        currentTagNameBuffer = new StringBuilder();
      }
      else
      {
        currentState = BASICPARSESTATE_IN_TAG_NAME;
        currentTagNameBuffer = new StringBuilder();
        if (!isHTMLWhitespace(thisChar))
          currentTagNameBuffer.append(thisCharLower);
      }
      break;
    case BASICPARSESTATE_SAWEXCLAMATION:
      if (thisChar == '-')
        currentState = BASICPARSESTATE_SAWDASH;
      else
        currentState = BASICPARSESTATE_NORMAL;
      break;
    case BASICPARSESTATE_SAWDASH:
      if (thisChar == '-')
        currentState = BASICPARSESTATE_IN_COMMENT;
      else
        currentState = BASICPARSESTATE_NORMAL;
      break;
    case BASICPARSESTATE_IN_COMMENT:
      // We're in a comment.  All we should look for is the end of the comment.
      if (thisChar == '-')
        currentState = BASICPARSESTATE_SAWCOMMENTDASH;
      break;
    case BASICPARSESTATE_SAWCOMMENTDASH:
      if (thisChar == '-')
        currentState = BASICPARSESTATE_SAWSECONDCOMMENTDASH;
      else
        currentState = BASICPARSESTATE_IN_COMMENT;
      break;
    case BASICPARSESTATE_SAWSECONDCOMMENTDASH:
      if (thisChar == '>')
        currentState = BASICPARSESTATE_NORMAL;
      else if (thisChar != '-')
        currentState = BASICPARSESTATE_IN_COMMENT;
      break;
    case BASICPARSESTATE_IN_TAG_NAME:
      if (isHTMLWhitespace(thisChar))
      {
        if (currentTagNameBuffer.length() > 0)
        {
          // Done with the tag name!
          currentTagName = currentTagNameBuffer.toString();
          currentTagNameBuffer = null;
          currentAttrMap = new HashMap<String,String>();
          currentState = BASICPARSESTATE_IN_ATTR_NAME;
          currentAttrNameBuffer = new StringBuilder();
        }
      }
      else if (thisChar == '/')
      {
        if (currentTagNameBuffer.length() > 0)
        {
          currentTagName = currentTagNameBuffer.toString();
          currentTagNameBuffer = null;
          currentAttrMap = new HashMap<String,String>();
          currentState = BASICPARSESTATE_IN_TAG_SAW_SLASH;
          noteTag(currentTagName,currentAttrMap);
        }
        else
        {
          currentState = BASICPARSESTATE_NORMAL;
          currentTagNameBuffer = null;
        }
      }
      else if (thisChar == '>')
      {
        if (currentTagNameBuffer.length() > 0)
        {
          currentTagName = currentTagNameBuffer.toString();
          currentTagNameBuffer = null;
          currentAttrMap = new HashMap<String,String>();
        }
        if (currentTagName != null)
        {
          noteTag(currentTagName,currentAttrMap);
        }
        currentState = BASICPARSESTATE_NORMAL;
        currentTagName = null;
        currentAttrMap = null;
      }
      else
        currentTagNameBuffer.append(thisCharLower);
      break;
    case BASICPARSESTATE_IN_ATTR_NAME:
      if (isHTMLWhitespace(thisChar))
      {
        if (currentAttrNameBuffer.length() > 0)
        {
          // Done with attr name!
          currentAttrName = currentAttrNameBuffer.toString();
          currentAttrNameBuffer = null;
          currentState = BASICPARSESTATE_IN_ATTR_LOOKING_FOR_VALUE;
        }
      }
      else if (thisChar == '=')
      {
        if (currentAttrNameBuffer.length() > 0)
        {
          currentAttrName = currentAttrNameBuffer.toString();
          currentAttrNameBuffer = null;
          currentState = BASICPARSESTATE_IN_ATTR_VALUE;
          currentValueBuffer = new StringBuilder();
        }
      }
      else if (thisChar == '/')
      {
        if (currentAttrNameBuffer.length() > 0)
        {
          currentAttrName = currentAttrNameBuffer.toString();
          currentAttrNameBuffer = null;
        }
        if (currentAttrName != null)
        {
          currentAttrMap.put(currentAttrName,"");
          currentAttrName = null;
        }
        noteTag(currentTagName,currentAttrMap);
        currentState = BASICPARSESTATE_IN_TAG_SAW_SLASH;
      }
      else if (thisChar == '>')
      {
        if (currentAttrNameBuffer.length() > 0)
        {
          currentAttrName = currentAttrNameBuffer.toString();
          currentAttrNameBuffer = null;
        }
        if (currentAttrName != null)
        {
          currentAttrMap.put(currentAttrName,"");
          currentAttrName = null;
        }
        currentState = BASICPARSESTATE_NORMAL;
        noteTag(currentTagName,currentAttrMap);
        currentTagName = null;
        currentAttrMap = null;
      }
      else
        currentAttrNameBuffer.append(thisCharLower);
      break;
    case BASICPARSESTATE_IN_ATTR_LOOKING_FOR_VALUE:
      if (thisChar == '=')
      {
        currentState = BASICPARSESTATE_IN_ATTR_VALUE;
        currentValueBuffer = new StringBuilder();
      }
      else if (thisChar == '>')
      {
        currentState = BASICPARSESTATE_NORMAL;
        noteTag(currentTagName,currentAttrMap);
        currentTagName = null;
        currentAttrMap = null;
      }
      else if (thisChar == '/')
      {
        currentState = BASICPARSESTATE_IN_TAG_SAW_SLASH;
        currentAttrMap.put(currentAttrName,"");
        currentAttrName = null;
        noteTag(currentTagName,currentAttrMap);
      }
      else if (!isHTMLWhitespace(thisChar))
      {
        currentAttrMap.put(currentAttrName,"");
        currentState = BASICPARSESTATE_IN_ATTR_NAME;
        currentAttrNameBuffer = new StringBuilder();
        currentAttrNameBuffer.append(thisCharLower);
        currentAttrName = null;
      }
      break;
    case BASICPARSESTATE_IN_ATTR_VALUE:
      if (thisChar == '\'')
        currentState = BASICPARSESTATE_IN_SINGLE_QUOTES_ATTR_VALUE;
      else if (thisChar == '"')
        currentState = BASICPARSESTATE_IN_DOUBLE_QUOTES_ATTR_VALUE;
      else if (!isHTMLWhitespace(thisChar))
      {
        currentState = BASICPARSESTATE_IN_UNQUOTED_ATTR_VALUE;
        currentValueBuffer.append(thisChar);
      }
      break;
    case BASICPARSESTATE_IN_TAG_SAW_SLASH:
      if (thisChar == '>')
      {
        noteEndTag(currentTagName);
        currentState = BASICPARSESTATE_NORMAL;
        currentTagName = null;
        currentAttrMap = null;
      }
      break;
    case BASICPARSESTATE_IN_END_TAG_NAME:
      if (isHTMLWhitespace(thisChar))
      {
        if (currentTagNameBuffer != null && currentTagNameBuffer.length() > 0)
        {
          // Done with the tag name!
          currentTagName = currentTagNameBuffer.toString();
          currentTagNameBuffer = null;
        }
      }
      else if (thisChar == '>')
      {
        if (currentTagNameBuffer != null && currentTagNameBuffer.length() > 0)
        {
          currentTagName = currentTagNameBuffer.toString();
          currentTagNameBuffer = null;
        }
        if (currentTagName != null)
        {
          noteEndTag(currentTagName);
        }
        currentTagName = null;
        currentState = BASICPARSESTATE_NORMAL;
      }
      else if (currentTagNameBuffer != null)
        currentTagNameBuffer.append(thisCharLower);
      break;
    case BASICPARSESTATE_IN_SINGLE_QUOTES_ATTR_VALUE:
      if (thisChar == '\'' || thisChar == '\n' || thisChar == '\r')
      {
        currentAttrMap.put(currentAttrName,attributeDecode(currentValueBuffer.toString()));
        currentAttrName = null;
        currentValueBuffer = null;
        currentState = BASICPARSESTATE_IN_ATTR_NAME;
        currentAttrNameBuffer = new StringBuilder();
      }
      else
        currentValueBuffer.append(thisChar);
      break;
    case BASICPARSESTATE_IN_DOUBLE_QUOTES_ATTR_VALUE:
      if (thisChar == '"' || thisChar == '\n' || thisChar == '\r')
      {
        currentAttrMap.put(currentAttrName,attributeDecode(currentValueBuffer.toString()));
        currentAttrName = null;
        currentValueBuffer = null;
        currentState = BASICPARSESTATE_IN_ATTR_NAME;
        currentAttrNameBuffer = new StringBuilder();
      }
      else
        currentValueBuffer.append(thisChar);
      break;
    case BASICPARSESTATE_IN_UNQUOTED_ATTR_VALUE:
      if (isHTMLWhitespace(thisChar))
      {
        currentAttrMap.put(currentAttrName,attributeDecode(currentValueBuffer.toString()));
        currentAttrName = null;
        currentValueBuffer = null;
        currentState = BASICPARSESTATE_IN_ATTR_NAME;
        currentAttrNameBuffer = new StringBuilder();
      }
      else if (thisChar == '/')
      {
        currentAttrMap.put(currentAttrName,attributeDecode(currentValueBuffer.toString()));
        noteTag(currentTagName,currentAttrMap);
        currentState = BASICPARSESTATE_IN_TAG_SAW_SLASH;
      }
      else if (thisChar == '>')
      {
        currentAttrMap.put(currentAttrName,attributeDecode(currentValueBuffer.toString()));
        currentAttrName = null;
        currentValueBuffer = null;
        currentState = BASICPARSESTATE_NORMAL;
        noteTag(currentTagName,currentAttrMap);
        currentTagName = null;
        currentAttrMap = null;
      }
      else
        currentValueBuffer.append(thisChar);
      break;
    default:
      throw new ManifoldCFException("Invalid state: "+Integer.toString(currentState));
    }
  }

  protected void noteTag(String tagName, Map<String,String> attributes)
    throws ManifoldCFException
  {
    Logging.misc.debug(" Saw tag '"+tagName+"'");
  }

  protected void noteEndTag(String tagName)
    throws ManifoldCFException
  {
    Logging.misc.debug(" Saw end tag '"+tagName+"'");
  }

  protected void noteNormalCharacter(char thisChar)
    throws ManifoldCFException
  {
  }
  
  public void finishUp()
    throws ManifoldCFException
  {
    // Does nothing
  }

  /** Decode body text */
  protected static String bodyDecode(String input)
  {
    return attributeDecode(input);
  }
  
  /** Decode an html attribute */
  protected static String attributeDecode(String input)
  {
    StringBuilder output = new StringBuilder();
    int i = 0;
    while (i < input.length())
    {
      char x = input.charAt(i++);
      if (x == '&')
      {
        int index = input.indexOf(";",i);
        if (index != -1)
        {
          String chunk = input.substring(i,index);
          String replacement = mapChunk(chunk);
          if (replacement != null)
          {
            output.append(replacement);
            i = index + 1;
            continue;
          }
        }
      }
      output.append(x);
    }
    return output.toString();
  }

  /** Map an entity reference back to a character */
  protected static String mapChunk(String input)
  {
    if (input.startsWith("#"))
    {
      // Treat as a decimal value
      try
      {
        int value = Integer.parseInt(input.substring(1));
        StringBuilder sb = new StringBuilder();
        sb.append((char)value);
        return sb.toString();
      }
      catch (NumberFormatException e)
      {
        return null;
      }
    }
    else
      return mapLookup.get(input);
  }

  /** Is a character HTML whitespace? */
  protected static boolean isHTMLWhitespace(char x)
  {
    return x <= ' ';
  }

}
