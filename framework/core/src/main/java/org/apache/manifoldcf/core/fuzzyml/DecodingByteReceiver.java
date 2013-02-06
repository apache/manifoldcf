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
import java.io.*;

/** This class represents a ByteReceiver that passes
* decoded characters on to a supplied CharacterReceiver.
*/
public class DecodingByteReceiver extends ByteReceiver
{
  protected final CharacterReceiver charReceiver;
  protected final String charSet;
  
  public DecodingByteReceiver(int chunkSize, String charSet, CharacterReceiver charReceiver)
    throws IOException
  {
    super();
    this.charSet = charSet;
    this.charReceiver = charReceiver;
  }
  
  /** Set the input stream.  The input stream must be
  * at the point where the bytes being received would start.
  * The stream is expected to be closed by the caller, when
  * the operations are all done.
  */
  @Override
  public void setInputStream(InputStream is)
    throws IOException
  {
    super.setInputStream(is);
    // Create a reader based on the encoding and the input stream
    Reader reader = new InputStreamReader(is,charSet);
    charReceiver.setReader(reader);
  }

  /** Receive a byte stream and process up to chunksize bytes,
  *@return true if end reached.
  */
  @Override
  public boolean dealWithBytes()
    throws IOException, ManifoldCFException
  {
    return charReceiver.dealWithCharacters();
  }
  
  /** Finish up all processing.
  */
  @Override
  public void finishUp()
    throws ManifoldCFException
  {
    super.finishUp();
    charReceiver.finishUp();
  }

}
