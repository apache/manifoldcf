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
package org.apache.manifoldcf.agents.output.searchblox;

import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.apache.manifoldcf.agents.output.searchblox.SearchBloxDocument.IndexingFormat;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.xerces.parsers.DOMParser;
import org.jboss.resteasy.plugins.providers.StringTextStar;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * SearchBox REST Client
 *
 * @author Rafa Haro <rharo@apache.org>
 */
public class SearchBloxClient {

  // TODO All this might need to be included in a configuration file
  public static final String DEFAULT_ENDPOINT = "http://localhost:8080/searchblox/rest/v1/api";

  private static final String ADD_PATH = "add";

  private static final String DELETE_PATH = "delete";

  private static final String STATUS_PATH = "status";

  private static final String CREATE_PATH = "coladd";

  private static final String CLEAR_PATH = "clear";

  private static final String STATUS_NODE = "statuscode";
  
  private static final Pattern status_pattern = Pattern.compile("^status code\\s?:\\s([0-9]+)$");

  public static enum ResponseCode {
    DOCUMENT_INDEXED(100),
    DOCUMENT_REJECTED(101),
    DOCUMENT_DELETED(200, 2001),
    DOCUMENT_NOT_EXIST(201, 2002),
    DOCUMENT_NOT_FOUND(301),
    COLLECTION_CLEARED(400),
    ERROR_CLEARING_COLLECTION(401),
    COLLECTION_CREATED(900),
    INVALID_COLLECTION_NAME(500, 501),
    INVALID_REQUEST(501),
    INVALID_DOCUMENT_LOCATION(502),
    NOT_CUSTOM_COLLECTION(503),
    LIMIT_EXCEEDED(504),
    INVALID_LICENSE_ID(601),
    SERVER_UNREACHABLE(700);

    private int code;
    private int jsonCode;
    
    ResponseCode(int code) {
      this.code = code;
    }
    
    ResponseCode(int code, int jsonCode) {
      this.code = code;
      this.jsonCode = jsonCode;
    }
    
    static ResponseCode getCodeFromValue(int value){
        for(ResponseCode e:ResponseCode.values())
            if(value == e.code)
                return e;
        return null;
    }
    
    static ResponseCode getCodeFromValue(int value, boolean json){
      for(ResponseCode e:ResponseCode.values())
        if((json && value == e.jsonCode) || (value == e.code)) {
          return e;
        }
      return null;
    }
    
    int getCode(){
      return code;
    }
    
    int getJsonCode() {
      return jsonCode;
    }
  }
  

  private String apikey;
  private Client client;
  private UriBuilder uriBuilder;

  public SearchBloxClient(String apikey, ClientBuilder builder, String endpoint) {
    this.apikey = apikey;
    builder.register(StringTextStar.class);
    this.client = builder.build();
    if (endpoint != null && !endpoint.isEmpty()) {
      uriBuilder = UriBuilder.fromUri(endpoint);
    } else {
      uriBuilder = UriBuilder.fromUri(DEFAULT_ENDPOINT);
    }
  }


  public ResponseCode addUpdateDocument(SearchBloxDocument document, String format)
      throws SearchBloxException {
    return post(document, format, SearchBloxDocument.DocumentAction.ADD_UPDATE);
  }

  public ResponseCode deleteDocument(SearchBloxDocument document, String format)
      throws SearchBloxException {
    return post(document, format, SearchBloxDocument.DocumentAction.DELETE);
  }

  public ResponseCode createCollection(String colname, String format)
      throws SearchBloxException {
    SearchBloxDocument document = new SearchBloxDocument(apikey);
    document.colName = colname;
    return post(document, format, SearchBloxDocument.DocumentAction.CREATE);
  }

  public ResponseCode clearCollection(String colname, String format)
      throws SearchBloxException {
    SearchBloxDocument document = new SearchBloxDocument(apikey);
    document.colName = colname;
    return post(document, format, SearchBloxDocument.DocumentAction.CLEAR);
  }

  public boolean ping(String format)
      throws SearchBloxException {
    SearchBloxDocument document = new SearchBloxDocument(apikey);
    document.colName = UUID.randomUUID().toString();
    document.uid = UUID.randomUUID().toString();
    ResponseCode result = post(document, format, SearchBloxDocument.DocumentAction.STATUS);
    return result == ResponseCode.INVALID_COLLECTION_NAME;
  }

  private ResponseCode post(SearchBloxDocument document, String format, SearchBloxDocument.DocumentAction action)
      throws SearchBloxException {
    
    SearchBloxDocument.IndexingFormat iFormat = SearchBloxDocument.IndexingFormat.valueOf(format.toUpperCase(Locale.ROOT));
      
    if (iFormat == null) {
      Logging.connectors.error("[Post request] Format not recognized " +format);
      throw new SearchBloxException("Unknown Serialization Format " + format);
    }
      
    boolean isJson = iFormat.equals(SearchBloxDocument.IndexingFormat.JSON);
      
    

    UriBuilder uri = uriBuilder.clone();
    if (action == SearchBloxDocument.DocumentAction.ADD_UPDATE) {
      uri = uri.path(ADD_PATH);
    } else if (action == SearchBloxDocument.DocumentAction.DELETE) {
      uri = uri.path(DELETE_PATH);
    } else if (action == SearchBloxDocument.DocumentAction.STATUS) {
      uri = uri.path(STATUS_PATH);
    } else if (action == SearchBloxDocument.DocumentAction.CREATE) {
      uri = uri.path(CREATE_PATH);
    } else if (action == SearchBloxDocument.DocumentAction.CLEAR) {
      uri = uri.path(CLEAR_PATH);
    }

    WebTarget target = client.target(uri.build());
    Builder httpRequest = target.request();
    if (iFormat == SearchBloxDocument.IndexingFormat.JSON) {
      httpRequest.accept(MediaType.APPLICATION_JSON_TYPE);
    }else{
      httpRequest.accept(MediaType.APPLICATION_XML_TYPE);
    }
    

    document.apiKey = this.apikey;
    
    String body = document.toString(iFormat, action);
    Logging.connectors.debug("Document for document: " + document.uid +":" + body);
    MediaType type = MediaType.TEXT_PLAIN_TYPE;
    if (iFormat == SearchBloxDocument.IndexingFormat.JSON) {
      type = MediaType.APPLICATION_JSON_TYPE;
    }

    
    Entity<String> entity = Entity.entity(body, type);
    Response response = null;
    try {
      response = httpRequest.post(entity);
    }
    catch(Exception e) {
    //    return e.getCause() instanceof ConnectException ? ResponseCode.SERVER_UNREACHABLE : ResponseCode.INVALID_COLLECTION_NAME;
      Logging.connectors.error("[No Connection] Error trying to connect ",e);
      return ResponseCode.SERVER_UNREACHABLE;
    }
    
    String rawResponse = response.readEntity(String.class);
    if(iFormat == IndexingFormat.XML){
      DOMParser parser = new DOMParser();
      try {
        parser.parse(new InputSource(new StringReader(rawResponse)));
      } catch (SAXException | IOException e) {
        Logging.connectors.error("[Response parsing] Dom parsing error", e);
        throw new SearchBloxException(e);
      }
      Document doc = parser.getDocument();
      NodeList nodeList = doc.getElementsByTagName(STATUS_NODE);
      if (nodeList == null || nodeList.getLength() == 0) {
        String message = "[Response Parsing] Status code not found";
        Logging.connectors.error(message);
        throw new SearchBloxException(message);
      }
      String codeStr = nodeList.item(0).getTextContent();
      int statusCode = Integer.parseInt(codeStr);
      return ResponseCode.getCodeFromValue(statusCode, isJson);
    }else{
      Matcher matcher = status_pattern.matcher(rawResponse);
      String codeStr = null;
      if(matcher.find())
        codeStr = matcher.group(1);
      if(codeStr == null){
        String message = "[Response parsing] Response code parsing error";
        Logging.connectors.error(message);
        throw new SearchBloxException(message);
      }
                
      int statusCode = Integer.parseInt(codeStr);
      return ResponseCode.getCodeFromValue(statusCode, isJson);
    }
  }
}
