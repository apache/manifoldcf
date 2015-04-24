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

import com.google.common.collect.Lists;

//import junit.framework.TestCase;

import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.agents.output.searchblox.SearchBloxDocument;
import org.apache.manifoldcf.agents.output.searchblox.SearchBloxDocument.DocumentAction;
import org.apache.manifoldcf.agents.output.searchblox.SearchBloxDocument.IndexingFormat;
import org.apache.manifoldcf.agents.output.searchblox.SearchBloxException;
import org.junit.Test;
import org.junit.Ignore;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Alessandro Benedetti
 *         07/03/2015
 *         mcf-searchblox-connector
 */
public class SearchBloxDocumentTest /*extends TestCase */ {
    SearchBloxDocument toTest;
    RepositoryDocument rd;

   public void setUp() throws ManifoldCFException {
       Map<String, List<String>> args=initArgs();
       String apikey="apikey";
       rd=initRepoDocument();
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
    @Ignore("fails on jdk 8 due to hash order")
    public void updateJsonString() throws SearchBloxException {
        String jsonGenerated=toTest.toString(IndexingFormat.JSON, DocumentAction.ADD_UPDATE);
        String expectedJson="{\"document\":{\"content\":\"I am a nice content in english!\",\"uid\":\"URI\",\"title\":\"I am a nice title\",\"description\":\"I am a little tiny description\",\"contenttype\":\"html\",\"colname\":\"collection1\",\"meta\":{\"meta2\":[\"I am META2!\"],\"meta1\":[\"I am META1!\"],\"share_allow\":[\"user3\",\"user2\",\"user1\"],\"share_deny\":[\"user4\",\"user5\"],\"document_deny\":[\"user52\",\"user42\"],\"document_allow\":[\"user22\",\"user12\",\"user33\"]},\"size\":\"100\"},\"apikey\":\"apikey\"}";
        assertEquals(expectedJson,jsonGenerated);
    }

    @Test
    @Ignore("fails on jdk 8 due to hash order")
    public void deleteJsonString() throws SearchBloxException {
        String jsonGenerated=toTest.toString(IndexingFormat.JSON, DocumentAction.DELETE);
        System.out.println(jsonGenerated);
        String xmlExpected="{\"document\":{\"uid\":\"URI\",\"colname\":\"collection1\"},\"apikey\":\"apikey\"}";
        assertEquals(xmlExpected,jsonGenerated);
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
        Map<String, List<String>> argMap=new HashMap<String, List<String>>();
        argMap.put("collection", Lists.newArrayList("collection1"));
        argMap.put("title_boost", Lists.newArrayList("1"));
        argMap.put("content_boost", Lists.newArrayList("2"));
        argMap.put("keywords_boost", Lists.newArrayList("3"));
        argMap.put("description_boost", Lists.newArrayList("4"));
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
