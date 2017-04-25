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

package org.apache.manifoldcf.crawler.connectors.email;


/**
* Parameters data for the Email repository connector.
*/
public class EmailConfig {

  /**
  * Username
  */
  public static final String USERNAME_PARAM = "username";

  /**
  * Password
  */
  public static final String PASSWORD_PARAM = "password";

  /**
  * Protocol
  */
  public static final String PROTOCOL_PARAM = "protocol";

  /**
  * Server name
  */
  public static final String SERVER_PARAM = "server";

  /**
  * Port
  */
  public static final String PORT_PARAM = "port";

  /**
  * URL template
  */
  public static final String URL_PARAM = "url";
  
  /**
  * Attachment URL template
  */
  public static final String ATTACHMENT_URL_PARAM = "attachmenturl";
  
  // Protocol options
  
  public static final String PROTOCOL_IMAP = "IMAP";
  public static final String PROTOCOL_IMAPS = "IMAP-SSL";
  public static final String PROTOCOL_POP3 = "POP3";
  public static final String PROTOCOL_POP3S = "POP3-SSL";
  
  // Protocol providers
  
  public static final String PROTOCOL_IMAP_PROVIDER = "imap";
  public static final String PROTOCOL_IMAPS_PROVIDER = "imaps";
  public static final String PROTOCOL_POP3_PROVIDER = "pop3";
  public static final String PROTOCOL_POP3S_PROVIDER = "pop3s";
  
  // Default values and various other constants
  
  public static final String PROTOCOL_DEFAULT_VALUE = "IMAP";
  public static final String PORT_DEFAULT_VALUE = "";
  public static final String[] BASIC_METADATA = {"To","From","Subject","Date","Encoding of Attachment",
      "MIME Type of attachment", "File Name of Attachment"};
  public static final String BASIC_EXTRACT_EMAIL = "Use E-Mail Extractor";
  public static final String[] BASIC_SEARCHABLE_ATTRIBUTES = {"To","From","Subject","Body","Start Date", "End Date"};

  // Specification nodes
  
  public static final String NODE_PROPERTIES = "properties";
  public static final String NODE_METADATA = "metadata";
  public static final String NODE_EXTRACT_EMAIL = "extractemail";
  public static final String NODE_FILTER = "filter";
  public static final String NODE_FOLDER = "folder";
  
  public static final String ATTRIBUTE_NAME = "name";
  public static final String ATTRIBUTE_VALUE = "value";

  // Metadata field names
  
  public static final String EMAIL_SUBJECT = "subject";
  public static final String EMAIL_FROM = "from";
  public static final String EMAIL_TO = "to";
  public static final String EMAIL_BODY = "body";
  public static final String EMAIL_DATE = "date";
  public static final String EMAIL_ATTACHMENT_ENCODING = "encoding of attachment";
  public static final String EMAIL_ATTACHMENT_MIMETYPE = "mime type of attachment";
  public static final String EMAIL_ATTACHMENTNAME = "file name of attachment";
  public static final String EMAIL_VERSION = "1.0";

  // Date field names for filtering
  public static final String EMAIL_FILTERING_DATE_FORMAT = "dd/MM/yyyy";
  public static final String EMAIL_START_DATE = "start date";
  public static final String EMAIL_END_DATE = "end date";

  // Mime types
  
  public static final String MIMETYPE_TEXT_PLAIN = "text/plain";
  public static final String MIMETYPE_HTML = "text/html";
  public static final String MIMETYPE_MULTIPART_GENERIC = "multipart/*";
  public static final String MIMETYPE_MULTIPART_ALTERNATIVE = "multipart/alternative";
  
  // Fields
  
  public static final String ENCODING_FIELD = "encoding";
  public static final String MIMETYPE_FIELD = "mimetype";
  public static final String ATTACHMENTNAME_FIELD = "attachmentname";
  public static final String MAILSUBJECT_FIELD = "mailsubject";
  //public static final String TO = "To";
  
  // Activity names
  
  public final static String ACTIVITY_FETCH = "fetch";

}