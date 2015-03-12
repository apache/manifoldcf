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

import junit.framework.TestCase;

import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.agents.output.searchblox.SearchBloxDocument;
import org.apache.manifoldcf.agents.output.searchblox.SearchBloxDocument.DocumentAction;
import org.apache.manifoldcf.agents.output.searchblox.SearchBloxDocument.IndexingFormat;
import org.apache.manifoldcf.agents.output.searchblox.SearchBloxException;

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
public class SearchBloxDocumentTest extends TestCase{
    SearchBloxDocument toTest;
    RepositoryDocument rd;

   public void setUp() throws ManifoldCFException {
       Map<String, List<String>> args=initArgs();
       String apikey="apikey";
       rd=initRepoDocument();
       String docURI = "URI";
       toTest=new SearchBloxDocument(apikey, docURI,rd,args);
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
        realRepoDoc.setMimeType("html");
        realRepoDoc.setBinary(stream,100);
        realRepoDoc.setCreatedDate(new Date(System.currentTimeMillis()));
        return realRepoDoc;
    }

    public void testXmlString() throws SearchBloxException {
        String xmlGenerated=toTest.toString(IndexingFormat.XML, DocumentAction.ADD_UPDATE);
    }

}
