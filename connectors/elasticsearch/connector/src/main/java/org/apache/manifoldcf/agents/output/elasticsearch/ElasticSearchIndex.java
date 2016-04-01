/* $Id: ElasticSearchIndex.java 1299512 2012-03-12 00:58:38Z piergiorgio $ */

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

package org.apache.manifoldcf.agents.output.elasticsearch;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.Header;
import org.apache.commons.io.IOUtils;
import org.apache.manifoldcf.agents.interfaces.IOutputHistoryActivity;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.output.elasticsearch.ElasticSearchConnection.Result;
import org.apache.manifoldcf.core.common.Base64;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.util.URLEncoder;
import org.apache.manifoldcf.crawler.system.Logging;

public class ElasticSearchIndex extends ElasticSearchConnection
{

  /** The allow attribute name */
  protected final static String allowAttributeName = "allow_token_";
  /** The deny attribute name */
  protected final static String denyAttributeName = "deny_token_";
  /** The no-security token */
  protected final static String noSecurityToken = "__nosecurity__";
  
  /** Flag set as to whether null_value works in ES.  Right now it doesn't work,
  * so we have to do everything in the connector. */
  protected final static boolean useNullValue = false;
  
  private class IndexRequestEntity implements HttpEntity
  {

    private final RepositoryDocument document;
    private final InputStream inputStream;
    private final String[] acls;
    private final String[] denyAcls;
    private final String[] shareAcls;
    private final String[] shareDenyAcls;
    private final String[] parentAcls;
    private final String[] parentDenyAcls;
    private final boolean useMapperAttachments;
    private final String contentAttributeName;

    public IndexRequestEntity(RepositoryDocument document, InputStream inputStream,
      String[] acls, String[] denyAcls, String[] shareAcls, String[] shareDenyAcls, String[] parentAcls, String[] parentDenyAcls,
      boolean useMapperAttachments, String contentAttributeName)
      throws ManifoldCFException
    {
      this.document = document;
      this.inputStream = inputStream;
      this.acls = acls;
      this.denyAcls = denyAcls;
      this.shareAcls = shareAcls;
      this.shareDenyAcls = shareDenyAcls;
      this.parentAcls = parentAcls;
      this.parentDenyAcls = parentDenyAcls;
      this.useMapperAttachments = useMapperAttachments;
      this.contentAttributeName = contentAttributeName;
    }

    @Override
    public boolean isChunked() {
      return false;
    }
    
    @Override
    @Deprecated
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
      PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
      try
      {
        pw.print("{");
        Iterator<String> i = document.getFields();
        boolean needComma = false;
        while (i.hasNext()){
          String fieldName = i.next();
          String[] fieldValues = document.getFieldAsStrings(fieldName);
          needComma = writeField(pw, needComma, fieldName, fieldValues);
        }

        needComma = writeACLs(pw, needComma, "document", acls, denyAcls);
        needComma = writeACLs(pw, needComma, "share", shareAcls, shareDenyAcls);
        needComma = writeACLs(pw, needComma, "parent", parentAcls, parentDenyAcls);

        if (useMapperAttachments && inputStream != null) {
          if(needComma){
            pw.print(",");
          }
          // I'm told this is not necessary: see CONNECTORS-690
          //pw.print("\"type\" : \"attachment\",");
          pw.print("\"file\" : {");
          String contentType = document.getMimeType();
          if (contentType != null)
            pw.print("\"_content_type\" : "+jsonStringEscape(contentType)+",");
          String fileName = document.getFileName();
          if (fileName != null)
            pw.print("\"_name\" : "+jsonStringEscape(fileName)+",");
          // Since ES 1.0
          pw.print(" \"_content\" : \"");
          Base64 base64 = new Base64();
          base64.encodeStream(inputStream, pw);
          pw.print("\"}");
        }
        
        if (!useMapperAttachments && inputStream != null) {
          if (contentAttributeName != null)
          {
            Reader r = new InputStreamReader(inputStream, Consts.UTF_8);
            StringBuilder sb = new StringBuilder((int)document.getBinaryLength());
            char[] buffer = new char[65536];
            while (true)
            {
              int amt = r.read(buffer,0,buffer.length);
              if (amt == -1)
                break;
              sb.append(buffer,0,amt);
            }
            needComma = writeField(pw, needComma, contentAttributeName, new String[]{sb.toString()});
          }
        }
        
        pw.print("}");
      } catch (ManifoldCFException e)
      {
        throw new IOException(e.getMessage());
      } finally
      {
        pw.flush();
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
      return new BasicHeader("Content-type","application/x-www-form-urlencoded");
    }

    @Override
    public Header getContentEncoding() {
      return null;
    }

  }

  protected static boolean writeField(PrintWriter pw, boolean needComma,
    String fieldName, String[] fieldValues)
    throws IOException
  {
    if (fieldValues == null)
      return needComma;

    if (fieldValues.length == 1){
      if (needComma)
        pw.print(",");
      pw.print(jsonStringEscape(fieldName)+" : "+jsonStringEscape(fieldValues[0]));
      needComma = true;
      return needComma;
    }

    if (fieldValues.length > 1){
      if (needComma)
        pw.print(",");
      StringBuilder sb = new StringBuilder();
      sb.append("[");
      for(int j=0; j<fieldValues.length; j++){
        sb.append(jsonStringEscape(fieldValues[j])).append(",");
      }
      sb.setLength(sb.length() - 1); // discard last ","
      sb.append("]");
      pw.print(jsonStringEscape(fieldName)+" : "+sb.toString());
      needComma = true;
    }
    return needComma;
  }
  
  /** Output an acl level */
  protected static boolean writeACLs(PrintWriter pw, boolean needComma,
    String aclType, String[] acl, String[] denyAcl)
    throws IOException
  {
    String metadataACLName = allowAttributeName + aclType;
    if (acl != null && acl.length > 0)
      needComma = writeField(pw,needComma,metadataACLName,acl);
    else if (!useNullValue)
      needComma = writeField(pw,needComma,metadataACLName,new String[]{noSecurityToken});
    String metadataDenyACLName = denyAttributeName + aclType;
    if (denyAcl != null && denyAcl.length > 0)
      needComma = writeField(pw,needComma,metadataDenyACLName,denyAcl);
    else if (!useNullValue)
      needComma = writeField(pw,needComma,metadataDenyACLName,new String[]{noSecurityToken});
    return needComma;
  }

  protected static String jsonStringEscape(String value)
  {
    StringBuilder sb = new StringBuilder("\"");
    for (int i = 0; i < value.length(); i++)
    {
      char x = value.charAt(i);
      if (x == '\n')
        sb.append('\\').append('n');
      else if (x == '\r')
        sb.append('\\').append('r');
      else if (x == '\t')
        sb.append('\\').append('t');
      else if (x == '\b')
        sb.append('\\').append('b');
      else if (x == '\f')
        sb.append('\\').append('f');
      else
      {
        if (x == '\"' || x == '\\' || x == '/')
          sb.append('\\');
        sb.append(x);
      }
    }
    sb.append("\"");
    return sb.toString();
  }
  

  public ElasticSearchIndex(HttpClient client, ElasticSearchConfig config)
  {
    super(config, client);
  }
  
  /** Do the indexing.
  *@return false to indicate that the document was rejected.
  */
  public boolean execute(String documentURI, RepositoryDocument document, 
    InputStream inputStream,
    String[] acls, String[] denyAcls, String[] shareAcls, String[] shareDenyAcls, String[] parentAcls, String[] parentDenyAcls)
    throws ManifoldCFException, ServiceInterruption
  {
    String idField;

    idField = URLEncoder.encode(documentURI);

    StringBuffer url = getApiUrl(config.getIndexType() + "/" + idField, false);
    HttpPut put = new HttpPut(url.toString());
    put.setEntity(new IndexRequestEntity(document, inputStream,
      acls, denyAcls, shareAcls, shareDenyAcls, parentAcls, parentDenyAcls,
      config.getUseMapperAttachments(), config.getContentAttributeName()));
    if (call(put) == false)
      return false;
    String error = checkJson(jsonException);
    if (getResult() == Result.OK && error == null)
      return true;
    setResult("JSONERROR",Result.ERROR, error);
    Logging.connectors.warn("ES: Index failed: "+getResponse());
    return true;
  }

}
