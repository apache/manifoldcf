/* $Id$ */

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
package org.apache.manifoldcf.agents.output.amazoncloudsearch.tests;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.manifoldcf.agents.output.amazoncloudsearch.SDFModel;
import org.apache.manifoldcf.agents.output.amazoncloudsearch.SDFModel.Document;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class AmazonCloudSearchConnectorTest {
  
  public static void main(String[] args){
    InputStream is;
    try {

      
      
      is = new FileInputStream(new File("000407.htm"));
      Parser parser = new HtmlParser();
      ContentHandler handler = new BodyContentHandler();
      Metadata metadata = new Metadata();
      parser.parse(is, handler, metadata, new ParseContext());
      
      //build json..
      SDFModel model = new SDFModel();
      Document doc = model.new Document();
      doc.setType("add");
      doc.setId("aabbcc");
      
      //set body text.
      Map<String,Object> fields = new HashMap<String,Object>();
      String bodyStr = handler.toString();
      if(bodyStr != null){
        bodyStr = handler.toString().replaceAll("\\n", "").replaceAll("\\t", "");
        fields.put("body", bodyStr);
      }
      
      //mapping metadata to SDF fields.
      String contenttype = metadata.get("Content-Style-Type");
      String title = metadata.get("dc.title");
      String size = metadata.get("Content-Length");
      String description = metadata.get("description");
      String keywords = metadata.get("keywords");
      if(contenttype != null && !"".equals(contenttype)) fields.put("content_type", contenttype);
      if(title != null && !"".equals(title)) fields.put("title", title);
      if(size != null && !"".equals(size)) fields.put("size", size);
      if(description != null && !"".equals(description)) fields.put("description", description);
      if(keywords != null && !"".equals(keywords))
      {
        List<String> keywordList = new ArrayList<String>();
        for(String tmp : keywords.split(",")){
          keywordList.add(tmp);
        }
        fields.put("keywords", keywordList);
      }
      doc.setFields(fields);
      model.addDocument(doc);
      
      //generate json data.
      String jsondata = model.toJSON();
      System.out.println(jsondata);
      
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (SAXException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (TikaException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
  }
  
}
