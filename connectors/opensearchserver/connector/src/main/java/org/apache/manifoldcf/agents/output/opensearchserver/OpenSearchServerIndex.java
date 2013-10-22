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

package org.apache.manifoldcf.agents.output.opensearchserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.HttpEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.Header;
import org.apache.http.util.EntityUtils;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.manifoldcf.core.common.Base64;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;

public class OpenSearchServerIndex extends OpenSearchServerConnection {

  private static class IndexRequestEntity implements HttpEntity {

    private String documentURI;

    private InputStream inputStream;

    private String fileName;

    public IndexRequestEntity(String documentURI, InputStream inputStream) {
      this.documentURI = documentURI;
      this.inputStream = inputStream;
      this.fileName = FilenameUtils.getName(documentURI);
    }

    @Override
    public boolean isChunked() {
      return false;
    }
    
    @Override
    public void consumeContent()
      throws IOException {
      EntityUtils.consume(this);
    }
    
    @Override
    public boolean isRepeatable() {
      return false;
    }

    @Override
    public boolean isStreaming() {
      return false;
    }
    
    @Override
    public InputStream getContent()
      throws IOException, IllegalStateException {
      return inputStream;
    }
    
    @Override
    public void writeTo(OutputStream out)
      throws IOException {
      PrintWriter pw = new PrintWriter(out);
      try {
        pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
        pw.println("<index>);");
        pw.print("<document><field name=\"uri\"><value>");
        pw.print(documentURI);
        pw.println("</value></field>");
        pw.print("<binary fileName=\"");
        pw.print(fileName);
        pw.println("\">");
        Base64 base64 = new Base64();
        base64.encodeStream(inputStream, pw);
        pw.println("</binary></document>");
        pw.println("</index>");
      } catch (ManifoldCFException e) {
        throw new IOException(e.getMessage());
      } finally {
        IOUtils.closeQuietly(pw);
      }
    }

    @Override
    public long getContentLength() {
      // Unknown (chunked) length
      return -1L;
    }

    @Override
    public Header getContentType() {
      return new BasicHeader("Content-type","text/xml; charset=utf-8");
    }

    @Override
    public Header getContentEncoding() {
      return null;
    }

  }

  public OpenSearchServerIndex(HttpClient client, String documentURI, InputStream inputStream,
      OpenSearchServerConfig config) throws ManifoldCFException {
    super(client, config);
    StringBuffer url = getApiUrl("update");
    HttpPut put = new HttpPut(url.toString());
    put.setEntity(new IndexRequestEntity(documentURI, inputStream));
    call(put);
    if ("OK".equals(checkXPath(xPathStatus)))
      return;
    String error = checkXPath(xPathException);
    setResult(Result.ERROR, error);
    System.err.println(getResponse());
  }

}
