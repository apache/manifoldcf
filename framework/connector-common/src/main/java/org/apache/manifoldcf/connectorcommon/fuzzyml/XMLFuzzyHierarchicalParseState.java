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
import java.io.*;
import java.util.*;

/** Class to keep track of XML hierarchy in the face of possibly corrupt
* XML and with case-insensitive tags, etc.
* Basically, this class accepts what is supposedly XML but allows for various
* kinds of handwritten corruption.  Specific kinds of errors allowed include:
*
* - Bad character encoding
* - Tag case match problems; all attributes are (optionally) bashed to lower case,
*    and tag names are checked to match when all lower case, if case-sensitive didn't
*    work
* - End tag matching problems, where someone lost an end tag somehow
* - Other parsing recoveries to be added as they arise
*
* The functionality of this class is also somewhat lessened vs. standard
* SAX-type parsers.  No namespace interpretation is done, for instance; tag qnames
* are split into namespace name and local name, and that's all folks.  But if you need
* more power, you can write a class extension that will do that readily.
*/
public class XMLFuzzyHierarchicalParseState extends XMLFuzzyParseState
{
  /** The current context */
  protected XMLParsingContext currentContext = null;
  /** The current value buffer */
  protected StringBuilder characterBuffer = new StringBuilder();
  /** Whether we're capturing escaped characters */
  protected boolean captureEscaped = false;
  
  /** This is the maximum size of a chunk of characters getting sent to the characters() method.
  */
  protected final static int MAX_CHUNK_SIZE = 4096;
  
  /** Constructor with default properties.
  */
  public XMLFuzzyHierarchicalParseState()
  {
    this(true,true,true,true,true,true);
  }
  
  /** Constructor.
  */
  public XMLFuzzyHierarchicalParseState(boolean lowerCaseAttributes, boolean lowerCaseTags,
    boolean lowerCaseQAttributes, boolean lowerCaseQTags,
    boolean lowerCaseBTags, boolean lowerCaseEscapeTags)
  {
    super(lowerCaseAttributes,lowerCaseTags,lowerCaseQAttributes,lowerCaseQTags,lowerCaseBTags,lowerCaseEscapeTags);
  }
  
  public void setContext(XMLParsingContext context)
  {
    currentContext = context;
  }

  public XMLParsingContext getContext()
  {
    return currentContext;
  }

  /** Call this method to clean up completely after a parse attempt, whether successful or failure. */
  public void cleanup()
    throws ManifoldCFException
  {
    // This sets currentContext == null as a side effect, unless an error occurs during cleanup!!
    if (currentContext != null)
    {
      currentContext.cleanup();
      currentContext = null;
    }
  }

  /** Map version of the noteTag method.
  *@return true to halt further processing.
  */
  @Override
  protected boolean noteTagEx(String tagName, String nameSpace, String localName, Map<String,String> attributes)
    throws ManifoldCFException
  {
    flushCharacterBuffer();
    if (currentContext != null)
      currentContext.startElement(nameSpace,localName,tagName,attributes);
    return false;
  }

  /** Note end tag.
  */
  @Override
  protected boolean noteEndTagEx(String tagName, String nameSpace, String localName)
    throws ManifoldCFException
  {
    flushCharacterBuffer();
    if (currentContext != null)
      currentContext.endElement(nameSpace,localName,tagName);
    return false;
  }

  /** This method gets called for every character that is not part of a tag etc.
  * Override this method to intercept such characters.
  *@return true to halt further processing.
  */
  @Override
  protected boolean noteNormalCharacter(char thisChar)
    throws ManifoldCFException
  {
    appendToCharacterBuffer(thisChar);
    return false;
  }
  
  protected void appendToCharacterBuffer(char thisChar)
    throws ManifoldCFException
  {
    characterBuffer.append(thisChar);
    if (characterBuffer.length() >= MAX_CHUNK_SIZE)
      flushCharacterBuffer();
  }

  protected void flushCharacterBuffer()
    throws ManifoldCFException
  {
    if (characterBuffer.length() > 0)
    {
      if (currentContext != null)
        currentContext.characters(characterBuffer.toString());
      characterBuffer.setLength(0);
    }
  }
  
  /** New version of the noteEscapedTag method.
  *@return true to halt further processing.
  */
  @Override
  protected boolean noteEscapedEx(String token)
    throws ManifoldCFException
  {
    if (token.toLowerCase(Locale.ROOT).equals("cdata"))
      captureEscaped = true;
    return false;
  }
  
  /** This method gets called for every character that is found within an
  * escape block, e.g. CDATA.
  * Override this method to intercept such characters.
  *@return true to halt further processing.
  */
  @Override
  protected boolean noteEscapedCharacter(char thisChar)
    throws ManifoldCFException
  {
    if (captureEscaped)
      appendToCharacterBuffer(thisChar);
    return false;
  }

  /** Called for the end of every cdata-like tag.
  *@return true to halt further processing.
  */
  @Override
  protected boolean noteEndEscaped()
    throws ManifoldCFException
  {
    captureEscaped = false;
    return false;
  }
  
  /** Called at the end of everything.
  */
  @Override
  public void finishUp()
    throws ManifoldCFException
  {
    flushCharacterBuffer();
    super.finishUp();
  }
  
}
