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
