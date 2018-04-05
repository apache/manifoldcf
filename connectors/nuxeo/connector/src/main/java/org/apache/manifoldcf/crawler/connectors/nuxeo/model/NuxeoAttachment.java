/**
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.manifoldcf.crawler.connectors.nuxeo.model;

import java.io.InputStream;

/**
 * @author David Arroyo Escobar <arroyoescobardavid@gmail.com>
 *
 */
public class NuxeoAttachment {

  public static final String ATT_KEY_FILES = "files:files";
  public static final String ATT_KEY_FILE = "file";
  
  public static final String ATT_KEY_NAME = "name";
  public static final String ATT_KEY_ENCODING = "encoding";
  public static final String ATT_KEY_DIGEST = "digest";
  public static final String ATT_KEY_DIGEST_ALGORITHM = "digestAlgorithm";
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