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
package org.apache.manifoldcf.agents.output.searchblox.tests;

import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.output.searchblox.SearchBloxDocument;
import org.apache.manifoldcf.agents.output.searchblox.SearchBloxDocument.DocumentAction;
import org.apache.manifoldcf.agents.output.searchblox.SearchBloxDocument.IndexingFormat;
import org.apache.manifoldcf.agents.output.searchblox.SearchBloxException;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
    @Ignore("fails on jdk 8 due to hash order")
    public void updateXmlString() throws SearchBloxException {
        String xmlGenerated=toTest.toString(IndexingFormat.XML, DocumentAction.ADD_UPDATE);
        System.out.println(xmlGenerated);
        String xmlExpected="<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<searchblox apikey=\"apikey\"><document colname=\"collection1\">" +
                "<uid>URI</uid><title boost=\"1\">I am a nice title</title><content boost=\"2\">I am a nice content in english!</content>" +
                "<description boost=\"4\">I am a little tiny description</description><size>100</size><contenttype>html</contenttype>" +
                "<meta name=\"meta2\">I am META2!</meta><meta name=\"share_allow\">user3</meta>" +
                "<meta name=\"share_allow\">user2</meta><meta name=\"share_allow\">user1</meta>" +
                "<meta name=\"meta1\">I am META1!</meta><meta name=\"share_deny\">user4</meta>" +
                "<meta name=\"share_deny\">user5</meta><meta name=\"document_deny\">user52</meta>" +
                "<meta name=\"document_deny\">user42</meta><meta name=\"document_allow\">user22</meta>" +
                "<meta name=\"document_allow\">user12</meta><meta name=\"document_allow\">user33</meta></document></searchblox>";
        assertEquals(xmlExpected,xmlGenerated);
    }

    @Test
    public void updateJsonString() throws SearchBloxException, JSONException {

        String jsonGenerated = toTest.toString(IndexingFormat.JSON, DocumentAction.ADD_UPDATE);

        JSONObject json = new JSONObject(jsonGenerated);
        assertTrue(json.has("apikey"));
        assertTrue(json.has("document"));

        Object apiObject = json.get("apikey");
        assertTrue(apiObject instanceof String);
        assertEquals("apikey", apiObject);

        Object documentObject = json.get("document");
        assertTrue(documentObject instanceof JSONObject);
        JSONObject document = (JSONObject) documentObject;

        assertTrue(document.has("uid"));
        assertTrue(document.get("uid") instanceof String);
        assertEquals("URI", document.get("uid"));


        assertTrue(document.has("colname"));
        assertTrue(document.get("colname") instanceof String);
        assertEquals("collection1", document.get("colname"));


        assertTrue(document.has("size"));
        assertTrue(document.get("size") instanceof String);
        assertEquals("100", document.get("size"));

        assertTrue(document.has("meta"));
        Object metaObject = document.get("meta");
        assertTrue(metaObject instanceof JSONObject);
        JSONObject meta = (JSONObject) metaObject;
        assertEquals(6, meta.length());

        assertTrue(meta.has("meta2"));
        assertTrue(meta.get("meta2") instanceof JSONArray);
        assertEquals(1, ((JSONArray) meta.get("meta2")).length());
        assertEquals("I am META2!", ((JSONArray) meta.get("meta2")).getString(0));

        assertTrue(meta.has("meta1"));
        assertTrue(meta.get("meta1") instanceof JSONArray);
        assertEquals(1, ((JSONArray) meta.get("meta1")).length());
        assertEquals("I am META1!", ((JSONArray) meta.get("meta1")).getString(0));

        assertTrue(meta.has("share_allow"));
        assertTrue(meta.get("share_allow") instanceof JSONArray);
        assertEquals(3, ((JSONArray) meta.get("share_allow")).length());
        assertEquals("user1", ((JSONArray) meta.get("share_allow")).getString(0));
        assertEquals("user2", ((JSONArray) meta.get("share_allow")).getString(1));
        assertEquals("user3", ((JSONArray) meta.get("share_allow")).getString(2));

        assertTrue(meta.has("document_deny"));
        assertTrue(meta.get("document_deny") instanceof JSONArray);
        assertEquals(2, ((JSONArray) meta.get("document_deny")).length());
        assertEquals("user42", ((JSONArray) meta.get("document_deny")).getString(0));
        assertEquals("user52", ((JSONArray) meta.get("document_deny")).getString(1));


        assertTrue(meta.has("share_deny"));
        assertTrue(meta.get("share_deny") instanceof JSONArray);
        assertEquals(2, ((JSONArray) meta.get("share_deny")).length());
        assertEquals("user5", ((JSONArray) meta.get("share_deny")).getString(0));
        assertEquals("user4", ((JSONArray) meta.get("share_deny")).getString(1));


        assertTrue(meta.has("document_allow"));
        assertTrue(meta.get("document_allow") instanceof JSONArray);
        assertEquals(3, ((JSONArray) meta.get("document_allow")).length());
        assertEquals("user22", ((JSONArray) meta.get("document_allow")).getString(0));
        assertEquals("user33", ((JSONArray) meta.get("document_allow")).getString(1));
        assertEquals("user12", ((JSONArray) meta.get("document_allow")).getString(2));


        assertTrue(document.has("description"));
        assertTrue(document.get("description") instanceof String);
        assertEquals("I am a little tiny description", document.get("description"));


        assertTrue(document.has("title"));
        assertTrue(document.get("title") instanceof String);
        assertEquals("I am a nice title", document.get("title"));

        assertTrue(document.has("content"));
        assertTrue(document.get("content") instanceof String);
        assertEquals("I am a nice content in english!", document.get("content"));

        assertTrue(document.has("contenttype"));
        assertTrue(document.get("contenttype") instanceof String);
        assertEquals("html", document.get("contenttype"));
    }

    @Test
    public void deleteJsonString() throws SearchBloxException, JSONException {
        String jsonGenerated=toTest.toString(IndexingFormat.JSON, DocumentAction.DELETE);

        JSONObject json = new JSONObject(jsonGenerated);
        assertTrue(json.has("apikey"));
        assertTrue(json.has("document"));

        Object apiObject = json.get("apikey");
        assertTrue(apiObject instanceof String);
        assertEquals("apikey", apiObject);

        Object documentObject = json.get("document");
        assertTrue(documentObject instanceof JSONObject);
        JSONObject document = (JSONObject) documentObject;

        assertTrue(document.has("uid"));
        assertTrue(document.has("colname"));

        Object uidObject = document.get("uid");
        assertTrue(uidObject instanceof String);
        assertEquals("URI", uidObject);

        Object colObject = document.get("colname");
        assertTrue(colObject instanceof String);
        assertEquals("collection1", colObject);
    }

    @Test
    @Ignore("fails on jdk 8 due to hash order")
    public void deleteXmlString() throws SearchBloxException {
        String xmlGenerated=toTest.toString(IndexingFormat.XML, DocumentAction.DELETE);
        System.out.println(xmlGenerated);
        String xmlExpected="<?xml version=\"1.0\" encoding=\"UTF-8\"?><searchblox apikey=\"apikey\"><document colname=\"collection1\" uid=\"URI\"/></searchblox>";
        assertEquals(xmlExpected,xmlGenerated);
    }

    private Map<String, List<String>> initArgs() {
        Map<String, List<String>> argMap=new HashMap<>();
        argMap.put("collection", Collections.singletonList("collection1"));
        argMap.put("title_boost", Collections.singletonList("1"));
        argMap.put("content_boost", Collections.singletonList("2"));
        argMap.put("keywords_boost", Collections.singletonList("3"));
        argMap.put("description_boost", Collections.singletonList("4"));
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
