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
import java.util.*;
import java.io.*;

/** This is the XML encoding detector.
* It is basically looking for the preamble's <?xml ... ?> tag, which it parses
* looking for the "encoding" attribute.  It stops either when it is beyond
* any possibility of finding the preamble, or it finds the tag, whichever comes first.
*/
public class XMLEncodingDetector extends XMLParseState implements EncodingDetector
{
  
  protected String encoding = null;
  
  /** Constructor.
  */
  public XMLEncodingDetector()
  {
  }
  
  /** Set initial encoding.
  */
  @Override
  public void setEncoding(String encoding)
  {
    this.encoding = encoding;
  }

  /** Retrieve final encoding determination.
  */
  @Override
  public String getEncoding()
  {
    return encoding;
  }

  /** Map version of the noteTag method.
  *@return true to halt further processing.
  */
  @Override
  protected boolean noteTag(String tagName, Map<String,String> attributes)
    throws ManifoldCFException
  {
    // Terminate immediately.
    return true;
  }
  
  /** This method gets called for every end tag.  Override this method to intercept tag ends.
  *@return true to halt further processing.
  */
  @Override
  protected boolean noteEndTag(String tagName)
    throws ManifoldCFException
  {
    return true;
  }

  /** Map version of noteQTag method.
  *@return true to halt further processing.
  */
  protected boolean noteQTag(String tagName, Map<String,String> attributes)
    throws ManifoldCFException
  {
    if (tagName.equals("xml"))
    {
      // Look for "encoding" attribute
      String value = attributes.get("encoding");
      if (value != null)
        encoding = value;
    }
    // Either way, stop now.
    return true;
  }

  /** This method is called for every <! <token> ... > construct, or 'btag'.
  * Override it to intercept these.
  *@return true to halt further processing.
  */
  @Override
  protected boolean noteBTag(String tagName)
    throws ManifoldCFException
  {
    return true;
  }

  /** This method is called for the end of every btag, or any time
  * there's a naked '>' in the document.  Override it if you want to intercept these.
  *@return true to halt further processing.
  */
  protected boolean noteEndBTag()
    throws ManifoldCFException
  {
    return true;
  }
  
  /** Called for the start of every cdata-like tag, e.g. <![ <token> [ ... ]]>
  *@param token may be empty!!!
  *@return true to halt further processing.
  */
  @Override
  protected boolean noteEscaped(String token)
    throws ManifoldCFException
  {
    return true;
  }

  /** Called for the end of every cdata-like tag.
  *@return true to halt further processing.
  */
  @Override
  protected boolean noteEndEscaped()
    throws ManifoldCFException
  {
    return true;
  }

  /** This method gets called for every token inside a btag.
  *@return true to halt further processing.
  */
  @Override
  protected boolean noteBTagToken(String token)
    throws ManifoldCFException
  {
    return true;
  }
  
  /** This method gets called for every character that is not part of a tag etc.
  * Override this method to intercept such characters.
  *@return true to halt further processing.
  */
  @Override
  protected boolean noteNormalCharacter(char thisChar)
    throws ManifoldCFException
  {
    return true;
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
    return true;
  }
  
}
