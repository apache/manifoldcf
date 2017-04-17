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

package org.apache.manifoldcf.crawler.connectors.nuxeo;

/**
 * 
 * NuxeoConfiguration class
 * 
 * Class to keep the server configuration and specification paramenters.
 * 
 * @author David Arroyo Escobar <arroyoescobardavid@gmail.com>
 *
 */
public class NuxeoConfiguration {

  public static interface Server {

    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String PROTOCOL = "protocol";
    public static final String HOST = "host";
    public static final String PORT = "port";
    public static final String PATH = "path";

    public static final String PROTOCOL_DEFAULT_VALUE = "http";
    public static final String HOST_DEFAULT_VALUE = "";
    public static final String PORT_DEFAULT_VALUE = "8080";
    public static final String PATH_DEFAULT_VALUE = "/nuxeo";
    public static final String USERNAME_DEFAULT_VALUE = "";
    public static final String PASSWORD_DEFAULT_VALUE = "";

  }

  public static interface Specification {

    public static final String DOMAINS = "domains";
    public static final String DOMAIN = "domain";
    public static final String DOMAIN_KEY = "key";
    public static final String DOCUMENTS = "documents";
    public static final String PROCESS_TAGS = "process_tags";
    public static final String PROCESS_ATTACHMENTS = "process_attachments";
    public static final String DOCUMENTS_TYPE = "documentsType";
    public static final String DOCUMENT_TYPE = "documentType";
    public static final String DOCUMENT_TYPE_KEY = "key";

  }
}
