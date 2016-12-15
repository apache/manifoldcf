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
import org.apache.manifoldcf.connectorcommon.fuzzyml.*;
import java.util.*;

/** This class interprets the tag stream generated by the BasicParseState class, and keeps track of the form tags. */
public class FormParseState extends LinkParseState
{
  // States for form handling.
  protected final static int FORMPARSESTATE_NORMAL = 0;
  protected final static int FORMPARSESTATE_IN_FORM = 1;
  protected final static int FORMPARSESTATE_IN_SELECT = 2;
  protected final static int FORMPARSESTATE_IN_TEXTAREA = 3;
  protected final static int FORMPARSESTATE_IN_OPTION = 4;
  
  protected int formParseState = FORMPARSESTATE_NORMAL;
  protected String selectName = null;
  protected String selectMultiple = null;
  protected String optionValue = null;
  protected String optionSelected = null;
  protected StringBuilder optionValueText = null;
  
  public FormParseState(IHTMLHandler handler)
  {
    super(handler);
  }

  // Override methods having to do with notification of tag discovery

  @Override
  protected boolean noteNonscriptTag(String tagName, Map<String,String> attributes)
    throws ManifoldCFException
  {
    if (super.noteNonscriptTag(tagName,attributes))
      return true;
    switch (formParseState)
    {
    case FORMPARSESTATE_NORMAL:
      if (tagName.equals("form"))
      {
        formParseState = FORMPARSESTATE_IN_FORM;
        handler.noteFormStart(attributes);
      }
      break;
    case FORMPARSESTATE_IN_FORM:
      if (tagName.equals("input"))
      {
        String type = (String)attributes.get("type");
        // We're only interested in form elements that can actually transmit data
        if (type == null || (!type.toLowerCase(Locale.ROOT).equals("button")
            && !type.toLowerCase(Locale.ROOT).equals("reset")
            && !type.toLowerCase(Locale.ROOT).equals("image")))
          handler.noteFormInput(attributes);
      }
      else if (tagName.equals("select"))
      {
        selectName = (String)attributes.get("name");
        selectMultiple = (String)attributes.get("multiple");
        formParseState = FORMPARSESTATE_IN_SELECT;
      }
      else if (tagName.equals("textarea"))
      {
        formParseState = FORMPARSESTATE_IN_TEXTAREA;
        Map textareaMap = new HashMap();
        textareaMap.put("type","textarea");
        // Default value is too tough to meaningfully compute because of the embedded tags etc.  Known limitation.
        textareaMap.put("value","");
        handler.noteFormInput(textareaMap);
      }
      else if (tagName.equals("button"))
      {
        String type = (String)attributes.get("type");
        if (type == null || type.toLowerCase(Locale.ROOT).equals("submit"))
        {
          // Same as input type="submit"
          handler.noteFormInput(attributes);
        }
      }
      else if (tagName.equals("isindex"))
      {
        Map indexMap = new HashMap();
        indexMap.put("type","text");
      }
      break;
    case FORMPARSESTATE_IN_SELECT:
      if (tagName.equals("option"))
      {
        optionValue = (String)attributes.get("value");
        optionSelected = (String)attributes.get("selected");
        formParseState = FORMPARSESTATE_IN_OPTION;
        // In case there's no end tag, if we have everything we need, do it now.
        if (optionValue != null)
        {
          Map optionMap = new HashMap();
          optionMap.put("type","select");
          optionMap.put("name",selectName);
          optionMap.put("multiple",selectMultiple);
          optionMap.put("value",optionValue);
          optionMap.put("selected",optionSelected);
          handler.noteFormInput(optionMap);
        }
        else
          optionValueText = new StringBuilder();
      }
      break;
    case FORMPARSESTATE_IN_OPTION:
      break;
    case FORMPARSESTATE_IN_TEXTAREA:
      break;
    default:
      throw new ManifoldCFException("Unknown form parse state: "+Integer.toString(formParseState));
    }
    return false;
  }

  @Override
  protected boolean noteNonscriptEndTag(String tagName)
    throws ManifoldCFException
  {
    if (super.noteNonscriptEndTag(tagName))
      return true;
    switch (formParseState)
    {
    case FORMPARSESTATE_NORMAL:
      break;
    case FORMPARSESTATE_IN_FORM:
      if (tagName.equals("form"))
      {
        handler.noteFormEnd();
        formParseState = FORMPARSESTATE_NORMAL;
      }
      break;
    case FORMPARSESTATE_IN_SELECT:
      formParseState = FORMPARSESTATE_IN_FORM;
      selectName = null;
      selectMultiple = null;
      break;
    case FORMPARSESTATE_IN_OPTION:
      if (tagName.equals("option"))
      {
        // If we haven't already emitted the option, emit it now.
        if (optionValueText != null)
        {
          Map optionMap = new HashMap();
          optionMap.put("type","select");
          optionMap.put("name",selectName);
          optionMap.put("multiple",selectMultiple);
          optionMap.put("value",optionValueText.toString());
          optionMap.put("selected",optionSelected);
          handler.noteFormInput(optionMap);
        }
        formParseState = FORMPARSESTATE_IN_SELECT;
        optionSelected = null;
        optionValue = null;
        optionValueText = null;
      }
      break;
    case FORMPARSESTATE_IN_TEXTAREA:
      if (tagName.equals("textarea"))
        formParseState = FORMPARSESTATE_IN_FORM;
      break;
    default:
      throw new ManifoldCFException("Unknown form parse state: "+Integer.toString(formParseState));
    }
    return false;
  }

  @Override
  protected boolean noteNormalCharacter(char thisChar)
    throws ManifoldCFException
  {
    if (super.noteNormalCharacter(thisChar))
      return true;
    if (formParseState == FORMPARSESTATE_IN_OPTION)
    {
      if (optionValueText != null)
        optionValueText.append(thisChar);
    }
    else
      handler.noteTextCharacter(thisChar);
    return false;
  }

}
