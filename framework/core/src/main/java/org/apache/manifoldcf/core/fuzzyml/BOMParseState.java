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
import java.util.*;

/** This class represents the parse state of the BOM (byte order mark) parser.
* The byte order mark parser looks for a byte order mark at the start of a byte sequence,
* and based on whether it finds it or not, and what it finds, selects a preliminary character encoding.
* Once a preliminary character encoding is determined, an EncodingAccepter is notified,
* and further bytes are sent to a provided ByteReceiver.
*/
public class BOMParseState extends EncodingDetector
{
  protected String encoding = null;
  protected final ByteReceiver byteReceiver;
  
  /** Constructor.  Pass in the receiver of all overflow bytes.
  * If no receiver is passed in, the detector will stop as soon as the
  * BOM is either seen, or not seen.
  */
  public BOMParseState(ByteReceiver byteReceiver)
  {
    super(8);
    this.byteReceiver = byteReceiver;
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
  
  /** Receive a byte.
  */
  @Override
  public boolean dealWithByte(byte b)
    throws ManifoldCFException
  {
    // MHL
    return true;
  }
  
  /** Finish up all processing.
  */
  @Override
  public void finishUp()
    throws ManifoldCFException
  {
    // MHL
  }

}
