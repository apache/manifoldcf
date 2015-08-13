package org.apache.manifoldcf.crawler.connectors.confluence.exception;

public class ConfluenceException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5903550079897330304L;

	public ConfluenceException(String message) {
		super(message);
	}
	
	public ConfluenceException(String message, Throwable cause) {
		super(message, cause);
	}
}
