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
package org.apache.manifoldcf.crawler.connectors.webcrawler;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import java.util.regex.*;
import java.util.*;

/** This class is the handler for HTML content grepping during state transitions */
public class FindContentHandler extends FindHandler implements IHTMLHandler
{
  protected final Pattern contentPattern;
  protected final StringBuilder contentBuffer = new StringBuilder();

  public FindContentHandler(String parentURI, Pattern contentPattern)
  {
    super(parentURI);
    this.contentPattern = contentPattern;
  }

  /** Apply overrides */
  public void applyOverrides(LoginParameters lp)
    throws ManifoldCFException
  {
    if (targetURI != null && lp != null)
    {
      if (lp.getOverrideTargetURL() != null)
        super.noteDiscoveredLink(lp.getOverrideTargetURL());
    }
  }

  /** Note a character of text.
  * Structured this way to keep overhead low for handlers that don't use text.
  */
  @Override
  public void noteTextCharacter(char textCharacter)
    throws ManifoldCFException
  {
    if (targetURI != null)
      return;
    // Build characters up into lines, and apply the regexp against them
    if (textCharacter == '\t' || textCharacter >= ' ')
      contentBuffer.append(textCharacter);
    else
    {
      String bufferContents = contentBuffer.toString();
      contentBuffer.setLength(0);
      if (contentPattern.matcher(bufferContents).find())
        targetURI = "";
    }
  }

  /** Note a meta tag */
  @Override
  public void noteMetaTag(Map metaAttributes)
    throws ManifoldCFException
  {
  }

  /** Note the start of a form */
  @Override
  public void noteFormStart(Map formAttributes)
    throws ManifoldCFException
  {
  }

  /** Note an input tag */
  @Override
  public void noteFormInput(Map inputAttributes)
    throws ManifoldCFException
  {
  }

  /** Note the end of a form */
  @Override
  public void noteFormEnd()
    throws ManifoldCFException
  {
  }

  /** Note discovered href */
  @Override
  public void noteAHREF(String rawURL)
    throws ManifoldCFException
  {
  }

  /** Note discovered href */
  @Override
  public void noteLINKHREF(String rawURL)
    throws ManifoldCFException
  {
  }

  /** Note discovered IMG SRC */
  @Override
  public void noteIMGSRC(String rawURL)
    throws ManifoldCFException
  {
  }

  /** Note discovered FRAME SRC */
  @Override
  public void noteFRAMESRC(String rawURL)
    throws ManifoldCFException
  {
  }


}
