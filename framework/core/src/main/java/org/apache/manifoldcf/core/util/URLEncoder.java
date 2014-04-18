package org.apache.manifoldcf.core.util;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Wrapper of {@link java.net.URLEncoder}
 * Intends to replace java.net.URLEncoder.encode(String s, "UTF-8")
 * avoiding {@link UnsupportedEncodingException} handling.
 * {@link StandardCharsets} are guaranteed to be available
 * on every implementation of the Java platform.
 *
 * @since 1.7
 */
public class URLEncoder {

  public static String encode(String s) {

    String str = null;

    try {
      str = java.net.URLEncoder.encode(s, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      // Can't happen. java.nio.charset.StandardCharsets are guaranteed
      // to be available on every implementation of the Java platform.
      throw new RuntimeException("UTF-8 not supported " + e.getMessage(), e);
    }

    return str;
  }
}
