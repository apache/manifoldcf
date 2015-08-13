package org.apache.manifoldcf.crawler.connectors.confluence;

/**
 * <p>
 * ConfluenceConfiguration class
 * </p>
 * <p>
 * Class used to keep configuration parameters for Confluence repository connection
 * </p>
 * 
 * @author Antonio David Perez Morales <adperezmorales@gmail.com>
 *
 */
public class ConfluenceConfiguration {

	public static interface Server {
		public static final String USERNAME = "username";
		public static final String PASSWORD = "password";
		public static final String PROTOCOL = "protocol";
		public static final String HOST = "host";
		public static final String PORT = "port";
		public static final String PATH = "path";
		
		public static final String PROTOCOL_DEFAULT_VALUE = "http";
		public static final String HOST_DEFAULT_VALUE = "";
		public static final String PORT_DEFAULT_VALUE = "8090";
		public static final String PATH_DEFAULT_VALUE = "/confluence";
		public static final String USERNAME_DEFAULT_VALUE = "";
		public static final String PASSWORD_DEFAULT_VALUE = "";
	}

	public static interface Specification {
		public static final String SPACES = "spaces";
		public static final String SPACE = "space";
		public static final String SPACE_KEY_ATTRIBUTE = "key";
		public static final String PAGES = "pages";
		public static final String PROCESS_ATTACHMENTS_ATTRIBUTE_KEY = "process_attachments";
		
	}
	
}
