package org.apache.manifoldcf.agents.output.amazoncloudsearch;

import org.apache.manifoldcf.agents.output.BaseOutputConnector;
import org.apache.manifoldcf.agents.output.solr.HttpPoster;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;

public class AmazonCloudSearchConnector  extends BaseOutputConnector {

  /** Ingestion activity */
  public final static String INGEST_ACTIVITY = "document ingest";
  /** Document removal activity */
  public final static String REMOVE_ACTIVITY = "document deletion";

  /** Local connection */
  protected HttpPoster poster = null;

  /** Constructor.
   */
  public AmazonCloudSearchConnector(){
  }
  
  /** Return the list of activities that this connector supports (i.e. writes into the log).
  *@return the list.
  */
  @Override
  public String[] getActivitiesList()
  {
    return new String[]{INGEST_ACTIVITY,REMOVE_ACTIVITY};
  }

  /** Connect.
  *@param configParameters is the set of configuration parameters, which
  * in this case describe the target appliance, basic auth configuration, etc.  (This formerly came
  * out of the ini file.)
  */
  @Override
  public void connect(ConfigParams configParameters)
  {
    super.connect(configParameters);
  }

  /** This method is called to assess whether to count this connector instance should
  * actually be counted as being connected.
  *@return true if the connector instance is actually connected.
  */
  @Override
  public boolean isConnected()
  {
    return poster != null;
  }
  
  /** Close the connection.  Call this before discarding the connection.
  */
  @Override
  public void disconnect()
    throws ManifoldCFException
  {
    if (poster != null)
    {
      poster.shutdown();
      poster = null;
    }
    super.disconnect();
  }
  

  /** Set up a session */
  protected void getSession()
    throws ManifoldCFException
  {
    if (poster == null)
    {
      String documentEndpointUrl = "xxxxx.us-east-1.cloudsearch.amazonaws.com";
      String accessKey = "xxxxx";
      String secretKey = "xxxxx";
      
      //curl -X POST --upload-file data1.json doc.movies-123456789012.us-east-1.cloudsearch.amazonaws.com/2013-01-01/documents/batch --header "Content-Type:application/json"
      
    }
  }
  
  
}