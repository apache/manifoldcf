package org.apache.manifoldcf.authorities.authorities.newgeneric.api;

import java.util.List;

public class NewGenericUser {

  
  
  private String username;
  private List<String> tokens;
  boolean existsUser;

  public NewGenericUser() {
    this.username = null;
    this.tokens = null;
    this.existsUser = false;
  }
  
  public NewGenericUser(String username, List<String> tokens,boolean existsUser ) {
    this.username = username;
    this.tokens = tokens;
    this.existsUser = existsUser;
  }


  public String getUsername() {
    return username;
  }

  public List<String> getTokens() {
    return tokens;
  }
  
  public void setUsername(String username) {
    this.username = username;
  }
  
  public void setTokens(List<String> tokens) {
    this.tokens = tokens;
  }
  
  public void setExistsUser(boolean existsUser) {
    this.existsUser = existsUser;
  }
  
  public boolean getExistsUser() {
   return  existsUser ;
    
  }
}
