package org.apache.manifoldcf.agents.output.amazoncloudsearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize.Inclusion;

public class SDFModel {
  
  private List<Document> documentList = new ArrayList<Document>();
  
  public void addDocument(Document doc){
    documentList.add(doc);
  }

  public String toJSON() throws JsonProcessingException{
    ObjectMapper mapper = new ObjectMapper();
    mapper.setSerializationInclusion(Include.NON_NULL);
    return mapper.writeValueAsString(documentList);
  }
  
  public class Document {
    private String type;
    private String id;
    private Map<String,Object> fields;
    
    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public Map getFields() {
      return fields;
    }

    public void setFields(Map<String,Object> fields) {
      this.fields = fields;
    }
  }
}
