package org.apache.manifoldcf.crawler.connectors.solringester;

public class SolrIngesterConfig {

  // Repository connection Configuration parameters
  public static final String PARAM_SOLRADDRESS = "solrAddress";
  public static final String PARAM_SOLRUSERNAME = "solrUsername";
  public static final String PARAM_SOLRPASSWORD = "solrPassword";
//  public static final String PARAM_SOLRPORT = "solrPort";
  //public static final String SOLRPORTPORT_DEFAULT = "8983";
  public static final String PARAM_CONNECTIONTIMEOUT = "connectionTimeout";
  public static final String SOLRUSERNAME_DEFAULT = "";
  public static final String SOLRPASSWORD_DEFAULT = "";
  public static final String PARAM_SOCKETTIMEOUT = "socketTimeout";
  public static final String SOLRADDRESS_DEFAULT = "http://localhost:8983/solr";
  public static final String CONNECTIONTIMEOUT_DEFAULT = "60000";
  public static final String SOCKETTIMEOUT_DEFAULT = "180000";
 
  // Specification nodes and values
  public static final String NODE_FIELDMAP = "fieldmap";
  public static final String ATTRIBUTE_SOURCE = "source";
  public static final String ATTRIBUTE_TARGET = "target";
  public static final String ATTRIBUTE_VALUE = "value";
  public static final String SECURITY_ACTIVATED = "securityactivated";
  public static final String SECURITY_FIELD = "securityfield";
  public static final String SECURITY_FIELD2 = "securityfield2";
  public static final String ID_FIELD = "fieldid";
  public static final String ROWS_NUMBER = "rowsnumber";
  public static final String DATE_FIELD = "fielddate";
  public static final String CONTENT_FIELD = "fieldcontent";
  public static final String FILTER_CONDITION = "filter";
  public static final String COLLECTION_NAME = "collection";

}
