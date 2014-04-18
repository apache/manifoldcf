package org.apache.manifoldcf.core.util;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Wrapper of {@link java.net.URLDecoder}
 * Intends to replace java.net.URLDecoder.decode(String s, "UTF-8")
 * avoiding {@link UnsupportedEncodingException} handling.
 * {@link StandardCharsets} are guaranteed to be available
 * on every implementation of the Java platform.
 *
 * @since 1.7
 */
public class URLDecoder {

  public static String decode(String s) {

    String str = null;

    try {
      str = java.net.URLDecoder.decode(s, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      // Can't happen. java.nio.charset.StandardCharsets are guaranteed
      // to be available on every implementation of the Java platform.
      throw new RuntimeException("UTF-8 not supported " + e.getMessage(), e);
    }

    return str;
  }
}
