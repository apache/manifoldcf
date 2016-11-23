/* $Id: DefaultAuthenticator.java 1688076 2015-06-28 23:04:30Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.nuxeo.tests;

import org.apache.manifoldcf.crawler.connectors.nuxeo.NuxeoRepositoryConnector;
import org.apache.manifoldcf.crawler.connectors.nuxeo.client.NuxeoClient;
import org.junit.After;
import org.junit.Before;
import org.mockito.Mock;

/**
 * @author David Arroyo Escobar <arroyoescobardavid@gmail.com>
 *
 */
public abstract class AbstractTestBase {

  @Mock
  public NuxeoClient client;

  public NuxeoRepositoryConnector repositoryConnector;

  @Before
  public void setup() throws Exception {
    repositoryConnector = new NuxeoRepositoryConnector();
    repositoryConnector.setNuxeoClient(client);
  }

  @After
  public void tearDown() throws Exception {

  }

}
