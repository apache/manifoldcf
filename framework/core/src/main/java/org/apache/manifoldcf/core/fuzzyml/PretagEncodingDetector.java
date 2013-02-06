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

/** This class represents the ability to pick an encoding out of a <?...?> preamble.
*/
public class PretagEncodingDetector extends PretagParseState implements EncodingDetector
{
  protected String currentEncoding = null;

  /** Constructor.  Pass in the post-preamble character receiver.
  * 
  */
  public PretagEncodingDetector(CharacterReceiver postPreambleReceiver)
  {
    super(postPreambleReceiver);
  }

  /** Accept a starting encoding value.
  */
  @Override
  public void setEncoding(String encoding)
  {
    currentEncoding = encoding;
  }
  
  /** Read out the detected encoding, when finished.
  */
  @Override
  public String getEncoding()
  {
    return currentEncoding;
  }

  /** Receive a pretag.
  */
  @Override
  protected void notePretag(String tagName, Map<String,String> attributes)
    throws ManifoldCFException
  {
    if (tagName.equals("xml"))
    {
      String newEncoding = attributes.get("encoding");
      if (newEncoding != null)
      {
        // Here we can do something fancy, like override the old encoding only if it
        // has the same basic structure as the original encoding; e.g. ignore 8-bit
        // encodings if the originally specified one is 16-bit etc.
        // MHL
        currentEncoding = newEncoding;
      }
    }
  }


}
