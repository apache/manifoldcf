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

package org.apache.manifoldcf.crawler.connectors.jira;

import java.io.IOException;
import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.ParseException;

/** An instance of this class represents a Jira JSON object, and the parser hooks
* needed to extract the data from the JSON event stream we use to parse it.  It
* is meant to be overridden (selectively) by derived classes.
*/
public class JiraJSONResponse implements ContentHandler {

  public JiraJSONResponse() {
  }
  
  /**
  * Receive notification of the beginning of JSON processing.
  * The parser will invoke this method only once.
  *
  * @throws ParseException
  *                      - JSONParser will stop and throw the same exception to the caller when receiving this exception.
  */
  @Override
  public void startJSON() throws ParseException, IOException {
  }
       
  /**
  * Receive notification of the end of JSON processing.
  *
  * @throws ParseException
  */
  @Override
  public void endJSON() throws ParseException, IOException {
  }
       
  /**
  * Receive notification of the beginning of a JSON object.
  *
  * @return false if the handler wants to stop parsing after return.
  * @throws ParseException
  *          - JSONParser will stop and throw the same exception to the caller when receiving this exception.
  * @see #endJSON
  */
  @Override
  public boolean startObject() throws ParseException, IOException {
    return true;
  }
       
  /**
  * Receive notification of the end of a JSON object.
  *
  * @return false if the handler wants to stop parsing after return.
  * @throws ParseException
  *
  * @see #startObject
  */
  @Override
  public boolean endObject() throws ParseException, IOException {
    return true;
  }
       
  /**
  * Receive notification of the beginning of a JSON object entry.
  *
  * @param key - Key of a JSON object entry.
  *
  * @return false if the handler wants to stop parsing after return.
  * @throws ParseException
  *
  * @see #endObjectEntry
  */
  @Override
  public boolean startObjectEntry(String key) throws ParseException, IOException {
    return true;
  }
       
  /**
  * Receive notification of the end of the value of previous object entry.
  *
  * @return false if the handler wants to stop parsing after return.
  * @throws ParseException
  *
  * @see #startObjectEntry
  */
  @Override
  public boolean endObjectEntry() throws ParseException, IOException {
    return true;
  }
       
  /**
  * Receive notification of the beginning of a JSON array.
  *
  * @return false if the handler wants to stop parsing after return.
  * @throws ParseException
  *
  * @see #endArray
  */
  @Override
  public boolean startArray() throws ParseException, IOException {
    return true;
  }
       
  /**
  * Receive notification of the end of a JSON array.
  *
  * @return false if the handler wants to stop parsing after return.
  * @throws ParseException
  *
  * @see #startArray
  */
  @Override
  public boolean endArray() throws ParseException, IOException {
    return true;
  }
       
  /**
  * Receive notification of the JSON primitive values:
  *      java.lang.String,
  *      java.lang.Number,
  *      java.lang.Boolean
  *      null
  *
  * @param value - Instance of the following:
  *                      java.lang.String,
  *                      java.lang.Number,
  *                      java.lang.Boolean
  *                      null
  *
  * @return false if the handler wants to stop parsing after return.
  * @throws ParseException
  */
  @Override
  public boolean primitive(Object value) throws ParseException, IOException {
    return true;
  }

}
