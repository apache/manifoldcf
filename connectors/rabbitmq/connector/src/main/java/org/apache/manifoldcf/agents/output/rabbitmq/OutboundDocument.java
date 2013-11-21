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

package org.apache.manifoldcf.agents.output.rabbitmq;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.core.common.Base64;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class OutboundDocument {
	protected static final String allowAttributeName = "allow_token_";
	protected static final String denyAttributeName = "deny_token_";
	protected static final String noSecurityToken = "__nosecurity__";
	protected static final boolean useNullValue = false;
	private RepositoryDocument document;
	private InputStream inputStream;
	private String documentURI;
        public static enum Operation { ADD, UPDATE, DELETE };
        Operation operation = Operation.ADD;

	public OutboundDocument(RepositoryDocument document) {
		this.document = document;
		this.inputStream = document.getBinaryStream();
	}

	public OutboundDocument() {
	}

	public RepositoryDocument getDocument() {
		return this.document;
	}

	public String getDocumentURI() {
		return this.documentURI;
	}

        public void setOperation(Operation operation) {
            this.operation = operation;
        }
        
	public String writeTo(Writer out) throws JSONException, IOException,
			ManifoldCFException {
		JSONObject json = new JSONObject();

		json.put("documentUri", this.documentURI);
		json.put("acl", this.document.getACL());
		json.put("acl_deny", this.document.getDenyACL());
		json.put("acl_share", this.document.getShareACL());
		json.put("acl_share_deny", this.document.getShareDenyACL());
                
                

		JSONObject fields = new JSONObject();
		json.put("fields", fields);
		Iterator i = this.document.getFields();
		while (i.hasNext()) {
			String fieldName = (String) i.next();
			String[] fieldValues = this.document.getFieldAsStrings(fieldName);
			fields.put(fieldName, fieldValues);
		}

		Base64 base64 = new Base64();
		StringWriter outputWriter = new StringWriter();
		base64.encodeStream(this.inputStream, outputWriter);

		JSONObject file = new JSONObject();
		file.put("content", outputWriter.toString());
		file.put("name", this.document.getFileName());
                
		outputWriter.close();
		json.put("file", file);
                json.write(out);
                
                return json.toString();
                
	}

	public void loadString(String content) throws JSONException,
			ManifoldCFException, IOException {
		JSONObject json = new JSONObject(content);

		this.document = new RepositoryDocument();
		if(json.has("file")) {
			JSONObject file = json.getJSONObject("file");
			this.document.setFileName(file.getString("name"));
			Base64 base64 = new Base64();
			String fileContentB64 = file.getString("content");
			byte[] bytes = base64.decodeString(fileContentB64);
			InputStream is = decompress(new ByteArrayInputStream(bytes));
			this.inputStream = is;
			int fileBytes = bytes.length;
			this.document.setBinary(is, fileBytes);
		}
        else {
            this.document.setBinary(new ByteArrayInputStream(new byte[0]), 0);
        }

		this.documentURI = json.getString("documentUri");

        if(json.has("fields")) {
            JSONObject fields = json.getJSONObject("fields");
            Iterator i = fields.keys();
            while (i.hasNext()) {
                String fieldName = (String) i.next();
                JSONArray fieldValues = fields.getJSONArray(fieldName);
                String[] values = toArray(fieldValues);
                this.document.addField(fieldName, values);
            }
        }

		if(json.has("acl"))
			this.document.setACL(toArray(json.getJSONArray("acl")));
		if(json.has("acl_deny"))
				this.document.setDenyACL(toArray(json.getJSONArray("acl_deny")));
		if(json.has("acl_share"))
				this.document.setShareACL(toArray(json.getJSONArray("acl_share")));
		if(json.has("acl_share_deny"))
				this.document.setShareDenyACL(toArray(json
				.getJSONArray("acl_share_deny")));
	}

	private String[] toArray(JSONArray array) throws JSONException {
		if (array == null) {
			return null;
		}
		String[] values = new String[array.length()];
		for (int j = 0; j < array.length(); j++) {
			values[j] = array.getString(j);
		}
		return values;
	}

	private static InputStream compress(InputStream inputStream)
			throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DeflaterOutputStream compressor = new DeflaterOutputStream(out);
		long byteCount = 0L;
		byte[] buf = new byte[1024];
		int read;
		while ((read = inputStream.read(buf)) != -1) {
			compressor.write(buf, 0, read);
			byteCount += read;
		}
		inputStream.close();
		out.close();
		compressor.close();
		byte[] outBytes = out.toByteArray();
		return new ByteArrayInputStream(outBytes);
	}

	private static InputStream decompress(InputStream inputStream)
			throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		InflaterOutputStream compressor = new InflaterOutputStream(out);
		long byteCount = 0L;
		byte[] buf = new byte[1024];
		int read;
		while ((read = inputStream.read(buf)) != -1) {
			compressor.write(buf, 0, read);
			byteCount += read;
		}
		inputStream.close();
		out.close();
		compressor.close();
		byte[] outBytes = out.toByteArray();
		return new ByteArrayInputStream(outBytes);
	}
}
