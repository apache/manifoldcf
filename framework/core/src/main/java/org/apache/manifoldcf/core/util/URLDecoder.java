package org.apache.manifoldcf.core.util;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
