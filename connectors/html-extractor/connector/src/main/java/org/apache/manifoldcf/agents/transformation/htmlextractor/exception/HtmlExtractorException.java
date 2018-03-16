package com.francelabs.datafari.htmlextractor.exception;


public class HtmlExtractorException extends Exception {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public HtmlExtractorException(final String message) {
    super(message);
  }

  public HtmlExtractorException(final String message, final Exception e) {
    super(message, e);
  }

}
