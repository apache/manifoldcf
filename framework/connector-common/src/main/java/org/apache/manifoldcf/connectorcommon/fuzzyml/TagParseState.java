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
package org.apache.manifoldcf.connectorcommon.fuzzyml;

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
*
* Of these, the messiest is the <! ... > construct, since there can be multiple nested
* btags, cdata-like escapes, and qtags inside.  Ideally the parser should produce a
* sequence of preparsed tokens from these tags.  Since they can be nested, keeping
* track of the depth is also essential, so we do that with a btag depth counter.
* Thus, in this case, it is not the state that matters, but the btag depth, to determine
* if the parser is operating inside a btag.
*/
public class TagParseState extends SingleCharacterReceiver
{
  protected static final int TAGPARSESTATE_NORMAL = 0;
  protected static final int TAGPARSESTATE_SAWLEFTANGLE = 1;
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
  protected static final int TAGPARSESTATE_IN_BRACKET_TOKEN = 24;
  protected static final int TAGPARSESTATE_NEED_FINAL_BRACKET = 25;
  protected static final int TAGPARSESTATE_IN_BANG_TOKEN = 26;
  protected static final int TAGPARSESTATE_IN_CDATA_BODY = 27;
  protected static final int TAGPARSESTATE_SAWRIGHTBRACKET = 28;
  protected static final int TAGPARSESTATE_SAWSECONDRIGHTBRACKET = 29;
  protected static final int TAGPARSESTATE_IN_UNQUOTED_ATTR_VALUE_SAW_SLASH = 30;
  
  protected int currentState = TAGPARSESTATE_NORMAL;

  /** The btag depth, which indicates btag behavior when > 0. */
  protected int bTagDepth = 0;
  
  /** This is the only buffer we actually accumulate stuff in.
  */
  protected StringBuilder accumBuffer = new StringBuilder();
  
  // The following are pointers to the accum buffer above, when allocated.
  
  protected StringBuilder currentTagNameBuffer = null;
  protected StringBuilder currentAttrNameBuffer = null;
  protected StringBuilder currentValueBuffer = null;

  protected String currentTagName = null;
  protected String currentAttrName = null;
  protected List<AttrNameValue> currentAttrList = null;

  // Body decoding state

  /** Whether we've seen an ampersand */
  protected boolean inAmpersand = false;
  /** Buffer of characters seen after ampersand. */
  protected StringBuilder ampBuffer = new StringBuilder();

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
      {
        if (inAmpersand)
        {
          outputAmpBuffer();
          inAmpersand = false;
        }
        currentState = TAGPARSESTATE_SAWLEFTANGLE;
      }
      else if (bTagDepth > 0 && thisChar == '>')
      {
        // Output current token, if any
        if (currentTagNameBuffer != null && currentTagNameBuffer.length() > 0)
        {
          currentTagName = currentTagNameBuffer.toString();
          if (noteBTagToken(currentTagName))
            return true;
          currentTagName = null;
          currentTagNameBuffer = null;
        }
        if (noteEndBTag())
          return true;
        bTagDepth--;
      }
      else if (bTagDepth == 0)
      {
        if (inAmpersand)
        {
          if (thisChar == ';')
          {
            // We append the semi so that the output function can make good decisions
            ampBuffer.append(thisChar);
            if (outputAmpBuffer())
              return true;
            inAmpersand = false;
          }
          else if (isWhitespace(thisChar))
          {
            // Interpret ampersand buffer.
            if (outputAmpBuffer())
              return true;
            inAmpersand = false;
            if (noteNormalCharacter(thisChar))
              return true;
          }
          else
            ampBuffer.append(thisChar);
        }
        else if (thisChar == '&')
        {
          inAmpersand = true;
          ampBuffer.setLength(0);
        }
        else
        {
          if (noteNormalCharacter(thisChar))
            return true;
        }
      }
      else
      {
        // In btag; accumulate tokens
        if (isPunctuation(thisChar))
        {
          if (currentTagNameBuffer != null && currentTagNameBuffer.length() > 0)
          {
            currentTagName = currentTagNameBuffer.toString();
            if (noteBTagToken(currentTagName))
              return true;
            currentTagNameBuffer = null;
            currentTagName = null;
          }
          if (noteBTagToken(new StringBuilder().append(thisChar).toString()))
            return true;
        }
        else if (isWhitespace(thisChar))
        {
          if (currentTagNameBuffer != null && currentTagNameBuffer.length() > 0)
          {
            currentTagName = currentTagNameBuffer.toString();
            if (noteBTagToken(currentTagName))
              return true;
            currentTagNameBuffer = null;
            currentTagName = null;
          }
        }
        else
        {
          if (currentTagNameBuffer == null)
            currentTagNameBuffer = newBuffer();
          currentTagNameBuffer.append(thisChar);
        }
      }
      break;
  
    case TAGPARSESTATE_IN_CDATA_BODY:
      if (thisChar == ']')
        currentState = TAGPARSESTATE_SAWRIGHTBRACKET;
      else
      {
        if (noteEscapedCharacter(thisChar))
          return true;
      }
      break;

    case TAGPARSESTATE_SAWRIGHTBRACKET:
      if (thisChar == ']')
        currentState = TAGPARSESTATE_SAWSECONDRIGHTBRACKET;
      else
      {
        currentState = TAGPARSESTATE_IN_CDATA_BODY;
        if (noteEscapedCharacter(']'))
          return true;
        if (noteEscapedCharacter(thisChar))
          return true;
      }
      break;

    case TAGPARSESTATE_SAWSECONDRIGHTBRACKET:
      if (thisChar == '>')
        currentState = TAGPARSESTATE_NORMAL;
      else if (thisChar == ']')
      {
        // currentstate unchanged; emit the first bracket
        if (noteEscapedCharacter(']'))
          return true;
      }
      else
      {
        currentState = TAGPARSESTATE_IN_CDATA_BODY;
        if (noteEscapedCharacter(']'))
          return true;
        if (noteEscapedCharacter(']'))
          return true;
        if (noteEscapedCharacter(thisChar))
          return true;
      }
      break;
      
    case TAGPARSESTATE_SAWLEFTANGLE:
      if (thisChar == '!')
        currentState = TAGPARSESTATE_SAWEXCLAMATION;
      else if (thisChar == '?')
      {
        currentState = TAGPARSESTATE_IN_QTAG_NAME;
        currentTagNameBuffer = newBuffer();
      }
      else if (bTagDepth == 0 && thisChar == '/')
      {
        currentState = TAGPARSESTATE_IN_END_TAG_NAME;
        currentTagNameBuffer = newBuffer();
      }
      else if (bTagDepth == 0)
      {
        if (isWhitespace(thisChar))
        {
          // Not a tag.
          currentState = TAGPARSESTATE_NORMAL;
          if (noteNormalCharacter('<'))
            return true;
          if (noteNormalCharacter(thisChar))
            return true;
        }
        else
        {
          currentState = TAGPARSESTATE_IN_TAG_NAME;
          currentTagNameBuffer = newBuffer();
          currentTagNameBuffer.append(thisChar);
        }
      }
      else
      {
        // in btag, saw left angle, nothing recognizable after - must be a token
        if (noteBTagToken("<"))
          return true;
        if (!isWhitespace(thisChar))
        {
          // Add char to current token buffer.
          currentTagNameBuffer = newBuffer();
          currentTagNameBuffer.append(thisChar);
        }
        currentState = TAGPARSESTATE_NORMAL;
      }
      break;

    case TAGPARSESTATE_SAWEXCLAMATION:
      if (thisChar == '-')
        currentState = TAGPARSESTATE_SAWDASH;
      else if (thisChar == '[')
      {
        currentState = TAGPARSESTATE_IN_BRACKET_TOKEN;
        currentTagNameBuffer = newBuffer();
      }
      else
      {
        bTagDepth++;
        currentState = TAGPARSESTATE_IN_BANG_TOKEN;
        currentTagNameBuffer = newBuffer();
        if (!isWhitespace(thisChar))
          currentTagNameBuffer.append(thisChar);
      }
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
          currentAttrNameBuffer = newBuffer();
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

    case TAGPARSESTATE_IN_BRACKET_TOKEN:
      if (isWhitespace(thisChar))
      {
        if (currentTagNameBuffer.length() > 0)
        {
          // Done with the bracket token!
          currentTagName = currentTagNameBuffer.toString();
          currentTagNameBuffer = null;
          currentState = TAGPARSESTATE_NEED_FINAL_BRACKET;
        }
      }
      else if (thisChar == '[')
      {
        currentTagName = currentTagNameBuffer.toString();
        currentTagNameBuffer = null;
        currentState = TAGPARSESTATE_IN_CDATA_BODY;
        if (noteEscaped(currentTagName))
          return true;
        currentTagName = null;
      }
      else
        currentTagNameBuffer.append(thisChar);
      break;

    case TAGPARSESTATE_NEED_FINAL_BRACKET:
      if (thisChar == '[')
      {
        if (noteEscaped(currentTagName))
          return true;
        currentTagName = null;
        currentState = TAGPARSESTATE_IN_CDATA_BODY;
      }
      break;

    case TAGPARSESTATE_IN_BANG_TOKEN:
      if (isWhitespace(thisChar))
      {
        if (currentTagNameBuffer.length() > 0)
        {
          // Done with bang token
          currentTagName = currentTagNameBuffer.toString();
          currentTagNameBuffer = null;
          if (noteBTag(currentTagName))
            return true;
          currentTagName = null;
          currentState = TAGPARSESTATE_NORMAL;
        }
      }
      else if (thisChar == '>')
      {
        // Also done, but signal end too.
        currentTagName = currentTagNameBuffer.toString();
        currentTagNameBuffer = null;
        if (noteBTag(currentTagName))
          return true;
        currentTagName = null;
        currentState = TAGPARSESTATE_NORMAL;
        if (noteEndBTag())
          return true;
        bTagDepth--;
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
          currentAttrNameBuffer = newBuffer();
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

    case TAGPARSESTATE_IN_QTAG_ATTR_NAME:
      if (isWhitespace(thisChar))
      {
        if (currentAttrNameBuffer.length() > 0)
        {
          // Done with attr name!
          currentAttrName = currentAttrNameBuffer.toString();
          currentAttrNameBuffer = null;
          currentState = TAGPARSESTATE_IN_QTAG_ATTR_LOOKING_FOR_VALUE;
        }
      }
      else if (thisChar == '=')
      {
        if (currentAttrNameBuffer.length() > 0)
        {
          currentAttrName = currentAttrNameBuffer.toString();
          currentAttrNameBuffer = null;
          currentState = TAGPARSESTATE_IN_QTAG_ATTR_VALUE;
          currentValueBuffer = newBuffer();
        }
      }
      else if (thisChar == '?')
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
        if (noteQTag(currentTagName,currentAttrList))
          return true;
        currentState = TAGPARSESTATE_IN_QTAG_SAW_QUESTION;
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
        if (noteQTag(currentTagName,currentAttrList))
          return true;
        currentTagName = null;
        currentAttrList = null;
      }
      else
        currentAttrNameBuffer.append(thisChar);
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
          currentValueBuffer = newBuffer();
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
        currentValueBuffer = newBuffer();
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
        currentAttrNameBuffer = newBuffer();
        currentAttrNameBuffer.append(thisChar);
        currentAttrName = null;
      }
      break;

    case TAGPARSESTATE_IN_ATTR_LOOKING_FOR_VALUE:
      if (thisChar == '=')
      {
        currentState = TAGPARSESTATE_IN_ATTR_VALUE;
        currentValueBuffer = newBuffer();
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
        currentAttrNameBuffer = newBuffer();
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
      else if (thisChar == '/')
        currentState = TAGPARSESTATE_IN_UNQUOTED_ATTR_VALUE_SAW_SLASH;
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
        currentAttrNameBuffer = newBuffer();
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
        currentAttrNameBuffer = newBuffer();
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
        currentAttrNameBuffer = newBuffer();
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
        currentAttrNameBuffer = newBuffer();
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
        currentAttrNameBuffer = newBuffer();
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

    case TAGPARSESTATE_IN_UNQUOTED_ATTR_VALUE_SAW_SLASH:
      if (isWhitespace(thisChar))
      {
        currentValueBuffer.append('/');
        currentAttrList.add(new AttrNameValue(currentAttrName,attributeDecode(currentValueBuffer.toString())));
        currentAttrName = null;
        currentValueBuffer = null;
        currentState = TAGPARSESTATE_IN_ATTR_NAME;
        currentAttrNameBuffer = newBuffer();
      }
      else if (thisChar == '/')
      {
        currentValueBuffer.append('/');
      }
      else if (thisChar == '>')
      {
        currentValueBuffer.append('/');
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
      {
        currentValueBuffer.append('/');
        currentValueBuffer.append(thisChar);
        currentState = TAGPARSESTATE_IN_UNQUOTED_ATTR_VALUE;
      }
      break;

    case TAGPARSESTATE_IN_UNQUOTED_ATTR_VALUE:
      if (isWhitespace(thisChar))
      {
        currentAttrList.add(new AttrNameValue(currentAttrName,attributeDecode(currentValueBuffer.toString())));
        currentAttrName = null;
        currentValueBuffer = null;
        currentState = TAGPARSESTATE_IN_ATTR_NAME;
        currentAttrNameBuffer = newBuffer();
      }
      else if (thisChar == '/')
      {
        currentState = TAGPARSESTATE_IN_UNQUOTED_ATTR_VALUE_SAW_SLASH;
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

  /** Allocate the buffer.
  */
  protected StringBuilder newBuffer()
  {
    accumBuffer.setLength(0);
    return accumBuffer;
  }
  
  /** Interpret ampersand buffer.
  */
  protected boolean outputAmpBuffer()
    throws ManifoldCFException
  {
    if (ampBuffer.length() == 0 || (ampBuffer.length() == 1 && ampBuffer.charAt(0) == ';'))
    {
      // Length is zero; probably a mistake, so just output the whole thing
      if (noteNormalCharacter('&'))
        return true;
      if (dumpValues(ampBuffer.toString()))
        return true;
      return false;
    }
    else
    {
      // Is it a known entity?
      String entity = ampBuffer.toString();
      if (entity.endsWith(";"))
        entity = entity.substring(0,entity.length()-1);
      String replacement = mapChunk(entity);
      if (replacement != null)
      {
        if (dumpValues(replacement))
          return true;
      }
      return false;
    }
  }
  
  protected boolean dumpValues(String value)
    throws ManifoldCFException
  {
    for (int i = 0; i < value.length(); i++)
    {
      if (noteNormalCharacter(value.charAt(i)))
        return true;
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
  *@param token may be empty!!!
  *@return true to halt further processing.
  */
  protected boolean noteEscaped(String token)
    throws ManifoldCFException
  {
    if (Logging.misc.isDebugEnabled())
      Logging.misc.debug(" Saw escaped block '"+token+"'");
    return false;
  }
  
  /** Called for the end of every cdata-like tag.
  *@return true to halt further processing.
  */
  protected boolean noteEndEscaped()
    throws ManifoldCFException
  {
    Logging.misc.debug(" Saw end of escaped block");
    return false;
  }
  
  /** This method gets called for every token inside a btag.
  *@return true to halt further processing.
  */
  protected boolean noteBTagToken(String token)
    throws ManifoldCFException
  {
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

  /** This method gets called for every character that is found within an
  * escape block, e.g. CDATA.
  * Override this method to intercept such characters.
  *@return true to halt further processing.
  */
  protected boolean noteEscapedCharacter(char thisChar)
    throws ManifoldCFException
  {
    return false;
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
        input = input.substring(1);
        int value;
        if (input.startsWith("x"))
        {
          // Hex
          value = Integer.decode("0"+input);
        }
        else
        {
          // Decimal
          value = Integer.parseInt(input);
        }
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

  /** Is a character markup language punctuation? */
  protected static boolean isPunctuation(char x)
  {
    return x == '%' || x == '|' || x == '&' || x == '!' || x == '^' || x == ',' || x == ';' || x == '[' || x == ']' ||
      x == '(' || x == ')' || x == ':' || x == '/' || x == '\\' || x == '+' || x == '=';
  }

}
