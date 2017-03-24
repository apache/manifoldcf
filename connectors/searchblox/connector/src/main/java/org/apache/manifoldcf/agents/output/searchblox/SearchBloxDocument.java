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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.crawler.system.Logging;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.jsoup.Jsoup;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * "Package" class modeling a SearchBox document as a POJO
 *
 * @author Rafa Haro <rharo@apache.org>
 * @author Antonio David Perez Morales <adperezmorales@apache.org>
 */
public class SearchBloxDocument {

  static final String API_KEY = "apikey";
  static final String SEARCHBLOX_COLLECTION = "collection";
  static final String DATE_FORMAT = "dd MMMM yyyy HH:mm:ss z";

  public enum IndexingFormat {
    JSON, XML
  }

  public enum DocumentAction {
    ADD_UPDATE, DELETE, STATUS, CREATE, CLEAR
  }
  static final List<String> xmlElements= Lists.newArrayList("searchblox","document","url","title","keywords","content","description","lastmodified","size",
      "alpha","contenttype","category","meta","uid");

  static final String COLNAME_ATTRIBUTE = "colname";
  static final String APIKEY_ATTRIBUTE = "apikey";
  static final String NAME_ATTRIBUTE = "name";
  static final String UID_ATTRIBUTE = "uid";
  static final String BOOST_ATTRIBUTE = "boost";

  private Multimap<String, Object> data_fields = HashMultimap.create();

  /**
   * API key accessible in the SearchBlox Admin Console.
   */
  String apiKey;

  /**
   * Name of the Custom collection
   */
  String colName;

  /**
   * unique identifer for a document (default when unassigned is url location)
   */
  String uid;

  public SearchBloxDocument(String apikey) {
    this.apiKey = apikey;
  }

  public SearchBloxDocument(String apikey, String documentURI,
      RepositoryDocument rd, Map<String, List<String>> args) {
    this(apikey);
    SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.ROOT);

    this.uid = documentURI;
    this.colName = args.get(SEARCHBLOX_COLLECTION).get(0);

    Date date = rd.getModifiedDate();
    if(date!=null){
      data_fields.put(xmlElements.get(7),
          dateFormat.format(rd.getModifiedDate()));
    }  

    // content
    String content = "";
    try {
      if (rd.getField(xmlElements.get(5)) != null)
        content = (String) rd.getField(xmlElements.get(5))[0];
      else
        content = this.buildString(rd.getBinaryStream());
    } catch (IOException e) {
      Logging.connectors
      .error("[Parsing Content]Content is not text plain, verify you are properly using Apache Tika Transformer",
          e);
    }
    data_fields.put(xmlElements.get(5), this.clean(content));

    // Content Type
    data_fields.put(xmlElements.get(10), rd.getMimeType());

    // Size
    data_fields.put(xmlElements.get(8), "" + rd.getBinaryLength());

    // Boosting
    for(String boostId:args.keySet()){
      if(boostId.endsWith("_boost")){
        List<String> argBoost = args.get(boostId);
        if(argBoost!=null && !argBoost.isEmpty())
          data_fields.put(boostId,argBoost.get(0));
      }
    }

    // Metadata
    Multimap<String, String> metadata = HashMultimap.create();
    Iterator<String> it = rd.getFields();
    while (it.hasNext()) {
      String name = it.next();
      try {
        String[] values = rd.getFieldAsStrings(name);
        for (String value : values) {
          String key = name.toLowerCase(Locale.ROOT);
          int indexOf = xmlElements.indexOf(key);
          if(indexOf != 5)
            if (indexOf != -1 &&
              indexOf != 0 &&
              indexOf != 7 &&
              indexOf != 8) {
              data_fields.put(key, value);
            } else
              metadata.put(name, value);
        }
      } catch (IOException e) {
        Logging.connectors.error(
            "[Getting Field Values]Impossible to read value for metadata "
                + name, e);
      }
    }

    // ACLS must be stored as metadata, as Searchblox use that construct to index custom named fields
    //the approach has been implemented and tested live
    Iterator<String> aclTypes = rd.securityTypesIterator();
    while (aclTypes.hasNext()) {
      String aclType = aclTypes.next();
      String[] allow_tokens = rd.getSecurityACL(aclType);
      for (String token : allow_tokens)
        metadata.put(aclType+"_allow", token);
      String[] deny_tokens = rd.getSecurityDenyACL(aclType);
      for (String token : deny_tokens)
        metadata.put(aclType+"_deny", token);
    }
    data_fields.put(xmlElements.get(12), metadata);
  }

  /**
   * Clean a String from html tags or  break lines
   * @param content
   * @return
   */
  private String clean(String content) {
    content = content.replaceAll("(\r\n|\n)", " ");
    String cleanContent= Jsoup.parseBodyFragment(content).text();
    return cleanContent;
  }

  private String buildString(InputStream binaryStream) throws IOException {
    StringWriter writer = new StringWriter();
    IOUtils.copy(binaryStream, writer, "UTF-8");
    return writer.toString();
  }

  public String toString(IndexingFormat format, DocumentAction action)
      throws SearchBloxException {
    if(format == IndexingFormat.XML)
      return toStringXML(action);
    else
      return toStringJSON(action);
  }

  private String toStringJSON(DocumentAction action) throws SearchBloxException {
    JSONObject result = new JSONObject();
    if (apiKey == null)
      throw new SearchBloxException(
          "The API Key for accessing SearchBlox Server CAN'T be NULL");
    
    result.put(APIKEY_ATTRIBUTE, apiKey);

    JSONObject document = new JSONObject();
    if (colName == null)
      throw new SearchBloxException(
          "The Collection Name of the SearchBlox Server CAN'T be NULL");
    document.put(COLNAME_ATTRIBUTE, colName);
    document.put(UID_ATTRIBUTE, uid);

    if(action == DocumentAction.ADD_UPDATE){
      for(String element:xmlElements){
        if (!element.equals(xmlElements.get(12))) {
          Collection<Object> values = data_fields.get(element);
          if (values!=null && values.size()>0) {
            Object next = values.iterator()
                .next();
            String value =(String) next;
            if (value != null && !value.isEmpty()) {
              if(element.equals("keywords"))
                document.put(element, StringUtils.join(values, ','));
              else
                document.put(element, value);
                
            }
          }
        }
      }

      // Metadata
      Collection<Object> metadataSet = data_fields
          .get(xmlElements.get(12));
      JSONObject metaObject = new JSONObject();
      if(metadataSet!=null && metadataSet.size()>0){
        Multimap<String, String> metadata = (Multimap<String, String>) metadataSet.iterator().next();
        if (metadata != null && !metadata.isEmpty()) {
          for (String name : metadata.keySet()){
            JSONArray nextMetadata = new JSONArray();
            for (String value : metadata.get(name)) {
              nextMetadata.add(value);
            }
            metaObject.put(name, nextMetadata);
          }
        }  
      }
      document.put(xmlElements.get(12), metaObject);
    }

    result.put(xmlElements.get(1), document);

    return result.toJSONString();
  }

  private String toStringXML(DocumentAction action) throws SearchBloxException{
    Document doc = null;
    try {
      doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
          .newDocument();

    } catch (ParserConfigurationException e) {
      throw new SearchBloxException(e);
    }

    // Document Base Data
    Element root = doc.createElement(xmlElements.get(0));
    if (apiKey == null)
      throw new SearchBloxException(
          "The API Key for accessing SearchBlox Server CAN'T be NULL");
    root.setAttribute(APIKEY_ATTRIBUTE, apiKey);
    doc.appendChild(root);
    Element document = doc.createElement(xmlElements.get(1));
    if (colName == null)
      throw new SearchBloxException(
          "The Collection Name of the SearchBlox Server CAN'T be NULL");
    document.setAttribute(COLNAME_ATTRIBUTE, colName);
    if(action == DocumentAction.DELETE)
      document.setAttribute(UID_ATTRIBUTE,uid);
    root.appendChild(document);

    if (action == DocumentAction.ADD_UPDATE) {
      // Uid
      if (uid != null && !uid.isEmpty()) {
        Element uidElement = doc.createElement(xmlElements.get(13));
        uidElement.setTextContent(uid);
        document.appendChild(uidElement);
      }

      for(String element:xmlElements){
        if (!element.equals(xmlElements.get(12))) {
          Collection<Object> values = data_fields.get(element);
          if (values!=null && values.size()>0) {
            Object next = values.iterator()
                .next();
            String value =(String) next;
            if (value != null && !value.isEmpty()) {
              Element eValue = doc.createElement(element);
              if(element.equals("keywords"))
                eValue.setTextContent(StringUtils.join(values, ','));
              else
                eValue.setTextContent(value);
              Collection<Object> boostElement = data_fields
                  .get(element + "_boost");
              if(boostElement!=null && boostElement.size()>0){
                String value_boost = (String) boostElement.iterator()
                    .next();
                eValue.setAttribute(BOOST_ATTRIBUTE, "" + value_boost);
              }
              document.appendChild(eValue);
            }
          }
        }
      }

      // Metadata
      Collection<Object> metadataSet = data_fields
          .get(xmlElements.get(12));
      if(metadataSet!=null && metadataSet.size()>0){
        Multimap<String, String> metadata = (Multimap<String, String>) metadataSet.iterator().next();
        if (metadata != null && !metadata.isEmpty()) {
          for (String name : metadata.keySet())
            for (String value : metadata.get(name)) {
              Element metaElement = doc.createElement(xmlElements.get(12));
              metaElement.setAttribute(NAME_ATTRIBUTE, name);
              metaElement.setTextContent(value);
              document.appendChild(metaElement);
            }
        }
      }
    }

    return getStringFromDocument(doc);
  }

  /**
   * <p>Transform a {@code Document} to its XML string representation</p>
   * @param doc the document to transform
   * @return the document in the XML-String format
   */
  private String getStringFromDocument(Document doc) {
    try {
      DOMSource domSource = new DOMSource(doc);
      StringWriter writer = new StringWriter();
      StreamResult result = new StreamResult(writer);
      TransformerFactory tf = TransformerFactory.newInstance();
      Transformer transformer = tf.newTransformer();
      //      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
      transformer.transform(domSource, result);
      return writer.toString();
    } catch (TransformerException ex) {
      ex.printStackTrace();
      return null;
    }

  }
}
