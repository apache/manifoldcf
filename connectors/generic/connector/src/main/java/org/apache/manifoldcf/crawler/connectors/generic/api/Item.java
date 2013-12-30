/*
 * Copyright 2013 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.manifoldcf.crawler.connectors.generic.api;

import java.util.Date;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 *
 * @author krycek
 */
@XmlRootElement(name = "item")
public class Item {

  @XmlAttribute(name = "id", required = true)
  public String id;

  @XmlElement(name = "url", required = true)
  public String url;

  @XmlElement(name = "version", required = true)
  public String version;

  @XmlElement(name = "content")
  public String content;

  @XmlElement(name = "mimetype")
  public String mimeType;

  @XmlElement(name = "created")
  @XmlJavaTypeAdapter(value = DateAdapter.class)
  public Date created;

  @XmlElement(name = "updated")
  @XmlJavaTypeAdapter(value = DateAdapter.class)
  public Date updated;

  @XmlElement(name = "filename")
  public String fileName;

  @XmlElementWrapper(name = "metadata")
  @XmlElements(value = {
    @XmlElement(name = "meta", type = Meta.class)})
  public List<Meta> metadata;

  @XmlElementWrapper(name = "auth")
  @XmlElements(value = {
    @XmlElement(name = "token", type = String.class)})
  public List<String> auth;

  @XmlElementWrapper(name = "related")
  @XmlElements(value = {
    @XmlElement(name = "id", type = String.class)})
  public List<String> related;

  public String getVersionString() {
    if (version == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(version);
    if (auth != null) {
      for (String t : auth) {
        sb.append("|").append(t);
      }
    }
    return sb.toString();
  }
}
