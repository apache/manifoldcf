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
import java.io.*;

/** Class to keep track of XML hierarchy in the face of possibly corrupt
* XML and with case-insensitive tags, etc.
* Basically, this class accepts what is supposedly XML but allows for various
* kinds of handwritten corruption.  Specific kinds of errors allowed include:
*
* - Bad character encoding
* - Tag case match problems; all attributes are (optionally) bashed to lower case
* - Other parsing recoveries to be added as they arise
*
* The functionality of this class is also somewhat lessened vs. standard
* SAX-type parsers.  No namespace interpretation is done, for instance; tag qnames
* are split into namespace name and local name, and that's all folks.  But if you need
* more power, you can write a class extension that will do that readily.
*/
public class XMLFuzzyParseState extends TagParseState
{
  protected final boolean lowerCaseAttributes;
  protected final boolean lowerCaseTags;
  protected final boolean lowerCaseQAttributes;
  protected final boolean lowerCaseQTags;
  protected final boolean lowerCaseBTags;
  protected final boolean lowerCaseEscapeTags;

  /** Constructor.
  */
  public XMLFuzzyParseState(boolean lowerCaseAttributes, boolean lowerCaseTags,
    boolean lowerCaseQAttributes, boolean lowerCaseQTags,
    boolean lowerCaseBTags, boolean lowerCaseEscapeTags)
  {
    this.lowerCaseAttributes = lowerCaseAttributes;
    this.lowerCaseTags = lowerCaseTags;
    this.lowerCaseQAttributes = lowerCaseQAttributes;
    this.lowerCaseQTags = lowerCaseQTags;
    this.lowerCaseBTags = lowerCaseBTags;
    this.lowerCaseEscapeTags = lowerCaseEscapeTags;
  }
  
  /** This method gets called for every tag.  Override this method to intercept tag begins.
  *@return true to halt further processing.
  */
  @Override
  protected final boolean noteTag(String tagName, List<AttrNameValue> attributes)
    throws ManifoldCFException
  {
    Map<String,String> attrMap = new HashMap<String,String>(attributes.size());
    for (AttrNameValue nv : attributes)
    {
      String name = nv.getName();
      if (lowerCaseAttributes)
        name = nv.getName().toLowerCase(Locale.ROOT);
      attrMap.put(name, nv.getValue());
    }
    if (lowerCaseTags)
      tagName = tagName.toLowerCase(Locale.ROOT);
    int index = tagName.indexOf(":");
    String nameSpace;
    String localName;
    if (index == -1)
    {
      localName = tagName;
      nameSpace = null;
    }
    else
    {
      localName = tagName.substring(index+1);
      nameSpace = tagName.substring(0,index);
    }
    return noteTagEx(tagName, nameSpace, localName, attrMap);
  }

  /** Map version of the noteTag method.
  *@return true to halt further processing.
  */
  protected boolean noteTagEx(String tagName, String nameSpace, String localName, Map<String,String> attributes)
    throws ManifoldCFException
  {
    if (Logging.misc.isDebugEnabled())
      Logging.misc.debug(" Saw tag '"+tagName+"'");
    return false;
  }

  /** This method gets called for every end tag.  Override this method to intercept tag ends.
  *@return true to halt further processing.
  */
  @Override
  protected final boolean noteEndTag(String tagName)
    throws ManifoldCFException
  {
    if (lowerCaseTags)
      tagName = tagName.toLowerCase(Locale.ROOT);
    int index = tagName.indexOf(":");
    String nameSpace;
    String localName;
    if (index == -1)
    {
      localName = tagName;
      nameSpace = null;
    }
    else
    {
      localName = tagName.substring(index+1);
      nameSpace = tagName.substring(0,index);
    }

    return noteEndTagEx(tagName, nameSpace, localName);
  }

  /** Note end tag.
  */
  protected boolean noteEndTagEx(String tagName, String nameSpace, String localName)
    throws ManifoldCFException
  {
    if (Logging.misc.isDebugEnabled())
      Logging.misc.debug(" Saw end tag '"+tagName+"'");
    return false;
  }
  
  /** This method is called for every <? ... ?> construct, or 'qtag'.
  * This is not useful for HTML.
  *@return true to halt further processing.
  */
  @Override
  protected final boolean noteQTag(String tagName, List<AttrNameValue> attributes)
    throws ManifoldCFException
  {
    Map<String,String> attrMap = new HashMap<String,String>(attributes.size());
    for (AttrNameValue nv : attributes)
    {
      String name = nv.getName();
      if (lowerCaseQAttributes)
        name = nv.getName().toLowerCase(Locale.ROOT);
      attrMap.put(name, nv.getValue());
    }
    if (lowerCaseQTags)
      tagName = tagName.toLowerCase(Locale.ROOT);
    return noteQTagEx(tagName, attrMap);
  }

  /** Map version of the noteQTag method.
  *@return true to halt further processing.
  */
  protected boolean noteQTagEx(String tagName, Map<String,String> attributes)
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
  @Override
  protected final boolean noteBTag(String tagName)
    throws ManifoldCFException
  {
    if (lowerCaseBTags)
      tagName = tagName.toLowerCase(Locale.ROOT);
    return noteBTagEx(tagName);
  }

  /** New version of the noteBTag method.
  *@return true to halt further processing.
  */
  protected boolean noteBTagEx(String tagName)
    throws ManifoldCFException
  {
    if (Logging.misc.isDebugEnabled())
      Logging.misc.debug(" Saw BTag '"+tagName+"'");
    return false;
  }

  /** Called for the start of every cdata-like tag, e.g. <![ <token> [ ... ]]>
  *@param token may be empty!!!
  *@return true to halt further processing.
  */
  @Override
  protected final boolean noteEscaped(String token)
    throws ManifoldCFException
  {
    if (lowerCaseEscapeTags && token != null)
      token = token.toLowerCase(Locale.ROOT);
    return noteEscapedEx(token);
  }

  /** New version of the noteEscapedTag method.
  *@return true to halt further processing.
  */
  protected boolean noteEscapedEx(String token)
    throws ManifoldCFException
  {
    if (Logging.misc.isDebugEnabled())
      Logging.misc.debug(" Saw Escaped '"+token+"'");
    return false;
  }

  /** This method gets called for every token inside a btag.
  *@return true to halt further processing.
  */
  @Override
  protected final boolean noteBTagToken(String token)
    throws ManifoldCFException
  {
    if (lowerCaseBTags)
      token = token.toLowerCase(Locale.ROOT);
    return noteBTagTokenEx(token);
  }

  /** New version of the noteBTagToken method.
  *@return true to halt further processing.
  */
  protected boolean noteBTagTokenEx(String token)
    throws ManifoldCFException
  {
    if (Logging.misc.isDebugEnabled())
      Logging.misc.debug(" Saw BTag token '"+token+"'");
    return false;
  }

}
