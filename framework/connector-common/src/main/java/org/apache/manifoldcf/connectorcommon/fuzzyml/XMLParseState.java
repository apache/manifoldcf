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
* typical XML usage.  It takes the attribute lists, for instance, and converts
* them to case-sensitive maps.
*/
public class XMLParseState extends TagParseState
{
  
  /** Constructor.
  */
  public XMLParseState()
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
      attrMap.put(nv.getName(), nv.getValue());
    }
    return noteTag(tagName, attrMap);
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

  /** This method is called for every <? ... ?> construct, or 'qtag'.
  * Override it to intercept such constructs.
  *@return true to halt further processing.
  */
  @Override
  protected final boolean noteQTag(String tagName, List<AttrNameValue> attributes)
    throws ManifoldCFException
  {
    Map<String,String> attrMap = new HashMap<String,String>(attributes.size());
    for (AttrNameValue nv : attributes)
    {
      attrMap.put(nv.getName(), nv.getValue());
    }
    return noteQTag(tagName, attrMap);
  }
  
  /** Map version of noteQTag method.
  *@return true to halt further processing.
  */
  protected boolean noteQTag(String tagName, Map<String,String> attributes)
    throws ManifoldCFException
  {
    if (Logging.misc.isDebugEnabled())
      Logging.misc.debug(" Saw QTag '"+tagName+"'");
    return false;
  }

}
