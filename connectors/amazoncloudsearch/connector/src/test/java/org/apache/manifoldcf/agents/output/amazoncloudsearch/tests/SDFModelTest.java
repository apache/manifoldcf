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

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.apache.manifoldcf.agents.output.amazoncloudsearch.SDFModel;
import org.junit.Test;
import org.junit.Ignore;

import com.fasterxml.jackson.core.JsonProcessingException;

public class SDFModelTest {

  @Test
  @Ignore
  // Hash ordering dependency makes this test unreliable.
  public void testToJSON() {
    SDFModel model = new SDFModel();
    
    SDFModel.Document doc = model.new Document();
    doc.setType("add");
    doc.setId("aaaabbbbcccc");
    Map fields = new HashMap();
    fields.put("title", "The Seeker: The Dark Is Rising");
    fields.put("director", "Cunningham, David L.");
    String[] genre = {"Adventure","Drama","Fantasy","Thriller"};
    fields.put("genre", genre);
    doc.setFields(fields);
    
    model.addDocument(doc);
    
    SDFModel.Document doc2 = model.new Document();
    doc2.setType("delete");
    doc2.setId("xxxxxffffddddee");
    model.addDocument(doc2);
    
    try {
      String jsonStr = model.toJSON();
      System.out.println(jsonStr);
      String expect = "[{\"type\":\"add\",\"id\":\"aaaabbbbcccc\",\"fields\":{\"genre\":[\"Adventure\",\"Drama\",\"Fantasy\",\"Thriller\"],\"title\":\"The Seeker: The Dark Is Rising\",\"director\":\"Cunningham, David L.\"}},{\"type\":\"delete\",\"id\":\"xxxxxffffddddee\"}]";
      assertEquals(expect, jsonStr);
      
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      fail();
    }
  }

}
