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

/** This class represents a basic xml/html tag parser.
* It is capable of recognizing the following xml and html constructs:
*
* '<' <token> <attrs> '>' ... '</' <token> '>'
* '<' <token> <attrs> '/>'
* '<?' <token> <attrs>  '?>'
* '<![' [<token>] '[' ... ']]>'
* '<!' <token> ... '>'
* '<!--' ... '-->'
*
* Each of these, save the comment, has supporting protected methods that will be
* called by the parsing engine.  Overriding these methods will allow an extending
* class to perform higher-level data extraction and parsing.
*/
public class TagParseState extends SingleCharacterReceiver
{
  protected static final int TAGPARSESTATE_NORMAL = 0;
  protected static final int TAGPARSESTATE_SAWLEFTBRACKET = 1;
  protected static final int TAGPARSESTATE_SAWEXCLAMATION = 2;
  protected static final int TAGPARSESTATE_SAWDASH = 3;
  protected static final int TAGPARSESTATE_IN_COMMENT = 4;
  protected static final int TAGPARSESTATE_SAWCOMMENTDASH = 5;
  protected static final int TAGPARSESTATE_SAWSECONDCOMMENTDASH = 6;
  protected static final int TAGPARSESTATE_IN_TAG_NAME = 7;
  protected static final int TAGPARSESTATE_IN_ATTR_NAME = 8;
  protected static final int TAGPARSESTATE_IN_ATTR_VALUE = 9;
  protected static final int TAGPARSESTATE_IN_TAG_SAW_SLASH = 10;
  protected static final int TAGPARSESTATE_IN_END_TAG_NAME = 11;
  protected static final int TAGPARSESTATE_IN_ATTR_LOOKING_FOR_VALUE = 12;
  protected static final int TAGPARSESTATE_IN_SINGLE_QUOTES_ATTR_VALUE = 13;
  protected static final int TAGPARSESTATE_IN_DOUBLE_QUOTES_ATTR_VALUE = 14;
  protected static final int TAGPARSESTATE_IN_UNQUOTED_ATTR_VALUE = 15;
  protected static final int TAGPARSESTATE_IN_QTAG_NAME = 16;
  protected static final int TAGPARSESTATE_IN_QTAG_ATTR_NAME = 17;
  protected static final int TAGPARSESTATE_IN_QTAG_SAW_QUESTION = 18;
  protected static final int TAGPARSESTATE_IN_QTAG_ATTR_VALUE = 19;
  protected static final int TAGPARSESTATE_IN_QTAG_ATTR_LOOKING_FOR_VALUE = 20;
  protected static final int TAGPARSESTATE_IN_QTAG_SINGLE_QUOTES_ATTR_VALUE = 21;
  protected static final int TAGPARSESTATE_IN_QTAG_DOUBLE_QUOTES_ATTR_VALUE = 22;
  protected static final int TAGPARSESTATE_IN_QTAG_UNQUOTED_ATTR_VALUE = 23;
  
  // These still need to be added to the case statement

  protected int currentState = TAGPARSESTATE_NORMAL;

  protected StringBuilder currentTagNameBuffer = null;
  protected StringBuilder currentAttrNameBuffer = null;
  protected StringBuilder currentValueBuffer = null;

  protected String currentTagName = null;
  protected String currentAttrName = null;
  protected List<AttrNameValue> currentAttrList = null;

  protected static final Map<String,String> mapLookup = new HashMap<String,String>();
  static
  {
    mapLookup.put("amp","&");
    mapLookup.put("lt","<");
    mapLookup.put("gt",">");
    mapLookup.put("quot","\"");
    mapLookup.put("apos","'");
  }

  public TagParseState()
  {
    super(65536);
  }

  /** Deal with a character.  No exceptions are allowed, since those would represent
  * syntax errors, and we don't want those to cause difficulty. */
  @Override
  public boolean dealWithCharacter(char thisChar)
    throws ManifoldCFException
  {
    // At this level we want basic lexical analysis - that is, we deal with identifying tags and comments, that's it.
    // We don't even attempt to map to lower case, that's how naive this is.
    switch (currentState)
    {
    case TAGPARSESTATE_NORMAL:
      if (thisChar == '<')
        currentState = TAGPARSESTATE_SAWLEFTBRACKET;
      else
        if (noteNormalCharacter(thisChar))
          return true;
      break;
  
    case TAGPARSESTATE_SAWLEFTBRACKET:
      if (thisChar == '!')
        currentState = TAGPARSESTATE_SAWEXCLAMATION;
      else if (thisChar == '?')
      {
        currentState = TAGPARSESTATE_IN_QTAG_NAME;
        currentTagNameBuffer = new StringBuilder();
      }
      else if (thisChar == '/')
      {
        currentState = TAGPARSESTATE_IN_END_TAG_NAME;
        currentTagNameBuffer = new StringBuilder();
      }
      else
      {
        currentState = TAGPARSESTATE_IN_TAG_NAME;
        currentTagNameBuffer = new StringBuilder();
        if (!isWhitespace(thisChar))
          currentTagNameBuffer.append(thisChar);
      }
      break;

    case TAGPARSESTATE_SAWEXCLAMATION:
      if (thisChar == '-')
        currentState = TAGPARSESTATE_SAWDASH;
      else
        currentState = TAGPARSESTATE_NORMAL;
      break;
    case TAGPARSESTATE_SAWDASH:
      if (thisChar == '-')
        currentState = TAGPARSESTATE_IN_COMMENT;
      else
        currentState = TAGPARSESTATE_NORMAL;
      break;

    case TAGPARSESTATE_IN_COMMENT:
      // We're in a comment.  All we should look for is the end of the comment.
      if (thisChar == '-')
        currentState = TAGPARSESTATE_SAWCOMMENTDASH;
      break;

    case TAGPARSESTATE_SAWCOMMENTDASH:
      if (thisChar == '-')
        currentState = TAGPARSESTATE_SAWSECONDCOMMENTDASH;
      else
        currentState = TAGPARSESTATE_IN_COMMENT;
      break;

    case TAGPARSESTATE_SAWSECONDCOMMENTDASH:
      if (thisChar == '>')
        currentState = TAGPARSESTATE_NORMAL;
      else if (thisChar != '-')
        currentState = TAGPARSESTATE_IN_COMMENT;
      break;

    case TAGPARSESTATE_IN_QTAG_NAME:
      if (isWhitespace(thisChar))
      {
        if (currentTagNameBuffer.length() > 0)
        {
          // Done with the tag name!
          currentTagName = currentTagNameBuffer.toString();
          currentTagNameBuffer = null;
          currentAttrList = new ArrayList<AttrNameValue>();
          currentState = TAGPARSESTATE_IN_QTAG_ATTR_NAME;
          currentAttrNameBuffer = new StringBuilder();
        }
      }
      else if (thisChar == '?')
      {
        if (currentTagNameBuffer.length() > 0)
        {
          currentTagName = currentTagNameBuffer.toString();
          currentTagNameBuffer = null;
          currentAttrList = new ArrayList<AttrNameValue>();
          currentState = TAGPARSESTATE_IN_QTAG_SAW_QUESTION;
          if (noteQTag(currentTagName,currentAttrList))
            return true;
        }
        else
        {
          currentState = TAGPARSESTATE_NORMAL;
          currentTagNameBuffer = null;
        }
      }
      else if (thisChar == '>')
      {
        if (currentTagNameBuffer.length() > 0)
        {
          currentTagName = currentTagNameBuffer.toString();
          currentTagNameBuffer = null;
          currentAttrList = new ArrayList<AttrNameValue>();
        }
        if (currentTagName != null)
        {
          if (noteQTag(currentTagName,currentAttrList))
            return true;
        }
        currentState = TAGPARSESTATE_NORMAL;
        currentTagName = null;
        currentAttrList = null;
      }
      else
        currentTagNameBuffer.append(thisChar);
      break;

    case TAGPARSESTATE_IN_TAG_NAME:
      if (isWhitespace(thisChar))
      {
        if (currentTagNameBuffer.length() > 0)
        {
          // Done with the tag name!
          currentTagName = currentTagNameBuffer.toString();
          currentTagNameBuffer = null;
          currentAttrList = new ArrayList<AttrNameValue>();
          currentState = TAGPARSESTATE_IN_ATTR_NAME;
          currentAttrNameBuffer = new StringBuilder();
        }
      }
      else if (thisChar == '/')
      {
        if (currentTagNameBuffer.length() > 0)
        {
          currentTagName = currentTagNameBuffer.toString();
          currentTagNameBuffer = null;
          currentAttrList = new ArrayList<AttrNameValue>();
          currentState = TAGPARSESTATE_IN_TAG_SAW_SLASH;
          if (noteTag(currentTagName,currentAttrList))
            return true;
        }
        else
        {
          currentState = TAGPARSESTATE_NORMAL;
          currentTagNameBuffer = null;
        }
      }
      else if (thisChar == '>')
      {
        if (currentTagNameBuffer.length() > 0)
        {
          currentTagName = currentTagNameBuffer.toString();
          currentTagNameBuffer = null;
          currentAttrList = new ArrayList<AttrNameValue>();
        }
        if (currentTagName != null)
        {
          if (noteTag(currentTagName,currentAttrList))
            return true;
        }
        currentState = TAGPARSESTATE_NORMAL;
        currentTagName = null;
        currentAttrList = null;
      }
      else
        currentTagNameBuffer.append(thisChar);
      break;

    case TAGPARSESTATE_IN_ATTR_NAME:
      if (isWhitespace(thisChar))
      {
        if (currentAttrNameBuffer.length() > 0)
        {
          // Done with attr name!
          currentAttrName = currentAttrNameBuffer.toString();
          currentAttrNameBuffer = null;
          currentState = TAGPARSESTATE_IN_ATTR_LOOKING_FOR_VALUE;
        }
      }
      else if (thisChar == '=')
      {
        if (currentAttrNameBuffer.length() > 0)
        {
          currentAttrName = currentAttrNameBuffer.toString();
          currentAttrNameBuffer = null;
          currentState = TAGPARSESTATE_IN_ATTR_VALUE;
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
          currentAttrList.add(new AttrNameValue(currentAttrName,""));
          currentAttrName = null;
        }
        if (noteTag(currentTagName,currentAttrList))
          return true;
        currentState = TAGPARSESTATE_IN_TAG_SAW_SLASH;
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
          currentAttrList.add(new AttrNameValue(currentAttrName,""));
          currentAttrName = null;
        }
        currentState = TAGPARSESTATE_NORMAL;
        if (noteTag(currentTagName,currentAttrList))
          return true;
        currentTagName = null;
        currentAttrList = null;
      }
      else
        currentAttrNameBuffer.append(thisChar);
      break;

    case TAGPARSESTATE_IN_QTAG_ATTR_LOOKING_FOR_VALUE:
      if (thisChar == '=')
      {
        currentState = TAGPARSESTATE_IN_QTAG_ATTR_VALUE;
        currentValueBuffer = new StringBuilder();
      }
      else if (thisChar == '>')
      {
        currentState = TAGPARSESTATE_NORMAL;
        if (noteQTag(currentTagName,currentAttrList))
          return true;
        currentTagName = null;
        currentAttrList = null;
      }
      else if (thisChar == '?')
      {
        currentState = TAGPARSESTATE_IN_QTAG_SAW_QUESTION;
        currentAttrList.add(new AttrNameValue(currentAttrName,""));
        currentAttrName = null;
        if (noteQTag(currentTagName,currentAttrList))
          return true;
      }
      else if (!isWhitespace(thisChar))
      {
        currentAttrList.add(new AttrNameValue(currentAttrName,""));
        currentState = TAGPARSESTATE_IN_QTAG_ATTR_NAME;
        currentAttrNameBuffer = new StringBuilder();
        currentAttrNameBuffer.append(thisChar);
        currentAttrName = null;
      }
      break;

    case TAGPARSESTATE_IN_ATTR_LOOKING_FOR_VALUE:
      if (thisChar == '=')
      {
        currentState = TAGPARSESTATE_IN_ATTR_VALUE;
        currentValueBuffer = new StringBuilder();
      }
      else if (thisChar == '>')
      {
        currentState = TAGPARSESTATE_NORMAL;
        if (noteTag(currentTagName,currentAttrList))
          return true;
        currentTagName = null;
        currentAttrList = null;
      }
      else if (thisChar == '/')
      {
        currentState = TAGPARSESTATE_IN_TAG_SAW_SLASH;
        currentAttrList.add(new AttrNameValue(currentAttrName,""));
        currentAttrName = null;
        if (noteTag(currentTagName,currentAttrList))
          return true;
      }
      else if (!isWhitespace(thisChar))
      {
        currentAttrList.add(new AttrNameValue(currentAttrName,""));
        currentState = TAGPARSESTATE_IN_ATTR_NAME;
        currentAttrNameBuffer = new StringBuilder();
        currentAttrNameBuffer.append(thisChar);
        currentAttrName = null;
      }
      break;

    case TAGPARSESTATE_IN_QTAG_ATTR_VALUE:
      if (thisChar == '\'')
        currentState = TAGPARSESTATE_IN_QTAG_SINGLE_QUOTES_ATTR_VALUE;
      else if (thisChar == '"')
        currentState = TAGPARSESTATE_IN_QTAG_DOUBLE_QUOTES_ATTR_VALUE;
      else if (!isWhitespace(thisChar))
      {
        currentState = TAGPARSESTATE_IN_QTAG_UNQUOTED_ATTR_VALUE;
        currentValueBuffer.append(thisChar);
      }
      break;
      
    case TAGPARSESTATE_IN_ATTR_VALUE:
      if (thisChar == '\'')
        currentState = TAGPARSESTATE_IN_SINGLE_QUOTES_ATTR_VALUE;
      else if (thisChar == '"')
        currentState = TAGPARSESTATE_IN_DOUBLE_QUOTES_ATTR_VALUE;
      else if (!isWhitespace(thisChar))
      {
        currentState = TAGPARSESTATE_IN_UNQUOTED_ATTR_VALUE;
        currentValueBuffer.append(thisChar);
      }
      break;

    case TAGPARSESTATE_IN_QTAG_SAW_QUESTION:
      if (thisChar == '>')
      {
        // No end-tag notification for this one
        currentState = TAGPARSESTATE_NORMAL;
        currentTagName = null;
        currentAttrList = null;
      }
      break;

    case TAGPARSESTATE_IN_TAG_SAW_SLASH:
      if (thisChar == '>')
      {
        if (noteEndTag(currentTagName))
          return true;
        currentState = TAGPARSESTATE_NORMAL;
        currentTagName = null;
        currentAttrList = null;
      }
      break;

    case TAGPARSESTATE_IN_END_TAG_NAME:
      if (isWhitespace(thisChar))
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
          if (noteEndTag(currentTagName))
            return true;
        }
        currentTagName = null;
        currentState = TAGPARSESTATE_NORMAL;
      }
      else if (currentTagNameBuffer != null)
        currentTagNameBuffer.append(thisChar);
      break;

    case TAGPARSESTATE_IN_QTAG_SINGLE_QUOTES_ATTR_VALUE:
      if (thisChar == '\'' || thisChar == '\n' || thisChar == '\r')
      {
        currentAttrList.add(new AttrNameValue(currentAttrName,attributeDecode(currentValueBuffer.toString())));
        currentAttrName = null;
        currentValueBuffer = null;
        currentState = TAGPARSESTATE_IN_QTAG_ATTR_NAME;
        currentAttrNameBuffer = new StringBuilder();
      }
      else
        currentValueBuffer.append(thisChar);
      break;

    case TAGPARSESTATE_IN_SINGLE_QUOTES_ATTR_VALUE:
      if (thisChar == '\'' || thisChar == '\n' || thisChar == '\r')
      {
        currentAttrList.add(new AttrNameValue(currentAttrName,attributeDecode(currentValueBuffer.toString())));
        currentAttrName = null;
        currentValueBuffer = null;
        currentState = TAGPARSESTATE_IN_ATTR_NAME;
        currentAttrNameBuffer = new StringBuilder();
      }
      else
        currentValueBuffer.append(thisChar);
      break;

    case TAGPARSESTATE_IN_QTAG_DOUBLE_QUOTES_ATTR_VALUE:
      if (thisChar == '"' || thisChar == '\n' || thisChar == '\r')
      {
        currentAttrList.add(new AttrNameValue(currentAttrName,attributeDecode(currentValueBuffer.toString())));
        currentAttrName = null;
        currentValueBuffer = null;
        currentState = TAGPARSESTATE_IN_QTAG_ATTR_NAME;
        currentAttrNameBuffer = new StringBuilder();
      }
      else
        currentValueBuffer.append(thisChar);
      break;

    case TAGPARSESTATE_IN_DOUBLE_QUOTES_ATTR_VALUE:
      if (thisChar == '"' || thisChar == '\n' || thisChar == '\r')
      {
        currentAttrList.add(new AttrNameValue(currentAttrName,attributeDecode(currentValueBuffer.toString())));
        currentAttrName = null;
        currentValueBuffer = null;
        currentState = TAGPARSESTATE_IN_ATTR_NAME;
        currentAttrNameBuffer = new StringBuilder();
      }
      else
        currentValueBuffer.append(thisChar);
      break;

    case TAGPARSESTATE_IN_QTAG_UNQUOTED_ATTR_VALUE:
      if (isWhitespace(thisChar))
      {
        currentAttrList.add(new AttrNameValue(currentAttrName,attributeDecode(currentValueBuffer.toString())));
        currentAttrName = null;
        currentValueBuffer = null;
        currentState = TAGPARSESTATE_IN_QTAG_ATTR_NAME;
        currentAttrNameBuffer = new StringBuilder();
      }
      else if (thisChar == '?')
      {
        currentAttrList.add(new AttrNameValue(currentAttrName,attributeDecode(currentValueBuffer.toString())));
        if (noteTag(currentTagName,currentAttrList))
          return true;
        currentState = TAGPARSESTATE_IN_QTAG_SAW_QUESTION;
      }
      else if (thisChar == '>')
      {
        currentAttrList.add(new AttrNameValue(currentAttrName,attributeDecode(currentValueBuffer.toString())));
        currentAttrName = null;
        currentValueBuffer = null;
        currentState = TAGPARSESTATE_NORMAL;
        if (noteTag(currentTagName,currentAttrList))
          return true;
        currentTagName = null;
        currentAttrList = null;
      }
      else
        currentValueBuffer.append(thisChar);
      break;

    case TAGPARSESTATE_IN_UNQUOTED_ATTR_VALUE:
      if (isWhitespace(thisChar))
      {
        currentAttrList.add(new AttrNameValue(currentAttrName,attributeDecode(currentValueBuffer.toString())));
        currentAttrName = null;
        currentValueBuffer = null;
        currentState = TAGPARSESTATE_IN_ATTR_NAME;
        currentAttrNameBuffer = new StringBuilder();
      }
      else if (thisChar == '/')
      {
        currentAttrList.add(new AttrNameValue(currentAttrName,attributeDecode(currentValueBuffer.toString())));
        if (noteTag(currentTagName,currentAttrList))
          return true;
        currentState = TAGPARSESTATE_IN_TAG_SAW_SLASH;
      }
      else if (thisChar == '>')
      {
        currentAttrList.add(new AttrNameValue(currentAttrName,attributeDecode(currentValueBuffer.toString())));
        currentAttrName = null;
        currentValueBuffer = null;
        currentState = TAGPARSESTATE_NORMAL;
        if (noteTag(currentTagName,currentAttrList))
          return true;
        currentTagName = null;
        currentAttrList = null;
      }
      else
        currentValueBuffer.append(thisChar);
      break;

    default:
      throw new ManifoldCFException("Invalid state: "+Integer.toString(currentState));
    }
    return false;
  }

  /** This method gets called for every tag.  Override this method to intercept tag begins.
  *@return true to halt further processing.
  */
  protected boolean noteTag(String tagName, List<AttrNameValue> attributes)
    throws ManifoldCFException
  {
    if (Logging.misc.isDebugEnabled())
      Logging.misc.debug(" Saw tag '"+tagName+"'");
    return false;
  }

  /** This method gets called for every end tag.  Override this method to intercept tag ends.
  *@return true to halt further processing.
  */
  protected boolean noteEndTag(String tagName)
    throws ManifoldCFException
  {
    if (Logging.misc.isDebugEnabled())
      Logging.misc.debug(" Saw end tag '"+tagName+"'");
    return false;
  }

  /** This method is called for every <? ... ?> construct, or 'qtag'.
  * Override it to intercept such constructs.
  *@return true to halt further processing.
  */
  protected boolean noteQTag(String tagName, List<AttrNameValue> attributes)
    throws ManifoldCFException
  {
    if (Logging.misc.isDebugEnabled())
      Logging.misc.debug(" Saw QTag '"+tagName+"'");
    return false;
  }
  
  /** This method is called for every <! <token> ... > construct, or 'btag'.
  * Override it to intercept these.
  *@return true to halt further processing.
  */
  protected boolean noteBTag(String tagName)
    throws ManifoldCFException
  {
    if (Logging.misc.isDebugEnabled())
      Logging.misc.debug(" Saw BTag '"+tagName+"'");
    return false;
  }
  
  /** This method is called for the end of every btag, or any time
  * there's a naked '>' in the document.  Override it if you want to intercept these.
  *@return true to halt further processing.
  */
  protected boolean noteEndBTag()
    throws ManifoldCFException
  {
    Logging.misc.debug(" Saw end BTag");
    return false;
  }
  
  /** Called for the start of every cdata-like tag, e.g. <![ <token> [ ... ]]>
  *@param token may be null!!!
  *@return true to halt further processing.
  */
  protected boolean noteEscaped(String token)
    throws ManifoldCFException
  {
    if (Logging.misc.isDebugEnabled())
      Logging.misc.debug(" Saw escaped '"+((token==null)?null:token)+"'");
    return false;
  }
  
  /** Called for the end of every cdata-like tag.
  *@return true to halt further processing.
  */
  protected boolean noteEndEscaped()
    throws ManifoldCFException
  {
    Logging.misc.debug(" Saw end escaped");
    return false;
  }
  
  /** This method gets called for every character that is not part of a tag etc.
  * Override this method to intercept such characters.
  *@return true to halt further processing.
  */
  protected boolean noteNormalCharacter(char thisChar)
    throws ManifoldCFException
  {
    return false;
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

  /** Is a character markup language whitespace? */
  protected static boolean isWhitespace(char x)
  {
    return x <= ' ';
  }

}
