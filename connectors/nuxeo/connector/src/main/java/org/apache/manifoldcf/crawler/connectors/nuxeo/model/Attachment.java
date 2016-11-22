/**
 * 
 */
package org.apache.manifoldcf.crawler.connectors.nuxeo.model;

/**
 * @author David Arroyo Escobar <arroyoescobardavid@gmail.com>
 *
 */
public class Attachment{

	public static final String ATT_KEY_FILE = "file";
	public static final String ATT_KEY_NAME = "name";
	public static final String ATT_KEY_MIME_TYPE = "mime-type";
	public static final String ATT_KEY_URL = "data";
	public static final String ATT_KEY_LENGTH = "length";
	
	protected String name;
	protected String mime_type;
	protected String url;
	protected long length;
	
	public String getName() {
		return name;
	}

	public String getMime_type() {
		return mime_type;
	}

	public String getUrl() {
		return url;
	}
	
	public long getLength() {
		return length;
	}

}