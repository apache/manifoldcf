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
package org.apache.lcf.agents.output.lucene;


/** Parameters and output data for MetaCarta Lucene output connector.
*/
public class LuceneConfig
{
        public static final String _rcsid = "@(#)$Id$";

        // Configuration parameters
    
        /** Protocol */
        public static final String PARAM_PROTOCOL = "Server protocol";
        /** Server name */
        public static final String PARAM_SERVER = "Server name";
        /** Port */
        public static final String PARAM_PORT = "Server port";
        /** Webapp */
        public static final String PARAM_WEBAPPNAME = "Server web application";
        /** Update path */
        public static final String PARAM_UPDATEPATH = "Server update handler";
        /** Remove path */
        public static final String PARAM_REMOVEPATH = "Server remove handler";
        /** Status path */
        public static final String PARAM_STATUSPATH = "Server status handler";
        /** Optional basic auth realm */
        public static final String PARAM_REALM = "Realm";
        /** Optional user ID */
        public static final String PARAM_USERID = "User ID";
        /** Optional user password */
        public static final String PARAM_PASSWORD = "Password";

        // Output specification
    
        // Nothing yet...
        
}
