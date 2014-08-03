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
import java.io.Reader;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.HttpEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.Header;
import org.apache.http.util.EntityUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.core.common.Base64;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;

public class OpenSearchServerIndex extends OpenSearchServerConnection {

  private static class IndexRequestEntity implements HttpEntity {

    private final String documentURI;

    private final RepositoryDocument document;

    private final String fileName;

    public IndexRequestEntity(String documentURI, RepositoryDocument document) {
      this.documentURI = documentURI;
      this.document = document;
      this.fileName = FilenameUtils.getName(documentURI);
    }

    @Override
    public boolean isChunked() {
      return false;
    }

    @Override
    @Deprecated
    public void consumeContent() throws IOException {
      EntityUtils.consume(this);
    }

    @Override
    public boolean isRepeatable() {
      return false;
    }

    @Override
    public boolean isStreaming() {
      return true;
    }

    @Override
    public InputStream getContent() throws IOException, IllegalStateException {
      return null;
    }

    @Override
    public void writeTo(OutputStream out)
      throws IOException {
      PrintWriter pw = new PrintWriter(out);
      boolean bUri = false;
      try
      {
        pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
        pw.println("<index>);");
        pw.print("<document>");
        List<String> values = new ArrayList<>(1);
        Iterator<String> iter = document.getFields();
        if (iter != null)
        {
          while (iter.hasNext())
          {
            String fieldName = iter.next();
            Object[] fieldValues = document.getField(fieldName);
            if (fieldValues != null && fieldValues.length > 0)
            {
            	values.clear();
                for (Object fieldValue : fieldValues)
                  if (fieldValue != null)
                    values.add(fieldValue.toString());
                if (!values.isEmpty())
                {
                  if ("uri".equals(fieldName))
                	  bUri = true;
                  pw.print("<field name=\"");
                  pw.print(StringEscapeUtils.escapeXml(fieldName));
                  pw.print("\">");
                  for (String value : values)
                  {
            	    pw.print("<value><![CDATA[");
            	    pw.print(value);
                    pw.print("]]></value>");
                    pw.println("</field>");
                  }
               }
             }
           }
        }
        if (!bUri)
        {
            pw.print("<document><field name=\"uri\"><value>");
            pw.print(documentURI);        	
            pw.println("</value>");
        }
        if (document.getBinaryLength() > 0)
        {
          Base64 base64 = new Base64();
          pw.print("<binary fileName=\"");
          pw.print(fileName);
          pw.println("\">");
          base64.encodeStream(document.getBinaryStream(), pw);
          pw.println("</binary>");
        }
        pw.println("</document>");
        pw.println("</index>");
      }
      catch (ManifoldCFException e)
      {
        throw new IOException(e.getMessage(), e);
      }
      finally
      {
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
      return new BasicHeader("Content-type", "text/xml; charset=utf-8");
    }

    @Override
    public Header getContentEncoding() {
      return null;
    }

	}

  public OpenSearchServerIndex(HttpClient client, String documentURI,
    OpenSearchServerConfig config, RepositoryDocument document)
    throws ManifoldCFException {
    super(client, config);
    StringBuffer url = getApiUrl("update");
    HttpPut put = new HttpPut(url.toString());
    put.setEntity(new IndexRequestEntity(documentURI, document));
    call(put);
    if ("OK".equals(checkXPath(xPathStatus)))
      return;
    String error = checkXPath(xPathException);
    setResult(Result.ERROR, error);
    throw new ManifoldCFException("Error, unexpected response: " + error);
  }

}
