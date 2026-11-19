package com.mcplusa.manifoldcf.agents.output.appsearch;

public class ResponseBody {

  private String id;
  private String[] errors;

  public ResponseBody() {
  }

  public ResponseBody(String id, String[] errors) {
    this.id = id;
    this.errors = errors;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String[] getErrors() {
    return errors;
  }

  public void setErrors(String[] errors) {
    this.errors = errors;
  }

  @Override
  public String toString() {
    return "ResponseBody{" + "id=" + id + ", errors=" + errors + '}';
  }
}
