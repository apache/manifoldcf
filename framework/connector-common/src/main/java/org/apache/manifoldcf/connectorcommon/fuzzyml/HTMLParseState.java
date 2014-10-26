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

/** This class takes the output of the basic tag parser and converts it for
* typical HTML usage.  It takes the attribute lists, for instance, and converts
* them to lowercased maps.  It also bashes all tag names etc to lower case as
* well.
*/
public class HTMLParseState extends TagParseState
{
  
  /** Constructor.
  */
  public HTMLParseState()
  {
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
      attrMap.put(nv.getName().toLowerCase(Locale.ROOT), nv.getValue());
    }
    return noteTag(tagName.toLowerCase(Locale.ROOT), attrMap);
  }

  /** Map version of the noteTag method.
  *@return true to halt further processing.
  */
  protected boolean noteTag(String tagName, Map<String,String> attributes)
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
    return noteTagEnd(tagName.toLowerCase(Locale.ROOT));
  }

  /** Note end tag.
  */
  protected boolean noteTagEnd(String tagName)
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
    return super.noteQTag(tagName, attributes);
  }

  /** This method is called for every <! <token> ... > construct, or 'btag'.
  * Override it to intercept these.
  *@return true to halt further processing.
  */
  @Override
  protected final boolean noteBTag(String tagName)
    throws ManifoldCFException
  {
    return super.noteBTag(tagName);
  }

  /** This method is called for the end of every btag, or any time
  * there's a naked '>' in the document.  Override it if you want to intercept these.
  *@return true to halt further processing.
  */
  @Override
  protected final boolean noteEndBTag()
    throws ManifoldCFException
  {
    return super.noteEndBTag();
  }

  /** Called for the start of every cdata-like tag, e.g. <![ <token> [ ... ]]>
  *@param token may be empty!!!
  *@return true to halt further processing.
  */
  @Override
  protected final boolean noteEscaped(String token)
    throws ManifoldCFException
  {
    return super.noteEscaped(token);
  }

  /** Called for the end of every cdata-like tag.
  *@return true to halt further processing.
  */
  @Override
  protected final boolean noteEndEscaped()
    throws ManifoldCFException
  {
    return super.noteEndEscaped();
  }

  /** This method gets called for every token inside a btag.
  *@return true to halt further processing.
  */
  @Override
  protected final boolean noteBTagToken(String token)
    throws ManifoldCFException
  {
    return super.noteBTagToken(token);
  }

  /** This method gets called for every character that is found within an
  * escape block, e.g. CDATA.
  * Override this method to intercept such characters.
  *@return true to halt further processing.
  */
  @Override
  protected final boolean noteEscapedCharacter(char thisChar)
    throws ManifoldCFException
  {
    return super.noteEscapedCharacter(thisChar);
  }

}
