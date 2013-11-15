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
     * Properties
     */
    public static final String PROPERTIES_PARAM = "properties";

    public static final String PROTOCOL_DEFAULT_VALUE = "IMAP";
    public static final String PORT_DEFAULT_VALUE = "25";


    public static final String NODE_PROPERTIES = "properties";
    public static final String NODE_METADATA = "metadata";
    public static final String NODE_FILTER = "filter";
    public static final String SERVER_PROPERTY = "serverproperty";
    public static final String VALUE = "p_value";
    public static final String ATTRIBUTE_NAME = "name";

    public static final String ATTRIBUTE_VALUE = "value";
    public static final String[] BASIC_METADATA = {"To","From","Subject","Body","Date","Encoding of Attachment","MIME type of attachment"};
    public static final String[] BASIC_SEARCHABLE_ATTRIBUTES = {"To","From","Subject","Body","Date","Folder"};
    protected final static String ACTIVITY_FETCH = "fetch";


    public static final String RELATIONSHIP_CHILD = "child";
    public static final String PROTOCOL_IMAP = "imap";
    public static final String PROTOCOL_IMAP_PROVIDER = "imaps";
    public static final String PROTOCOL_POP3 = "POP3";
    public static final String PROTOCOL_POP3_PROVIDER = "pop3";
    public static final String FOLDER_INBOX = "INBOX";
    public static final String EMAIL_SUBJECT = "subject";
    public static final String EMAIL_FROM = "from";
    public static final String EMAIL_TO = "to";
    public static final String EMAIL_BODY = "body";
    public static final String ATTRIBUTE_FOLDER = "Folder";
    public static final String EMAIL_DATE = "date";
    public static final String EMAIL_ATTACHMENT_ENCODING = "encoding of attachment";
    public static final String EMAIL_ATTACHMENT_MIMETYPE = "mime type of attachment";
    public static final String EMAIL_VERSION = "1.0";
    public static final String MIMETYPE_TEXT_PLAIN = "text/plain";
    public static final String MIMETYPE_HTML = "text/html";
    public static final String ENCODING_FIELD = "encoding";
    public static final String MIMETYPE_FIELD = "mimetype";
    protected final static long SESSION_EXPIRATION_MILLISECONDS = 300000L;
    //public static final String TO = "To";
}
