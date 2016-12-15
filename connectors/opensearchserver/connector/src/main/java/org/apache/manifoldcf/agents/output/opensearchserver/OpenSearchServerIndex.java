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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputHistoryActivity;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.core.common.Base64;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;

public class OpenSearchServerIndex extends OpenSearchServerConnection
{

  private static class Acls
  {
    private String[] acls = null;
    private String[] denyAcls = null;
    private String[] shareAcls = null;
    private String[] shareDenyAcls = null;

    private void setDocument(String[] acls, String[] denyAcls)
    {
      this.acls = acls;
      this.denyAcls = denyAcls;
    }

    private void setShare(String[] acls, String[] denyAcls)
    {
      this.shareAcls = acls;
      this.shareDenyAcls = denyAcls;
    }
  }

  private static class IndexRequestEntity implements HttpEntity
  {

    private final String documentURI;
    private final RepositoryDocument document;
    private final String fileName;
    private final Acls acls;

    public IndexRequestEntity(String documentURI, RepositoryDocument document,
        Acls acls)
    {
      this.documentURI = documentURI;
      this.document = document;
      this.fileName = FilenameUtils.getName(documentURI);
      this.acls = acls;
    }

    @Override
    public boolean isChunked()
    {
      return false;
    }

    @Override
    @Deprecated
    public void consumeContent()
        throws IOException
    {
      EntityUtils.consume(this);
    }

    @Override
    public boolean isRepeatable()
    {
      return false;
    }

    @Override
    public boolean isStreaming()
    {
      return true;
    }

    @Override
    public InputStream getContent()
        throws IOException, IllegalStateException
    {
      return null;
    }

    private static final void writeFieldCdata(String fieldName,
        Collection<String> values, PrintWriter pw)
    {
      if (CollectionUtils.isEmpty(values))
        return;
      pw.print("<field name=\"");
      pw.print(StringEscapeUtils.escapeXml(fieldName));
      pw.print("\">");
      for (String value : values)
      {
        pw.print("<value><![CDATA[");
        pw.print(value);
        pw.print("]]></value>");
      }
      pw.println("</field>");
    }

    private static final void writeField(String fieldName, String value,
        PrintWriter pw)
    {
      if (StringUtils.isEmpty(value))
        return;
      pw.print("<field name=\"");
      pw.print(fieldName);
      pw.print("\"><value>");
      pw.print(value);
      pw.println("</value></field>");
    }

    private static final void writeFieldValues(String fieldName,
        String[] values, PrintWriter pw)
    {
      if (values == null || values.length == 0)
        return;
      pw.print("<field name=\"");
      pw.print(fieldName);
      pw.println("\">");
      for (String value : values)
      {
        pw.print("<value>");
        pw.print(value);
        pw.println("</value>");
      }
      pw.println("</field>");
    }

    @Override
    public void writeTo(OutputStream out)
        throws IOException
    {
      PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), false);
      try
      {
        pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
        pw.println("<index>);");
        pw.print("<document>");
        List<String> values = new ArrayList<String>(1);
        Iterator<String> iter = document.getFields();
        if (iter != null)
        {
          while (iter.hasNext())
          {
            String fieldName = iter.next();
            if ("uri".equals(fieldName))
              continue;
            Object[] fieldValues = document.getField(fieldName);
            if (fieldValues != null && fieldValues.length > 0)
            {
              values.clear();
              for (Object fieldValue : fieldValues)
                if (fieldValue != null)
                  values.add(fieldValue.toString());
              writeFieldCdata(fieldName, values, pw);
            }
          }
        }
        writeField("uri", documentURI, pw);
        if (document.getBinaryLength() > 0)
        {
          Base64 base64 = new Base64();
          pw.print("<binary fileName=\"");
          pw.print(fileName);
          pw.println("\">");
          base64.encodeStream(document.getBinaryStream(), pw);
          pw.println("</binary>");
        }
        if (acls != null)
        {
          writeFieldValues("userAllow", acls.acls, pw);
          writeFieldValues("userDeny", acls.denyAcls, pw);
          writeFieldValues("groupAllow", acls.shareAcls, pw);
          writeFieldValues("groupDeny", acls.shareDenyAcls, pw);
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
    public long getContentLength()
    {
      // Unknown (chunked) length
      return -1L;
    }

    @Override
    public Header getContentType()
    {
      return new BasicHeader("Content-type", "text/xml; charset=utf-8");
    }

    @Override
    public Header getContentEncoding()
    {
      return null;
    }
  }

  /**
   * Convert an unqualified ACL to qualified form.
   * 
   * @param acl
   *          is the initial, unqualified ACL.
   * @param authorityNameString
   *          is the name of the governing authority for this document's acls,
   *          or null if none.
   * @param activities
   *          is the activities object, so we can report what's happening.
   * @return the modified ACL.
   */
  protected static String[] convertACL(String[] acl,
      String authorityNameString, IOutputAddActivity activities)
      throws ManifoldCFException
  {
    if (acl != null)
    {
      String[] rval = new String[acl.length];
      int i = 0;
      while (i < rval.length)
      {
        rval[i] = activities.qualifyAccessToken(authorityNameString, acl[i]);
        i++;
      }
      return rval;
    }
    return new String[0];
  }

  public OpenSearchServerIndex(HttpClient client, String documentURI,
      OpenSearchServerConfig config, RepositoryDocument document,
      String authorityNameString, IOutputAddActivity activities)
      throws ManifoldCFException
  {
    super(client, config);

    Acls acls = new Acls();
    Iterator<String> a = document.securityTypesIterator();
    if (a != null)
    {
      while (a.hasNext())
      {
        String securityType = a.next();
        String[] convertedAcls = convertACL(
            document.getSecurityACL(securityType), authorityNameString,
            activities);
        String[] convertedDenyAcls = convertACL(
            document.getSecurityDenyACL(securityType), authorityNameString,
            activities);
        if (securityType.equals(RepositoryDocument.SECURITY_TYPE_DOCUMENT))
        {
          acls.setDocument(convertedAcls, convertedDenyAcls);
        }
        else if (securityType.equals(RepositoryDocument.SECURITY_TYPE_SHARE))
        {
          acls.setShare(convertedAcls, convertedDenyAcls);
        }
        else
        {
          // Don't know how to deal with it
          setResult(activities.UNKNOWN_SECURITY,Result.ERROR, "Unhandled security type: " + securityType);
          return;
        }
      }
    }

    StringBuffer url = getApiUrl("update");
    HttpPut put = new HttpPut(url.toString());
    put.setEntity(new IndexRequestEntity(documentURI, document, acls));
    call(put);
    if ("OK".equals(checkXPath(xPathStatus)))
      return;
    String error = checkXPath(xPathException);
    setResult("XPATHEXCEPTION",Result.ERROR, error);
    throw new ManifoldCFException("Error, unexpected response: " + error);
  }

}
