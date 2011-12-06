package org.apache.manifoldcf.crawler.connectors.alfresco;

/** 
 * Parameters data for the Alfresco repository connector.
*/
public class AlfrescoConfig {

  /** Username */
  public static final String USERNAME_PARAM = "username";
 
  /** Password */
  public static final String PASSWORD_PARAM = "password";
  
  /** Protocol */
  public static final String PROTOCOL_PARAM = "protocol";
  
  /** Server name */
  public static final String SERVER_PARAM = "server";
  
  /** Port */
  public static final String PORT_PARAM = "port";
  
  /** Path of the context of the Alfresco Web Services API */
  public static final String PATH_PARAM = "path";
  
  /** The Lucene Query parameter */
  public static final String LUCENE_QUERY_PARAM = "luceneQuery";
  
  //default values
  public static final String USERNAME_DEFAULT_VALUE = "admin";
  public static final String PASSWORD_DEFAULT_VALUE = "admin";
  public static final String PROTOCOL_DEFAULT_VALUE = "http";
  public static final String SERVER_DEFAULT_VALUE = "localhost";
  public static final String PORT_DEFAULT_VALUE = "8080";
  public static final String PATH_DEFAULT_VALUE = "/alfresco/api";
  
}
