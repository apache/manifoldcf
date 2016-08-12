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

package org.apache.manifoldcf.agents.output.kafka;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import org.apache.commons.io.IOUtils;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import static org.apache.manifoldcf.agents.output.kafka.KafkaOutputConnector.allowAttributeName;
import static org.apache.manifoldcf.agents.output.kafka.KafkaOutputConnector.denyAttributeName;
import static org.apache.manifoldcf.agents.output.kafka.KafkaOutputConnector.noSecurityToken;
import static org.apache.manifoldcf.agents.output.kafka.KafkaOutputConnector.useNullValue;
import org.apache.manifoldcf.core.common.Base64;

/**
 *
 * @author tugba
 */
public class KafkaMessage {

  private final String[] acls = null;
  private final String[] denyAcls = null;
  private final String[] shareAcls = null;
  private final String[] shareDenyAcls = null;
  private final String[] parentAcls = null;
  private final String[] parentDenyAcls = null;
  private InputStream inputStream = null;

  public byte[] createJSON(RepositoryDocument document) {
    String finalString = null;
    // create temporaray byte array output stream
    OutputStream out = new ByteArrayOutputStream();
    try {
      inputStream = document.getBinaryStream();

      // print to our byte array output stream
      PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

      pw.print("{");
      Iterator<String> i = document.getFields();
      boolean needComma = false;
      while (i.hasNext()) {
        String fieldName = i.next();
        String[] fieldValues = document.getFieldAsStrings(fieldName);
        needComma = writeField(pw, needComma, fieldName, fieldValues);
      }

      needComma = writeACLs(pw, needComma, "document", acls, denyAcls);
      needComma = writeACLs(pw, needComma, "share", shareAcls, shareDenyAcls);
      needComma = writeACLs(pw, needComma, "parent", parentAcls, parentDenyAcls);

      if (inputStream != null) {
        if (needComma) {
          pw.print(",");
        }
        // I'm told this is not necessary: see CONNECTORS-690
        //pw.print("\"type\" : \"attachment\",");
        pw.print("\"file\" : {");
        String contentType = document.getMimeType();
        if (contentType != null) {
          pw.print("\"_content_type\" : " + jsonStringEscape(contentType) + ",");
        }
        String fileName = document.getFileName();
        if (fileName != null) {
          pw.print("\"_name\" : " + jsonStringEscape(fileName) + ",");
        }
        // Since ES 1.0
        pw.print(" \"_content\" : \"");
        Base64 base64 = new Base64();
        base64.encodeStream(inputStream, pw);
        pw.print("\"}");
      }
      pw.print("}");
      pw.flush();
      IOUtils.closeQuietly(pw);
      finalString = new String(((ByteArrayOutputStream) out).toByteArray(), StandardCharsets.UTF_8);
      //System.out.println("FINAL: " + finalString);
    } catch (Exception e) {
      e.printStackTrace();
      // throw new IOException(e.getMessage());
    }
    return ((ByteArrayOutputStream) out).toByteArray();
  }

  protected static boolean writeField(PrintWriter pw, boolean needComma,
          String fieldName, String[] fieldValues)
          throws IOException {
    if (fieldValues == null) {
      return needComma;
    }

    if (fieldValues.length == 1) {
      if (needComma) {
        pw.print(",");
      }
      pw.print(jsonStringEscape(fieldName) + " : " + jsonStringEscape(fieldValues[0]));
      needComma = true;
      return needComma;
    }

    if (fieldValues.length > 1) {
      if (needComma) {
        pw.print(",");
      }
      StringBuilder sb = new StringBuilder();
      sb.append("[");
      for (int j = 0; j < fieldValues.length; j++) {
        sb.append(jsonStringEscape(fieldValues[j])).append(",");
      }
      sb.setLength(sb.length() - 1); // discard last ","
      sb.append("]");
      pw.print(jsonStringEscape(fieldName) + " : " + sb.toString());
      needComma = true;
    }
    return needComma;
  }

  /**
   * Output an acl level
   */
  protected static boolean writeACLs(PrintWriter pw, boolean needComma,
          String aclType, String[] acl, String[] denyAcl)
          throws IOException {
    String metadataACLName = allowAttributeName + aclType;
    if (acl != null && acl.length > 0) {
      needComma = writeField(pw, needComma, metadataACLName, acl);
    } else if (!useNullValue) {
      needComma = writeField(pw, needComma, metadataACLName, new String[]{noSecurityToken});
    }
    String metadataDenyACLName = denyAttributeName + aclType;
    if (denyAcl != null && denyAcl.length > 0) {
      needComma = writeField(pw, needComma, metadataDenyACLName, denyAcl);
    } else if (!useNullValue) {
      needComma = writeField(pw, needComma, metadataDenyACLName, new String[]{noSecurityToken});
    }
    return needComma;
  }

  protected static String jsonStringEscape(String value) {
    StringBuilder sb = new StringBuilder("\"");
    for (int i = 0; i < value.length(); i++) {
      char x = value.charAt(i);
      if (x == '\n') {
        sb.append('\\').append('n');
      } else if (x == '\r') {
        sb.append('\\').append('r');
      } else if (x == '\t') {
        sb.append('\\').append('t');
      } else if (x == '\b') {
        sb.append('\\').append('b');
      } else if (x == '\f') {
        sb.append('\\').append('f');
      } else {
        if (x == '\"' || x == '\\' || x == '/') {
          sb.append('\\');
        }
        sb.append(x);
      }
    }
    sb.append("\"");
    return sb.toString();
  }
}
