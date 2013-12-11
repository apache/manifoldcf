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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Iterator;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.core.common.Base64;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
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

        public OutboundDocument(String documentUri) {
            this.documentURI = documentUri;
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
        
        
        // TODO: write to Logstash format, or support 
        // a range of inputs.
	public String writeTo(Writer out) throws JSONException, IOException,
			ManifoldCFException {
		JSONObject json = new JSONObject();

		json.put("documentUri", this.documentURI);
                json.put("operation", this.operation);
                
                if (operation != Operation.DELETE) {
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
                    // TODO: We can not, in general, assume we can 
                    // fit the entire stream in memory. 
                    base64.encodeStream(this.inputStream, outputWriter);

                    
                    JSONObject file = new JSONObject();
                    file.put("content", outputWriter.toString());
                    file.put("name", this.document.getFileName());
                    outputWriter.close();
                    json.put("file", file);
                }
                
                json.write(out);
                return json.toString();
         }
}
