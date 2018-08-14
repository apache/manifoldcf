
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

package org.apache.manifoldcf.agents.transformation.htmlextractor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.apache.manifoldcf.crawler.system.Logging;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class JsoupProcessing {




  public static Hashtable<String,String> extractTextAndMetadataHtmlDocument(InputStream streamDoc,String whitelist,List<String> blacklist, boolean stripHtml) throws IOException{
    Document doc = Jsoup.parse(streamDoc, "UTF-8", "");
    Hashtable<String,String> metadata = new Hashtable<String,String>();
    for(Element meta : doc.select("meta")) {
      Logging.connectors.debug("Name: " + meta.attr("name") + " - Content: " + meta.attr("content"));
      metadata.put(meta.attr("name"), meta.attr("content"));
    }


    if (doc.select("title") != null){
      String title = doc.select("title").text();
      metadata.put("title", title);
    }

    Element element_keywords = doc.select("meta[name='keywords']").first();
    if (element_keywords != null) {
      String keywords = (element_keywords.attr("content"));
      metadata.put("keywords",keywords);
    }

    Element element_description = doc.select("meta[name=\"description\"]").first();
    if (element_description != null) {
      String description = (element_description.attr("content"));
      metadata.put("description",description);
    }

    Element element_author = doc.select("meta[name=\"author\"]").first();
    if (element_author != null) {
      String author = (element_author.attr("content"));
      metadata.put("author",author);
    }


    Element element_dcterms_subject = doc.select("meta[name=\"dcterms.subject\"]").first();
    if (element_dcterms_subject != null) {
      String dc_terms_subject = (element_dcterms_subject.attr("content"));
      metadata.put("dc_terms_subject",dc_terms_subject);
    }


    Element element_dcterms_title = doc.select("meta[name=\"dcterms.title\"]").first();
    if (element_dcterms_title != null) {
      String dc_terms_title = (element_dcterms_title.attr("content"));
      metadata.put("dc_terms_title",dc_terms_title);

    }

    Element element_dcterms_creator = doc.select("meta[name=\"dcterms.creator\"]").first();
    if (element_dcterms_creator != null) {
      String dc_terms_creator = (element_dcterms_creator.attr("content"));
      metadata.put("dc_terms_creator",dc_terms_creator);

    }

    Element element_dcterms_description = doc.select("meta[name=\"dcterms.description\"]").first();
    if (element_dcterms_description != null) {
      String dc_terms_description = (element_dcterms_description.attr("content"));
      metadata.put("dc_terms_description",dc_terms_description);

    }

    Element element_dcterms_publisher = doc.select("meta[name=\"dcterms.publisher\"]").first();
    if (element_dcterms_publisher != null) {
      String dc_terms_publisher = (element_dcterms_publisher.attr("content"));
      metadata.put("dc_terms_publisher",dc_terms_publisher);

    }

    Element element_dcterms_contributor = doc.select("meta[name=\"dcterms.contributor\"]").first();
    if (element_dcterms_contributor != null) {
      String dc_terms_contributor = (element_dcterms_contributor.attr("content"));
      metadata.put("dc_terms_contributor",dc_terms_contributor);

    }

    Element element_dcterms_date = doc.select("meta[name=\"dcterms.date\"]").first();
    if (element_dcterms_date != null) {
      String dc_terms_date = (element_dcterms_date.attr("content"));
      metadata.put("dc_terms_date",dc_terms_date);

    }

    Element element_dcterms_type = doc.select("meta[name=\"dcterms.type\"]").first();
    if (element_dcterms_type != null) {
      String dc_terms_type = (element_dcterms_type.attr("content"));
      metadata.put("dc_terms_type",dc_terms_type);

    }

    Element element_dcterms_format = doc.select("meta[name=\"dcterms.format\"]").first();
    if (element_dcterms_format != null) {
      String dc_terms_format = (element_dcterms_format.attr("content"));
      metadata.put("dc_terms_format",dc_terms_format);

    }

    Element element_dcterms_language = doc.select("meta[name=\"dcterms.language\"]").first();
    if (element_dcterms_language != null) {
      String dc_terms_language = (element_dcterms_language.attr("content"));
      metadata.put("dc_terms_language",dc_terms_language);

    }

    Element element_dcterms_identifier = doc.select("meta[name=\"dcterms.identifier\"]").first();
    if (element_dcterms_identifier != null) {
      String dc_terms_identifier = (element_dcterms_identifier.attr("content"));
      metadata.put("dc_terms_identifier",dc_terms_identifier);
    }


    Element docToKeep = doc.body();
    String finalDoc ;

    // Englobing Tag
    if (whitelist!="body"){
      docToKeep = doc.select(whitelist).first();
    }



    // Blacklist
    if (blacklist != null){
      for (int i=0; i< blacklist.size();i++){
        docToKeep.select(blacklist.get(i)).remove();
      }
    }

    if (stripHtml)
      finalDoc = docToKeep.text();
    else
      finalDoc = docToKeep.html();
    
    
    metadata.put("extractedDoc",finalDoc);

    return metadata;
  }

}