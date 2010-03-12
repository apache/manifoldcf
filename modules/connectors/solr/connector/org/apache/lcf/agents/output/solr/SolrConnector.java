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
package org.apache.lcf.agents.output.solr;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;

import java.util.*;

/** This is the output connector for SOLR.  Currently, no frills.
*/
public class SolrConnector extends org.apache.lcf.agents.output.BaseOutputConnector
{
  public static final String _rcsid = "@(#)$Id$";

  // Activities we log

  /** Ingestion activity */
  public final static String INGEST_ACTIVITY = "document ingest";
  /** Document removal activity */
  public final static String REMOVE_ACTIVITY = "document deletion";

  /** Local data */
  protected HttpPoster poster = null;

  /** Constructor.
  */
  public SolrConnector()
  {
  }

  /** Return the list of activities that this connector supports (i.e. writes into the log).
  *@return the list.
  */
  public String[] getActivitiesList()
  {
    return new String[]{INGEST_ACTIVITY,REMOVE_ACTIVITY};
  }

  /** Return the path for the UI interface JSP elements.
  * This method should return the name of the folder, under the <webapp>/output/
  * area, where the appropriate JSP's can be found.  The name should NOT have a slash in it.
  *@return the folder part
  */
  public String getJSPFolder()
  {
    return "solr";
  }

  /** Connect.
  *@param configParameters is the set of configuration parameters, which
  * in this case describe the target appliance, basic auth configuration, etc.  (This formerly came
  * out of the ini file.)
  */
  public void connect(ConfigParams configParameters)
  {
    super.connect(configParameters);
  }

  /** Close the connection.  Call this before discarding the connection.
  */
  public void disconnect()
    throws LCFException
  {
    poster = null;
    super.disconnect();
  }

  /** Set up a session */
  protected void getSession()
    throws LCFException
  {
    if (poster == null)
    {
      String protocol = params.getParameter(org.apache.lcf.agents.output.solr.SolrConfig.PARAM_PROTOCOL);
      if (protocol == null || protocol.length() == 0)
        throw new LCFException("Missing parameter: "+org.apache.lcf.agents.output.solr.SolrConfig.PARAM_PROTOCOL);

      String server = params.getParameter(org.apache.lcf.agents.output.solr.SolrConfig.PARAM_SERVER);
      if (server == null || server.length() == 0)
        throw new LCFException("Missing parameter: "+org.apache.lcf.agents.output.solr.SolrConfig.PARAM_SERVER);

      String port = params.getParameter(org.apache.lcf.agents.output.solr.SolrConfig.PARAM_PORT);
      if (port == null || port.length() == 0)
        port = "80";

      String webapp = params.getParameter(org.apache.lcf.agents.output.solr.SolrConfig.PARAM_WEBAPPNAME);
      if (webapp == null || webapp.length() == 0)
        webapp = "";

      String updatePath = params.getParameter(org.apache.lcf.agents.output.solr.SolrConfig.PARAM_UPDATEPATH);
      if (updatePath == null || updatePath.length() == 0)
        updatePath = "";

      String removePath = params.getParameter(org.apache.lcf.agents.output.solr.SolrConfig.PARAM_REMOVEPATH);
      if (removePath == null || removePath.length() == 0)
        removePath = "";

      String statusPath = params.getParameter(org.apache.lcf.agents.output.solr.SolrConfig.PARAM_STATUSPATH);
      if (statusPath == null || statusPath.length() == 0)
        statusPath = "";

      String userID = params.getParameter(SolrConfig.PARAM_USERID);
      String password = params.getObfuscatedParameter(SolrConfig.PARAM_PASSWORD);
      String realm = params.getParameter(SolrConfig.PARAM_REALM);
      try
      {
        poster = new HttpPoster(protocol,server,Integer.parseInt(port),webapp,updatePath,removePath,statusPath,realm,userID,password);
      }
      catch (NumberFormatException e)
      {
        throw new LCFException(e.getMessage());
      }
    }
  }

  /** Test the connection.  Returns a string describing the connection integrity.
  *@return the connection's status as a displayable string.
  */
  public String check()
    throws LCFException
  {
    try
    {
      getSession();
      poster.checkPost();
      return super.check();
    }
    catch (ServiceInterruption e)
    {
      return "Transient error: "+e.getMessage();
    }
  }

  /** Get an output version string, given an output specification.  The output version string is used to uniquely describe the pertinent details of
  * the output specification and the configuration, to allow the Connector Framework to determine whether a document will need to be output again.
  * Note that the contents of the document cannot be considered by this method, and that a different version string (defined in IRepositoryConnector)
  * is used to describe the version of the actual document.
  *
  * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be
  * necessary.
  *@param spec is the current output specification for the job that is doing the crawling.
  *@return a string, of unlimited length, which uniquely describes output configuration and specification in such a way that if two such strings are equal,
  * the document will not need to be sent again to the output data store.
  */
  public String getOutputDescription(OutputSpecification spec)
    throws LCFException
  {
    // No output description data at this time.
    return "";
  }

  /** Add (or replace) a document in the output data store using the connector.
  * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be
  * necessary.
  * The OutputSpecification is *not* provided to this method, because the goal is consistency, and if output is done it must be consistent with the
  * output description, since that was what was partly used to determine if output should be taking place.  So it may be necessary for this method to decode
  * an output description string in order to determine what should be done.
  *@param documentURI is the URI of the document.  The URI is presumed to be the unique identifier which the output data store will use to process
  * and serve the document.  This URI is constructed by the repository connector which fetches the document, and is thus universal across all output connectors.
  *@param outputDescription is the description string that was constructed for this document by the getOutputDescription() method.
  *@param document is the document data to be processed (handed to the output data store).
  *@param authorityNameString is the name of the authority responsible for authorizing any access tokens passed in with the repository document.  May be null.
  *@param activities is the handle to an object that the implementer of an output connector may use to perform operations, such as logging processing activity.
  *@return the document status (accepted or permanently rejected).
  */
  public int addOrReplaceDocument(String documentURI, String outputDescription, RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
    throws LCFException, ServiceInterruption
  {
    // Establish a session
    getSession();

    // Now, go off and call the ingest API.
    if (poster.indexPost(documentURI,document,activities))
      return DOCUMENTSTATUS_ACCEPTED;
    return DOCUMENTSTATUS_REJECTED;
  }

  /** Remove a document using the connector.
  * Note that the last outputDescription is included, since it may be necessary for the connector to use such information to know how to properly remove the document.
  *@param documentURI is the URI of the document.  The URI is presumed to be the unique identifier which the output data store will use to process
  * and serve the document.  This URI is constructed by the repository connector which fetches the document, and is thus universal across all output connectors.
  *@param outputDescription is the last description string that was constructed for this document by the getOutputDescription() method above.
  *@param activities is the handle to an object that the implementer of an output connector may use to perform operations, such as logging processing activity.
  */
  public void removeDocument(String documentURI, String outputDescription, IOutputRemoveActivity activities)
    throws LCFException, ServiceInterruption
  {
    // Establish a session
    getSession();

    // Call the ingestion API.
    poster.deletePost(documentURI,activities);
  }

  // Protected methods

  /** Stuffer for packing a single string with an end delimiter */
  protected static void pack(StringBuffer output, String value, char delimiter)
  {
    int i = 0;
    while (i < value.length())
    {
      char x = value.charAt(i++);
      if (x == '\\' || x == delimiter)
        output.append('\\');
      output.append(x);
    }
    output.append(delimiter);
  }

  /** Unstuffer for the above. */
  protected static int unpack(StringBuffer sb, String value, int startPosition, char delimiter)
  {
    while (startPosition < value.length())
    {
      char x = value.charAt(startPosition++);
      if (x == '\\')
      {
        if (startPosition < value.length())
          x = value.charAt(startPosition++);
      }
      else if (x == delimiter)
        break;
      sb.append(x);
    }
    return startPosition;
  }

  /** Stuffer for packing lists of fixed length */
  protected static void packFixedList(StringBuffer output, String[] values, char delimiter)
  {
    int i = 0;
    while (i < values.length)
    {
      pack(output,values[i++],delimiter);
    }
  }

  /** Unstuffer for unpacking lists of fixed length */
  protected static int unpackFixedList(String[] output, String value, int startPosition, char delimiter)
  {
    StringBuffer sb = new StringBuffer();
    int i = 0;
    while (i < output.length)
    {
      sb.setLength(0);
      startPosition = unpack(sb,value,startPosition,delimiter);
      output[i++] = sb.toString();
    }
    return startPosition;
  }

  /** Stuffer for packing lists of variable length */
  protected static void packList(StringBuffer output, ArrayList values, char delimiter)
  {
    pack(output,Integer.toString(values.size()),delimiter);
    int i = 0;
    while (i < values.size())
    {
      pack(output,values.get(i++).toString(),delimiter);
    }
  }

  /** Another stuffer for packing lists of variable length */
  protected static void packList(StringBuffer output, String[] values, char delimiter)
  {
    pack(output,Integer.toString(values.length),delimiter);
    int i = 0;
    while (i < values.length)
    {
      pack(output,values[i++],delimiter);
    }
  }

  /** Unstuffer for unpacking lists of variable length.
  *@param output is the output array to put the unpacked values into.
  *@param value is the value to unpack.
  *@param startPosition is the place to start the unpack.
  *@param delimiter is the character to use between values.
  *@return the next position beyond the end of the list.
  */
  protected static int unpackList(ArrayList output, String value, int startPosition, char delimiter)
  {
    StringBuffer sb = new StringBuffer();
    startPosition = unpack(sb,value,startPosition,delimiter);
    try
    {
      int count = Integer.parseInt(sb.toString());
      int i = 0;
      while (i < count)
      {
        sb.setLength(0);
        startPosition = unpack(sb,value,startPosition,delimiter);
        output.add(sb.toString());
        i++;
      }
    }
    catch (NumberFormatException e)
    {
    }
    return startPosition;
  }

}
