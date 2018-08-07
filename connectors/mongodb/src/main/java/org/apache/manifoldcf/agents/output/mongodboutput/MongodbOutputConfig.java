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
package org.apache.manifoldcf.agents.output.mongodboutput;

/**
 * Parameters data for the Mongodb output connector.
 */
public class MongodbOutputConfig {

    /** Username */
    public static final String USERNAME_PARAM = "username";

    /** Password */
    public static final String PASSWORD_PARAM = "password";

    /** Server name */
    public static final String HOST_PARAM = "host";

    /** Port */
    public static final String PORT_PARAM = "port";

    /** Database */
    public static final String DATABASE_PARAM = "database";

    /** Collection */
    public static final String COLLECTION_PARAM = "collection";

    //default values
    public static final String HOST_DEFAULT_VALUE = "localhost";
    public static final String PORT_DEFAULT_VALUE = "27017";
    public static final String DATABASE_DEFAULT_VALUE = "database";
    public static final String COLLECTION_DEFAULT_VALUE = "collection";

}