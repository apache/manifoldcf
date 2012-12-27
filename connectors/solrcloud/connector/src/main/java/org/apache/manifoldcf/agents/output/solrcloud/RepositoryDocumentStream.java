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
package org.apache.manifoldcf.agents.output.solrcloud;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;

import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.solr.common.util.ContentStreamBase;

/**
 * @author minoru
 *
 */
public class RepositoryDocumentStream extends ContentStreamBase {
  private final RepositoryDocument document;
  private byte[] binary;

  /**
   * @param document
   */
  public RepositoryDocumentStream( RepositoryDocument document ) {
    this.document = document;
    this.contentType = null;
    this.name = this.document.getFileName();
    this.size = this.document.getBinaryLength();
    this.binary = this.getBytes(this.document.getBinaryStream());
    this.sourceInfo = "repository document";
  }

  /* (non-Javadoc)
   * @see org.apache.solr.common.util.ContentStream#getStream()
   */
  @Override
  public InputStream getStream() throws IOException {
    return new ByteArrayInputStream(this.binary);
  }

  /* (non-Javadoc)
   * @see org.apache.solr.common.util.ContentStreamBase#getReader()
   */
  @Override
  public Reader getReader() throws IOException {
    String charset = getCharsetFromContentType( this.getContentType() );
    return charset == null ? new InputStreamReader( this.getStream() ) : new InputStreamReader( this.getStream(), charset );
  }

  /* (non-Javadoc)
   * @see org.apache.solr.common.util.ContentStreamBase#getContentType()
   */
  @Override
  public String getContentType() {
    if(contentType==null) {
      InputStream stream = null;
      try {
        stream = this.getStream();
        char first = (char)stream.read();
        if(first == '<') {
          contentType = "application/xml";
        } else if(first == '{') {
          contentType = "application/json";
        } else {
          contentType = "application/octet-stream";
        }
      } catch(Exception ex) {
      } finally {
        if (stream != null) try {
          stream.close();
        } catch (IOException ioe) {}
      }
    }
    return contentType;
  }

  /**
   * @param is
   * @return
   */
  private byte[] getBytes(InputStream is) {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    OutputStream os = new BufferedOutputStream(b);
    int c;
    try {
      while ((c = is.read()) != -1) {
        os.write(c);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (os != null) {
        try {
          os.flush();
          os.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    return b.toByteArray();
  }
}
