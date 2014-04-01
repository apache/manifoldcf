package org.apache.manifoldcf.agents.output.amazoncloudsearch.tests;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.apache.manifoldcf.agents.output.amazoncloudsearch.SDFModel;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

public class SDFModelTest {

  @Test
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
