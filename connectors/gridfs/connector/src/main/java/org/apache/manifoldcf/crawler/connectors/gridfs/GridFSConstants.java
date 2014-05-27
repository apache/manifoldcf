/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.manifoldcf.crawler.connectors.gridfs;

/**
 *
 * @author molgun
 */
public class GridFSConstants {

    /**
     * MongoDB username parameter to get value from the user interface.
     */
    protected static final String USERNAME_PARAM = "username";
    /**
     * MongoDB password parameter to get value from the user interface.
     */
    protected static final String PASSWORD_PARAM = "password";
    /**
     * MongoDB host parameter to get value from the user interface.
     */
    protected static final String HOST_PARAM = "host";
    /**
     * MongoDB port parameter to get value from the user interface.
     */
    protected static final String PORT_PARAM = "port";
    /**
     * MongoDB database parameter to get value from the user interface.
     */
    protected static final String DB_PARAM = "db";
    /**
     * MongoDB bucket parameter to get value from the user interface.
     */
    protected static final String BUCKET_PARAM = "bucket";
    /**
     * MongoDB url field name parameter to get value from the user interface.
     */
    protected static final String URL_RETURN_FIELD_NAME_PARAM = "url";
    /**
     * MongoDB acl field name parameter to get value from the user interface.
     */
    protected static final String ACL_RETURN_FIELD_NAME_PARAM = "acl";
    /**
     * MongoDB denyAcl field name parameter to get value from the user interface.
     */
    protected static final String DENY_ACL_RETURN_FIELD_NAME_PARAM = "denyAcl";
    /**
     * MongoDB default database name.
     */
    protected static final String DEFAULT_DB_NAME = "test";
    /**
     * MongoDB default bucket name.
     */
    protected static final String DEFAULT_BUCKET_NAME = "fs";
    /**
     * MongoDB default files subcollection name.
     */
    protected static final String FILES_COLLECTION_NAME = "files";
    /**
     * MongoDB md5 field name.
     */
    protected static final String MD5_FIELD_NAME = "md5";
    /**
     * MongoDB subcollection seperator.
     */
    protected static final String COLLECTION_SEPERATOR = ".";
    /**
     * MongoDB default unique key field name.
     */
    protected static final String DEFAULT_ID_FIELD_NAME = "_id";
    /**
     * Job start point node type.
     */
    protected static final String JOB_STARTPOINT_NODE_TYPE = "startpoint";
}
