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

package org.apache.manifoldcf.core.extmimemap;

import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import java.util.*;

/** Map file extension to a mime type.
*/
public class ExtensionMimeMap
{
  protected final static Map<String,String> mimeMap;
  static {
    mimeMap = new HashMap<String,String>();
    mimeMap.put("xml", "text/xml");
    mimeMap.put("csv", "text/csv");
    mimeMap.put("json", "application/json");
    mimeMap.put("pdf", "application/pdf");
    mimeMap.put("rtf", "text/rtf");
    mimeMap.put("html", "text/html");
    mimeMap.put("htm", "text/html");
    mimeMap.put("doc", "application/msword");
    mimeMap.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    mimeMap.put("ppt", "application/vnd.ms-powerpoint");
    mimeMap.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    mimeMap.put("xls", "application/vnd.ms-excel");
    mimeMap.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    mimeMap.put("odt", "application/vnd.oasis.opendocument.text");
    mimeMap.put("ott", "application/vnd.oasis.opendocument.text");
    mimeMap.put("odp", "application/vnd.oasis.opendocument.presentation");
    mimeMap.put("otp", "application/vnd.oasis.opendocument.presentation");
    mimeMap.put("ods", "application/vnd.oasis.opendocument.spreadsheet");
    mimeMap.put("ots", "application/vnd.oasis.opendocument.spreadsheet");
    mimeMap.put("txt", "text/plain");
    mimeMap.put("log", "text/plain");
  }

  /** Map extension to mime type */
  public static String mapToMimeType(String extension)
  {
    return mimeMap.get(extension.toLowerCase(java.util.Locale.ROOT));
  }
  
}
