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

package org.apache.manifoldcf.agents.transformation.documentfilter;

/** Parameters for DocumentFilter transformation connector.
 */
public class DocumentFilterConfig {

  // Configuration parameters
  
  // Specification nodes and values
  public static final String NODE_MAXLENGTH = "maxlength";
  public static final String MAXLENGTH_DEFAULT = "16777216";
  public static final String NODE_MIMETYPES = "mimetypes";
  public static final String MIMETYPES_DEFAULT =
                        "application/msword\n"
		        + "application/vnd.ms-excel\n"
		        + "application/vnd.openxmlformats-officedocument.wordprocessingml.document\n"
		        + "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\n"
		        + "text/html\n"
		        + "application/pdf\n"
		        + "application/vnd.ms-powerpoint\n"
		        + "application/vnd.openxmlformats-officedocument.presentationml.presentation\n"
		        + "application/vnd.oasis.opendocument.text\n"
		        + "application/vnd.oasis.opendocument.spreadsheet\n"
		        + "application/vnd.oasis.opendocument.formula\n"
		        + "application/rtf\n" + "text/plain\n" + "audio/mpeg\n"
		        + "audio/x-wav\n" + "audio/ogg\n" + "audio/flac\n"
		        + "application/x-bittorrent";
  public static final String NODE_EXTENSIONS = "extensions";
  public static final String EXTENSIONS_DEFAULT =
                    "doc\n" + "docx\n" + "xls\n" + "xlsx\n" + "ppt\n" + "pptx\n"
		    + "html\n" + "pdf\n" + "odt\n" + "ods\n" + "rtf\n" + "txt\n" + "mp3\n"
		    + "mp4\n" + "wav\n" + "ogg\n" + "flac\n" + "torrent";
  public static final String ATTRIBUTE_VALUE = "value";
}
