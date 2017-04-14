/**
 * 
 */
package org.apache.manifoldcf.crawler.connectors.nuxeo.model;

import java.io.InputStream;

/**
 * @author David Arroyo Escobar <arroyoescobardavid@gmail.com>
 *
 */
public class Attachment{

	public static final String ATT_KEY_FILES = "files:files";
	public static final String ATT_KEY_FILE = "file";
	
	public static final String ATT_KEY_NAME = "name";
	public static final String ATT_KEY_MIME_TYPE = "mime-type";
	public static final String ATT_KEY_ENCODING = "encoding";
	public static final String ATT_KEY_DIGEST = "digest";
	public static final String ATT_KEY_DIGEST_ALGORITHM = "digestAlgorithm";
	public static final String ATT_KEY_URL = "data";
	public static final String ATT_KEY_LENGTH = "length";
	
	//Properties
	protected String name;
	protected String mime_type;
	protected String url;
	protected String encoding;
	protected String digest;
	protected String digestAlgorithm;
	protected long length;
	protected InputStream data;
	
	//Getters
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

	public String getEncoding() {
		return encoding;
	}

	public String getDigest() {
		return digest;
	}

	public String getDigestAlgorithm() {
		return digestAlgorithm;
	}

	public InputStream getData() {
		return data;
	}

}