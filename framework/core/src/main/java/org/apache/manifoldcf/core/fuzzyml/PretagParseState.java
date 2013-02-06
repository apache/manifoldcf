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

/** This class represents the ability to parse <?...?> preamble tags.
*/
public class PretagParseState extends SingleCharacterReceiver
{
  protected final CharacterReceiver postPreambleReceiver;

  protected static final int PRETAGPARSESTATE_NORMAL = 0;
  protected static final int PRETAGPARSESTATE_SAWLEFTBRACKET = 1;
  protected static final int PRETAGPARSESTATE_SAWEXCLAMATION = 2;
  protected static final int PRETAGPARSESTATE_SAWQUESTION = 3;
  protected static final int PRETAGPARSESTATE_IN_TAG_SAW_QUESTION = 4;
  protected static final int PRETAGPARSESTATE_IN_TAG_NAME = 7;
  protected static final int PRETAGPARSESTATE_IN_ATTR_NAME = 8;
  protected static final int PRETAGPARSESTATE_IN_ATTR_VALUE = 9;
  protected static final int PRETAGPARSESTATE_IN_ATTR_LOOKING_FOR_VALUE = 12;
  protected static final int PRETAGPARSESTATE_IN_SINGLE_QUOTES_ATTR_VALUE = 13;
  protected static final int PRETAGPARSESTATE_IN_DOUBLE_QUOTES_ATTR_VALUE = 14;
  protected static final int PRETAGPARSESTATE_POST = 15;

  protected int currentState = PRETAGPARSESTATE_NORMAL;

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
    mapLookup.put("apos","'");
  }

  /** Constructor.  Pass in the post-preamble character receiver.
  * 
  */
  public PretagParseState(CharacterReceiver postPreambleReceiver)
  {
    // Small buffer - preambles are short
    super(1024);
    this.postPreambleReceiver = postPreambleReceiver;
  }

  /** Receive a byte.
  * @return true if done.
  */
  @Override
  public boolean dealWithCharacter(char c)
    throws ManifoldCFException
  {
    // MHL
    return true;
  }
  
  protected void notePretag(String tagName, Map<String,String> attributes)
    throws ManifoldCFException
  {
    Logging.misc.debug(" Saw pretag '"+tagName+"'");
  }


}
