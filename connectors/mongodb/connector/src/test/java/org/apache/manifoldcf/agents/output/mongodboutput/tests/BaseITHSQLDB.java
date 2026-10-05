/* $Id: BaseITHSQLDB.java 1800083 2017-06-27 19:55:21Z piergiorgio $ */

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.manifoldcf.agents.output.mongodboutput.tests;

import org.junit.After;
import org.junit.Before;

import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.ImmutableMongod;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.os.CommonArchitecture;
import de.flapdoodle.os.CommonOS;
import de.flapdoodle.os.ImmutablePlatform;
import de.flapdoodle.os.Platform;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.transitions.Start;

/**
 * Base integration tests class for MongoDB tested against a CMIS repository
 *
 * @author Irindu Nugawela
 */
public class BaseITHSQLDB extends org.apache.manifoldcf.crawler.tests.BaseITHSQLDB {

	private TransitionWalker.ReachedState<RunningMongodProcess> mongodProcess;

	protected String[] getConnectorNames() {
		return new String[] { "CMIS" };
	}

	protected String[] getConnectorClasses() {
		return new String[] { "org.apache.manifoldcf.crawler.tests.TestingRepositoryConnector" };
	}

	protected String[] getOutputNames() {
		return new String[] { "MongoDB" };
	}

	protected String[] getOutputClasses() {
		return new String[] { "org.apache.manifoldcf.agents.output.mongodboutput.MongodbOutputConnector" };
	}

	// Setup/teardown

	@Before
	public void setUpMongoDB() throws Exception {

		String bindIp = "localhost";
		int port = 27017;
		ImmutableMongod.Builder mongodBuilder = Mongod.builder()
				.net(Start.to(Net.class).initializedWith(Net.of(bindIp, port, false)));

		Platform detected = Platform.detect(CommonOS.list());
		if (detected.operatingSystem() == CommonOS.OS_X && detected.architecture() == CommonArchitecture.ARM_64) {
			Platform x86Platform = ImmutablePlatform.builder().from(detected)
					.architecture(CommonArchitecture.X86_64)
					.build();
			mongodBuilder.platform(Start.to(Platform.class).initializedWith(x86Platform));
		}

		mongodProcess = mongodBuilder.build().start(Version.Main.V4_0);
	}

	@After
	public void cleanUpMongoDB() throws Exception {
		if (mongodProcess != null) {
			mongodProcess.close();
		}
	}

}
