/**
 * 
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
public abstract class AbstractTest {

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
