package org.apache.manifoldcf.crawler.connectors.cmis;

import org.apache.commons.lang.StringUtils;

/** 
 * Parameters data for the CMIS repository connector.
*/
public class CmisConfig {
  
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
  
  /** Path of the context of the CMIS AtomPub api */
  public static final String PATH_PARAM = "path";
  
  /** CMIS Repository Id */
  public static final String REPOSITORY_ID_PARAM = "repositoryId";
  
  /** CMIS protocol binding */
  public static final String BINDING_PARAM = "binding";
  
  /** CMIS Query */
  public static final String CMIS_QUERY_PARAM = "cmisQuery";
  
  //default values
  public static final String USERNAME_DEFAULT_VALUE = "dummyuser";
  public static final String PASSWORD_DEFAULT_VALUE = "dummysecrect";
  public static final String PROTOCOL_DEFAULT_VALUE = "http";
  public static final String SERVER_DEFAULT_VALUE = "localhost";
  public static final String PORT_DEFAULT_VALUE = "9090";
  public static final String BINDING_DEFAULT_VALUE = "atom";
  public static final String PATH_DEFAULT_VALUE = "/chemistry-opencmis-server-inmemory-war/atom";
  public static final String REPOSITORY_ID_DEFAULT_VALUE = StringUtils.EMPTY;
  public static final String BINDING_ATOM_VALUE = "atom";
  public static final String BINDING_WS_VALUE = "ws";
  
}
