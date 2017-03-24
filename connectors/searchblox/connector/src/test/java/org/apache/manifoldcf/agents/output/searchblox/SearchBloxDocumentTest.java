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

import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.output.searchblox.SearchBloxDocument.DocumentAction;
import org.apache.manifoldcf.agents.output.searchblox.SearchBloxDocument.IndexingFormat;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.apache.manifoldcf.agents.output.searchblox.SearchBloxConfig.*;
import static org.apache.manifoldcf.agents.output.searchblox.SearchBloxDocument.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SearchBloxDocumentTest {
    private SearchBloxDocument toTest;
    @Before
   public void setUp() throws ManifoldCFException {
       Map<String, List<String>> args=initArgs();
       String apikey="apikey";
        RepositoryDocument rd=initRepoDocument();
       String docURI = "URI";
       toTest=new SearchBloxDocument(apikey, docURI,rd,args);
   }

    @Test
    public void updateXmlString() throws SearchBloxException, ParserConfigurationException, SAXException, IOException {
        String xmlGenerated = toTest.toString(IndexingFormat.XML, DocumentAction.ADD_UPDATE);

        InputSource is = new InputSource(new ByteArrayInputStream(xmlGenerated.getBytes(StandardCharsets.UTF_8)));

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(is);
        doc.getDocumentElement().normalize();

        Element root = doc.getDocumentElement();
        assertEquals("searchblox", root.getNodeName());
        assertEquals("apikey", root.getAttribute(APIKEY_ATTRIBUTE));

        assertEquals(1, root.getElementsByTagName("document").getLength());
        assertEquals(Node.ELEMENT_NODE, root.getElementsByTagName("document").item(0).getNodeType());

        Element document = (Element) root.getElementsByTagName("document").item(0);
        assertEquals("collection1", document.getAttribute(COLNAME_ATTRIBUTE));

        NodeList nList = document.getChildNodes();
        assertEquals(18, nList.getLength());

        nList = document.getElementsByTagName(UID_ATTRIBUTE);
        assertEquals(1, nList.getLength());
        assertEquals(Node.ELEMENT_NODE, nList.item(0).getNodeType());
        assertEquals("URI", nList.item(0).getTextContent());

        nList = document.getElementsByTagName("title");
        assertEquals(1, nList.getLength());
        assertEquals(Node.ELEMENT_NODE, nList.item(0).getNodeType());
        assertEquals("I am a nice title", nList.item(0).getTextContent());
        assertEquals("1", ((Element) nList.item(0)).getAttribute(BOOST_ATTRIBUTE));

        nList = document.getElementsByTagName("content");
        assertEquals(1, nList.getLength());
        assertEquals(Node.ELEMENT_NODE, nList.item(0).getNodeType());
        assertEquals("I am a nice content in english!", nList.item(0).getTextContent());
        assertEquals("2", ((Element) nList.item(0)).getAttribute(BOOST_ATTRIBUTE));

        nList = document.getElementsByTagName("description");
        assertEquals(1, nList.getLength());
        assertEquals(Node.ELEMENT_NODE, nList.item(0).getNodeType());
        assertEquals("I am a little tiny description", nList.item(0).getTextContent());
        assertEquals("4", ((Element) nList.item(0)).getAttribute(BOOST_ATTRIBUTE));

        nList = document.getElementsByTagName("size");
        assertEquals(1, nList.getLength());
        assertEquals(Node.ELEMENT_NODE, nList.item(0).getNodeType());
        assertEquals("100", nList.item(0).getTextContent());

        nList = document.getElementsByTagName("contenttype");
        assertEquals(1, nList.getLength());
        assertEquals(Node.ELEMENT_NODE, nList.item(0).getNodeType());
        assertEquals("html", nList.item(0).getTextContent());

        nList = document.getElementsByTagName("meta");
        assertEquals(12, nList.getLength());

        assertTrue(find(nList, "meta2", "I am META2!"));
        assertTrue(find(nList, "meta1", "I am META1!"));

        assertTrue(find(nList, "share_allow", "user1"));
        assertTrue(find(nList, "share_allow", "user2"));
        assertTrue(find(nList, "share_allow", "user3"));

        assertTrue(find(nList, "document_deny", "user42"));
        assertTrue(find(nList, "document_deny", "user52"));

        assertTrue(find(nList, "share_deny", "user5"));
        assertTrue(find(nList, "share_deny", "user4"));

        assertTrue(find(nList, "document_allow", "user22"));
        assertTrue(find(nList, "document_allow", "user33"));
        assertTrue(find(nList, "document_allow", "user12"));


    }

    private boolean find(NodeList nList, String name, String textContent) {
        for (int i = 0; i < nList.getLength(); i++) {
            Node node = nList.item(i);

            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;

                if (name.equals(element.getAttribute(NAME_ATTRIBUTE)) && textContent.equals(element.getTextContent()))
                    return true;
            }
        }
        return false;
    }

    @Test
    public void updateJsonString() throws Exception {

        String jsonGenerated = toTest.toString(IndexingFormat.JSON, DocumentAction.ADD_UPDATE);

        final JSONParser parser = new JSONParser();
        JSONObject json = (JSONObject)parser.parse(new java.io.StringReader(jsonGenerated));

        assertTrue(json.get(APIKEY_ATTRIBUTE) != null);
        assertTrue(json.get("document") != null);

        Object apiObject = json.get(APIKEY_ATTRIBUTE);
        assertTrue(apiObject instanceof String);
        assertEquals("apikey", apiObject);

        Object documentObject = json.get("document");
        assertTrue(documentObject instanceof JSONObject);
        JSONObject document = (JSONObject) documentObject;

        assertTrue(document.get(UID_ATTRIBUTE) != null);
        assertTrue(document.get(UID_ATTRIBUTE) instanceof String);
        assertEquals("URI", document.get(UID_ATTRIBUTE));


        assertTrue(document.get(COLNAME_ATTRIBUTE)  != null);
        assertTrue(document.get(COLNAME_ATTRIBUTE) instanceof String);
        assertEquals("collection1", document.get(COLNAME_ATTRIBUTE));


        assertTrue(document.get("size") != null);
        assertTrue(document.get("size") instanceof String);
        assertEquals("100", document.get("size"));

        assertTrue(document.get("meta") != null);
        Object metaObject = document.get("meta");
        assertTrue(metaObject instanceof JSONObject);
        JSONObject meta = (JSONObject) metaObject;
        assertEquals(6, meta.size());

        assertTrue(find(meta, "meta2", "I am META2!", 1));
        assertTrue(find(meta, "meta1", "I am META1!", 1));

        assertTrue(find(meta, "share_allow", "user1", 3));
        assertTrue(find(meta, "share_allow", "user2", 3));
        assertTrue(find(meta, "share_allow", "user3", 3));

        assertTrue(find(meta, "document_deny", "user42", 2));
        assertTrue(find(meta, "document_deny", "user52", 2));

        assertTrue(find(meta, "share_deny", "user5", 2));
        assertTrue(find(meta, "share_deny", "user4", 2));

        assertTrue(find(meta, "document_allow", "user22", 3));
        assertTrue(find(meta, "document_allow", "user33", 3));
        assertTrue(find(meta, "document_allow", "user12", 3));


        assertTrue(document.get("description") != null);
        assertTrue(document.get("description") instanceof String);
        assertEquals("I am a little tiny description", document.get("description"));


        assertTrue(document.get("title") != null);
        assertTrue(document.get("title") instanceof String);
        assertEquals("I am a nice title", document.get("title"));

        assertTrue(document.get("content") != null);
        assertTrue(document.get("content") instanceof String);
        assertEquals("I am a nice content in english!", document.get("content"));

        assertTrue(document.get("contenttype") != null);
        assertTrue(document.get("contenttype") instanceof String);
        assertEquals("html", document.get("contenttype"));
    }

    private boolean find(JSONObject meta, String name, String textContent, int size) {

        assertTrue(meta.get(name) != null);
        assertTrue(meta.get(name) instanceof JSONArray);

        assertEquals(size, ((JSONArray) meta.get(name)).size());

        for (int i = 0; i < size; i++) {
            if (textContent.equals(((JSONArray) meta.get(name)).get(i).toString()))
                return true;
        }
        return false;
    }

    @Test
    public void deleteJsonString() throws Exception {
        String jsonGenerated=toTest.toString(IndexingFormat.JSON, DocumentAction.DELETE);

        final JSONParser parser = new JSONParser();
        JSONObject json = (JSONObject)parser.parse(new java.io.StringReader(jsonGenerated));

        assertTrue(json.get(APIKEY_ATTRIBUTE) != null);
        assertTrue(json.get("document") != null);

        Object apiObject = json.get(APIKEY_ATTRIBUTE);
        assertTrue(apiObject instanceof String);
        assertEquals("apikey", apiObject);

        Object documentObject = json.get("document");
        assertTrue(documentObject instanceof JSONObject);
        JSONObject document = (JSONObject) documentObject;

        assertTrue(document.get(UID_ATTRIBUTE) != null);
        assertTrue(document.get(COLNAME_ATTRIBUTE) != null);

        Object uidObject = document.get(UID_ATTRIBUTE);
        assertTrue(uidObject instanceof String);
        assertEquals("URI", uidObject);

        Object colObject = document.get(COLNAME_ATTRIBUTE);
        assertTrue(colObject instanceof String);
        assertEquals("collection1", colObject);
    }

    @Test
    public void deleteXmlString() throws SearchBloxException, ParserConfigurationException, SAXException, IOException {
        String xmlGenerated=toTest.toString(IndexingFormat.XML, DocumentAction.DELETE);

        InputSource is = new InputSource(new ByteArrayInputStream(xmlGenerated.getBytes(StandardCharsets.UTF_8)));

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(is);
        doc.getDocumentElement().normalize();

        Element root = doc.getDocumentElement();
        assertEquals("searchblox", root.getNodeName());
        assertEquals("apikey", root.getAttribute(APIKEY_ATTRIBUTE));

        assertEquals(1, root.getElementsByTagName("document").getLength());
        assertEquals(Node.ELEMENT_NODE, root.getElementsByTagName("document").item(0).getNodeType());

        Element document = (Element) root.getElementsByTagName("document").item(0);
        assertEquals("collection1", document.getAttribute(COLNAME_ATTRIBUTE));
        assertEquals("URI", document.getAttribute(UID_ATTRIBUTE));
    }

    private Map<String, List<String>> initArgs() {
        Map<String, List<String>> argMap=new HashMap<>();
        argMap.put(ATTRIBUTE_COLLECTION_NAME, Collections.singletonList("collection1"));
        argMap.put(ATTRIBUTE_TITLEBOOST, Collections.singletonList("1"));
        argMap.put(ATTRIBUTE_CONTENTBOOST, Collections.singletonList("2"));
        argMap.put(ATTRIBUTE_KEYWORDSBOOST, Collections.singletonList("3"));
        argMap.put(ATTRIBUTE_DESCRIPTIONBOOST, Collections.singletonList("4"));
        return argMap;

    }

    private RepositoryDocument initRepoDocument() throws ManifoldCFException {
        RepositoryDocument realRepoDoc=new RepositoryDocument();
        String binaryContent="I am the binary content of an Amazing Document";
        InputStream stream = new ByteArrayInputStream(binaryContent.getBytes(StandardCharsets.UTF_8));

        realRepoDoc.addField("title","I am a nice title");
        realRepoDoc.addField("content","I am a nice content in english!");
        realRepoDoc.addField("description","I am a little tiny description");
        realRepoDoc.addField("meta1","I am META1!");
        realRepoDoc.addField("meta2","I am META2!");
        realRepoDoc.setMimeType("html");
        realRepoDoc.setBinary(stream,100);
        realRepoDoc.setCreatedDate(new Date(System.currentTimeMillis()));
        realRepoDoc.setSecurityACL(RepositoryDocument.SECURITY_TYPE_SHARE,new String[]{"user1","user2","user3"});
        realRepoDoc.setSecurityACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT,new String[]{"user12","user22","user33"});
        realRepoDoc.setSecurityDenyACL(RepositoryDocument.SECURITY_TYPE_SHARE, new String[]{"user4", "user5"});
        realRepoDoc.setSecurityDenyACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT, new String[]{"user42", "user52"});
        //allowAttributeName + aclType
        return realRepoDoc;
    }

}
