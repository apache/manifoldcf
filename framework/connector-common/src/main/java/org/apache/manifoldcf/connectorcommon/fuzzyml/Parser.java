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
import java.io.*;
import java.nio.charset.StandardCharsets;

/** This is the main parser class.
* This class has an entry point for both parsing XML and HTML.  The way the
* parser works is to accept both an input stream (which the caller is responsible
* for closing) as well as a CharacterReceiver that will do the actual parsing.
* This class is responsible mainly for setup and character set detection, 
*/
public class Parser
{
  
  /** Constructor.
  * Someday there will be a constructor which accepts character detection
  * configuration information, but for now there is none.
  */
  public Parser()
  {
  }
  
  /** Parse an input stream with character set detection.
  * This method uses BOM (byte order mark) and the xml encoding tag to determine the character encoding to use.
  * The caller may pass in a starting character encoding, which functions as the default if no better determination
  * is made.
  *@param startingCharset is the starting character set.  Pass null if this is unknown.
  *@param inputStream is the input stream.  It is the caller's responsibility to close the stream when the parse is done.
  *@param characterReceiver is the character receiver that will actually do the parsing.
  */
  public void parseWithCharsetDetection(String startingCharset, InputStream inputStream, CharacterReceiver characterReceiver)
    throws IOException, ManifoldCFException
  {
    // Wrap the input stream, before we do anything else
    ReplayableInputStream replayableInputStream = new ReplayableInputStream(inputStream);
    
    // First go-around: use the BOM detector with nothing downstream, since we don't know the character set yet.
    BOMEncodingDetector bomEncodingDetector = new BOMEncodingDetector(null);
    bomEncodingDetector.setEncoding(startingCharset);
    if (bomEncodingDetector.dealWithBytes(replayableInputStream) == false)
      bomEncodingDetector.finishUp();
    
    // Update our notion of what the character set is
    startingCharset = bomEncodingDetector.getEncoding();
    if (startingCharset == null)
      startingCharset = StandardCharsets.UTF_8.name();
    // Reset the stream
    replayableInputStream.restart(false);
    // Set up a detection chain that includes the XML detector.
    // BOMEncodingDetector (for BOM detection) -> XMLEncodingDetector (for xml encoding tag access)
    XMLEncodingDetector xmlEncodingDetector = new XMLEncodingDetector();
    xmlEncodingDetector.setEncoding(startingCharset);
    bomEncodingDetector = new BOMEncodingDetector(new DecodingByteReceiver(1024,startingCharset,xmlEncodingDetector));
    // Rerun the detection; this should finalize the value.
    if (bomEncodingDetector.dealWithBytes(replayableInputStream) == false)
      bomEncodingDetector.finishUp();

    // Get the final charset determination
    startingCharset = xmlEncodingDetector.getEncoding();
    // Reset for the final time
    replayableInputStream.restart(true);
    // Set up the whole chain and parse
    bomEncodingDetector = new BOMEncodingDetector(new DecodingByteReceiver(65536,startingCharset,characterReceiver));
    if (bomEncodingDetector.dealWithBytes(replayableInputStream) == false)
      bomEncodingDetector.finishUp();
  }
  
  /** Parse an input stream without character set detection.
  *@param startingCharset is the starting character set.  If null is passed, the code will presume utf-8.
  *@param inputStream is the input stream.  It is the caller's responsibility to close the stream when the parse is done.
  *@param characterReceiver is the character receiver that will actually do the parsing.
  */
  public void parseWithoutCharsetDetection(String startingCharset, InputStream inputStream, CharacterReceiver characterReceiver)
    throws IOException, ManifoldCFException
  {
    if (startingCharset == null)
      startingCharset = StandardCharsets.UTF_8.name();
    ByteReceiver byteReceiver = new DecodingByteReceiver(65536, startingCharset, characterReceiver);
    // Process to completion
    if (byteReceiver.dealWithBytes(inputStream) == false)
      byteReceiver.finishUp();
  }

}
