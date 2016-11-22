package org.apache.manifoldcf.crawler.connectors.nuxeo.exception;

public class NuxeoException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7504820888917970500L;

	public NuxeoException(String message) {
		super(message);
	}

	public NuxeoException(String message, Throwable throwable) {
		super(message, throwable);
	}
}
