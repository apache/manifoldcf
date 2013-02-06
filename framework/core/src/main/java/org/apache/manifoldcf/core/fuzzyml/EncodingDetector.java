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

/** This interface represents a receiver for bytes.
* Implementers of this interface will accept documents a byte at a time,
* AFTER an encoding has been set.
*/
public abstract class EncodingDetector extends ByteReceiver
{
  protected String currentEncoding = null;
  
  /** Constructor */
  public EncodingDetector(int chunkSize)
  {
    super(chunkSize);
  }

  /** Accept a starting encoding value.
  */
  public void setEncoding(String encoding)
  {
    currentEncoding = encoding;
  }
  
  /** Read out the detected encoding, when finished.
  */
  public String getEncoding()
  {
    return currentEncoding;
  }
  
}
