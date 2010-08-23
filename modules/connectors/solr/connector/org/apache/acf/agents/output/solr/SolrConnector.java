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
package org.apache.acf.agents.output.solr;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.agents.interfaces.*;

import java.util.*;
import java.io.*;

/** This is the output connector for SOLR.  Currently, no frills.
*/
public class SolrConnector extends org.apache.acf.agents.output.BaseOutputConnector
{
  public static final String _rcsid = "@(#)$Id$";

  // Activities we log

  /** Ingestion activity */
  public final static String INGEST_ACTIVITY = "document ingest";
  /** Document removal activity */
  public final static String REMOVE_ACTIVITY = "document deletion";

  /** Local data */
  protected HttpPoster poster = null;
  /** The allow attribute name */
  protected String allowAttributeName = "allow_token_";
  /** The deny attribute name */
  protected String denyAttributeName = "deny_token_";
  
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
    throws ACFException
  {
    poster = null;
    super.disconnect();
  }

  /** Set up a session */
  protected void getSession()
    throws ACFException
  {
    if (poster == null)
    {
      String protocol = params.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_PROTOCOL);
      if (protocol == null || protocol.length() == 0)
        throw new ACFException("Missing parameter: "+org.apache.acf.agents.output.solr.SolrConfig.PARAM_PROTOCOL);

      String server = params.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_SERVER);
      if (server == null || server.length() == 0)
        throw new ACFException("Missing parameter: "+org.apache.acf.agents.output.solr.SolrConfig.PARAM_SERVER);

      String port = params.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_PORT);
      if (port == null || port.length() == 0)
        port = "80";

      String webapp = params.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_WEBAPPNAME);
      if (webapp == null || webapp.length() == 0)
        webapp = "";

      String core = params.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_CORE);
      if (core != null && core.length() == 0)
        core = null;
      
      String updatePath = params.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_UPDATEPATH);
      if (updatePath == null || updatePath.length() == 0)
        updatePath = "";

      String removePath = params.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_REMOVEPATH);
      if (removePath == null || removePath.length() == 0)
        removePath = "";

      String statusPath = params.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_STATUSPATH);
      if (statusPath == null || statusPath.length() == 0)
        statusPath = "";

      String idAttributeName = params.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_IDFIELD);
      if (idAttributeName == null || idAttributeName.length() == 0)
        idAttributeName = "id";
      
      String userID = params.getParameter(SolrConfig.PARAM_USERID);
      String password = params.getObfuscatedParameter(SolrConfig.PARAM_PASSWORD);
      String realm = params.getParameter(SolrConfig.PARAM_REALM);
      
      if (core != null)
      {
        if (webapp.length() == 0)
          throw new ACFException("Webapp must be specified if core is specified.");
        webapp = webapp + "/" + core;
      }
      
      try
      {
        poster = new HttpPoster(protocol,server,Integer.parseInt(port),webapp,updatePath,removePath,statusPath,realm,userID,password,
          allowAttributeName,denyAttributeName,idAttributeName);
      }
      catch (NumberFormatException e)
      {
        throw new ACFException(e.getMessage());
      }
    }
  }

  /** Test the connection.  Returns a string describing the connection integrity.
  *@return the connection's status as a displayable string.
  */
  public String check()
    throws ACFException
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
    throws ACFException
  {
    StringBuffer sb = new StringBuffer();

    // All the arguments need to go into this string, since they affect ingestion.
    Map args = new HashMap();
    int i = 0;
    while (i < params.getChildCount())
    {
      ConfigNode node = params.getChild(i++);
      if (node.getType().equals(SolrConfig.NODE_ARGUMENT))
      {
        String attrName = node.getAttributeValue(SolrConfig.ATTRIBUTE_NAME);
        ArrayList list = (ArrayList)args.get(attrName);
        if (list == null)
        {
          list = new ArrayList();
          args.put(attrName,list);
        }
        list.add(node.getAttributeValue(SolrConfig.ATTRIBUTE_VALUE));
      }
    }
    
    String[] sortArray = new String[args.size()];
    Iterator iter = args.keySet().iterator();
    i = 0;
    while (iter.hasNext())
    {
      sortArray[i++] = (String)iter.next();
    }
    
    // Always use sorted order, because we need this to be comparable.
    java.util.Arrays.sort(sortArray);
    
    String[] fixedList = new String[2];
    ArrayList nameValues = new ArrayList();
    i = 0;
    while (i < sortArray.length)
    {
      String name = sortArray[i++];
      ArrayList values = (ArrayList)args.get(name);
      int j = 0;
      while (j < values.size())
      {
        String value = (String)values.get(j++);
        fixedList[0] = name;
        fixedList[1] = value;
        StringBuffer pairBuffer = new StringBuffer();
        packFixedList(pairBuffer,fixedList,'=');
        nameValues.add(pairBuffer.toString());
      }
    }
    
    packList(sb,nameValues,'+');
    
    Map fieldMap = new HashMap();
    i = 0;
    while (i < spec.getChildCount())
    {
      SpecificationNode sn = spec.getChild(i++);
      if (sn.getType().equals(SolrConfig.NODE_FIELDMAP))
      {
        String source = sn.getAttributeValue(SolrConfig.ATTRIBUTE_SOURCE);
        String target = sn.getAttributeValue(SolrConfig.ATTRIBUTE_TARGET);
        if (target == null)
          target = "";
        fieldMap.put(source,target);
      }
    }
    
    sortArray = new String[fieldMap.size()];
    i = 0;
    iter = fieldMap.keySet().iterator();
    while (iter.hasNext())
    {
      sortArray[i++] = (String)iter.next();
    }
    java.util.Arrays.sort(sortArray);
    
    ArrayList sourceTargets = new ArrayList();
    
    i = 0;
    while (i < sortArray.length)
    {
      String source = sortArray[i++];
      String target = (String)fieldMap.get(source);
      fixedList[0] = source;
      fixedList[1] = target;
      StringBuffer pairBuffer = new StringBuffer();
      packFixedList(pairBuffer,fixedList,'=');
      sourceTargets.add(pairBuffer.toString());
    }
    
    packList(sb,sourceTargets,'+');

    return sb.toString();
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
    throws ACFException, ServiceInterruption
  {
    // Build the argument map we'll send.
    Map args = new HashMap();
    Map sourceTargets = new HashMap();
    int index = 0;
    ArrayList nameValues = new ArrayList();
    index = unpackList(nameValues,outputDescription,index,'+');
    ArrayList sts = new ArrayList();
    index = unpackList(sts,outputDescription,index,'+');
    String[] fixedBuffer = new String[2];
    
    // Do the name/value pairs
    int i = 0;
    while (i < nameValues.size())
    {
      String x = (String)nameValues.get(i++);
      unpackFixedList(fixedBuffer,x,0,'=');
      String attrName = fixedBuffer[0];
      ArrayList list = (ArrayList)args.get(attrName);
      if (list == null)
      {
        list = new ArrayList();
        args.put(attrName,list);
      }
      list.add(fixedBuffer[1]);
    }
    
    // Do the source/target pairs
    i = 0;
    while (i < sts.size())
    {
      String x = (String)sts.get(i++);
      unpackFixedList(fixedBuffer,x,0,'=');
      sourceTargets.put(fixedBuffer[0],fixedBuffer[1]);
    }

    // Establish a session
    getSession();

    // Now, go off and call the ingest API.
    if (poster.indexPost(documentURI,document,args,sourceTargets,authorityNameString,activities))
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
    throws ACFException, ServiceInterruption
  {
    // Establish a session
    getSession();

    // Call the ingestion API.
    poster.deletePost(documentURI,activities);
  }

  // UI support methods.
  //
  // These support methods come in two varieties.  The first bunch is involved in setting up connection configuration information.  The second bunch
  // is involved in presenting and editing output specification information for a job.  The two kinds of methods are accordingly treated differently,
  // in that the first bunch cannot assume that the current connector object is connected, while the second bunch can.  That is why the first bunch
  // receives a thread context argument for all UI methods, while the second bunch does not need one (since it has already been applied via the connect()
  // method, above).
    
  /** Output the configuration header section.
  * This method is called in the head section of the connector's configuration page.  Its purpose is to add the required tabs to the list, and to output any
  * javascript methods that might be needed by the configuration editing HTML.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, ConfigParams parameters, ArrayList tabsArray)
    throws ACFException, IOException
  {
    tabsArray.add("Server");
    tabsArray.add("Schema");
    tabsArray.add("Arguments");

    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function checkConfig()\n"+
"{\n"+
"  if (editconnection.servername.value == \"\")\n"+
"  {\n"+
"    alert(\"Please supply a valid Solr server name\");\n"+
"    editconnection.servername.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.serverport.value != \"\" && !isInteger(editconnection.serverport.value))\n"+
"  {\n"+
"    alert(\"Solr server port must be a valid integer\");\n"+
"    editconnection.serverport.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.webappname.value != \"\" && editconnection.webappname.value.indexOf(\"/\") != -1)\n"+
"  {\n"+
"    alert(\"Web application name cannot have '/' characters\");\n"+
"    editconnection.webappname.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.core.value != \"\" && editconnection.core.value.indexOf(\"/\") != -1)\n"+
"  {\n"+
"    alert(\"Core name cannot have '/' characters\");\n"+
"    editconnection.core.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.webappname.value == \"\" && editconnection.core.value != \"\")\n"+
"  {\n"+
"    alert(\"Web application must be specified if core is specified\");\n"+
"    editconnection.webappname.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.updatepath.value != \"\" && editconnection.updatepath.value.substring(0,1) != \"/\")\n"+
"  {\n"+
"    alert(\"Update path must start with a  '/' character\");\n"+
"    editconnection.updatepath.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.removepath.value != \"\" && editconnection.removepath.value.substring(0,1) != \"/\")\n"+
"  {\n"+
"    alert(\"Remove path must start with a  '/' character\");\n"+
"    editconnection.removepath.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.statuspath.value != \"\" && editconnection.statuspath.value.substring(0,1) != \"/\")\n"+
"  {\n"+
"    alert(\"Status path must start with a  '/' character\");\n"+
"    editconnection.statuspath.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function checkConfigForSave()\n"+
"{\n"+
"  if (editconnection.servername.value == \"\")\n"+
"  {\n"+
"    alert(\"Please supply a valid Solr server name\");\n"+
"    SelectTab(\"Server\");\n"+
"    editconnection.servername.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.serverport.value != \"\" && !isInteger(editconnection.serverport.value))\n"+
"  {\n"+
"    alert(\"Solr server port must be a valid integer\");\n"+
"    SelectTab(\"Server\");\n"+
"    editconnection.serverport.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.webappname.value != \"\" && editconnection.webappname.value.indexOf(\"/\") != -1)\n"+
"  {\n"+
"    alert(\"Web application name cannot have '/' characters\");\n"+
"    SelectTab(\"Server\");\n"+
"    editconnection.webappname.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.core.value != \"\" && editconnection.core.value.indexOf(\"/\") != -1)\n"+
"  {\n"+
"    alert(\"Core name cannot have '/' characters\");\n"+
"    SelectTab(\"Server\");\n"+
"    editconnection.core.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.webappname.value == \"\" && editconnection.core.value != \"\")\n"+
"  {\n"+
"    alert(\"Web application must be specified if core is specified\");\n"+
"    SelectTab(\"Server\");\n"+
"    editconnection.webappname.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.updatepath.value != \"\" && editconnection.updatepath.value.substring(0,1) != \"/\")\n"+
"  {\n"+
"    alert(\"Update path must start with a  '/' character\");\n"+
"    SelectTab(\"Server\");\n"+
"    editconnection.updatepath.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.removepath.value != \"\" && editconnection.removepath.value.substring(0,1) != \"/\")\n"+
"  {\n"+
"    alert(\"Remove path must start with a  '/' character\");\n"+
"    SelectTab(\"Server\");\n"+
"    editconnection.removepath.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.statuspath.value != \"\" && editconnection.statuspath.value.substring(0,1) != \"/\")\n"+
"  {\n"+
"    alert(\"Status path must start with a  '/' character\");\n"+
"    SelectTab(\"Server\");\n"+
"    editconnection.statuspath.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function deleteArgument(i)\n"+
"{\n"+
"  // Set the operation\n"+
"  eval(\"editconnection.argument_\"+i+\"_op.value=\\\"Delete\\\"\");\n"+
"  // Submit\n"+
"  if (editconnection.argument_count.value==i)\n"+
"    postFormSetAnchor(\"argument\");\n"+
"  else\n"+
"    postFormSetAnchor(\"argument_\"+i)\n"+
"  // Undo, so we won't get two deletes next time\n"+
"  eval(\"editconnection.argument_\"+i+\"_op.value=\\\"Continue\\\"\");\n"+
"}\n"+
"\n"+
"function addArgument()\n"+
"{\n"+
"  if (editconnection.argument_name.value == \"\")\n"+
"  {\n"+
"    alert(\"Argument name cannot be an empty string\");\n"+
"    editconnection.argument_name.focus();\n"+
"    return;\n"+
"  }\n"+
"  editconnection.argument_op.value=\"Add\";\n"+
"  postFormSetAnchor(\"argument\");\n"+
"}\n"+
"\n"+
"//-->\n"+
"</script>\n"
    );

  }
  
  /** Output the configuration body section.
  * This method is called in the body section of the connector's configuration page.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editconnection".
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabName is the current tab name.
  */
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out, ConfigParams parameters, String tabName)
    throws ACFException, IOException
  {
    String protocol = parameters.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_PROTOCOL);
    if (protocol == null)
      protocol = "http";
		
    String server = parameters.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_SERVER);
    if (server == null)
      server = "localhost";

    String port = parameters.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_PORT);
    if (port == null)
      port = "8983";

    String webapp = parameters.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_WEBAPPNAME);
    if (webapp == null)
      webapp = "solr";

    String core = parameters.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_CORE);
    if (core == null)
      core = "";

    String updatePath = parameters.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_UPDATEPATH);
    if (updatePath == null)
      updatePath = "/update/extract";

    String removePath = parameters.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_REMOVEPATH);
    if (removePath == null)
      removePath = "/update";

    String statusPath = parameters.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_STATUSPATH);
    if (statusPath == null)
      statusPath = "/admin/ping";

    String idField = parameters.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_IDFIELD);
    if (idField == null)
      idField = "id";
    
    String realm = parameters.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_REALM);
    if (realm == null)
      realm = "";

    String userID = parameters.getParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_USERID);
    if (userID == null)
      userID = "";
		
    String password = parameters.getObfuscatedParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_PASSWORD);
    if (password == null)
      password = "";
		
    // "Server" tab
    if (tabName.equals("Server"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Protocol:</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"serverprotocol\">\n"+
"        <option value=\"http\""+(protocol.equals("http")?" selected=\"true\"":"")+">http</option>\n"+
"        <option value=\"https\""+(protocol.equals("https")?" selected=\"true\"":"")+">https</option>\n"+
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Server name:</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"servername\" type=\"text\" size=\"32\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(server)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Port:</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"serverport\" type=\"text\" size=\"5\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(port)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Web application name:</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"webappname\" type=\"text\" size=\"16\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(webapp)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Core name:</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"core\" type=\"text\" size=\"16\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(core)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Update handler:</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"updatepath\" type=\"text\" size=\"32\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(updatePath)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Remove handler:</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"removepath\" type=\"text\" size=\"32\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(removePath)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Status handler:</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"statuspath\" type=\"text\" size=\"32\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(statusPath)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Realm:</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"realm\" type=\"text\" size=\"32\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(realm)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>User ID:</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"userid\" type=\"text\" size=\"32\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(userID)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Password:</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"password\" size=\"32\" name=\"password\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(password)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Server tab hiddens
      out.print(
"<input type=\"hidden\" name=\"serverprotocol\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(protocol)+"\"/>\n"+
"<input type=\"hidden\" name=\"servername\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(server)+"\"/>\n"+
"<input type=\"hidden\" name=\"serverport\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(port)+"\"/>\n"+
"<input type=\"hidden\" name=\"webappname\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(webapp)+"\"/>\n"+
"<input type=\"hidden\" name=\"core\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(core)+"\"/>\n"+
"<input type=\"hidden\" name=\"updatepath\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(updatePath)+"\"/>\n"+
"<input type=\"hidden\" name=\"removepath\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(removePath)+"\"/>\n"+
"<input type=\"hidden\" name=\"statuspath\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(statusPath)+"\"/>\n"+
"<input type=\"hidden\" name=\"realm\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(realm)+"\"/>\n"+
"<input type=\"hidden\" name=\"userid\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(userID)+"\"/>\n"+
"<input type=\"hidden\" name=\"password\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(password)+"\"/>\n"
      );
    }

    // "Schema" tab
    if (tabName.equals("Schema"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>ID field name:</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"idfield\" type=\"text\" size=\"32\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(idField)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"idfield\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(idField)+"\"/>\n"
      );
    }
    
    // Prepare for the argument tab
    Map argumentMap = new HashMap();
    int i = 0;
    while (i < parameters.getChildCount())
    {
      ConfigNode sn = parameters.getChild(i++);
      if (sn.getType().equals(org.apache.acf.agents.output.solr.SolrConfig.NODE_ARGUMENT))
      {
        String name = sn.getAttributeValue(org.apache.acf.agents.output.solr.SolrConfig.ATTRIBUTE_NAME);
        String value = sn.getAttributeValue(org.apache.acf.agents.output.solr.SolrConfig.ATTRIBUTE_VALUE);
        ArrayList values = (ArrayList)argumentMap.get(name);
        if (values == null)
        {
          values = new ArrayList();
          argumentMap.put(name,values);
        }
        values.add(value);
      }
    }
    // "Arguments" tab
    if (tabName.equals("Arguments"))
    {
      // For the display, sort the arguments into alphabetic order
      String[] sortArray = new String[argumentMap.size()];
      i = 0;
      Iterator iter = argumentMap.keySet().iterator();
      while (iter.hasNext())
      {
        sortArray[i++] = (String)iter.next();
      }
      java.util.Arrays.sort(sortArray);
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Arguments:</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>Name</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>Value</nobr></td>\n"+
"        </tr>\n"
      );
      i = 0;
      int k = 0;
      while (k < sortArray.length)
      {
        String name = sortArray[k++];
        ArrayList values = (ArrayList)argumentMap.get(name);
        int j = 0;
        while (j < values.size())
        {
          String value = (String)values.get(j++);
          // Its prefix will be...
          String prefix = "argument_" + Integer.toString(i);
          out.print(
"        <tr class=\""+(((i % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\""+prefix+"\"><input type=\"button\" value=\"Delete\" alt=\"Delete argument #"+Integer.toString(i+1)+"\" onclick=\"javascript:deleteArgument("+Integer.toString(i)+");"+"\"/>\n"+
"              <input type=\"hidden\" name=\""+prefix+"_op"+"\" value=\"Continue\"/>\n"+
"              <input type=\"hidden\" name=\""+prefix+"_name"+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(name)+"\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+org.apache.acf.ui.util.Encoder.bodyEscape(name)+"</nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"30\" name=\""+prefix+"_value"+"\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(value)+"\"</nobr>\n"+
"          </td>\n"+
"        </tr>\n"
          );
          i++;
        }
      }
      if (i == 0)
      {
        out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"3\">No arguments specified</td></tr>\n"
        );
      }
      out.print(
"        <tr class=\"formrow\"><td class=\"formseparator\" colspan=\"3\"><hr/></td></tr>\n"+
"        <tr class=\"formrow\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\"argument\"><input type=\"button\" value=\"Add\" alt=\"Add argument\" onclick=\"javascript:addArgument();\"/></a>\n"+
"              <input type=\"hidden\" name=\"argument_count\" value=\""+Integer.toString(i)+"\"/>\n"+
"              <input type=\"hidden\" name=\"argument_op\" value=\"Continue\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"30\" name=\"argument_name\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"30\" name=\"argument_value\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"        </tr>\n"+
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Emit hiddens for argument tab
      i = 0;
      Iterator iter = argumentMap.keySet().iterator();
      while (iter.hasNext())
      {
        String name = (String)iter.next();
        ArrayList values = (ArrayList)argumentMap.get(name);
        int j = 0;
        while (j < values.size())
        {
          String value = (String)values.get(j++);
          // It's prefix will be...
          String prefix = "argument_" + Integer.toString(i++);
          out.print(
"<input type=\"hidden\" name=\""+prefix+"_name\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(name)+"\"/>\n"+
"<input type=\"hidden\" name=\""+prefix+"_value\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(value)+"\"/>\n"
          );
        }
      }
      out.print(
"<input type=\"hidden\" name=\"argument_count\" value=\""+Integer.toString(i)+"\"/>\n"
      );
    }
  }
  
  /** Process a configuration post.
  * This method is called at the start of the connector's configuration page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the configuration parameters accordingly.
  * The name of the posted form is "editconnection".
  *@param threadContext is the local thread context.
  *@param variableContext is the set of variables available from the post, including binary file post information.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the connection (and cause a redirection to an error page).
  */
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext, ConfigParams parameters)
    throws ACFException
  {
    String protocol = variableContext.getParameter("serverprotocol");
    if (protocol != null)
      parameters.setParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_PROTOCOL,protocol);
		
    String server = variableContext.getParameter("servername");
    if (server != null)
      parameters.setParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_SERVER,server);

    String port = variableContext.getParameter("serverport");
    if (port != null)
      parameters.setParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_PORT,port);

    String webapp = variableContext.getParameter("webappname");
    if (webapp != null)
      parameters.setParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_WEBAPPNAME,webapp);

    String core = variableContext.getParameter("core");
    if (core != null)
      parameters.setParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_CORE,core);

    String updatePath = variableContext.getParameter("updatepath");
    if (updatePath != null)
      parameters.setParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_UPDATEPATH,updatePath);

    String removePath = variableContext.getParameter("removepath");
    if (removePath != null)
      parameters.setParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_REMOVEPATH,removePath);

    String statusPath = variableContext.getParameter("statuspath");
    if (statusPath != null)
      parameters.setParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_STATUSPATH,statusPath);

    String idField = variableContext.getParameter("idfield");
    if (idField != null)
      parameters.setParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_IDFIELD,idField);

    String realm = variableContext.getParameter("realm");
    if (realm != null)
      parameters.setParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_REALM,realm);

    String userID = variableContext.getParameter("userid");
    if (userID != null)
      parameters.setParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_USERID,userID);
		
    String password = variableContext.getParameter("password");
    if (password != null)
      parameters.setObfuscatedParameter(org.apache.acf.agents.output.solr.SolrConfig.PARAM_PASSWORD,password);
    
    String x = variableContext.getParameter("argument_count");
    if (x != null && x.length() > 0)
    {
      // About to gather the argument nodes, so get rid of the old ones.
      int i = 0;
      while (i < parameters.getChildCount())
      {
        ConfigNode node = parameters.getChild(i);
        if (node.getType().equals(org.apache.acf.agents.output.solr.SolrConfig.NODE_ARGUMENT))
          parameters.removeChild(i);
        else
          i++;
      }
      int count = Integer.parseInt(x);
      i = 0;
      while (i < count)
      {
        String prefix = "argument_"+Integer.toString(i);
        String op = variableContext.getParameter(prefix+"_op");
        if (op == null || !op.equals("Delete"))
        {
          // Gather the name and value.
          String name = variableContext.getParameter(prefix+"_name");
          String value = variableContext.getParameter(prefix+"_value");
          ConfigNode node = new ConfigNode(org.apache.acf.agents.output.solr.SolrConfig.NODE_ARGUMENT);
          node.setAttribute(org.apache.acf.agents.output.solr.SolrConfig.ATTRIBUTE_NAME,name);
          node.setAttribute(org.apache.acf.agents.output.solr.SolrConfig.ATTRIBUTE_VALUE,value);
          parameters.addChild(parameters.getChildCount(),node);
        }
        i++;
      }
      String addop = variableContext.getParameter("argument_op");
      if (addop != null && addop.equals("Add"))
      {
        String name = variableContext.getParameter("argument_name");
        String value = variableContext.getParameter("argument_value");
        ConfigNode node = new ConfigNode(org.apache.acf.agents.output.solr.SolrConfig.NODE_ARGUMENT);
        node.setAttribute(org.apache.acf.agents.output.solr.SolrConfig.ATTRIBUTE_NAME,name);
        node.setAttribute(org.apache.acf.agents.output.solr.SolrConfig.ATTRIBUTE_VALUE,value);
        parameters.addChild(parameters.getChildCount(),node);
      }
    }
    return null;
  }
  
  /** View configuration.
  * This method is called in the body section of the connector's view configuration page.  Its purpose is to present the connection information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  */
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, ConfigParams parameters)
    throws ACFException, IOException
  {
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>Parameters:</nobr></td>\n"+
"    <td class=\"value\" colspan=\"3\">\n"
    );
    Iterator iter = parameters.listParameters();
    while (iter.hasNext())
    {
      String param = (String)iter.next();
      String value = parameters.getParameter(param);
      if (param.length() >= "password".length() && param.substring(param.length()-"password".length()).equalsIgnoreCase("password"))
      {
        out.print(
"      <nobr>"+org.apache.acf.ui.util.Encoder.bodyEscape(param)+"=********</nobr><br/>\n"
        );
      }
      else if (param.length() >="keystore".length() && param.substring(param.length()-"keystore".length()).equalsIgnoreCase("keystore"))
      {
        IKeystoreManager kmanager = KeystoreManagerFactory.make("",value);
        out.print(
"      <nobr>"+org.apache.acf.ui.util.Encoder.bodyEscape(param)+"=<"+Integer.toString(kmanager.getContents().length)+" certificate(s)></nobr><br/>\n"
        );
      }
      else
      {
        out.print(
"      <nobr>"+org.apache.acf.ui.util.Encoder.bodyEscape(param)+"="+org.apache.acf.ui.util.Encoder.bodyEscape(value)+"</nobr><br/>\n"
        );
      }
    }
    
    out.print(
"    </td>\n"+
"  </tr>\n"+
"\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>Arguments:</nobr></td>\n"+
"    <td class=\"boxcell\" colspan=\"3\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>Name</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>Value</nobr></td>\n"+
"        </tr>\n"
    );
    
    int i = 0;
    int instanceNumber = 0;
    while (i < parameters.getChildCount())
    {
      ConfigNode cn = parameters.getChild(i++);
      if (cn.getType().equals(org.apache.acf.agents.output.solr.SolrConfig.NODE_ARGUMENT))
      {
        // An argument node!  Look for all its parameters.
        String name = cn.getAttributeValue(org.apache.acf.agents.output.solr.SolrConfig.ATTRIBUTE_NAME);
        String value = cn.getAttributeValue(org.apache.acf.agents.output.solr.SolrConfig.ATTRIBUTE_VALUE);

        out.print(
"        <tr class=\""+(((instanceNumber % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\"><nobr>"+org.apache.acf.ui.util.Encoder.bodyEscape(name)+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+org.apache.acf.ui.util.Encoder.bodyEscape(value)+"</nobr></td>\n"+
"        </tr>\n"
        );
        
        instanceNumber++;
      }
    }
    if (instanceNumber == 0)
    {
      out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"5\">No arguments</td></tr>\n"
      );
    }
    
    out.print(
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
    );
  }
  
  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected an output connection of the current type.  Its purpose is to add the required tabs
  * to the list, and to output any javascript methods that might be needed by the job editing HTML.
  *@param out is the output to which any HTML should be sent.
  *@param os is the current output specification for this job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  public void outputSpecificationHeader(IHTTPOutput out, OutputSpecification os, ArrayList tabsArray)
    throws ACFException, IOException
  {
    tabsArray.add("Field Mapping");
    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"\n"+
"function checkOutputSpecification()\n"+
"{\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function addFieldMapping()\n"+
"{\n"+
"  if (editjob.solr_fieldmapping_source.value == \"\")\n"+
"  {\n"+
"    alert(\"Field map must have non-null source\");\n"+
"    editjob.solr_fieldmapping_source.focus();\n"+
"    return;\n"+
"  }\n"+
"  editjob.solr_fieldmapping_op.value=\"Add\";\n"+
"  postFormSetAnchor(\"solr_fieldmapping\");\n"+
"}\n"+
"\n"+
"function deleteFieldMapping(i)\n"+
"{\n"+
"  // Set the operation\n"+
"  eval(\"editjob.solr_fieldmapping_\"+i+\"_op.value=\\\"Delete\\\"\");\n"+
"  // Submit\n"+
"  if (editjob.solr_fieldmapping_count.value==i)\n"+
"    postFormSetAnchor(\"solr_fieldmapping\");\n"+
"  else\n"+
"    postFormSetAnchor(\"solr_fieldmapping_\"+i)\n"+
"  // Undo, so we won't get two deletes next time\n"+
"  eval(\"editjob.solr_fieldmapping_\"+i+\"_op.value=\\\"Continue\\\"\");\n"+
"}\n"+
"\n"+
"//-->\n"+
"</script>\n"
    );
  }
  
  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected an output connection of the current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editjob".
  *@param out is the output to which any HTML should be sent.
  *@param os is the current output specification for this job.
  *@param tabName is the current tab name.
  */
  public void outputSpecificationBody(IHTTPOutput out, OutputSpecification os, String tabName)
    throws ACFException, IOException
  {
    // Prep for field mapping tab
    HashMap fieldMap = new HashMap();
    int i = 0;
    while (i < os.getChildCount())
    {
      SpecificationNode sn = os.getChild(i++);
      if (sn.getType().equals(SolrConfig.NODE_FIELDMAP))
      {
        String source = sn.getAttributeValue(SolrConfig.ATTRIBUTE_SOURCE);
        String target = sn.getAttributeValue(SolrConfig.ATTRIBUTE_TARGET);
        if (target != null && target.length() == 0)
          target = null;
        fieldMap.put(source,target);
      }
    }
    
    // Field Mapping tab
    if (tabName.equals("Field Mapping"))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Field mappings:</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>Metadata field name</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>Solr field name</nobr></td>\n"+
"        </tr>\n"
      );

      String[] sourceFieldNames = new String[fieldMap.size()];
      Iterator iter = fieldMap.keySet().iterator();
      i = 0;
      while (iter.hasNext())
      {
        sourceFieldNames[i++] = (String)iter.next();
      }
      java.util.Arrays.sort(sourceFieldNames);
      
      int fieldCounter = 0;
      i = 0;
      while (i < sourceFieldNames.length)
      {
        String source = sourceFieldNames[i++];
        String target = (String)fieldMap.get(source);
        String targetDisplay = target;
        if (target == null)
        {
          target = "";
          targetDisplay = "(remove)";
        }
        // It's prefix will be...
        String prefix = "solr_fieldmapping_" + Integer.toString(fieldCounter);
        out.print(
"        <tr class=\""+(((fieldCounter % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\""+prefix+"\">\n"+
"              <input type=\"button\" value=\"Delete\" alt=\""+"Delete field mapping #"+Integer.toString(fieldCounter+1)+"\" onclick='javascript:deleteFieldMapping("+Integer.toString(fieldCounter)+");'/>\n"+
"              <input type=\"hidden\" name=\""+prefix+"_op\" value=\"Continue\"/>\n"+
"              <input type=\"hidden\" name=\""+prefix+"_source\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(source)+"\"/>\n"+
"              <input type=\"hidden\" name=\""+prefix+"_target\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(target)+"\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+org.apache.acf.ui.util.Encoder.bodyEscape(source)+"</nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+org.apache.acf.ui.util.Encoder.bodyEscape(targetDisplay)+"</nobr>\n"+
"          </td>\n"+
"        </tr>\n"
        );
        fieldCounter++;
      }
      
      if (fieldCounter == 0)
      {
        out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"3\">No field mapping specified</td></tr>\n"
        );
      }
      out.print(
"        <tr class=\"formrow\"><td class=\"formseparator\" colspan=\"3\"><hr/></td></tr>\n"+
"        <tr class=\"formrow\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\"solr_fieldmapping\">\n"+
"              <input type=\"button\" value=\"Add\" alt=\"Add field mapping\" onclick=\"javascript:addFieldMapping();\"/>\n"+
"            </a>\n"+
"            <input type=\"hidden\" name=\"solr_fieldmapping_count\" value=\""+fieldCounter+"\"/>\n"+
"            <input type=\"hidden\" name=\"solr_fieldmapping_op\" value=\"Continue\"/>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"15\" name=\"solr_fieldmapping_source\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"15\" name=\"solr_fieldmapping_target\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"        </tr>\n"+
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for field mapping
      out.print(
"<input type=\"hidden\" name=\"solr_fieldmapping_count\" value=\""+Integer.toString(fieldMap.size())+"\"/>\n"
      );
      Iterator iter = fieldMap.keySet().iterator();
      int fieldCounter = 0;
      while (iter.hasNext())
      {
        String source = (String)iter.next();
        String target = (String)fieldMap.get(source);
        if (target == null)
          target = "";
        // It's prefix will be...
        String prefix = "solr_fieldmapping_" + Integer.toString(fieldCounter);
        out.print(
"<input type=\"hidden\" name=\""+prefix+"_source\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(source)+"\"/>\n"+
"<input type=\"hidden\" name=\""+prefix+"_target\" value=\""+org.apache.acf.ui.util.Encoder.attributeEscape(target)+"\"/>\n"
        );
        fieldCounter++;
      }
    }

  }
  
  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the output specification accordingly.
  * The name of the posted form is "editjob".
  *@param variableContext contains the post data, including binary file-upload information.
  *@param os is the current output specification for this job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
  */
  public String processSpecificationPost(IPostParameters variableContext, OutputSpecification os)
    throws ACFException
  {
    String x = variableContext.getParameter("solr_fieldmapping_count");
    if (x != null && x.length() > 0)
    {
      // About to gather the fieldmapping nodes, so get rid of the old ones.
      int i = 0;
      while (i < os.getChildCount())
      {
        SpecificationNode node = os.getChild(i);
        if (node.getType().equals(SolrConfig.NODE_FIELDMAP))
          os.removeChild(i);
        else
          i++;
      }
      int count = Integer.parseInt(x);
      i = 0;
      while (i < count)
      {
        String prefix = "solr_fieldmapping_"+Integer.toString(i);
        String op = variableContext.getParameter(prefix+"_op");
        if (op == null || !op.equals("Delete"))
        {
          // Gather the fieldmap etc.
          String source = variableContext.getParameter(prefix+"_source");
          String target = variableContext.getParameter(prefix+"_target");
          if (target == null)
            target = "";
          SpecificationNode node = new SpecificationNode(SolrConfig.NODE_FIELDMAP);
          node.setAttribute(SolrConfig.ATTRIBUTE_SOURCE,source);
          node.setAttribute(SolrConfig.ATTRIBUTE_TARGET,target);
          os.addChild(os.getChildCount(),node);
        }
        i++;
      }
      String addop = variableContext.getParameter("solr_fieldmapping_op");
      if (addop != null && addop.equals("Add"))
      {
        String source = variableContext.getParameter("solr_fieldmapping_source");
        String target = variableContext.getParameter("solr_fieldmapping_target");
        if (target == null)
          target = "";
        SpecificationNode node = new SpecificationNode(SolrConfig.NODE_FIELDMAP);
        node.setAttribute(SolrConfig.ATTRIBUTE_SOURCE,source);
        node.setAttribute(SolrConfig.ATTRIBUTE_TARGET,target);
        os.addChild(os.getChildCount(),node);
      }
    }
    return null;
  }
  
  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the output specification information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param out is the output to which any HTML should be sent.
  *@param os is the current output specification for this job.
  */
  public void viewSpecification(IHTTPOutput out, OutputSpecification os)
    throws ACFException, IOException
  {
    // Prep for field mappings
    HashMap fieldMap = new HashMap();
    int i = 0;
    while (i < os.getChildCount())
    {
      SpecificationNode sn = os.getChild(i++);
      if (sn.getType().equals(SolrConfig.NODE_FIELDMAP))
      {
        String source = sn.getAttributeValue(SolrConfig.ATTRIBUTE_SOURCE);
        String target = sn.getAttributeValue(SolrConfig.ATTRIBUTE_TARGET);
        if (target != null && target.length() == 0)
          target = null;
        fieldMap.put(source,target);
      }
    }

    String[] sourceFieldNames = new String[fieldMap.size()];
    Iterator iter = fieldMap.keySet().iterator();
    i = 0;
    while (iter.hasNext())
    {
      sourceFieldNames[i++] = (String)iter.next();
    }
    java.util.Arrays.sort(sourceFieldNames);

    // Display field mappings
    out.print(
"\n"+
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>Field mappings:</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>Metadata field name</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>Solr field name</nobr></td>\n"+
"        </tr>\n"
    );

    int fieldCounter = 0;
    while (fieldCounter < sourceFieldNames.length)
    {
      String source = sourceFieldNames[fieldCounter++];
      String target = (String)fieldMap.get(source);
      String targetDisplay = target;
      if (target == null)
      {
        target = "";
        targetDisplay = "(remove)";
      }
      out.print(
"        <tr class=\""+(((fieldCounter % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+org.apache.acf.ui.util.Encoder.bodyEscape(source)+"</nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+org.apache.acf.ui.util.Encoder.bodyEscape(targetDisplay)+"</nobr>\n"+
"          </td>\n"+
"        </tr>\n"
      );
      fieldCounter++;
    }
    
    if (fieldCounter == 0)
    {
      out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"3\">No field mapping specified</td></tr>\n"
      );
    }
    out.print(
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
    );

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
