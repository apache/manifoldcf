/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.manifoldcf.crawler.connectors.dropbox;

/**
 *
 * @author andrew
 */
public class DropboxConfig {
     
  /** Username */
  public static final String KEY_PARAM = "key";
 
  /** Password */
  public static final String SECRET_PARAM = "secret";
  
  
   public static final String APP_KEY_PARAM = "app_key";
 
  /** Password */
  public static final String APP_SECRET_PARAM = "app_secret";

  
  /** Path of the context of the CMIS AtomPub api */
  public static final String PATH_PARAM = "path";
  
  /** CMIS Repository Id */
  public static final String REPOSITORY_ID_PARAM = "repositoryId";
  
  //default values
  public static final String PATH_DEFAULT_VALUE = "NOT USED YET";
  public static final String REPOSITORY_ID_DEFAULT_VALUE = "dropbox";
  
  public static final String DROPBOX_QUERY_PARAM = "dropboxQuery";
  
  
  
  
}
