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
