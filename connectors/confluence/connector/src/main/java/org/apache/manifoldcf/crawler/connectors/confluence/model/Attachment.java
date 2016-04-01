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

package org.apache.manifoldcf.crawler.connectors.confluence.model;

import java.io.InputStream;
import java.util.Map;

import org.apache.manifoldcf.crawler.connectors.confluence.model.builder.ConfluenceResourceBuilder;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * <p>
 * Attachment class
 * </p>
 * <p>
 * Represents a Confluence Attachment
 * </p>
 * 
 * @author Antonio David Perez Morales <adperezmorales@gmail.com>
 */
public class Attachment extends Page {

  protected static final String KEY_DOWNLOAD = "download";
  protected static final String KEY_EXTENSIONS = "extensions";
  protected String downloadUrl;
  protected InputStream contentStream;

  public static ConfluenceResourceBuilder<Attachment> builder() {
    return new AttachmentBuilder();
  }

  public String getDownloadUrl() {
    return this.downloadUrl;
  }

  @Override
  public boolean hasContent() {
    return (this.length > 0 && this.hasContentStream()) || (this.downloadUrl != null && !this.downloadUrl.isEmpty());
  }

  public Boolean hasContentStream() {
    return this.contentStream != null;
  }

  @Override
  public InputStream getContentStream() {
    if(hasContentStream()) {
      return this.contentStream;
    }
    return super.getContentStream();
  }

  @Override
  protected void refineMetadata(Map<String, Object> metadata) {
    super.refineMetadata(metadata);
    metadata.put("downloadUrl", this.getBaseUrl() + this.getUrlContext()
        + downloadUrl);
  }

  /**
   * <p>
   * AttachmentBuilder internal class
   * </p>
   * <p>
   * Used to build Attachments
   * </p>
   * 
   * @author Antonio David Perez Morales <adperezmorales@gmail.com>
   *
   */
  public static class AttachmentBuilder implements ConfluenceResourceBuilder<Attachment>{
    
    @Override
    public Attachment fromJson(JSONObject jsonPage) {
      return fromJson(jsonPage, new Attachment());
    }

    @SuppressWarnings("unchecked")
    public Attachment fromJson(JSONObject jsonPage, Attachment attachment) {
      ((ConfluenceResourceBuilder<Page>) Page.builder()).fromJson(jsonPage, attachment);

      try {
        /*
         * Download URL
         */

        JSONObject links = (JSONObject) jsonPage.get(Page.KEY_LINKS);
        if (links != null) {
          attachment.downloadUrl = links.optString(KEY_DOWNLOAD, "");
        }

        /*
         * Extensions
         */
        JSONObject extensions = (JSONObject) jsonPage
            .get(KEY_EXTENSIONS);
        if (extensions != null) {
          attachment.mediaType = extensions.optString(
              Page.KEY_MEDIATYPE, "");
        }
      } catch (JSONException e) {
        e.printStackTrace();
      }

      return attachment;
    }

    @Override
    public Class<Attachment> getType() {
      return Attachment.class;
    }

  }
}
