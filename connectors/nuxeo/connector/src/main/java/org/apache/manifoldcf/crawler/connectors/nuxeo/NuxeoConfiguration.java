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

  public interface Server {

    String USERNAME = "username";
    String PASSWORD = "password";
    String PROTOCOL = "protocol";
    String HOST = "host";
    String PORT = "port";
    String PATH = "path";

    String PROTOCOL_DEFAULT_VALUE = "http";
    String HOST_DEFAULT_VALUE = "";
    String PORT_DEFAULT_VALUE = "8080";
    String PATH_DEFAULT_VALUE = "/nuxeo";
    String USERNAME_DEFAULT_VALUE = "";
    String PASSWORD_DEFAULT_VALUE = "";

  }

  public interface Specification {

    String DOMAINS = "domains";
    String DOMAIN = "domain";
    String DOMAIN_KEY = "key";
    String DOCUMENTS = "documents";
    String PROCESS_TAGS = "process_tags";
    String PROCESS_ATTACHMENTS = "process_attachments";
    String DOCUMENTS_TYPE = "documentsType";
    String DOCUMENT_TYPE = "documentType";
    String DOCUMENT_TYPE_KEY = "key";

  }
}
